package com.archetypes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.minecraft.world.entity.player.Player;

/**
 * Owning and buying tree nodes. The same checks run on the client (to paint a
 * node as buyable) and again on the server (which is the only side that
 * writes) — the attachment syncs to its owning client, so both sides read the
 * same state.
 */
public final class NodePurchases {
	private NodePurchases() {
	}

	/** Node indices owned in one sub-tree. */
	public static Set<Integer> owned(final Player player, final SubTree tree) {
		Map<String, List<Integer>> all = ((AttachmentTarget) player).getAttached(ModAttachments.PURCHASED);

		if (all == null) {
			return Set.of();
		}

		List<Integer> list = all.get(tree.id());
		return list == null ? Set.of() : list.stream().collect(Collectors.toUnmodifiableSet());
	}

	/**
	 * Whether this node can be bought right now. Reasons are returned rather than
	 * a bare boolean so the screen can say *why* not.
	 */
	public enum Verdict {
		BUYABLE, OWNED, NOT_CONNECTED, NO_POINTS, TREE_FULL, EXCLUSIVE_TAKEN, NEEDS_CAPSTONE;

		public String key() {
			return "node.archetypes.verdict." + this.name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	public static Verdict check(final Player player, final SubTree tree, final int node) {
		Set<Integer> owned = owned(player, tree);

		if (owned.contains(node)) {
			return Verdict.OWNED;
		}

		Constellation shape = tree.constellation();

		// Roots are the bottom row; everything else needs an owned neighbour.
		boolean connected = shape.nodes().get(node).row() == 0;

		for (int[] edge : shape.edges()) {
			if ((edge[0] == node && owned.contains(edge[1]))
					|| (edge[1] == node && owned.contains(edge[0]))) {
				connected = true;
			}
		}

		if (!connected) {
			return Verdict.NOT_CONNECTED;
		}

		// The capstones are a choice, not a pair: owning one locks the other.
		ProtectorNodes.Family family = ProtectorNodes.def(tree, node).family();

		if (family == ProtectorNodes.Family.OMNI_BLOCK
				&& ProtectorNodes.rank(tree, owned, ProtectorNodes.Family.GROUND_SLAM) > 0) {
			return Verdict.EXCLUSIVE_TAKEN;
		}

		if (family == ProtectorNodes.Family.GROUND_SLAM
				&& ProtectorNodes.rank(tree, owned, ProtectorNodes.Family.OMNI_BLOCK) > 0) {
			return Verdict.EXCLUSIVE_TAKEN;
		}

		// Taunt sits past the crown: geometry makes it adjacent to Braced too,
		// but by design it opens only after a capstone is chosen.
		if (family == ProtectorNodes.Family.TAUNT
				&& ProtectorNodes.rank(tree, owned, ProtectorNodes.Family.OMNI_BLOCK) == 0
				&& ProtectorNodes.rank(tree, owned, ProtectorNodes.Family.GROUND_SLAM) == 0) {
			return Verdict.NEEDS_CAPSTONE;
		}

		if (owned.size() >= SkillPoints.MAX_POINTS_PER_SUB_TREE) {
			return Verdict.TREE_FULL;
		}

		if (SkillPoints.available(player) <= 0) {
			return Verdict.NO_POINTS;
		}

		return Verdict.BUYABLE;
	}

	/** Server-side only: commit the purchase. Returns whether it went through. */
	public static boolean buy(final Player player, final SubTree tree, final int node) {
		if (node < 0 || node >= tree.constellation().nodes().size()
				|| check(player, tree, node) != Verdict.BUYABLE) {
			return false;
		}

		AttachmentTarget target = (AttachmentTarget) player;
		Map<String, List<Integer>> all = target.getAttached(ModAttachments.PURCHASED);
		Map<String, List<Integer>> next = all == null ? new HashMap<>() : new HashMap<>(all);
		List<Integer> list = new ArrayList<>(next.getOrDefault(tree.id(), List.of()));
		list.add(node);
		next.put(tree.id(), list);
		target.setAttached(ModAttachments.PURCHASED, next);

		Integer spent = target.getAttached(ModAttachments.SPENT_POINTS);
		target.setAttached(ModAttachments.SPENT_POINTS, (spent == null ? 0 : spent) + 1);
		return true;
	}
}
