package com.fairyringmap;

import com.google.gson.Gson;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

/**
 * The code-label solver, tested against the real forty-two rather than against invented points.
 * <p>
 * Synthetic cases prove the rules; only the shipped definitions prove the map is actually
 * labellable, and that is the question the feature turns on. The marker positions here are
 * projected exactly as {@code layoutIcons} projects them, nudges included, so a change to the world
 * box or to {@code NUDGES} moves these tests too.
 */
public class LabelPlacementTest
{
	private static final int LABEL_WIDTH = 20;
	private static final int LABEL_HEIGHT = 10;
	private static final int MARKER_RADIUS = 5;

	private FairyRingDefinitions definitions;
	private List<LabelPlacement.Anchor> surface;

	@Before
	public void setUp() throws Exception
	{
		try (InputStream in = getClass().getResourceAsStream("/FairyRingMap/FairyRingDefinitions.json");
			 Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8))
		{
			definitions = new Gson().fromJson(reader, FairyRingDefinitions.class);
		}

		MapDefinition map = definitions.getMap();
		MapProjection projection = new MapProjection(
			map.getWorldX0(), map.getWorldY0(), map.getWorldX1(), map.getWorldY1(),
			map.getSpriteWidth(), map.getSpriteHeight());

		surface = new ArrayList<>();
		for (RingDefinition ring : definitions.getRings())
		{
			if (!ring.isOnSurface())
			{
				continue;
			}
			surface.add(new LabelPlacement.Anchor(
				ring.getCode(),
				projection.pixelX(ring.getWorldX()) + ring.getOffsetX(),
				projection.pixelY(ring.getWorldY()) + ring.getOffsetY()));
		}
	}

	private Map<String, LabelPlacement> solveSurface(List<LabelPlacement.Anchor> wanted)
	{
		MapDefinition map = definitions.getMap();
		return LabelPlacement.solve(wanted, surface,
			LABEL_WIDTH, LABEL_HEIGHT, MARKER_RADIUS,
			0, 0, map.getSpriteWidth(), map.getSpriteHeight());
	}

	/**
	 * The feature's premise. If this fails the map cannot carry every code, and the config's
	 * "every destination" mode is silently showing fewer than it claims.
	 */
	@Test
	public void everySurfaceRingGetsALabel()
	{
		// Pinned before the real assertion, which compares two counts and would therefore pass
		// on an empty list. Forty-two is what FairyRingDefinitionsTest independently expects.
		assertEquals("the surface set is not what the definitions hold", 42, surface.size());

		Map<String, LabelPlacement> placed = solveSurface(surface);
		assertEquals("some surface rings could not be labelled: " + missing(placed),
			surface.size(), placed.size());
	}

	/**
	 * The failure a single fixed offset produces, and the reason the solver exists: nine labels
	 * landing on a different ring's marker. A label over a marker hides a click target.
	 */
	@Test
	public void noLabelCoversAnotherRingsMarker()
	{
		Map<String, LabelPlacement> placed = solveSurface(surface);
		for (LabelPlacement.Anchor anchor : surface)
		{
			int[] box = boxFor(anchor, placed.get(anchor.getKey()));
			for (LabelPlacement.Anchor other : surface)
			{
				if (other == anchor)
				{
					continue;
				}
				int[] marker = boxFor(other, null);
				assertFalse(anchor.getKey() + "'s label covers " + other.getKey() + "'s marker",
					intersects(box, marker, MARKER_RADIUS));
			}
		}
	}

	@Test
	public void noTwoLabelsOverlap()
	{
		Map<String, LabelPlacement> placed = solveSurface(surface);
		List<LabelPlacement.Anchor> all = new ArrayList<>(surface);
		for (int i = 0; i < all.size(); i++)
		{
			for (int j = i + 1; j < all.size(); j++)
			{
				int[] a = boxFor(all.get(i), placed.get(all.get(i).getKey()));
				int[] b = boxFor(all.get(j), placed.get(all.get(j).getKey()));
				assertFalse(all.get(i).getKey() + " and " + all.get(j).getKey() + " labels overlap",
					intersects(a, b, 0));
			}
		}
	}

	/**
	 * The worst case the config can produce: the largest font, with the bottom band reserved for
	 * the thirteen off-map codes. Both settings eat the same space, so if anything fails to place
	 * it fails here. Sizes come from {@link CodeLabelSize} rather than being retyped, so adding a
	 * larger font to that enum puts it under test automatically.
	 */
	@Test
	public void everyRingStillPlacesAtTheLargestSizeWithTheOffMapBandReserved()
	{
		MapDefinition map = definitions.getMap();
		for (CodeLabelSize size : CodeLabelSize.values())
		{
			int reserved = size.getHeight() + 1;
			Map<String, LabelPlacement> placed = LabelPlacement.solve(
				surface, surface,
				size.getWidth(), size.getHeight(), MARKER_RADIUS,
				0, 0, map.getSpriteWidth(), map.getSpriteHeight() - reserved);

			assertEquals("at size " + size + " these could not be placed: " + missing(placed),
				surface.size(), placed.size());
		}
	}

	@Test
	public void noLabelLeavesTheMap()
	{
		MapDefinition map = definitions.getMap();
		Map<String, LabelPlacement> placed = solveSurface(surface);
		for (LabelPlacement.Anchor anchor : surface)
		{
			int[] box = boxFor(anchor, placed.get(anchor.getKey()));
			assertTrue(anchor.getKey() + " label runs off the map: " + box[0] + "," + box[1],
				box[0] >= 0 && box[1] >= 0
					&& box[2] <= map.getSpriteWidth() && box[3] <= map.getSpriteHeight());
		}
	}

	/**
	 * Favourites-only mode labels a handful of rings while every marker still has to be avoided.
	 * Ten is the most favourites the game allows, so this is the worst case that mode can reach.
	 */
	@Test
	public void favouritesOnlyStillAvoidsEveryUnlabelledMarker()
	{
		List<LabelPlacement.Anchor> wanted = surface.subList(0, 10);
		Map<String, LabelPlacement> placed = solveSurface(wanted);
		assertEquals(wanted.size(), placed.size());

		for (LabelPlacement.Anchor anchor : wanted)
		{
			int[] box = boxFor(anchor, placed.get(anchor.getKey()));
			for (LabelPlacement.Anchor other : surface)
			{
				if (other == anchor)
				{
					continue;
				}
				assertFalse(anchor.getKey() + "'s label covers " + other.getKey() + "'s marker",
					intersects(box, boxFor(other, null), MARKER_RADIUS));
			}
		}
	}

	/**
	 * The solve must not depend on the order the caller happens to hand anchors over, or the same
	 * map would label differently between runs and every assertion above would be a coin toss.
	 * That is what the tie-break on the key buys.
	 */
	@Test
	public void theResultDoesNotDependOnInputOrder()
	{
		Map<String, LabelPlacement> forwards = solveSurface(surface);

		List<LabelPlacement.Anchor> shuffled = new ArrayList<>(surface);
		Collections.reverse(shuffled);
		Map<String, LabelPlacement> backwards = solveSurface(shuffled);

		assertEquals(forwards.keySet(), backwards.keySet());
		for (String code : forwards.keySet())
		{
			assertEquals(code + " moved when the input order changed",
				forwards.get(code).getOffsetX(), backwards.get(code).getOffsetX());
			assertEquals(code + " moved when the input order changed",
				forwards.get(code).getOffsetY(), backwards.get(code).getOffsetY());
		}
	}

	/** With nothing in the way a label takes the first candidate, which is below the marker. */
	@Test
	public void anIsolatedLabelGoesBelowItsMarker()
	{
		LabelPlacement.Anchor lone = new LabelPlacement.Anchor("AAA", 100, 100);
		Map<String, LabelPlacement> placed = LabelPlacement.solve(
			Collections.singletonList(lone), Collections.singletonList(lone),
			LABEL_WIDTH, LABEL_HEIGHT, MARKER_RADIUS, 0, 0, 200, 200);

		LabelPlacement spot = placed.get("AAA");
		assertNotNull(spot);
		assertEquals(0, spot.getOffsetX());
		assertTrue("expected the label below the marker", spot.getOffsetY() > 0);
	}

	/** A ring boxed in on every side is left unlabelled rather than drawn over something. */
	@Test
	public void aRingWithNowhereToGoIsLeftUnlabelled()
	{
		List<LabelPlacement.Anchor> all = new ArrayList<>();
		LabelPlacement.Anchor centre = new LabelPlacement.Anchor("MID", 100, 100);
		all.add(centre);
		for (int dx = -1; dx <= 1; dx++)
		{
			for (int dy = -1; dy <= 1; dy++)
			{
				if (dx != 0 || dy != 0)
				{
					all.add(new LabelPlacement.Anchor("N" + dx + dy, 100 + dx * 9, 100 + dy * 9));
				}
			}
		}

		Map<String, LabelPlacement> placed = LabelPlacement.solve(
			Collections.singletonList(centre), all,
			LABEL_WIDTH, LABEL_HEIGHT, MARKER_RADIUS, 0, 0, 200, 200);

		assertFalse("a boxed-in ring should get no label, not an overlapping one",
			placed.containsKey("MID"));
	}

	private int[] boxFor(LabelPlacement.Anchor anchor, LabelPlacement spot)
	{
		if (spot == null)
		{
			return new int[]{anchor.getX(), anchor.getY(), anchor.getX(), anchor.getY()};
		}
		int left = anchor.getX() + spot.getOffsetX() - LABEL_WIDTH / 2;
		int top = anchor.getY() + spot.getOffsetY() - LABEL_HEIGHT / 2;
		return new int[]{left, top, left + LABEL_WIDTH, top + LABEL_HEIGHT};
	}

	private String missing(Map<String, LabelPlacement> placed)
	{
		List<String> gaps = new ArrayList<>();
		for (LabelPlacement.Anchor anchor : surface)
		{
			if (!placed.containsKey(anchor.getKey()))
			{
				gaps.add(anchor.getKey());
			}
		}
		return gaps.toString();
	}

	/** {@code grow} inflates the second box, which turns a marker centre into its bounding square. */
	private static boolean intersects(int[] a, int[] b, int grow)
	{
		return a[0] < b[2] + grow && b[0] - grow < a[2]
			&& a[1] < b[3] + grow && b[1] - grow < a[3];
	}
}
