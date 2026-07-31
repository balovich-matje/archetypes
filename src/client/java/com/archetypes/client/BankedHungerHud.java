package com.archetypes.client;

import com.archetypes.Archetypes;

// STAGE 7: the import goes with its ONE use, and that use does not exist on the two 1.21.1
// nodes — see the long note at the `y` fork below for why. Gated rather than left dangling so
// no node compiles an import it never resolves a reference through.
//? if >=1.21.11 {
import com.archetypes.compat.SpecialitiesBridge;
//?} elif >=1.21 {
//?} else {
/*import com.archetypes.compat.SpecialitiesBridge;
*///?}

//? if >=1.21 {
import net.minecraft.client.DeltaTracker;
//?}
import net.minecraft.client.Minecraft;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}
//? if >=1.21.11 {
import net.minecraft.client.renderer.RenderPipelines;
//?}
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodConstants;

/**
 * What Well Fed's banked hunger looks like.
 *
 * <p>Vanilla's hunger row is ten drumsticks and knows nothing above twenty, so
 * a bar that can hold forty would otherwise read "full" for the whole second
 * half of it and the node would be invisible. The first attempt drew the bank
 * as gilded drumsticks over the same ten sockets, which could not work twice
 * over: the element was anchored after HOTBAR, so it ran BEFORE vanilla's food
 * row and was painted over by it — and even correctly ordered, a second
 * drumstick silhouette in the same nine pixels says nothing the first one did
 * not. The registration now anchors to FOOD_BAR itself.
 *
 * <p>So the bank is not a bar at all now — it is a mark ON vanilla's own
 * drumsticks. A one-pixel halo is drawn hugging the outside of the icons that
 * are currently banked, which is the one place in a socket that vanilla leaves
 * empty, so nothing of ours can be swallowed by anything of theirs.
 *
 * <p>The halo textures are baked from vanilla's {@code hud/food_empty} sprite
 * — the socket, which is the drumstick's FULL silhouette; {@code food_full} is
 * only the lit meat inside it, and a ring grown off that would land on the
 * drumstick's own dark border instead of outside it. The mask is dilated by
 * one pixel (eight-neighbour, so corners close) and the original subtracted,
 * leaving the ring.
 *
 * <p>That ring is BEVELLED rather than flat, and it has to be: a flat silver
 * halo is invisible against snow, sand or a bright sky, which a first pass in
 * game confirmed. Each ring pixel is coloured by the direction it faces away
 * from the drumstick — near-white where it faces up-left, near-black where it
 * faces down-right — so every marked icon carries both a highlight and a
 * shadow and no backdrop can swallow the whole mark. The colours are baked
 * into the texture, so the blit passes no tint.
 *
 * <p>The ring needs one pixel of margin on every side, hence an 11x11 texture drawn
 * at {@code (x - 1, y - 1)}. Sockets are eight pixels apart with nine-pixel
 * sprites, but no ring pixel ever lands on a NEIGHBOURING drumstick's own
 * pixels — the ring only reaches sideways where the neighbour's silhouette is
 * transparent (the meat sits high-left, the bone low-right) — so an outlined
 * icon beside a plain one stays legible.
 *
 * <p>Which icons get the halo: the bank is {@code foodLevel - 20} points, i.e.
 * {@code ceil(banked / 2)} icons, and they are taken from the LEFT end of the
 * row because that is the end vanilla drains first. The handoff is then
 * seamless — the last halo to go is on the leftmost drumstick, and the moment
 * food drops under twenty it is that same drumstick vanilla turns to a half.
 * When the bank is odd the group's inner edge is half an icon, and the half it
 * covers is the LEFT half, matching the half vanilla is about to eat.
 */
public final class BankedHungerHud {
	/** The ring baked off {@code hud/food_empty}: a closed halo around the whole
	 * drumstick, bone included. */
	/*? if >=1.21 {*/private static final Identifier RING = Archetypes.id("hud/banked_food_ring");
	/*?} else *///private static final Identifier RING = Archetypes.id("textures/gui/hud/banked_food_ring.png");
	/** The same halo around the drumstick's left half only, closed by a stroke
	 * down the icon's middle, for an odd bank. */
	/*? if >=1.21 {*/private static final Identifier RING_HALF = Archetypes.id("hud/banked_food_ring_half");
	/*?} else *///private static final Identifier RING_HALF = Archetypes.id("textures/gui/hud/banked_food_ring_half.png");

	private static final int SLOTS = 10;
	private static final int SPRITE = 9;
	private static final int STEP = 8;
	/** The halo textures are the sprite plus one pixel of margin all round. */
	private static final int RING_SPRITE = 11;
	private static final int RING_MARGIN = 1;
	/** Vanilla's hunger row: {@code guiHeight() - 39}, the same line the hearts
	 * are drawn on. */
	private static final int BOTTOM = 39;
	/** Steel, not gold. Gold is already spoken for on this corner of the HUD:
	 * Battle Trance banks raw vanilla Absorption, which draws gold hearts one
	 * row up, and gilding the row below it would read as more of the same
	 * thing. Blue is the Seeker's mana orbs directly above. The exact
	 * highlight and shadow live in the textures — see the class comment. */
	private static final int NO_TINT = 0xFFFFFFFF;

