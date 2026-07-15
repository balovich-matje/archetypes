#!/usr/bin/env zsh
# Tier portraits for the Choose-your-Archetype picker: one per archetype per
# tier (start / peak), shown in the diagonal-split frames.
#
# Deliberately the inverse of gen-archetypes.sh: there the subject is pushed to
# the edges and the middle left empty, because nodes sit on top. Here the
# character IS the subject — centred, lit, facing us.
#
# Square, because the picker frames are square. 768x768 is ~the same pixel count
# as the 1024x576 backdrops, so it costs about the same ~4 min each.
#
# Usage: ./gen-portraits.sh [brawler|colossus|all]

set -u
ROOT="${0:A:h}"
BIN="$ROOT/build/bin/sd-cli"
MODELS="$ROOT/models"
OUT="$ROOT/out/portraits"
mkdir -p "$OUT"

COMMON=(
  --diffusion-model "$MODELS/z_image_turbo-Q6_K.gguf"
  --vae             "$MODELS/ae.safetensors"
  --llm             "$MODELS/Qwen3-4B-Instruct-2507-Q4_K_M.gguf"
  --cfg-scale 1.0
  --steps 8
  -W 768 -H 768
  --offload-to-cpu
  --diffusion-fa
)

# Naming the blocky body plan explicitly: the model leans photoreal, and without
# this it renders a human in armour rather than a Minecraft character.
STYLE="Minecraft video game style, blocky voxel character with a cubic head and rectangular boxy arms and legs, pixelated 16x16 textures, cubic geometry"
MOOD="full body standing character centred in frame, plain very dark background, dramatic rim lighting, RPG class select portrait, sharp focus on the character, no text, no UI, no logos"
# Same, minus the "dark background" and rim light, which fight a green screen.
MOOD_CHROMA="full body standing character centred in frame, even studio lighting on the character, RPG class select portrait, sharp focus on the character, no shadow on the backdrop, no text, no UI, no logos"

typeset -A PROMPTS
# v2: green screen, same character. The v1 dark-backdrop shot could not be cut —
# a luma key eats a dark subject, and a flood key either leaves the cast shadows
# behind (low threshold) or leaks through the shading and shreds the armour
# (high). Nothing about the art was wrong; it was unkeyable.
PROMPTS[brawler]="$STYLE, a scrappy novice fighter early in the game, mismatched worn copper and iron armour pieces over plain clothes, no helmet, holding a simple wooden shield in one hand and a plain iron sword in the other, scuffed and dented gear, humble underdog, standing against a flat uniform chroma key green screen background, solid green backdrop, no green light on the character, $MOOD_CHROMA"

# v2. Three changes:
#  - green screen, because netherite is near-black and so was the old backdrop;
#    keying by hue instead of luma is the difference between a clean cut and
#    eating the armour.
#  - "one sword in each hand, two swords total" — "dual wielding two swords" read
#    as two per hand and gave us four.
#  - helmet: it is part of a full netherite set anyway, and it sidesteps the
#    smeared face, which is really the detail budget spread over a full body.
PROMPTS[colossus]="$STYLE, a towering armoured champion, full netherite armour set dark charcoal black with bright red armour trim, netherite helmet fully covering the head, holding one enchanted netherite sword in each hand, two swords total, one per hand, purple enchantment glimmer on the blades, imposing heroic stance, standing against a flat uniform chroma key green screen background, solid green backdrop, no green light on the character, $MOOD_CHROMA"

gen() {
  local name="$1"
  echo "=== $name ==="
  "$BIN" "${COMMON[@]}" -p "${PROMPTS[$name]}" -o "$OUT/$name.png" 2>&1 | grep -E "save result|error"
}

target="${1:-all}"
if [[ "$target" == "all" ]]; then
  for k in brawler colossus; do gen "$k"; done
else
  gen "$target"
fi

echo "=== output ==="
ls -la "$OUT"
