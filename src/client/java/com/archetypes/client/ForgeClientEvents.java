package com.archetypes.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;

import com.archetypes.Archetypes;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

/**
 * The LexForge half of the CLIENT event wiring for the {@code 1.20.1-forge} node, and this
 * node's client BOOTSTRAP. Every line of HUD, tab, key-poll and animation logic stays
 * shared; only registration crosses into this file.
 *
 * <p>It lives in {@code com.archetypes.client} rather than behind the
 * {@code com.archetypes.platform} seam because it has to: the seam is in {@code src/main},
 * which cannot see {@code net.minecraft.client} on any node (Skill Proficiencies'
 * conventions §5g). The file is named after its loader so the other node scripts'
 * {@code com/archetypes/client/Forge*} exclusion glob keeps it off the six nodes that must
 * not compile it — and {@code Forge*} is anchored, so it does not match {@code NeoForge*}.
 *
 * <p>ARTIFACT PROVENANCE — {@code forge-1.20.1-47.4.22-sources.jar}, not recalled:
 * <ul>
 * <li>{@code client/event/ScreenEvent.java} — {@code Init.Post extends Init extends
 *     ScreenEvent}, with {@code Screen getScreen()} and
 *     {@code void addListener(GuiEventListener)}.
 *     {@code patches/net/minecraft/client/gui/screens/Screen.java.patch} shows
 *     {@code Init.Post} posted immediately after the screen's own {@code init()}, and shows
 *     that the {@code add} consumer it carries is {@code Screen::addEventWidget}, which
 *     appends to BOTH {@code renderables} and {@code children} — exactly what
 *     {@code Screens.getButtons(screen).add(w)} does on Fabric.</li>
 * <li>{@code event/TickEvent.java} — {@code ClientTickEvent extends TickEvent} with the
 *     inherited {@code public final Phase phase}. {@code ScreenEvent} declares NO tick
 *     family, which is why {@link #afterScreenTick} is a re-rooting.</li>
 * <li>{@code client/event/RegisterKeyMappingsEvent.java} — {@code implements IModBusEvent},
 *     {@code void register(KeyMapping)}.</li>
 * <li>{@code client/event/EntityRenderersEvent.java} — {@code RegisterRenderers} with
 *     {@code <T extends Entity> void registerEntityRenderer(EntityType<? extends T>,
 *     EntityRendererProvider<T>)}.</li>
 * <li>{@code client/event/RegisterParticleProvidersEvent.java} —
 *     {@code <T extends ParticleOptions> void registerSpriteSet(ParticleType<T>,
 *     ParticleEngine.SpriteParticleRegistration<T>)}, which takes the same
 *     {@code SpriteSet -> ParticleProvider} shape fabric-api's
 *     {@code ParticleFactoryRegistry.register} does.</li>
 * </ul>
 *
 * <h2>THE BOOTSTRAP, AND WHY IT IS NOT {@code FMLClientSetupEvent} ALONE</h2>
 *
 * <p>Skill Proficiencies shipped its 1.20.1-forge jar with every client helper present and
 * NOTHING invoking them; the fix there was
 * {@code @Mod.EventBusSubscriber(Dist.CLIENT, bus = MOD)} plus an {@code FMLClientSetupEvent}
 * subscriber. Copying that verbatim here would have been wrong, and the reason is an ORDERING
 * fact that node never had to face — it registers no key mapping, no entity renderer and no
 * particle provider.
 *
 * <p>MEASURED, from {@code patches/net/minecraft/client/Minecraft.java.patch} and
 * {@code ForgeHooksClient.initClientHooks}, in the order the constructor runs them:
 *
 * <pre>
 *   Minecraft.&lt;init&gt;
 *     ClientModLoader.begin(...)                  &lt;- mod CONSTRUCT … LOAD_REGISTRIES
 *     new ParticleEngine(...)
 *     ForgeHooksClient.onRegisterParticleProviders  -&gt; RegisterParticleProvidersEvent
 *     new ForgeGui(this)
 *     ForgeHooksClient.initClientHooks(...)
 *         -&gt; EntityRenderersEvent.RegisterRenderers
 *         -&gt; RegisterKeyMappingsEvent
 *     resourceManager.createReload(...)           &lt;- ClientModLoader.onResourceReload runs LATER
 *         -&gt; ModLoader.loadMods()  -&gt; COMMON_SETUP, then FMLClientSetupEvent
 * </pre>
 *
 * <p>So {@code FMLClientSetupEvent} fires <b>after all three registration events</b>. A
 * client init driven from it would register seven key mappings, one entity renderer and one
 * particle provider into events that had already been dispatched: seven binds visible in the
 * controls screen and dead in game, a spell projectile rendered as nothing, and a greatsword
 * sweep that draws no particle. Every one of those is in-game-only — the jar builds, the
 * dedicated server boots clean, and the mixin audit is green.
 *
 * <p>So the bootstrap runs from {@link #onRegisterParticleProviders}, which is the EARLIEST
 * of the three and still comfortably after LOAD_REGISTRIES — which matters just as much in
 * the other direction: {@code ArchetypesClient.onInitializeClient()} names
 * {@code ModParticles.GREATSWORD_SWEEP} and {@code ModEntities.SPELL_PROJECTILE}, and
 * touching either class before the registration window is R-22's boot crash. There is
 * exactly one window that satisfies both, and this is it.
 *
 * <p>{@link #onClientSetup} is kept anyway, guarded, as a belt-and-braces path: it costs one
 * boolean and it means a Forge build that ever stopped posting the particle event would lose
 * the three registrations rather than the whole client.
 *
 * <p><b>Every {@code @SubscribeEvent} method here is PUBLIC.</b> Forge's
 * {@code EventBus.register(Class)} only sees public static {@code @SubscribeEvent} methods —
 * package-private compiles and silently never fires, which is the exact shape of the bug
 * next door shipped.
 */
