package com.archetypes.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.archetypes.Archetypes;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.RegisterEvent;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

/**
 * The NeoForge client entrypoint AND the client half of the event seam — one class, the way
 * Skill Proficiencies' {@code NeoForgeClientEvents} is one class, because the
 * {@code @Mod(dist = CLIENT)} constructor is the only place a client-only listener can be
 * installed from and the helpers all need the mod bus anyway.
 *
 * <p>It lives in {@code com.archetypes.client} rather than behind {@code platform/} because
 * it must be: {@code com.archetypes.platform} is in {@code src/main}, which cannot see
 * {@code net.minecraft.client} at all. Every Fabric and Forge node excludes it by the
 * anchored {@code NeoForge*} glob in {@code sourceSets["client"]}.
 *
 * <p>Three of the eight helpers below are Skill Proficiencies', reused unchanged
 * ({@link #afterScreenInit}, {@link #addWidget}, {@link #afterScreenTick} — the bookmark
 * surface is the same one). <b>Four have no precedent next door at all</b>: a client tick,
 * key-mapping registration, an entity-renderer registration and a particle-provider
 * registration. The eighth thing a Fabric node's {@code onInitializeClient} does — the HUD —
 * is NOT a helper here; see the mixin-config note at the bottom of this javadoc.
 *
 * <p><b>WHEN THE SHARED CLIENT INIT RUNS, and why it is not {@code FMLClientSetupEvent}.</b>
 * Skill Proficiencies calls {@code new SpecialitiesClient().onInitializeClient()} straight
 * from this constructor. That is not available here: this mod's client init dereferences
 * {@code ModEntities.SPELL_PROJECTILE} and {@code ModParticles.GREATSWORD_SWEEP}, and
 * touching either class runs a {@code Registry.register} in its static initialiser — at mod
 * construction the registries are still frozen, so it is design R-22 again, from the client
 * side. The obvious next answer, {@code FMLClientSetupEvent}, is <b>too late</b>, and that
 * is measured rather than assumed: reading {@code Minecraft.<init>} in
 * {@code neoforge-21.1.243-sources.jar},
 *
 * <pre>
 *   :486  ClientModLoader.begin(...)          -&gt; mod construction, then the RegisterEvent window
 *   :556  ClientHooks.onRegisterParticleProviders(particleEngine)
 *   :601  ClientHooks.initClientHooks(...)    -&gt; EntityRenderersEvent.RegisterRenderers,
 *                                                RegisterKeyMappingsEvent, gui.initModdedOverlays()
 *   later the resource reload runs CommonModLoader.load  -&gt; FMLClientSetupEvent
 * </pre>
 *
 * so all four client registration events have already fired by the time
 * {@code FMLClientSetupEvent} is dispatched. A client init there would leave seven keybinds,
 * the spell-projectile renderer and the sweep particle silently unregistered — no crash and
 * no log line, which is exactly the class of bug the Forge client-bootstrap lesson is about.
 *
 * <p>So client init runs in the SAME {@code RegisterEvent} window the common half does, at
 * {@link EventPriority#LOW} so it lands after {@link com.archetypes.platform.ArchetypesNeoForge}'s
 * default-priority listener. By then {@code ModEntities} and {@code ModParticles} are
 * registered and the four client events are still ahead.
 *
 * <p><b>The HUD is a mixin on this node, not a helper.</b> Design R-11 answered this for
 * Skill Proficiencies with {@code RegisterGuiLayersEvent.wrapLayer} because that mod RAISES
 * seven vanilla elements. Archetypes raises nothing — it draws six elements and conditions
 * two vanilla draws — and three of the shared {@code client/mixin/GuiMixin}'s four anchors
 * survive NeoForge's Gui patch untouched. The one that does not is retargeted by this node's
 * own {@code client/mixin/NeoForgeGuiMixin}; read its header for the measurement. The shared
 * {@code GuiMixin} class is in this jar and deliberately NOT listed in this node's mixin
 * config, which is Skill Proficiencies' rule verbatim: a present-but-unlisted class is
 * silently unused, whereas excluding it turns a future config entry into a boot crash.
 */
@Mod(value = Archetypes.MOD_ID, dist = Dist.CLIENT)
public final class NeoForgeClientEvents {
	private static final List<AfterScreenInit> INIT_LISTENERS = new ArrayList<>();
	private static final List<Consumer<Screen>> TICK_LISTENERS = new ArrayList<>();
	private static final List<Consumer<Minecraft>> CLIENT_TICK_LISTENERS = new ArrayList<>();
	private static final List<KeyMapping> KEY_MAPPINGS = new ArrayList<>();
	private static final List<Consumer<EntityRenderersEvent.RegisterRenderers>> RENDERERS =
			new ArrayList<>();
	private static final List<Consumer<RegisterParticleProvidersEvent>> PARTICLES = new ArrayList<>();

