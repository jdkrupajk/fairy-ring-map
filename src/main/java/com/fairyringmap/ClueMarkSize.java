/*
 * Copyright (c) 2026, Joe Krupa
 * All rights reserved.
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */
package com.fairyringmap;

import net.runelite.api.FontID;

/**
 * How big the clue step's X is drawn.
 * <p>
 * The same trade as {@link CodeLabelSize}: widget text is drawn in the game's bitmap fonts, so a
 * bigger X is a different font, not a scale factor. Each constant carries its font, the box the
 * widget is given, and how close two clue spots may be before they share one X, so the three
 * cannot drift apart.
 * <p>
 * The glyph sizes are <b>measured</b>, not estimated: a game font is a sprite archive with the
 * font's id, one frame per character code, so frame 88 is the X. Read out of the cache 2026-09-27:
 *
 * <pre>
 *   BOLD_12            6 x 10
 *   VERDANA_13_BOLD    8 x 9     not offered: smaller than BOLD_12 despite the name
 *   BARBARIAN         11 x 11
 *   QUILL_MEDIUM      16 x 18
 *   QUILL_CAPS_LARGE  31 x 34
 * </pre>
 *
 * The box is the frame's canvas, which is what the client centres the glyph within. The merge
 * distance is the glyph's width plus two pixels, so no two X's ever touch.
 * <p>
 * <b>A released constant is permanent.</b> Removing one resets anyone who chose it, silently; see
 * the project notes on config enums.
 */
public enum ClueMarkSize
{
	SMALL("Small", FontID.BOLD_12, 14, 16, 8),
	MEDIUM("Medium", FontID.BARBARIAN, 19, 30, 13),
	LARGE("Large", FontID.QUILL_MEDIUM, 24, 31, 18),
	HUGE("Huge", FontID.QUILL_CAPS_LARGE, 54, 61, 33);

	private final String label;
	private final int fontId;
	private final int width;
	private final int height;
	private final int mergeWithin;

	ClueMarkSize(String label, int fontId, int width, int height, int mergeWithin)
	{
		this.label = label;
		this.fontId = fontId;
		this.width = width;
		this.height = height;
		this.mergeWithin = mergeWithin;
	}

	int getFontId()
	{
		return fontId;
	}

	int getWidth()
	{
		return width;
	}

	int getHeight()
	{
		return height;
	}

	/** Two clue spots closer than this on both axes, in map pixels, draw as one X. */
	int getMergeWithin()
	{
		return mergeWithin;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
