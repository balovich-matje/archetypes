package com.archetypes;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -&gt; client: what a parry did to your swing timer.
 *
 * <p>The value is vanilla's own {@code attackStrengthTicker}, already computed
 * by the server, and the client installs it verbatim. Both consequences the
 * author asked for are one number: a successful parry sends a ticker already
 * at full charge (no swing cooldown at all), a missed one sends a NEGATIVE
 * ticker, which is exactly how long the swing has to climb back — set so that
 * full charge arrives {@link Tuning#PARRY_MISS_SWING_FACTOR} times the weapon's
 * delay after the press.
 *
 * <p>It is sent rather than derived because the server is the only side that
 * knows a parry landed: the hit that pays for it arrives after the press. The
 * client cannot guess, and left to its own ticker it would let a penalised
 * player swing on time into a server that scaled the blow down.
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