	private BankedHungerHud() {
	}

	// STAGE 5: `DeltaTracker` is 1.21's (frozen row); below it every HUD callback is handed
	// the raw partial tick as a float. None of these six reads it — it is carried because the
	// element signature carries it — so the third arm is the parameter TYPE and nothing else.
	//? if >=26.1 {
	public static void render(final GuiGraphicsExtractor graphics, final DeltaTracker delta) {
	//?} elif >=1.21 {
	/*public static void render(final GuiGraphics graphics, final DeltaTracker delta) {
	*///?} else {
	/*public static void render(final GuiGraphics graphics, final float delta) {
	*///?}
		Minecraft client = Minecraft.getInstance();
		Player player = client.player;

		// The row is not ours when the night form has taken it away, and not
		// there at all when a mount's health has replaced it. Creative and
		// spectator have no hunger row either — vanilla stops drawing the whole
		// thing there, so a halo would be hanging in empty air.
		if (player == null || client.level == null || UndeadHud.active()
				|| player.isCreative() || player.isSpectator()
				|| player.getVehicle() instanceof net.minecraft.world.entity.LivingEntity) {
			return;
		}

		// Guarded on the number, not on the node: a player who respecs out of
		// Well Fed while over twenty keeps what they banked until they burn it,
		// and the bar has to keep saying so.
		int banked = player.getFoodData().getFoodLevel() - FoodConstants.MAX_FOOD;

		if (banked <= 0) {
			return;
		}

		// Half a point still marks its icon, so the count rounds up; rank 2's
		// twenty banked points is exactly the ten sockets, and the clamp is
		// there for anything that hands out food past that ceiling.
		int icons = Math.min(SLOTS, (banked + 1) / 2);
		// i counts right to left, so the leftmost icons are the highest indices
		// and the group's inner edge — the one that can be a half — is the
		// lowest index in it.
		int edge = SLOTS - icons;
		boolean halfEdge = (banked & 1) == 1;

		int right = client.getWindow().getGuiScaledWidth() / 2 + 91;
		// Same live read as ManaHud's, and for the same reason (design R-C4): the
		// hunger row rides the vanilla stack Specialities may or may not be raising.
		//
		// STAGE 7 CORRECTION — THE SHIFT IS A QUESTION ABOUT *WHERE THIS DRAW SITS*, AND THIS
		// DRAW DOES NOT SIT IN THE SAME PLACE ON ALL SEVEN NODES. Reported in game on
		// 1.21.1-fabric: with the ring finally blitting (the previous fix), the halos came out
		// as a row floating ABOVE the drumsticks instead of outlining them. Measured, not
		// guessed:
		//
		//   1.21.1 vanilla `Gui.renderPlayerHealth` (`javap -c`) computes
		//       local 9  = guiWidth()  / 2 + 91      -> the row's RIGHT edge
		//       local 10 = guiHeight() - 39          -> the row's TOP
		//   and calls `renderFood(g, player, iload 10, iload 9)` at offset 426, i.e. the
		//   descriptor is `renderFood(GuiGraphics, Player, int y, int x)` — Y THIRD, X FOURTH,
		//   which is the order both wrap handlers already declare. Inside, the icon x is
		//   `param4 - j * 8 - 9` and the icon y is `param3`. So `right`/`BOTTOM` above are
		//   vanilla's own basis, to the pixel, and the x half of this was never wrong.
		//
		// What IS wrong below 1.21.11 is subtracting Skill Proficiencies' shift a SECOND time.
		// Their raise is a POSE TRANSLATE, and on those two nodes our draw runs INSIDE it:
		//
		//   >=1.21.11  we are a SIBLING. `HudElementRegistry.attachElementAfter(FOOD_BAR, …)`
		//              adds our element next to the one their `replaceElement(FOOD_BAR, …)`
		//              wraps, so their translate is already popped when we draw and we have to
		//              apply the shift ourselves. Unchanged — this is the reference arm and
		//              26.2 has been in daily use.
		//   >=1.21     we are NESTED. Both 1.21.1 nodes reach this through a `@WrapMethod` on
		//              `Gui.renderFood`, which vanilla calls from `renderPlayerHealth` — and
		//              that is exactly the method Skill Proficiencies' own `GuiMixin` wraps
		//              with `pose().translate(0, -hudShift(), 0)` on 1.21.1-fabric. On
		//              1.21.1-neoforge it is the same nesting one level out: their
		//              `RegisterGuiLayersEvent.wrapLayer` translates the whole `FOOD_LEVEL`
		//              layer, whose body is `renderFoodLevel` -> `renderFood`. Either way the
		//              pose is ALREADY shifted when this runs, so subtracting again put the
		//              ring HUD_SHIFT (7 px) above its drumstick — which is what the screenshot
		//              shows.
		//   <1.21      we are a SIBLING again, and by a different route: on 1.20.1 there is no
		//              food method to anchor to at all, so `GuiMixin` (fabric) and
		//              `ForgeGuiMixin` (forge) both draw at the TAIL of the whole HUD frame,
		//              outside every wrapped element. Unchanged.
		//
		// The rule this leaves behind, and it is NOT a version rule: an element that reads
		// another mod's HUD offset must know whether it draws INSIDE or BESIDE the thing that
		// offset moved. Same class of trap as the render-phase one, one level up — nothing
		// about the arithmetic is version-specific, only the composition the port chose per
		// node. `ManaHud` is unaffected and stays as it is: its anchor is the hotbar
		// (`renderItemHotbar` TAIL / the HOTBAR element), which Skill Proficiencies does not
		// raise, so it is a sibling on every node.
		//? if >=1.21.11 {
		int y = client.getWindow().getGuiScaledHeight() - BOTTOM
				- SpecialitiesBridge.hudShift();
		//?} elif >=1.21 {
		/*int y = client.getWindow().getGuiScaledHeight() - BOTTOM;
		*///?} else {
		/*int y = client.getWindow().getGuiScaledHeight() - BOTTOM
				- SpecialitiesBridge.hudShift();
		*///?}

		for (int i = edge; i < SLOTS; i++) {
			int x = right - i * STEP - SPRITE;
			Identifier ring = halfEdge && i == edge ? RING_HALF : RING;

			// STAGE 7 CORRECTION — THE SIX-ARGUMENT `blitSprite` IS NOT THE SAME CALL ON BOTH
			// SIDES OF 1.21.11, and this is the second time the port has been bitten by that
			// exact shape (HudMixin's header records the first). Stage 4 wrote the 1.21.1 arm
			// by dropping only the pipeline argument and claimed in this comment that "the
			// sprite/x/y/w/h/colour tail is declared identically on both, tint included".
			// It is not. Measured with `javap -c` on both mojmap jars:
			//
			//   1.21.11  blitSprite(RenderPipeline, Identifier, I, I, I, I, I)
			//            -> the four-int form appends `iconst_m1`, so the ints are
			//               (x, y, width, height, COLOUR).
			//   1.21.1   blitSprite(Identifier, I, I, I, I, I)
			//            -> the four-int form inserts `iconst_0` in THIRD place and the
			//               private tail is `if (width == 0 || height == 0) return;
			//               innerBlit(atlas, x, x + width, y, y + height, z, …)`, so the ints
			//               are (x, y, Z, width, height) and there is NO colour parameter.
			//
			// So the copied-down call passed `RING_SPRITE` (11) as the z offset and
			// `NO_TINT` (0xFFFFFFFF, i.e. -1) as the HEIGHT. A negative height clears the
			// zero guard and reaches `innerBlit` with y1 = y, y2 = y - 1: an inverted quad one
			// pixel tall with the whole 11x11 ring squeezed into it. Nothing readable is drawn
			// — which is exactly how it was reported, "the halo does not render".
			//
			// The fix is the FOUR-int overload, which is what vanilla's own `renderFood`,
			// `renderArmor` and `renderHeart` call on this node (`javap -c`, and it is the same
			// overload `HudMixin`'s legacy arm wraps). It passes z = 0, the same z the
			// drumsticks under it are drawn at, and the tint is dropped rather than moved:
			// `NO_TINT` is white, and an untinted blit already draws white. The rings are
			// hard-edged (alpha is 0 or 255 and nothing between — measured on both PNGs), and
			// `position_tex.fsh` discards `a == 0.0`, so the blend state vanilla leaves behind
			// at the end of `renderFood` cannot show through the margin either.
			//? if >=1.21.11 {
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ring,
					x - RING_MARGIN, y - RING_MARGIN, RING_SPRITE, RING_SPRITE, NO_TINT);
			//?} elif >=1.21 {
			/*graphics.blitSprite(ring,
					x - RING_MARGIN, y - RING_MARGIN, RING_SPRITE, RING_SPRITE);
			*///?} else {
			/*// R-17: below 1.21 there is no sprite atlas, so the two rings ship as ordinary
			// textures (`processResources` moves them out of `textures/gui/sprites/` on this
			// node) and are blitted whole — an 11x11 file drawn at 11x11, u=v=0. The tint
			// argument goes with the sprite call: `NO_TINT` is white, which is what an
			// untinted blit already draws.
			graphics.blit(ring, x - RING_MARGIN, y - RING_MARGIN, 0, 0,
					RING_SPRITE, RING_SPRITE, RING_SPRITE, RING_SPRITE);
			*///?}
		}
	}
}
