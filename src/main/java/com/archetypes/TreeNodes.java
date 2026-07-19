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
			case WIZARD -> WizardNodes.def(tree, index).family().nameKey();
			case PRIEST -> PriestNodes.def(tree, index).family().nameKey();
			case PROTECTOR -> ProtectorNodes.def(tree, index).family().nameKey();
			case ORACLE_ELEMENTALIST -> OracleElementalistNodes.def(tree, index).family().nameKey();
			case ORACLE_WIZARD -> OracleWizardNodes.def(tree, index).family().nameKey();
			case ORACLE_PRIEST -> OraclePriestNodes.def(tree, index).family().nameKey();
			case NEMESIS_SHADOW -> NemesisShadowNodes.def(tree, index).family().nameKey();
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
			case WIZARD -> WizardNodes.def(tree, index).family().descriptionKey();
			case PRIEST -> PriestNodes.def(tree, index).family().descriptionKey();
			case PROTECTOR -> ProtectorNodes.def(tree, index).family().descriptionKey();
			case ORACLE_ELEMENTALIST -> OracleElementalistNodes.def(tree, index).family().descriptionKey();
			case ORACLE_WIZARD -> OracleWizardNodes.def(tree, index).family().descriptionKey();
			case ORACLE_PRIEST -> OraclePriestNodes.def(tree, index).family().descriptionKey();
			case NEMESIS_SHADOW -> NemesisShadowNodes.def(tree, index).family().descriptionKey();
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
			case WIZARD -> WizardNodes.def(tree, index).family().icon();
			case PRIEST -> PriestNodes.def(tree, index).family().icon();
			case PROTECTOR -> ProtectorNodes.def(tree, index).family().icon();
			case ORACLE_ELEMENTALIST -> OracleElementalistNodes.def(tree, index).family().icon();
			case ORACLE_WIZARD -> OracleWizardNodes.def(tree, index).family().icon();
			case ORACLE_PRIEST -> OraclePriestNodes.def(tree, index).family().icon();
			case NEMESIS_SHADOW -> NemesisShadowNodes.def(tree, index).family().icon();
		};
	}

	/**
	 * A family's 32px node sprite under textures/node/&lt;tree&gt;/ — one
	 * complete set per tree. MINOR families have no sprite and fall back to
	 * their item icon. Public so the proc HUD's crosshair flash and the
	 * picker share the tree screen's exact art.
	 */
	public static net.minecraft.resources.@Nullable Identifier familySprite(final SubTree tree,
			final Enum<?> family) {
		return "MINOR".equals(family.name()) ? null
				: Archetypes.id("textures/node/" + tree.id()
						+ "/" + family.name().toLowerCase(java.util.Locale.ROOT) + ".png");
	}

	/** Texture-based icon (effect sprites and the like), or null if the
	 * family's icon is an item. */
	public static net.minecraft.resources.@Nullable Identifier iconSprite(final SubTree tree, final int index) {
		return switch (tree) {
			// Strength trees: family sprite first; MINOR (null sprite) falls
			// back to the hand-made overlays some families carry.
			case SLAYER -> {
				SlayerNodes.Family family = SlayerNodes.def(tree, index).family();
				var sprite = familySprite(tree, family);
				yield sprite != null ? sprite : family.sprite();
			}
			case CRUSHER -> {
				CrusherNodes.Family family = CrusherNodes.def(tree, index).family();
				var sprite = familySprite(tree, family);
				yield sprite != null ? sprite : family.sprite();
			}
			case PROTECTOR -> {
				ProtectorNodes.Family family = ProtectorNodes.def(tree, index).family();
				var sprite = familySprite(tree, family);
				yield sprite != null ? sprite : family.sprite();
			}
			// Shadow's hand-made sprites (Invisibility) outrank the set.
			case SHADOW -> {
				ShadowNodes.Family family = ShadowNodes.def(tree, index).family();
				yield family.sprite() != null ? family.sprite() : familySprite(tree, family);
			}
			case MARKSMAN -> familySprite(tree, MarksmanNodes.def(tree, index).family());
			case ASSASSIN -> familySprite(tree, AssassinNodes.def(tree, index).family());
			case WIZARD -> familySprite(tree, WizardNodes.def(tree, index).family());
			case PRIEST -> familySprite(tree, PriestNodes.def(tree, index).family());
			case ELEMENTALIST -> familySprite(tree, ElementalistNodes.def(tree, index).family());
			case ORACLE_ELEMENTALIST -> familySprite(tree,
					OracleElementalistNodes.def(tree, index).family());
			case ORACLE_WIZARD -> familySprite(tree, OracleWizardNodes.def(tree, index).family());
			case ORACLE_PRIEST -> familySprite(tree, OraclePriestNodes.def(tree, index).family());
			case NEMESIS_SHADOW -> familySprite(tree, NemesisShadowNodes.def(tree, index).family());
		};
	}

	/** Pixel size of the square texture behind iconSprite. */
	public static int iconSpriteSize(final SubTree tree, final int index) {
		return switch (tree) {
			case SLAYER -> familySprite(tree, SlayerNodes.def(tree, index).family()) != null
					? 32 : SlayerNodes.def(tree, index).family().spriteSize();
			case CRUSHER -> familySprite(tree, CrusherNodes.def(tree, index).family()) != null
					? 32 : CrusherNodes.def(tree, index).family().spriteSize();
			case PROTECTOR -> familySprite(tree, ProtectorNodes.def(tree, index).family()) != null
					? 32 : ProtectorNodes.def(tree, index).family().spriteSize();
			case SHADOW -> {
				ShadowNodes.Family family = ShadowNodes.def(tree, index).family();
				yield family.sprite() != null ? family.spriteSize() : 32;
			}
			case MARKSMAN, ASSASSIN, WIZARD, PRIEST, ELEMENTALIST -> 32;
			case ORACLE_ELEMENTALIST, ORACLE_WIZARD, ORACLE_PRIEST, NEMESIS_SHADOW -> 32;
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
			case WIZARD -> WizardNodes.def(tree, index).family() == WizardNodes.Family.MINOR;
			case PRIEST -> PriestNodes.def(tree, index).family() == PriestNodes.Family.MINOR;
			case PROTECTOR -> ProtectorNodes.def(tree, index).family() == ProtectorNodes.Family.MINOR;
			case ORACLE_ELEMENTALIST -> OracleElementalistNodes.def(tree, index).family()
					== OracleElementalistNodes.Family.MINOR;
			case ORACLE_WIZARD -> OracleWizardNodes.def(tree, index).family() == OracleWizardNodes.Family.MINOR;
			case ORACLE_PRIEST -> OraclePriestNodes.def(tree, index).family() == OraclePriestNodes.Family.MINOR;
			case NEMESIS_SHADOW -> NemesisShadowNodes.def(tree, index).family() == NemesisShadowNodes.Family.MINOR;
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
			case WIZARD -> WizardNodes.def(tree, index).rank();
			case PRIEST -> PriestNodes.def(tree, index).rank();
			case PROTECTOR -> ProtectorNodes.def(tree, index).rank();
			case ORACLE_ELEMENTALIST -> OracleElementalistNodes.def(tree, index).rank();
			case ORACLE_WIZARD -> OracleWizardNodes.def(tree, index).rank();
			case ORACLE_PRIEST -> OraclePriestNodes.def(tree, index).rank();
			case NEMESIS_SHADOW -> NemesisShadowNodes.def(tree, index).rank();
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

	/** How a node should be dressed on screen. */
	public enum NodeKind {
		ACTIVE, CAPSTONE, NORMAL
	}

	/**
	 * Actives are the castable roots (blue on screen); capstones the
	 * exclusive-pair choices (purple). Slayer's and Crusher's actives ARE
	 * their capstones, so they read as capstones.
	 */
	public static NodeKind kind(final SubTree tree, final int index) {
		return switch (tree) {
			case PROTECTOR -> switch (ProtectorNodes.def(tree, index).family()) {
				case BASH -> NodeKind.ACTIVE;
				case OMNI_BLOCK, GROUND_SLAM -> NodeKind.CAPSTONE;
				default -> NodeKind.NORMAL;
			};
			case SLAYER -> switch (SlayerNodes.def(tree, index).family()) {
				case BLADESTORM, DECIMATE -> NodeKind.CAPSTONE;
				default -> NodeKind.NORMAL;
			};
			case CRUSHER -> switch (CrusherNodes.def(tree, index).family()) {
				case HAYMAKER, QUAKE -> NodeKind.CAPSTONE;
				default -> NodeKind.NORMAL;
			};
			case MARKSMAN -> switch (MarksmanNodes.def(tree, index).family()) {
				case TRUE_SHOT -> NodeKind.ACTIVE;
				case SEEKER_ARROW, SNAP_SHOT -> NodeKind.CAPSTONE;
				default -> NodeKind.NORMAL;
			};
			case ASSASSIN -> switch (AssassinNodes.def(tree, index).family()) {
				case SHADOW_STEP -> NodeKind.ACTIVE;
				case SHADOW_FLURRY, MOMENTUM -> NodeKind.CAPSTONE;
				default -> NodeKind.NORMAL;
			};
			case SHADOW -> switch (ShadowNodes.def(tree, index).family()) {
				case INVISIBILITY -> NodeKind.ACTIVE;
				case LAST_SHADOW, PREDATOR -> NodeKind.CAPSTONE;
				default -> NodeKind.NORMAL;
			};
			case ELEMENTALIST -> switch (ElementalistNodes.def(tree, index).family()) {
				case FIREBALL, ICE_BLAST -> NodeKind.ACTIVE;
				case METEORITE, FLAMETHROWER, GLACIAL_SPIKE, BLIZZARD -> NodeKind.CAPSTONE;
				default -> NodeKind.NORMAL;
			};
			case WIZARD -> switch (WizardNodes.def(tree, index).family()) {
				case MAGIC_MISSILE -> NodeKind.ACTIVE;
				case SEEKER_MISSILE, LANCE -> NodeKind.CAPSTONE;
				default -> NodeKind.NORMAL;
			};
			case PRIEST -> switch (PriestNodes.def(tree, index).family()) {
				case HOLY_LIGHT -> NodeKind.ACTIVE;
				case RENEWAL, BENEDICTION -> NodeKind.CAPSTONE;
				default -> NodeKind.NORMAL;
			};
			case ORACLE_ELEMENTALIST -> switch (OracleElementalistNodes.def(tree, index).family()) {
				case LIGHTNING_STRIKE -> NodeKind.ACTIVE;
				// The AOE-conversion node crowns the tree — ringed as a capstone.
				case TEMPEST -> NodeKind.CAPSTONE;
				default -> NodeKind.NORMAL;
			};
			case ORACLE_WIZARD -> switch (OracleWizardNodes.def(tree, index).family()) {
				case MAGIC_ARMAMENTS -> NodeKind.ACTIVE;
				// The bow-variant node crowns the tree — ringed as a capstone.
				case SPELLBOW -> NodeKind.CAPSTONE;
				default -> NodeKind.NORMAL;
			};
			case ORACLE_PRIEST -> switch (OraclePriestNodes.def(tree, index).family()) {
				// Cast-triggered, not key-bound, but it is the tree's one
				// castable effect — blue like every other active.
				case AURA_OF_RADIANCE -> NodeKind.ACTIVE;
				// The head of the cross crowns the tree — ringed as a capstone.
				case RETRIBUTION -> NodeKind.CAPSTONE;
				default -> NodeKind.NORMAL;
			};
			case NEMESIS_SHADOW -> switch (NemesisShadowNodes.def(tree, index).family()) {
				case DARK_RITUAL -> NodeKind.ACTIVE;
				// The crown of the left line — ringed as a capstone.
				case INCORPOREAL -> NodeKind.CAPSTONE;
				default -> NodeKind.NORMAL;
			};
		};
	}

	private static final java.util.List<ElementalistNodes.Family> ELEMENTALIST_CAPSTONES =
			java.util.List.of(ElementalistNodes.Family.METEORITE, ElementalistNodes.Family.FLAMETHROWER,
					ElementalistNodes.Family.GLACIAL_SPIKE, ElementalistNodes.Family.BLIZZARD);

	/**
	 * The picker's preview actives per tree, as node indices in display
	 * order. Explicit rather than derived from kind(): the fork pairs are
	 * pinned left/right (Decimate|Bladestorm, Quake|Haymaker,
	 * Fireball|Ice Blast) and can't drift with the constellations.
	 */
	public static java.util.List<Integer> pickerActives(final SubTree tree) {
		return switch (tree) {
			case PROTECTOR -> indicesOf(tree, ProtectorNodes.Family.BASH);
			// One preview per tree (user call): the capstone forks are for
			// the tree screen to explain, not the picker.
			case SLAYER -> indicesOf(tree, SlayerNodes.Family.BLADESTORM);
			case CRUSHER -> indicesOf(tree, CrusherNodes.Family.QUAKE);
			case MARKSMAN -> indicesOf(tree, MarksmanNodes.Family.TRUE_SHOT);
			case ASSASSIN -> indicesOf(tree, AssassinNodes.Family.SHADOW_STEP);
			case SHADOW -> indicesOf(tree, ShadowNodes.Family.INVISIBILITY);
			case ELEMENTALIST -> indicesOf(tree, ElementalistNodes.Family.FIREBALL);
			case WIZARD -> indicesOf(tree, WizardNodes.Family.MAGIC_MISSILE);
			case PRIEST -> indicesOf(tree, PriestNodes.Family.HOLY_LIGHT);
			// Epic trees never appear on the picker, but the switch is exhaustive.
			case ORACLE_ELEMENTALIST -> indicesOf(tree, OracleElementalistNodes.Family.LIGHTNING_STRIKE);
			case ORACLE_WIZARD -> indicesOf(tree, OracleWizardNodes.Family.MAGIC_ARMAMENTS);
			case ORACLE_PRIEST -> indicesOf(tree, OraclePriestNodes.Family.AURA_OF_RADIANCE);
			case NEMESIS_SHADOW -> indicesOf(tree, NemesisShadowNodes.Family.DARK_RITUAL);
		};
	}

	private static Enum<?> familyOf(final SubTree tree, final int index) {
		return switch (tree) {
			case PROTECTOR -> ProtectorNodes.def(tree, index).family();
			case SLAYER -> SlayerNodes.def(tree, index).family();
			case CRUSHER -> CrusherNodes.def(tree, index).family();
			case MARKSMAN -> MarksmanNodes.def(tree, index).family();
			case ASSASSIN -> AssassinNodes.def(tree, index).family();
			case SHADOW -> ShadowNodes.def(tree, index).family();
			case ELEMENTALIST -> ElementalistNodes.def(tree, index).family();
			case WIZARD -> WizardNodes.def(tree, index).family();
			case PRIEST -> PriestNodes.def(tree, index).family();
			case ORACLE_ELEMENTALIST -> OracleElementalistNodes.def(tree, index).family();
			case ORACLE_WIZARD -> OracleWizardNodes.def(tree, index).family();
			case ORACLE_PRIEST -> OraclePriestNodes.def(tree, index).family();
			case NEMESIS_SHADOW -> NemesisShadowNodes.def(tree, index).family();
		};
	}

	private static java.util.List<Integer> indicesOf(final SubTree tree, final Enum<?>... families) {
		java.util.List<Integer> out = new java.util.ArrayList<>();

		for (Enum<?> family : families) {
			int index = indexOfFamily(tree, family);

			if (index >= 0) {
				out.add(index);
			}
		}

		return out;
	}

	/** First node index of a family, or -1 — the cooldown bar's icon lookup. */
	public static int indexOfFamily(final SubTree tree, final Enum<?> family) {
		for (int i = 0; i < tree.constellation().nodes().size(); i++) {
			if (familyOf(tree, i) == family) {
				return i;
			}
		}

		return -1;
	}

	/**
	 * Capstones come in mutually exclusive pairs: owning one locks the other.
	 * Protector: Bulwark vs Ground Slam. Slayer: Bladestorm vs Decimate.
	 * Crusher: Quake vs Haymaker.
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
			CrusherNodes.Family family = CrusherNodes.def(tree, index).family();

			if (family == CrusherNodes.Family.QUAKE) {
				return CrusherNodes.rank(tree, owned, CrusherNodes.Family.HAYMAKER) > 0;
			}

			if (family == CrusherNodes.Family.HAYMAKER) {
				return CrusherNodes.rank(tree, owned, CrusherNodes.Family.QUAKE) > 0;
			}

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

		if (tree == SubTree.WIZARD) {
			WizardNodes.Family family = WizardNodes.def(tree, index).family();

			if (family == WizardNodes.Family.SEEKER_MISSILE) {
				return WizardNodes.rank(tree, owned, WizardNodes.Family.LANCE) > 0;
			}

			if (family == WizardNodes.Family.LANCE) {
				return WizardNodes.rank(tree, owned, WizardNodes.Family.SEEKER_MISSILE) > 0;
			}

			return false;
		}

		if (tree == SubTree.PRIEST) {
			PriestNodes.Family family = PriestNodes.def(tree, index).family();

			if (family == PriestNodes.Family.RENEWAL) {
				return PriestNodes.rank(tree, owned, PriestNodes.Family.BENEDICTION) > 0;
			}

			if (family == PriestNodes.Family.BENEDICTION) {
				return PriestNodes.rank(tree, owned, PriestNodes.Family.RENEWAL) > 0;
			}

			return false;
		}

		if (tree == SubTree.ELEMENTALIST) {
			ElementalistNodes.Family family = ElementalistNodes.def(tree, index).family();

			// The two openers exclude each other; the four capstones are ONE
			// choice total — a fire pick was leaving the ice pair open (user
			// bug report), so any owned capstone locks the other three.
			return switch (family) {
				case FIREBALL -> ElementalistNodes.rank(tree, owned, ElementalistNodes.Family.ICE_BLAST) > 0;
				case ICE_BLAST -> ElementalistNodes.rank(tree, owned, ElementalistNodes.Family.FIREBALL) > 0;
				case METEORITE, FLAMETHROWER, GLACIAL_SPIKE, BLIZZARD -> {
					for (ElementalistNodes.Family capstone : ELEMENTALIST_CAPSTONES) {
						if (capstone != family && ElementalistNodes.rank(tree, owned, capstone) > 0) {
							yield true;
						}
					}

					yield false;
				}
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
