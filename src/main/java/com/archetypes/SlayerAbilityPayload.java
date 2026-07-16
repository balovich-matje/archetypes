package com.archetypes;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client -> server: the Slayer capstone key. The server dispatches on the
 * mainhand — Decimate with a greatsword, Bladestorm with a sword — and
 * validates ownership and cooldowns itself. */
public record SlayerAbilityPayload() implements CustomPacketPayload {
	public static final Type<SlayerAbilityPayload> TYPE = new Type<>(Archetypes.id("slayer_ability"));

	public static final StreamCodec<RegistryFriendlyByteBuf, SlayerAbilityPayload> CODEC =
			StreamCodec.unit(new SlayerAbilityPayload());

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
