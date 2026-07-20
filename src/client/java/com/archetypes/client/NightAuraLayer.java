package com.archetypes.client;

import com.archetypes.Archetypes;
import com.mojang.blaze3d.vertex.PoseStack;

import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * What a vampire looks like to everyone else: violet filaments crawling over
 * the body, scrolling slowly, lit from within.
 *
 * <p>Built on vanilla's own energy-swirl path — the same one that dresses a
 * charged creeper — because that pipeline already solves the two hard parts:
 * an animated texture matrix, and an emissive pass that reads at night, which
 * is when a night-form Cutpurse is out. Note what the pipeline is: ADDITIVE
 * with a 0.1 alpha cutout. Nothing drawn here can DARKEN the skin, so the
 * "dark overlay" is a deep violet drawn as light and kept sparse; the texture
 * is mostly transparent so the skin shows between the veins.
 *
 * <p>The shell is vanilla's outer-armor humanoid rather than the parent player
 * model: a coplanar copy of the skin would z-fight with it, and the armor mesh
 * is exactly the inflated humanoid vanilla itself uses for a surface layer. It
 * is posed here explicitly — {@code LivingEntityRenderer} only runs
 * {@code setupAnim} on the model it owns.
 *
 * <h2>The invisibility rule</h2>
 * A Cutpurse in night form lives on the Shadow tree's Invisibility, and an
 * aura that outlined an invisible player would give away the one thing that
 * build is buying. So {@link #ACTIVE} is written false whenever the player is
 * invisible by ANY route — the mod's Invisibility and vanilla's potion are the
 * same {@code MobEffects.INVISIBILITY} and both land on the entity's invisible
 * flag, which is what extraction reads. This is belt-and-braces rather than
 * decoration: {@code LivingEntityRenderer.shouldRenderLayers} defaults to true,
 * so layers are NOT skipped for an invisible entity — the body vanishes and its
 * layers keep drawing unless something stops them.
 */
public class NightAuraLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	public static final RenderStateDataKey<Boolean> ACTIVE = RenderStateDataKey.create();

	private static final Identifier TEXTURE = Archetypes.id("textures/entity/night_form_aura.png");
	/** Halves the texture's own violet, as vanilla halves the creeper's swirl,
	 * so an additive pass over a lit skin does not blow out to white. */
	private static final int TINT = 0xFF808080;
	/** How fast the sheet crawls across the body, in UV per tick. Slow enough
	 * to read as movement rather than as static noise. */
	private static final float U_PER_TICK = 0.0035F;
	private static final float V_PER_TICK = 0.0016F;

	private final HumanoidModel<AvatarRenderState> shell;

	public NightAuraLayer(final RenderLayerParent<AvatarRenderState, PlayerModel> parent,
			final EntityRendererProvider.Context context) {
		super(parent);
		this.shell = new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_ARMOR.chest()));
	}

	@Override
	public void submit(final PoseStack pose, final SubmitNodeCollector collector, final int light,
			final AvatarRenderState state, final float yRot, final float xRot) {
		if (!Boolean.TRUE.equals(((FabricRenderState) state).getData(ACTIVE))) {
			return;
		}

		float t = state.ageInTicks;
		this.shell.setupAnim(state);
		// A slight sideways wander on top of the drift, so the filaments never
		// settle into a straight scroll.
		float u = (t * U_PER_TICK + Mth.cos(t * 0.03F) * 0.02F) % 1.0F;
		collector.order(1).submitModel(this.shell, state, pose,
				RenderTypes.energySwirl(TEXTURE, u, t * V_PER_TICK % 1.0F),
				light, OverlayTexture.NO_OVERLAY, TINT, null, state.outlineColor, null);
	}
}
