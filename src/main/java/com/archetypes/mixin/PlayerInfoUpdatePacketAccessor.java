package com.archetypes.mixin;

import java.util.List;

import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The one field that stands between this mod and a fake player.
 *
 * <p>{@code ClientboundPlayerInfoUpdatePacket} has exactly two public
 * constructors and both of them take real {@code ServerPlayer}s — its
 * {@code Entry} record is public and freely constructible, but there is no way
 * to hand a list of entries to the packet. So the packet is built empty from
 * the public constructor and its entry list is written afterwards.
 *
 * <p>Why this is needed at all: the client refuses to spawn a {@code PLAYER}
 * entity it has no profile for. {@code ClientPacketListener.createEntityFromPacket}
 * looks the add-entity packet's UUID up in {@code playerInfoMap}, logs
 * "Server attempted to add player prior to sending player info" and returns
 * null when it misses. A player-shaped puppet therefore has to be announced as
 * a player first, and announcing one that does not exist means an entry we
 * wrote ourselves.
 *
 * <p>An accessor rather than an injector on purpose: nothing here can fail to
 * find an injection point at runtime, and a missing field is a load-time error
 * with the field's name in it.
 */
@Mixin(ClientboundPlayerInfoUpdatePacket.class)
public interface PlayerInfoUpdatePacketAccessor {
	@Mutable
	@Accessor("entries")
	void archetypes$setEntries(List<ClientboundPlayerInfoUpdatePacket.Entry> entries);
}
