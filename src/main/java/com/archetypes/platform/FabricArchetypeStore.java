package com.archetypes.platform;

import java.util.List;

import com.archetypes.Archetypes;
import com.archetypes.state.StateKey;
import com.archetypes.state.WireCodec;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

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
	}

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

	/**
	 * Adapts one portable {@link WireCodec} to the payload stack's {@code StreamCodec}.
	 *
	 * <p>{@code FriendlyByteBuf} rather than {@code RegistryFriendlyByteBuf} on
	 * purpose: the attachment builder wants a {@code StreamCodec<? super
	 * RegistryFriendlyByteBuf, T>}, and none of the 47 synced values carries
	 * registry-bound data, so the wider buffer type is both sufficient and honest
	 * about that.
	 */
	private static <T> StreamCodec<FriendlyByteBuf, T> stream(final WireCodec<T> wire) {
		return StreamCodec.of(wire::write, wire::read);
	}

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
	}

	@Override
	public <T> @Nullable T remove(final Entity target, final StateKey<T> key) {
		return ((AttachmentTarget) target).removeAttached(type(key));
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
	}

	@Override
	public void syncOnStartTracking(final Entity tracked, final ServerPlayer viewer) {
		// No-op for the same reason: `AttachmentSyncPredicate.all()` already replays
		// on start-tracking here.
	}
}
