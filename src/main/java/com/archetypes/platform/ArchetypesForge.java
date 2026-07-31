package com.archetypes.platform;

import java.util.ArrayList;
import java.util.List;

import com.archetypes.Archetypes;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.ForgeRegistry;
import net.minecraftforge.registries.RegisterEvent;
import net.minecraftforge.registries.RegistryManager;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;

/**
 * The {@code javafml} entrypoint for the {@code 1.20.1-forge} node.
 *
 * <p>ARTIFACT PROVENANCE — the constructor shape is NOT the NeoForge one and getting it
 * wrong is a hard crash at mod construction, so it was read out of
 * {@code javafmllanguage-1.20.1-47.4.22-sources.jar},
 * {@code net/minecraftforge/fml/javafmlmod/FMLModContainer.java}:
 *
 * <pre>
 *   constructor = modClass.getDeclaredConstructor(context.getClass());   // FMLJavaModLoadingContext
 *   … catch (NoSuchMethodException …) { constructor = modClass.getDeclaredConstructor(); }
 * </pre>
 *
 * So LexForge 47.4.22 accepts exactly TWO constructor shapes — {@code (FMLJavaModLoadingContext)}
 * or no-arg — and nothing else. It is not NeoForge's injection whitelist
 * ({@code IEventBus}/{@code ModContainer}/{@code Dist}), and {@code IEventBus} is NOT
 * injectable here. Taking the context argument is also the non-deprecated path:
 * {@code FMLJavaModLoadingContext.get()} carries {@code @Deprecated(forRemoval = true)}.
 *
 * <p>{@code getModEventBus()} returns the MOD bus. The GAME bus is the static
 * {@code MinecraftForge.EVENT_BUS}; the two are different objects and a listener on the
 * wrong one is a silent no-op, which is why {@link ForgeArchetypeStore} names both
 * explicitly.
 *
 * <p><b>It calls {@code onInitialize()}, not an extracted {@code init()}.</b> The shared
 * tree deliberately did not create one: adding a method and rewriting {@code onInitialize}
 * would have moved bytecode on five Fabric nodes required to stay instruction-identical
 * while this axis lands. On the loader axis {@code onInitialize()} is a plain public
 * instance method with no interface behind it, and this is its one caller.
 *
 * <p><b>WHY COMMON INIT DOES NOT RUN IN THIS CONSTRUCTOR — R-22, and it is WIDER here than
 * it was next door.</b> Skill Proficiencies measured the wall on a real server boot:
 *
 * <pre>
 *   java.lang.IllegalStateException: Registry is already frozen
 *     at net.minecraftforge.registries.NamespacedWrapper.createIntrusiveHolder
 *     at net.minecraft.world.item.Item.&lt;init&gt;
 * </pre>
 *
 * <p>Note where it throws: not at the {@code Registry.register} call but inside
 * {@code Item.<init>}, which asks the item registry for an intrusive holder. So on this
 * loader the items cannot even be CONSTRUCTED at mod-construct time, which rules out every
 * fix that leaves {@code ModItems}' static fields where they are and only moves the
 * {@code register} call. Fabric has no registration window at all and that is why five
 * Fabric nodes never saw it.
 *
 * <p><b>ARCHETYPES WIDENS THE EXPOSURE FROM ONE REGISTRY TO SIX</b> — {@code ITEM},
 * {@code ENTITY_TYPE}, {@code MOB_EFFECT}, {@code POTION}, {@code PARTICLE_TYPE} and
 * {@code CREATIVE_MODE_TAB} are all written during the shared init sequence — <b>and the
 * single-registry answer next door does NOT transfer.</b> That is design's experiment
 * E-R22-1, and the measurement is a real boot rather than a reading:
 *
 * <pre>
 *   java.lang.IllegalStateException: Can not register to a locked registry.
 *                                    Modder should use Forge Register methods.
 *     at net.minecraftforge.registries.NamespacedWrapper.register
 *     at net.minecraft.core.Registry.registerForHolder
 *     at com.archetypes.ModEntities.register
 *     at com.archetypes.ModEntities.&lt;clinit&gt;
 * </pre>
 *
 * <p>The reason, read back out of {@code registries/ForgeRegistry.java} afterwards, is that
 * the window is PER REGISTRY and not per phase:
 *
 * <pre>
 *   public void freeze()   { this.isFrozen = true;  var w = getWrapper(); if (w != null) w.locked = true; }
 *   public void unfreeze() { this.isFrozen = false; var w = getWrapper(); if (w != null) w.locked = false; }
 * </pre>
 *
 * <p>{@code GameData.postRegisterEvents} calls that pair around EACH registry's own
 * {@code RegisterEvent}, and the wrapper it toggles IS {@code BuiltInRegistries.X}. So
 * during the item event the item registry is open and the other five are shut — which is
 * exactly why Skill Proficiencies, which registers items and nothing else, never saw this.
 *
 * <p><b>THE FIX IS FORGE'S OWN PAIR, USED THE WAY FORGE USES IT.</b> The five other
 * registries are unfrozen immediately before the shared init and frozen immediately after,
 * in a {@code finally}, through the public {@code RegistryManager.ACTIVE.getRegistry(...)}
 * handle. Three reasons this is the right shape rather than a hack:
 *
 * <ul>
 * <li><b>It keeps the init sequence atomic and shared.</b> The alternative — running each
 *     registry's step inside its own event — is impossible without editing shared code:
 *     {@code AmnesiaPotions}' single class initialiser writes a {@code MobEffect} AND two
 *     {@code Potion}s, and {@code ManaPotions} references {@code ManaEffects} from its own
 *     initialiser, so the class-init cascade crosses registries that Forge never has open at
 *     the same time.</li>
 * <li><b>Nothing is left open.</b> The {@code finally} re-freezes, and Forge's own
 *     unfreeze/freeze around each later event is unaffected by having run once more.</li>
 * <li><b>The entries are complete.</b> {@code NamespacedWrapper.register} forwards to
 *     {@code ForgeRegistry.add}, so an entry added here is in both views; and the id BAKE
 *     happens in {@code GameData.freezeData()} at FREEZE_DATA, long after LOAD_REGISTRIES,
 *     so an entry added at any point in this phase is baked normally.</li>
 * </ul>
 *
 * <p>Three things that make the deferral itself SAFE rather than merely working:
 * <ul>
 * <li><b>The init ORDER is untouched.</b> There is still exactly one copy of the sequence,
 *     in {@code Archetypes.onInitialize()}, and no shared file changed. Only the TIMING of
 *     the whole block moves.</li>
 * <li><b>Nothing in that block is too late at LOAD_REGISTRIES.</b> Game-bus listeners may be
 *     added any time before a world loads; {@code NetworkRegistry} is locked only at
 *     NETWORK_LOCK, after COMPLETE, so both {@code SimpleChannel}s are in time;
 *     {@code BuildCreativeModeTabContentsEvent}, {@code FMLCommonSetupEvent} and
 *     {@code RegisterCommandsEvent} all fire later; {@code ModList} is populated before
 *     CONSTRUCT.</li>
 * <li><b>The one piece that WOULD be too late is registered here instead.</b>
 *     {@code RegisterCapabilitiesEvent} is posted at INJECT_CAPABILITIES, one state BEFORE
 *     LOAD_REGISTRIES, and an unregistered capability fails silently rather than loudly —
 *     every one of the 74 keys would read absent forever. Hence the
 *     {@link ForgeArchetypeStore#registerCapabilities} call below.</li>
 * </ul>
 *
 * <p>{@code Registries.ITEM} is the chosen anchor rather than "the first event that
 * arrives", for one reason: it is the registry whose window the crash above is actually
 * about, so if the phase model above is ever wrong the failure lands on the call that names
 * it instead of somewhere downstream.
 *
 * <p>Same placement rationale as Skill Proficiencies' entrypoint: this lives in
 * {@code com.archetypes.platform} so the bus hand-off to {@link ForgeArchetypeStore} stays
 * package-private, and so the whole file is caught by the other node scripts'
 * {@code Forge*} / {@code ArchetypesForge} exclusion globs.
 */
