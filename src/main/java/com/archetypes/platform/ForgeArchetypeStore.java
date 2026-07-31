package com.archetypes.platform;

import java.util.List;

import com.archetypes.Archetypes;
import com.archetypes.state.StateKey;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.IEventBus;

import com.mojang.serialization.Codec;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * LexForge implementation of {@link ArchetypeStore} for the {@code 1.20.1-forge} node: the
 * 74-entry key table becomes ONE capability holding a flat array.
 *
 * <p>ARTIFACT PROVENANCE — every signature read out of
 * {@code forge-1.20.1-47.4.22-sources.jar}, not recalled:
 *
 * <ul>
 * <li>{@code common/capabilities/CapabilityManager.java} —
 *     {@code static <T> Capability<T> get(CapabilityToken<T>)}.</li>
 * <li>{@code common/capabilities/RegisterCapabilitiesEvent.java} —
 *     {@code final class … implements IModBusEvent} with {@code <T> void register(Class<T>)}.
 *     Being {@code IModBusEvent} it needs the MOD bus, not
 *     {@code MinecraftForge.EVENT_BUS}.</li>
 * <li>{@code common/capabilities/ICapabilitySerializable.java} —
 *     {@code extends ICapabilityProvider, INBTSerializable<T>}, and
 *     {@code common/util/INBTSerializable.java} is {@code T serializeNBT()} /
 *     {@code void deserializeNBT(T)} with <b>no {@code HolderLookup.Provider}
 *     parameter</b> — that arrived later; do not copy a 1.21-era signature here.</li>
 * <li>{@code event/AttachCapabilitiesEvent.java} — {@code class AttachCapabilitiesEvent<T>
 *     extends GenericEvent<T>} with {@code T getObject()} and
 *     {@code void addCapability(ResourceLocation, ICapabilityProvider)}. A
 *     {@code GenericEvent} needs {@code IEventBus.addGenericListener(Class, Consumer)}.</li>
 * <li>{@code patches/net/minecraft/world/entity/Entity.java.patch} — {@code Entity extends
 *     CapabilityProvider<Entity>}, {@code gatherCapabilities()} in the constructor,
 *     {@code invalidateCaps()} on removal, and {@code reviveCaps()} public.</li>
 * </ul>
 *
 * <p><b>WHY ONE CAPABILITY AND ONE ARRAY, and not Skill Proficiencies' three.</b> That
 * seam names five values and can afford a capability per group. This one describes 74 as
 * DATA ({@link StateKey}), and the store's whole job is to be indexable: reads happen
 * inside the damage funnel and inside per-tick loops over every online player, so a read
 * has to cost an array load. One {@link EntityState} per entity, holding an
 * {@code Object[]} allocated on first WRITE, is the cheapest shape that keeps that
 * promise. An entity that never receives a value carries two small objects and no array.
 *
 * <p><b>WHY IT IS ATTACHED TO EVERY ENTITY.</b> The keys are not all player keys:
 * {@code MARKED_BY} lands on any {@code LivingEntity}, and {@code SPELLBOW_ARROW},
 * {@code DEADEYE_ARROW}, {@code TRUE_SHOT_ARROW}, {@code DEADEYE_SIEGE_ARROW},
 * {@code DEADEYE_PHASED} and {@code REFLECT_AIM} land on arrows. Narrowing the attachment
 * to a guessed set of classes would make a {@code set} silently evaporate on anything left
 * out, which is the failure class R-20 exists to catch — and fabric-api's attachments have
 * no such restriction, so a narrower store here would be a per-node behaviour difference
 * rather than an optimisation. Skill Proficiencies narrowed its own to {@code Projectile}
 * and {@code Mob} because it could enumerate its five call sites; 260 cannot be enumerated
 * that way.
 *
 * <p><b>PERSISTENCE.</b> Nine of the 74 keys carry a {@code persist} codec and every one of
 * them is a player key, so only players get the SERIALISING provider — everything else gets
 * a plain {@code ICapabilityProvider} that Forge never writes, which is exactly what an
 * attachment with no {@code persistent(...)} clause gives on Fabric.
 *
 * <p><b>WORLD COMPATIBILITY WITH THE FABRIC 1.20.1 JAR — what is achieved and what cannot
 * be.</b> The per-key VALUE bytes match exactly: both loaders run the same
 * {@code StateKey.persist()} codec through the same {@code NbtOps.INSTANCE}, and the
 * sub-keys inside this provider's tag are the same {@code archetypes:<id>} strings
 * fabric-api uses. Two containers above that differ and both are loader-owned, so no code
 * here can reach them: fabric-api nests attachments under {@code fabric:attachments} keyed
 * by the attachment id, and Forge nests providers under {@code ForgeCaps} keyed by the
 * provider id — which is why this provider's own tag is one level deeper than fabric's
 * (a provider per key would flatten it, but then only the first of nine could answer
 * {@code getCapability}, since {@code CapabilityDispatcher} returns the first non-empty
 * provider). A world carried across will not find its archetype. DOCUMENTED AS IMPOSSIBLE
 * rather than left to be discovered, and what IS matched is the part a one-shot importer
 * would need.
 *
 * <p><b>SYNC.</b> Capabilities have none, so {@link #resyncAll} and
 * {@link #syncOnStartTracking} are load-bearing here rather than the no-ops they are on
 * every Fabric node from 1.20.5 up, and every {@link #set}/{@link #remove} pushes. All four
 * paths are {@link ForgeStateSync}; read its header for the contract they reproduce.
 */
final class ForgeArchetypeStore implements ArchetypeStore {
	/**
	 * The one capability. The {@code new CapabilityToken<>(){}} anonymous subclass is
	 * mandatory rather than stylistic: {@code CapabilityToken.getType()} is implemented by a
	 * Forge class transformer that reads the generic argument off the subclass signature, so
	 * a raw token loses the type and {@code CapabilityManager.get} answers with the wrong
	 * capability.
	 */
	private static final Capability<EntityState> STATE =
			CapabilityManager.get(new CapabilityToken<EntityState>() { });

	/**
	 * Provider keys. These are NOT the Fabric attachment ids — a Forge provider key is the
	 * NBT tag name {@code CapabilityDispatcher} writes under {@code ForgeCaps}, so these two
	 * strings ARE world format from the day this node first ships. Frozen, exactly as the
	 * key ids in {@code ModState} are.
	 */
	private static final Identifier PLAYER_STATE_KEY = Archetypes.id("player_state");

	private static final Identifier ENTITY_STATE_KEY = Archetypes.id("entity_state");

	/** The key table, indexed by {@link StateKey#index()}; null slots are impossible. */
	private static StateKey<?>[] keys = new StateKey<?>[0];

	ForgeArchetypeStore() {
	}

	/**
	 * The ONE piece of wiring that cannot wait for {@link #register}, and the reason it is a
	 * separate static method called straight from {@code ArchetypesForge}'s constructor.
	 *
	 * <p>MEASURED from {@code ForgeStatesProvider} in
	 * {@code forge-1.20.1-47.4.22-universal.jar}, which is the whole mod-loading order in one
	 * class:
	 *
	 * <pre>
	 *   CONSTRUCT
	 *     CREATE_REGISTRIES     NewRegistryEvent
	 *     OBJECT_HOLDERS
	 *     INJECT_CAPABILITIES   RegisterCapabilitiesEvent      &lt;-- this listener must exist by here
	 *     UNFREEZE_DATA         GameData.unfreezeData()
	 *     LOAD_REGISTRIES       GameData.postRegisterEvents()  &lt;-- where onInitialize() runs
	 *   … CONFIG_LOAD, COMMON_SETUP, SIDED_SETUP …
	 *     FREEZE_DATA / NETWORK_LOCK
	 * </pre>
	 *
	 * <p>{@code ArchetypesForge} defers the whole shared init into the item
	 * {@code RegisterEvent}, which lands in LOAD_REGISTRIES — one state AFTER
	 * {@code RegisterCapabilitiesEvent} has already been posted. A capability that never
	 * reaches {@code RegisterCapabilitiesEvent.register} stays {@code isRegistered() == false}
	 * and {@code Capability.orEmpty} then answers empty for every lookup: no exception, no log
	 * line, every one of the 74 keys silently absent forever. So this half is registered at
	 * CONSTRUCT and the rest from {@link #register}.
	 */
	static void registerCapabilities(final IEventBus modEventBus) {
		// Two buses, and a listener on the wrong one is a silent no-op. RegisterCapabilitiesEvent
		// implements IModBusEvent -> the MOD bus.
		modEventBus.addListener(ForgeArchetypeStore::onRegisterCapabilities);
	}

	@Override
	public void register(final List<StateKey<?>> table) {
		StateKey<?>[] built = new StateKey<?>[StateKey.count()];

		for (final StateKey<?> key : table) {
			built[key.index()] = key;
		}

		keys = built;

		// AttachCapabilitiesEvent is a GenericEvent, so it must go through
		// addGenericListener and the class filter is what Forge dispatches on. Game bus, and
		// game-bus listeners may be added any time before a world loads — which is why only
		// the mod-bus half above had to move earlier.
		MinecraftForge.EVENT_BUS.addGenericListener(Entity.class, ForgeArchetypeStore::onAttach);

		// Replaces the attachment builder's copyOnDeath() clause. Registered here, with the
		// store that owns the state, rather than in common init: it is persistence plumbing.
		MinecraftForge.EVENT_BUS.addListener(ForgeArchetypeStore::onPlayerClone);

		// The sync half, armed from exactly where FabricArchetypeStore's legacy arm arms its
		// own — see ForgeStateSync's header for why it is a channel of its own.
		ForgeStateSync.register(table);
		MinecraftForge.EVENT_BUS.addListener((PlayerEvent.StartTracking event) -> {
			if (event.getEntity() instanceof ServerPlayer viewer) {
				this.syncOnStartTracking(event.getTarget(), viewer);
			}
		});
	}

	private static void onRegisterCapabilities(final RegisterCapabilitiesEvent event) {
		event.register(EntityState.class);
	}

	private static void onAttach(final AttachCapabilitiesEvent<Entity> event) {
		if (event.getObject() instanceof Player) {
			event.addCapability(PLAYER_STATE_KEY, new PlayerStateProvider());
		} else {
			event.addCapability(ENTITY_STATE_KEY, new EntityStateProvider());
		}
	}

	private static void onPlayerClone(final PlayerEvent.Clone event) {
		// reviveCaps()/invalidateCaps() are mandatory, and the artifact says why: the old
		// player has already been through Entity.remove, which calls invalidateCaps
		// (patches/net/minecraft/world/entity/Entity.java.patch), so getCapability on it
		// answers empty without the revive.
		event.getOriginal().reviveCaps();

		try {
			EntityState from = event.getOriginal().getCapability(STATE).orElse(null);
			EntityState to = event.getEntity().getCapability(STATE).orElse(null);

			if (from == null || to == null) {
				return;
			}

			// The two cases fabric-api distinguishes, reproduced: a DEATH clone carries only
			// the keys that asked for copyOnDeath, and any other clone (the End-return
			// portal, which ServerPlayer.restoreFrom drives through the same event) carries
			// everything. Getting this backwards is not a crash — it is either a respawn that
			// keeps a cooldown it should have lost, or an End return that forgets an
			// archetype.
			for (final StateKey<?> key : keys) {
				if (key == null || (event.isWasDeath() && !key.copyOnDeath())) {
					continue;
				}

				to.set(key.index(), from.get(key.index()));
			}
		} finally {
			event.getOriginal().invalidateCaps();
		}
	}

	private static @Nullable EntityState state(final Entity target) {
		return target.getCapability(STATE).orElse(null);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> @Nullable T get(final Entity target, final StateKey<T> key) {
		EntityState state = state(target);
		return state == null ? null : (T) state.get(key.index());
	}

	@Override
	public <T> void set(final Entity target, final StateKey<T> key, final T value) {
		EntityState state = state(target);

		if (state == null) {
			return;
		}

		state.set(key.index(), value);
		ForgeStateSync.push(target, key, value);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> @Nullable T remove(final Entity target, final StateKey<T> key) {
		EntityState state = state(target);

		if (state == null) {
			return null;
		}

		T previous = (T) state.get(key.index());
		state.set(key.index(), null);
		// Unconditional, exactly as the Fabric legacy arm is: a remove is a DISTINCT message,
		// not an absent one, because the client has to drop the value rather than merely stop
		// hearing about it.
		ForgeStateSync.push(target, key, null);
		return previous;
	}

	@Override
	public boolean has(final Entity target, final StateKey<?> key) {
		EntityState state = state(target);
		return state != null && state.get(key.index()) != null;
	}

	@Override
	public void resyncAll(final ServerPlayer player) {
		// Both scopes, because a player's own ALL_TRACKING values are also their own: the join
		// hook is the only thing that runs before anyone can be tracking them.
		ForgeStateSync.replay(this, player, player, false);
	}

	@Override
	public void syncOnStartTracking(final Entity tracked, final ServerPlayer viewer) {
		ForgeStateSync.replay(this, tracked, viewer, true);
	}

	private static <T> @Nullable Tag encode(final StateKey<T> key, final Object value) {
		Codec<T> codec = key.persist();

		if (codec == null) {
			return null;
		}

		@SuppressWarnings("unchecked")
		T typed = (T) value;
		return codec.encodeStart(NbtOps.INSTANCE, typed)
				.resultOrPartial(error -> Archetypes.LOGGER.error(
						"Failed to write {}: {}", key.id(), error))
				.orElse(null);
	}

	private static <T> @Nullable Object decode(final StateKey<T> key, final Tag tag) {
		Codec<T> codec = key.persist();

		if (codec == null) {
			return null;
		}

		return codec.parse(NbtOps.INSTANCE, tag)
				.resultOrPartial(error -> Archetypes.LOGGER.error(
						"Failed to read {}: {}", key.id(), error))
				.orElse(null);
	}

	// ---------------------------------------------------------------------------------
	// The value holder. Mutable, because LazyOptional caches the instance its supplier
	// returned and the object identity behind a capability therefore cannot be swapped.
	// ---------------------------------------------------------------------------------

	/** One entity's whole state: a flat array, allocated on the first write. */
	static final class EntityState {
		private @Nullable Object[] values;

		@Nullable Object get(final int index) {
			Object[] slots = this.values;
			return slots == null || index >= slots.length ? null : slots[index];
		}

		void set(final int index, final @Nullable Object value) {
			Object[] slots = this.values;

			if (slots == null) {
				if (value == null) {
					return;
				}

				slots = new Object[StateKey.count()];
				this.values = slots;
			}

			slots[index] = value;
		}
	}

	// ---------------------------------------------------------------------------------
	// Providers.
	// ---------------------------------------------------------------------------------

	/**
	 * The serialising provider, attached to players only. Writes one sub-tag per persistent
	 * key, keyed by the same {@code archetypes:<id>} string fabric-api uses — see the class
	 * javadoc for what that does and does not buy.
	 */
	private static final class PlayerStateProvider implements ICapabilitySerializable<CompoundTag> {
		private final EntityState state = new EntityState();

		private final LazyOptional<EntityState> optional = LazyOptional.of(() -> this.state);

		@Override
		public <T> LazyOptional<T> getCapability(final Capability<T> cap, final @Nullable Direction side) {
			return STATE.orEmpty(cap, this.optional);
		}

		@Override
		public CompoundTag serializeNBT() {
			CompoundTag tag = new CompoundTag();

			for (final StateKey<?> key : keys) {
				if (key == null || key.persist() == null) {
					continue;
				}

				Object value = this.state.get(key.index());

				if (value == null) {
					continue;
				}

				Tag written = encode(key, value);

				if (written != null) {
					tag.put(Archetypes.id(key.id()).toString(), written);
				}
			}

			return tag;
		}

		@Override
		public void deserializeNBT(final CompoundTag tag) {
			for (final StateKey<?> key : keys) {
				if (key == null || key.persist() == null) {
					continue;
				}

				String id = Archetypes.id(key.id()).toString();

				if (!tag.contains(id)) {
					continue;
				}

				this.state.set(key.index(), decode(key, tag.get(id)));
			}
		}
	}

	/**
	 * Everything that is not a player. No {@code INBTSerializable}, so Forge never writes it
	 * — which is what an attachment with no {@code persistent(...)} clause gives on Fabric,
	 * and all 65 non-persistent keys want exactly that.
	 */
	private static final class EntityStateProvider implements ICapabilityProvider {
		private final EntityState state = new EntityState();

		private final LazyOptional<EntityState> optional = LazyOptional.of(() -> this.state);

		@Override
		public <T> LazyOptional<T> getCapability(final Capability<T> cap, final @Nullable Direction side) {
			return STATE.orEmpty(cap, this.optional);
		}
	}
}
