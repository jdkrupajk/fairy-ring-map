package com.fairyringmap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * The projection is the one piece of maths in the plugin, so it is pinned hard: the world box and
 * sprite size below are the ones the shipped map was rendered with, and the landmark cases are
 * real fairy ring coordinates read out of the game's own database table.
 */
public class MapProjectionTest
{
	private static final int X0 = 1088;
	private static final int Y0 = 2304;
	private static final int X1 = 3904;
	private static final int Y1 = 4032;
	private static final int W = 508;
	private static final int H = 312;

	private final MapProjection projection = new MapProjection(X0, Y0, X1, Y1, W, H);

	@Test
	public void westEdgeIsColumnZero()
	{
		assertEquals(0, projection.pixelX(X0));
	}

	@Test
	public void eastEdgeIsTheFullWidth()
	{
		assertEquals(W, projection.pixelX(X1));
	}

	@Test
	public void northEdgeIsRowZeroBecauseImageYRunsTheOtherWay()
	{
		assertEquals(0, projection.pixelY(Y1));
	}

	@Test
	public void southEdgeIsTheFullHeight()
	{
		assertEquals(H, projection.pixelY(Y0));
	}

	@Test
	public void yDecreasesAsTheWorldGoesNorth()
	{
		assertTrue(projection.pixelY(3500) < projection.pixelY(3100));
	}

	@Test
	public void xIncreasesAsTheWorldGoesEast()
	{
		assertTrue(projection.pixelX(3100) > projection.pixelX(1500));
	}

	@Test
	public void centreOfTheBoxIsTheCentreOfTheSprite()
	{
		assertEquals(W / 2, projection.pixelX((X0 + X1) / 2));
		assertEquals(H / 2, projection.pixelY((Y0 + Y1) / 2));
	}

	/** DKR, Edgeville. Roughly two thirds east and just above the middle. */
	@Test
	public void edgevilleLandsWhereItShould()
	{
		int x = projection.pixelX(3129);
		int y = projection.pixelY(3496);
		assertEquals(368, x);
		assertEquals(97, y);
		assertTrue(projection.contains(3129, 3496));
	}

	/** CLR, Ape Atoll: far south, and well inside the sprite. */
	@Test
	public void apeAtollLandsInTheSouth()
	{
		assertTrue(projection.pixelY(2740) > H / 2);
		assertTrue(projection.contains(2743, 2740));
	}

	/** AIS, Auburn Valley in Varlamore: the western extreme of the surface rings. */
	@Test
	public void varlamoreIsInsideTheWesternEdge()
	{
		int x = projection.pixelX(1429);
		assertTrue(x > 0);
		assertTrue(x < W / 4);
		assertTrue(projection.contains(1429, 3324));
	}

	/** Zanaris sits in the same coordinate space thousands of tiles north of anything drawable. */
	@Test
	public void offSurfaceCoordinatesAreRejected()
	{
		assertFalse(projection.contains(2412, 4434));
		assertFalse(projection.contains(2926, 10455));
	}

	@Test
	public void tilesOutsideTheBoxAreRejected()
	{
		assertFalse(projection.contains(X0 - 20, 3000));
		assertFalse(projection.contains(X1 + 20, 3000));
		assertFalse(projection.contains(2500, Y0 - 20));
		assertFalse(projection.contains(2500, Y1 + 20));
	}

	/**
	 * Recorded rather than fixed: at roughly six and a half tiles to the pixel, a tile just outside
	 * the box rounds back onto the edge pixel and counts as inside. Harmless here — the box is the
	 * whole surface world and the nearest ring is two hundred tiles inside it — but worth knowing
	 * before reusing the class.
	 */
	@Test
	public void aTileJustOutsideRoundsOntoTheEdge()
	{
		assertTrue(projection.contains(X0 - 1, 3000));
		assertEquals(0, projection.pixelX(X0 - 1));
	}

	@Test(expected = IllegalArgumentException.class)
	public void anInvertedWorldBoxIsRejected()
	{
		new MapProjection(X1, Y0, X0, Y1, W, H);
	}

	@Test(expected = IllegalArgumentException.class)
	public void aZeroSizedSpriteIsRejected()
	{
		new MapProjection(X0, Y0, X1, Y1, 0, H);
	}
}
