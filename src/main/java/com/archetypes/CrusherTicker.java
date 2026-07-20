package com.archetypes;

import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.server.level.ServerPlayer;

/**
 * The Crusher's stances, mirroring SlayerTicker's pattern: attribute
 * modifiers that exist exactly while their condition holds. Bare-Knuckle and
 * Iron Skin while the hands are bare; Battle Trance's absorption drains
 * once the fight goes quiet.
 */
public final class CrusherTicker {
	private static final Identifier BARE_KNUCKLE_ID = Archetypes.id("bare_knuckle");
	private static final Identifier IRON_SKIN_ARMOR_ID = Archetypes.id("iron_skin_armor");
	private static final Identifier IRON_SKIN_TOUGHNESS_ID = Archetypes.id("iron_skin_toughness");
	private static final Identifier CLINCH_ID = Archetypes.id("clinch");
	private static final Identifier QUAKE_IMMUNITY_ID = Archetypes.id("quake_immunity");
	private static final Identifier TRANCE_CAP_ID = Archetypes.id("battle_trance_cap");
	/** Immovable's own id, distinct from Clinch's and Quake's: three
	 * modifiers on one attribute must not share a key or the last writer wins. */
	private static final Identifier IMMOVABLE_EXPLOSION_ID = Archetypes.id("immovable_explosion");

	private CrusherTicker() {
	}

