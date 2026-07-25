package com.archetypes;

import com.archetypes.ColossusSlayerNodes.Family;
import com.archetypes.mixin.LivingEntityAccessor;

import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.AbstractWindCharge;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/**
 * Every node of the epic Colossus-Slayer tree. The sketch rebuilt this tree
 * around a parry — "much more skill expression in PVP fights than a damage
 * buffing skill" — so the whole tree is one input and what that input is
 * worth: Parry in the middle, the two bodies that earn it below (Barbarian,
 * Blade Master) and the three ways it pays above (Riposte, Stalwart, Spell
 * Reflect).
 *
 * <h2>The parry, end to end</h2>
 * <ol>
 * <li>The ability key goes down and the client sends the same
 *     {@code ActiveAbilityPayload} every active rides — slot 5, the Colossus
 *     Slayer's place among the epic keys. The dispatch in {@code Archetypes}
 *     hands a Brawler's slot 5 to {@link #parry}. It was an attack+block combo
 *     in the first implementation; that read the attack and use keys and so
 *     stood between the player and normal weapon use, which is why it is a key
 *     of its own now.</li>
 * <li>{@link #parry} validates the node, the archetype, the blade and the
 *     cooldown, and stamps a {@link Tuning#PARRY_WINDOW_TICKS}-tick window.</li>
 * <li>A hit arriving inside the window is a parry: {@link #tryParry}, hung off
 *     the victim's {@code hurtServer} intake next to Ghost Form and Sidestep,
 *     voids it and pays. A spell arriving inside the window never reaches
 *     {@code hurtServer} at all — {@link #parriesSpell} answers vanilla's own
 *     {@code Entity.deflection} and the shot turns around before it lands.</li>
 * <li>{@link #tickParry} lapses an unpaid window and starts the cooldown.</li>
 * </ol>
 *
 * <h2>What a guess costs</h2>
 * Read wrong, {@link Tuning#PARRY_COOLDOWN_TICKS} — eight seconds off the key,
 * and nothing done to the swing at all. Read right, a fraction of that:
 * {@link #pay} stamps {@link Tuning#PARRY_SUCCESS_GREATSWORD_COOLDOWN_TICKS} or
 * {@link Tuning#PARRY_SUCCESS_SWORD_COOLDOWN_TICKS} depending on the blade, so
 * the key comes back in a second or two rather than the very next tick. It used
 * to come back free, and the two things that broke are named on the first of
 * those constants: a Colossus in a mob pack could re-open the window on every
 * incoming blow, and two of them could parry-lock each other forever.
 *
 * <p>So only the success side touches vanilla's {@code attackStrengthTicker},
 * and it fills it to full charge: that is the node's "no swing cooldown" half,
 * a reward rather than the other end of a punishment. The server writes its own
 * copy and sends the value to the client as a {@link ParrySwingPayload} rather
 * than letting the client derive it — only the server knows whether the hit
 * that pays for a parry ever arrived.
 */
public final class ColossusSlayer {
	/**
	 * What Barbarian counts as magic. A data tag rather than a list in code:
	 * the author's note enumerates sources ("any of the seeker/oracle spells,
	 * blaze fireballs, ender dragon's lingering breath") and a tag is where
	 * that enumeration belongs. Every spell this mod throws arrives as
	 * {@code indirect_magic}, so the mod's own schools need no entry of their
	 * own.
	 */
	public static final TagKey<DamageType> MAGICAL =
			TagKey.create(Registries.DAMAGE_TYPE, Archetypes.id("magical"));

	private static final Identifier BLADE_MASTER_SPEED_ID = Archetypes.id("blade_master_speed");

	/**
	 * True while a parry is paying itself out, so the riposte's own damage can
	 * never be parried in turn — {@code SlayerCombat.dancing}'s shape, and the
	 * same reason: the payout calls {@code hurtServer} from inside a
	 * {@code hurtServer} hook.
	 */
	private static boolean parrying;

