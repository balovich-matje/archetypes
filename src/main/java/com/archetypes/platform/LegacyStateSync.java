package com.archetypes.platform;

// STAGE 5 — THE ATTACHMENT-SYNC FALLBACK (design §3.1 / R-B1), and the largest genuinely new
// infrastructure in the port. fabric-api 0.92.11's attachment API has a persistent builder
// and NO SYNC AT ALL: no `syncWith`, no `AttachmentSyncPredicate`, not even the
// `create(Identifier, Consumer)` overload the modern arm uses. So the 16 `ALL_TRACKING` keys
// (every renderer flag other players read) and the 31 `TARGET_ONLY` ones (everything the
// owner's own HUD and tree screen read) have to be moved by hand, over one channel.
//
// THE CONTRACT BEING REPRODUCED, taken from fabric-api's own implementation rather than from
// the idea of it, because R-20 is about exactly this:
//
//  1. A synced attachment is sent WHENEVER IT CHANGES, set and remove alike, and a remove is
//     a distinct message rather than an absent one — the client has to drop the value, not
//     merely stop hearing about it.
//  2. `targetOnly()` reaches the entity itself when it is a player, and nobody else.
//  3. `all()` reaches every player tracking the entity AND the entity itself when it is a
//     player (fabric's `PlayerLookup.tracking` deliberately excludes the entity's own
//     player, and fabric-api adds it back for this predicate).
//  4. A client that STARTS TRACKING an entity is sent every `all()` value it already
//     carries — otherwise a Bulwark that went up before the viewer came into range is
//     invisible until it changes.
//  5. A player JOINING is sent their own values back (`resyncAll`, called from common init's
//     join hook on every node).
//
// WIRE FORMAT, frozen with the rest of `state/WireId`'s contract: varInt entity id, varInt
// key INDEX, boolean present, then the key's own `WireCodec` bytes if present. The index and
// not the string id, because `StateKey.index()` is assigned by declaration order in
// `ModState` and both sides run the same registration — the same reason the store's own
// handle array is indexed by it.
//
// The whole compilation unit is below-1.20.5 only (conventions §4's whole-file form).
// STAGE 6 SCOPED IT TO FABRIC, AND THAT LEAVES A HOLE THE FORGE NODE HAS TO FILL. `<1.20.5` is
// true on 1.20.1-forge too, but every line of the body below is fabric-api — `PlayerLookup`,
// `ServerPlayNetworking`, and a channel registered through `FabricNet`. So the predicate is
// scoped rather than widened.
//
// WHAT 1.20.1-FORGE INHERITS FROM THIS FILE: nothing yet, and it needs all of it. Forge
// capabilities have no sync of any kind, so that node is in exactly the position this file was
// written for — 16 `ALL_TRACKING` keys and 31 `TARGET_ONLY` ones that no client will ever see
// without a broadcast of its own. Design R-B1's prescription is ONE fallback with three
// consumers, built on the `Net` seam; this implementation predates the loader axis and calls
// fabric-api directly instead, so generalising it (moving the four paths onto
// `Net.INSTANCE.sendToClient` and keeping the frozen wire format) is the honest way for that
// node to reuse it. That is Stage 6b's call and its largest single piece of work — NOT
// something to bolt on here, where no node can exercise it.
//
// THE FAILURE MODE IF IT IS SKIPPED, measured on this very node family in Stage 5: there is no
// error anywhere. The server sends on a channel the client never registered, vanilla drops it
// silently, every server-authoritative behaviour keeps working, and the whole visible half is
// blank. A dedicated-server smoke cannot see it by construction.
//? if fabric && <1.20.5 {
/*import java.util.List;

import com.archetypes.Archetypes;
import com.archetypes.state.StateKey;
import com.archetypes.state.WireCodec;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

public final class LegacyStateSync {
	public static final Identifier CHANNEL = Archetypes.id("state_sync");

	private static StateKey<?>[] keys = new StateKey<?>[0];

	private LegacyStateSync() {
	}

	/^* Called once from the store's own registration, with the same list. ^/
	static void register(final List<StateKey<?>> table) {
		StateKey<?>[] built = new StateKey<?>[StateKey.count()];

		for (final StateKey<?> key : table) {
			built[key.index()] = key;
		}

		keys = built;
	}

	/^* One value changed on one entity: push it to whoever the key's scope says. ^/
	static <T> void push(final Entity target, final StateKey<T> key, final @Nullable T value) {
		if (key.wire() == null || target.level().isClientSide()) {
			return;
		}

		FriendlyByteBuf buf = encode(target, key, value);

		if (key.sync() == StateKey.Sync.ALL_TRACKING) {
			for (final ServerPlayer viewer : PlayerLookup.tracking(target)) {
				ServerPlayNetworking.send(viewer, CHANNEL, copy(buf));
			}
		}

		if (target instanceof ServerPlayer owner) {
			ServerPlayNetworking.send(owner, CHANNEL, copy(buf));
		}
	}

	/^* Every value this entity carries for the given scope, to one viewer. ^/
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
	}

	private static <T> void send(final ArchetypeStore store, final Entity target,
			final ServerPlayer viewer, final StateKey<T> key) {
		T value = store.get(target, key);

		if (value != null) {
			ServerPlayNetworking.send(viewer, CHANNEL, encode(target, key, value));
		}
	}

	private static <T> FriendlyByteBuf encode(final Entity target, final StateKey<T> key,
			final @Nullable T value) {
		FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
		buf.writeVarInt(target.getId());
		buf.writeVarInt(key.index());
		buf.writeBoolean(value != null);

		if (value != null) {
			WireCodec<T> wire = key.wire();
			wire.write(buf, value);
		}

		return buf;
	}

	// One buffer per recipient: `ServerPlayNetworking.send` reads the buffer it is given,
	// so the same one cannot be handed to two players.
	private static FriendlyByteBuf copy(final FriendlyByteBuf buf) {
		return new FriendlyByteBuf(Unpooled.copiedBuffer(buf));
	}

	/^*
	 * The client half's entry point: the entity has already been resolved (that needs
	 * `Minecraft`, which this source set cannot name — see `client/ClientHandDown`), and
	 * everything after it is common.
	 ^/
	public static void apply(final @Nullable Entity target, final FriendlyByteBuf buf) {
		int index = buf.readVarInt();
		boolean present = buf.readBoolean();

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

		// The value is read whether or not the entity is still here: a half-read buffer is
		// a desynchronised one, and an entity can leave between the send and the hop onto
		// the client thread.
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
}
*///?}
