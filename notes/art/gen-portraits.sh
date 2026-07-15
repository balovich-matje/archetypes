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
# v3. Loose art direction instead of a parts list — naming exact armour and
# weapon pieces was fighting the model rather than using it.
#
# Two hard-won constraints stay, because they are mechanical, not creative:
#  - green screen: the dark-backdrop shots were unkeyable at any threshold.
#  - warm directional light: v2 flattened into default Steve when "even studio
#    lighting" replaced the torchlight. Green screen was never the problem;
#    losing the key light was.
# v4. v3 read "light armour" as an excuse to draw none at all and gave a bare
# torso in an odd lunge. Armour stated first and concretely, and the stance named
# rather than left to "dynamic".
PROMPTS[brawler]="$STYLE, a scrappy young fighter wearing worn brown leather armour, leather chest piece and shoulder pads and bracers over a tunic, standing square in a boxing guard with both fists raised and clenched in front of him, bare knuckles, no weapon, warm directional key light from the left, fantasy game art, standing against a flat uniform chroma key green screen background, solid green backdrop, no ground, no horizon, no green light on the character, $MOOD_CHROMA"

# v3. Loose art direction, and one structural fix: a single two-handed greatsword
# held in BOTH hands. Counting swords was never going to work — "one in each
# hand" gave four, then three. With one weapon and both hands committed to it
# there is nothing left to duplicate.
PROMPTS[colossus]="$STYLE, a towering legendary champion in epic ornate full body plate armour, full helmet covering the face, gripping a single enormous two-handed greatsword with both hands, blade planted point down in front of him, imposing heroic stance, dramatic directional key light, embers, fantasy game art, standing against a flat uniform chroma key green screen background, solid green backdrop, no green light on the character, $MOOD_CHROMA"

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
