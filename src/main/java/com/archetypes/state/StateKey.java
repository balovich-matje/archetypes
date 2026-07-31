package com.archetypes.state;

import java.util.concurrent.atomic.AtomicInteger;

import com.mojang.serialization.Codec;

//? if >=1.21.11 {
import org.jspecify.annotations.Nullable;
//?} else {
/*import org.jetbrains.annotations.Nullable;
*///?}

/**
 * One piece of attached state, described portably — the key table entry that
 * {@code ModState} declares and {@code platform/ArchetypeStore} turns into
 * whatever the loader underneath actually has.
 *
 * <p><b>Zero loader imports, by construction.</b> Nothing here names
 * {@code net.fabricmc}, NeoForge or Forge, and nothing names an API that arrives
 * after 1.20.1: {@link Codec} (com.mojang.serialization) and
 * {@code FriendlyByteBuf} (behind {@link WireCodec}) are the two oldest things
 * that can express "persist this" and "sync this". That is what lets the seventy-odd
 * declarations in {@code ModState} carry across all seven targets with no
 * preprocessor directive in them at all — every version and loader difference
 * lands inside one implementation file per loader instead.
 *
 * <p>Design {@code docs/MULTIVERSION.md} §3.1 explains why this is a key TABLE and
 * not Skill Proficiencies' one-method-per-value seam: at 74 keys and ~260 call
 * sites the named-method shape is 150 interface methods and 450 implementations.
 *
 * @param index       dense slot, assigned in construction order. The platform store
 *                    keeps its own handles in a flat array indexed by this, so a
 *                    read costs an array load rather than a hash lookup — these are
 *                    read inside the damage funnel and inside per-tick loops.
 * @param id          the on-disk / on-wire path, namespaced by the store. <b>Frozen:</b>
 *                    changing one orphans that value in every existing world.
 * @param type        the value's runtime class; a loader whose storage is typed
 *                    (Forge capabilities) needs it.
 * @param persist     codec for the save file, or {@code null} for transient state.
 * @param wire        codec for the sync packet, or {@code null} when {@code sync} is
 *                    {@link Sync#NONE}.
 * @param sync        who is told about a change.
 * @param copyOnDeath whether the value survives the player's death.
 */
public record StateKey<T>(int index, String id, Class<?> type, @Nullable Codec<T> persist,
		@Nullable WireCodec<T> wire, StateKey.Sync sync, boolean copyOnDeath) {
	/** Who a change is pushed to. */
	public enum Sync {
		/** Server-side only; no client is told. */
		NONE,
		/** The owning client only — the entity's own player. */
		TARGET_ONLY,
		/**
		 * Every client tracking the entity, the owner included. Sixteen keys use
		 * this and it is the one scope with no template anywhere below 1.20.5 or
		 * on Forge (design §3.1 / R-B1).
		 */
		ALL_TRACKING
	}

	private static final AtomicInteger NEXT_INDEX = new AtomicInteger();

	/** How many keys have been constructed; the platform store's array length. */
	public static int count() {
		return NEXT_INDEX.get();
	}

	public static <T> Builder<T> of(final String id, final Class<?> type) {
		return new Builder<>(id, type);
	}

	/** Mirrors the attachment builder the Fabric-only tree used, one call per clause. */
	public static final class Builder<T> {
		private final String id;
		private final Class<?> type;
		private @Nullable Codec<T> persist;
		private @Nullable WireCodec<T> wire;
		private Sync sync = Sync.NONE;
		private boolean copyOnDeath;

		private Builder(final String id, final Class<?> type) {
			this.id = id;
			this.type = type;
		}

		public Builder<T> persist(final Codec<T> codec) {
			this.persist = codec;
			return this;
		}

		public Builder<T> sync(final WireCodec<T> codec, final Sync scope) {
			this.wire = codec;
			this.sync = scope;
			return this;
		}

		public Builder<T> copyOnDeath() {
			this.copyOnDeath = true;
			return this;
		}

		public StateKey<T> build() {
			return new StateKey<>(NEXT_INDEX.getAndIncrement(), this.id, this.type,
					this.persist, this.wire, this.sync, this.copyOnDeath);
		}
	}
}
