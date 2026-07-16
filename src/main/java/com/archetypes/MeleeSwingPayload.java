package com.archetypes;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client -> server: a charged combat swing began (not mining). The server
 * derives the weapon class itself and bumps the synced swing counter, which
 * every client turns into the matching pose. */
public record MeleeSwingPayload() implements CustomPacketPayload {
	public static final Type<MeleeSwingPayload> TYPE = new Type<>(Archetypes.id("melee_swing"));

	public static final StreamCodec<RegistryFriendlyByteBuf, MeleeSwingPayload> CODEC =
			StreamCodec.unit(new MeleeSwingPayload());

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
