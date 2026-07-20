package com.archetypes;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client -&gt; server: attack and block were pressed together (Colossus Slayer's
 * Parry). The client only reports the key edge — the node, the weapon, the
 * window and everything a parry is worth are decided server-side in
 * {@link ColossusSlayer}.
 */
public record ParryPayload() implements CustomPacketPayload {
	public static final Type<ParryPayload> TYPE = new Type<>(Archetypes.id("parry"));

	public static final StreamCodec<RegistryFriendlyByteBuf, ParryPayload> CODEC =
			StreamCodec.unit(new ParryPayload());

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
