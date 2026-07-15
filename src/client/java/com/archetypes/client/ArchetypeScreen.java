package com.archetypes.client;

import com.archetypes.Archetype;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Placeholder for the archetype page. The real thing is a WoW-style active/passive
 * skill tree over a full-bleed background with the archetype's figure in it.
 */
public class ArchetypeScreen extends Screen {
	private final @Nullable Screen parent;
	private final Archetype archetype;

	public ArchetypeScreen(final @Nullable Screen parent, final Archetype archetype) {
		super(archetype.tierName(0));
		this.parent = parent;
		this.archetype = archetype;
	}

	@Override
	protected void init() {
		this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose())
				.bounds(this.width / 2 - 60, this.height - 32, 120, 20)
				.build());
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(this.parent);
	}

	@Override
	public void extractRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);

		int centerX = this.width / 2;

		graphics.fakeItem(new ItemStack(this.archetype.icon()), centerX - 8, this.height / 2 - 48);

		Component name = this.archetype.tierName(0);
		graphics.text(this.font, name, centerX - this.font.width(name) / 2, this.height / 2 - 24,
				this.archetype.color(), true);

		Component blurb = this.archetype.blurb();
		graphics.text(this.font, blurb, centerX - this.font.width(blurb) / 2, this.height / 2 - 8, 0xFFDDDDDD, false);

		Component soon = Component.translatable("screen.archetypes.placeholder",
				this.archetype.tierName(Archetype.TIERS - 1));
		graphics.text(this.font, soon, centerX - this.font.width(soon) / 2, this.height / 2 + 12, 0xFF888888, false);
	}
}
