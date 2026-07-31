package com.archetypes.platform;

import java.util.List;

import com.archetypes.Archetypes;
import com.archetypes.state.StateKey;
import com.archetypes.state.WireCodec;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
//? if >=1.20.5 {
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
//?}
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
//? if >=1.20.5 {
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
//?}
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
//? if >=1.21.11 {
import org.jspecify.annotations.Nullable;
//?} else {
/*import org.jetbrains.annotations.Nullable;
*///?}

/**
 * {@link ArchetypeStore} on Fabric: the key table becomes fabric-api attachments.
 *
 * <p>This is the ONLY file in the mod that names {@code AttachmentRegistry},
 * {@code AttachmentType}, {@code AttachmentTarget} or {@code AttachmentSyncPredicate}
 * — the seam-hygiene rule (Skill Proficiencies' conventions §5g), and a grep is the
 * review gate for it.
 *
 * <p>Handles live in a flat array indexed by {@link StateKey#index()}, not a map:
 * these are read inside the damage funnel and inside per-tick loops over every
 * online player, so a read has to cost an array load.
 */
final class FabricArchetypeStore implements ArchetypeStore {
	private AttachmentType<?>[] types = new AttachmentType<?>[0];

	FabricArchetypeStore() {
	}

	@Override
	public void register(final List<StateKey<?>> keys) {
		AttachmentType<?>[] built = new AttachmentType<?>[StateKey.count()];

		for (final StateKey<?> key : keys) {
			built[key.index()] = build(key);
		}

		this.types = built;
		// STAGE 5: below 1.20.5 the attachment API cannot sync, so the same key table is
		// handed to the hand-rolled channel and the start-tracking replay is armed here —
		// in the seam that owns the state, rather than in common init, so nothing outside
		// `platform/` learns that this node is different.
		//? if <1.20.5 {
		/*LegacyStateSync.register(keys);
		net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents.START_TRACKING.register(
				(tracked, viewer) -> this.syncOnStartTracking(tracked, viewer));
		*///?}
	}

	// The builder itself forks: 0.92.11 has `AttachmentRegistry.builder()` +
	// `buildAndRegister(id)` and no `create(Identifier, Consumer)` at all, and neither
	// `syncWith` nor `AttachmentSyncPredicate` exists there — the sync half moves to
	// `LegacyStateSync`. `persistent` and `copyOnDeath` are the same two calls on both.
	//? if >=1.20.5 {
	private static <T> AttachmentType<T> build(final StateKey<T> key) {
		return AttachmentRegistry.create(Archetypes.id(key.id()), builder -> {
			var persist = key.persist();

			if (persist != null) {
				builder.persistent(persist);
			}

			WireCodec<T> wire = key.wire();

			if (wire != null) {
				builder.syncWith(stream(wire), key.sync() == StateKey.Sync.ALL_TRACKING
						? AttachmentSyncPredicate.all()
						: AttachmentSyncPredicate.targetOnly());
			}

			if (key.copyOnDeath()) {
				builder.copyOnDeath();
			}
		});
	}
	//?} else {
	/*private static <T> AttachmentType<T> build(final StateKey<T> key) {
		AttachmentRegistry.Builder<T> builder = AttachmentRegistry.builder();
		var persist = key.persist();

		if (persist != null) {
			builder.persistent(persist);
		}

		if (key.copyOnDeath()) {
			builder.copyOnDeath();
		}

		return builder.buildAndRegister(Archetypes.id(key.id()));
	}
	*///?}

	/**
	 * Adapts one portable {@link WireCodec} to the payload stack's {@code StreamCodec}.
	 *
	 * <p>{@code FriendlyByteBuf} rather than {@code RegistryFriendlyByteBuf} on
	 * purpose: the attachment builder wants a {@code StreamCodec<? super
	 * RegistryFriendlyByteBuf, T>}, and none of the 47 synced values carries
	 * registry-bound data, so the wider buffer type is both sufficient and honest
	 * about that.
	 */
	//? if >=1.20.5 {
	private static <T> StreamCodec<FriendlyByteBuf, T> stream(final WireCodec<T> wire) {
		return StreamCodec.of(wire::write, wire::read);
	}
	//?}

	@SuppressWarnings("unchecked")
	private <T> AttachmentType<T> type(final StateKey<T> key) {
		return (AttachmentType<T>) this.types[key.index()];
	}

	@Override
	public <T> @Nullable T get(final Entity target, final StateKey<T> key) {
		return ((AttachmentTarget) target).getAttached(type(key));
	}

	@Override
	public <T> void set(final Entity target, final StateKey<T> key, final T value) {
		((AttachmentTarget) target).setAttached(type(key), value);
		//? if <1.20.5 {
		/*LegacyStateSync.push(target, key, value);
		*///?}
	}

	@Override
	public <T> @Nullable T remove(final Entity target, final StateKey<T> key) {
		//? if >=1.20.5 {
		return ((AttachmentTarget) target).removeAttached(type(key));
		//?} else {
		/*T previous = ((AttachmentTarget) target).removeAttached(type(key));
		LegacyStateSync.push(target, key, null);
		return previous;
		*///?}
	}

	@Override
	public boolean has(final Entity target, final StateKey<?> key) {
		return ((AttachmentTarget) target).hasAttached(this.types[key.index()]);
	}

	@Override
	public void resyncAll(final ServerPlayer player) {
		// No-op: fabric-api syncs a `syncWith` attachment to its target itself, on
		// join and on every change. The method exists for the nodes that cannot —
		// see the interface.
		//? if <1.20.5 {
		/*// Both scopes, because a player's own `all()` values are also their own: the
		// join hook is the only thing that runs before anyone can be tracking them.
		LegacyStateSync.replay(this, player, player, false);
		*///?}
	}

	@Override
	public void syncOnStartTracking(final Entity tracked, final ServerPlayer viewer) {
		// No-op for the same reason: `AttachmentSyncPredicate.all()` already replays
		// on start-tracking here.
		//? if <1.20.5 {
		/*LegacyStateSync.replay(this, tracked, viewer, true);
		*///?}
	}
}
