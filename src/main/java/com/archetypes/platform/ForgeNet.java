package com.archetypes.platform;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.archetypes.Archetypes;
import com.archetypes.state.WireId;

import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * LexForge implementation of {@link Net} for the {@code 1.20.1-forge} node: the eleven
 * {@link WireId}s over one {@code SimpleChannel}.
 *
 * <p>ARTIFACT PROVENANCE — read out of {@code forge-1.20.1-47.4.22-sources.jar}, not
 * recalled:
 *
 * <ul>
 * <li>{@code network/NetworkRegistry.java} — {@code static SimpleChannel
 *     newSimpleChannel(ResourceLocation, Supplier<String>, Predicate<String>,
 *     Predicate<String>)}. Still present and non-deprecated at 47.4.22;
 *     {@code ChannelBuilder} is the newer alternative and buys nothing here.</li>
 * <li>{@code network/simple/SimpleChannel.java} — {@code <MSG> …
 *     registerMessage(int index, Class<MSG>, BiConsumer<MSG,FriendlyByteBuf> encoder,
 *     Function<FriendlyByteBuf,MSG> decoder,
 *     BiConsumer<MSG,Supplier<NetworkEvent.Context>>)}, {@code <MSG> void
 *     send(PacketDistributor.PacketTarget, MSG)} and {@code sendToServer(MSG)}.</li>
 * <li>{@code network/PacketDistributor.java} — {@code PacketDistributor<ServerPlayer>
 *     PLAYER} and {@code PacketTarget with(Supplier<T>)}.</li>
 * <li>{@code network/NetworkEvent.java} — {@code ServerPlayer getSender()},
 *     {@code CompletableFuture<Void> enqueueWork(Runnable)},
 *     {@code void setPacketHandled(boolean)}.</li>
 * </ul>
 *
 * <p><b>ONE message index, not eleven, and that is the interesting choice.</b> Skill
 * Proficiencies gave each of its three payloads its own index because each was a
 * distinct record class — {@code IndexedMessageCodec} keys its outbound lookup by
 * {@code MSG.class}, so a shared class across indices cannot be sent. This seam has no
 * payload classes at all: {@link Net} speaks {@code FriendlyByteBuf} and a
 * writer/reader pair in both directions, because every one of the eleven carries only
 * {@code int} and {@code String} (design §3.2). So there is exactly one message type
 * here — {@link Frame} — and the channel that eleven ids would have been is a varint
 * inside it.
 *
 * <p><b>THE FRAME IS FROZEN the day this node ships:</b> varint {@code WireId.ordinal()},
 * then the caller's own bytes verbatim. The ordinal is safe as a discriminator for the
 * same reason {@code WireId}'s javadoc gives — "an id may be added but never renamed or
 * reordered" — and appending an id therefore cannot move an existing one. The BODY bytes
 * are byte-for-byte what every other node writes, because they are produced by the same
 * shared writer lambda; only this two-byte-ish header and Forge's own channel envelope
 * differ, and a Forge client cannot join a Fabric server in any case.
 *
 * <p><b>Handlers run on the network thread.</b> Fabric's typed receiver hands you the
 * main thread; {@code SimpleChannel} does not. So every handler wraps its body in
 * {@code context.enqueueWork(...)} and then calls {@code setPacketHandled(true)} —
 * without the former the game state is mutated off-thread, without the latter Forge logs
 * the packet as unhandled.
 *
 * <p><b>The client receivers arrive through a sink</b>, exactly as Skill Proficiencies'
 * do and for exactly the same reason: {@code registerMessage} takes the handler at
 * registration time, registration must run in common init so a dedicated server can also
 * send, and the two clientbound handler bodies live in {@code src/client}. See
 * {@link Net#clientReceivers}.
 */
final class ForgeNet implements Net {
	/** Channel id, in the mod's own namespace so it cannot collide. */
	private static final Identifier CHANNEL_ID = Archetypes.id("main");

	private static final String PROTOCOL = "1";

	private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
			CHANNEL_ID, () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

	/** FROZEN discriminator — the only message type on the channel. See the class javadoc. */
	private static final int INDEX_FRAME = 0;

	private static final WireId[] BY_ORDINAL = WireId.values();

	// Written once from common init and once from client init, read from the network
	// thread, so both are volatile. A dedicated server keeps the empty client map: it
	// registers the same message type (it has to, or `send` cannot encode) and never
	// receives a clientbound one.
	private static volatile Map<WireId, BiConsumer<ServerPlayer, FriendlyByteBuf>> serverbound =
			new EnumMap<>(WireId.class);

	private static volatile Map<WireId, Consumer<FriendlyByteBuf>> clientbound =
			new EnumMap<>(WireId.class);

	ForgeNet() {
	}

	@Override
	public void registerAll() {
		CHANNEL.registerMessage(INDEX_FRAME, Frame.class, ForgeNet::write, ForgeNet::read,
				ForgeNet::handle);
	}

	@Override
	public void sendToServer(final WireId id, final Consumer<FriendlyByteBuf> writer) {
		CHANNEL.sendToServer(new Frame(id, bytes(writer)));
	}

	@Override
	public void sendToClient(final ServerPlayer to, final WireId id,
			final Consumer<FriendlyByteBuf> writer) {
		CHANNEL.send(PacketDistributor.PLAYER.with(() -> to), new Frame(id, bytes(writer)));
	}

	@Override
	public void onServerbound(final WireId id,
			final BiConsumer<ServerPlayer, FriendlyByteBuf> handler) {
		// Copy-on-write rather than a mutable shared map: this runs on the init thread while
		// the network thread reads, and one volatile publish of a finished map is the whole
		// of the memory model this needs.
		Map<WireId, BiConsumer<ServerPlayer, FriendlyByteBuf>> updated =
				new EnumMap<>(serverbound);
		updated.put(id, handler);
		serverbound = updated;
	}

	@Override
	public void clientReceivers(final Map<WireId, Consumer<FriendlyByteBuf>> sinks) {
		clientbound = new EnumMap<>(sinks);
	}

	private static byte[] bytes(final Consumer<FriendlyByteBuf> writer) {
		FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
		writer.accept(buf);
		byte[] data = new byte[buf.readableBytes()];
		buf.readBytes(data);
		return data;
	}

	private static void write(final Frame frame, final FriendlyByteBuf buf) {
		buf.writeVarInt(frame.id().ordinal());
		buf.writeByteArray(frame.data());
	}

	private static Frame read(final FriendlyByteBuf buf) {
		int ordinal = buf.readVarInt();
		byte[] data = buf.readByteArray();
		// An unknown id is a mod-version mismatch, not a protocol error: the frame is
		// consumed and dropped rather than throwing on the network thread.
		return new Frame(ordinal >= 0 && ordinal < BY_ORDINAL.length ? BY_ORDINAL[ordinal] : null,
				data);
	}

	private static void handle(final Frame frame, final java.util.function.Supplier<NetworkEvent.Context> supplier) {
		NetworkEvent.Context context = supplier.get();
		context.setPacketHandled(true);

		if (frame.id() == null) {
			return;
		}

		ServerPlayer sender = context.getSender();

		if (sender == null) {
			Consumer<FriendlyByteBuf> sink = clientbound.get(frame.id());

			if (sink != null) {
				// The sink schedules its OWN hop onto the client thread — the seam's
				// documented contract, and the reason this branch does not call
				// enqueueWork itself: `NetworkEvent.Context.enqueueWork` on the client
				// would need the client's work queue, which this source set must not name.
				sink.accept(wrap(frame.data()));
			}

			return;
		}

		BiConsumer<ServerPlayer, FriendlyByteBuf> handler = serverbound.get(frame.id());

		if (handler != null) {
			// The buffer is built from OUR byte array, so it outlives the netty buffer the
			// decoder read and a deferred read of it is safe.
			FriendlyByteBuf buf = wrap(frame.data());
			context.enqueueWork(() -> handler.accept(sender, buf));
		}
	}

	private static FriendlyByteBuf wrap(final byte[] data) {
		return new FriendlyByteBuf(Unpooled.wrappedBuffer(data));
	}

	/**
	 * The one message on the channel: which of the eleven, and the caller's bytes.
	 *
	 * <p>{@code id} is nullable only on the decode path — see {@link #read}.
	 */
	private record Frame(WireId id, byte[] data) {
	}
}
