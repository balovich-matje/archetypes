package com.archetypes.platform;

import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.archetypes.state.WireId;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

/**
 * Seam 2 of three (design {@code docs/MULTIVERSION.md} §3.2) — the eleven
 * channels: their registration, both directions of send, and where a message
 * lands.
 *
 * <p>Why this is a seam and not a {@code //?} block: on 1.20.1 the whole payload
 * stack is missing — {@code CustomPacketPayload}, {@code StreamCodec},
 * {@code ByteBufCodecs}, {@code RegistryFriendlyByteBuf} and
 * {@code PayloadTypeRegistry} are all absent, and the replacement
 * ({@code FabricPacket} + {@code PacketType.create}) needs different payload
 * TYPES, not just a different call. NeoForge and Forge fold registration and the
 * handler into one call each.
 *
 * <p><b>Bidirectional, unlike Skill Proficiencies' clientbound-only {@code Net}</b>,
 * because Archetypes has nine serverbound channels to its two clientbound. What
 * makes that cheap is measured: every one of the eleven payloads carries only
 * {@code int} and {@code String} — <b>zero registry-bound data</b> — so nothing at
 * this boundary needs a {@code RegistryFriendlyByteBuf} and nothing needs a
 * {@code StreamCodec} in its signature. A plain {@code FriendlyByteBuf} and a
 * writer/reader pair say everything the eleven have to say, on every node.
 *
 * <p>The payload RECORDS survive untouched behind this; they are simply no longer
 * named outside {@link FabricNet}. Below 1.20.5 that implementation swaps them for
 * {@code FabricPacket}, inside the implementation only.
 *
 * <p><b>Wire contract:</b> {@link WireId} owns the frozen ids and documents the
 * frozen field order. This interface only moves bytes.
 */
public interface Net {
	// Same three-arm BLOCK chain as the other two seams; see ArchetypeStore.
	Net INSTANCE = new FabricNet();

	/**
	 * Registers all eleven channel types. Called once from common init, before any
	 * handler is installed — a dedicated server registers the clientbound types too,
	 * which is the whole reason registration cannot live on the client side.
	 */
	void registerAll();

	/**
	 * Client to server.
	 *
	 * <p>Declared on the common seam even though Fabric's implementation of it is
	 * client-only, because two of the three loaders' equivalents are not: NeoForge's
	 * {@code PacketDistributor.sendToServer} is common API. {@link FabricNet} keeps
	 * its one client-only call in a nested holder so a dedicated server never loads
	 * the class.
	 */
	void sendToServer(WireId id, Consumer<FriendlyByteBuf> writer);

	/** Server to one client. */
	void sendToClient(ServerPlayer to, WireId id, Consumer<FriendlyByteBuf> writer);

	/**
	 * Installs the server-side handler for one serverbound channel. The handler runs
	 * on the server thread; the implementation is responsible for getting it there.
	 */
	void onServerbound(WireId id, BiConsumer<ServerPlayer, FriendlyByteBuf> handler);

	/**
	 * Installs the client-side sinks, handed down from client init.
	 *
	 * <p>Skill Proficiencies' exact trick, for the exact same reason: NeoForge's
	 * {@code PayloadRegistrar.playToClient(TYPE, CODEC, handler)} and Forge's
	 * {@code SimpleChannel.registerMessage(...)} take the handler as an ARGUMENT to
	 * the one registration call, which has to run in common init. So the client hands
	 * its sinks down and registration stays where it is on every node — and nothing in
	 * {@code src/main} names a client type.
	 *
	 * <p><b>A sink schedules itself onto the client thread.</b> It is handed a buffer
	 * this mod owns, so a deferred read of it is safe, and it is the reason the Fabric
	 * implementation can register a client receiver from {@code src/main} at all:
	 * {@code Minecraft} is the one client class that source set genuinely cannot name,
	 * and the only thing that wanted it was scheduling.
	 */
	void clientReceivers(Map<WireId, Consumer<FriendlyByteBuf>> sinks);
}
