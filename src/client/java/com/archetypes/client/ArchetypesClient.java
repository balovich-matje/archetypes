package com.archetypes.client;

import com.archetypes.Archetype;
import com.archetypes.ModAttachments;
import com.archetypes.client.mixin.AbstractContainerScreenAccessor;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;

public class ArchetypesClient implements ClientModInitializer {
	private static final int BUTTON_SIZE = 20;
	/**
	 * When Specialities is installed its "S" button owns the top slot beside the
	 * inventory, so we sit one slot below it. Alone, we take the top slot.
	 */
	private static final String SPECIALITIES = "specialities";

	@Override
	public void onInitializeClient() {
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
