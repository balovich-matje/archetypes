package com.archetypes;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client -> server: sprint pressed while drawing a bow. Validated server-side. */
public record DisengagePayload() implements CustomPacketPayload {
	public static final Type<DisengagePayload> TYPE = new Type<>(Archetypes.id("disengage"));

	public static final StreamCodec<RegistryFriendlyByteBuf, DisengagePayload> CODEC =
			StreamCodec.unit(new DisengagePayload());

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