	private static Screen tickScreen;
	private static ScreenEvent.Init.Post currentInit;
	private static boolean initialized;

	public NeoForgeClientEvents(final IEventBus modBus, final ModContainer container) {
		modBus.addListener(RegisterKeyMappingsEvent.class, NeoForgeClientEvents::onRegisterKeyMappings);
		modBus.addListener(EntityRenderersEvent.RegisterRenderers.class,
				NeoForgeClientEvents::onRegisterRenderers);
		modBus.addListener(RegisterParticleProvidersEvent.class,
				NeoForgeClientEvents::onRegisterParticleProviders);
		NeoForge.EVENT_BUS.addListener(ScreenEvent.Init.Post.class, NeoForgeClientEvents::onScreenInitPost);
		NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, NeoForgeClientEvents::onClientTickPost);
		// See the class javadoc: the shared client init has to run inside the registration
		// window, and after the common half. LOW is what orders it against
		// ArchetypesNeoForge's listener on the same event class, on the same bus.
		modBus.addListener(EventPriority.LOW, RegisterEvent.class, NeoForgeClientEvents::onRegister);
	}

	private static void onRegister(final RegisterEvent event) {
		if (initialized || !event.getRegistryKey().equals(Registries.ITEM)) {
			return;
		}

		initialized = true;
		new ArchetypesClient().onInitializeClient();
	}

	// ------------------------------------------------------------------ client tick

	/**
	 * {@code ClientTickEvents.END_CLIENT_TICK} — <b>six registrations</b>: the ability-key
	 * poll with its GLFW auto-repeat guard and the level-up toast in
	 * {@code ArchetypesClient}, plus all five Player Animation Library drivers.
	 *
	 * <p>Contract owed: fire once per client tick, at the END of it, with the
	 * {@code Minecraft}. {@code ClientTickEvent.Post} is exactly that and fires once, which
	 * is the difference from LexForge next door — there {@code TickEvent.ClientTickEvent}
	 * fires twice per tick and an unchecked phase would drain every {@code consumeClick}
	 * twice and send two payloads per keypress.
	 *
	 * <p>Listeners are kept in registration order in one list rather than each adding its own
	 * bus listener, so the ability poll always runs before the animation drivers read the
	 * state it changed — the same order the single fabric-api event gives them.
	 */
	public static void endClientTick(final Consumer<Minecraft> listener) {
		CLIENT_TICK_LISTENERS.add(listener);
	}

	private static void onClientTickPost(final ClientTickEvent.Post event) {
		Minecraft client = Minecraft.getInstance();

		for (int i = 0; i < CLIENT_TICK_LISTENERS.size(); i++) {
			CLIENT_TICK_LISTENERS.get(i).accept(client);
		}

		tickScreenListeners(client);
	}

	// ------------------------------------------------------------------ key mappings

	/**
	 * {@code KeyMappingHelper.registerKeyMapping} — the seven ability binds.
	 *
	 * <p><b>Returns THE SAME INSTANCE, which the call site depends on</b>: it assigns the
	 * result into {@code ABILITY_KEYS[slot]} and everything downstream polls
	 * {@code isDown()}/{@code consumeClick()} on that array, so a helper that registered a
	 * copy would leave seven keys bound in the controls screen and dead in game. The
	 * mapping is held until {@code RegisterKeyMappingsEvent}, which
	 * {@code ClientHooks.initClientHooks} posts after the registration window this runs in.
	 */
	public static KeyMapping registerKeyMapping(final KeyMapping mapping) {
		KEY_MAPPINGS.add(mapping);
		return mapping;
	}

	private static void onRegisterKeyMappings(final RegisterKeyMappingsEvent event) {
		for (final KeyMapping mapping : KEY_MAPPINGS) {
			event.register(mapping);
		}
	}

	// ------------------------------------------------------------------ renderers and particles

	/**
	 * {@code EntityRendererRegistry.register} — the Seeker's spell projectile.
	 * {@code EntityRenderersEvent.RegisterRenderers} is a mod-bus event posted from
	 * {@code ClientHooks.initClientHooks}.
	 */
	public static <T extends Entity> void entityRenderer(final EntityType<? extends T> type,
			final EntityRendererProvider<T> provider) {
		RENDERERS.add(event -> event.registerEntityRenderer(type, provider));
	}

	private static void onRegisterRenderers(final EntityRenderersEvent.RegisterRenderers event) {
		for (final Consumer<EntityRenderersEvent.RegisterRenderers> registration : RENDERERS) {
			registration.accept(event);
		}
	}

	/**
	 * {@code ParticleProviderRegistry.getInstance().register(type, factory)} — the greatsword
	 * sweep.
	 *
	 * <p>{@code registerSpriteSet} and not {@code registerSpecial}: fabric-api's
	 * {@code PendingParticleProvider} and NeoForge's
	 * {@code ParticleEngine.SpriteParticleRegistration} are the same functional shape —
	 * {@code (SpriteSet) -> ParticleProvider<T>} — which is why the constructor reference at
	 * the call site is shared and unchanged. {@code registerSpecial} takes a bare provider
	 * and would not compile against it, which is the loud failure this note exists to keep
	 * loud.
	 */
	public static <T extends net.minecraft.core.particles.ParticleOptions> void particleProvider(
			final ParticleType<T> type, final ParticleEngine.SpriteParticleRegistration<T> factory) {
		PARTICLES.add(event -> event.registerSpriteSet(type, factory));
	}

	private static void onRegisterParticleProviders(final RegisterParticleProvidersEvent event) {
		for (final Consumer<RegisterParticleProvidersEvent> registration : PARTICLES) {
			registration.accept(event);
		}
	}

	// ------------------------------------------------------------------ screens

	/** {@code ScreenEvents.AFTER_INIT}'s listener shape, parameter for parameter. */
	@FunctionalInterface
	public interface AfterScreenInit {
		void afterInit(Minecraft client, Screen screen, int scaledWidth, int scaledHeight);
	}

	/**
	 * {@code ScreenEvents.AFTER_INIT} — the bookmark tab and the creative button.
	 *
	 * <p>Contract owed: fire after the screen's own widgets exist. {@code ScreenEvent.Init.Post}
	 * is posted from {@code ClientHooks.onInitScreenPost}, after {@code Screen.init} has run,
	 * which is the same position fabric-screen-api-v1's AFTER_INIT occupies.
	 */
	public static void afterScreenInit(final AfterScreenInit listener) {
		INIT_LISTENERS.add(listener);
	}

	/**
	 * {@code Screens.getWidgets(screen).add(widget)} — the one call in the shared body with
	 * no vanilla equivalent, since {@code Screen.addRenderableWidget} is protected.
	 * {@code ScreenEvent.Init.addListener} adds to both the renderable and the event lists,
	 * which is what fabric-api's list does.
	 */
	public static void addWidget(final Screen screen, final AbstractWidget widget) {
		if (currentInit == null || currentInit.getScreen() != screen) {
			throw new IllegalStateException(
					"addWidget must be called from an afterScreenInit listener, for the screen being initialised");
		}

		currentInit.addListener(widget);
	}

	/**
	 * {@code ScreenEvents.afterTick(screen).register(...)} — the re-anchor, which is not
	 * cosmetic: the recipe book shifts {@code leftPos} without re-running init, so a helper
	 * that fired only once would leave the bookmark behind the panel the first time the book
	 * is opened.
	 *
	 * <p>NeoForge has no per-screen tick event, so this rides the client tick and checks that
	 * the screen is still the current one — the same shape Skill Proficiencies uses, and the
	 * reason the listener list is cleared on every screen init.
	 */
	public static void afterScreenTick(final Screen screen, final Consumer<Screen> listener) {
		if (currentInit == null || currentInit.getScreen() != screen) {
			throw new IllegalStateException(
					"afterScreenTick must be called from an afterScreenInit listener, for the screen being initialised");
		}

		tickScreen = screen;
		TICK_LISTENERS.add(listener);
	}

	private static void onScreenInitPost(final ScreenEvent.Init.Post event) {
		TICK_LISTENERS.clear();
		tickScreen = null;
		currentInit = event;

		try {
			Minecraft client = Minecraft.getInstance();
			Screen screen = event.getScreen();

			for (final AfterScreenInit listener : INIT_LISTENERS) {
				listener.afterInit(client, screen, screen.width, screen.height);
			}
		} finally {
			currentInit = null;
		}
	}

	private static void tickScreenListeners(final Minecraft client) {
		if (TICK_LISTENERS.isEmpty()) {
			return;
		}

		Screen current = client.screen;

		if (current == null || current != tickScreen) {
			TICK_LISTENERS.clear();
			tickScreen = null;
			return;
		}

		for (final Consumer<Screen> listener : TICK_LISTENERS) {
			listener.accept(current);
		}
	}
}
