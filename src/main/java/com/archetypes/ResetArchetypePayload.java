package com.archetypes;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;

/**
 * Client -> server: clear the archetype so it can be picked again. A testing
 * affordance — the server only honours it in creative mode, and checks that
 * itself rather than trusting the client's button.
 */
public record ResetArchetypePayload() implements CustomPacketPayload {
	public static final Type<ResetArchetypePayload> TYPE =
			new Type<>(Archetypes.id("reset_archetype"));

	public static final StreamCodec<RegistryFriendlyByteBuf, ResetArchetypePayload> CODEC =
			StreamCodec.unit(new ResetArchetypePayload());

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
