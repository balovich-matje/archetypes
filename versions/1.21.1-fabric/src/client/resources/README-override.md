# Why this node overrides the client mixin config

Entries have to leave the list here AND entries have to join it, and the shared
line-blanking transform in `build.fabric.gradle.kts` can do neither of those jobs
completely.

## Entries that only exist here (Stage 4-D)

* `GhostArmorMixin` — below 1.21.11 there is no render state whose `*Equipment` fields
  Ghost Armor can blank, so the three vanilla layers that draw gear are cancelled instead.
* `LevelRendererMixin` — `EntityRendererMixin`'s replacement, not its companion. Above
  1.21.11 one field is both the outline's ticket and its colour; below, those are two
  calls in `LevelRenderer.renderLevel` and both get wrapped there.
* `GuiMixin` — the whole HUD registration path. fabric-rendering-v1 has no `hud` package
  here, so `ArchetypesClient`'s eight `HudElementRegistry` calls have nowhere to go; this
  anchors each of them on a vanilla `Gui` method instead. Distinct from `HudMixin`, which
  also targets `Gui` on this node but only for the night form's grey hearts.

All three classes sit inside a `//? if <1.21.11` block, so they are in no other node's jar; a
mixin named in a config whose class is absent is a hard boot failure, which is exactly why
they must NOT go in the shared config.

## Entries that have to leave

Three of them now, and the transform cannot do any two of them:

* `EntityRendererMixin` — `EntityRenderer.extractRenderState` and the
  `EntityRenderState.outlineColor` it writes are both `>=1.21.11`. The whole compilation
  unit is inside a `//?` block, so the class is not in this jar and the entry has to go.
  Both halves are load-bearing: a config naming an absent class is a hard boot failure, and
  a class present but unlisted is a silent no-op.

* `LevelExtractorMixin` — `client.renderer.extract` is 26.2-only; the transform already
  blanks this one everywhere below 26.2, and it is repeated here only because a per-node
  override REPLACES the shared file rather than layering on it.
* `UseDurationMixin` — `client.renderer.item.properties.numeric.UseDuration` does not exist
  on 1.21.1 (see that file's header). It is the LAST element of the shared array, and
  blanking a last element leaves a trailing comma, which is not something to hand to a
  mixin config loader on purpose.

A mixin listed in the config whose class is not in the jar is a HARD BOOT FAILURE, not a
warning, so this file is load-bearing rather than tidy-up.

**Keep it in step with `src/client/resources/archetypes.client.mixins.json`.** Adding a
client mixin means editing both. This is the cost the Stage-2 note predicted for an
override, accepted here because this node is the first that has to both drop an entry the
transform cannot reach and (once the render-state rewrite lands) add one of its own.
