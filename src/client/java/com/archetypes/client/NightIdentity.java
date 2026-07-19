package com.archetypes.client;

import com.archetypes.Archetypes;
import com.archetypes.AssassinNodes;
import com.archetypes.MarksmanNodes;
import com.archetypes.NightForm;
import com.archetypes.SubTree;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * The two Cutpurse actives the night form renames: while transformed, True
 * Shot is Heart-piercing Shot and Shadow Step is Stalker's Step.
 *
 * <p>{@code NightForm.empoweredNameKey} owns the wording; this owns the
 * matching sprite and the node-index plumbing the display code needs. Both
 * halves resolve off the LOCAL player, because both are display: the tree
 * screen, the cooldown bar and the picker all draw what THIS client's player
 * is, and nobody else's form should rename anybody's buttons.
 *
 * <p>The sprites are the base nodes' own art bled over — the arrow gone
 * bone-pale through a heart, the dagger's teal turned to blood — so the
 * empowered form reads as the same ability wearing the night, not as a
 * different skill that appeared in the tree.
 */
public final class NightIdentity {
	private static final Identifier TRUE_SHOT_SPRITE =
			Archetypes.id("textures/node/nemesis_shadow/heart_piercing_shot.png");
	private static final Identifier SHADOW_STEP_SPRITE =
			Archetypes.id("textures/node/nemesis_shadow/stalkers_step.png");
	/** Both sprites are authored in the house's 32px node format. */
	public static final int SPRITE_SIZE = 32;

	private NightIdentity() {
	}

	/** The empowered name key for this node, or null when the node is not one
	 * of the two or the local player is mortal. */
	public static @Nullable String nameKey(final SubTree tree, final int index) {
		Player player = Minecraft.getInstance().player;

		if (player == null) {
			return null;
		}

		return switch (tree) {
			case MARKSMAN -> NightForm.empoweredNameKey(player, tree,
					MarksmanNodes.def(tree, index).family());
			case ASSASSIN -> NightForm.empoweredNameKey(player, tree,
					AssassinNodes.def(tree, index).family());
			default -> null;
		};
	}

	/** The empowered sprite for this node, or null — same gate as the name, so
	 * the icon and the wording can never disagree. */
	public static @Nullable Identifier sprite(final SubTree tree, final int index) {
		String key = nameKey(tree, index);

		if (key == null) {
			return null;
		}

		return tree == SubTree.MARKSMAN ? TRUE_SHOT_SPRITE : SHADOW_STEP_SPRITE;
	}
}
