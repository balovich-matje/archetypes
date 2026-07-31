package com.archetypes.platform;

import java.util.ArrayList;
import java.util.List;

import com.archetypes.Archetypes;
import com.archetypes.state.StateKey;
import com.archetypes.state.WireCodec;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import net.minecraft.core.Registry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

// jspecify is one of the game's own libraries only from 1.21.11 up (Skill Proficiencies'
// conventions §5e-bis) and this node is 1.21.1, so this file uses the same annotation the
// shared tree forks to on this node. MEASURED rather than assumed, the same way it was next
// door: `:1.21.1-neoforge:dependencies --configuration compileClasspath` resolves
// `org.jetbrains:annotations` transitively through neoform and zero jspecify artifacts, so no
// node-script dependency is added for it.
import org.jetbrains.annotations.Nullable;

/**
 * {@link ArchetypeStore} on NeoForge: the 74-key table becomes 74 NeoForge data
 * attachments, registered directly into {@code NeoForgeRegistries.ATTACHMENT_TYPES}.
 *
 * <p>This is the ONLY file on this node that names {@code AttachmentType} or
 * {@code NeoForgeRegistries} — the seam-hygiene rule (conventions §5g), and a grep is
 * the review gate for it.
 *
 * <p><b>Both replay methods are genuine no-ops here, and that is measured rather than
 * hoped.</b> The floor the node script asserts (21.1.200, design R-09) buys native sync
 * for BOTH scopes the key table uses, so nothing in this class has to reproduce what
 * {@code platform/LegacyStateSync} does one node down:
 *
 * <ul>
 * <li>{@link StateKey.Sync#TARGET_ONLY} is
 *     {@code Builder.sync((holder, to) -> holder == to, codec)}. A change is pushed by
 *     {@code AttachmentSync.syncEntityUpdate}, which is called from {@code setData}; for a
 *     {@code ServerPlayer} that method explicitly appends the player itself to the watcher
 *     list ("Players do not track themselves",
 *     {@code attachment/AttachmentSync.java:128-141}), which is what makes a target-only
 *     attachment on a player reach its owner at all.</li>
 * <li>{@link StateKey.Sync#ALL_TRACKING} is {@code Builder.sync(codec)}, whose one-argument
 *     overload is exactly {@code sync((holder, to) -> true, codec)}
 *     ({@code AttachmentType.java:261-263}) — every tracking client, owner included.</li>
 * <li>The JOIN replay is {@code AttachmentSync.syncInitialPlayerAttachments(player)}, called
 *     from the patched {@code PlayerList.placeNewPlayer} before
 *     {@code firePlayerLoggedIn}. The START_TRACKING replay is
 *     {@code syncInitialEntityAttachments(entity, to, packetConsumer)}, whose signature is
 *     {@code ServerEntity}'s pairing-packet consumer. Sixteen broadcast keys carry other
 *     players' Bulwark aura, Ghost Armor, Bladestorm and Decimate poses, the mark, the
 *     Deadeye trail and the night form — <b>on this node the platform replays every one of
 *     them and this class does nothing</b>.</li>
 * </ul>
 *
 * <p><b>NO {@code DeferredRegister}, and that is the one deliberate deviation from Skill
 * Proficiencies' {@code NeoForgeSkillStore}.</b> Its store binds a
 * {@code DeferredRegister<AttachmentType<?>>} to the mod event bus from common init, which
 * means common init has to run at mod-CONSTRUCTION time, which in turn forced that repo to
 * defer only {@code ModItems.initialize()} into the registry window and to fork
 * {@code Specialities.onInitialize} to do it (its R-22). Registering here instead — one
 * {@code Registry.register} per key, from wherever {@code register(keys)} is called — has
 * exactly the same effect and removes that constraint, which is what lets
 * {@link ArchetypesNeoForge} defer the WHOLE shared init and leave {@code Archetypes.java}
 * untouched. Read that class for the window; the three facts this file depends on, all read
 * out of {@code neoforge-21.1.243-sources.jar}:
 *
 * <ol>
 * <li>{@code GameData.unfreezeData()} unfreezes EVERY {@code BaseMappedRegistry} in
 *     {@code BuiltInRegistries.REGISTRY} in one go, before the first {@code RegisterEvent} is
 *     posted, and {@code freezeData()} runs only after the last one
 *     ({@code registries/GameData.java:63-107}). So during ANY {@code RegisterEvent} every
 *     registry accepts writes, not just the one being posted.</li>
 * <li>{@code NeoForgeRegistries.ATTACHMENT_TYPES} is
 *     {@code new RegistryBuilder<>(Keys.ATTACHMENT_TYPES).create()}, i.e. a
 *     {@code MappedRegistry} — a {@code BaseMappedRegistry} — and
 *     {@code NeoForgeRegistriesSetup.registerRegistries} registers it, and
 *     {@code AttachmentSync.SYNCED_ATTACHMENT_TYPES} beside it, into the root registry
 *     during {@code NewRegistryEvent}, which runs BEFORE {@code unfreezeData}. Both are
 *     therefore unfrozen for the whole window.</li>
 * <li>The sync bookkeeping is a registry CALLBACK, not something {@code DeferredRegister}
 *     does: {@code NeoForgeRegistriesSetup} installs
 *     {@code AttachmentSync.ATTACHMENT_TYPE_ADD_CALLBACK} on {@code ATTACHMENT_TYPES}, and it
 *     mirrors any type with a sync handler into {@code SYNCED_ATTACHMENT_TYPES}. A callback
 *     fires on {@code Registry.register} however the entry got there.</li>
 * </ol>
 *
 * <p>Handles live in a flat array indexed by {@link StateKey#index()}, not a map, for the
 * same reason as on Fabric: these are read inside the damage funnel and inside per-tick loops
 * over every online player, so a read has to cost an array load.
 */
