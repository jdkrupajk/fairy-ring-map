/*
 * Copyright (c) 2026, Joe Krupa
 * All rights reserved.
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */
package com.fairyringmap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Where a destination's code label goes, and the solver that decides.
 * <p>
 * <b>Why this is not just "draw the code under the marker".</b> One fixed offset fails on this map.
 * Measured against the shipped definitions at a 17x9 label: seven pairs of labels overlap, and nine
 * labels land on top of a <em>different</em> ring's marker. The second is the real damage — a label
 * covering a marker hides a click target, which is worse than two labels touching. The cause is the
 * same crowding that forced the {@code AJS}/{@code CIP} nudge: {@code BIS}/{@code DJP} sit 7.7 px
 * apart, {@code BKR}/{@code CKS} 8.1, {@code AIR}/{@code DJP} 8.2. A seventeen-pixel label does not
 * fit in an eight-pixel gap, and no single choice of offset can make it.
 * <p>
 * <b>What works.</b> Give each label eight candidate positions and let it take the first that is
 * clear. With the shipped forty-two that places every one with no overlap at all, and still does at
 * 24x12, so there is real margin against the font being wider than assumed.
 * <p>
 * <b>Most crowded first is the part that matters.</b> Labels are placed in ascending order of
 * distance to their nearest neighbour, so the rings with the fewest options choose before the ones
 * with a whole coastline to themselves. Placing in definition order instead lets a ring in open
 * water take the one position its crowded neighbour needed. This is the standard point-feature
 * labelling heuristic, and it is greedy rather than optimal: it can fail where an exhaustive search
 * would succeed. A ring it cannot place is left unlabelled rather than drawn overlapping, which is
 * the honest failure - a missing label is a gap, a colliding one is a lie about where a ring is.
 * <p>
 * Pure on purpose. It takes coordinates and returns coordinates, touches no {@code Widget} and no
 * client type, and is therefore unit tested for real rather than through mocks - the same reason
 * {@link MapProjection} and {@link LogRow} are their own classes.
 */
final class LabelPlacement
{
	/**
	 * Candidate offsets from a marker's centre to a label's centre, in preference order: below,
	 * above, right, left, then the four diagonals.
	 * <p>
	 * Below first because that is where a reader expects a place name, and because the map's
	 * densest clusters run east-west, which leaves vertical room more often than horizontal.
	 * The diagonals are last because they read as belonging to no marker in particular; they exist
	 * so a crowded ring gets something rather than nothing.
	 */
	private static final int[][] DIRECTIONS = {
		{0, 1}, {0, -1}, {1, 0}, {-1, 0}, {1, 1}, {-1, 1}, {1, -1}, {-1, -1},
	};

	private final int offsetX;
	private final int offsetY;

	private LabelPlacement(int offsetX, int offsetY)
	{
		this.offsetX = offsetX;
		this.offsetY = offsetY;
	}

	/** Offset from the marker's centre to the label's centre. */
	int getOffsetX()
	{
		return offsetX;
	}

	int getOffsetY()
	{
		return offsetY;
	}

	/**
	 * A marker's centre, and the key a caller recognises it by.
	 * <p>
	 * Kept as a type of its own rather than parallel arrays because a caller that mixes up two
	 * {@code int[]} arguments gets labels on the wrong rings and no error.
	 */
	static final class Anchor
	{
		private final String key;
		private final int x;
		private final int y;

		Anchor(String key, int x, int y)
		{
			this.key = key;
			this.x = x;
			this.y = y;
		}

		String getKey()
		{
			return key;
		}

		int getX()
		{
			return x;
		}

		int getY()
		{
			return y;
		}
	}

	/**
	 * Place a label beside each of {@code labelled}, avoiding every marker in {@code obstacles}.
	 * <p>
	 * {@code obstacles} is every marker on the map, not only the labelled ones: a label must not
	 * cover an unlabelled ring's marker either, and in favourites-only mode almost every marker is
	 * unlabelled. Passing the labelled set twice is correct and expected.
	 *
	 * @return an offset per key, in placement order. A key is absent when no candidate was clear.
	 */
	static Map<String, LabelPlacement> solve(
		List<Anchor> labelled,
		List<Anchor> obstacles,
		int labelWidth,
		int labelHeight,
		int markerRadius,
		int minX,
		int minY,
		int maxX,
		int maxY)
	{
		return solve(labelled, obstacles, labelWidth, labelHeight, markerRadius,
			minX, minY, maxX, maxY, Collections.emptyMap());
	}

