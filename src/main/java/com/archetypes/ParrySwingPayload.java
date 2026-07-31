package com.archetypes;

// STAGE 5 — THE WHOLE COMPILATION UNIT IS `>=1.20.5` ONLY (conventions §4's whole-file form),
// because every type in it is: `CustomPacketPayload`, its `Type`, `StreamCodec`,
// `ByteBufCodecs` and `RegistryFriendlyByteBuf` all arrived together in 1.20.5. Below that
// fabric-api 0.92.11 speaks the raw channel API — an id and a `FriendlyByteBuf` — which is
// what `platform/FabricNet`'s legacy arm uses, so there is nothing for a payload record to
// be there. A file whose only surviving text is its package statement is a legal compilation
// unit and produces no `.class`; the bytes on the wire are unchanged either way, which is
// what `state/WireId`'s frozen contract is about.
//? if >=1.20.5 {
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -&gt; client: a landed parry waived your swing cooldown.
 *
 * <p>The value is vanilla's own {@code attackStrengthTicker}, already computed
 * by the server, and the client installs it verbatim — a ticker already at
 * full charge. Only successful parries send this: a miss costs
 * {@link Tuning#PARRY_COOLDOWN_TICKS} on the ability key and leaves the swing
 * timer alone. (A success costs the key a second or two of its own now — see
 * {@link Tuning#PARRY_SUCCESS_GREATSWORD_COOLDOWN_TICKS} — but that is the
 * ability's clock, not the swing's, and this packet is about the swing.)
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
//?}
