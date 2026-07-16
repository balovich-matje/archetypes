package com.archetypes;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client -> server, once per client tick while the Flamethrower holder keeps
 * the elementalist key down. The server treats a gap in the stream as the
 * channel ending — there is no explicit stop packet to lose.
 */
public record SpellChannelPayload() implements CustomPacketPayload {
	public static final Type<SpellChannelPayload> TYPE = new Type<>(Archetypes.id("spell_channel"));

	public static final StreamCodec<RegistryFriendlyByteBuf, SpellChannelPayload> CODEC =
			StreamCodec.unit(new SpellChannelPayload());

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
