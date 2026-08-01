package com.archetypes.client;

import java.util.HashSet;
import java.util.Set;

import com.archetypes.Archetypes;
import com.archetypes.DeathMark;
import com.archetypes.NightForm;
import com.mojang.blaze3d.vertex.PoseStack;

// STAGE 4 — the render-state collapse (design §4.3), third of the three avatar layers. The
// three-state decision was ALREADY written against the entity (`glowFor(Entity)`), so below
// 1.21.11 the layer simply calls it itself instead of reading the answer the extraction hook
// stashed. Nothing about the state machine moves; only who asks it, and when.
//
// The draw forks with the pipeline, and both shapes were read off the mapped jars:
//   >=1.21.11  SubmitNodeCollector.order(int).submitModelPart(ModelPart, PoseStack, RenderType,
//                  int, int, ..., int tint, ...)   — package `client.renderer.rendertype`
//   1.21.1     ModelPart.render(PoseStack, VertexConsumer, int light, int overlay, int color)
//                  with the buffer taken from `MultiBufferSource.getBuffer(..)` — `RenderType`
//                  sits at `net.minecraft.client.renderer` there, not on a `RenderTypes` factory.
// The tint is the same ARGB int on both, so the two alpha dials below are untouched.
//
// THE ONE MEASURED DIFFERENCE — and Stage 7 found it in-game, because the first port wrote it
// down WRONG. Below 1.21.11 `RenderType.eyes` blends through `ADDITIVE_TRANSPARENCY`, and that
// shard is `blendFunc(ONE, ONE)`: source alpha is not a factor in the blend, so alpha does NOT
// scale the glow there and the texture's alpha-0 white ground is added at full strength. That
// is a solid band across the whole quad, not two eyes. The legacy arms therefore bind
// `NightEyeRenderType.translucentEyes`, which is vanilla's own eyes composite with exactly the
// transparency shard replaced; read that file's header for the measurement. Everything else —
// colour-write-only, depth-tested, back-face culled, no lightmap — is vanilla's and holds on
// every node, so the occlusion and back-face rules the doc below describes still apply.
//? if >=1.21.11 {
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
//?}
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
//? if >=1.21.11 {
import net.minecraft.client.model.player.PlayerModel;
//?} else {
/*import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
*///?}
import net.minecraft.client.multiplayer.ClientLevel;
//? if >=1.21.11 {
import net.minecraft.client.renderer.SubmitNodeCollector;
//?} else {
/*import net.minecraft.client.renderer.MultiBufferSource;
*///?}
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
//? if >=1.21.11 {
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
//?}
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * What a vampire looks like to everyone else: a red glow where the eyes are.
 *
 * <h2>The three states</h2>
 * All three are decided in one place, {@link #glowFor}, off state that is
 * already synced to every client — there is no eye-glow packet:
 * <ol>
 *   <li><b>{@link Glow#FAINT}</b> — night form, no mark out, not invisible. A
 *       low tint alpha; present, but not a beacon.</li>
 *   <li><b>{@link Glow#NONE}</b> — night form and INVISIBLE with no mark out.
 *       Nothing is drawn at all. Invisibility is the Cutpurse's whole defence
 *       and the form must never cost it.</li>
 *   <li><b>{@link Glow#BRIGHT}</b> — a Death Mark is out on someone. Much
 *       brighter, and drawn <em>through</em> invisibility: a Nemesis who has
 *       named a victim gives their own position away. This is the only case
 *       that beats the invisibility rule, and it is the deal the author
 *       struck.</li>
 * </ol>
 *
 * <h2>Reading "has a mark out" on a client that is not the owner's</h2>
 * The mark's owner-side stamps ({@code MARK_TARGET} / {@code MARK_END}) are
 * {@code targetOnly()}, so {@link DeathMark#hasMark} answers only on the
 * assassin's own client. The half that reaches everyone is {@code MARKED_BY},
 * written onto the marked BODY and synced to all — so an onlooker's client
 * answers the question backwards, by asking the bodies it can see who is
 * hunting them ({@link #markersIn}). That scan is over
 * {@code ClientLevel.entitiesForRendering()} and is cached per game tick, so it
 * runs once a tick however many players are being extracted. No new packet is
 * needed; see the report note about the one thing this cannot see, a mark on a
 * body outside the onlooker's entity-tracking range.
 *
 * <h2>The render type</h2>
 * {@link RenderTypes#eyes} — vanilla's own spider/enderman eye pass. Its
 * pipeline is built with the {@code EMISSIVE} and {@code NO_CARDINAL_LIGHTING}
 * shader defines and takes NO lightmap, so the glow is full-bright regardless
 * of the block light it stands in: it reads at the bottom of a cave, which is
 * where a vampire lives. It blends TRANSLUCENT (so the tint's alpha is the
 * "slightly visible" vs "much brighter" dial), depth-TESTS but does not
 * depth-WRITE (so terrain still occludes it and nothing later z-fights against
 * it), and culls back faces (so the quad is invisible from behind the head —
 * which matters, because an invisible player writes no depth at all and an
 * unculled quad would show through the back of the skull).
 *
 * <p>This is emphatically NOT a light source: nothing here is placed in the
 * level and no block light is written. The face glows; the floor in front of
 * it does not.
 *
 * <h2>Where the eyes are</h2>
 * See {@link #EYE_PLANE_Z} and the mesh in {@link #bakeEyes}. Fixed to the
 * vanilla model, and the author has accepted that a custom skin that puts eyes
 * elsewhere will wear this glow where Steve's eyes are.
 */
//? if >=1.21.11 {
public class NightEyesLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
//?} else {
/*public class NightEyesLayer
		extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
*///?}
	/** Which of the three states a player is in this frame. Written by
	 * {@code AvatarRendererMixin} at extraction, read here at submit. */
	public enum Glow {
		NONE,
		FAINT,
		BRIGHT
	}

	//? if >=1.21.11 {
	public static final RenderStateDataKey<Glow> GLOW = RenderStateDataKey.create();
	//?}

	private static final Identifier TEXTURE = Archetypes.id("textures/entity/night_form_eyes.png");

	/**
	 * The glow quad, in head-local model pixels.
	 *
	 * <p>Derived from the vanilla skin rather than guessed. The head is
	 * {@code addBox(-4, -8, -4, 8, 8, 8)} at {@code texOffs(0, 0)}, and a cube's
	 * NORTH face — the one at minimum z, which is the face — unwraps to texture
	 * {@code (u+dz, v+dz)} .. {@code (u+dz+dx, v+dz+dy)} = (8,8)..(16,16). So
	 * face texel (tx, ty) sits at model {@code x = tx - 12}, {@code y = ty - 16}.
	 * Reading {@code steve.png} out of the 26.2 client jar, the eye pixels are
	 * row {@code ty = 12} at {@code tx = 9,10} and {@code tx = 13,14} — i.e.
	 * model x [-3,-1] and [1,3], model y [-4,-3]. Eye centres are therefore
	 * model (-2, -3.5) and (+2, -3.5).
	 *
	 * <p>The quad drawn is deliberately larger than the two 2x1 eyes — the whole
	 * face width by four rows — because the texture is a soft falloff and the
	 * halo needs somewhere to fall off INTO. The bright core still lands on the
	 * eye pixels.
	 */
	private static final float QUAD_MIN_X = -4.0F;
	private static final float QUAD_MIN_Y = -5.5F;
	private static final float QUAD_WIDTH = 8.0F;
	private static final float QUAD_HEIGHT = 4.0F;

	/**
	 * How far in front of the head the quad sits. The skin's face is at
	 * z = -4 and the second skin layer ("hat") is an inflated copy of the same
	 * cube at z = -4.5, so anything at -4 would be hidden by any skin whose hat
	 * layer paints an opaque face. -4.6 clears the hat by a tenth of a model
	 * pixel — 1/160th of a block, far too little to read as floating, far more
	 * than the depth buffer needs to tell the two apart.
	 *
	 * <p>It does NOT clear a helmet, which is a further half pixel out. A
	 * helmeted vampire's eyes are hidden, which is the honest answer.
	 */
	private static final float EYE_PLANE_Z = -4.6F;

	/** ARGB tints. The RGB is the colour; the ALPHA is the dial, because
	 * {@code eyes()} blends translucently — 0x4A is "you would notice it in the
	 * dark", 0xE6 is "you cannot miss it in daylight". The texture itself is
	 * white at the core, so all of the hue comes from here and retuning the
	 * colour never means repainting the sheet. */
	private static final int FAINT_TINT = 0x4AFF2A18;
	private static final int BRIGHT_TINT = 0xE6FF3A20;

	private final ModelPart eyes;

	//? if >=1.21.11 {
	public NightEyesLayer(final RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
	//?} else {
	/*public NightEyesLayer(
			final RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
	*///?}
		super(parent);
		this.eyes = bakeEyes();
	}

	/**
	 * The whole state machine, in one call, so the three cases can be read
	 * against the author's spec side by side. Safe for any entity; anything
	 * that is not a transformed player is {@link Glow#NONE}.
	 *
	 * <p>{@code isInvisible()} covers the Shadow tree's Invisibility and
	 * vanilla's potion alike — both are {@code MobEffects.INVISIBILITY} and both
	 * land on the same entity flag.
	 */
	public static Glow glowFor(final Entity entity) {
		if (!(entity instanceof Player player) || !NightForm.isActive(player)) {
			return Glow.NONE;
		}

		if (hasMarkOut(player)) {
			return Glow.BRIGHT;
		}

		return entity.isInvisible() ? Glow.NONE : Glow.FAINT;
	}

	//? if >=1.21.11 {
	@Override
	public void submit(final PoseStack pose, final SubmitNodeCollector collector, final int light,
			final AvatarRenderState state, final float yRot, final float xRot) {
		Glow glow = ((FabricRenderState) state).getData(GLOW);

		if (glow == null || glow == Glow.NONE) {
			return;
		}

		// The parent model was posed by LivingEntityRenderer immediately before
		// the layer pass, so its head carries this frame's look angles (and the
		// crouch offset). Riding its transform is what makes the glow stay on
		// the eyes when the head turns instead of on the body's facing.
		pose.pushPose();
		this.getParentModel().head.translateAndRotate(pose);
		collector.order(1).submitModelPart(this.eyes, pose, RenderTypes.eyes(TEXTURE), light,
				OverlayTexture.NO_OVERLAY, null,
				glow == Glow.BRIGHT ? BRIGHT_TINT : FAINT_TINT, null);
		pose.popPose();
	}
	//?} else {
	/*@Override
	public void render(final PoseStack pose, final MultiBufferSource buffers, final int light,
			final AbstractClientPlayer entity, final float limbSwing, final float limbSwingAmount,
			final float partialTicks, final float ageInTicks, final float netHeadYaw,
			final float headPitch) {
		// The extraction hook's one line, made here instead. glowFor already
		// took an Entity, so the state machine is untouched by the collapse.
		Glow glow = glowFor(entity);

		if (glow == Glow.NONE) {
			return;
		}

		// The parent model was posed by LivingEntityRenderer immediately before
		// the layer pass, so its head carries this frame's look angles (and the
		// crouch offset). Riding its transform is what makes the glow stay on
		// the eyes when the head turns instead of on the body's facing.
		pose.pushPose();
		this.getParentModel().head.translateAndRotate(pose);
		//? if >=1.21 {
		this.eyes.render(pose, buffers.getBuffer(NightEyeRenderType.translucentEyes(TEXTURE)),
				light, OverlayTexture.NO_OVERLAY,
				glow == Glow.BRIGHT ? BRIGHT_TINT : FAINT_TINT);
		//?} else {
		/^// `ModelPart.render` takes the tint as four floats below 1.21 rather than one packed
		// ARGB int. Same four channels, same order, unpacked from the same constant.
		int tint = glow == Glow.BRIGHT ? BRIGHT_TINT : FAINT_TINT;
		this.eyes.render(pose, buffers.getBuffer(NightEyeRenderType.translucentEyes(TEXTURE)),
				light, OverlayTexture.NO_OVERLAY,
				(tint >> 16 & 0xFF) / 255.0F, (tint >> 8 & 0xFF) / 255.0F,
				(tint & 0xFF) / 255.0F, (tint >>> 24) / 255.0F);
		^///?}
		pose.popPose();
	}
	*///?}

	/**
	 * One quad, NORTH face only. Zero depth, so the box degenerates to that one
	 * face and its UVs start exactly at the texOffs; the declared sheet size is
	 * the quad's own 8x4, so the whole texture maps onto it at u,v 0..1 and the
	 * PNG can be any resolution it likes (it is 64x32, eight texels per model
	 * pixel).
	 *
	 * <p>Baked here rather than through {@code EntityModelLayerRegistry}: this
	 * mesh depends on nothing the resource pack can change, so it needs no
	 * registration line in {@code ArchetypesClient} and cannot be reloaded out
	 * from under us.
	 */
	private static ModelPart bakeEyes() {
		MeshDefinition mesh = new MeshDefinition();
		mesh.getRoot().addOrReplaceChild("eyes",
				CubeListBuilder.create().texOffs(0, 0).addBox(QUAD_MIN_X, QUAD_MIN_Y, EYE_PLANE_Z,
						QUAD_WIDTH, QUAD_HEIGHT, 0.0F, Set.of(Direction.NORTH)),
				PartPose.ZERO);
		return LayerDefinition.create(mesh, (int) QUAD_WIDTH, (int) QUAD_HEIGHT)
				.bakeRoot().getChild("eyes");
	}

	// ------------------------------------------------------------------
	// "Does this player have a mark out?", answered on any client.
	// ------------------------------------------------------------------

	private static Level markersLevel;
	private static long markersTick = Long.MIN_VALUE;
	private static Set<Integer> markers = Set.of();

	private static boolean hasMarkOut(final Player player) {
		// The owner's own client has the authoritative pair and does not need
		// the scan — and gets the right answer even for a marked body it has
		// walked out of tracking range of.
		return DeathMark.hasMark(player) || markersIn(player.level()).contains(player.getId());
	}

	/**
	 * The ids of every player who owns a mark on a body this client can see,
	 * rebuilt at most once a game tick. Extraction runs per player per frame,
	 * so the cache is what keeps this from being a world scan per avatar.
	 */
	private static Set<Integer> markersIn(final Level level) {
		long now = level.getGameTime();

		if (level == markersLevel && now == markersTick) {
			return markers;
		}

		markersLevel = level;
		markersTick = now;
		Set<Integer> found = null;

		if (level instanceof ClientLevel client) {
			for (Entity entity : client.entitiesForRendering()) {
				Integer by = DeathMark.markedBy(entity);

				if (by != null) {
					if (found == null) {
						found = new HashSet<>();
					}

					found.add(by);
				}
			}
		}

		markers = found == null ? Set.of() : found;
		return markers;
	}
}
