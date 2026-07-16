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
 * Iron Skin while the hands are bare; Adrenaline's attack speed while its
 * window (kept open by landing hits) lasts; Battle Trance's absorption drains
 * once the fight goes quiet.
 */
public final class CrusherTicker {
	private static final Identifier BARE_KNUCKLE_ID = Archetypes.id("bare_knuckle");
	private static final Identifier IRON_SKIN_ARMOR_ID = Archetypes.id("iron_skin_armor");
	private static final Identifier IRON_SKIN_TOUGHNESS_ID = Archetypes.id("iron_skin_toughness");
	private static final Identifier ADRENALINE_ID = Archetypes.id("adrenaline");

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

		int knuckle = CrusherNodes.rank(SubTree.CRUSHER, owned, CrusherNodes.Family.BARE_KNUCKLE);
		apply(player.getAttribute(Attributes.ATTACK_DAMAGE), BARE_KNUCKLE_ID,
				hands && knuckle > 0, knuckle * Tuning.BARE_KNUCKLE_PER_RANK,
				AttributeModifier.Operation.ADD_VALUE);

		int skin = CrusherNodes.rank(SubTree.CRUSHER, owned, CrusherNodes.Family.IRON_SKIN);
		apply(player.getAttribute(Attributes.ARMOR), IRON_SKIN_ARMOR_ID,
				hands && skin > 0, skin * Tuning.IRON_SKIN_ARMOR_PER_RANK,
				AttributeModifier.Operation.ADD_VALUE);
		apply(player.getAttribute(Attributes.ARMOR_TOUGHNESS), IRON_SKIN_TOUGHNESS_ID,
				hands && skin > 0, skin * Tuning.IRON_SKIN_TOUGHNESS_PER_RANK,
				AttributeModifier.Operation.ADD_VALUE);

		// Adrenaline: rank-scaled attack speed, doubled for fists, while the
		// hit-fed window is open and a Crusher weapon is in use.
		AttachmentTarget target = (AttachmentTarget) player;
		int adrenaline = CrusherNodes.rank(SubTree.CRUSHER, owned, CrusherNodes.Family.ADRENALINE);
		Long until = target.getAttached(ModAttachments.ADRENALINE_UNTIL);
		boolean rushing = adrenaline > 0 && (weapon == WeaponClass.MACE || hands)
				&& until != null && until > now;
		apply(player.getAttribute(Attributes.ATTACK_SPEED), ADRENALINE_ID,
				rushing, adrenaline * Tuning.ADRENALINE_SPEED_PER_RANK * (hands ? 2.0F : 1.0F),
				AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

		// Battle Trance decay: the banked hearts fade once the fight is over.
		if (CrusherNodes.rank(SubTree.CRUSHER, owned, CrusherNodes.Family.BATTLE_TRANCE) > 0
				&& player.getAbsorptionAmount() > 0) {
			Long lastHit = target.getAttached(ModAttachments.TRANCE_HIT_AT);

			if (lastHit != null && now - lastHit > Tuning.TRANCE_DECAY_DELAY_TICKS && now % 20 == 0) {
				player.setAbsorptionAmount(Math.max(0.0F, player.getAbsorptionAmount() - 1.0F));
			}
		}
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
		}
	}
}
