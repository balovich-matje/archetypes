package com.archetypes;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * A per-blow readout of the damage shaping chain, for verifying by hand what
 * the arithmetic claims. Opt-in per player through {@code /archetypes trace on}.
 *
 * <h2>Why it exists</h2>
 * Every melee multiplier this mod owns is a {@code @ModifyVariable} on
 * {@code hurtServer}'s {@code amount} (see {@code LivingEntityMixin}), so a
 * finished hit is a product of a dozen factors that composed silently. Reading
 * the final number tells you nothing about which of them fired: a Cutpurse
 * one-shotting a Colossus is either the design working or one gate inverted,
 * and the total looks identical either way. This class prints the chain.
 *
 * <h2>How it hooks up</h2>
 * {@code DamageTraceMixin} opens a frame at the very head of {@code hurtServer}
 * — before any shaper, which is what makes the first line the RAW damage vanilla
 * handed in — and closes it at RETURN, where the victim's remaining health is
 * final. In between, each shaping handler ends with exactly one
 * {@link #record(String, float, float)} call, and vanilla's own
 * {@code getDamageAfterArmorAbsorb} / {@code getDamageAfterMagicAbsorb} are
 * observed at their RETURN. Nothing here writes a damage number back; every
 * entry point takes floats and returns void.
 *
 * <h2>Cost when off</h2>
 * Every entry point opens with {@link #WATCHING}, one static boolean read, which
 * is false unless somebody has switched the trace on. Only past that gate does
 * anything allocate. A blow dealt by an unwatched entity while somebody else is
 * being traced pushes a <em>muted</em> frame — cheap, and it keeps the push/pop
 * pairing honest so a nested hit (a thorns reflect, a Shockwave splash) cannot
 * be mistaken for its parent.
 *
 * <h2>The breakdown, and why it is derived rather than logged</h2>
 * The instruction the shaping handlers follow is "one {@code record} call at the
 * end, nothing else" — scattering a log line into every conditional branch of
 * {@code archetypes$daggerDamage} would be a dozen edits to a hot, load-bearing
 * method for the sake of a dev tool. So the per-multiplier lines are re-derived
 * here from the same node ranks, the same {@link Tuning} constants and the same
 * live victim state the handler just read (the {@code record} call happens at
 * HEAD, so nothing has resolved yet and the state is still the state the handler
 * saw). The recorded {@code before}/{@code after} pair is the <em>authority</em>:
 * if the derived product disagrees with it by more than 1%, the trace says so on
 * its own line. That alarm is the whole reason the duplication is safe — a gate
 * that moves in the handler and not here announces itself the first time it is
 * traced, instead of quietly lying.
 *
 * <p>Server thread only, like everything that reads {@link MeleeSwing}.
 */
public final class DamageTrace {
	// --- stage keys, one per shaping handler on the hurtServer funnel ---

	/** {@code archetypes$greatswordDamage}: Heavy Blows, First Blood, Executioner. */
	public static final String STAGE_GREATSWORD = "greatsword";

	/** {@code archetypes$daggerDamage}: the whole Assassin/Nemesis Assassin chain. */
	public static final String STAGE_DAGGER = "dagger";

	/** {@code archetypes$firstStrike}: the Shadow's opener out of invisibility. */
	public static final String STAGE_FIRST_STRIKE = "first_strike";

	/** {@code archetypes$sunderDamage}: Meteor's smash bonus and Sunder's shred. */
	public static final String STAGE_SUNDER = "sunder";

	/** {@code archetypes$marksmanArrowHit}, delegated to {@code MarksmanCombat}. */
	public static final String STAGE_MARKSMAN = "marksman";

	/** {@code archetypes$magicArmamentHit}, delegated to {@code MagicArmaments}. */
	public static final String STAGE_ARMAMENTS = "armaments";

	/** Victim-side shapers. Each is one node's flat reduction, no gates worth
	 * expanding — the stage line's own factor says everything. */
	public static final String STAGE_MANA_SHIELD = "mana_shield";
	public static final String STAGE_COLOSSUS_BULWARK = "colossus_bulwark";
	public static final String STAGE_INSTINCTIVE_GUARD = "instinctive_guard";
	public static final String STAGE_BARBARIAN = "barbarian";

	/** Vanilla's own two absorption steps, observed at their RETURN. */
	public static final String STAGE_ARMOUR = "armour";
	public static final String STAGE_PROTECTION = "protection";

	/** Above this relative gap the derived product is flagged as drifted. */
	private static final float MISMATCH_TOLERANCE = 0.01F;

	/** Vanilla's armour curve: each point absorbs 4%, the whole capped at 80%.
	 * Mirrored from the two shapers that already compute it by hand. */
	private static final float ARMOR_PER_POINT = 0.04F;
	private static final float ARMOR_CAP = 0.8F;

	private static final String KEY = "commands.archetypes.trace.";

	private static final Set<UUID> WATCHED = new HashSet<>();

	/**
	 * The zero-cost gate: {@code !WATCHED.isEmpty()}, cached as a plain field so
	 * the common case (nobody tracing) is one static read and a branch, with no
	 * set lookup and no allocation behind it.
	 */
	private static boolean watching;

	/**
	 * Open frames, innermost last. A stack rather than a single slot because a
	 * hit can provoke a hit while it is still being shaped — Shockwave's splash
	 * is dealt from inside {@code archetypes$sunderDamage}, Iron Spikes' thorns
	 * from inside vanilla's blocking — and the inner blow must not steal the
	 * outer one's lines.
	 */
	private static final Deque<Frame> STACK = new ArrayDeque<>();

	/**
	 * The tick {@link #STACK} belongs to. Frames leak when a hit is cancelled by
	 * one of the {@code hurtServer} HEAD injections that {@code setReturnValue}
	 * (Sidestep, Ghost Form, the parry, the clash, Last Shadow's grace): a
	 * cancelled call never reaches its RETURN, so its {@code finish} never runs.
	 * A leak cannot outlive the tick it happened in, so a tick change empties the
	 * stack — that plus {@link #finish}'s pop-until-matched loop is the whole
	 * self-healing story.
	 */
	private static long stackTick = Long.MIN_VALUE;

	private DamageTrace() {
	}

	/** One blow being shaped: who threw it, at what, and the lines so far. */
	private static final class Frame {
		/** The traced attacker, or null for a muted frame nobody asked to see. */
		private final @Nullable ServerPlayer attacker;
		private final LivingEntity victim;
		private final float base;
		private final float victimHealth;
		private final List<Component> lines = new ArrayList<>();

		/** Cleared when a stage's derivation cannot be expressed as a product —
		 * an Executioner clamp is not a multiplier — so the mismatch alarm knows
		 * to hold its tongue for that stage. */
		private boolean exact = true;

		private Frame(final @Nullable ServerPlayer attacker, final LivingEntity victim,
				final float base) {
			this.attacker = attacker;
			this.victim = victim;
			this.base = base;
			this.victimHealth = victim.getHealth();
		}
	}

	// --- the switch ---

	/** Whether this player's blows are being traced. */
	public static boolean isWatched(final ServerPlayer player) {
		return watching && WATCHED.contains(player.getUUID());
	}

	/** Turn the trace on or off for one player. Returns the new state. */
	public static boolean watch(final ServerPlayer player, final boolean on) {
		if (on) {
			WATCHED.add(player.getUUID());
		} else {
			WATCHED.remove(player.getUUID());
		}

		watching = !WATCHED.isEmpty();

		if (!watching) {
			// Nothing will call finish again, so nothing would ever drain it.
			STACK.clear();
		}

		return on;
	}

	// --- the funnel's ends ---

	/**
	 * Open a frame for a blow. Called at the very head of {@code hurtServer},
	 * ahead of every shaper, so {@code amount} here is the raw number vanilla
	 * computed from the weapon, the strength buff and the crit.
	 */
	public static void begin(final ServerLevel level, final DamageSource source,
			final LivingEntity victim, final float amount) {
		if (!watching) {
			return;
		}

		long now = level.getGameTime();

		if (now != stackTick) {
			STACK.clear();
			stackTick = now;
		}

		ServerPlayer attacker = source.getEntity() instanceof ServerPlayer player
				&& WATCHED.contains(player.getUUID()) ? player : null;
		STACK.push(new Frame(attacker, victim, amount));
	}

	/**
	 * One shaping stage's contribution. The single call every shaping handler
	 * makes, on the way out, with the value it was handed and the value it is
	 * about to return.
	 *
	 * <p>Called at HEAD, before vanilla resolves anything, which is what lets
	 * the per-multiplier breakdown below read the victim's health and armour and
	 * get the same answers the handler just got.
	 */
	public static void record(final String stage, final float before, final float after) {
		if (!watching) {
			return;
		}

		Frame frame = STACK.peek();

		if (frame == null || frame.attacker == null) {
			return;
		}

		// A victim-side shaper that changed nothing is a node the victim does not
		// own; printing it would bury the chain in blanks. An attacker-side stage
		// that ran at all is worth a line even at x1 — that IS the finding.
		boolean unchanged = Math.abs(after - before) < 1.0E-4F;

		if (unchanged && !hasBreakdown(stage)) {
			return;
		}

		frame.lines.add(stageLine(stage, before, after));
		explain(frame, stage, frame.attacker, frame.victim, before, after);
	}

	/**
	 * Close the blow and print it. Called at {@code hurtServer}'s RETURN, where
	 * the victim's health is whatever the hit left them with.
	 *
	 * <p>Pops until it removes <em>this</em> victim's frame, which is how a leaked
	 * frame from a hit some other injection cancelled at HEAD gets cleared out
	 * instead of being mistaken for the caller's own.
	 */
	public static void finish(final LivingEntity victim) {
		if (!watching) {
			return;
		}

		Frame frame = null;

		while (!STACK.isEmpty()) {
			Frame popped = STACK.pop();

			if (popped.victim == victim) {
				frame = popped;
				break;
			}
		}

		if (frame == null || frame.attacker == null) {
			return;
		}

		ServerPlayer attacker = frame.attacker;
		attacker.sendSystemMessage(Component.translatable(KEY + "header",
				victim.getDisplayName(), num(frame.base)).withStyle(ChatFormatting.DARK_GRAY));

		for (Component line : frame.lines) {
			attacker.sendSystemMessage(line);
		}

		attacker.sendSystemMessage(Component.translatable(KEY + "result",
				num(frame.victimHealth - victim.getHealth()),
				num(victim.getHealth()), num(victim.getMaxHealth()))
						.withStyle(ChatFormatting.DARK_GRAY));
	}

	// --- line building ---

	private static Component stageLine(final String stage, final float before, final float after) {
		return Component.translatable(KEY + "stage",
				Component.translatable(KEY + "stage." + stage),
				num(before), num(after), factorText(before, after));
	}

	/**
	 * One multiplier's line. {@code fired} decides which of the two templates is
	 * used, because "Expose did not fire and here is why" is exactly as much of
	 * the finding as "Razor Edge multiplied by 1.24".
	 */
	private static void factor(final Frame frame, final String nameKey, final boolean fired,
			final float multiplier, final Component note) {
		frame.lines.add(fired
				? Component.translatable(KEY + "factor.on",
						Component.translatable(nameKey), num(multiplier), note)
				: Component.translatable(KEY + "factor.off",
						Component.translatable(nameKey), note));
	}

	/** A multiplier that is not a multiplier: an execute clamping to lethal. */
	private static void clamp(final Frame frame, final String nameKey, final Component note) {
		frame.exact = false;
		frame.lines.add(Component.translatable(KEY + "factor.clamp",
				Component.translatable(nameKey), note));
	}

	private static String num(final float value) {
		return String.format(Locale.ROOT, "%.2f", value);
	}

	private static String pct(final float fraction) {
		return String.format(Locale.ROOT, "%.0f", fraction * 100.0F);
	}

	private static String factorText(final float before, final float after) {
		return before <= 0.0F ? "-" : num(after / before);
	}

	private static Component note(final String suffix, final Object... args) {
		return Component.translatable(KEY + "note." + suffix, args);
	}

	private static Component rankNote(final int rank) {
		return rank > 0 ? note("rank", rank) : note("unowned");
	}

	private static Component healthNote(final LivingEntity victim, final float gate) {
		return note("health", pct(victim.getHealth() / victim.getMaxHealth()), pct(gate));
	}

	// --- the per-stage breakdowns ---

	/** Whether a stage has anything to say beyond its own before/after. */
	private static boolean hasBreakdown(final String stage) {
		return STAGE_GREATSWORD.equals(stage) || STAGE_DAGGER.equals(stage)
				|| STAGE_FIRST_STRIKE.equals(stage) || STAGE_SUNDER.equals(stage);
	}

	private static void explain(final Frame frame, final String stage, final ServerPlayer attacker,
			final LivingEntity victim, final float before, final float after) {
		float derived = switch (stage) {
			case STAGE_GREATSWORD -> explainGreatsword(frame, attacker, victim);
			case STAGE_DAGGER -> explainDagger(frame, attacker, victim);
			case STAGE_FIRST_STRIKE -> explainFirstStrike(frame, attacker);
			case STAGE_SUNDER -> explainSunder(frame, attacker, victim);
			default -> Float.NaN;
		};

		if (Float.isNaN(derived) || !frame.exact || before <= 0.0F) {
			frame.exact = true;
			return;
		}

		float actual = after / before;

		if (Math.abs(derived - actual) > MISMATCH_TOLERANCE * Math.max(1.0F, actual)) {
			frame.lines.add(Component.translatable(KEY + "mismatch", num(derived), num(actual))
					.withStyle(ChatFormatting.RED));
		}
	}

	/** Mirror of {@code archetypes$greatswordDamage}. */
	private static float explainGreatsword(final Frame frame, final ServerPlayer attacker,
			final LivingEntity victim) {
		Set<Integer> owned = NodePurchases.owned(attacker, SubTree.SLAYER);
		float product = 1.0F;

		int heavy = SlayerNodes.rank(SubTree.SLAYER, owned, SlayerNodes.Family.HEAVY);
		float heavyFactor = 1.0F + Tuning.HEAVY_PER_RANK * heavy;
		factor(frame, SlayerNodes.Family.HEAVY.nameKey(), heavy > 0, heavyFactor, rankNote(heavy));
		product *= heavyFactor;

		int firstBlood = SlayerNodes.rank(SubTree.SLAYER, owned, SlayerNodes.Family.FIRSTBLOOD);
		boolean unhurt = victim.getHealth() >= victim.getMaxHealth() - 0.01F;
		float bloodFactor = unhurt ? 1.0F + Tuning.FIRSTBLOOD_PER_RANK * firstBlood : 1.0F;
		factor(frame, SlayerNodes.Family.FIRSTBLOOD.nameKey(), firstBlood > 0 && unhurt,
				bloodFactor, firstBlood > 0 ? note("full_health", pct(
						victim.getHealth() / victim.getMaxHealth())) : note("unowned"));
		product *= bloodFactor;

		int executioner = SlayerNodes.rank(SubTree.SLAYER, owned, SlayerNodes.Family.EXECUTIONER);
		boolean finishable = victim.getHealth() <= victim.getMaxHealth() * Tuning.EXECUTE_THRESHOLD;

		if (executioner > 0 && finishable) {
			clamp(frame, SlayerNodes.Family.EXECUTIONER.nameKey(),
					healthNote(victim, Tuning.EXECUTE_THRESHOLD));
		} else {
			factor(frame, SlayerNodes.Family.EXECUTIONER.nameKey(), false, 1.0F,
					executioner > 0 ? healthNote(victim, Tuning.EXECUTE_THRESHOLD) : note("unowned"));
		}

		return product;
	}

	/**
	 * Mirror of {@code archetypes$daggerDamage} — the long one, and the reason
	 * this class exists. Everything past the Shadow Step gate only ever fires on
	 * the strike the blink delivers, so on an ordinary swing those lines all read
	 * "not a Shadow Step strike", which is itself the answer to "why was that hit
	 * small".
	 */
	private static float explainDagger(final Frame frame, final ServerPlayer attacker,
			final LivingEntity victim) {
		Set<Integer> owned = NodePurchases.owned(attacker, SubTree.ASSASSIN);
		float product = 1.0F;

		int razor = AssassinNodes.rank(SubTree.ASSASSIN, owned, AssassinNodes.Family.RAZOR_EDGE);
		float razorFactor = 1.0F + Tuning.RAZOR_EDGE_PER_RANK * razor;
		factor(frame, AssassinNodes.Family.RAZOR_EDGE.nameKey(), razor > 0, razorFactor,
				rankNote(razor));
		product *= razorFactor;

		int expose = AssassinNodes.rank(SubTree.ASSASSIN, owned, AssassinNodes.Family.EXPOSE);
		boolean hurt = victim.getHealth() < victim.getMaxHealth() * 0.5F;
		float exposeFactor = hurt ? 1.0F + Tuning.EXPOSE_PER_RANK * expose : 1.0F;
		factor(frame, AssassinNodes.Family.EXPOSE.nameKey(), expose > 0 && hurt, exposeFactor,
				expose > 0 ? healthNote(victim, 0.5F) : note("unowned"));
		product *= exposeFactor;

		int flense = AssassinNodes.rank(SubTree.ASSASSIN, owned, AssassinNodes.Family.FLENSE);
		float absorbed = armorAbsorption(victim);
		float flenseFactor = 1.0F;

		if (flense > 0) {
			float ignored = Math.min(1.0F, Tuning.FLENSE_PER_RANK * flense);
			flenseFactor = (1.0F - absorbed * (1.0F - ignored)) / (1.0F - absorbed);
		}

		factor(frame, AssassinNodes.Family.FLENSE.nameKey(), flense > 0, flenseFactor,
				flense > 0 ? note("armour", num((float) victim.getAttributeValue(Attributes.ARMOR)),
						pct(absorbed)) : note("unowned"));
		product *= flenseFactor;

		boolean marked = DeathMark.isMarkedBy(victim, attacker) && DeathMark.hasMark(attacker);
		float markFactor = marked ? 1.0F + Tuning.DEATH_MARK_DAMAGE_FACTOR : 1.0F;
		factor(frame, NemesisAssassinNodes.Family.DEATH_MARK.nameKey(), marked, markFactor,
				note(marked ? "marked" : "unmarked"));
		product *= markFactor;

		int headhunter = NemesisAssassinNodes.rank(attacker,
				NemesisAssassinNodes.Family.HEADHUNTER);
		float huntFactor = marked ? 1.0F + Tuning.HEADHUNTER_PER_RANK * headhunter : 1.0F;
		factor(frame, NemesisAssassinNodes.Family.HEADHUNTER.nameKey(),
				marked && headhunter > 0, huntFactor,
				headhunter > 0 ? note(marked ? "marked" : "unmarked") : note("unowned"));
		product *= huntFactor;

		Long stepStrike = ((AttachmentTarget) attacker).getAttached(ModAttachments.STEP_STRIKE_AT);
		boolean stepping = stepStrike != null && stepStrike == attacker.level().getGameTime();

		return product * explainStepStrike(frame, attacker, victim, owned, marked, stepping);
	}

	/**
	 * The Shadow Step half of the dagger chain. Split out because every one of
	 * these is gated twice — once on the blink, once on its own node — and the
	 * pair is what the author is looking for.
	 */
	private static float explainStepStrike(final Frame frame, final ServerPlayer attacker,
			final LivingEntity victim, final Set<Integer> owned, final boolean marked,
			final boolean stepping) {
		float product = 1.0F;
		Component notStepped = note("not_step");

		int coup = NemesisAssassinNodes.rank(attacker, NemesisAssassinNodes.Family.COUP_DE_GRACE);
		boolean isPlayer = victim instanceof Player;
		boolean coupArmed = stepping && marked && coup > 0;

		if (coupArmed && isPlayer) {
			factor(frame, NemesisAssassinNodes.Family.COUP_DE_GRACE.nameKey(), true,
					Tuning.COUP_DE_GRACE_PLAYER_MULTIPLIER, note("player"));
			product *= Tuning.COUP_DE_GRACE_PLAYER_MULTIPLIER;
		} else if (coupArmed
				&& victim.getHealth() <= victim.getMaxHealth() * Tuning.COUP_DE_GRACE_THRESHOLD) {
			clamp(frame, NemesisAssassinNodes.Family.COUP_DE_GRACE.nameKey(),
					healthNote(victim, Tuning.COUP_DE_GRACE_THRESHOLD));
		} else {
			// The one the author already suspects: on a mob the x2 is simply not
			// on the table, and the execute wants the target under a third first.
			factor(frame, NemesisAssassinNodes.Family.COUP_DE_GRACE.nameKey(), false, 1.0F,
					coup <= 0 ? note("unowned")
							: !stepping ? notStepped
									: !marked ? note("unmarked") : note("player_only"));
		}

		boolean night = stepping && NightForm.isActive(attacker);
		float nightFactor = night ? Tuning.NIGHT_FORM_SHADOW_STEP_FACTOR : 1.0F;
		frame.lines.add(night
				? Component.translatable(KEY + "factor.on",
						Component.translatable(KEY + "factor.night_form"),
						num(nightFactor), note("night_form"))
				: Component.translatable(KEY + "factor.off",
						Component.translatable(KEY + "factor.night_form"),
						stepping ? note("no_night_form") : notStepped));
		product *= nightFactor;

		int flurry = AssassinNodes.rank(SubTree.ASSASSIN, owned,
				AssassinNodes.Family.SHADOW_FLURRY);
		float flurryFactor = stepping && flurry > 0 ? Tuning.SHADOW_FLURRY_MULTIPLIER : 1.0F;
		factor(frame, AssassinNodes.Family.SHADOW_FLURRY.nameKey(), stepping && flurry > 0,
				flurryFactor, flurry > 0 ? notStepped : note("unowned"));
		product *= flurryFactor;

		int fangs = AssassinNodes.rank(SubTree.ASSASSIN, owned, AssassinNodes.Family.TWIN_FANGS);
		float main = ModItems.daggerSwingDamage(attacker.getMainHandItem());
		float off = ModItems.daggerSwingDamage(attacker.getOffhandItem());
		boolean twinned = stepping && fangs > 0 && main > 0.0F && off > 0.0F;
		float fangsFactor = twinned ? 1.0F + Tuning.TWIN_FANGS_OFFHAND_FACTOR * off / main : 1.0F;
		factor(frame, AssassinNodes.Family.TWIN_FANGS.nameKey(), twinned, fangsFactor,
				fangs <= 0 ? note("unowned")
						: !stepping ? notStepped : note("offhand", num(off), num(main)));

		return product * fangsFactor;
	}

	/** Mirror of {@code archetypes$firstStrike}. */
	private static float explainFirstStrike(final Frame frame, final ServerPlayer attacker) {
		int rank = ShadowNodes.rank(SubTree.SHADOW, NodePurchases.owned(attacker, SubTree.SHADOW),
				ShadowNodes.Family.FIRST_STRIKE);
		float value = rank > 0 ? 1.0F + Tuning.FIRST_STRIKE_PER_RANK * rank : 1.0F;
		factor(frame, ShadowNodes.Family.FIRST_STRIKE.nameKey(), rank > 0, value, rankNote(rank));
		return value;
	}

	/**
	 * Mirror of {@code archetypes$sunderDamage}. Meteor is additive on the fall,
	 * not a multiplier, so the derived product folds it in against the incoming
	 * amount — which is why this one takes the frame's base rather than deriving
	 * from ranks alone.
	 */
	private static float explainSunder(final Frame frame, final ServerPlayer attacker,
			final LivingEntity victim) {
		Set<Integer> owned = NodePurchases.owned(attacker, SubTree.CRUSHER);
		WeaponClass weapon = WeaponClass.of(attacker);
		Long stamp = ((AttachmentTarget) attacker).getAttached(ModAttachments.SMASH_AT);
		boolean smashing = weapon == WeaponClass.MACE
				&& (attacker.fallDistance > Tuning.SMASH_MIN_FALL
						|| (stamp != null && attacker.level().getGameTime() - stamp <= 3));

		int meteor = CrusherNodes.rank(SubTree.CRUSHER, owned, CrusherNodes.Family.METEOR);
		boolean meteorFired = meteor > 0 && smashing;

		if (meteorFired) {
			// Flat, so it is reported as blocks fallen rather than as a factor —
			// and it makes the stage inexact, since a sum is not a product.
			clamp(frame, CrusherNodes.Family.METEOR.nameKey(),
					note("fall", num((float) attacker.fallDistance),
							num((float) attacker.fallDistance * Tuning.METEOR_PER_BLOCK_PER_RANK
									* meteor)));
		} else {
			factor(frame, CrusherNodes.Family.METEOR.nameKey(), false, 1.0F,
					meteor > 0 ? note("no_smash") : note("unowned"));
		}

		int rank = CrusherNodes.rank(SubTree.CRUSHER, owned, CrusherNodes.Family.SUNDER);
		int levels = rank * (weapon == WeaponClass.HANDS ? 2 : 1);
		float absorbed = armorAbsorption(victim);
		float sunderFactor = 1.0F + absorbed * Tuning.SUNDER_PER_LEVEL * levels;
		factor(frame, CrusherNodes.Family.SUNDER.nameKey(), rank > 0, sunderFactor,
				rank > 0 ? note("armour", num((float) victim.getAttributeValue(Attributes.ARMOR)),
						pct(absorbed)) : note("unowned"));
		return sunderFactor;
	}

	/** Vanilla's absorbed fraction for a victim's current armour points. */
	private static float armorAbsorption(final LivingEntity victim) {
		return Math.min(ARMOR_CAP,
				(float) victim.getAttributeValue(Attributes.ARMOR) * ARMOR_PER_POINT);
	}
}
