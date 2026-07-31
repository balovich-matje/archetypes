package com.archetypes.client;

// STAGE 4 — THE WHOLE CLASS IS ABOVE-1.21.11 ONLY, and that is the port's central finding
// rather than an excision. Below 1.21.11 a RenderLayer is handed the ENTITY, not an extracted
// state (`render(PoseStack, MultiBufferSource, int, T, float x6)`), so there is no handoff to
// carry: BulwarkShieldLayer reads the attachment and the held shield off the entity in its own
// render(). The indirection COLLAPSES; it is not reimplemented. Design §4.3 predicted exactly
// this, and the same collapse governs BladestormLayer's two keys and NightEyesLayer's GLOW.
//
// A compilation unit with a package declaration and no type declaration is legal and emits no
// `.class` — the same mechanism Skill Proficiencies' client/mixin/GuiMixin.java uses. Nothing
// lists this class in a mixin config, so no config has to fork for it.
//? if >=1.21.11 {
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.renderer.item.ItemStackRenderState;

/**
 * Extraction-to-render handoff for the Bulwark aura: 26.2 renderers never see
 * the entity, only its extracted state, so the active flag and the resolved
 * shield model ride along as Fabric render-state data.
 */
public final class BulwarkRenderData {
	public static final RenderStateDataKey<Boolean> ACTIVE = RenderStateDataKey.create();
	public static final RenderStateDataKey<ItemStackRenderState> GHOST = RenderStateDataKey.create();

	private BulwarkRenderData() {
	}
}
//?}
