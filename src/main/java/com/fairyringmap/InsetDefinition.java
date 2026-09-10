/*
 * Copyright (c) 2026, Joe Krupa
 * All rights reserved.
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */
package com.fairyringmap;

import lombok.Getter;

/**
 * The geometry of {@code inset-sheet.png}: one pre-rendered close-up per destination, tiled in a
 * grid.
 * <p>
 * Every number here is also read by {@code cache-tools}' {@code InsetSheet}, which is what renders
 * the sheet. That is the point of keeping them in the definitions JSON rather than in either
 * program: the generator, the renderer and the plugin cannot drift into disagreeing about which
 * picture is which, because there is only one copy of the answer.
 * <p>
 * <b>A cell is named, never counted.</b> Each ring carries its own {@code insetCell}; nothing here
 * derives a cell from a ring's position in the array. Reordering the ring list therefore cannot
 * silently re-point all 54 images at the wrong destinations.
 *
 * @see RingDefinition#getInsetCell()
 */
@Getter
public class InsetDefinition
{
	/** Size of one cell, which is also the size of the window the plugin shows. */
	private int cellWidth;
	private int cellHeight;
	/**
	 * Map pixels per world tile inside a cell.
	 * <p>
	 * The plugin does no arithmetic with this — the window shows a cell whole, so the scale is
	 * baked into the picture. It is carried because it is the number that decides what the sheet
	 * looks like, and a sheet whose scale is not recorded beside it cannot be reasoned about later.
	 */
	private int pxPerTile;

	private int cols;
	private int rows;
	private int sheetWidth;
	private int sheetHeight;
}
