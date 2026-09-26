/*
 * Copyright (c) 2026, Joe Krupa
 * All rights reserved.
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */
package com.fairyringmap;

import net.runelite.api.FontID;

/**
 * How big a code label is drawn.
 * <p>
 * <b>Why this is a list and not a number.</b> Widget text is drawn with the game's own bitmap
 * fonts, loaded from the cache by id. There is no point size to scale — a font either exists at a
 * size or it does not, so "bigger" means "a different font", and the choices are the ones the game
 * ships. Offering a slider would imply a continuum that the client cannot render.
 * <p>
 * <b>Each size carries the space it needs.</b> The placement solver reserves a box per label, and
 * that box has to match the font actually used, or labels are laid out for one size and drawn at
 * another. Keeping the two in one enum constant is what stops them drifting: there is no way to
 * change the font without passing the matching box, because they are the same object.
 * <p>
 * The widths are for three uppercase letters plus the shadow, and they are <b>estimates rather
 * than measured glyph metrics</b>. They are deliberately generous — the solver still places all
 * forty-two at 24x12, so an over-estimate costs a slightly roomier map and an under-estimate would
 * cost a collision.
 * <p>
 * {@code FAIRY_SMALL} and {@code FAIRY_LARGE} are not offered despite being thematically perfect:
 * they render the fairy alphabet, so a three-letter code becomes three runes.
 *
 * <h2>Why the list stops at BOLD_12</h2>
 *
 * The map cannot carry a bigger one. Measured 2026-09-26 against the shipped definitions, with the
 * bottom band reserved for the off-map codes, the largest width that still places all forty-two:
 *
 * <pre>
 *   label height 10-11  ->  max width 29
 *   label height 12     ->  max width 25   BOLD_12 at 24x12 just fits
 *   label height 14     ->  max width 22
 *   label height 15     ->  max width 20
 * </pre>
 *
 * {@code VERDANA_13_BOLD} was tried at 30x15 and leaves {@code AIQ}, {@code CIQ}, {@code CLS} and
 * {@code DKP} unplaceable. Widening the solver from eight candidate directions to sixteen fixes
 * 28x14 but still not 30x15, so this is the map being full rather than the solver being weak.
 * A larger font would mean dropping the off-map code row, which buys back only its own height.
 * {@code everyRingStillPlacesAtTheLargestSizeWithTheOffMapBandReserved} walks these constants, so
 * adding one that does not fit fails the build rather than silently losing four labels.
 */
public enum CodeLabelSize
{
	SMALL("Small", FontID.PLAIN_11, 20, 10),
	MEDIUM("Medium", FontID.PLAIN_12, 22, 11),
	BOLD("Bold", FontID.BOLD_12, 24, 12);

	private final String label;
	private final int fontId;
	private final int width;
	private final int height;

	CodeLabelSize(String label, int fontId, int width, int height)
	{
		this.label = label;
		this.fontId = fontId;
		this.width = width;
		this.height = height;
	}

	int getFontId()
	{
		return fontId;
	}

	/** The box the solver reserves, which is also the widget's size. */
	int getWidth()
	{
		return width;
	}

	int getHeight()
	{
		return height;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
