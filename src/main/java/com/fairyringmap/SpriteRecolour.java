/*
 * Copyright (c) 2026, Joe Krupa
 * All rights reserved.
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */
package com.fairyringmap;

import java.awt.Color;
import java.awt.image.BufferedImage;

/**
 * Recolour a marker sprite to a new hue while reproducing its shading ramp exactly.
 * <p>
 * <b>Why a marker cannot just be given a colour.</b> A marker is a {@code GRAPHIC} widget showing
 * a sprite, and a sprite is pixels. There is no colour property to set — the only way to change
 * one at runtime is to build new pixels and replace the entry in the client's sprite-override
 * table. This class is the pixel half of that.
 * <p>
 * <b>The maths, and why it is exact rather than approximate.</b> The source sprites are single
 * hue: every pixel is one base colour scaled down by a brightness ratio, which is what makes an
 * eleven-pixel circle read as a ring instead of a dot. So
 *
 * <pre>
 *   ratio = max(r, g, b) / max(base)
 *   out   = clamp(target * ratio)
 * </pre>
 *
 * reproduces that ramp against any new base. Nothing is interpolated and nothing is guessed;
 * hand-drawing a second sprite is what gets the ramp subtly wrong.
 * <p>
 * This is a port of {@code cache-tools/tools/recolour-sprite.py}, which generated the shipped
 * PNGs. Two implementations of one formula is a drift risk, so
 * {@code SpriteRecolourTest.matchesTheShippedFavouriteSprite} recolours {@code ring.png} to the
 * favourite red and asserts the result equals {@code ring-fave.png} pixel for pixel. If the two
 * ever disagree that test says so.
 */
final class SpriteRecolour
{
	private SpriteRecolour()
	{
	}

	/**
	 * @param source a single-hue RGBA sprite
	 * @param target the colour its brightest pixel should become
	 * @return a new image; the source is not modified
	 */
	static BufferedImage recolour(BufferedImage source, Color target)
	{
		int base = brightestChannel(source);
		if (base == 0)
		{
			// Nothing opaque to scale against. Returning the source unchanged is better than
			// dividing by zero, and it cannot happen with the shipped sprites.
			return source;
		}

		BufferedImage out = new BufferedImage(
			source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);

		for (int y = 0; y < source.getHeight(); y++)
		{
			for (int x = 0; x < source.getWidth(); x++)
			{
				int argb = source.getRGB(x, y);
				int alpha = argb >>> 24;
				if (alpha == 0)
				{
					continue;
				}

				int r = (argb >> 16) & 0xFF;
				int g = (argb >> 8) & 0xFF;
				int b = argb & 0xFF;
				double ratio = Math.max(r, Math.max(g, b)) / (double) base;

				// The target's alpha is deliberately ignored here. The client's SpritePixels has
				// no alpha channel at all - getPixels() is an int[] where 0 means transparent, so
				// transparency is one bit and getImageSpritePixels silently discards anything
				// partial. Baking alpha in looks right and does nothing. Marker transparency is
				// applied as widget opacity instead; see FairyRingMap.markerOpacityFor.
				out.setRGB(x, y, (alpha << 24)
					| (scale(target.getRed(), ratio) << 16)
					| (scale(target.getGreen(), ratio) << 8)
					| scale(target.getBlue(), ratio));
			}
		}

		return out;
	}

	/**
	 * The brightest single channel across every opaque pixel. This is the sprite's own base
	 * colour: on a single-hue image every other pixel is that value scaled down.
	 * <p>
	 * Opacity is tested as {@code > 128} to match the audit tooling, so a soft edge pixel does not
	 * set the base and flatten the whole ramp.
	 */
	private static int brightestChannel(BufferedImage image)
	{
		int base = 0;
		for (int y = 0; y < image.getHeight(); y++)
		{
			for (int x = 0; x < image.getWidth(); x++)
			{
				int argb = image.getRGB(x, y);
				if ((argb >>> 24) <= 128)
				{
					continue;
				}
				base = Math.max(base, (argb >> 16) & 0xFF);
				base = Math.max(base, (argb >> 8) & 0xFF);
				base = Math.max(base, argb & 0xFF);
			}
		}
		return base;
	}

	private static int scale(int channel, double ratio)
	{
		return Math.min(255, (int) Math.round(channel * ratio));
	}
}
