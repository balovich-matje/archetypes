package com.archetypes.client;

import com.archetypes.ActiveAbilityPayload;
import com.archetypes.Archetype;
import com.archetypes.ModAttachments;
import com.archetypes.ModEntities;
import com.archetypes.NodePurchases;
import com.archetypes.SpellChannelPayload;
import com.archetypes.SubTree;
import com.archetypes.client.mixin.AbstractContainerScreenAccessor;
import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class ArchetypesClient implements ClientModInitializer {
	private static final int BUTTON_SIZE = 20;
	/**
	 * When Specialities is installed its "S" button owns the top slot beside the
	 * inventory, so we sit one slot below it. Alone, we take the top slot.
	 */
	private static final String SPECIALITIES = "specialities";

	/** The ability binds: slots 0-2 are the sub-trees left to right, slot 3
	 * is the Elementalist's capstone, slots 4-6 are the epic actives (Lightning
	 * Strike, Magic Armaments, Dark Ritual). Exposed so the cooldown bar can
	 * label its slots. */
	static final KeyMapping[] ABILITY_KEYS = new KeyMapping[7];

	/** Our own section in the controls screen, not vanilla's Gameplay. */
	private static final KeyMapping.Category KEY_CATEGORY =
			KeyMapping.Category.register(com.archetypes.Archetypes.id("archetypes"));

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

	@Override
	public void onInitializeClient() {
		SlayerAnimations.initialize();
		NightAnimations.initialize();
		NightFormFx.initialize();
		RadianceLight.initialize();

		net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry.getInstance()
				.register(com.archetypes.ModParticles.GREATSWORD_SWEEP, GreatswordSweepParticle.Provider::new);

		// Rebindable slot keys — what a slot casts depends on the archetype,
		// and the server resolves that; the cooldown bar shows each slot's
		// current bind. The keys only report the press. V, N, M are vanilla-free.
		int[] defaults = { GLFW.GLFW_KEY_G, GLFW.GLFW_KEY_H, GLFW.GLFW_KEY_B, GLFW.GLFW_KEY_V,
				GLFW.GLFW_KEY_N, GLFW.GLFW_KEY_M, GLFW.GLFW_KEY_J };

		for (int slot = 0; slot < ABILITY_KEYS.length; slot++) {
			ABILITY_KEYS[slot] = KeyMappingHelper.registerKeyMapping(new KeyMapping(
					"key.archetypes.ability_" + (slot + 1), InputConstants.Type.KEYSYM, defaults[slot],
					KEY_CATEGORY));
		}

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
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
					ClientPlayNetworking.send(new ActiveAbilityPayload(slot));
				}

				ABILITY_KEY_HELD[slot] = held;
			}

			// The level-up toast: the XP attachment syncs to this client, so
			// watching the derived level needs no packet of its own.
			if (client.player == null || ModAttachments.get(client.player) == null) {
				lastLevel = -1;
			} else {
				int level = com.archetypes.SkillPoints.level(client.player);

				if (lastLevel >= 0 && level > lastLevel) {
					client.gui.toastManager().addToast(new ArchetypeLevelUpToast(
							ModAttachments.get(client.player), lastLevel, level));
				}

				lastLevel = level;
			}

			// The Flamethrower is a channel, not a press: while the CAPSTONE
			// key is held, one payload per tick keeps the stream alive. The
			// press payload above still goes out; the server ignores it for
			// the channel holder.
			if (client.player != null && ABILITY_KEYS[3].isDown()
					&& ModAttachments.get(client.player) == Archetype.INTELLECT) {
				var owned = NodePurchases.owned(client.player, SubTree.ELEMENTALIST);

				if (com.archetypes.ElementalistNodes.rank(SubTree.ELEMENTALIST, owned,
						com.archetypes.ElementalistNodes.Family.FLAMETHROWER) > 0) {
					ClientPlayNetworking.send(new SpellChannelPayload());
				}
			}

			// Ghost Form's dash: sprint pressed while sneaking, in night form.
			// Consumed before the Shield Rush edge below because the two read
			// the same key; a sneaking vampire is never also blocking a rush.
			if (client.player != null && client.player.isShiftKeyDown()
					&& com.archetypes.NightForm.isActive(client.player)) {
				while (client.options.keySprint.consumeClick()) {
					ClientPlayNetworking.send(new com.archetypes.NightDashPayload());
				}
			}

			// Shield Rush: sprint pressed while the shield is raised. Only
			// consumed while blocking, so normal sprinting is untouched.
			if (client.player != null && client.player.isBlocking()) {
				while (client.options.keySprint.consumeClick()) {
					ClientPlayNetworking.send(new com.archetypes.RushPayload());
				}
			}

			// Disengage: a sprint press made while a bow is drawn. Vanilla
			// reads the sprint key via isDown and never consumes its CLICKS,
			// so presses pile up during normal running — and the moment a
			// draw began, those stale presses fired the roll instantly (user
			// bug). Drain the backlog on the draw's first tick; only presses
			// made during the draw itself count.
			boolean drawingBow = client.player != null && client.player.isUsingItem()
					&& client.player.getUseItem().is(net.minecraft.world.item.Items.BOW);

			if (drawingBow) {
				while (client.options.keySprint.consumeClick()) {
					if (wasDrawingBow) {
						ClientPlayNetworking.send(new com.archetypes.DisengagePayload());
					}
				}
			}

			wasDrawingBow = drawingBow;

		});

		// Seeker spells render as thrown items — the projectile carries which,
		// and empowered missiles come out half again bigger.
		net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(
				ModEntities.SPELL_PROJECTILE, SpellProjectileRenderer::new);

		// The centred bar of owned-active cooldowns, the proc flashes that
		// fall from the crosshair, and the Seeker's mana bottles. All after
		// HOTBAR so they draw on top.
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR,
				com.archetypes.Archetypes.id("cooldown_bar"), CooldownBarHud::render);
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR,
				com.archetypes.Archetypes.id("proc_indicators"), ProcIndicatorHud::render);
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR,
				com.archetypes.Archetypes.id("mana_bar"), ManaHud::render);

		// Sunlight through a vampire's eyes. On MISC_OVERLAYS like Specialities'
		// stealth vignette, so it washes the world but stays under the bars.
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS,
				com.archetypes.Archetypes.id("sun_blind"), SunBlindOverlay::render);

		// The night form pins hunger full and stops natural regeneration, so
		// the hunger row is a gauge of nothing while it lasts: it is not
		// greyed, it is gone. Reverts the frame the form lapses — the gate is
		// re-read every draw and no state is stashed.
		HudElementRegistry.replaceElement(VanillaHudElements.FOOD_BAR, original ->
				(graphics, tickCounter) -> {
					if (!UndeadHud.active()) {
						original.extractRenderState(graphics, tickCounter);
					}
				});

		// The mana row sits where vanilla draws air bubbles; for a Seeker the
		// bubbles step up one row instead of hiding the orbs underwater.
		HudElementRegistry.replaceElement(VanillaHudElements.AIR_BAR, original ->
				(graphics, tickCounter) -> {
					if (ManaHud.visible()) {
						graphics.pose().pushMatrix();
						graphics.pose().translate(0.0F, -10.0F);
						original.extractRenderState(graphics, tickCounter);
						graphics.pose().popMatrix();
					} else {
						original.extractRenderState(graphics, tickCounter);
					}
				});

		ClientPlayNetworking.registerGlobalReceiver(com.archetypes.PassiveProcPayload.TYPE,
				(payload, context) -> context.client().execute(() -> ProcIndicatorHud.push(payload)));

		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (screen instanceof InventoryScreen) {
				// Survival inventory: a bookmark on the top edge, clear of
				// the effect list vanilla draws to the panel's right. The
				// recipe book shifts leftPos without re-running init, so it
				// re-anchors every tick — and refreshes the label, since the
				// archetype can be picked while this screen is still alive.
				BookmarkTab tab = new BookmarkTab(tabLabel(client), () -> openArchetypeUi(client, screen));
				anchorTab((AbstractContainerScreen<?>) screen, tab);
				Screens.getWidgets(screen).add(tab);

				ScreenEvents.afterTick(screen).register(s -> {
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
				Screens.getWidgets(screen).add(button);

				ScreenEvents.afterTick(screen).register(s -> {
					anchorButton((AbstractContainerScreen<?>) s, button);
					button.setMessage(label(client));
				});
			}
		});
	}

	/** Gold while you still have a choice to make; plain once you've picked. */
	private static Component label(final net.minecraft.client.Minecraft client) {
		boolean unpicked = client.player == null || ModAttachments.get(client.player) == null;
		return Component.literal("A").withStyle(unpicked ? ChatFormatting.GOLD : ChatFormatting.WHITE);
	}

	/**
	 * The bookmark spells it out; unstyled it takes the tab's dark label
	 * ink. Gold whenever the tree wants a visit — no archetype picked yet,
	 * or a perk point sitting unspent.
	 */
	private static Component tabLabel(final net.minecraft.client.Minecraft client) {
		boolean beckons = client.player == null
				|| ModAttachments.get(client.player) == null
				|| com.archetypes.SkillPoints.available(client.player) > 0;
		var text = Component.translatable("screen.archetypes.button");
		return beckons ? text.withStyle(ChatFormatting.GOLD) : text;
	}

	private static void anchorTab(final AbstractContainerScreen<?> screen, final BookmarkTab tab) {
		AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) screen;
		int x = accessor.archetypes$getLeftPos() + 4;

		// Slot in after the Skills bookmark, whose width both mods compute
		// with the same label-plus-padding formula.
		if (FabricLoader.getInstance().isModLoaded(SPECIALITIES)) {
			x += BookmarkTab.widthFor(Component.translatable("screen.specialities.skills")) + 2;
		}

		tab.setX(x);
		tab.setY(accessor.archetypes$getTopPos() - BookmarkTab.HEIGHT);
	}

	private static void openArchetypeUi(final net.minecraft.client.Minecraft client, final Screen parent) {
		if (client.player == null) {
			return;
		}

		Archetype current = ModAttachments.get(client.player);
		client.gui.setScreen(current == null
				? new ArchetypePickerScreen(parent)
				: new ArchetypeScreen(parent, current));
	}

	private static void anchorButton(final AbstractContainerScreen<?> screen, final Button button) {
		AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) screen;
		int slot = FabricLoader.getInstance().isModLoaded(SPECIALITIES) ? 1 : 0;
		button.setX(accessor.archetypes$getLeftPos() + accessor.archetypes$getImageWidth() + 4);
		button.setY(accessor.archetypes$getTopPos() + slot * (BUTTON_SIZE + 4));
	}
}