@Mod.EventBusSubscriber(modid = Archetypes.MOD_ID, value = Dist.CLIENT,
		bus = Mod.EventBusSubscriber.Bus.MOD)
final class ForgeClientEvents {
	/**
	 * The per-screen tick listeners, and the one genuine RE-ROOTING in this file.
	 *
	 * <p>LexForge 1.20.1 has no per-screen tick event, so {@code ScreenEvents.afterTick(screen)}
	 * re-roots onto a single permanent {@code ClientTickEvent} listener at {@code Phase.END}
	 * that dispatches to whatever is registered for the screen currently open. That fires once
	 * per client tick while the screen is open, which is what {@code Screen.tick()} does, so
	 * the re-anchor cadence is unchanged. {@code ScreenEvent.Render} was rejected: it fires per
	 * FRAME.
	 *
	 * <p>{@code WeakHashMap} because a Forge bus listener cannot be removed once added — this
	 * map is the only thing holding these lambdas, so a closed screen and its listeners become
	 * collectable.
	 *
	 * <p>Keyed by screen IDENTITY and CLEARED on every {@code Init.Post} for that screen. Both
	 * matter: an {@code AbstractContainerScreen} re-runs {@code init()} on the same object when
	 * the recipe book is toggled — which is the exact case the re-anchor exists for — so
	 * without the clear each toggle would leave another copy of the same anchor callback
	 * behind.
	 */
	private static final Map<Screen, List<Consumer<Screen>>> SCREEN_TICKERS = new WeakHashMap<>();

	/**
	 * The three MOD-bus registrations, queued by the shared client init and drained by the
	 * event that owns each one. They are queues rather than direct calls because the init that
	 * fills them runs INSIDE the first of the three dispatches — see the class javadoc.
	 */
	private static final List<Consumer<RegisterParticleProvidersEvent>> PARTICLES = new ArrayList<>();

	private static final List<Consumer<EntityRenderersEvent.RegisterRenderers>> RENDERERS =
			new ArrayList<>();

	private static final List<KeyMapping> KEY_MAPPINGS = new ArrayList<>();

