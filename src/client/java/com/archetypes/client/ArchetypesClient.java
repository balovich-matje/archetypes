package com.archetypes.client;

import java.util.Map;

import com.archetypes.Archetype;
import com.archetypes.ModState;
import com.archetypes.ModEntities;
import com.archetypes.NodePurchases;
import com.archetypes.SubTree;
import com.archetypes.client.mixin.AbstractContainerScreenAccessor;
import com.archetypes.platform.Net;
import com.archetypes.state.WireId;

import com.archetypes.platform.Platform;

import com.mojang.blaze3d.platform.InputConstants;

// THE CLIENT SEAM IS THIS FILE, and on the loader axis it stays this file. A client-side helper
// is unavoidable rather than a `platform` seam member: `com.archetypes.platform` lives in
// `src/main`, which cannot see `net.minecraft.client` at all (measured — read
// `platform/ClientNetHooks`' header for the three dependency reports that proved it). So the
// loader helpers are `client/NeoForgeClientEvents` and `client/ForgeClientEvents`, in this same
// package, and every node script excludes the other loader's by anchored glob.
//
// Only the WIRING forks. Every line of UI logic, every anchor formula, the toast gate and the
// eight ability-key rules stay outside every conditional — §5a applied to events instead of to
// mixins, which is what keeps one implementation on all seven nodes.
//
// SKILL PROFICIENCIES' THREE CLIENT HELPERS ARE REUSED UNCHANGED (afterScreenInit, addWidget,
// afterScreenTick — the bookmark surface is the same one). FIVE MORE ARE NEW HERE AND HAVE NO
// PRECEDENT NEXT DOOR AT ALL: a client tick, key-mapping registration, an entity-renderer
// registration, a particle-provider registration, and — on the two nodes below 1.21.11 — the
// HUD, which is not a helper but `client/mixin/GuiMixin` (R-11).
//? if fabric {
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
//?}
// 26.1 renamed the MODULE and the class with it: `fabric-key-binding-api-v1` /
// `client.keybinding.v1.KeyBindingHelper.registerKeyBinding` became
// `fabric-key-mapping-api-v1` / `client.keymapping.v1.KeyMappingHelper.registerKeyMapping`.
// The node script swaps the module at the same boundary.
//
// WATCH THE ADJACENT LINE (conventions §5k): the KeyMapping CONSTRUCTOR's category argument
// is a different boundary — `KeyMapping.Category` is `>=1.21.11` and 1.21.11 HAS it
// (`javap`: `KeyMapping(String, InputConstants$Type, int, KeyMapping$Category)` plus the
// `Category` record with `register(Identifier)`), so the `new KeyMapping(...)` below is
// SHARED on this node and only breaks at Stage 4. Collapsing the two into one predicate is
// exactly the bug §5k describes.
//? if fabric && >=26.1 {
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
//?} elif fabric {
/*import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
*///?}
// STAGE 4 — the `hud` package of fabric-rendering-v1 does not exist below 1.21.11
// (0.116.14+1.21.1 ships fabric-rendering-v1 3.x), so there is nothing to register a HUD
// element with and nothing to wrap a vanilla one with. All eight calls at the bottom of this
// method move into `client/mixin/GuiMixin.java`, which maps each one onto a vanilla `Gui`
// method by full descriptor. Read that file's header for the mapping; the DRAW code is the
// same six shared render methods either way (conventions §5l).
//? if >=1.21.11 {
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
//?}
//? if fabric {
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
//?}
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

// Only the declaration forks — the same shape `Archetypes` uses, for the same reason. On the
// loader axis `onInitializeClient()` is a plain public method the node's client-setup hook
// calls, and THE FORGE LESSON IS THAT THIS IS EASY TO FORGET: Skill Proficiencies shipped its
// 1.20.1-forge jar with every client helper present and NOTHING invoking them, because no
// bootstrap existed. The fix there was `@Mod.EventBusSubscriber(Dist.CLIENT, bus = MOD)` plus
// an `FMLClientSetupEvent` subscriber — and the `@SubscribeEvent` method MUST be public, or it
// silently never fires. That is a runtime-only bug on the node with the most client surface in
// this repo.
//? if fabric {
public class ArchetypesClient implements ClientModInitializer {
//?} else {
/*public class ArchetypesClient {
*///?}
	private static final int BUTTON_SIZE = 20;
	/**
	 * When Specialities is installed its "S" button owns the top slot beside the
	 * inventory, so we sit one slot below it. Alone, we take the top slot.
	 */
	private static final String SPECIALITIES = "specialities";

