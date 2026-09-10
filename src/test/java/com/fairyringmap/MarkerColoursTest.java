package com.fairyringmap;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import javax.imageio.ImageIO;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * Checks that the five marker sprites stay tellable apart, including for the ~8% of men with a
 * colour-vision deficiency.
 * <p>
 * This is shipped data, like the definitions JSON — the colours live only in the pixels of five
 * PNGs, produced by {@code cache-tools/tools/recolour-sprite.py}. Nothing in the source states
 * them, so a recolour that destroys a distinction produces no compile error, no test failure and
 * no visible symptom for anyone with normal colour vision. That is exactly what happened once:
 * {@code ring-fave} at {@code #FF4D6D} and {@code ring-locked} at {@code #6A6A6A} were 17 apart
 * under protanopia, and they mean opposite things — a favourite is reachable, a locked ring is
 * not.
 * <p>
 * The favourite is now {@code #E92100}, the red of the travel log's own favourited heart. What
 * rescued the pair was **saturation, not brightness**: a dark saturated red stays 174 from grey
 * under deuteranopia, where the pale rose greyed out and a brightened rose instead collapsed onto
 * the plain teal marker. Worst pair across the palette is now 171.
 * <p>
 * The measurement mirrors {@code cache-tools/tools/marker-audit.py}, which reports the full
 * matrix. This test only fails the build.
 */
public class MarkerColoursTest
{
	/**
	 * Below this two markers read as the same colour at eleven pixels. It is a rule of thumb
	 * rather than a standard — redmean has no perceptual threshold defined for it — so it is set
	 * where a distinction is plainly gone, not where it is merely reduced. The shipped sprites
	 * clear it by a factor of two on their worst pair.
	 */
	private static final double MIN_SEPARATION = 40;

	private static final String[] MARKERS = {
		"ring", "ring-hover", "ring-selected", "ring-locked", "ring-fave"
	};

	/**
	 * Brettel/Vienot-style simulation matrices, applied in sRGB. Approximate — an exact model
	 * would linearise first — but the question here is "have two colours collapsed onto each
	 * other", and that survives the approximation.
	 */
	private static final double[][][] DEFICIENCIES = {
		{{1, 0, 0}, {0, 1, 0}, {0, 0, 1}},
		{{0.367322, 0.860646, -0.227968}, {0.280085, 0.672501, 0.047413}, {-0.011820, 0.042940, 0.968881}},
		{{0.152286, 1.052583, -0.204868}, {0.114503, 0.786281, 0.099216}, {-0.003882, -0.048116, 1.051998}},
		{{1.255528, -0.076749, -0.178779}, {-0.078411, 0.930809, 0.147602}, {0.004733, 0.691367, 0.303900}},
	};

	private static final String[] DEFICIENCY_NAMES = {
		"normal vision", "deuteranopia", "protanopia", "tritanopia"
	};

	@Test
	public void noTwoMarkerStatesCollapseUnderAnyColourVisionDeficiency() throws Exception
	{
		int[][] colours = new int[MARKERS.length][];
		for (int i = 0; i < MARKERS.length; i++)
		{
			colours[i] = brightestOpaquePixel(MARKERS[i]);
		}

		for (int a = 0; a < MARKERS.length; a++)
		{
			for (int b = a + 1; b < MARKERS.length; b++)
			{
				for (int d = 0; d < DEFICIENCIES.length; d++)
				{
					double separation = redmean(
						simulate(colours[a], DEFICIENCIES[d]),
						simulate(colours[b], DEFICIENCIES[d]));

					assertTrue(String.format(
						"%s and %s are %.0f apart under %s, below the %.0f at which they read as "
							+ "the same colour. Re-run cache-tools/tools/marker-audit.py for the "
							+ "full matrix.",
						MARKERS[a], MARKERS[b], separation, DEFICIENCY_NAMES[d], MIN_SEPARATION),
						separation >= MIN_SEPARATION);
				}
			}
		}
	}

	/**
	 * What the eye reads off an eleven-pixel sprite is its brightest pixel: every other opaque
	 * pixel is that colour scaled down by the shared shading ramp, so the brightest one is the
	 * marker's colour and the mean is that colour darkened by however much of the sprite is
	 * shadow. Same rule as the audit tool, so the two agree.
	 */
	private int[] brightestOpaquePixel(String name) throws Exception
	{
		BufferedImage image;
		try (InputStream in = getClass().getResourceAsStream("/FairyRingMap/" + name + ".png"))
		{
			assertNotNull("/FairyRingMap/" + name + ".png is not on the classpath", in);
			image = ImageIO.read(in);
		}

		int[] brightest = null;
		int best = -1;
		for (int y = 0; y < image.getHeight(); y++)
		{
			for (int x = 0; x < image.getWidth(); x++)
			{
				int argb = image.getRGB(x, y);
				if ((argb >>> 24) <= 128)
				{
					continue;
				}
				int r = (argb >> 16) & 0xFF;
				int g = (argb >> 8) & 0xFF;
				int bl = argb & 0xFF;
				if (r + g + bl > best)
				{
					best = r + g + bl;
					brightest = new int[]{r, g, bl};
				}
			}
		}

		assertNotNull(name + ".png has no opaque pixels", brightest);
		return brightest;
	}

	private static int[] simulate(int[] rgb, double[][] matrix)
	{
		int[] out = new int[3];
		for (int i = 0; i < 3; i++)
		{
			double v = matrix[i][0] * rgb[0] + matrix[i][1] * rgb[1] + matrix[i][2] * rgb[2];
			out[i] = Math.max(0, Math.min(255, (int) Math.round(v)));
		}
		return out;
	}

	/**
	 * Redmean: a cheap approximation of perceived colour difference that weights the red and blue
	 * terms by where in the red range the pair sits. Chosen over plain Euclidean RGB because the
	 * eye's sensitivity is not uniform across the cube, and over CIEDE2000 because this only has
	 * to answer "are these two obviously different", which it does without a colour library.
	 */
	private static double redmean(int[] a, int[] b)
	{
		double rm = (a[0] + b[0]) / 2.0;
		double dr = a[0] - b[0];
		double dg = a[1] - b[1];
		double db = a[2] - b[2];
		return Math.sqrt((2 + rm / 256) * dr * dr + 4 * dg * dg + (2 + (255 - rm) / 256) * db * db);
	}
}