	/**
	 * The {@code Init.Post} currently being dispatched, so {@link #addWidget} can reach the
	 * event's {@code add} consumer while being handed only the {@code Screen}.
	 *
	 * <p>Not a trick for its own sake: {@code Screens.getButtons(screen)} needs nothing but the
	 * screen, so the shared call site passes only the screen, and widening that signature would
	 * fork a line that is otherwise identical on all seven nodes. Written and cleared around
	 * one synchronous dispatch on the client thread; saved and restored rather than nulled, so
	 * a nested screen init could not strand it.
	 */
	private static ScreenEvent.Init currentInit;

	private static boolean bootstrapped;

	private static boolean tickPumpInstalled;

	private ForgeClientEvents() {
	}

	/**
	 * THE CLIENT BOOTSTRAP. See the class javadoc for why it hangs off this event and not
	 * {@code FMLClientSetupEvent}.
	 *
	 * <p>Order inside the method is load-bearing: the shared init is what FILLS
	 * {@link #PARTICLES}, so it has to run before the drain.
	 */
	@SubscribeEvent
	public static void onRegisterParticleProviders(final RegisterParticleProvidersEvent event) {
		bootstrap();

		for (final Consumer<RegisterParticleProvidersEvent> pending : PARTICLES) {
			pending.accept(event);
		}

		PARTICLES.clear();
	}

	@SubscribeEvent
	public static void onRegisterRenderers(final EntityRenderersEvent.RegisterRenderers event) {
		bootstrap();

		for (final Consumer<EntityRenderersEvent.RegisterRenderers> pending : RENDERERS) {
			pending.accept(event);
		}

		RENDERERS.clear();
	}

	@SubscribeEvent
	public static void onRegisterKeyMappings(final RegisterKeyMappingsEvent event) {
		bootstrap();

		for (final KeyMapping key : KEY_MAPPINGS) {
			event.register(key);
		}

		KEY_MAPPINGS.clear();
	}

	/**
	 * The belt-and-braces path. Idempotent through {@link #bootstrapped}; on a normal client
	 * it finds the work already done. {@code enqueueWork} puts it on the main thread, matching
	 * when the other six nodes run their client init.
	 */
	@SubscribeEvent
	public static void onClientSetup(final FMLClientSetupEvent event) {
		event.enqueueWork(ForgeClientEvents::bootstrap);
	}

	private static void bootstrap() {
		if (bootstrapped) {
			return;
		}

		bootstrapped = true;
		new ArchetypesClient().onInitializeClient();
	}

	/**
	 * Registers a particle provider. Queued; drained by
	 * {@link #onRegisterParticleProviders}.
	 *
	 * <p>{@code registerSpriteSet} takes the same {@code SpriteSet -> ParticleProvider} factory
	 * shape fabric-api's {@code ParticleFactoryRegistry.register} does, so the shared
	 * constructor reference needs no adaptation.
	 */
	static <T extends ParticleOptions> void particleProvider(final ParticleType<T> type,
			final ParticleEngine.SpriteParticleRegistration<T> registration) {
		PARTICLES.add(event -> event.registerSpriteSet(type, registration));
	}

	/** Registers an entity renderer. Queued; drained by {@link #onRegisterRenderers}. */
	static <T extends Entity> void entityRenderer(final EntityType<? extends T> type,
			final EntityRendererProvider<T> provider) {
		RENDERERS.add(event -> event.registerEntityRenderer(type, provider));
	}

	/**
	 * Registers a key mapping and RETURNS THE SAME INSTANCE — which the caller depends on:
	 * everything downstream polls {@code ABILITY_KEYS[slot].consumeClick()}, so a helper that
	 * registered a copy would leave seven binds visible in the controls screen and dead in
	 * game.
	 */
	static KeyMapping registerKeyMapping(final KeyMapping key) {
		KEY_MAPPINGS.add(key);
		return key;
	}

