package com.archetypes.platform;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.archetypes.Archetypes;
import com.archetypes.state.ClientSyncGate;
import com.archetypes.state.StateKey;
import com.archetypes.state.WireCodec;

import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * THE ATTACHED-STATE SYNC FOR {@code 1.20.1-forge} — 16 {@code ALL_TRACKING} keys and 31
 * {@code TARGET_ONLY} ones, moved by hand, because Forge capabilities have <b>no sync of
 * any kind</b>.
 *
 * <p>This is the LexForge twin of {@code platform/LegacyStateSync}, which is the same
 * fallback for the one Fabric node whose fabric-api cannot sync either. That file is
 * scoped {@code fabric && <1.20.5} — every line of its body is {@code PlayerLookup} and
 * {@code ServerPlayNetworking} — and its header records that this node owes the same four
 * paths in its own vocabulary. This is that.
 *
 * <p><b>THE CONTRACT BEING REPRODUCED</b>, copied from {@code LegacyStateSync}'s header
 * because it is fabric-api's own behaviour rather than an idea of it, and R-20 is about
 * exactly the difference:
 *
 * <ol>
 * <li>A synced value is sent WHENEVER IT CHANGES, set and remove alike, and a remove is a
 *     distinct message rather than an absent one — the client has to DROP the value, not
 *     merely stop hearing about it.</li>
 * <li>{@code TARGET_ONLY} reaches the entity itself when it is a player, and nobody
 *     else.</li>
 * <li>{@code ALL_TRACKING} reaches every player tracking the entity AND the entity itself
 *     when it is a player.</li>
 * <li>A client that STARTS TRACKING an entity is sent every {@code ALL_TRACKING} value it
 *     already carries — otherwise a Bulwark raised before the viewer came into range is
 *     invisible until it changes.</li>
 * <li>A player JOINING is sent their own values back.</li>
 * </ol>
 *
 * <p><b>Point 3 is one method call here and that is the find.</b>
 * {@code ServerChunkCache.broadcastAndSend(Entity, Packet)} is public on this version and
 * sends to every player tracking the entity <i>plus the entity itself when it is a
 * player</i> — which is precisely what fabric-api's {@code AttachmentSyncPredicate.all()}
 * means and precisely why {@code LegacyStateSync} has to write
 * {@code PlayerLookup.tracking(...)} plus an explicit re-add of the owner.
 * {@code broadcast} (without {@code AndSend}) is the one that excludes the entity's own
 * player, so the name is load-bearing.
 *
 * <p><b>WIRE FORMAT, frozen with the rest of {@code state/WireId}'s contract and identical
 * to {@code LegacyStateSync}'s:</b> varInt entity id, varInt key INDEX, boolean present,
 * then the key's own {@link WireCodec} bytes if present. The index and not the string id,
 * because {@code StateKey.index()} is assigned by declaration order in {@code ModState} and
 * both sides run the same registration. Forge's own channel envelope wraps it; that is
 * unavoidable and harmless, since a Forge client cannot join a Fabric server.
 *
 * <p><b>A SEPARATE CHANNEL FROM {@link ForgeNet}, on purpose.</b> The eleven {@link
 * com.archetypes.state.WireId} channels are registered from {@code Net.registerAll()},
 * which common init runs near the END of its sequence; the store is handed its key table by
 * {@code ModState.initialize()}, which is the FIRST call in it. A store that had to wait for
 * the network seam would be a new ordering constraint in shared code. Its own channel, armed
 * from {@link ForgeArchetypeStore#register}, has none — and it is also what
 * {@code LegacyStateSync} does (its own {@code archetypes:state_sync}), so the two nodes in
 * the same position have the same shape.
 *
 * <p><b>THE FAILURE MODE IF THIS IS ABSENT, measured on the Fabric node in the same
 * position:</b> there is no error anywhere. The server sends on a channel the client never
 * registered, the client drops it silently, every server-authoritative behaviour keeps
 * working, and the whole visible half — the HUD, the tree screen, every renderer flag other
 * players read — is blank. A dedicated-server smoke cannot see it by construction.
 */
public final class ForgeStateSync {
	/** Channel id — the same path {@code LegacyStateSync} uses. */
	private static final Identifier CHANNEL_ID = Archetypes.id("state_sync");

	private static final String PROTOCOL = "1";

	private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
			CHANNEL_ID, () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

	private static final int INDEX_STATE = 0;

	/**
	 * The END-OF-REPLAY marker, written into the key-index slot of an otherwise ordinary frame.
	 *
	 * <p>A negative index cannot collide with a real one — {@code StateKey.index()} is a
	 * declaration-order counter — and {@link #apply} already had to reject out-of-range indices,
	 * so the frozen wire format is not widened by this, only a value it already had to tolerate
	 * is given a meaning. {@code LegacyStateSync}, the Fabric twin of this file, deliberately does
	 * NOT get the same marker: that node's client keeps its attachments across a player swap by
	 * itself (fabric-api's own client-side transfer mixin), so it has nothing to gate on.
	 *
	 * <p>What it is FOR: a brand-new player owns no keys at all, so {@link #replay} sends them
	 * nothing, and "no messages" is indistinguishable from "not synced yet". Without an explicit
	 * marker {@link ClientSyncGate} could never open for the one player who most needs the picker.
	 */
	private static final int INDEX_REPLAY_DONE = -1;

	private static StateKey<?>[] keys = new StateKey<?>[0];

	// Installed from client init, read from the network thread. A dedicated server keeps the
	// no-op: it sends on this channel and never receives on it.
	private static volatile Consumer<FriendlyByteBuf> sink = buf -> { };

	private ForgeStateSync() {
	}

	/**
	 * Called once from the store's own registration, with the same list — the exact place
	 * {@code FabricArchetypeStore}'s legacy arm calls {@code LegacyStateSync.register}.
	 */
	static void register(final List<StateKey<?>> table) {
		StateKey<?>[] built = new StateKey<?>[StateKey.count()];

		for (final StateKey<?> key : table) {
			built[key.index()] = key;
		}

		keys = built;
		CHANNEL.registerMessage(INDEX_STATE, Frame.class, ForgeStateSync::write,
				ForgeStateSync::read, ForgeStateSync::handle);

		// This node is the only one of the seven whose client can hold a stale, silently empty
		// copy of its own state, because it is the only one whose platform neither syncs
		// attachments nor transfers them across a client player swap. Arming the gate from the
		// backend that has the problem is what keeps every other node's gate open by
		// construction — see ClientSyncGate's header.
		ClientSyncGate.requireExplicitSync();
	}

	/**
	 * The client half's entry point, installed from client init.
	 *
	 * <p>The sink is handed the already-decoded frame because resolving an entity id needs
	 * {@code Minecraft}, which this source set must never name — the same division
	 * {@code client/LegacyStateSyncClient} makes on the Fabric node.
	 */
	public static void clientSink(final Consumer<FriendlyByteBuf> installed) {
		sink = installed;
	}

	/** One value changed on one entity: push it to whoever the key's scope says. */
	static <T> void push(final Entity target, final StateKey<T> key, final @Nullable T value) {
		if (key.wire() == null || !(target.level() instanceof ServerLevel level)) {
			return;
		}

		Frame frame = encode(target, key, value);

		if (key.sync() == StateKey.Sync.ALL_TRACKING) {
			// Every tracking player AND the owner — see the class javadoc.
			level.getChunkSource().broadcastAndSend(target, packet(frame));
			return;
		}

		if (target instanceof ServerPlayer owner) {
			CHANNEL.send(PacketDistributor.PLAYER.with(() -> owner), frame);
		}
	}

	/** Every value this entity carries for the given scope, to one viewer. */
	static void replay(final ArchetypeStore store, final Entity target, final ServerPlayer viewer,
			final boolean trackingOnly) {
		for (final StateKey<?> key : keys) {
			if (key == null || key.wire() == null) {
				continue;
			}

			if (trackingOnly && key.sync() != StateKey.Sync.ALL_TRACKING) {
				continue;
			}

			send(store, target, viewer, key);
		}

		// The end-of-replay marker, and ONLY for a player being sent its own full state — a
		// tracking replay is somebody else's entity and says nothing about whether the viewer's
		// own tree has arrived. It goes last, after the loop, so a client that has processed it
		// has processed everything before it: the channel is ordered, and this is what lets the
		// gate mean "complete" rather than "started".
		if (!trackingOnly && target == viewer) {
			CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer), replayDone(viewer));
		}
	}

	/** A frame carrying nothing but {@link #INDEX_REPLAY_DONE}, in the frozen triple's shape. */
	private static Frame replayDone(final ServerPlayer viewer) {
		FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
		buf.writeVarInt(INDEX_REPLAY_DONE);
		buf.writeBoolean(false);

		byte[] data = new byte[buf.readableBytes()];
		buf.readBytes(data);
		return new Frame(viewer.getId(), data);
	}

	private static <T> void send(final ArchetypeStore store, final Entity target,
			final ServerPlayer viewer, final StateKey<T> key) {
		T value = store.get(target, key);

		if (value != null) {
			CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer), encode(target, key, value));
		}
	}

	private static <T> Frame encode(final Entity target, final StateKey<T> key,
			final @Nullable T value) {
		FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
		buf.writeVarInt(key.index());
		buf.writeBoolean(value != null);

		if (value != null) {
			WireCodec<T> wire = key.wire();
			wire.write(buf, value);
		}

		byte[] data = new byte[buf.readableBytes()];
		buf.readBytes(data);
		return new Frame(target.getId(), data);
	}

	private static Packet<?> packet(final Frame frame) {
		return CHANNEL.toVanillaPacket(frame, NetworkDirection.PLAY_TO_CLIENT);
	}

	private static void write(final Frame frame, final FriendlyByteBuf buf) {
		buf.writeVarInt(frame.entityId());
		buf.writeByteArray(frame.value());
	}

	private static Frame read(final FriendlyByteBuf buf) {
		return new Frame(buf.readVarInt(), buf.readByteArray());
	}

	private static void handle(final Frame frame, final Supplier<NetworkEvent.Context> supplier) {
		NetworkEvent.Context context = supplier.get();
		context.setPacketHandled(true);

		if (context.getSender() != null) {
			// Clientbound only. A frame arriving from a client is a protocol violation, not
			// a state change; dropping it is what keeps this channel one-directional.
			return;
		}

		// The buffer is built over OUR byte array, so the sink may defer its read of it —
		// which it does, by one hop onto the client thread. Entity id first, matching the
		// frozen format and `apply`'s expectation that it has already been consumed.
		FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
		buf.writeVarInt(frame.entityId());
		buf.writeBytes(frame.value());
		sink.accept(buf);
	}

	/**
	 * The common half of the client apply, after the entity has been resolved.
	 *
	 * <p>Identical body to {@code LegacyStateSync.apply}, and deliberately so: this is the
	 * same fallback for the same reason, and the two have to agree about what the frozen
	 * bytes mean.
	 */
	public static void apply(final @Nullable Entity target, final FriendlyByteBuf buf) {
		int index = buf.readVarInt();
		boolean present = buf.readBoolean();

		// The one frame that is not a value. It is read exactly like every other — index, then
		// the present flag — so the buffer is fully consumed whichever branch runs.
		if (index == INDEX_REPLAY_DONE) {
			ClientSyncGate.markSynced(target);
			return;
		}

		if (index < 0 || index >= keys.length || keys[index] == null) {
			return;
		}

		applyOne(target, keys[index], present, buf);
	}

	private static <T> void applyOne(final @Nullable Entity target, final StateKey<T> key,
			final boolean present, final FriendlyByteBuf buf) {
		WireCodec<T> wire = key.wire();

		if (wire == null) {
			return;
		}

		// The value is read whether or not the entity is still here: a half-read buffer is a
		// desynchronised one, and an entity can leave between the send and the hop onto the
		// client thread.
		T value = present ? wire.read(buf) : null;

		if (target == null) {
			return;
		}

		if (value == null) {
			ArchetypeStore.INSTANCE.remove(target, key);
		} else {
			ArchetypeStore.INSTANCE.set(target, key, value);
		}
	}

	/** Entity id, then the frozen (keyIndex, present, value) triple as raw bytes. */
	private record Frame(int entityId, byte[] value) {
	}
}
