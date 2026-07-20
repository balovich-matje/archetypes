package com.archetypes;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -&gt; client: a landed parry waived your swing cooldown.
 *
 * <p>The value is vanilla's own {@code attackStrengthTicker}, already computed
 * by the server, and the client installs it verbatim — a ticker already at
 * full charge. Only successful parries send this: a miss now costs
 * {@link Tuning#PARRY_COOLDOWN_TICKS} on the ability key and leaves the swing
 * timer alone.
 *
 * <p>It is sent rather than derived because the server is the only side that
 * knows a parry landed: the hit that pays for it arrives after the press. Left
 * to its own ticker the client would keep charging a swing the server has
 * already filled, and the crosshair indicator would lie about when the free
 * hit is available.
 */
public record ParrySwingPayload(int ticker) implements CustomPacketPayload {
	public static final Type<ParrySwingPayload> TYPE = new Type<>(Archetypes.id("parry_swing"));

	public static final StreamCodec<RegistryFriendlyByteBuf, ParrySwingPayload> CODEC =
			StreamCodec.composite(ByteBufCodecs.VAR_INT, ParrySwingPayload::ticker,
					ParrySwingPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