final class NeoForgeArchetypeStore implements ArchetypeStore {
	private AttachmentType<?>[] types = new AttachmentType<?>[0];

	/**
	 * The keys with no {@code persist} codec, kept for {@link #onPlayerClone}. See its
	 * javadoc — <b>this list is a bug fix, not bookkeeping</b>.
	 */
	private List<StateKey<?>> transientKeys = List.of();

	NeoForgeArchetypeStore() {
	}

	@Override
	public void register(final List<StateKey<?>> keys) {
		AttachmentType<?>[] built = new AttachmentType<?>[StateKey.count()];
		List<StateKey<?>> transients = new ArrayList<>();

		for (final StateKey<?> key : keys) {
			built[key.index()] = build(key);

			if (key.persist() == null) {
				transients.add(key);
			}
		}

		this.types = built;
		this.transientKeys = List.copyOf(transients);
		// Registered from the seam that owns the state rather than from common init, for the
		// same reason FabricArchetypeStore arms its own start-tracking replay there: nothing
		// outside `platform/` should have to learn that this loader's copy rule is narrower
		// than the key table's. EventPriority.LOW so it lands AFTER NeoForge's own
		// AttachmentInternals.onPlayerClone (a default-priority @SubscribeEvent), whose copy
		// this one completes rather than competes with.
		NeoForge.EVENT_BUS.addListener(EventPriority.LOW, PlayerEvent.Clone.class, this::onPlayerClone);
	}

	/**
	 * <b>THE ONE PLACE THIS NODE HAS TO REPRODUCE A CONTRACT INSTEAD OF INHERITING IT, and
	 * without it two things break that no build-shaped gate can see</b> (design R-20's whole
	 * subject). Both halves are measured, not reasoned:
	 *
	 * <ul>
	 * <li><b>Fabric</b> ({@code fabric-data-attachment-api-v1} 1.4.7, the 1.21.1 pin —
	 *     {@code impl/attachment/AttachmentTargetImpl.transfer}): iterates EVERY attachment on
	 *     the old instance and copies it when {@code !isDeath || type.copyOnDeath()}. Whether
	 *     the type persists to disk does not enter into it.</li>
	 * <li><b>NeoForge</b> ({@code attachment/AttachmentInternals.copyAttachments:29-32}): the
	 *     very first statement of the loop body is
	 *     {@code if (type.serializer == null) continue;} — a transient attachment is copied
	 *     NEVER, on death or on a dimension change, and there is no builder flag that changes
	 *     that. {@code AttachmentType.Builder.copyOnDeath()} in fact throws
	 *     {@code IllegalStateException("copyOnDeath requires a serializer")}
	 *     ({@code AttachmentType.java:224-227}), which is why {@link #build} only calls it for
	 *     a key that persists.</li>
	 * </ul>
	 *
	 * <p>What that costs without this listener, on this node only:
	 *
	 * <ol>
	 * <li>{@code ADVANCEMENT_COUNT}, {@code ADVANCEMENT_GOALS} and
	 *     {@code ADVANCEMENT_CHALLENGES} are the three keys in the table that ask for
	 *     {@code copyOnDeath} WITHOUT persisting, and they are the input to the perk-point
	 *     budget. Losing them on death zeroes a player's earned points until the next login or
	 *     the next advancement.</li>
	 * <li><b>Every transient key would be wiped by a dimension change</b> — the whole live
	 *     ability layer: Bulwark, Ghost Armor, the night form's channel, the mark, Deadeye,
	 *     Titan's Leap, every cooldown stamp. On Fabric a trip to the Nether preserves all of
	 *     it, because {@code isDeath} is false there and the copy is unconditional.</li>
	 * </ol>
	 *
	 * <p>So: for the transient subset only (the persistent subset NeoForge already filters by
	 * exactly Fabric's rule), copy on the same terms Fabric uses. The persistent keys are
	 * deliberately NOT touched here — copying them twice would be harmless but would hide
	 * which half of the contract each platform owns.
	 *
	 * <p>Residue, stated rather than papered over: {@code PlayerEvent.Clone} is players only.
	 * Fabric's {@code transfer} also runs for entity CONVERSION (zombie to drowned, and the
	 * like), which NeoForge routes through {@code LivingConversionEvent.Post} with
	 * {@code isDeath = true}. Archetypes hangs transient keys on non-players too
	 * ({@code MARKED_BY} on a mob, the two arrow flags), and none of them is
	 * {@code copyOnDeath}, so Fabric would not copy them across a conversion either — the two
	 * platforms agree there by accident rather than by design, and a future
	 * {@code copyOnDeath} key on a mob would need a second listener here.
	 */
	private void onPlayerClone(final PlayerEvent.Clone event) {
		Player from = event.getOriginal();
		Player to = event.getEntity();
		boolean death = event.isWasDeath();

		for (final StateKey<?> key : this.transientKeys) {
			if (!death || key.copyOnDeath()) {
				copyOne(from, to, key);
			}
		}
	}

