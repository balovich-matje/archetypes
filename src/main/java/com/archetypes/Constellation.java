package com.archetypes;

import java.util.ArrayList;
import java.util.List;

/**
 * A sub-tree's node layout, authored as an ASCII grid so the shape stays
 * readable — and editable — in source. {@code '#'} is a node, anything else is
 * empty.
 *
 * <p>Grids are written top-down as they appear on screen, but parsed so that
 * {@link Node#row()} 0 is the <em>bottom</em> row: trees grow upward from roots
 * at the bottom. Edges join nodes adjacent in the grid (8-connectivity), so a
 * chain of touching cells becomes a chain of connected nodes and the outline of
 * a shape becomes a ring.
 */
public final class Constellation {
	/** A node's cell. Column 0 is the left of the grid, row 0 the bottom. */
	public record Node(int col, int row) {
	}

	private final int width;
	private final int height;
	private final List<Node> nodes;
	private final List<int[]> edges;
	private final List<int[]> decorativeEdges;

	private Constellation(final int width, final int height, final List<Node> nodes,
			final List<int[]> edges, final List<int[]> decorativeEdges) {
		this.width = width;
		this.height = height;
		this.nodes = nodes;
		this.edges = edges;
		this.decorativeEdges = decorativeEdges;
	}

	public static Constellation of(final String... grid) {
		int height = grid.length;
		int width = 0;

		for (String row : grid) {
			width = Math.max(width, row.length());
		}

		List<Node> nodes = new ArrayList<>();

		for (int row = 0; row < height; row++) {
			for (int col = 0; col < grid[row].length(); col++) {
				if (grid[row].charAt(col) == '#') {
					nodes.add(new Node(col, height - 1 - row));
				}
			}
		}

		List<int[]> edges = new ArrayList<>();

		for (int a = 0; a < nodes.size(); a++) {
			for (int b = a + 1; b < nodes.size(); b++) {
				int dx = Math.abs(nodes.get(a).col() - nodes.get(b).col());
				int dy = Math.abs(nodes.get(a).row() - nodes.get(b).row());

				if (Math.max(dx, dy) == 1) {
					edges.add(new int[] { a, b });
				}
			}
		}

		return new Constellation(width, height, List.copyOf(nodes), List.copyOf(edges), List.of());
	}

	/**
	 * The same constellation with a purely cosmetic line between two cells —
	 * closing a shape's outline across a gap so the silhouette reads finished.
	 * It renders like any connection but does <em>not</em> count for purchase
	 * adjacency: the outline is decoration, the paths are the gameplay.
	 */
	public Constellation withDecorativeEdge(final int col1, final int row1, final int col2, final int row2) {
		int a = this.indexOf(col1, row1);
		int b = this.indexOf(col2, row2);
		List<int[]> extended = new ArrayList<>(this.decorativeEdges);
		extended.add(new int[] { a, b });
		return new Constellation(this.width, this.height, this.nodes, this.edges, List.copyOf(extended));
	}

	private int indexOf(final int col, final int row) {
		for (int i = 0; i < this.nodes.size(); i++) {
			if (this.nodes.get(i).col() == col && this.nodes.get(i).row() == row) {
				return i;
			}
		}

		throw new IllegalArgumentException("No node at (" + col + ", " + row + ")");
	}

	/** Grid columns; the shape spans 0..width-1. */
	public int width() {
		return this.width;
	}

	/** Grid rows; the shape spans 0..height-1, row 0 at the bottom. */
	public int height() {
		return this.height;
	}

	public List<Node> nodes() {
		return this.nodes;
	}

	/** Each entry is a {@code {from, to}} pair of indices into {@link #nodes()}. */
	public List<int[]> edges() {
		return this.edges;
	}

	/** Cosmetic lines only — drawn like edges, ignored for adjacency. */
	public List<int[]> decorativeEdges() {
		return this.decorativeEdges;
	}
}