	/** The ability binds: slots 0-2 are the sub-trees left to right, slot 3
	 * is the Elementalist's capstone, slots 4-6 are the epic actives, shared
	 * across archetypes — 4 is Lightning Strike or Deadeye, 5 is Magic
	 * Armaments, Death Mark or the Colossus Slayer's Parry, 6 is the Dark
	 * Ritual or Titan's Leap. Exposed so the cooldown bar can label its
	 * slots. */
	static final KeyMapping[] ABILITY_KEYS = new KeyMapping[7];

	/** Our own section in the controls screen, not vanilla's Gameplay. */
	// STAGE 4, and this is the boundary §5k's warning above was written for: `KeyMapping.Category`
	// is `>=1.21.11`, one step BELOW the `>=26.1` module rename on the adjacent lines. Below it
	// the constructor's fourth argument is a raw translation KEY string. The key is the same
	// either way — 1.21.11's `Category.label()` builds `key.category.<namespace>.<path>` from the
	// Identifier, which is `key.category.archetypes.archetypes`, exactly the entry the lang file
	// already carries — so nothing outside this declaration moves and no resource forks.
	//? if >=1.21.11 {
	private static final KeyMapping.Category KEY_CATEGORY =
			KeyMapping.Category.register(com.archetypes.Archetypes.id("archetypes"));
	//?} else {
	/*private static final String KEY_CATEGORY = "key.category.archetypes.archetypes";
	*///?}

	/** Last archetype level seen, for the level-up toast; -1 = not yet
	 * observed this session, so the join-time sync never toasts. */
	private static int lastLevel = -1;

	/** Whether last tick had a bow mid-draw — the edge lets Disengage drain
	 * stale sprint presses the moment a draw begins. */
	private static boolean wasDrawingBow;

	/** Whether each ability key was already held last tick. GLFW auto-repeat
	 * feeds KeyboardHandler a stream of PRESS-shaped events while a key is
	 * held, and 26.2's handler calls KeyMapping.click for every one of them
	 * (it only branches on release) — so a HELD key racks up clicks at the OS
	 * repeat rate. For a press-to-fire ability that meant one payload per
	 * repeat: the Dark Ritual's toggle started, cancelled and restarted its
	 * channel several times a second, which is the sound the author heard.
	 * Clicks made while the key was already down are dropped here. */
	private static final boolean[] ABILITY_KEY_HELD = new boolean[ABILITY_KEYS.length];

