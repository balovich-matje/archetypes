package com.archetypes;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client -> server: the bash key was pressed. Everything validates server-side. */
public record ShieldBashPayload() implements CustomPacketPayload {
	public static final Type<ShieldBashPayload> TYPE = new Type<>(Archetypes.id("shield_bash"));

	public static final StreamCodec<RegistryFriendlyByteBuf, ShieldBashPayload> CODEC =
			StreamCodec.unit(new ShieldBashPayload());

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
