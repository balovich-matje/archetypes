# Marksman node icons — opus-marksman

Every icon starts FROM a real vanilla sprite (pulled from the client jar, so
the palette matches the game) and adds a small readable mechanic element on
top. Composed at 16px native resolution, upscaled 2x NEAREST to 32px — the
house norm, so every effect pixel is the same chunky size as vanilla's art.

Shared grammar: white speed-dashes/echo = faster, green = kept/refunded,
redstone-red = charge, gold = the empowered True Shot, a clock = cooldown,
a curving trail = homing, a punched hole = armour ignored.

| icon | composition | why it reads |
|---|---|---|
| **true_shot** | vanilla `spectral_arrow` on a dead-straight gold flight-line running past both tips, a power-spark bursting off the head | the glowing arrow is literally this node's item; the ruler-straight line = "unbent by gravity", the spark = the x2 hit |
| **conservation** | real `arrow` with a green-tinted ghost copy of itself held behind it and a green gleam | two arrows where one was fired = the spare you kept; green = saved/free, not spent |
| **pinning** | real `cobweb` block with an `arrow` driven through its centre into a dark seat | an arrow stuck fast in a web is the game's own picture of a slowed, pinned target |
| **swift_flight** | crisp diagonal `arrow` with two fading motion-echoes streaking off the fletch + bright speed ticks | same arrow, blurred by speed — the round-one "faster" cue, done as motion-blur so the sprite never looks rotated/mangled |
| **acrobatics** | `rabbit_foot` (agility) mid-tumble under a dashed roll-arc that sweeps forward into an arrowhead | rabbit's foot = nimbleness; the forward arc + head = the sprint-roll dodge |
| **nimble_draw** | vanilla `bow_pulling_2` (a fully-drawn, nocked bow) with speed-dashes streaming underneath | the bow is held at full draw AND moving fast — walk speed kept while the string is back |
| **rapid_reload** | `crossbow_arrow` (loaded crossbow) with speed-dashes behind it and a redstone-red charge-spark at the string | loaded + fast + redstone energy = the kill-charged, near-instant re-cock |
| **combustion** | `fire_charge` with an `arrow` buried in it, detonation rays blowing outward | arrow meets fire and the whole space around it goes up — the AoE detonation |
| **focus** | `spyglass` (take aim) with a small `clock_00` in the corner and a down-arrow over it | aim/focus + a clock ticking down = every hit shaves True Shot's cooldown |
| **piercing_tips** | `iron_chestplate` with an `arrow` punched clean through a dark cracked hole | the shot goes straight through the armour — 2 points of protection ignored |
| **seeker_arrow** *(capstone)* | `ender_eye` (the thing that seeks) with an arrow on a curving, dashed trajectory bending in to track it | eye of ender = homing; a curving arrow (arrows never bend on their own) = the shot steering onto its target |
| **snap_shot** *(capstone)* | `crossbow_arrow` firing with a white/gold muzzle-flash bursting off the tip and speed-lines behind | fired the instant you ask — no draw, no load — and the bright burst = the x4 hit |
