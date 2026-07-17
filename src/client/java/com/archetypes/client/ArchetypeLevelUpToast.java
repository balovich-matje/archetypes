package com.archetypes.client;

import com.archetypes.Archetype;
import com.archetypes.SkillPoints;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Popup shown when the archetype gains a level — the same shape as
 * Specialities' skill toast, so the two mods read as one family:
 *
 * <pre>
 * Brawler
 * Level x -> y
 * </pre>
 *
 * Reaching the level-45 cap plays the epic challenge-complete jingle;
 * ordinary levels ride the quiet toast slide alone.
 */
public class ArchetypeLevelUpToast implements Toast {
	private static final Identifier BACKGROUND_SPRITE = Identifier.withDefaultNamespace("toast/advancement");
	private static final long DISPLAY_TIME_MS = 5000;

	private final Archetype archetype;
	private final int fromLevel;
	private final int newLevel;
	private final ItemStack icon;
	private Toast.Visibility wantedVisibility = Toast.Visibility.HIDE;

	public ArchetypeLevelUpToast(final Archetype archetype, final int fromLevel, final int newLevel) {
		this.archetype = archetype;
		this.fromLevel = fromLevel;
		this.newLevel = newLevel;
		this.icon = new ItemStack(archetype.icon());
	}

	@Override
	public Toast.Visibility getWantedVisibility() {
		return this.wantedVisibility;
	}

	@Override
	public void update(final ToastManager manager, final long fullyVisibleForMs) {
		this.wantedVisibility = fullyVisibleForMs >= DISPLAY_TIME_MS * manager.getNotificationDisplayTimeMultiplier()
				? Toast.Visibility.HIDE
				: Toast.Visibility.SHOW;
	}

	@Override
	public @Nullable SoundEvent getSoundEvent() {
		return this.newLevel >= SkillPoints.MAX_LEVEL && this.fromLevel < SkillPoints.MAX_LEVEL
				? SoundEvents.UI_TOAST_CHALLENGE_COMPLETE
				: null;
	}

	@Override
	public void extractRenderState(final GuiGraphicsExtractor graphics, final Font font, final long fullyVisibleForMs) {
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BACKGROUND_SPRITE, 0, 0, this.width(), this.height());
		graphics.text(font, this.archetype.tierName(0), 30, 7, this.archetype.color(), false);
		graphics.text(font, Component.translatable("toast.archetypes.levelup.desc", this.fromLevel, this.newLevel),
				30, 18, 0xFFFFFFFF, false);
		graphics.fakeItem(this.icon, 8, 8);
	}
}
