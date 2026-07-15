package com.archetypes;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client -> server: sprint pressed while blocking. Everything validates server-side. */
public record RushPayload() implements CustomPacketPayload {
	public static final Type<RushPayload> TYPE = new Type<>(Archetypes.id("shield_rush"));

	public static final StreamCodec<RegistryFriendlyByteBuf, RushPayload> CODEC =
			StreamCodec.unit(new RushPayload());

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
