package com.archetypes;

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
