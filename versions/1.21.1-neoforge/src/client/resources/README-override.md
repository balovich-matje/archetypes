# Why this node overrides the client mixin config

It is `versions/1.21.1-fabric/src/client/resources/archetypes.client.mixins.json` with
**one entry swapped**, and the swap is design R-11 for this loader.

| | 1.21.1-fabric | here |
|---|---|---|
| HUD registration | `GuiMixin` | `NeoForgeGuiMixin` |

Everything else in the list is identical, for the reasons that node's own README gives:
`GhostArmorMixin` and `LevelRendererMixin` join below 1.21.11, and `EntityRendererMixin`,
`LevelExtractorMixin` and `UseDurationMixin` leave because their target APIs do not exist
here.

## The swap

The shared `GuiMixin` has four anchors. Three of them survive NeoForge's `Gui` patch
untouched. The fourth — a `@WrapOperation` on `GuiGraphics.blitSprite` **inside
`renderPlayerHealth`** — does not: NeoForge split that method into per-layer methods, marked
it `@Deprecated // Neo: Split up into different layers`, left it with no `blitSprite` call in
its body and no caller. With `injectors.defaultRequire: 1` that is a mixin-apply failure at
client boot, not a silent no-op.

`versions/1.21.1-neoforge/src/client/java/com/archetypes/client/mixin/NeoForgeGuiMixin.java`
is the shared file's four handlers with that one retargeted to `renderAirLevel`, which is
where the air bubbles moved and which contains exactly the two `blitSprite` calls and no
others. Its header carries the full measurement, including why the
`RegisterGuiLayersEvent` route Skill Proficiencies uses is wrong for *this* mod's HUD.

The shared `GuiMixin` class is still compiled into this jar and is simply not listed. That
is deliberate and is Skill Proficiencies' rule: a present-but-unlisted class is silently
unused, whereas excluding it from the jar turns a future config entry into a hard boot
failure.

**Keep it in step with `src/client/resources/archetypes.client.mixins.json` AND with
`src/client/java/com/archetypes/client/mixin/GuiMixin.java`.** Adding a client mixin means
editing this file too; changing what the HUD draws means editing `NeoForgeGuiMixin` too.
