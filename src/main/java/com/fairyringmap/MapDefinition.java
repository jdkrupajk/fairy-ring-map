/*
 * Copyright (c) 2026, Joe Krupa
 * All rights reserved.
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */
package com.fairyringmap;

import lombok.Getter;

/**
 * The map sprite's size, and the box of world tiles it covers.
 * <p>
 * Both halves have to travel together: a pixel position is only meaningful relative to the world
 * box that produced it. Keeping them in the definitions JSON rather than in code means
 * regenerating the sprite at a different scale is a data change, not a code change.
 */
@Getter
public class MapDefinition
{
	private int spriteWidth;
	private int spriteHeight;

	/** West edge, inclusive. */
	private int worldX0;
	/** South edge, inclusive. */
	private int worldY0;
	/** East edge, exclusive. */
	private int worldX1;
	/** North edge, exclusive. */
	private int worldY1;
}
