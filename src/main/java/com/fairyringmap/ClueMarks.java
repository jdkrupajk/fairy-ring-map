/*
 * Copyright (c) 2026, Joe Krupa
 * All rights reserved.
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */
package com.fairyringmap;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Where the clue step's X marks go on the map sprite.
 * <p>
 * Pure, like {@link MapProjection} and {@link LabelPlacement}: world tiles in, sprite pixels out,
 * no RuneLite type, so every rule below has a test. {@link ClueLocations} is what reads the clue;
 * this only decides what of it can be drawn.
 * <p>
 * Four rules, in order:
 * <ol>
 *   <li><b>Dungeons fold onto the surface.</b> The game stores most underground areas 6400 tiles
 *       north of the ground above them, so subtracting that puts the X over the entrance region
 *       rather than nowhere.</li>
 *   <li><b>Off the sprite is not drawn.</b> What still lands outside the map box after folding -
 *       Prifddinas, the other realms - has no honest place on it. A missing X is a gap; an X
 *       clamped to the map's edge is a lie about where the clue is.</li>
 *   <li><b>Points closer than an X merge into one.</b> A hot-cold clue can leave dozens of
 *       candidate spots, many a few tiles apart; at 0.18 px/tile they would stack into a yellow
 *       smudge. The first in a fixed order keeps its place, so the result does not depend on the
 *       order the clue handed them over.</li>
 *   <li><b>More marks than the pool holds draws none.</b> Only a hot-cold clue early on gets there,
 *       and showing the first few of its candidates would read as the whole answer.</li>
 * </ol>
 */
final class ClueMarks
{
	/**
	 * How far north of the surface the game keeps most underground areas. Taverley Dungeon's
	 * entrance region, for one, is the same x and y + 6400.
	 */
	static final int UNDERGROUND_OFFSET = 6400;

	private ClueMarks()
	{
	}

	/**
	 * @param tiles       every place the clue step could be, in world tiles, in any order
	 * @param projection  the map's world-to-sprite transform
	 * @param mergeWithin two marks whose centres are closer than this on both axes become one
	 * @param limit       the most marks there are widgets for
	 * @return sprite-pixel centres, at most {@code limit}; empty when there are more than that
	 */
	static List<Point> place(List<Point> tiles, MapProjection projection, int mergeWithin, int limit)
	{
		List<Point> onMap = new ArrayList<>();
		for (Point tile : tiles)
		{
			int y = tile.y >= UNDERGROUND_OFFSET ? tile.y - UNDERGROUND_OFFSET : tile.y;
			if (projection.contains(tile.x, y))
			{
				onMap.add(new Point(projection.pixelX(tile.x), projection.pixelY(y)));
			}
		}

		// North-west first, so which of two close candidates survives is fixed by where they are.
		onMap.sort(Comparator.<Point>comparingInt(p -> p.y).thenComparingInt(p -> p.x));

		List<Point> marks = new ArrayList<>();
		for (Point candidate : onMap)
		{
			if (!nearAny(marks, candidate, mergeWithin))
			{
				marks.add(candidate);
			}
		}

		return marks.size() > limit ? Collections.emptyList() : marks;
	}

	private static boolean nearAny(List<Point> marks, Point candidate, int within)
	{
		for (Point mark : marks)
		{
			if (Math.abs(mark.x - candidate.x) < within && Math.abs(mark.y - candidate.y) < within)
			{
				return true;
			}
		}
		return false;
	}
}
