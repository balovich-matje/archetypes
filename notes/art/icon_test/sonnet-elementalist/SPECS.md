# Elementalist node icons -- specs

One 32x32 icon per family (MINOR skipped), built from vanilla sprites pulled
from the client jar via `make.py`, house style per `notes/art/make_node_icons.py`.

**FIREBALL** -- vanilla fire charge, thrown; a short fading flame trail off
its lower-left. Reads as "the fire orb," the plain projectile it is.

**ICE_BLAST** -- a compact orb built from the vanilla `ice` block's own
crackled pale-blue (masked to a circle, forced opaque, dark rim added), with
an icy trail mirroring Fireball's. Fireball's sibling: same silhouette,
opposite element -- a bolt, not a block, since the tooltip promises a thrown
bolt and the raw `ice` block texture alone reads as wall, not weapon.

**FOCUSED_MIND** -- lapis lazuli (the mana gem) with a small "+" and rising
sparks above it: mana regeneration, ticking up over time.

**KINDLING** -- flint and steel, plus a struck spark right where flint meets
steel (the sprite alone is grey/black and has no fire cue on its own) and
the shared cost-down mark (mana drop + "-" on a dark badge): fire spells
cheaper.

**SCORCH** -- a heart wreathed in flame, reusing this pack's own
heart-plus-damage idiom (Slayer's Taste of Blood, blood instead of fire).
Fire hits hearts harder.

**IGNITION** -- campfire with the gold clock this pack already uses for
"lasts longer" (Braced) riding the corner. Burn duration extended.

**VAPORIZE** -- sponge (opaque, fills the canvas) with a water drop
bottom-left and a rising chain of steam puffs boiling up and away toward the
top-right corner. Fire evaporates the water it crosses.

**CHILL** -- snowball with the shared cost-down mark on a dark badge. Ice
spells cheaper -- Kindling's mirror, same idiom, ice palette.

**FROSTBITE** -- powder snow bucket with vanilla's own Slowness icon riding
the corner: the exact status effect this node piles on harder and longer.

**SHATTER** -- the amethyst shard breaking apart: two smaller, fainter
echoes flying off into the sprite's own empty corners, white crack marks at
the point of failure. Bonus damage to something already cracking.

**PERMAFROST** -- packed ice (opaque, nearly the same pale blue as a plain
water drop, so it swallows one without help) with a water drop crossed by a
white snowflake on a dark badge in the corner. The water this node's ice
crosses glazes into walkable frost.

**METEORITE** -- a fireball on a chain of shrinking, fading puffs (not a
rigid bar -- that read as a torch handle in an earlier pass) streaking
diagonally in from the top-right corner, landing on a cracked magma floor
with an impact glow. Bigger and more violent than Fireball's own little
orb: the whole-mana-pool, sky-called crater capstone.

**FLAMETHROWER** -- the blaze rod as a nozzle, a straight widening cone of
flame sprayed from its tip. A continuous jet, not a single thrown ball --
the capstone that turns Fireball into "hold the key."

**GLACIAL_SPIKE** -- one large hand-built icicle lance thrust diagonally
across the frame (no vanilla icicle sprite exists, so this is flat-filled
polygon work in the ice palette, iron_spikes-style), a burst of white shards
at the tip. One huge piercing hit, not a spray -- 2.5x damage and a freeze.

**BLIZZARD** -- the blaze rod hue-shifted to icy silver-blue, a widening
flurry of snowflakes off its tip instead of a tight jet. Flamethrower's
mirror across the tree: same rod, same hold-the-key idea, a storm instead of
a straight flame.

**SPELLWEAVER** -- enchanted book with the shared cost-down mark on a dark
badge (the book's own warm reds/browns swallow a plain blue drop otherwise)
and a small four-point magic twinkle top-left. Every spell cheaper, not just
one element -- hence neutral silver rather than fire-orange or ice-blue.

**ARCANE_POWER** -- the nether star with eight extra rays radiating past its
own points. Every spell hits harder, full stop -- the star's own starburst
shape amplified rather than replaced.

## Process note
First render surfaced five icons that didn't read cold: Kindling (no fire
cue at all -- fixed with a struck spark), Vaporize and Permafrost (their
accent marks were nearly invisible or looked like scattered noise -- fixed
with clearer shapes and, for Permafrost, a dark badge for contrast against
the pale block), Meteorite (the tail-plus-ball read as a torch/mallet
standing in the crater rather than something falling -- fixed by rebuilding
the tail as a chain of shrinking, fading puffs), and Spellweaver (the cost
mark vanished into the book's warm palette -- fixed with a dark badge, same
as Permafrost). All five were re-rendered and re-checked before finishing.
