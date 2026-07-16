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
			case SHADOW -> ShadowNodes.def(tree, index).family().nameKey();
			case ASSASSIN -> AssassinNodes.def(tree, index).family().nameKey();
			case ELEMENTALIST -> ElementalistNodes.def(tree, index).family().nameKey();
			case PROTECTOR -> ProtectorNodes.def(tree, index).family().nameKey();
			default -> PlaceholderNodes.nameKey(tree, index);
		};
	}

	public static String descriptionKey(final SubTree tree, final int index) {
		return switch (tree) {
			case SLAYER -> SlayerNodes.def(tree, index).family().descriptionKey();
			case CRUSHER -> CrusherNodes.def(tree, index).family().descriptionKey();
			case MARKSMAN -> MarksmanNodes.def(tree, index).family().descriptionKey();
			case SHADOW -> ShadowNodes.def(tree, index).family().descriptionKey();
			case ASSASSIN -> AssassinNodes.def(tree, index).family().descriptionKey();
			case ELEMENTALIST -> ElementalistNodes.def(tree, index).family().descriptionKey();
			case PROTECTOR -> ProtectorNodes.def(tree, index).family().descriptionKey();
			default -> PlaceholderNodes.descriptionKey(tree, index);
		};
	}

	public static @Nullable Item icon(final SubTree tree, final int index) {
		return switch (tree) {
			case SLAYER -> SlayerNodes.def(tree, index).family().icon();
			case CRUSHER -> CrusherNodes.def(tree, index).family().icon();
			case MARKSMAN -> MarksmanNodes.def(tree, index).family().icon();
			case SHADOW -> ShadowNodes.def(tree, index).family().icon();
			case ASSASSIN -> AssassinNodes.def(tree, index).family().icon();
			case ELEMENTALIST -> ElementalistNodes.def(tree, index).family().icon();
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
			case SHADOW -> ShadowNodes.def(tree, index).family() == ShadowNodes.Family.MINOR;
			case ASSASSIN -> AssassinNodes.def(tree, index).family() == AssassinNodes.Family.MINOR;
			case ELEMENTALIST -> ElementalistNodes.def(tree, index).family() == ElementalistNodes.Family.MINOR;
			case PROTECTOR -> ProtectorNodes.def(tree, index).family() == ProtectorNodes.Family.MINOR;
			default -> PlaceholderNodes.kind(tree, index) == PlaceholderNodes.Kind.MINOR;
		};
	}

	public static int rankOf(final SubTree tree, final int index) {
		return switch (tree) {
			case SLAYER -> SlayerNodes.def(tree, index).rank();
			case CRUSHER -> CrusherNodes.def(tree, index).rank();
			case MARKSMAN -> MarksmanNodes.def(tree, index).rank();
			case SHADOW -> ShadowNodes.def(tree, index).rank();
			case ASSASSIN -> AssassinNodes.def(tree, index).rank();
			case ELEMENTALIST -> ElementalistNodes.def(tree, index).rank();
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

		if (tree == SubTree.ELEMENTALIST) {
			ElementalistNodes.Family family = ElementalistNodes.def(tree, index).family();

			return switch (family) {
				case FIREBALL -> ElementalistNodes.rank(tree, owned, ElementalistNodes.Family.ICE_BLAST) > 0;
				case ICE_BLAST -> ElementalistNodes.rank(tree, owned, ElementalistNodes.Family.FIREBALL) > 0;
				case METEORITE -> ElementalistNodes.rank(tree, owned, ElementalistNodes.Family.FLAMETHROWER) > 0;
				case FLAMETHROWER -> ElementalistNodes.rank(tree, owned, ElementalistNodes.Family.METEORITE) > 0;
				case GLACIAL_SPIKE -> ElementalistNodes.rank(tree, owned, ElementalistNodes.Family.BLIZZARD) > 0;
				case BLIZZARD -> ElementalistNodes.rank(tree, owned, ElementalistNodes.Family.GLACIAL_SPIKE) > 0;
				default -> false;
			};
		}

		if (tree == SubTree.ASSASSIN) {
			AssassinNodes.Family family = AssassinNodes.def(tree, index).family();

			if (family == AssassinNodes.Family.SHADOW_FLURRY) {
				return AssassinNodes.rank(tree, owned, AssassinNodes.Family.MOMENTUM) > 0;
			}

			if (family == AssassinNodes.Family.MOMENTUM) {
				return AssassinNodes.rank(tree, owned, AssassinNodes.Family.SHADOW_FLURRY) > 0;
			}

			return false;
		}

		if (tree == SubTree.SHADOW) {
			ShadowNodes.Family family = ShadowNodes.def(tree, index).family();

			if (family == ShadowNodes.Family.LAST_SHADOW) {
				return ShadowNodes.rank(tree, owned, ShadowNodes.Family.PREDATOR) > 0;
			}

			if (family == ShadowNodes.Family.PREDATOR) {
				return ShadowNodes.rank(tree, owned, ShadowNodes.Family.LAST_SHADOW) > 0;
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
