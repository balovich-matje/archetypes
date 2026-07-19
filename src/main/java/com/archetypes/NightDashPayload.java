package com.archetypes;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client -&gt; server: sprint pressed while sneaking in night form (Ghost Form's
 * dash). Validated server-side — the client only reports the key edge. */
public record NightDashPayload() implements CustomPacketPayload {
	public static final Type<NightDashPayload> TYPE = new Type<>(Archetypes.id("night_dash"));

	public static final StreamCodec<RegistryFriendlyByteBuf, NightDashPayload> CODEC =
			StreamCodec.unit(new NightDashPayload());

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
