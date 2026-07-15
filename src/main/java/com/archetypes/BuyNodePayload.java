package com.archetypes;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client -> server: spend a point on one node. The server re-runs every check. */
public record BuyNodePayload(String subTreeId, int node) implements CustomPacketPayload {
	public static final Type<BuyNodePayload> TYPE = new Type<>(Archetypes.id("buy_node"));

	public static final StreamCodec<RegistryFriendlyByteBuf, BuyNodePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, BuyNodePayload::subTreeId,
			ByteBufCodecs.VAR_INT, BuyNodePayload::node,
			BuyNodePayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
