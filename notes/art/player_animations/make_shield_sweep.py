"""Emit the three Shield Sweep player-animator files from one authored arm track.

The right-arm track is the only thing written by hand; the left is its exact
mirror (yaw and roll negated) and the dual file is the two of them together
with a torso that no longer twists, because a two-shield sweep is symmetric.

Schema notes that are NOT guesses (read out of PlayerAnimatorLoader):
  * "version": 3 is REQUIRED. Below 3 the loader rewrites the bone name
    `torso` to `body`, and `body` additionally has its pitch and yaw negated
    on the way in (fillKeyframeStack's negate flags are isItem || isBody).
  * a move's `easing` governs the segment LEAVING that keyframe.
  * camelCase bone keys are normalised (`rightArm` -> `right_arm`), and for an
    ARM no axis is negated, so `yaw` lands straight on ModelPart.yRot.
"""
import collections
import json
import os

DST = os.path.normpath(os.path.join(
    os.path.dirname(os.path.abspath(__file__)),
    "../../../src/main/resources/assets/archetypes/player_animations"))

# tick -> (easing, torso, rightArm)
# The right arm starts on vanilla's own blocking pose: poseBlockingArm sets
# xRot = -0.9424779 rad (-54 deg) and yRot = -30 deg for the right arm with a
# level head, so tick 0 IS "the position the shield is in during a block".
# Positive yaw swings an arm toward the player's RIGHT (derived from
# poseBlockingArm's mirrored +-30 and the ZYX rotation order), so the right
# arm's sweep runs -38 -> +62.
TRACK = [
    (0, "EASEINQUAD",    {"pitch": 2, "yaw": -8},  {"pitch": -54, "yaw": -38, "roll": 4}),
    (2, "EASEOUTQUAD",   {"pitch": 0, "yaw": 14},  {"pitch": -80, "yaw": 62, "roll": -22}),
    (4, "EASEINOUTQUAD", {"pitch": 3, "yaw": 8},   {"pitch": -64, "yaw": 44, "roll": -10}),
    (8, "EASEINOUTQUAD", {"pitch": 0, "yaw": 0},   {"pitch": 0, "yaw": 0, "roll": 0}),
]

BEGIN, END, STOP, RETURN = 0, 8, 10, 2


def mirror(bone):
    return {"pitch": bone["pitch"], "yaw": -bone["yaw"], "roll": -bone["roll"]}


def emote(name, description, arms):
    """arms: list of (bone key, transform picker) applied to every move."""
    moves = []

    for tick, easing, torso, right in TRACK:
        move = collections.OrderedDict()
        move["tick"] = tick
        move["easing"] = easing
        move["torso"] = torso if len(arms) == 1 else {"pitch": torso["pitch"], "yaw": 0}

        for key, pick in arms:
            move[key] = pick(right)

        moves.append(move)

    return collections.OrderedDict([
        ("name", name),
        ("author", "archetypes"),
        ("description", description),
        ("version", 3),
        ("emote", collections.OrderedDict([
            ("isLoop", False),
            ("returnTick", RETURN),
            ("beginTick", BEGIN),
            ("endTick", END),
            ("stopTick", STOP),
            ("degrees", True),
            ("moves", moves),
        ])),
    ])


FILES = {
    "shield_sweep_right": (
        "Shield Sweep: the right arm swings its shield out to the player's right",
        [("rightArm", lambda r: r)],
    ),
    "shield_sweep_left": (
        "Shield Sweep: the mirror of shield_sweep_right, out to the player's left",
        [("leftArm", mirror)],
    ),
    "shield_sweep_dual": (
        "Shield Sweep: two shields opening from the block position, one each way",
        [("rightArm", lambda r: r), ("leftArm", mirror)],
    ),
}

for name, (description, arms) in FILES.items():
    path = os.path.join(DST, name + ".json")
    with open(path, "w") as handle:
        json.dump(emote(name, description, arms), handle, indent="\t")
        handle.write("\n")
    print("wrote", path)
