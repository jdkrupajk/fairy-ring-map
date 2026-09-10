/*
 * Copyright (c) 2026, Joe Krupa
 * All rights reserved.
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */
package com.fairyringmap;

/**
 * Turns a world tile into a pixel on the map sprite.
 * <p>
 * The transform is a plain affine scale on each axis, with y inverted: world y grows north, image
 * y grows south. There is no rotation and no projection distortion because the sprite was rendered
 * straight out of the game cache over the same axis-aligned tile box it is being mapped back onto
 * — see {@code cache-tools} {@code MapDump}. That is the whole reason to render our own map rather
 * than borrow one: an image whose exact world box we chose needs no fitting.
 * <p>
 * Kept free of any RuneLite type so it can be unit tested on its own.
 */
public final class MapProjection
{
	private final int worldX0;
	private final int worldY1;
	private final int worldWidth;
	private final int worldHeight;
	private final int pixelWidth;
	private final int pixelHeight;

	public MapProjection(int worldX0, int worldY0, int worldX1, int worldY1, int pixelWidth, int pixelHeight)
	{
		if (worldX1 <= worldX0 || worldY1 <= worldY0)
		{
			throw new IllegalArgumentException("world box must have positive extent");
		}
		if (pixelWidth <= 0 || pixelHeight <= 0)
		{
			throw new IllegalArgumentException("sprite must have positive extent");
		}

		this.worldX0 = worldX0;
		this.worldY1 = worldY1;
		this.worldWidth = worldX1 - worldX0;
		this.worldHeight = worldY1 - worldY0;
		this.pixelWidth = pixelWidth;
		this.pixelHeight = pixelHeight;
	}

	public static MapProjection of(MapDefinition definition)
	{
		return new MapProjection(
			definition.getWorldX0(), definition.getWorldY0(),
			definition.getWorldX1(), definition.getWorldY1(),
			definition.getSpriteWidth(), definition.getSpriteHeight());
	}

	/** Pixel column, 0 at the west edge. Not clamped — see {@link #contains}. */
	public int pixelX(int worldX)
	{
		return (int) Math.round((worldX - worldX0) * (double) pixelWidth / worldWidth);
	}

	/** Pixel row, 0 at the <em>north</em> edge, because image y runs the other way to world y. */
	public int pixelY(int worldY)
	{
		return (int) Math.round((worldY1 - worldY) * (double) pixelHeight / worldHeight);
	}

	/**
	 * Whether a tile projects to a pixel that is actually on the sprite.
	 * <p>
	 * Stated in terms of the result rather than the world box on purpose: the two axes are
	 * half-open at opposite ends because of the y flip, and asking the projection is less
	 * error-prone than restating that asymmetry at every call site.
	 */
	public boolean contains(int worldX, int worldY)
	{
		int px = pixelX(worldX);
		int py = pixelY(worldY);
		return px >= 0 && px < pixelWidth && py >= 0 && py < pixelHeight;
	}
}