	/**
	 * True while a heal that Barbarian should cut is being applied. Healing has
	 * no {@code DamageSource} to read, so magic is declared at the call site
	 * instead: vanilla's potion effects through the two mixins named in
	 * {@code archetypes.mixins.json}, this mod's Priest and Oracle heals
	 * through {@link #magicalHeal}. Everything not marked — food, natural
	 * regeneration, Taste of Blood — is mundane and passes through whole,
	 * which is the author's "healing from the food or brawler/colossus nodes
	 * unaffected".
	 */
	private static boolean magicalHealing;

	private ColossusSlayer() {
	}

	public static int rank(final Player player, final Family family) {
		return ColossusSlayerNodes.rank(player, family);
	}

	public static void initialize() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				// Run for every player, not just Brawlers: revoking Blade
				// Master is this call's job, and an archetype dropped by
				// Amnesia II or the creative reset would otherwise skip the
				// player forever and strand both modifiers (AgilityTicker's
				// lesson, and RadianceAura's before it).
				tickStance(player);
				tickParry(player);
			}
		});
	}

	// ------------------------------------------------------------------
	// Blade Master — armour on both blades, and a slower greatsword.
	// ------------------------------------------------------------------

	/**
	 * Blade Master's swing-time half — a PENALTY now, not a bonus — asserted and
	 * revoked every tick in {@code SlayerTicker.tickStance}'s shape so no respec
	 * can leave the modifier standing.
	 *
	 * <p>An attribute rather than a damage hook because it has to be one:
	 * {@code getCurrentItemAttackStrengthDelay} reads {@code ATTACK_SPEED} and
	 * nothing else. The number is carried straight through rather than converted,
	 * which is the second half of the change: {@link
	 * Tuning#BLADE_MASTER_SWING_PENALTY_PER_RANK} is denominated in attack speed,
	 * exactly like {@link Tuning#HEAVY_PER_RANK} on the same attribute two nodes
	 * down the tree. Vanilla applies each {@code ADD_MULTIPLIED_TOTAL} in turn, so
	 * the two compound rather than cancelling — which is the point: Heavy Blows'
	 * trade is no longer refunded by the epic node above it.
	 *
	 * <p>Clamped at -90% so no future rank count can drive the attribute to zero
	 * and the swing timer to infinity; rank 2 is -40% and nowhere near it.
	 *
	 * <p>Greatsword only, deliberately. The node's armour half reaches ordinary
	 * swords ({@link #bladeMasterFactor}) and this half does not.
	 *
	 * <p>The node's third half used to be +20% sword ATTACK_DAMAGE per rank and
	 * is gone; see {@link #bladeMasterFactor} for what replaced it and
	 * {@link Tuning#BLADE_MASTER_ARMOUR_IGNORE_PER_RANK} for why the swap. There
	 * is deliberately no revoke path left for the retired damage modifier:
	 * {@code addTransientModifier} is never saved to disk, so no live attribute
	 * map can carry one across the restart this change requires — and the same
	 * fact is why the speed modifier's flipped sign needs no migration either.
	 */
	private static void tickStance(final ServerPlayer player) {
		int rank = rank(player, Family.BLADE_MASTER);
		ItemStack held = player.getMainHandItem();
		double penalty = Math.min(0.9, Tuning.BLADE_MASTER_SWING_PENALTY_PER_RANK * rank);

		stance(player.getAttribute(Attributes.ATTACK_SPEED), BLADE_MASTER_SPEED_ID,
				rank > 0 && ModItems.isGreatsword(held), -penalty);
	}

	/**
	 * Blade Master's armour half: the multiplier that makes a greatsword blow
	 * land as though the victim wore {@code 1 - ignore} of their armour AND
	 * {@code bite} fewer points of enchantment protection.
	 *
	 * <h2>Two mitigations, not one</h2>
	 * "Cut through full enchanted netherite" is two separate stages of
	 * {@code LivingEntity.actuallyHurt} and they need different answers:
	 * <ul>
	 * <li>{@code getDamageAfterArmorAbsorb} → {@code CombatRules
	 *     .getDamageAfterAbsorb}. Against a Colossus with Ironclad (ARMOR 30,
	 *     TOUGHNESS 20, so {@code t = 7}) this is {@code realArmour = clamp(30 -
	 *     d/7, 6, 20)}, which pins at the 20 ceiling for every hit under 70 raw
	 *     — a flat x0.20 whatever you swing. Armour ignore reaches this one.</li>
	 * <li>{@code getDamageAfterMagicAbsorb} → {@code CombatRules
	 *     .getDamageAfterMagicAbsorb}. Protection IV in four slots is 16 EPF, a
	 *     flat {@code x(1 - 16/25) = x0.36}, and NO amount of armour penetration
	 *     touches it. Vanilla lets exactly one thing out of that stage, the
	 *     {@code #minecraft:bypasses_enchantments} tag, and a tag is a property
	 *     of a damage TYPE — it cannot be conditioned on the attacker's nodes,
	 *     which is precisely what a node has to be.</li>
	 * </ul>
	 *
	 * <h2>The instrument</h2>
	 * Both halves are pre-compensated in front of vanilla rather than changed
	 * inside it, which is {@code ArmourMath}'s whole reason to exist and what
	 * Flense already does. The armour stage is inverted exactly (it is monotone,
	 * so the inverse is single-valued); the magic stage is a plain linear
	 * multiply, so its compensation is the ratio of the two multipliers.
	 *
	 * <p>The alternatives, and why not:
	 * <ul>
	 * <li>A real {@code armor_effectiveness} enchantment (Breach's instrument)
	 *     is the vanilla-shaped answer to the FIRST stage only, and it lives on
	 *     the item. Stamping one onto a player's own greatsword would follow the
	 *     sword out of their hands; and it would still leave x0.36 standing.</li>
	 * <li>A partial {@code bypasses_enchantments} does not exist — the tag is
	 *     all or nothing, and putting a greatsword's ordinary
	 *     {@code player_attack} in it would delete Protection for every attacker
	 *     in the game, ours or not.</li>
	 * </ul>
	 * So the EPF reduction is ours, and it is a subtraction rather than a
	 * bypass: half a Protection IV suit at rank 2, nothing at all against a
	 * target wearing none.
	 *
	 * @param damage the damage the blow would carry WITHOUT this node — every
	 *     other shaper's work and none of this one's, for {@code ArmourMath
	 *     .ignoreArmourFactor}'s reason: the share of armour vanilla will
	 *     actually apply depends on how big the hit is, so feeding the
	 *     post-compensation number back in would be circular. That is why the
	 *     stage runs LAST in {@code archetypes$greatswordDamage}.
	 */
	public static float bladeMasterFactor(final Player player, final ServerLevel level,
			final LivingEntity victim, final DamageSource source, final float damage) {
		int rank = rank(player, Family.BLADE_MASTER);

		if (rank <= 0 || damage <= 0.0F) {
			return 1.0F;
		}

		float armour = ArmourMath.armour(victim);
		float t = ArmourMath.toughnessTerm(victim);
		float ignore = Math.min(1.0F, Tuning.BLADE_MASTER_ARMOUR_IGNORE_PER_RANK * rank);

		// Where the blow would land if the plate were thinner. afterArmour hands
		// back `damage` untouched for an unarmoured victim, so this is a no-op
		// against one rather than a special case.
		float ideal = ArmourMath.afterArmour(damage, armour * (1.0F - ignore), t);

		// ...and where it would land if the Protection were weaker. Vanilla
		// clamps EPF to [0, 20] before dividing by 25, so the clamp comes first
		// and the bite is taken out of the value that will actually be spent.
		float protection = Mth.clamp(
				EnchantmentHelper.getDamageProtection(level, victim, source), 0.0F, 20.0F);
		float bitten = Math.max(0.0F,
				protection - Tuning.BLADE_MASTER_PROTECTION_BITE_PER_RANK * rank);
		// Both denominators are >= 1 - 20/25 = 0.2, so neither can be zero.
		ideal *= (1.0F - bitten / 25.0F) / (1.0F - protection / 25.0F);

		return ArmourMath.rawForAfterArmour(ideal, armour, t) / damage;
	}

	/** The victim's clamped EPF, for the trace — so a reader can see what the
	 * bite is being taken out of instead of a constant. */
	public static float protectionPoints(final ServerLevel level, final LivingEntity victim,
			final DamageSource source) {
		return Mth.clamp(EnchantmentHelper.getDamageProtection(level, victim, source), 0.0F, 20.0F);
	}

	private static void stance(final AttributeInstance attribute, final Identifier id,
			final boolean should, final double value) {
		if (attribute == null) {
			return;
		}

		if (should) {
			// AttributeModifier is immutable and the value moves with the rank,
			// so a standing modifier is replaced rather than left alone.
			AttributeModifier existing = attribute.getModifier(id);

			if (existing == null || existing.amount() != value) {
				attribute.removeModifier(id);
				attribute.addTransientModifier(new AttributeModifier(id, value,
						AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			}
		} else if (attribute.hasModifier(id)) {
			attribute.removeModifier(id);
		}
	}

	// ------------------------------------------------------------------
	// Barbarian — the magic half of everything, cut.
	// ------------------------------------------------------------------

	/** Barbarian's damage half: a victim-side share of any magical hit. */
	public static float barbarianDamage(final Player player, final DamageSource source,
			final float amount) {
		int rank = rank(player, Family.BARBARIAN);

		if (rank <= 0 || !source.is(MAGICAL)) {
			return amount;
		}

		if (player instanceof ServerPlayer server) {
			ProcIndicators.send(server, SubTree.COLOSSUS_SLAYER, Family.BARBARIAN);
		}

		return amount * Math.max(0.0F, 1.0F - Tuning.BARBARIAN_MAGIC_CUT_PER_RANK * rank);
	}

	/** Barbarian's healing half, on the same per-rank share. */
	public static float barbarianHealing(final Player player, final float amount) {
		int rank = rank(player, Family.BARBARIAN);
		return rank <= 0 || !magicalHealing ? amount
				: amount * Math.max(0.0F, 1.0F - Tuning.BARBARIAN_MAGIC_CUT_PER_RANK * rank);
	}

	/** Heal that Barbarian is allowed to cut — this mod's own spell healing. */
	public static void magicalHeal(final LivingEntity target, final float amount) {
		beginMagicalHealing();
		try {
			target.heal(amount);
		} finally {
			endMagicalHealing();
		}
	}

	/** For the potion mixins, which cannot wrap a call they do not make. */
	public static void beginMagicalHealing() {
		magicalHealing = true;
	}

	public static void endMagicalHealing() {
		magicalHealing = false;
	}

	// ------------------------------------------------------------------
	// The window.
	// ------------------------------------------------------------------

	/**
	 * Whether this player could parry right now: the archetype, the node, and a
	 * blade to parry with. The weapon gate is the same {@code ModItems} pair the
	 * riposte branches on, so nothing can open a window it has no answer for.
	 */
	private static boolean canParry(final Player player) {
		if (ModAttachments.get(player) != Archetype.STRENGTH
				|| rank(player, Family.PARRY) <= 0) {
			return false;
		}

		ItemStack held = player.getMainHandItem();
		return ModItems.isSword(held) || ModItems.isGreatsword(held);
	}

	/**
	 * The ability key: open the window. Ignored while one is already open — a
	 * held key is one parry, not sixty — and while the miss cooldown runs.
	 */
	public static void parry(final ServerPlayer player) {
		if (!canParry(player) || isWindowOpen(player) || onCooldown(player)) {
			return;
		}

		AttachmentTarget target = (AttachmentTarget) player;
		target.setAttached(ModAttachments.PARRY_UNTIL,
				player.level().getGameTime() + Tuning.PARRY_WINDOW_TICKS);
	}

	/** Whether the miss cooldown is still running. Re-checked server-side even
	 * though the cooldown bar shows it: the stamp is the only fence there is,
	 * and a client that presses every tick would otherwise stand in a permanent
	 * window. */
	private static boolean onCooldown(final Player player) {
		Long readyAt = ((AttachmentTarget) player).getAttached(ModAttachments.PARRY_READY_AT);
		return readyAt != null && player.level().getGameTime() < readyAt;
	}

	/** Drop a running window and its cooldown — the respec path. */
	public static void clearWindow(final ServerPlayer player) {
		AttachmentTarget target = (AttachmentTarget) player;
		target.removeAttached(ModAttachments.PARRY_UNTIL);
		target.removeAttached(ModAttachments.PARRY_READY_AT);
	}

	private static boolean isWindowOpen(final Player player) {
		Long until = ((AttachmentTarget) player).getAttached(ModAttachments.PARRY_UNTIL);
		return until != null && player.level().getGameTime() < until;
	}

	/** The window lapsed unpaid: start the cooldown. The whole price of a wrong
	 * guess — the swing is not touched, so a miss costs the key and not the
	 * fight. */
	private static void tickParry(final ServerPlayer player) {
		AttachmentTarget target = (AttachmentTarget) player;
		Long until = target.getAttached(ModAttachments.PARRY_UNTIL);

		if (until == null || player.level().getGameTime() < until) {
			return;
		}

		closeWindow(player);
		target.setAttached(ModAttachments.PARRY_READY_AT,
				player.level().getGameTime() + Tuning.PARRY_COOLDOWN_TICKS);

		// The whiff: the only thing that tells the player the guess was wrong
		// before the cooldown tile starts draining.
		((ServerLevel) player.level()).playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.PLAYER_ATTACK_NODAMAGE, SoundSource.PLAYERS, 0.7F, 0.6F);
	}

	/** Drops the window without touching the cooldown, so the paid and unpaid
	 * ends can each write their own. */
	private static void closeWindow(final ServerPlayer player) {
		((AttachmentTarget) player).removeAttached(ModAttachments.PARRY_UNTIL);
	}

	// ------------------------------------------------------------------
	// The melee parry.
	// ------------------------------------------------------------------

	/**
	 * A hit landing on a player with an open window. Returns true when the hit
	 * is parried and must be voided.
	 *
	 * <p>Two things are parryable. A melee blow — the attacker IS the damage,
	 * {@code SlayerCombat}'s sidestep test — is what the node sells. Magical
	 * damage with no projectile to turn around is the author's area case: with
	 * Spell Reflect bought, "AOE spells will have their damage negated still,
	 * but not send the spell back at the caster". A directed spell never
	 * reaches here at all; see {@link #parriesSpell}.
	 */
	public static boolean tryParry(final ServerPlayer player, final ServerLevel level,
			final DamageSource source, final float amount) {
		if (parrying || !isWindowOpen(player)) {
			return false;
		}

		Entity direct = source.getDirectEntity();
		boolean melee = direct instanceof LivingEntity && direct == source.getEntity();
		boolean areaSpell = !melee && source.is(MAGICAL)
				&& rank(player, Family.SPELL_REFLECT) > 0;

		if (!melee && !areaSpell) {
			return false;
		}

		closeWindow(player);
		parrying = true;

		try {
			if (melee) {
				riposte(player, level, (LivingEntity) direct, amount);
			}
		} finally {
			parrying = false;
		}

		pay(player, level);
		return true;
	}

	/**
	 * What a parried melee blow costs its owner. A greatsword answers with the
	 * Decimate the author asked for — free, and legal even mid-cooldown, so a
	 * parry is never the swing you could not afford. A sword answers the way
	 * the description says: the blow comes back, and then you hit them anyway.
	 *
	 * <p>The swing timer is filled BEFORE the riposte, not only after, or the
	 * "normal attack" would land at whatever fraction of charge the parry
	 * press left behind — vanilla scales {@code Player.attack} by the ticker.
	 *
	 * <p>A greatsword build that took Bladestorm instead of Decimate has no
	 * Decimate to cast; rather than swallow the parry it falls through to the
	 * sword's answer, which is the only other thing a parry can honestly do.
	 * The same fall-through now covers a second case: {@code SlayerActives
	 * .decimate(player, true)} reports whether the free cast was accepted, and
	 * it refuses one that is inside {@link Tuning#DECIMATE_FREE_COOLDOWN_TICKS}.
	 * That clock is not the key's — the author's "automatically cast Decimates
	 * will not incur a cooldown, and can happen while the skill is already on
	 * cooldown" still holds exactly — it is the fence that stops a parry, which
	 * refunds itself on success in {@link #pay}, from being an unbounded
	 * armour-ignoring nuke fired by being attacked often enough.
	 *
	 * <p>The riposte's Decimate is immediate: no wind-up, no Slowness, no
	 * telegraph to read. A parry is already a reaction, and the wind-up exists
	 * to buy the victim one; the person who just swung into a parry has had it.
	 */
	private static void riposte(final ServerPlayer player, final ServerLevel level,
			final LivingEntity attacker, final float amount) {
		if (SlayerActives.decimate(player, true)) {
			return;
		}

		attacker.hurtServer(level, player.damageSources().thorns(player), amount);

		if (attacker.isAlive()) {
			// Server-side only: {@link #pay} fills the ticker again a moment
			// later and THAT is the number the client is told, so the swing
			// this borrows never needs a packet of its own.
			applySwingTicker(player, (int) Math.ceil(player.getCurrentItemAttackStrengthDelay()));
			player.attack(attacker);
		}
	}

	/**
	 * Everything a successful parry is worth, whatever was parried: the waived
	 * swing cooldown, Riposte's Strength, Stalwart's temporary hearts, and the
	 * blade's own voice — plus the short clock the key now answers to.
	 *
	 * <p>The stamp is WRITTEN rather than cleared, and it is written first: a
	 * parry landing is exactly the moment the key's next answer is decided, and
	 * reading that from the code should not require knowing which other branch
	 * last touched the stamp. Which clock is a question about the blade, so it is
	 * asked once here and the answer serves the sound cue below as well.
	 */
	private static void pay(final ServerPlayer player, final ServerLevel level) {
		boolean greatsword = ModItems.isGreatsword(player.getMainHandItem());

		((AttachmentTarget) player).setAttached(ModAttachments.PARRY_READY_AT,
				player.level().getGameTime() + (greatsword
						? Tuning.PARRY_SUCCESS_GREATSWORD_COOLDOWN_TICKS
						: Tuning.PARRY_SUCCESS_SWORD_COOLDOWN_TICKS));
		setSwingTicker(player, (int) Math.ceil(player.getCurrentItemAttackStrengthDelay()));

		int riposte = rank(player, Family.RIPOSTE);

		if (riposte > 0) {
			player.addEffect(new MobEffectInstance(MobEffects.STRENGTH,
					Tuning.RIPOSTE_STRENGTH_TICKS,
					Tuning.RIPOSTE_AMPLIFIER_STEP * riposte - 1));
			ProcIndicators.send(player, SubTree.COLOSSUS_SLAYER, Family.RIPOSTE);
		}

		if (rank(player, Family.STALWART) > 0) {
			stalwart(player);
			ProcIndicators.send(player, SubTree.COLOSSUS_SLAYER, Family.STALWART);
		}

		// The author's two cues: a clank for the sword, a low loud metallic
		// bang for the greatsword. Both are the anvil, an octave apart.
		if (greatsword) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 1.4F, 0.45F);
			level.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.IRON_GOLEM_DAMAGE, SoundSource.PLAYERS, 1.0F, 0.5F);
		} else {
			level.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.7F, 1.9F);
			level.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.SHIELD_BLOCK.value(), SoundSource.PLAYERS, 0.9F, 1.6F);
		}

		level.sendParticles(ParticleTypes.CRIT, player.getX(),
				player.getY() + player.getBbHeight() * 0.7, player.getZ(),
				greatsword ? 16 : 8, 0.3, 0.3, 0.3, 0.15);
		ProcIndicators.send(player, SubTree.COLOSSUS_SLAYER, Family.PARRY);
	}

	/**
	 * Stalwart: two temporary hearts a parry, up to ten.
	 *
	 * <p>Vanilla's Absorption is exactly this shape — its step is 4 health per
	 * amplifier, i.e. the author's 2 hearts, and the effect carries its own
	 * {@code MAX_ABSORPTION} modifier so nothing here has to police the
	 * ceiling. The next rung is read off the absorption the player is standing
	 * in rather than a stack counter of our own, which makes the node
	 * stateless and makes "up to 10 hearts" true of the total: a Colossus
	 * already shielded to ten gets nothing more, and one whose shield has been
	 * eaten back down is topped up again.
	 */
	private static void stalwart(final ServerPlayer player) {
		int step = (int) Math.floor(Math.max(0.0F, player.getAbsorptionAmount())
				/ Tuning.STALWART_ABSORPTION_STEP);
		player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, Tuning.STALWART_TICKS,
				Math.min(Tuning.STALWART_MAX_AMPLIFIER, step)));
	}

	// ------------------------------------------------------------------
	// Spell Reflect.
	// ------------------------------------------------------------------

	/**
	 * Whether this projectile turns around. Answered into vanilla's own
	 * {@code Entity.deflection} hook, which {@code hitTargetOrDeflectSelf}
	 * consults BEFORE {@code onHit} — so a parried spell never damages anyone
	 * and never discards itself, and the return-to-sender is the Protector's
	 * Reflection verbatim (see {@code ProjectileMixin}).
	 *
	 * <p>"Directed spells" is read narrowly: this mod's own spells, and the
	 * vanilla hurting projectiles that are magic rather than missiles — blaze
	 * and ghast fireballs, wither skulls, dragon fireballs, shulker bullets.
	 * Wind charges are excluded (they are a shove, not a spell) and so are
	 * arrows and thrown potions — the first belong to the Protector's
	 * Reflection, the second are the author's area case and are merely voided.
	 */
	public static boolean parriesSpell(final Player player, final Projectile projectile) {
		if (!isWindowOpen(player) || rank(player, Family.SPELL_REFLECT) <= 0) {
			return false;
		}

		Entity shooter = projectile.getOwner();

		if (!(shooter instanceof LivingEntity) || shooter == player) {
			return false;
		}

		return projectile instanceof SpellProjectile
				|| projectile instanceof net.minecraft.world.entity.projectile.ShulkerBullet
				|| (projectile instanceof AbstractHurtingProjectile
						&& !(projectile instanceof AbstractWindCharge));
	}

	/** The spell turned: pay the parry and close the window. Called from
	 * {@code ProjectileMixin}'s deflect hook, once, after vanilla has already
	 * handed the shot back. */
	public static void onSpellParried(final ServerPlayer player, final ServerLevel level) {
		closeWindow(player);
		pay(player, level);
		ProcIndicators.send(player, SubTree.COLOSSUS_SLAYER, Family.SPELL_REFLECT);
	}

	// ------------------------------------------------------------------
	// The swing timer.
	// ------------------------------------------------------------------

	/**
	 * Install a raw {@code attackStrengthTicker}, server-side, and tell the
	 * owning client the same number. Vanilla only ever offers to RESET the
	 * ticker to zero — its weakest state — and a parry's reward is the opposite,
	 * a swing already at full charge, so the field is written directly.
	 */
	private static void setSwingTicker(final ServerPlayer player, final int ticker) {
		applySwingTicker(player, ticker);
		ServerPlayNetworking.send(player, new ParrySwingPayload(ticker));
	}

	/** The write itself, shared with the client's payload handler so the two
	 * sides can never disagree about what a parry did to the swing. */
	public static void applySwingTicker(final Player player, final int ticker) {
		((LivingEntityAccessor) player).archetypes$setAttackStrengthTicker(ticker);
	}
}