@Mod(Archetypes.MOD_ID)
public final class ArchetypesForge {
	/**
	 * The five registries the shared init writes that are NOT the one whose event it rides.
	 * Frozen list, in the order {@code Archetypes.onInitialize()} reaches them, so a reader
	 * can check it against that method rather than against this comment.
	 *
	 * <p>{@code Registries.ITEM} is deliberately absent: Forge has already opened it for the
	 * event this runs inside, and re-freezing it here would shut it before Forge's own
	 * {@code freeze()} and before any other mod's item listener.
	 */
	private static final List<ResourceKey<? extends Registry<?>>> ALSO_WRITTEN = List.of(
			Registries.ENTITY_TYPE,
			Registries.MOB_EFFECT,
			Registries.POTION,
			Registries.PARTICLE_TYPE,
			Registries.CREATIVE_MODE_TAB);

	private static IEventBus modEventBus;

	private static boolean initialized;

	public ArchetypesForge(final FMLJavaModLoadingContext context) {
		IEventBus modBus = context.getModEventBus();
		modEventBus = modBus;

		// CONSTRUCT phase, and it has to be: RegisterCapabilitiesEvent is posted before the
		// registration window the init below waits for. See the method's own javadoc.
		ForgeArchetypeStore.registerCapabilities(modBus);

		// The SAME body the Fabric ModInitializer runs, in the same order — there is only one
		// copy of it — deferred into the registration window. See the class javadoc.
		modBus.addListener((RegisterEvent event) -> {
			if (!Registries.ITEM.equals(event.getRegistryKey()) || initialized) {
				return;
			}

			initialized = true;
			List<ForgeRegistry<?>> opened = open();

			try {
				new Archetypes().onInitialize();
			} finally {
				for (final ForgeRegistry<?> registry : opened) {
					registry.freeze();
				}
			}
		});
	}

	/**
	 * Opens the five registries {@link #ALSO_WRITTEN} names and hands back what was actually
	 * opened, so the {@code finally} closes exactly that.
	 *
	 * <p>A null handle is not an error and is skipped: a vanilla registry Forge does not
	 * mirror has no {@code NamespacedWrapper} and is therefore already writable for the whole
	 * of this phase ({@code GameData.unfreezeData()} at UNFREEZE_DATA), which is the case the
	 * skip is for.
	 */
	private static List<ForgeRegistry<?>> open() {
		List<ForgeRegistry<?>> opened = new ArrayList<>(ALSO_WRITTEN.size());

		for (final ResourceKey<? extends Registry<?>> key : ALSO_WRITTEN) {
			ForgeRegistry<?> registry = RegistryManager.ACTIVE.getRegistry(key.location());

			if (registry != null) {
				registry.unfreeze();
				opened.add(registry);
			}
		}

		return opened;
	}

	/** The MOD event bus. Never null after construction. */
	static IEventBus modEventBus() {
		if (modEventBus == null) {
			throw new IllegalStateException("The Forge mod event bus was read before mod construction");
		}

		return modEventBus;
	}
}
