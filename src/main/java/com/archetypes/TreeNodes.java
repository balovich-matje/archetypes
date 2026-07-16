package com.archetypes;

import java.util.Set;

import net.minecraft.world.item.Item;
import org.jspecify.annotations.Nullable;

/**
 * One face over the per-tree node tables, so the screen and the purchase rules
 * need not know which tree they are looking at. Gameplay code keeps using the
 * tree-specific classes directly — this is display and rule plumbing only.
 */
public final class TreeNodes {
	private TreeNodes() {
	}

	public static String nameKey(final SubTree tree, final int index) {
		return tree == SubTree.SLAYER
				? SlayerNodes.def(tree, index).family().nameKey()
				: ProtectorNodes.def(tree, index).family().nameKey();
	}

	public static String descriptionKey(final SubTree tree, final int index) {
		return tree == SubTree.SLAYER
				? SlayerNodes.def(tree, index).family().descriptionKey()
				: ProtectorNodes.def(tree, index).family().descriptionKey();
	}

	public static @Nullable Item icon(final SubTree tree, final int index) {
		return tree == SubTree.SLAYER
				? SlayerNodes.def(tree, index).family().icon()
				: ProtectorNodes.def(tree, index).family().icon();
	}

	/** Texture-based icon (effect sprites and the like), or null if the
	 * family's icon is an item. */
	public static net.minecraft.resources.@Nullable Identifier iconSprite(final SubTree tree, final int index) {
		return tree == SubTree.SLAYER ? SlayerNodes.def(tree, index).family().sprite() : null;
	}

	/** Pixel size of the square texture behind iconSprite. */
	public static int iconSpriteSize(final SubTree tree, final int index) {
		return tree == SubTree.SLAYER ? SlayerNodes.def(tree, index).family().spriteSize() : 0;
	}

	/** Effect layer drawn over the item icon, or null when the item stands alone. */
	public static net.minecraft.resources.@Nullable Identifier iconOverlay(final SubTree tree, final int index) {
		return tree == SubTree.SLAYER ? null : ProtectorNodes.def(tree, index).family().overlay();
	}

	/** Pixel size of the square texture behind iconOverlay. */
	public static int iconOverlaySize(final SubTree tree, final int index) {
		return tree == SubTree.SLAYER ? 0 : ProtectorNodes.def(tree, index).family().overlaySize();
	}

	public static boolean isMinor(final SubTree tree, final int index) {
		return tree == SubTree.SLAYER
				? SlayerNodes.def(tree, index).family() == SlayerNodes.Family.MINOR
				: ProtectorNodes.def(tree, index).family() == ProtectorNodes.Family.MINOR;
	}

	public static int rankOf(final SubTree tree, final int index) {
		return tree == SubTree.SLAYER
				? SlayerNodes.def(tree, index).rank()
				: ProtectorNodes.def(tree, index).rank();
	}

	/** How many nodes share this node's family — >1 means show the rank label. */
	public static int familySize(final SubTree tree, final int index) {
		int count = 0;

		for (int i = 0; i < tree.constellation().nodes().size(); i++) {
			if (nameKey(tree, i).equals(nameKey(tree, index))) {
				count++;
			}
		}

		return count;
	}

	/**
	 * Capstones come in mutually exclusive pairs: owning one locks the other.
	 * Protector: Bulwark vs Ground Slam. Slayer: Bladestorm vs Decimate.
	 */
	public static boolean exclusiveTaken(final SubTree tree, final Set<Integer> owned, final int index) {
		if (tree == SubTree.SLAYER) {
			SlayerNodes.Family family = SlayerNodes.def(tree, index).family();

			if (family == SlayerNodes.Family.BLADESTORM) {
				return SlayerNodes.rank(tree, owned, SlayerNodes.Family.DECIMATE) > 0;
			}

			if (family == SlayerNodes.Family.DECIMATE) {
				return SlayerNodes.rank(tree, owned, SlayerNodes.Family.BLADESTORM) > 0;
			}

			return false;
		}

		ProtectorNodes.Family family = ProtectorNodes.def(tree, index).family();

		if (family == ProtectorNodes.Family.OMNI_BLOCK) {
			return ProtectorNodes.rank(tree, owned, ProtectorNodes.Family.GROUND_SLAM) > 0;
		}

		if (family == ProtectorNodes.Family.GROUND_SLAM) {
			return ProtectorNodes.rank(tree, owned, ProtectorNodes.Family.OMNI_BLOCK) > 0;
		}

		return false;
	}
}
