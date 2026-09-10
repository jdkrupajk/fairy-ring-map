/*
 * Copyright (c) 2026, Joe Krupa
 * All rights reserved.
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */
package com.fairyringmap;

import lombok.Getter;

/**
 * One map icon inside one inset cell: which icon, and where in the cell it sits.
 * <p>
 * Written by {@code cache-tools}' {@code InsetSheet --emit-icons}, which walks every object in
 * every region the cell covers, follows {@code ObjectDefinition.mapAreaId} to an
 * {@code AreaDefinition} and takes its sprite id. Positions are the icon's <b>centre</b>, in pixels
 * from the cell's top-left corner, at the sheet's own scale.
 * <p>
 * These are coordinates and not pixels on purpose. Baking the icons into the sheet was built first
 * and works; it is a dead end because no config setting can re-cull a pixel. Shipping positions
 * instead is what turns "how many", "how big", "how far apart" and "which kinds" into settings.
 *
 * @see MapIcon
 */
@Getter
public class IconPlacement
{
	/** The game's own sprite id. Resolved to a {@link MapIcon} for ranking; drawn directly. */
	private int sprite;
	private int x;
	private int y;
}
