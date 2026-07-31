package com.archetypes.platform;

import com.archetypes.Archetypes;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.RegisterEvent;

import net.minecraft.core.registries.Registries;

/**
 * The NeoForge entrypoint — {@code fabric.mod.json}'s {@code "main"} entry, as a
 * {@code @Mod} class. It stashes the mod event bus and runs the shared
 * {@code Archetypes.onInitialize()}; the SECOND {@code @Mod} class, for the client half, is
 * {@code client/NeoForgeClientEvents}.
 *
 * <p><b>WHY THE WHOLE INIT IS DEFERRED INTO THE ITEM {@code RegisterEvent} — design R-22,
 * widened.</b> Skill Proficiencies' finding was {@code Item.<init>} asking the ITEM registry
 * for an intrusive holder outside the registration window, which is a boot crash. This mod
 * has five more registries in the same position, every one of them written from a static
 * field initialiser that runs the moment the class is touched:
 *
 * <pre>
 *   ModItems        30 items, and 28 of them through Item.&lt;init&gt;
 *   ModEntities     EntityType.Builder.build -&gt; ENTITY_TYPE
 *   ModParticles    new SimpleParticleType(false) -&gt; PARTICLE_TYPE
 *   ManaEffects     2 MobEffect subclasses  -&gt; MOB_EFFECT
 *   RadianceEffect  1 MobEffect subclass    -&gt; MOB_EFFECT
 *   ManaPotions     4 Potions               -&gt; POTION
 *   AmnesiaPotions  1 MobEffect + 2 Potions -&gt; MOB_EFFECT, POTION
 * </pre>
 *
 * <p>All six are reached from {@code Archetypes.onInitialize()}'s first eight lines, so
 * running that method at mod-construction time is seven separate versions of the same
 * {@code Registry is already frozen} crash. Deferring the WHOLE method is therefore simpler
 * than deferring some of it, and it costs nothing: <b>every registry is unfrozen for the
 * whole window, not just the one being posted</b> — {@code GameData.unfreezeData()} walks
 * every {@code BaseMappedRegistry} in {@code BuiltInRegistries.REGISTRY} before the first
 * {@code RegisterEvent} and {@code freezeData()} runs only after the last
 * ({@code registries/GameData.java:63-107}). {@code Registries.ITEM} is simply a
 * well-defined point inside it.
 *
 * <p><b>This is a DEVIATION from Skill Proficiencies' NeoForge node, and the reason it is
 * available here is one line in {@code NeoForgeArchetypeStore}.</b> That repo deferred only
 * {@code ModItems.initialize()} and forked {@code Specialities.onInitialize} to do it,
 * because its {@code NeoForgeSkillStore.initialize()} calls
 * {@code ATTACHMENTS.register(modEventBus)} — adding a {@code RegisterEvent} listener while
 * the bus is dispatching {@code RegisterEvent}, i.e. mutating the very {@code ListenerList}
 * being iterated. This node's store uses no {@code DeferredRegister} at all: it calls
 * {@code Registry.register(NeoForgeRegistries.ATTACHMENT_TYPES, …)} directly, which needs no
 * listener and no bus. With that constraint gone the deferral moves out of shared code
 * entirely — <b>{@code Archetypes.java} needs no {@code neoforge} arm and is not touched by
 * this node</b>, which is worth more than matching the shape next door: five Fabric nodes
 * are required to stay instruction-identical while this axis lands.
 *
 * <p>Three orderings this depends on, each read out of {@code neoforge-21.1.243-sources.jar}
 * rather than assumed:
 *
 * <ol>
 * <li><b>Adding a listener for a DIFFERENT event class mid-dispatch is safe.</b> The bus
 *     resolves a {@code ListenerList} per event class
 *     ({@code bus-8.0.5 EventBus.addToListeners} -&gt; {@code getListenerList(eventType)}), so
 *     the three mod-bus listeners the shared init installs from inside the window —
 *     {@code RegisterPayloadHandlersEvent} ({@code NeoForgeNet}) and two
 *     {@code BuildCreativeModeTabContentsEvent}s ({@code ModItems}) — do not touch the list
 *     being walked. Game-bus listeners (every ticker, both interaction denials, the death
 *     pair, the command tree, brewing) are on a different bus entirely.</li>
 * <li><b>None of those events has fired yet.</b> {@code RegisterPayloadHandlersEvent} is
 *     posted by {@code NetworkRegistry.setup}, which is the LAST init task of
 *     {@code CommonModLoader.finish}; {@code BuildCreativeModeTabContentsEvent} is posted
 *     when a tab is first built, after {@code CreativeModeTabRegistry.sortTabs()} which
 *     {@code postRegisterEvents} itself calls at the end;
 *     {@code RegisterBrewingRecipesEvent} is posted from {@code PotionBrewing.bootstrap}
 *     during a world's registry build. All three are strictly later than this window.</li>
 * <li><b>The creative tab's own registration is inside the window and needs to be.</b>
 *     {@code ModItems.initialize()} ends with
 *     {@code Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, …)}, which is a write
 *     like any other.</li>
 * </ol>
 *
 * <p>The init ORDER the shared method documents as a contract is preserved exactly — all
 * twenty-eight calls still run in the same sequence, just later. Nothing between mod
 * construction and this window reads any of it.
 */
@Mod(Archetypes.MOD_ID)
public final class ArchetypesNeoForge {
	private static IEventBus modEventBus;

	/** Guards against a second dispatch of the same {@code RegisterEvent} for any reason. */
	private static boolean initialized;

	public ArchetypesNeoForge(final IEventBus modBus, final ModContainer container) {
		modEventBus = modBus;
		modBus.addListener(RegisterEvent.class, ArchetypesNeoForge::onRegister);
	}

	private static void onRegister(final RegisterEvent event) {
		if (initialized || !event.getRegistryKey().equals(Registries.ITEM)) {
			return;
		}

		initialized = true;
		new Archetypes().onInitialize();
	}

	/**
	 * The mod event bus, for the three helpers that need it
	 * ({@link NeoForgeEvents#creativeTabOutput}, {@link NeoForgeNet#registerAll}, and the
	 * client's own entrypoint). Stashed in the constructor, which NeoForge runs before any
	 * event is dispatched, so every caller downstream of {@link #onRegister} sees it.
	 */
	public static IEventBus modEventBus() {
		if (modEventBus == null) {
			throw new IllegalStateException("The NeoForge mod event bus was read before mod construction");
		}

		return modEventBus;
	}
}
