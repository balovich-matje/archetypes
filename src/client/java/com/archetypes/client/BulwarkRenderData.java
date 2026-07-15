package com.archetypes.client;

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