	public static void initialize() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				tick(player);
			}
		});
	}

	private static void tick(final ServerPlayer player) {
		var owned = NodePurchases.owned(player, SubTree.CRUSHER);
		WeaponClass weapon = WeaponClass.of(player);
		boolean hands = weapon == WeaponClass.HANDS;
		long now = player.level().getGameTime();

		// Bare-Knuckle: the handle pays with either weapon — hard with
		// fists, lighter with the mace. apply() re-asserts on value change,
		// so swapping between them retunes the same modifier.
		int knuckle = CrusherNodes.rank(SubTree.CRUSHER, owned, CrusherNodes.Family.BARE_KNUCKLE);
		boolean mace = weapon == WeaponClass.MACE;
		apply(player.getAttribute(Attributes.ATTACK_DAMAGE), BARE_KNUCKLE_ID,
				(hands || mace) && knuckle > 0,
				knuckle * (hands ? Tuning.BARE_KNUCKLE_FIST_PER_RANK : Tuning.BARE_KNUCKLE_MACE_PER_RANK),
				AttributeModifier.Operation.ADD_VALUE);

		int skin = CrusherNodes.rank(SubTree.CRUSHER, owned, CrusherNodes.Family.IRON_SKIN);
		apply(player.getAttribute(Attributes.ARMOR), IRON_SKIN_ARMOR_ID,
				hands && skin > 0, skin * Tuning.IRON_SKIN_ARMOR_PER_RANK,
				AttributeModifier.Operation.ADD_VALUE);
		apply(player.getAttribute(Attributes.ARMOR_TOUGHNESS), IRON_SKIN_TOUGHNESS_ID,
				hands && skin > 0, skin * Tuning.IRON_SKIN_TOUGHNESS_PER_RANK,
				AttributeModifier.Operation.ADD_VALUE);

		// Clinch: bare fists take less knockback (the applied half lives in
		// the knockback mixin). KNOCKBACK_RESISTANCE is ranged 0..1, so this
		// can never overshoot into negative knockback.
		AttachmentTarget target = (AttachmentTarget) player;
		int clinch = CrusherNodes.rank(SubTree.CRUSHER, owned, CrusherNodes.Family.CLINCH);
		apply(player.getAttribute(Attributes.KNOCKBACK_RESISTANCE), CLINCH_ID,
				hands && clinch > 0, clinch * Tuning.CLINCH_KNOCKBACK_REDUCTION_PER_RANK,
				AttributeModifier.Operation.ADD_VALUE);

		// Quake: knockback immunity while the charge holds; the slam lands the
		// tick the charge ends.
		Long chargeEnd = target.getAttached(ModAttachments.QUAKE_CHARGE_END);
		boolean charging = chargeEnd != null && chargeEnd > now;
		apply(player.getAttribute(Attributes.KNOCKBACK_RESISTANCE), QUAKE_IMMUNITY_ID,
				charging, 1.0, AttributeModifier.Operation.ADD_VALUE);

		if (chargeEnd != null && chargeEnd == now) {
			CrusherActives.quakeSlam(player);
		}

		// A true smash in progress. fallDistance, not velocity: player motion
		// is client-authoritative and the server's copy is stale, but the
		// server tracks fallDistance itself (fall damage depends on it).
		if (weapon == WeaponClass.MACE && !player.onGround()
				&& player.fallDistance > Tuning.SMASH_MIN_FALL) {
			target.setAttached(ModAttachments.SMASH_AT, now);
		}

		// Battle Trance banks raw absorption, and since 1.20.2 that amount is
		// clamped to the MAX_ABSORPTION attribute — which defaults to zero.
		// The rank's cap must live in the attribute or every grant clamps
		// straight back to nothing.
		int trance = CrusherNodes.rank(SubTree.CRUSHER, owned, CrusherNodes.Family.BATTLE_TRANCE);
		apply(player.getAttribute(Attributes.MAX_ABSORPTION), TRANCE_CAP_ID,
				trance > 0, tranceCap(player, owned),
				AttributeModifier.Operation.ADD_VALUE);

		// Battle Trance decay: the banked hearts fade once the fight is over.
		if (trance > 0 && player.getAbsorptionAmount() > 0) {
			Long lastHit = target.getAttached(ModAttachments.TRANCE_HIT_AT);

			if (lastHit != null && now - lastHit > Tuning.TRANCE_DECAY_DELAY_TICKS && now % 20 == 0) {
				player.setAbsorptionAmount(Math.max(0.0F, player.getAbsorptionAmount() - 1.0F));
			}
		}

		// Immovable: explosions and wind charges do not route through
		// LivingEntity.knockback at all — ServerExplosion pushes entities
		// directly and asks EXPLOSION_KNOCKBACK_RESISTANCE instead, so the
		// attribute is the only place that clause can live. Hits are the
		// knockback funnel's job (LivingEntityMixin$immovableKnockback).
		apply(player.getAttribute(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE), IMMOVABLE_EXPLOSION_ID,
				TitansLeap.rank(player, ColossusCrusherNodes.Family.IMMOVABLE) > 0, 1.0,
				AttributeModifier.Operation.ADD_VALUE);

		TitansLeap.tick(player);
	}

	/**
	 * Battle Trance's ceiling in health: the base tree's 2.0 per rank plus the
	 * epic Bulwark's 6.0 per rank. Both the MAX_ABSORPTION modifier above and
	 * the per-hit clamp in {@code CrusherCombat} have to read this same number,
	 * or Bulwark raises a ceiling the banking never reaches.
	 */
	public static float tranceCap(final ServerPlayer player, final java.util.Set<Integer> owned) {
		int trance = CrusherNodes.rank(SubTree.CRUSHER, owned, CrusherNodes.Family.BATTLE_TRANCE);
		int bulwark = TitansLeap.rank(player, ColossusCrusherNodes.Family.BULWARK);
		return trance * Tuning.TRANCE_CAP_PER_RANK
				+ bulwark * Tuning.COLOSSUS_BULWARK_TRANCE_CAP_PER_RANK;
	}

	private static void apply(final AttributeInstance attribute, final Identifier id,
			final boolean should, final double value, final AttributeModifier.Operation operation) {
		if (attribute == null) {
			return;
		}

		if (should && !attribute.hasModifier(id)) {
			attribute.addTransientModifier(new AttributeModifier(id, value, operation));
		} else if (!should && attribute.hasModifier(id)) {
			attribute.removeModifier(id);
		} else if (should) {
			// The value can change while active (rank bought, weapon swapped
			// between fists and mace) — cheap to re-assert.
			AttributeModifier current = attribute.getModifier(id);

			if (current == null || current.amount() != value) {
				attribute.removeModifier(id);
				attribute.addTransientModifier(new AttributeModifier(id, value, operation));
			}
		}
	}
}
