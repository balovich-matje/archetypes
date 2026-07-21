package com.archetypes;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import com.archetypes.mixin.LivingEntityAccessor;

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
 * {@code applyItemBlocking}, {@code getDamageAfterArmorAbsorb} and
 * {@code getDamageAfterMagicAbsorb} are observed at their RETURN through
 * {@link #observe}. Nothing here writes a damage number back; every entry point
 * takes floats and returns void.
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
 * <h2>The two alarms, and why there have to be two</h2>
 * They ask different questions and can fire independently on the same blow.
 *
 * <ul>
 * <li><b>{@code mismatch}</b> — "<em>my mirror disagrees with my handler.</em>"
 * Per stage, comparing the product this class re-derived against the
 * {@code before}/{@code after} the handler actually recorded. Both numbers are
 * this mod's. It catches a gate that moved in {@code LivingEntityMixin} and not
 * in the mirror above.</li>
 * <li><b>{@code unaccounted}</b> — "<em>somebody else is in this funnel.</em>"
 * Once per blow, comparing the product of every factor the trace <em>observed</em>
 * against what the victim demonstrably lost. Neither number is derived; both are
 * measured. It catches damage this mod never touched at all.</li>
 * </ul>
 *
 * <p>They cannot fight, because they share no term. The mismatch alarm reads
 * {@code derived}, which the unaccounted alarm never looks at; the unaccounted
 * alarm reads {@link Frame#clean} and the victim's health, which the mismatch
 * alarm never looks at. A stage whose derivation is inexact (an Executioner
 * clamp) silences the mismatch alarm for that stage only — {@link Frame#clean}
 * still takes the handler's recorded ratio, because a clamp is still an honestly
 * observed before/after even when it is not a multiplier.
 *
 * <h2>Why the unaccounted alarm exists</h2>
 * Because the trace was not wrong, it was <em>short</em>. A fully built Nemesis
 * ambush printed a chain ending at 126.94 and then killed a 500 HP Warden: a
 * sibling mod's own {@code hurtServer} handlers had multiplied the same
 * {@code amount} by another 4.5 in between, and nothing in this class was
 * positioned to notice. A dev tool that presents a partial chain as if it were
 * the whole chain is worse than no tool — the author used this one to verify a
 * damage model and it silently omitted the largest factor in the blow. Every
 * stage line it prints is still true; what it lacked was the ability to say
 * "and something I cannot name did the rest".
 *
 * <p>Server thread only, like everything that reads {@link MeleeSwing}.
 */
public final class DamageTrace {
	// --- stage keys, one per shaping handler on the hurtServer funnel ---

	/** {@code archetypes$greatswordDamage}: Heavy Blows, First Blood, Executioner. */
	public static final String STAGE_GREATSWORD = "greatsword";

	/** {@code archetypes$daggerDamage}: the whole Assassin/Nemesis Assassin chain. */
	public static final String STAGE_DAGGER = "dagger";

	// First Strike has no stage: it is Strength on the ATTACK_DAMAGE attribute
	// now, so it is already inside the amount this trace starts from, the same
	// way Blade Master and Bare-Knuckle are.

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

	/** Vanilla's own absorption steps, observed at their RETURN. */
	public static final String STAGE_ARMOUR = "armour";
	public static final String STAGE_PROTECTION = "protection";
	public static final String STAGE_BLOCKING = "blocking";

	/**
	 * Vanilla's damage-cooldown carry-over, the one accounted step nothing
	 * observes: inside a victim's i-frames {@code hurtServer} hands
	 * {@code actuallyHurt} only {@code amount - lastHurt}, with no method call
	 * around the subtraction to hang an injection on. It is reconstructed at the
	 * armour stage instead — see {@link #account}.
	 */
	public static final String STAGE_COOLDOWN = "cooldown";

	/** Above this relative gap the derived product is flagged as drifted. */
	private static final float MISMATCH_TOLERANCE = 0.01F;

	/**
	 * Above this relative gap the blow is flagged as having passed through
	 * something the trace never saw. Looser than {@link #MISMATCH_TOLERANCE},
	 * which judges one stage in isolation: this one judges a ratio carried
	 * through every stage of the chain, so it has a whole blow's worth of float
	 * rounding behind it rather than one multiply's.
	 */
	private static final float UNACCOUNTED_TOLERANCE = 0.02F;

	/** Below this, a damage figure is treated as zero and no ratio is formed. */
	private static final float EPSILON = 1.0E-3F;

	/** The flat 4%-a-point stand-in for vanilla's armour curve, capped at 80%.
	 * Not vanilla's curve — vanilla degrades armour by {@code damage/toughness}
	 * before applying it (see {@link ArmourMath}) — but Sunder is still written
	 * against this approximation, so its mirror has to be too or the alarm
	 * fires on a stage nobody changed. Flense no longer uses it. */
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

		/**
		 * The blow's level and source, kept because one mirror needs to ask
		 * vanilla a question rather than derive an answer: Blade Master's
		 * Protection bite is denominated in the victim's EPF, and EPF is read by
		 * {@code EnchantmentHelper.getDamageProtection(level, victim, source)}.
		 * A mirror that guessed at it instead would be the one thing this class
		 * exists to stop.
		 */
		private final ServerLevel level;
		private final DamageSource source;

		/** Cleared when a stage's derivation cannot be expressed as a product —
		 * an Executioner clamp is not a multiplier — so the mismatch alarm knows
		 * to hold its tongue for that stage. */
		private boolean exact = true;

		/**
		 * The victim's absorption at HEAD. Half of the landed-damage measurement:
		 * {@code actuallyHurt} spends absorption before health, so a blow that
		 * lands entirely on a golden-apple shield moves no health at all.
		 */
		private final float victimAbsorption;

		/**
		 * What vanilla will silently subtract before {@code actuallyHurt} because
		 * the victim is still inside its i-frames — its {@code lastHurt}, read at
		 * HEAD, or zero on the ordinary full-hit branch. Sampled here because by
		 * the time anything downstream could ask, {@code hurtServer} has already
		 * overwritten the field with this blow's amount.
		 */
		private final float cooldownFloor;

		/** Whether {@link #cooldownFloor} still has to be folded into the chain. */
		private boolean cooldownPending;

		/**
		 * The base carried through <em>only</em> the factors this trace observed:
		 * {@code base} times every recorded {@code after / before}. Any shaping
		 * done by a handler the trace does not know about is, by construction,
		 * absent from this product — which is exactly what makes it comparable
		 * against what actually landed.
		 */
		private float clean;

		/**
		 * The last number the trace watched vanilla carry, i.e. the most recent
		 * stage's {@code after}. Unlike {@link #clean} this one <em>does</em>
		 * include a stranger's work, because it is read off the funnel rather
		 * than accumulated. The pair is the whole measurement: {@code clean} is
		 * what the chain claims, {@code expected} is what the funnel holds.
		 */
		private float expected;

		private Frame(final @Nullable ServerPlayer attacker, final LivingEntity victim,
				final float base, final float cooldownFloor, final ServerLevel level,
				final DamageSource source) {
			this.attacker = attacker;
			this.victim = victim;
			this.base = base;
			this.level = level;
			this.source = source;
			this.victimHealth = victim.getHealth();
			this.victimAbsorption = victim.getAbsorptionAmount();
			this.cooldownFloor = cooldownFloor;
			this.cooldownPending = cooldownFloor > EPSILON;
			this.clean = base;
			this.expected = base;
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
		STACK.push(new Frame(attacker, victim, amount, cooldownFloor(source, victim), level, source));
	}

	/**
	 * How much of this blow vanilla will throw away for the victim's damage
	 * cooldown, decided exactly as {@code hurtServer} decides it a few lines
	 * further down: past ten ticks of {@code invulnerableTime}, and for a damage
	 * type that does not bypass the cooldown, only the excess over the last blow
	 * is passed on. Read at HEAD or not at all — the branch that uses it also
	 * overwrites {@code lastHurt} with this blow's amount.
	 */
	private static float cooldownFloor(final DamageSource source, final LivingEntity victim) {
		if (victim.invulnerableTime <= 10
				|| source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_COOLDOWN)) {
			return 0.0F;
		}

		return Math.max(0.0F, ((LivingEntityAccessor) victim).archetypes$getLastHurt());
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

		account(frame, stage, before, after);
	}

	/**
	 * The same as {@link #record}, for the vanilla steps {@code DamageTraceMixin}
	 * observes rather than owns. It takes the entity whose method was observed
	 * and drops the observation unless that entity is the innermost frame's
	 * victim: {@code applyItemBlocking} and the two absorption helpers are public
	 * and can be called from outside a {@code hurtServer} call, and an
	 * observation attributed to somebody else's blow would poison the very
	 * product the unaccounted alarm relies on.
	 */
	public static void observe(final LivingEntity self, final String stage, final float before,
			final float after) {
		if (!watching) {
			return;
		}

		Frame frame = STACK.peek();

		if (frame == null || frame.attacker == null || frame.victim != self) {
			return;
		}

		account(frame, stage, before, after);
	}

	/**
	 * Fold one stage into the frame: into the running products first, then into
	 * the printed chain.
	 *
	 * <p>The accounting runs ahead of the "nothing changed, print nothing" gate
	 * on purpose. A stage that multiplied by one still moves
	 * {@link Frame#expected}, and moving it is how a stranger's contribution is
	 * seen at all — on the Warden that started this, armour and Protection were
	 * both x1 and printed nothing, yet the number they were handed was already
	 * 4.5x what the chain had built.
	 */
	private static void account(final Frame frame, final String stage, final float before,
			final float after) {
		ServerPlayer attacker = frame.attacker;

		if (attacker == null) {
			return;
		}

		// The armour stage is the first thing inside actuallyHurt, so it is where
		// vanilla's i-frame subtraction has already happened and can be undone:
		// the amount the funnel really held was this stage's input plus the floor.
		if (frame.cooldownPending && STAGE_ARMOUR.equals(stage)) {
			float full = before + frame.cooldownFloor;

			if (before > EPSILON && full > EPSILON) {
				// A flat subtraction expressed as the factor it is, so that a
				// partial hit is an ACCOUNTED shrink rather than a stranger's.
				frame.clean *= before / full;
				frame.lines.add(stageLine(STAGE_COOLDOWN, full, before));
				frame.cooldownPending = false;
			}

			// Left pending on purpose when it could not be folded: a frame that
			// still owes a subtraction it cannot express is one the unaccounted
			// alarm declines to speak about at all.
		}

		if (before > EPSILON) {
			frame.clean *= after / before;
		}

		frame.expected = after;

		// A victim-side shaper that changed nothing is a node the victim does not
		// own; printing it would bury the chain in blanks. An attacker-side stage
		// that ran at all is worth a line even at x1 — that IS the finding.
		boolean unchanged = Math.abs(after - before) < 1.0E-4F;

		if (unchanged && !hasBreakdown(stage)) {
			return;
		}

		frame.lines.add(stageLine(stage, before, after));
		explain(frame, stage, attacker, frame.victim, before, after);
	}

	/**
	 * Close the blow and print it. Called at {@code hurtServer}'s RETURN, where
	 * the victim's health is whatever the hit left them with.
	 *
	 * <p>Pops until it removes <em>this</em> victim's frame, which is how a leaked
	 * frame from a hit some other injection cancelled at HEAD gets cleared out
	 * instead of being mistaken for the caller's own.
	 *
	 * @param hurt {@code hurtServer}'s own return value, forwarded so the
	 *     unaccounted alarm can tell "nothing landed" from "nothing was left".
	 */
	public static void finish(final LivingEntity victim, final boolean hurt) {
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

		Component gap = unaccounted(frame, victim, hurt);

		for (Component line : frame.lines) {
			attacker.sendSystemMessage(line);
		}

		if (gap != null) {
			attacker.sendSystemMessage(gap);
		}

		attacker.sendSystemMessage(Component.translatable(KEY + "result",
				num(frame.victimHealth - victim.getHealth()),
				num(victim.getHealth()), num(victim.getMaxHealth()))
						.withStyle(ChatFormatting.DARK_GRAY));
	}

	// --- the unaccounted alarm ---

	/**
	 * The line that says the chain above is short, or null when it is not.
	 *
	 * <h2>Where "what landed" is sampled, and why there</h2>
	 * Health delta alone is the obvious candidate and the wrong one: it reads
	 * zero for a blow a golden apple ate whole, so every absorbed hit would be
	 * denounced as a x0.00 reduction by a phantom mod. The sample taken here is
	 * the drop in <em>health plus absorption</em> across the call, because that
	 * is precisely the pair {@code actuallyHurt} spends — it subtracts what it
	 * can from absorption, then puts the remainder through {@code setHealth} —
	 * and {@code setAbsorptionAmount} clamps at zero, so the two deltas sum to
	 * the damage applied without ever double-counting the overlap.
	 *
	 * <p>That sample is exact except at one edge, and the edge is a floor rather
	 * than a fog: {@code setHealth} clamps at zero, so a blow that kills reports
	 * only the health the victim had. When it is hit — the victim is dead or
	 * dying, or the blow consumed their whole health-plus-absorption pool — the
	 * measurement is replaced with {@link Frame#expected}, the last number the
	 * trace watched the funnel carry, and the line is printed as a bound rather
	 * than a value. The 500 HP Warden that provoked all this is exactly this
	 * case: 500 lost, but Protection had already been handed 571.
	 *
	 * <h2>What is deliberately not counted</h2>
	 * Everything the trace already prints as a stage. Vanilla's armour and
	 * Protection steps are observed at their own RETURN and are therefore in
	 * {@link Frame#clean} like any other factor, so they cancel out of the ratio
	 * instead of masquerading as a stranger. So is shield blocking, and so is the
	 * i-frame carry-over, reconstructed in {@link #account}. What is left over
	 * after all of those is, by elimination, somebody else's.
	 */
	private static @Nullable Component unaccounted(final Frame frame, final LivingEntity victim,
			final boolean hurt) {
		// hurtServer said no: invulnerable, already dying, fire-immune, or inside
		// i-frames with nothing to spare. Nothing landed and nothing is missing.
		if (!hurt) {
			return null;
		}

		// The i-frame subtraction was never reconciled because the armour step
		// never ran, so the chain is short by an amount nobody can attribute.
		// Silence beats a number that would be wrong in a knowable direction.
		if (frame.cooldownPending || frame.clean <= EPSILON) {
			return null;
		}

		float applied = Math.max(0.0F, (frame.victimHealth - victim.getHealth())
				+ (frame.victimAbsorption - victim.getAbsorptionAmount()));
		float pool = frame.victimHealth + frame.victimAbsorption;

		// Three ways to know the measurement bottomed out. The third is the one
		// that earns its keep: a totem fires from inside hurtServer, sets health
		// back to 1 and hands out Absorption II, so by RETURN the victim is
		// neither dead nor visibly emptied and the delta reads near zero — but
		// the funnel was demonstrably carrying more than the victim owned.
		boolean floored = victim.isDeadOrDying() || applied >= pool - EPSILON
				|| frame.expected >= pool - EPSILON;
		float landed = floored ? Math.max(applied, frame.expected) : applied;
		float gap = landed / frame.clean;

		if (Math.abs(gap - 1.0F) <= UNACCOUNTED_TOLERANCE) {
			return null;
		}

		return Component.translatable(KEY + (floored ? "unaccounted_least" : "unaccounted"),
				num(frame.clean), num(landed), num(gap),
				note(gap > 1.0F ? "unaccounted_up" : "unaccounted_down"))
						.withStyle(ChatFormatting.GOLD);
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

	/**
	 * One ambush-box term's line. Same two templates as {@link #factor}, but
	 * printed as {@code +0.50} rather than {@code x1.50}, because that is what
	 * the number is now: a summand in one box that is multiplied once. Printing
	 * a box term as a multiplier would be the trace lying about the shape of
	 * the arithmetic even while its total came out right.
	 */
	private static void bonus(final Frame frame, final String nameKey, final boolean fired,
			final float amount, final Component note) {
		frame.lines.add(fired
				? Component.translatable(KEY + "factor.add",
						Component.translatable(nameKey), num(amount), note)
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
				|| STAGE_SUNDER.equals(stage);
	}

	private static void explain(final Frame frame, final String stage, final ServerPlayer attacker,
			final LivingEntity victim, final float before, final float after) {
		float derived = switch (stage) {
			case STAGE_GREATSWORD -> explainGreatsword(frame, attacker, victim, before);
			case STAGE_DAGGER -> explainDagger(frame, attacker, victim, before);
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

	/**
	 * Mirror of {@code archetypes$greatswordDamage}. Takes the incoming amount
	 * because the last stage needs it: Blade Master, like Flense, is computed
	 * against the damage the rest of the chain produced rather than off ranks
	 * alone.
	 */
	private static float explainGreatsword(final Frame frame, final ServerPlayer attacker,
			final LivingEntity victim, final float before) {
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
		// What Blade Master will actually be handed. Normally the chain's own
		// product, but the Executioner clamp is a floor rather than a factor, so
		// on an execute it is the clamp that Blade Master compensates — and a
		// trace that reported the pre-clamp factor would understate by an order
		// of magnitude on precisely the blow the author would be inspecting.
		float chained = before * product;

		if (executioner > 0 && finishable) {
			clamp(frame, SlayerNodes.Family.EXECUTIONER.nameKey(),
					healthNote(victim, Tuning.EXECUTE_THRESHOLD));
			chained = Math.max(chained, victim.getHealth() + 100.0F);
		} else {
			factor(frame, SlayerNodes.Family.EXECUTIONER.nameKey(), false, 1.0F,
					executioner > 0 ? healthNote(victim, Tuning.EXECUTE_THRESHOLD) : note("unowned"));
		}

		return product * explainBladeMaster(frame, attacker, victim, chained);
	}

	/**
	 * Mirror of the Blade Master stage, which the handler runs LAST and against
	 * the damage the rest of the chain produced — so this one takes that number
	 * rather than deriving it from ranks, exactly like {@link #explainFlense}.
	 *
	 * <p>The note reports BOTH mitigations side by side, because the node
	 * answers both and reading only one of them is how "cut through full
	 * enchanted netherite" gets believed too early: the armour share at this
	 * blow's magnitude with and without the ignored plate, then the victim's
	 * clamped EPF and what is left of it after the bite.
	 */
	private static float explainBladeMaster(final Frame frame, final ServerPlayer attacker,
			final LivingEntity victim, final float damage) {
		int rank = ColossusSlayer.rank(attacker, ColossusSlayerNodes.Family.BLADE_MASTER);

		if (rank <= 0) {
			factor(frame, ColossusSlayerNodes.Family.BLADE_MASTER.nameKey(), false, 1.0F,
					note("unowned"));
			return 1.0F;
		}

		float ignore = Math.min(1.0F, Tuning.BLADE_MASTER_ARMOUR_IGNORE_PER_RANK * rank);
		float armour = ArmourMath.armour(victim);
		float protection = ColossusSlayer.protectionPoints(frame.level, victim, frame.source);
		float bitten = Math.max(0.0F,
				protection - Tuning.BLADE_MASTER_PROTECTION_BITE_PER_RANK * rank);
		float value = ColossusSlayer.bladeMasterFactor(attacker, frame.level, victim, frame.source,
				damage);
		factor(frame, ColossusSlayerNodes.Family.BLADE_MASTER.nameKey(), true, value,
				note("blade_master", num(armour), pct(ignore),
						pct(ArmourMath.mitigation(victim, damage, armour)),
						pct(ArmourMath.mitigation(victim, damage, armour * (1.0F - ignore))),
						num(protection), num(bitten)));
		return value;
	}

	/**
	 * Mirror of {@code archetypes$daggerDamage} — the long one, and the reason
	 * this class exists. Everything past the Shadow Step gate only ever fires on
	 * the strike the blink delivers, so on an ordinary swing those lines all read
	 * "not a Shadow Step strike", which is itself the answer to "why was that hit
	 * small".
	 */
	private static float explainDagger(final Frame frame, final ServerPlayer attacker,
			final LivingEntity victim, final float before) {
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

		boolean marked = DeathMark.isMarkedBy(victim, attacker) && DeathMark.hasMark(attacker);
		float ambush = 1.0F;

		float markBonus = marked ? Tuning.DEATH_MARK_DAMAGE_FACTOR : 0.0F;
		bonus(frame, NemesisAssassinNodes.Family.DEATH_MARK.nameKey(), marked, markBonus,
				note(marked ? "marked" : "unmarked"));
		ambush += markBonus;

		int headhunter = NemesisAssassinNodes.rank(attacker,
				NemesisAssassinNodes.Family.HEADHUNTER);
		float huntBonus = marked ? Tuning.HEADHUNTER_PER_RANK * headhunter : 0.0F;
		bonus(frame, NemesisAssassinNodes.Family.HEADHUNTER.nameKey(),
				marked && headhunter > 0, huntBonus,
				headhunter > 0 ? note(marked ? "marked" : "unmarked") : note("unowned"));
		ambush += huntBonus;

		Long stepStrike = ((AttachmentTarget) attacker).getAttached(ModAttachments.STEP_STRIKE_AT);
		boolean stepping = stepStrike != null && stepStrike == attacker.level().getGameTime();

		ambush += explainStepStrike(frame, attacker, victim, owned, marked, stepping);

		// The one line that says what the collapse bought: every term above is
		// a summand, and this is the single multiply they all pay into.
		frame.lines.add(Component.translatable(KEY + "factor.box", num(ambush),
				note("ambush")));
		product *= ambush;

		return product * explainFlense(frame, attacker, victim, owned, before * product);
	}

	/**
	 * The Shadow Step half of the ambush box. Split out because every one of
	 * these is gated twice — once on the blink, once on its own node — and the
	 * pair is what the author is looking for. Returns the SUM of its terms, not
	 * a product: the caller adds it into the same box the mark terms went into.
	 */
	private static float explainStepStrike(final Frame frame, final ServerPlayer attacker,
			final LivingEntity victim, final Set<Integer> owned, final boolean marked,
			final boolean stepping) {
		float sum = 0.0F;
		Component notStepped = note("not_step");

		int coup = NemesisAssassinNodes.rank(attacker, NemesisAssassinNodes.Family.COUP_DE_GRACE);
		boolean isPlayer = victim instanceof Player;
		boolean coupArmed = stepping && marked && coup > 0;

		if (coupArmed && isPlayer) {
			bonus(frame, NemesisAssassinNodes.Family.COUP_DE_GRACE.nameKey(), true,
					Tuning.COUP_DE_GRACE_PLAYER_BONUS, note("player"));
			sum += Tuning.COUP_DE_GRACE_PLAYER_BONUS;
		} else if (coupArmed
				&& victim.getHealth() <= victim.getMaxHealth() * Tuning.COUP_DE_GRACE_THRESHOLD) {
			clamp(frame, NemesisAssassinNodes.Family.COUP_DE_GRACE.nameKey(),
					healthNote(victim, Tuning.COUP_DE_GRACE_THRESHOLD));
		} else {
			// The one the author already suspects: on a mob the player bonus is
			// simply not on the table, and the execute wants the target under a
			// third first.
			bonus(frame, NemesisAssassinNodes.Family.COUP_DE_GRACE.nameKey(), false, 0.0F,
					coup <= 0 ? note("unowned")
							: !stepping ? notStepped
									: !marked ? note("unmarked") : note("player_only"));
		}

		// No night-form line: the Dark Ritual no longer weighs on the box, so
		// there is nothing here for a trace to report either way.

		int flurry = AssassinNodes.rank(SubTree.ASSASSIN, owned,
				AssassinNodes.Family.SHADOW_FLURRY);
		float flurryBonus = stepping && flurry > 0 ? Tuning.SHADOW_FLURRY_BONUS : 0.0F;
		// Three-way, like Twin Fangs below it: unowned, owned-but-not-stepping,
		// and fired. The two-way version claimed "not a Shadow Step strike" on
		// the line where it HAD fired off a Shadow Step, which is the one thing
		// a trace must never do — contradict its own verdict.
		bonus(frame, AssassinNodes.Family.SHADOW_FLURRY.nameKey(), stepping && flurry > 0,
				flurryBonus, flurry <= 0 ? note("unowned")
						: !stepping ? notStepped : note("stepped"));
		sum += flurryBonus;

		int fangs = AssassinNodes.rank(SubTree.ASSASSIN, owned, AssassinNodes.Family.TWIN_FANGS);
		float main = ModItems.daggerSwingDamage(attacker.getMainHandItem());
		float off = ModItems.daggerSwingDamage(attacker.getOffhandItem());
		boolean twinned = stepping && fangs > 0 && main > 0.0F && off > 0.0F;
		float fangsBonus = twinned ? Tuning.TWIN_FANGS_OFFHAND_BONUS * off / main : 0.0F;
		bonus(frame, AssassinNodes.Family.TWIN_FANGS.nameKey(), twinned, fangsBonus,
				fangs <= 0 ? note("unowned")
						: !stepping ? notStepped : note("offhand", num(off), num(main)));

		return sum + fangsBonus;
	}

	/**
	 * Mirror of the Flense stage, which the handler runs LAST and against the
	 * damage the rest of the chain produced — so this one takes that number
	 * rather than deriving it from ranks, and reports the armour curve at that
	 * magnitude beside the share being ignored. Reading a Flense line on a big
	 * hit and a small one back to back is the clearest statement of what the
	 * node now is: the same 60% of armour ignored both times, worth very little
	 * on the blow that had already flattened the armour and roughly double on
	 * the one that had not.
	 */
	private static float explainFlense(final Frame frame, final ServerPlayer attacker,
			final LivingEntity victim, final Set<Integer> owned, final float damage) {
		int flense = AssassinNodes.rank(SubTree.ASSASSIN, owned, AssassinNodes.Family.FLENSE);

		if (flense <= 0) {
			factor(frame, AssassinNodes.Family.FLENSE.nameKey(), false, 1.0F, note("unowned"));
			return 1.0F;
		}

		float ignore = Math.min(1.0F, Tuning.FLENSE_ARMOUR_IGNORE_PER_RANK * flense);
		float armour = ArmourMath.armour(victim);
		float value = ArmourMath.ignoreArmourFactor(victim, damage, ignore);
		factor(frame, AssassinNodes.Family.FLENSE.nameKey(), true, value,
				note("flense", num(armour), pct(ignore),
						pct(ArmourMath.mitigation(victim, damage, armour)),
						pct(ArmourMath.mitigation(victim, damage, armour * (1.0F - ignore)))));
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
