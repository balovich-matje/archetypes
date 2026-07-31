# Why this node overrides the client mixin config

The same two reasons the 1.21.1 override lists, and the list is currently IDENTICAL to that
node's — which is a fact about this stage, not a rule. Keep the two in step by reading both,
never by copying one over the other: the moment either node gains a client mixin the other
does not have, they diverge for good.

## Entries that only exist here

* `GhostArmorMixin` — no render state to blank below 1.21.11, so the three vanilla gear
  layers are cancelled instead.
* `LevelRendererMixin` — ESP's outline, rebuilt from the two calls that carry it below
  1.21.11 (membership from `Minecraft.shouldEntityAppearGlowing`, colour from
  `Entity.getTeamColor`). Its `renderLevel` descriptor forks AGAIN on this node: 1.20.1
  spells it `(PoseStack, F, J, Z, Camera, GameRenderer, LightTexture, Matrix4f)`.
* `GuiMixin` — the HUD registration path. fabric-rendering-v1 has no `hud` package here, and
  on THIS node the `Gui` it anchors on is shaped differently again: no `renderItemHotbar`,
  no `renderCameraOverlays`, no `renderFood`, so all six elements land at the TAIL of
  `render(GuiGraphics, float)`. That file's header records what the two anchors that could
  not be reproduced cost.

All three classes sit inside a `//?` block, so they are in no other node's jar; a mixin named
in a config whose class is absent is a hard boot failure, which is why they must NOT go in the
shared config.

## Entries that have to leave

* `EntityRendererMixin` — `EntityRenderer.extractRenderState` and the
  `EntityRenderState.outlineColor` it writes are both `>=1.21.11`; the class is not in this
  jar. `LevelRendererMixin` above is its replacement, not its companion.
* `LevelExtractorMixin` — `client.renderer.extract` is 26.2-only. The shared transform
  already blanks this line everywhere below 26.2; it is repeated here only because a
  per-node override REPLACES the shared file rather than layering on it.
* `UseDurationMixin` — `client.renderer.item.properties.numeric.UseDuration` does not exist
  below 1.21.11, and it is the LAST element of the shared array, where blanking a line would
  leave a trailing comma.

**Keep it in step with `src/client/resources/archetypes.client.mixins.json`.** Adding a client
mixin means editing this file too.
