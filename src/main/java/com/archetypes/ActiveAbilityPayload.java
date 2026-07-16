package com.archetypes;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client -> server: an ability key was pressed. The three keys are slots, one
 * per sub-tree in screen order (0 left, 1 middle, 2 right) — what a slot DOES
 * depends on the player's archetype, and the server resolves that; the client
 * only reports the press.
 */
public record ActiveAbilityPayload(int slot) implements CustomPacketPayload {
	public static final Type<ActiveAbilityPayload> TYPE = new Type<>(Archetypes.id("active_ability"));

	public static final StreamCodec<RegistryFriendlyByteBuf, ActiveAbilityPayload> CODEC =
			StreamCodec.composite(ByteBufCodecs.VAR_INT, ActiveAbilityPayload::slot, ActiveAbilityPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
