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
 * Client -> server: the player confirmed an archetype on the picker screen.
 * The server validates and is the only side that ever writes the attachment.
 */
public record PickArchetypePayload(String archetypeId) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<PickArchetypePayload> TYPE =
			new CustomPacketPayload.Type<>(Archetypes.id("pick_archetype"));

	public static final StreamCodec<RegistryFriendlyByteBuf, PickArchetypePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, PickArchetypePayload::archetypeId,
			PickArchetypePayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
//?}