	private <T> void copyOne(final Player from, final Player to, final StateKey<T> key) {
		T value = from.getExistingDataOrNull(type(key));

		if (value != null) {
			to.setData(type(key), value);
		}
	}

	private static <T> AttachmentType<T> build(final StateKey<T> key) {
		// A default-value supplier is mandatory on the builder and is never reached by this
		// class: every read below goes through getExistingDataOrNull, exactly as the Fabric
		// implementation's do. `null` would be the honest value and the builder forbids it,
		// so the supplier throws instead of inventing one — if anything in the tree ever
		// calls getData on one of these, that is a bug and it should say so loudly rather
		// than hand back a shape the caller never declared.
		AttachmentType.Builder<T> builder = AttachmentType.builder(() -> {
			throw new IllegalStateException(
					"Archetypes attachment '" + key.id() + "' has no default value; "
							+ "read it through ArchetypeStore#get, which never asks for one");
		});

		var persist = key.persist();

		if (persist != null) {
			builder.serialize(persist);
		}

		WireCodec<T> wire = key.wire();

		if (wire != null) {
			if (key.sync() == StateKey.Sync.ALL_TRACKING) {
				builder.sync(stream(wire));
			} else {
				builder.sync((holder, to) -> holder == to, stream(wire));
			}
		}

		// GUARDED, and the guard is load-bearing rather than defensive: `copyOnDeath()` throws
		// IllegalStateException("copyOnDeath requires a serializer") when no serializer has
		// been set (AttachmentType.java:224-227), and three keys in the table ask for exactly
		// that combination. The transient half of the copy rule is reproduced in
		// #onPlayerClone; read it before "simplifying" this line.
		if (key.copyOnDeath() && persist != null) {
			builder.copyOnDeath();
		}

		AttachmentType<T> type = builder.build();
		Registry.register(NeoForgeRegistries.ATTACHMENT_TYPES, Archetypes.id(key.id()), type);
		return type;
	}

	/**
	 * Adapts one portable {@link WireCodec} to the payload stack's {@code StreamCodec}.
	 *
	 * <p>{@code FriendlyByteBuf} rather than {@code RegistryFriendlyByteBuf}, the same
	 * choice and the same reason as the Fabric implementation: the builder wants a
	 * {@code StreamCodec<? super RegistryFriendlyByteBuf, T>}, and none of the 47 synced
	 * values carries registry-bound data, so the wider buffer type is both sufficient and
	 * honest about that.
	 *
	 * <p><b>Byte-for-byte the same encoding as the Fabric nodes', by construction</b> —
	 * both sides call the very same {@code WireCodec} constants, which is the whole point
	 * of the key table describing its wire format portably.
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
		return target.getExistingDataOrNull(type(key));
	}

	@Override
	public <T> void set(final Entity target, final StateKey<T> key, final T value) {
		target.setData(type(key), value);
	}

	@Override
	public <T> @Nullable T remove(final Entity target, final StateKey<T> key) {
		return target.removeData(type(key));
	}

	@Override
	public boolean has(final Entity target, final StateKey<?> key) {
		return target.hasData(this.types[key.index()]);
	}

	@Override
	public void resyncAll(final ServerPlayer player) {
		// No-op. The patched PlayerList pushes every synced attachment a player owns with
		// AttachmentSync.syncInitialPlayerAttachments, on login and on respawn, and every
		// later change rides setData -> syncEntityUpdate. See this class's javadoc for the
		// artifact lines; there is nothing left for this method to do on this node.
	}

	@Override
	public void syncOnStartTracking(final Entity tracked, final ServerPlayer viewer) {
		// No-op for the matching reason: an ALL_TRACKING attachment is replayed to a new
		// viewer by AttachmentSync.syncInitialEntityAttachments, from the entity's own
		// pairing packet.
	}
}
