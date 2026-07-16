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
		return switch (tree) {
			case SLAYER -> SlayerNodes.def(tree, index).family().nameKey();
			case CRUSHER -> CrusherNodes.def(tree, index).family().nameKey();
			case MARKSMAN -> MarksmanNodes.def(tree, index).family().nameKey();
			case PROTECTOR -> ProtectorNodes.def(tree, index).family().nameKey();
			default -> PlaceholderNodes.nameKey(tree, index);
		};
	}

	public static String descriptionKey(final SubTree tree, final int index) {
		return switch (tree) {
			case SLAYER -> SlayerNodes.def(tree, index).family().descriptionKey();
			case CRUSHER -> CrusherNodes.def(tree, index).family().descriptionKey();
			case MARKSMAN -> MarksmanNodes.def(tree, index).family().descriptionKey();
			case PROTECTOR -> ProtectorNodes.def(tree, index).family().descriptionKey();
			default -> PlaceholderNodes.descriptionKey(tree, index);
		};
	}

	public static @Nullable Item icon(final SubTree tree, final int index) {
		return switch (tree) {
			case SLAYER -> SlayerNodes.def(tree, index).family().icon();
			case CRUSHER -> CrusherNodes.def(tree, index).family().icon();
			case MARKSMAN -> MarksmanNodes.def(tree, index).family().icon();
			case PROTECTOR -> ProtectorNodes.def(tree, index).family().icon();
			default -> PlaceholderNodes.icon(tree, index);
		};
	}

	/** Texture-based icon (effect sprites and the like), or null if the
	 * family's icon is an item. */
	public static net.minecraft.resources.@Nullable Identifier iconSprite(final SubTree tree, final int index) {
		return switch (tree) {
			case SLAYER -> SlayerNodes.def(tree, index).family().sprite();
			case CRUSHER -> CrusherNodes.def(tree, index).family().sprite();
			case PROTECTOR -> ProtectorNodes.def(tree, index).family().sprite();
			default -> null;
		};
	}

	/** Pixel size of the square texture behind iconSprite. */
	public static int iconSpriteSize(final SubTree tree, final int index) {
		return switch (tree) {
			case SLAYER -> SlayerNodes.def(tree, index).family().spriteSize();
			case CRUSHER -> CrusherNodes.def(tree, index).family().spriteSize();
			case PROTECTOR -> ProtectorNodes.def(tree, index).family().spriteSize();
			default -> 0;
		};
	}

	/** Effect layer drawn over the item icon, or null when the item stands alone. */
	public static net.minecraft.resources.@Nullable Identifier iconOverlay(final SubTree tree, final int index) {
		return switch (tree) {
			case PROTECTOR -> ProtectorNodes.def(tree, index).family().overlay();
			case CRUSHER -> CrusherNodes.def(tree, index).family().overlay();
			default -> null;
		};
	}

	/** Pixel size of the square texture behind iconOverlay. */
	public static int iconOverlaySize(final SubTree tree, final int index) {
		return switch (tree) {
			case PROTECTOR -> ProtectorNodes.def(tree, index).family().overlaySize();
			case CRUSHER -> CrusherNodes.def(tree, index).family().overlaySize();
			default -> 0;
		};
	}

	/** True when the effect layer draws under the item render, not over it. */
	public static boolean iconOverlayBehind(final SubTree tree, final int index) {
		return switch (tree) {
			case PROTECTOR -> ProtectorNodes.def(tree, index).family().overlayBehind();
			case CRUSHER -> CrusherNodes.def(tree, index).family().overlayBehind();
			default -> false;
		};
	}

	public static boolean isMinor(final SubTree tree, final int index) {
		return switch (tree) {
			case SLAYER -> SlayerNodes.def(tree, index).family() == SlayerNodes.Family.MINOR;
			case CRUSHER -> CrusherNodes.def(tree, index).family() == CrusherNodes.Family.MINOR;
			case MARKSMAN -> MarksmanNodes.def(tree, index).family() == MarksmanNodes.Family.MINOR;
			case PROTECTOR -> ProtectorNodes.def(tree, index).family() == ProtectorNodes.Family.MINOR;
			default -> PlaceholderNodes.kind(tree, index) == PlaceholderNodes.Kind.MINOR;
		};
	}

	public static int rankOf(final SubTree tree, final int index) {
		return switch (tree) {
			case SLAYER -> SlayerNodes.def(tree, index).rank();
			case CRUSHER -> CrusherNodes.def(tree, index).rank();
			case MARKSMAN -> MarksmanNodes.def(tree, index).rank();
			case PROTECTOR -> ProtectorNodes.def(tree, index).rank();
			default -> 1;
		};
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
	 * Protector: Bulwark vs Ground Slam. Slayer: Bladestorm vs Decimate. The
	 * Crusher's mace capstone joins Haymaker's lockout when it lands.
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

		if (tree == SubTree.CRUSHER) {
			return false;
		}

		if (tree == SubTree.MARKSMAN) {
			MarksmanNodes.Family family = MarksmanNodes.def(tree, index).family();

			if (family == MarksmanNodes.Family.SEEKER_ARROW) {
				return MarksmanNodes.rank(tree, owned, MarksmanNodes.Family.SNAP_SHOT) > 0;
			}

			if (family == MarksmanNodes.Family.SNAP_SHOT) {
				return MarksmanNodes.rank(tree, owned, MarksmanNodes.Family.SEEKER_ARROW) > 0;
			}

			return false;
		}

		if (tree != SubTree.PROTECTOR) {
			return PlaceholderNodes.exclusiveTaken(tree, owned, index);
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
