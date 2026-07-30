package com.archetypes;

import net.minecraft.world.entity.player.Player;

/**
 * Sure Footing: a Protector stops being a statue while they block.
 *
 * <h2>What it replaced, and why</h2>
 * This is the node that used to be Concussive Blow — three ranks that traded
 * 12/24/36% of the bash's damage for a stronger shove. It was a knob on an
 * ability rather than a change to how the tree plays, and it was a knob most
 * builds turned the wrong way: the bash is the Protector's only damage, and the
 * tree's entire other half is about making it hit harder. Selling that back for
 * knockback put two of the tree's own columns in an argument with each other.
 *
 * <p>What a shield actually costs is movement. Vanilla scales a blocking
 * player's input to a fifth ({@code UseEffects.DEFAULT}'s {@code 0.2F}) and
 * refuses to let them start a sprint, which is why "raise the shield" is a
 * commitment rather than a stance you walk around in. Buying that back is a
 * change to what the whole archetype can do — a Protector who can advance
 * behind their guard is playing a different game from one who can only stand
 * behind it — and it costs the tree nothing it was already selling.
 *
 * <h2>Where it applies</h2>
 * Client-side, in {@code LocalPlayerMixin}, because that is where movement is
 * decided: players are movement-authoritative and the multiplier never reaches
 * the server. Gated on {@code isBlocking()} rather than on "am I using an
 * item", so eating and drawing a bow keep their own penalties.
 */
public final class SureFooting {
	private SureFooting() {
	}

	/**
	 * The blocking movement multiplier this player should actually move at.
	 *
	 * <p>The penalty is {@code 1 - multiplier}; the ranks hand a share of it
	 * back, so rank 3 returns 1.0 and a full-rank Protector walks at walking
	 * speed behind a raised shield. The sprint gate is deliberately NOT lifted
	 * even at rank 3: the node is about advancing, not about charging, and
	 * lifting it would also hand Shield Rush a free approach.
	 */
	public static float relieve(final Player player, final float blocking) {
		if (blocking >= 1.0F || !player.isBlocking()) {
			return blocking;
		}

		int rank = ProtectorNodes.rank(SubTree.PROTECTOR,
				NodePurchases.owned(player, SubTree.PROTECTOR), ProtectorNodes.Family.KNOCKBACK);

		if (rank <= 0) {
			return blocking;
		}

		return 1.0F - (1.0F - blocking) * (1.0F - Tuning.sureFootingRelief(rank));
	}
}
