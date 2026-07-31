# Why this node overrides the client mixin config

Entries have to leave the list here AND entries have to join it, and the shared
line-blanking transform in `build.fabric.gradle.kts` can do neither of those jobs
completely.

## Entries that only exist here (Stage 4-D)

* `GhostArmorMixin` — below 1.21.11 there is no render state whose `*Equipment` fields
  Ghost Armor can blank, so the three vanilla layers that draw gear are cancelled instead.
  The class is inside a `//? if <1.21.11` block, so it is not in any other node's jar; a
  mixin named in a config whose class is absent is a hard boot failure, which is exactly
  why it must NOT go in the shared config.

## Entries that have to leave

Two of them, and the transform cannot do both:

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
