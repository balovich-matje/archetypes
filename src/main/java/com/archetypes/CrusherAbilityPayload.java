package com.archetypes;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client -> server: the Crusher capstone key. Haymaker with bare fists; the
 * mace capstone joins when its flange lands. Everything validates server-side. */
public record CrusherAbilityPayload() implements CustomPacketPayload {
	public static final Type<CrusherAbilityPayload> TYPE = new Type<>(Archetypes.id("crusher_ability"));

	public static final StreamCodec<RegistryFriendlyByteBuf, CrusherAbilityPayload> CODEC =
			StreamCodec.unit(new CrusherAbilityPayload());

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
