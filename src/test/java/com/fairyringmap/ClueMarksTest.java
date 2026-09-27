package com.fairyringmap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.awt.Point;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

/**
 * The map box is the shipped one, and the first case is the clue location read out of the Clue
 * Scroll plugin in game on 2026-09-27, between Catherby and Taverley.
 */
public class ClueMarksTest
{
	private static final MapProjection MAP = new MapProjection(1088, 2304, 3904, 4032, 508, 312);
	private static final int MERGE = 8;
	private static final int POOL = 32;

	private static List<Point> place(Point... tiles)
	{
		return ClueMarks.place(Arrays.asList(tiles), MAP, MERGE, POOL);
	}

	@Test
	public void aSurfaceTileLandsWhereTheProjectionPutsIt()
	{
		assertEquals(Collections.singletonList(new Point(320, 109)), place(new Point(2860, 3431)));
	}

	@Test
	public void anUndergroundTileFoldsOntoTheGroundAboveIt()
	{
		// Taverley Dungeon, and the surface directly above it.
		assertEquals(place(new Point(2884, 3398)), place(new Point(2884, 3398 + 6400)));
	}

	@Test
	public void aTileStillOffTheMapAfterFoldingIsNotDrawn()
	{
		// Prifddinas: north of the surface box, and not far enough north to be a folded dungeon.
		assertTrue(place(new Point(3264, 6062)).isEmpty());
	}

	@Test
	public void pointsCloserThanAMarkBecomeOne()
	{
		// Twenty tiles is about 3.6 px at this scale.
		assertEquals(1, place(new Point(2860, 3431), new Point(2880, 3431)).size());
	}

	@Test
	public void pointsFartherApartThanAMarkStaySeparate()
	{
		// Catherby-Taverley and Lumbridge.
		assertEquals(2, place(new Point(2860, 3431), new Point(3222, 3218)).size());
	}

	@Test
	public void whichOfTwoClosePointsSurvivesDoesNotDependOnTheirOrder()
	{
		Point a = new Point(2860, 3431);
		Point b = new Point(2870, 3440);
		assertEquals(place(a, b), place(b, a));
	}

	@Test
	public void exactlyAPoolsWorthIsAllDrawn()
	{
		assertEquals(POOL, ClueMarks.place(spreadOut(POOL), MAP, MERGE, POOL).size());
	}

	@Test
	public void moreMarksThanThePoolDrawsNoneRatherThanSome()
	{
		assertTrue(ClueMarks.place(spreadOut(POOL + 1), MAP, MERGE, POOL).isEmpty());
	}

	@Test
	public void noClueDrawsNothing()
	{
		assertTrue(place().isEmpty());
	}

	/** {@code n} tiles along one row, 80 tiles (14 px) apart, so none of them merge. */
	private static List<Point> spreadOut(int n)
	{
		List<Point> tiles = new ArrayList<>();
		for (int i = 0; i < n; i++)
		{
			tiles.add(new Point(1100 + i * 80, 3200));
		}
		return tiles;
	}
}
