package com.archetypes;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client: one of your passives just fired. Carries the sub-tree id
 * and the family name so the HUD can look up the node's own icon — the
 * indicator IS the skill tree talking back.
 */
public record PassiveProcPayload(String subTreeId, String family) implements CustomPacketPayload {
	public static final Type<PassiveProcPayload> TYPE = new Type<>(Archetypes.id("passive_proc"));

	public static final StreamCodec<RegistryFriendlyByteBuf, PassiveProcPayload> CODEC =
			StreamCodec.composite(
					ByteBufCodecs.STRING_UTF8, PassiveProcPayload::subTreeId,
					ByteBufCodecs.STRING_UTF8, PassiveProcPayload::family,
					PassiveProcPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
