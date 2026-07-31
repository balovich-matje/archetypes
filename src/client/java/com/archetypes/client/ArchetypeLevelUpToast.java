package com.archetypes.client;

import com.archetypes.Archetype;
import com.archetypes.SkillPoints;

import net.minecraft.client.gui.Font;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}
import net.minecraft.client.gui.components.toasts.Toast;
// STAGE 4 — the whole-interface rewrite the header below predicted. From 1.21.11 up `Toast`
// is four members (getWantedVisibility / update(ToastManager, long) / getSoundEvent / a void
// draw); on 1.21.1 it is ONE — `Toast.Visibility render(GuiGraphics, ToastComponent, long)`,
// which both DRAWS and RETURNS the next visibility. `ToastManager` is called `ToastComponent`
// there, and there is no per-toast sound hook at all, so the level-45 jingle has to be played
// by the toast itself, once, on its first frame. This is Skill Proficiencies' fork verbatim
// (design §4.3) — the two mods' toasts are meant to stay the same shape.
//? if >=1.21.11 {
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.renderer.RenderPipelines;
//?} else {
/*import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
*///?}
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
//? if >=1.21.11 {
import org.jspecify.annotations.Nullable;
//?} else {
/*import org.jetbrains.annotations.Nullable;
*///?}

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
	//? if >=1.21.11 {
	private Toast.Visibility wantedVisibility = Toast.Visibility.HIDE;
	//?} else {
	/*private boolean soundPlayed;
	*///?}

	public ArchetypeLevelUpToast(final Archetype archetype, final int fromLevel, final int newLevel) {
		this.archetype = archetype;
		this.fromLevel = fromLevel;
		this.newLevel = newLevel;
		this.icon = new ItemStack(archetype.icon());
	}

	//? if >=1.21.11 {
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
	//?} else {
	/*// The single 1.21.1 hook: draw, decide the next visibility, and — since the version has
	// no getSoundEvent equivalent — play the cap jingle on the first frame. The timeout
	// arithmetic and the milestone condition are the SAME expressions as the arm above, so
	// behaviour does not fork here, only the plumbing does (conventions §5b).
	@Override
	public Toast.Visibility render(final GuiGraphics graphics, final ToastComponent manager,
			final long fullyVisibleForMs) {
		if (!this.soundPlayed) {
			this.soundPlayed = true;

			if (this.newLevel >= SkillPoints.MAX_LEVEL && this.fromLevel < SkillPoints.MAX_LEVEL) {
				Minecraft.getInstance().getSoundManager()
						.play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0F));
			}
		}

		this.draw(graphics, Minecraft.getInstance().font);

		return fullyVisibleForMs >= DISPLAY_TIME_MS * manager.getNotificationDisplayTimeMultiplier()
				? Toast.Visibility.HIDE
				: Toast.Visibility.SHOW;
	}
	*///?}

	// The `Toast` interface itself is otherwise UNCHANGED at this boundary — `getWantedVisibility`,
	// `update(ToastManager, long)` and the default `getSoundEvent()` are all declared the same on
	// 1.21.11 (`javap` on the interface). Only the draw method moves, and it moves the way every
	// other GUI surface does: extract-then-draw becomes draw-now. The whole-interface rewrite the
	// design warns about (one `Visibility render(GuiGraphics, ToastComponent, long)` that draws AND
	// returns visibility, with `ToastComponent` for `ToastManager` and no `getSoundEvent`) is the
	// PRE-1.21.11 shape and lands at Stage 4 — which is the arm now at the bottom of this
	// chain: there the interface has no draw hook of its own, so this becomes a plain private
	// method called from the render(...) above, and the @Override moves inside the chain with
	// it. Nothing in the BODY forks for that boundary; only the header does.
	//? if >=26.1 {
	@Override
	public void extractRenderState(final GuiGraphicsExtractor graphics, final Font font, final long fullyVisibleForMs) {
	//?} elif >=1.21.11 {
	/*@Override
	public void render(final GuiGraphics graphics, final Font font, final long fullyVisibleForMs) {
	*///?} else {
	/*private void draw(final GuiGraphics graphics, final Font font) {
	*///?}
		//? if >=1.21.11 {
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BACKGROUND_SPRITE, 0, 0, this.width(), this.height());
		//?} else {
		/*graphics.blitSprite(BACKGROUND_SPRITE, 0, 0, this.width(), this.height());
		*///?}
		//? if >=26.1 {
		graphics.text(font, this.archetype.tierName(0), 30, 7, this.archetype.color(), false);
		graphics.text(font, Component.translatable("toast.archetypes.levelup.desc", this.fromLevel, this.newLevel),
		//?} else {
		/*graphics.drawString(font, this.archetype.tierName(0), 30, 7, this.archetype.color(), false);
		graphics.drawString(font, Component.translatable("toast.archetypes.levelup.desc", this.fromLevel, this.newLevel),
		*///?}
				30, 18, 0xFFFFFFFF, false);
		//? if >=26.1 {
		graphics.fakeItem(this.icon, 8, 8);
		//?} else {
		/*graphics.renderFakeItem(this.icon, 8, 8);
		*///?}
	}
}