	/**
	 * Fires once per client tick, at the END of it, with the {@code Minecraft}.
	 *
	 * <p><b>THE PHASE CHECK IS THE WHOLE POINT.</b> {@code TickEvent.ClientTickEvent} fires
	 * TWICE per tick — {@code Phase.START} and {@code Phase.END} — where fabric-api's
	 * {@code ClientTickEvents.END_CLIENT_TICK} fires once. Six drivers ride this, including the
	 * ability-key poll, and an unchecked phase would drain every {@code consumeClick} twice and
	 * send two payloads per keypress.
	 *
	 * <p>The {@code Minecraft} comes from {@code getInstance()} rather than off the event,
	 * which carries none. It is the same object fabric-api hands its listener.
	 */
	static void endClientTick(final Consumer<Minecraft> listener) {
		MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
			if (event.phase == TickEvent.Phase.END) {
				listener.accept(Minecraft.getInstance());
			}
		});
	}

	/**
	 * Fires after a screen's widgets exist, with the same four parameters in the same order
	 * fabric-api's {@code ScreenEvents.AFTER_INIT} passes.
	 *
	 * <p>{@code Init.Post} is posted after {@code Screen.init()}, so the vanilla widgets are
	 * already in the lists — which the shared listener needs, because it reads
	 * {@code leftPos}/{@code topPos} off the container screen to anchor the tab.
	 *
	 * <p>The scaled width and height come from the window rather than off the event, which
	 * carries neither. They are the same numbers {@code Screen.width}/{@code height} hold.
	 */
	static void afterScreenInit(final ScreenInitListener listener) {
		MinecraftForge.EVENT_BUS.addListener((ScreenEvent.Init.Post event) -> {
			// See SCREEN_TICKERS: a re-init of the same screen object must not accumulate a
			// second copy of the anchor callback the listener is about to register.
			SCREEN_TICKERS.remove(event.getScreen());

			Minecraft minecraft = Minecraft.getInstance();
			ScreenEvent.Init previous = currentInit;
			currentInit = event;

			try {
				listener.afterScreenInit(minecraft, event.getScreen(),
						minecraft.getWindow().getGuiScaledWidth(),
						minecraft.getWindow().getGuiScaledHeight());
			} finally {
				currentInit = previous;
			}
		});
	}

	/** Registers a per-tick callback for one screen. See {@link #SCREEN_TICKERS}. */
	static void afterScreenTick(final Screen screen, final Consumer<Screen> listener) {
		installTickPump();
		SCREEN_TICKERS.computeIfAbsent(screen, key -> new ArrayList<>()).add(listener);
	}

	/**
	 * Adds a widget to the screen's renderable AND event-listener lists.
	 *
	 * <p>This is the one call in the shared client wiring with no vanilla equivalent —
	 * {@code Screen.addRenderableWidget} is protected — which is why fabric-api has
	 * {@code Screens.getButtons} at all. {@code ScreenEvent.Init.addListener} routes to
	 * {@code Screen::addEventWidget}, which appends to both lists, so a tab added through it
	 * both draws and takes clicks.
	 */
	static void addWidget(final Screen screen, final GuiEventListener widget) {
		ScreenEvent.Init init = currentInit;

		if (init != null && init.getScreen() == screen) {
			init.addListener(widget);
			return;
		}

		// Should be unreachable: the shared call site only runs inside the afterScreenInit
		// dispatch. Loud rather than silent, because a widget added to nothing is an invisible
		// tab and no error anywhere.
		Archetypes.LOGGER.error("addWidget called outside a screen-init dispatch for {}; the widget was dropped",
				screen.getClass().getName());
	}

	private static void installTickPump() {
		if (tickPumpInstalled) {
			return;
		}

		tickPumpInstalled = true;

		MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
			if (event.phase != TickEvent.Phase.END) {
				return;
			}

			Screen screen = Minecraft.getInstance().screen;

			if (screen == null) {
				return;
			}

			List<Consumer<Screen>> tickers = SCREEN_TICKERS.get(screen);

			if (tickers == null) {
				return;
			}

			for (final Consumer<Screen> ticker : tickers) {
				ticker.accept(screen);
			}
		});
	}

	/**
	 * The four parameters {@code ScreenEvents.AFTER_INIT} passes, in that order, so the shared
	 * lambda in {@code ArchetypesClient} infers them and needs no change.
	 */
	@FunctionalInterface
	interface ScreenInitListener {
		void afterScreenInit(Minecraft client, Screen screen, int scaledWidth, int scaledHeight);
	}
}
