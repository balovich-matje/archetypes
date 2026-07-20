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
 * <li>The client sees attack and block go down on the same tick and sends one
 *     {@link ParryPayload}. It consumes no clicks, so normal attacking and
 *     normal blocking are untouched — see {@code ArchetypesClient}.</li>
 * <li>{@link #open} validates the node, the archetype and the blade, and
 *     stamps a {@link Tuning#PARRY_WINDOW_TICKS}-tick window.</li>
 * <li>A hit arriving inside the window is a parry: {@link #tryParry}, hung off
 *     the victim's {@code hurtServer} intake next to Ghost Form and Sidestep,
 *     voids it and pays. A spell arriving inside the window never reaches
 *     {@code hurtServer} at all — {@link #parriesSpell} answers vanilla's own
 *     {@code Entity.deflection} and the shot turns around before it lands.</li>
 * <li>{@link #tickParry} lapses an unpaid window and charges the miss.</li>
 * </ol>
 *
 * <h2>Both swing consequences are one number</h2>
 * "It will not incur a swing cooldown" and "missing a parry will incur a x2.0
 * swing cooldown" are the same field, vanilla's {@code attackStrengthTicker}:
 * set to full charge, the next swing is free; set negative, the swing has that
 * much further to climb. The server writes its own copy and sends the value to
 * the client as a {@link ParrySwingPayload} rather than letting the client
 * derive it — only the server knows whether the hit that pays for a parry ever
 * arrived.
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
	private static final Identifier BLADE_MASTER_DAMAGE_ID = Archetypes.id("blade_master_damage");

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
				if (ModAttachments.get(player) == Archetype.STRENGTH) {
					tickStance(player);
					tickParry(player);
				}
			}
		});
	}

	// ------------------------------------------------------------------
	// Blade Master — two transient attribute modifiers, held by the hand.
	// ------------------------------------------------------------------

	/**
	 * Blade Master, asserted and revoked every tick in
	 * {@code SlayerTicker.tickStance}'s shape so no respec can leave either
	 * modifier standing.
	 *
	 * <p>Both halves are attributes rather than damage hooks on purpose. The
	 * swing-time cut has to be an attribute — {@code
	 * getCurrentItemAttackStrengthDelay} reads {@code ATTACK_SPEED} and nothing
	 * else — and routing the sword's damage the same way means Bladestorm,
	 * Blade Dance and Decimate, which all compute from {@code
	 * getAttributeValue(ATTACK_DAMAGE)}, inherit it without a line of their own.
	 *
	 * <p>The swing-time number is converted, not copied: {@code -20%} of the
	 * TIME is {@code x1.25} the rate, so the modifier carries
	 * {@code 1 / (1 - cut) - 1}. It multiplies against Heavy Blows' own
	 * {@code ATTACK_SPEED} cut rather than cancelling it, because vanilla
	 * applies each {@code ADD_MULTIPLIED_TOTAL} in turn — so the description's
	 * "-20/40%" stays true of whatever the greatsword swings at now.
	 */
	private static void tickStance(final ServerPlayer player) {
		int rank = rank(player, Family.BLADE_MASTER);
		ItemStack held = player.getMainHandItem();
		double cut = Math.min(0.9, Tuning.BLADE_MASTER_SWING_CUT_PER_RANK * rank);

		stance(player.getAttribute(Attributes.ATTACK_SPEED), BLADE_MASTER_SPEED_ID,
				rank > 0 && ModItems.isGreatsword(held), 1.0 / (1.0 - cut) - 1.0);
		stance(player.getAttribute(Attributes.ATTACK_DAMAGE), BLADE_MASTER_DAMAGE_ID,
				rank > 0 && ModItems.isSword(held),
				Tuning.BLADE_MASTER_SWORD_DAMAGE_PER_RANK * rank);
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
	 * Whether this player could parry right now, i.e. whether the client
	 * should bother sending the press at all. Decided from the SAME synced
	 * node set the server re-validates with, the mod's rule for every
	 * client-side input gate.
	 */
	public static boolean canParry(final Player player) {
		if (ModAttachments.get(player) != Archetype.STRENGTH
				|| rank(player, Family.PARRY) <= 0) {
			return false;
		}

		ItemStack held = player.getMainHandItem();
		return ModItems.isSword(held) || ModItems.isGreatsword(held);
	}

	/** Attack and block came down together: open the window, or ignore the
	 * press if one is already open (a held combo is one parry, not sixty). */
	public static void open(final ServerPlayer player) {
		if (!canParry(player) || isWindowOpen(player)) {
			return;
		}

		AttachmentTarget target = (AttachmentTarget) player;
		long now = player.level().getGameTime();
		target.setAttached(ModAttachments.PARRY_AT, now);
		target.setAttached(ModAttachments.PARRY_UNTIL, now + Tuning.PARRY_WINDOW_TICKS);
	}

	private static boolean isWindowOpen(final Player player) {
		Long until = ((AttachmentTarget) player).getAttached(ModAttachments.PARRY_UNTIL);
		return until != null && player.level().getGameTime() < until;
	}

	/** The window lapsed unpaid: charge the miss. */
	private static void tickParry(final ServerPlayer player) {
		AttachmentTarget target = (AttachmentTarget) player;
		Long until = target.getAttached(ModAttachments.PARRY_UNTIL);

		if (until == null || player.level().getGameTime() < until) {
			return;
		}

		long started = closeWindow(player);
		float delay = player.getCurrentItemAttackStrengthDelay();

		// Full charge must arrive at (press + factor x delay). The ticker
		// climbs one a tick, so the value that lands it exactly there is
		// now - press - delay — a negative number, and the deeper the window
		// ran the deeper it starts.
		setSwingTicker(player, (int) (player.level().getGameTime() - started
				- delay * (Tuning.PARRY_MISS_SWING_FACTOR - 1.0F)));

		((ServerLevel) player.level()).playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.PLAYER_ATTACK_NODAMAGE, SoundSource.PLAYERS, 0.7F, 0.6F);
	}

	/** Clears the window and returns the tick it opened on. */
	private static long closeWindow(final ServerPlayer player) {
		AttachmentTarget target = (AttachmentTarget) player;
		Long started = target.getAttached(ModAttachments.PARRY_AT);
		target.removeAttached(ModAttachments.PARRY_UNTIL);
		target.removeAttached(ModAttachments.PARRY_AT);
		return started == null ? player.level().getGameTime() : started;
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
	 */
	private static void riposte(final ServerPlayer player, final ServerLevel level,
			final LivingEntity attacker, final float amount) {
		boolean greatsword = ModItems.isGreatsword(player.getMainHandItem());

		if (greatsword && SlayerNodes.rank(SubTree.SLAYER,
				NodePurchases.owned(player, SubTree.SLAYER), SlayerNodes.Family.DECIMATE) > 0) {
			SlayerActives.decimate(player, true);
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
	 * blade's own voice.
	 */
	private static void pay(final ServerPlayer player, final ServerLevel level) {
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

		boolean greatsword = ModItems.isGreatsword(player.getMainHandItem());

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
	 * owning client the same number. Vanilla only ever offers to reset the
	 * ticker to zero — its weakest state — so both of this node's swing
	 * consequences have to write it directly.
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