	/**
	 * As above, but {@code preferred} names a side to try first for particular keys.
	 * <p>
	 * This exists because the solver optimises for "no collision" and a human optimises for "that
	 * label obviously belongs to that ring". Both are right; they just answer different questions,
	 * and where they disagree the human wins. A preference is a <em>first choice</em>, not a
	 * constraint: if the named side is occupied the ring falls back through the normal order
	 * rather than going unlabelled, because a label on the wrong side beats no label at all.
	 */
	static Map<String, LabelPlacement> solve(
		List<Anchor> labelled,
		List<Anchor> obstacles,
		int labelWidth,
		int labelHeight,
		int markerRadius,
		int minX,
		int minY,
		int maxX,
		int maxY,
		Map<String, int[]> preferred)
	{
		List<Anchor> order = new ArrayList<>(labelled);
		// Ties are broken on the key so the result does not depend on the input's order. Without
		// it two rings the same distance from their neighbours could swap places between runs,
		// which would make any test of this a coin toss.
		order.sort(Comparator
			.comparingLong((Anchor a) -> nearestSquared(a, obstacles))
			.thenComparing(Anchor::getKey));

		Map<String, LabelPlacement> placed = new LinkedHashMap<>();
		List<int[]> taken = new ArrayList<>();

		for (Anchor anchor : order)
		{
			for (int[] direction : candidatesFor(anchor.key, preferred))
			{
				int offsetX = direction[0] * (markerRadius + labelWidth / 2 + 1);
				int offsetY = direction[1] * (markerRadius + labelHeight / 2 + 1);

				int left = anchor.x + offsetX - labelWidth / 2;
				int top = anchor.y + offsetY - labelHeight / 2;
				int[] box = {left, top, left + labelWidth, top + labelHeight};

				if (box[0] < minX || box[1] < minY || box[2] > maxX || box[3] > maxY)
				{
					continue;
				}
				if (coversAnyMarker(box, obstacles, anchor, markerRadius) || overlapsAny(box, taken))
				{
					continue;
				}

				placed.put(anchor.key, new LabelPlacement(offsetX, offsetY));
				taken.add(box);
				break;
			}
		}

		return placed;
	}

	/**
	 * The candidate order for one anchor: its preferred side first, then the standard order with
	 * that side removed so it is not retried.
	 */
	private static List<int[]> candidatesFor(String key, Map<String, int[]> preferred)
	{
		int[] first = preferred.get(key);
		List<int[]> candidates = new ArrayList<>(DIRECTIONS.length);
		if (first != null)
		{
			candidates.add(first);
		}
		for (int[] direction : DIRECTIONS)
		{
			if (first == null || direction[0] != first[0] || direction[1] != first[1])
			{
				candidates.add(direction);
			}
		}
		return candidates;
	}

	/**
	 * Squared distance to the closest other marker. Squared because this only ever orders anchors
	 * against each other, and a square root cannot change an ordering.
	 */
	private static long nearestSquared(Anchor anchor, List<Anchor> obstacles)
	{
		long nearest = Long.MAX_VALUE;
		for (Anchor other : obstacles)
		{
			if (other == anchor)
			{
				continue;
			}
			long dx = other.x - anchor.x;
			long dy = other.y - anchor.y;
			nearest = Math.min(nearest, dx * dx + dy * dy);
		}
		return nearest;
	}

	/** A marker is treated as its bounding square, which is what the sprite actually occupies. */
	private static boolean coversAnyMarker(int[] box, List<Anchor> obstacles, Anchor self, int radius)
	{
		for (Anchor other : obstacles)
		{
			if (other == self)
			{
				continue;
			}
			if (intersects(box, new int[]{other.x - radius, other.y - radius,
				other.x + radius, other.y + radius}))
			{
				return true;
			}
		}
		return false;
	}

	private static boolean overlapsAny(int[] box, List<int[]> taken)
	{
		for (int[] other : taken)
		{
			if (intersects(box, other))
			{
				return true;
			}
		}
		return false;
	}

	/** Half-open on every edge, so two boxes that merely share a border do not count as touching. */
	private static boolean intersects(int[] a, int[] b)
	{
		return a[0] < b[2] && b[0] < a[2] && a[1] < b[3] && b[1] < a[3];
	}
}