	//? if fabric {
	@Override
	//?}
	public void onInitializeClient() {
		//? if >=1.21 {
		SlayerAnimations.initialize();
		NightAnimations.initialize();
		ElementalistAnimations.initialize();
		DaggerAnimations.initialize();
		ProtectorAnimations.initialize();
		//?}
		NightFormFx.initialize();
		RadianceLight.initialize();

		// Another straight 26.1 rename inside fabric-api: `ParticleFactoryRegistry` became
		// `ParticleProviderRegistry` (and `PendingParticleFactory` -> `PendingParticleProvider`).
		// `getInstance()` and both `register` overloads are shape-identical (`javap` on
		// fabric-particles-v1 5.0.18 and 4.2.12), so the constructor reference is shared.
		// Registration only; the provider reference is shared. A loader helper registers the
		// same `(ParticleType, provider factory)` pair from `RegisterParticleProvidersEvent` on
		// the MOD event bus, client side only.
		//? if fabric && >=26.1 {
		net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry.getInstance()
				.register(com.archetypes.ModParticles.GREATSWORD_SWEEP, GreatswordSweepParticle.Provider::new);
		//?} elif fabric {
		/*net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry.getInstance()
				.register(com.archetypes.ModParticles.GREATSWORD_SWEEP, GreatswordSweepParticle.Provider::new);
		*///?} elif neoforge {
		/*NeoForgeClientEvents.particleProvider(com.archetypes.ModParticles.GREATSWORD_SWEEP,
				GreatswordSweepParticle.Provider::new);
		*///?} elif forge {
		/*ForgeClientEvents.particleProvider(com.archetypes.ModParticles.GREATSWORD_SWEEP,
				GreatswordSweepParticle.Provider::new);
		*///?}

		// Rebindable slot keys — what a slot casts depends on the archetype,
		// and the server resolves that; the cooldown bar shows each slot's
		// current bind. The keys only report the press. V, N, M are vanilla-free.
		int[] defaults = { GLFW.GLFW_KEY_G, GLFW.GLFW_KEY_H, GLFW.GLFW_KEY_B, GLFW.GLFW_KEY_V,
				GLFW.GLFW_KEY_N, GLFW.GLFW_KEY_M, GLFW.GLFW_KEY_J };

		for (int slot = 0; slot < ABILITY_KEYS.length; slot++) {
			// Registration only — the `new KeyMapping(...)` below it, its `>=1.21.11` category
			// fork and the seven defaults are all shared. A loader helper registers the mapping
			// with `RegisterKeyMappingsEvent` on the MOD event bus and RETURNS THE SAME
			// INSTANCE, which is what the array assignment depends on: everything downstream
			// polls `ABILITY_KEYS[slot].isDown()`/`consumeClick()`, so a helper that registered
			// a copy would leave seven keys that are bound in the controls screen and dead in
			// game.
			//? if fabric && >=26.1 {
			ABILITY_KEYS[slot] = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			//?} elif fabric {
			/*ABILITY_KEYS[slot] = KeyBindingHelper.registerKeyBinding(new KeyMapping(
			*///?} elif neoforge {
			/*ABILITY_KEYS[slot] = NeoForgeClientEvents.registerKeyMapping(new KeyMapping(
			*///?} elif forge {
			/*ABILITY_KEYS[slot] = ForgeClientEvents.registerKeyMapping(new KeyMapping(
			*///?}
					"key.archetypes.ability_" + (slot + 1), InputConstants.Type.KEYSYM, defaults[slot],
					KEY_CATEGORY));
		}

		// THE BIGGEST NEW CLIENT SEAM, and Skill Proficiencies has no client tick event at all —
		// there is nothing to copy. Registration only; the whole body below (the ability-key
		// poll with its GLFW auto-repeat guard, the level-up toast, the flamethrower channel and
		// the three sprint-key edges) is shared.
		//
		// What a loader helper owes: fire ONCE per client tick, at the END of it, with the
		// `Minecraft`. On NeoForge that is `ClientTickEvent.Post`; on LexForge it is
		// `TickEvent.ClientTickEvent` AND THE PHASE MUST BE CHECKED — that event fires twice per
		// tick, START and END, and firing on both would double every `consumeClick` drain and
		// send two payloads per press.
		//? if fabric {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
		//?} elif neoforge {
		/*NeoForgeClientEvents.endClientTick(client -> {
		*///?} elif forge {
		/*ForgeClientEvents.endClientTick(client -> {
		*///?}
			for (int slot = 0; slot < ABILITY_KEYS.length; slot++) {
				boolean held = ABILITY_KEYS[slot].isDown();
				boolean clicked = false;

				// The queue is always drained, held or not, so a backlog can
				// never leak into a later tick and fire twice.
				while (ABILITY_KEYS[slot].consumeClick()) {
					clicked = true;
				}

				// A tap that began and ended inside one tick still fires (it
				// is not down NOW, so it cannot be a repeat); several clicks
				// in one tick collapse to one payload.
				if (clicked && !(held && ABILITY_KEY_HELD[slot]) && client.player != null) {
					final int pressed = slot;
					Net.INSTANCE.sendToServer(WireId.ACTIVE_ABILITY, buf -> buf.writeVarInt(pressed));
				}

				ABILITY_KEY_HELD[slot] = held;
			}

			// The level-up toast: the XP attachment syncs to this client, so
			// watching the derived level needs no packet of its own.
			if (client.player == null || ModState.get(client.player) == null) {
				lastLevel = -1;
			} else {
				int level = com.archetypes.SkillPoints.level(client.player);

				if (lastLevel >= 0 && level > lastLevel) {
					// 26.2 moved the toast manager onto the Gui object with it;
					// 26.1 still answers Minecraft.getToastManager(). Below
					// 1.21.11 it is getToasts(), returning a ToastComponent —
					// same addToast(Toast), different accessor and different
					// return type, which is why this is a three-arm chain and
					// not two — and why it is written in BLOCK form: the
					// one-line inline `elif` cannot chain (it closes the scope,
					// so it can only ever be the last arm), which is the
					// `Unmatched scope closer` the other repo measured.
					//? if >=26.2 {
					client.gui.toastManager().addToast(new ArchetypeLevelUpToast(
							ModState.get(client.player), lastLevel, level));
					//?} elif >=1.21.11 {
					/*client.getToastManager().addToast(new ArchetypeLevelUpToast(
							ModState.get(client.player), lastLevel, level));
					*///?} else {
					/*client.getToasts().addToast(new ArchetypeLevelUpToast(
							ModState.get(client.player), lastLevel, level));
					*///?}
				}

				lastLevel = level;
			}

			// The Flamethrower is a channel, not a press: while the CAPSTONE
			// key is held, one payload per tick keeps the stream alive. The
			// press payload above still goes out; the server ignores it for
			// the channel holder.
			if (client.player != null && ABILITY_KEYS[3].isDown()
					&& ModState.get(client.player) == Archetype.INTELLECT) {
				var owned = NodePurchases.owned(client.player, SubTree.ELEMENTALIST);

				if (com.archetypes.ElementalistNodes.rank(SubTree.ELEMENTALIST, owned,
						com.archetypes.ElementalistNodes.Family.FLAMETHROWER) > 0) {
					Net.INSTANCE.sendToServer(WireId.SPELL_CHANNEL, buf -> { });
				}
			}

			// Ghost Form's dash: sprint pressed while sneaking, in night form.
			// Consumed before the Shield Rush edge below because the two read
			// the same key; a sneaking vampire is never also blocking a rush.
			if (client.player != null && client.player.isShiftKeyDown()
					&& com.archetypes.NightForm.isActive(client.player)) {
				while (client.options.keySprint.consumeClick()) {
					Net.INSTANCE.sendToServer(WireId.NIGHT_DASH, buf -> { });
				}
			}

			// Shield Rush: sprint pressed while the shield is raised. Only
			// consumed while blocking, so normal sprinting is untouched.
			if (client.player != null && client.player.isBlocking()) {
				while (client.options.keySprint.consumeClick()) {
					Net.INSTANCE.sendToServer(WireId.RUSH, buf -> { });
				}
			}

			// Disengage: a sprint press made while a bow is drawn. Vanilla
			// reads the sprint key via isDown and never consumes its CLICKS,
			// so presses pile up during normal running — and the moment a
			// draw began, those stale presses fired the roll instantly (user
			// bug). Drain the backlog on the draw's first tick; only presses
			// made during the draw itself count.
			// Vault widens the gate to a crossbow, and for a crossbow "aimed"
			// means charged. Decided client-side off the SAME owned-node set
			// the server re-validates with, so the two never disagree about
			// whether a press should have gone out.
			boolean drawingBow = client.player != null && client.player.isUsingItem()
					&& client.player.getUseItem().is(net.minecraft.world.item.Items.BOW);

			if (client.player != null && !drawingBow
					&& com.archetypes.NemesisMarksmanNodes.rank(client.player,
							com.archetypes.NemesisMarksmanNodes.Family.VAULT) > 0) {
				var main = client.player.getMainHandItem();
				drawingBow = (client.player.isUsingItem()
						&& client.player.getUseItem().is(net.minecraft.world.item.Items.CROSSBOW))
						|| (main.is(net.minecraft.world.item.Items.CROSSBOW)
								&& net.minecraft.world.item.CrossbowItem.isCharged(main));
			}

			if (drawingBow) {
				while (client.options.keySprint.consumeClick()) {
					if (wasDrawingBow) {
						Net.INSTANCE.sendToServer(WireId.DISENGAGE, buf -> { });
					}
				}
			}

			wasDrawingBow = drawingBow;
		});

		// Seeker spells render as thrown items — the projectile carries which,
		// and empowered missiles come out half again bigger.
		// Registration only. A loader helper registers the same pair from
		// `EntityRenderersEvent.RegisterRenderers` on the MOD event bus.
		//? if fabric {
		net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(
				ModEntities.SPELL_PROJECTILE, SpellProjectileRenderer::new);
		//?} elif neoforge {
		/*NeoForgeClientEvents.entityRenderer(ModEntities.SPELL_PROJECTILE, SpellProjectileRenderer::new);
		*///?} elif forge {
		/*ForgeClientEvents.entityRenderer(ModEntities.SPELL_PROJECTILE, SpellProjectileRenderer::new);
		*///?}

		// EVERYTHING FROM HERE TO THE END OF THE HUD BLOCK IS `>=1.21.11` ONLY. Below the
		// boundary fabric-rendering-v1 has no `hud` package at all, so these eight calls have
		// no counterpart to fork INTO — they move wholesale into client/mixin/GuiMixin.java,
		// which anchors each of them on a vanilla `Gui` method by full descriptor. The six
		// render methods they name are unchanged and are what that mixin calls, so this is a
		// registration fork and nothing more (conventions §5l).
		//? if >=1.21.11 {
		// The centred bar of owned-active cooldowns, the proc flashes that
		// fall from the crosshair, and the Seeker's mana bottles. All after
		// HOTBAR so they draw on top.
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR,
				com.archetypes.Archetypes.id("cooldown_bar"), CooldownBarHud::render);
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR,
				com.archetypes.Archetypes.id("proc_indicators"), ProcIndicatorHud::render);
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR,
				com.archetypes.Archetypes.id("mana_bar"), ManaHud::render);

		// Well Fed's bank: a halo around the drumsticks vanilla stops filling at
		// twenty. Attached rather than replacing FOOD_BAR, because the row
		// underneath is still vanilla's and still says everything it always
		// said — but attached after FOOD_BAR specifically, not after HOTBAR
		// like the bars above. The vanilla elements run in registry order
		// (HOTBAR, ARMOR_BAR, HEALTH_BAR, FOOD_BAR, AIR_BAR), so anchoring to
		// the hotbar would have drawn the mark UNDER the icons it marks.
		HudElementRegistry.attachElementAfter(VanillaHudElements.FOOD_BAR,
				com.archetypes.Archetypes.id("banked_hunger"), BankedHungerHud::render);

		// Sunlight through a vampire's eyes. On MISC_OVERLAYS like Specialities'
		// stealth vignette, so it washes the world but stays under the bars.
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS,
				com.archetypes.Archetypes.id("sun_blind"), SunBlindOverlay::render);

		// The Deadeye stance's concentration vignette, same layer for the same
		// reason: it washes the world, not the bars.
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS,
				com.archetypes.Archetypes.id("deadeye_focus"), DeadeyeOverlay::render);

		// The night form pins hunger full and stops natural regeneration, so
		// the hunger row is a gauge of nothing while it lasts: it is not
		// greyed, it is gone. Reverts the frame the form lapses — the gate is
		// re-read every draw and no state is stashed.
		HudElementRegistry.replaceElement(VanillaHudElements.FOOD_BAR, original ->
				(graphics, tickCounter) -> {
					if (!UndeadHud.active()) {
						//? if >=26.1 {
						original.extractRenderState(graphics, tickCounter);
						//?} else {
						/*original.render(graphics, tickCounter);
						*///?}
					}
				});

		// The mana row sits where vanilla draws air bubbles; for a Seeker the
		// bubbles step up one row instead of hiding the orbs underwater.
		HudElementRegistry.replaceElement(VanillaHudElements.AIR_BAR, original ->
				(graphics, tickCounter) -> {
					// `HudElement`'s own functional method is the same extract-vs-immediate
					// move — `extractRenderState(GuiGraphicsExtractor, DeltaTracker)` is
					// `render(GuiGraphics, DeltaTracker)` below 26.1. The registry around it
					// does NOT move: `HudElementRegistry.replaceElement/attachElementAfter`
					// and every `VanillaHudElements` id are declared the same on
					// fabric-rendering-v1 16.2.10 (measured). `pose()` is a
					// `Matrix3x2fStack` on both, so the shift math is shared.
					if (ManaHud.visible()) {
						graphics.pose().pushMatrix();
						graphics.pose().translate(0.0F, -10.0F);
						//? if >=26.1 {
						original.extractRenderState(graphics, tickCounter);
						//?} else {
						/*original.render(graphics, tickCounter);
						*///?}
						graphics.pose().popMatrix();
					} else {
						//? if >=26.1 {
						original.extractRenderState(graphics, tickCounter);
						//?} else {
						/*original.render(graphics, tickCounter);
						*///?}
					}
				});
		//?}

		// Below 26.1 the remapping loom splits every split-environment mod jar and
		// `src/main` sees the common half only, so two things it reaches — fabric-api's
		// `ClientPlayNetworking` and Skill Proficiencies' `SpecialitiesClient.hudShift()` —
		// have to be handed down from here. Must run BEFORE `clientReceivers` below.
		//? if <26.1 {
		/*ClientHandDown.install();
		*///?}

		// R-B1's missing half. On the one node whose fabric-api cannot sync an attachment,
		// `platform/LegacyStateSync` moves all 47 wire-carrying keys over a channel of its
		// own, and until Stage 5's gate went looking, nothing on this side had ever
		// registered to receive them. Read that file's header for what the silence looked
		// like. Registered here rather than through `Net#clientReceivers` because the sink
		// needs the ENTITY the packet names, and resolving an entity id needs `Minecraft`
		// — which is the one thing the seam is not allowed to see.
		//? if <1.20.5 {
		/*LegacyStateSyncClient.install();
		*///?}

		// The two clientbound channels, handed down to the seam so registration itself
		// stays in common init on every loader (see Net#clientReceivers). Each sink
		// schedules its own hop onto the client thread — the buffer is ours, so a
		// deferred read of it is safe. The second one is what a parry did to the swing
		// timer: the server already wrote its own copy, and this keeps the crosshair's
		// charge indicator, and the mod's own "no half-charged flicks" gate in
		// MinecraftMixin, telling the truth.
		Net.INSTANCE.clientReceivers(Map.of(
				WireId.PASSIVE_PROC, buf -> {
					String subTreeId = buf.readUtf();
					String family = buf.readUtf();
					Minecraft.getInstance().execute(() -> ProcIndicatorHud.push(subTreeId, family));
				},
				WireId.PARRY_SWING, buf -> {
					int ticker = buf.readVarInt();
					Minecraft.getInstance().execute(() -> {
						if (Minecraft.getInstance().player != null) {
							com.archetypes.ColossusSlayer.applySwingTicker(
									Minecraft.getInstance().player, ticker);
						}
					});
				}));

		// Registration only — every line of the tab and button construction below is shared,
		// including its `>=26.2` screen-management fork. Skill Proficiencies' three client
		// helpers are reused here unchanged: a listener taking the same four parameters in the
		// same order (Minecraft, Screen, int, int), firing AFTER the screen's widgets exist.
		//? if fabric {
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
		//?} elif neoforge {
		/*NeoForgeClientEvents.afterScreenInit((client, screen, scaledWidth, scaledHeight) -> {
		*///?} elif forge {
		/*ForgeClientEvents.afterScreenInit((client, screen, scaledWidth, scaledHeight) -> {
		*///?}
			if (screen instanceof InventoryScreen) {
				// Survival inventory: a bookmark on the top edge, clear of
				// the effect list vanilla draws to the panel's right. The
				// recipe book shifts leftPos without re-running init, so it
				// re-anchors every tick — and refreshes the label, since the
				// archetype can be picked while this screen is still alive.
				BookmarkTab tab = new BookmarkTab(tabLabel(client), () -> openArchetypeUi(client, screen));
				anchorTab((AbstractContainerScreen<?>) screen, tab);
				// `Screens.getButtons` was renamed `getWidgets` at 26.1 — same list, same
				// element type (measured on fabric-screen-api-v1 3.1.7 and 5.1.0).
				// The loader helper adds the widget to the screen's own renderable+event lists;
				// it is the one call here with no vanilla equivalent, since
				// `Screen.addRenderableWidget` is protected.
				//? if fabric && >=26.1 {
				Screens.getWidgets(screen).add(tab);
				//?} elif fabric {
				/*Screens.getButtons(screen).add(tab);
				*///?} elif neoforge {
				/*NeoForgeClientEvents.addWidget(screen, tab);
				*///?} elif forge {
				/*ForgeClientEvents.addWidget(screen, tab);
				*///?}

				// The re-anchor is not cosmetic: the recipe book shifts `leftPos` without
				// re-running init, so a helper that fired only once would leave the bookmark
				// behind the panel the first time the book is opened.
				//? if fabric {
				ScreenEvents.afterTick(screen).register(s -> {
				//?} elif neoforge {
				/*NeoForgeClientEvents.afterScreenTick(screen, s -> {
				*///?} elif forge {
				/*ForgeClientEvents.afterScreenTick(screen, s -> {
				*///?}
					anchorTab((AbstractContainerScreen<?>) s, tab);
					tab.setMessage(tabLabel(client));
				});
			} else if (screen instanceof CreativeModeInventoryScreen) {
				// Creative keeps the compact square to the panel's right:
				// the top edge belongs to the real creative tabs, and
				// creative shows no effect list to collide with.
				Button button = Button.builder(label(client), b -> openArchetypeUi(client, screen))
						.bounds(0, 0, BUTTON_SIZE, BUTTON_SIZE)
						.tooltip(Tooltip.create(Component.translatable("screen.archetypes.button")))
						.build();
				anchorButton((AbstractContainerScreen<?>) screen, button);
				//? if fabric && >=26.1 {
				Screens.getWidgets(screen).add(button);
				//?} elif fabric {
				/*Screens.getButtons(screen).add(button);
				*///?} elif neoforge {
				/*NeoForgeClientEvents.addWidget(screen, button);
				*///?} elif forge {
				/*ForgeClientEvents.addWidget(screen, button);
				*///?}

				//? if fabric {
				ScreenEvents.afterTick(screen).register(s -> {
				//?} elif neoforge {
				/*NeoForgeClientEvents.afterScreenTick(screen, s -> {
				*///?} elif forge {
				/*ForgeClientEvents.afterScreenTick(screen, s -> {
				*///?}
					anchorButton((AbstractContainerScreen<?>) s, button);
					button.setMessage(label(client));
				});
			}
		});
	}

	/** Gold while you still have a choice to make; plain once you've picked. */
	private static Component label(final net.minecraft.client.Minecraft client) {
		boolean unpicked = client.player == null || ModState.get(client.player) == null;
		return Component.literal("A").withStyle(unpicked ? ChatFormatting.GOLD : ChatFormatting.WHITE);
	}

	/**
	 * The bookmark spells it out; unstyled it takes the tab's dark label
	 * ink. Gold whenever the tree wants a visit — no archetype picked yet,
	 * or a perk point sitting unspent.
	 */
	private static Component tabLabel(final net.minecraft.client.Minecraft client) {
		boolean beckons = client.player == null
				|| ModState.get(client.player) == null
				|| com.archetypes.SkillPoints.available(client.player) > 0;
		var text = Component.translatable("screen.archetypes.button");
		return beckons ? text.withStyle(ChatFormatting.GOLD) : text;
	}

	private static void anchorTab(final AbstractContainerScreen<?> screen, final BookmarkTab tab) {
		AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) screen;
		int x = accessor.archetypes$getLeftPos() + 4;

		// Slot in after the Skills bookmark, whose width both mods compute
		// with the same label-plus-padding formula.
		if (Platform.INSTANCE.isModLoaded(SPECIALITIES)) {
			x += BookmarkTab.widthFor(Component.translatable("screen.specialities.skills")) + 2;
		}

		tab.setX(x);
		tab.setY(accessor.archetypes$getTopPos() - BookmarkTab.HEIGHT);
	}

	private static void openArchetypeUi(final net.minecraft.client.Minecraft client, final Screen parent) {
		if (client.player == null) {
			return;
		}

		Archetype current = ModState.get(client.player);
		// 26.2 moved screen management off Minecraft onto the Gui object.
		/*? if >=26.2 {*/client.gui.setScreen(current == null
		/*?} else *///client.setScreen(current == null
				? new ArchetypePickerScreen(parent)
				: new ArchetypeScreen(parent, current));
	}

	private static void anchorButton(final AbstractContainerScreen<?> screen, final Button button) {
		AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) screen;
		int slot = Platform.INSTANCE.isModLoaded(SPECIALITIES) ? 1 : 0;
		button.setX(accessor.archetypes$getLeftPos() + accessor.archetypes$getImageWidth() + 4);
		button.setY(accessor.archetypes$getTopPos() + slot * (BUTTON_SIZE + 4));
	}
}
