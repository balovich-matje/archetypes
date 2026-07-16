package com.archetypes.client;

import com.archetypes.Archetype;
import com.archetypes.ModAttachments;
import com.archetypes.ShieldBashPayload;
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

	/** The ability binds, exposed so the cooldown bar can label its slots. */
	static KeyMapping BASH_KEY;
	static KeyMapping SLAYER_KEY;

	@Override
	public void onInitializeClient() {
		SlayerAnimations.initialize();

		net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry.getInstance()
				.register(com.archetypes.ModParticles.GREATSWORD_SWEEP, GreatswordSweepParticle.Provider::new);

		// One rebindable key per active, both under Gameplay; the cooldown bar
		// shows each slot's current bind. The keys only report the press —
		// the server decides whether anything actually happens.
		BASH_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.archetypes.shield_bash", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G,
				KeyMapping.Category.GAMEPLAY));
		SLAYER_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.archetypes.slayer_ability", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H,
				KeyMapping.Category.GAMEPLAY));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (BASH_KEY.consumeClick()) {
				if (client.player != null) {
					ClientPlayNetworking.send(new ShieldBashPayload());
				}
			}

			while (SLAYER_KEY.consumeClick()) {
				if (client.player != null) {
					ClientPlayNetworking.send(new com.archetypes.SlayerAbilityPayload());
				}
			}

			// Shield Rush: sprint pressed while the shield is raised. Only
			// consumed while blocking, so normal sprinting is untouched.
			if (client.player != null && client.player.isBlocking()) {
				while (client.options.keySprint.consumeClick()) {
					ClientPlayNetworking.send(new com.archetypes.RushPayload());
				}
			}
		});

		// The centred bar of owned-active cooldowns, and the proc flashes that
		// fall from the crosshair. Both after HOTBAR so they draw on top.
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR,
				com.archetypes.Archetypes.id("cooldown_bar"), CooldownBarHud::render);
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR,
				com.archetypes.Archetypes.id("proc_indicators"), ProcIndicatorHud::render);

		ClientPlayNetworking.registerGlobalReceiver(com.archetypes.PassiveProcPayload.TYPE,
				(payload, context) -> context.client().execute(() -> ProcIndicatorHud.push(payload)));

		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (!(screen instanceof InventoryScreen) && !(screen instanceof CreativeModeInventoryScreen)) {
				return;
			}

			Button button = Button.builder(label(client), b -> openArchetypeUi(client, screen))
					.bounds(0, 0, BUTTON_SIZE, BUTTON_SIZE)
					.tooltip(Tooltip.create(Component.translatable("screen.archetypes.button")))
					.build();
			anchorButton((AbstractContainerScreen<?>) screen, button);
			Screens.getWidgets(screen).add(button);

			// The recipe book shifts leftPos without re-running init, so keep the
			// button glued to the panel every tick — and refresh the label, since
			// the archetype can be picked while this screen is still alive.
			ScreenEvents.afterTick(screen).register(s -> {
				anchorButton((AbstractContainerScreen<?>) s, button);
				button.setMessage(label(client));
			});
		});
	}

	/** Gold while you still have a choice to make; plain once you've picked. */
	private static Component label(final net.minecraft.client.Minecraft client) {
		boolean unpicked = client.player == null || ModAttachments.get(client.player) == null;
		return Component.literal("A").withStyle(unpicked ? ChatFormatting.GOLD : ChatFormatting.WHITE);
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
