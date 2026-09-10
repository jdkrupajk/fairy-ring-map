package com.fairyringmap;

import com.google.gson.Gson;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.imageio.ImageIO;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

/**
 * Checks the shipped data rather than the code that reads it. The definitions and the map sprite
 * are generated from the game cache, so the risk is not a parsing bug — it is regenerating them
 * against a newer cache and quietly losing a ring, or moving the world box without re-rendering
 * the image.
 */
public class FairyRingDefinitionsTest
{
	private FairyRingDefinitions definitions;

	@Before
	public void setUp() throws Exception
	{
		try (InputStream in = getClass().getResourceAsStream("/FairyRingMap/FairyRingDefinitions.json");
			 Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8))
		{
			definitions = new Gson().fromJson(reader, FairyRingDefinitions.class);
		}
	}

	/**
	 * 55 of the 64 possible codes have a ring. The nine that do not are
	 * AIP BIR BJQ CJP CJS CLQ DJQ DJS DKQ.
	 */
	@Test
	public void thereAreFiftyFiveWorkingRings()
	{
		assertEquals(55, definitions.getRings().size());
	}

	@Test
	public void fortyTwoRingsAreOnTheSurfaceMap()
	{
		long onSurface = definitions.getRings().stream().filter(RingDefinition::isOnSurface).count();
		assertEquals(42, onSurface);
	}

	@Test
	public void everyCodeIsWellFormedAndUnique()
	{
		Set<String> seen = new HashSet<>();
		for (RingDefinition ring : definitions.getRings())
		{
			assertTrue(ring.getCode(), LogRow.isCode(ring.getCode()));
			assertTrue("duplicate " + ring.getCode(), seen.add(ring.getCode()));
		}
	}

	@Test
	public void everyRingHasADestinationAndAKnownLogRow()
	{
		for (RingDefinition ring : definitions.getRings())
		{
			assertNotNull(ring.getCode(), ring.getDestination());
			assertFalse(ring.getCode(), ring.getDestination().isEmpty());
			assertNotNull("no log row for " + ring.getCode(), FairyRingRows.componentFor(ring.getCode()));
		}
	}

	@Test
	public void theInterfaceHasARowForAllSixtyFourCodes()
	{
		assertEquals(64, FairyRingRows.all().size());
	}

	/**
	 * The reverse lookup is only usable if it is injective — two codes sharing a component id would
	 * make one of them unreachable, and the hover read would name the wrong destination.
	 */
	@Test
	public void everyRowComponentNamesExactlyOneCode()
	{
		Set<Integer> components = new HashSet<>();
		for (java.util.Map.Entry<String, Integer> row : FairyRingRows.all().entrySet())
		{
			assertTrue("duplicate component for " + row.getKey(), components.add(row.getValue()));
			assertEquals(row.getKey(), FairyRingRows.codeForComponent(row.getValue()));
		}
		assertEquals(64, components.size());
	}

	/** The thing most likely to break on a regeneration: a ring outside the rendered box. */
	@Test
	public void everySurfaceRingProjectsOntoTheSprite()
	{
		MapProjection projection = MapProjection.of(definitions.getMap());
		for (RingDefinition ring : definitions.getRings())
		{
			if (ring.isOnSurface())
			{
				assertTrue(ring.getCode() + " falls off the map",
					projection.contains(ring.getWorldX(), ring.getWorldY()));
			}
		}
	}

	/**
	 * No two markers land so close together that one of them cannot be clicked.
	 * <p>
	 * A marker is 11 px square, so centres closer than that overlap and the one drawn underneath
	 * becomes hard to click. {@code AJS} (penguins) and {@code CIP} (Miscellania) are 13 tiles apart,
	 * which at 0.1804 px per tile is 2.8 px — they were effectively one marker, and the hand nudges
	 * in {@code generate-definitions.py} separate them to 8.5 px.
	 * <p>
	 * <b>This is the guard on those nudges.</b> The definitions file is generated, so a regeneration
	 * that lost the {@code NUDGES} table would silently put the two markers back on top of each
	 * other — nothing about the map would look wrong, one destination would just stop being
	 * clickable.
	 * <p>
	 * The floor is 6 px rather than a marker's full width, because several pairs sit near 8 px for
	 * reasons no offset can fix and the map is usable that way: {@code BIS}/{@code DJP} at 8.1 and
	 * {@code AIR}/{@code DJP} at 8.5 have always been there. Separating {@code AJS}/{@code CIP} to a
	 * clean 11 px needed an 8 px offset — about 44 tiles of lie — to fix one pair the rest of the map
	 * does not treat as special. 6 px is the point where one marker genuinely disappears behind
	 * another; anything above it is tight but real.
	 */
	@Test
	public void everyMarkerPairIsClickablyApart()
	{
		MapProjection projection = MapProjection.of(definitions.getMap());
		List<RingDefinition> surface = new ArrayList<>();
		for (RingDefinition ring : definitions.getRings())
		{
			if (ring.isOnSurface())
			{
				surface.add(ring);
			}
		}

		double worst = Double.MAX_VALUE;
		String worstPair = "";
		for (int i = 0; i < surface.size(); i++)
		{
			for (int j = i + 1; j < surface.size(); j++)
			{
				RingDefinition a = surface.get(i);
				RingDefinition b = surface.get(j);
				double dx = (projection.pixelX(a.getWorldX()) + a.getOffsetX())
					- (projection.pixelX(b.getWorldX()) + b.getOffsetX());
				double dy = (projection.pixelY(a.getWorldY()) + a.getOffsetY())
					- (projection.pixelY(b.getWorldY()) + b.getOffsetY());
				double apart = Math.sqrt(dx * dx + dy * dy);
				if (apart < worst)
				{
					worst = apart;
					worstPair = a.getCode() + "/" + b.getCode();
				}
			}
		}

		assertTrue("closest markers " + worstPair + " are " + Math.round(worst)
			+ " px apart; a marker is 11 px and anything under 6 hides one entirely",
			worst >= 6.0);
	}

	/**
	 * Both halves of the Miscellania nudge are still being applied.
	 * <p>
	 * They move symmetrically and in the direction each destination genuinely lies — {@code AJS}
	 * north-west, {@code CIP} south-east — so neither marker carries the whole error, and 2 px each
	 * is enough to take the pair from 2.8 px to 8.5 px. Checked in both directions because losing
	 * either half halves the separation.
	 */
	@Test
	public void thePenguinsAndMiscellaniaAreBothNudgedApart()
	{
		MapProjection projection = MapProjection.of(definitions.getMap());
		RingDefinition penguins = ringFor("AJS");
		RingDefinition miscellania = ringFor("CIP");

		assertTrue("AJS has lost its hand nudge; regenerate with the NUDGES table",
			penguins.getOffsetX() != 0 || penguins.getOffsetY() != 0);
		assertTrue("CIP has lost its hand nudge; regenerate with the NUDGES table",
			miscellania.getOffsetX() != 0 || miscellania.getOffsetY() != 0);

		double dx = (projection.pixelX(penguins.getWorldX()) + penguins.getOffsetX())
			- (projection.pixelX(miscellania.getWorldX()) + miscellania.getOffsetX());
		double dy = (projection.pixelY(penguins.getWorldY()) + penguins.getOffsetY())
			- (projection.pixelY(miscellania.getWorldY()) + miscellania.getOffsetY());
		double apart = Math.sqrt(dx * dx + dy * dy);

		// 8 px: as far apart as the map's other tight pairs, which is the standard this was held to
		// rather than a marker's full width. See everyMarkerPairIsClickablyApart.
		assertTrue("AJS and CIP are only " + Math.round(apart) + " px apart", apart >= 8.0);
	}

	private RingDefinition ringFor(String code)
	{
		for (RingDefinition ring : definitions.getRings())
		{
			if (code.equals(ring.getCode()))
			{
				return ring;
			}
		}
		throw new AssertionError(code + " missing");
	}

	/** The player-owned house is a working ring whose destination is per-player, so it has none. */
	@Test
	public void thePlayerOwnedHouseHasNoCoordinates()
	{
		RingDefinition poh = definitions.getRings().stream()
			.filter(r -> "DIQ".equals(r.getCode()))
			.findFirst()
			.orElseThrow(() -> new AssertionError("DIQ missing"));
		assertFalse(poh.isOnSurface());
	}

	/**
	 * Every sprite id the component draws resolves to a file that is actually in the jar.
	 * <p>
	 * A widget asked for a sprite the client has never been given draws nothing, silently — the
	 * same symptom as a widget positioned off screen, which is exactly the pair that cost a round
	 * of in-game testing before. Registration is data (SpriteDefinitions.json) and use is code
	 * (the SPRITE_* constants), so nothing but a test holds the two together.
	 */
	@Test
	public void everySpriteTheMapDrawsIsRegisteredAndPresent() throws Exception
	{
		MapSprite[] sprites;
		try (InputStream in = getClass().getResourceAsStream("/FairyRingMap/SpriteDefinitions.json");
			 Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8))
		{
			sprites = new Gson().fromJson(reader, MapSprite[].class);
		}

		Set<Integer> registered = new HashSet<>();
		for (MapSprite sprite : sprites)
		{
			assertNotNull("no file named for sprite " + sprite.getSpriteId(), sprite.getFileName());
			assertNotNull("missing from the jar: " + sprite.getFileName(),
				getClass().getResourceAsStream(sprite.getFileName()));
			assertTrue("duplicate sprite id " + sprite.getSpriteId(),
				registered.add(sprite.getSpriteId()));
		}

		int[] drawn = {
			FairyRingMap.SPRITE_MAP,
			FairyRingMap.SPRITE_RING,
			FairyRingMap.SPRITE_RING_HOVER,
			FairyRingMap.SPRITE_RING_SELECTED,
			FairyRingMap.SPRITE_RING_LOCKED,
			FairyRingMap.SPRITE_RING_FAVE,
			FairyRingMap.SPRITE_INSET_SHEET,
		};
		for (int spriteId : drawn)
		{
			assertTrue("nothing registered for sprite " + spriteId, registered.contains(spriteId));
		}
		assertEquals("an unused sprite is shipped weight", drawn.length, registered.size());
	}

	/**
	 * Every destination but the player-owned house names a cell, and no two name the same one.
	 * <p>
	 * The cell is what points a destination at its picture, so a duplicate is not a crash — it is
	 * two rings quietly showing the same place, which reads as working. {@code DIQ} is the one
	 * legitimate absence: DB table 89 gives it coordgrid 0 because where it goes is per-player.
	 */
	@Test
	public void everyRingButThePlayerOwnedHouseNamesItsOwnInsetCell()
	{
		InsetDefinition inset = definitions.getInset();
		Set<Integer> seen = new HashSet<>();
		for (RingDefinition ring : definitions.getRings())
		{
			Integer cell = ring.getInsetCell();
			if ("DIQ".equals(ring.getCode()))
			{
				assertEquals("the player-owned house cannot have an inset", null, cell);
				continue;
			}
			assertNotNull("no inset cell for " + ring.getCode(), cell);
			assertTrue("inset cell " + cell + " out of the grid for " + ring.getCode(),
				cell >= 0 && cell < inset.getCols() * inset.getRows());
			assertTrue("two rings share inset cell " + cell, seen.add(cell));
		}
		assertEquals(54, seen.size());
	}

	/**
	 * The sheet's declared geometry, the grid it claims, and the PNG actually in the jar all agree.
	 * <p>
	 * This is the one that earns its keep. The plugin slides the sheet by {@code cell % cols} cells
	 * of {@code cellWidth}; regenerate the sheet at a different scale or grid and every one of those
	 * offsets points at the wrong picture, with nothing to see but 54 destinations showing 54 wrong
	 * places. Reading the PNG header rather than trusting the JSON is what makes it a check on the
	 * shipped artifact instead of a check on itself.
	 */
	@Test
	public void theInsetSheetMatchesTheGridItClaims() throws Exception
	{
		InsetDefinition inset = definitions.getInset();
		assertEquals(inset.getCols() * inset.getCellWidth(), inset.getSheetWidth());
		assertEquals(inset.getRows() * inset.getCellHeight(), inset.getSheetHeight());

		try (InputStream in = getClass().getResourceAsStream("/FairyRingMap/inset-sheet.png"))
		{
			assertNotNull("inset-sheet.png is not in the jar", in);
			java.awt.image.BufferedImage sheet = javax.imageio.ImageIO.read(in);
			assertEquals("sheet width", inset.getSheetWidth(), sheet.getWidth());
			assertEquals("sheet height", inset.getSheetHeight(), sheet.getHeight());
		}
	}

	/**
	 * Every destination with a cell also carries its icon positions.
	 * <p>
	 * <b>This one exists to make a specific accident loud.</b> Two programs write
	 * {@code FairyRingDefinitions.json}: {@code tools/generate-definitions.py} writes the rings and
	 * the grid, and {@code InsetSheet --emit-icons} then adds the icon positions. Run the generator
	 * on its own — which is the natural thing to do after a game update — and every icon position is
	 * silently gone, leaving a plugin that draws a perfectly good close-up with nothing on it. There
	 * is no symptom to notice. So the build fails instead.
	 */
	@Test
	public void everyRingWithACellCarriesItsIconPositions()
	{
		for (RingDefinition ring : definitions.getRings())
		{
			if (ring.getInsetCell() == null)
			{
				continue;
			}
			assertNotNull("no insetIcons for " + ring.getCode()
					+ " — was InsetSheet re-run with --emit-icons after the generator?",
				ring.getInsetIcons());
		}
	}

	/**
	 * Every sprite in the data is one this build can name, rank and switch off.
	 * <p>
	 * An unnamed sprite still draws — {@link MapIcon#forSprite(int)} returning null is handled — but
	 * it ranks last and cannot be excluded, so it is a gap rather than a crash. Catching it here is
	 * what turns "a game update added an icon" into a build failure with the sprite id in the
	 * message, rather than a mystery icon nobody can turn off.
	 */
	@Test
	public void everyIconSpriteInTheDataIsNamed()
	{
		for (RingDefinition ring : definitions.getRings())
		{
			if (ring.getInsetIcons() == null)
			{
				continue;
			}
			for (IconPlacement icon : ring.getInsetIcons())
			{
				assertNotNull("MapIcon has no entry for sprite " + icon.getSprite()
					+ ", seen in " + ring.getCode(), MapIcon.forSprite(icon.getSprite()));
			}
		}
	}

	/** No two entries in the priority list claim the same sprite, which would make one unreachable. */
	@Test
	public void everyMapIconClaimsADistinctSprite()
	{
		Set<Integer> seen = new HashSet<>();
		for (MapIcon icon : MapIcon.values())
		{
			assertTrue("two MapIcons share sprite " + icon.getSpriteId(),
				seen.add(icon.getSpriteId()));
		}
		assertEquals("the cache offered 76 distinct icon sprites", 76, seen.size());
	}

	/**
	 * Icon positions are cell-local and fit inside a cell.
	 * <p>
	 * A position outside the cell is what a scale or grid change looks like: the generator wrote
	 * against one cell size and the plugin reads against another. The inset layer clips, so the
	 * symptom in game is icons quietly missing from one edge rather than anything visibly wrong.
	 */
	@Test
	public void everyIconSitsInsideItsCell()
	{
		InsetDefinition inset = definitions.getInset();
		for (RingDefinition ring : definitions.getRings())
		{
			if (ring.getInsetIcons() == null)
			{
				continue;
			}
			assertTrue(ring.getCode() + " carries more icons than the pool can draw",
				ring.getInsetIcons().size() <= FairyRingMap.ICON_POOL);
			for (IconPlacement icon : ring.getInsetIcons())
			{
				assertTrue(ring.getCode() + " icon x " + icon.getX() + " outside the cell",
					icon.getX() >= 0 && icon.getX() < inset.getCellWidth());
				assertTrue(ring.getCode() + " icon y " + icon.getY() + " outside the cell",
					icon.getY() >= 0 && icon.getY() < inset.getCellHeight());
			}
		}
	}

	/**
	 * The Transportation icon at the centre of a cell — the fairy ring itself — was dropped by the
	 * generator, because the plugin draws its own marker over that spot.
	 * <p>
	 * If one survives, it is drawn underneath the centre marker and wastes a slot out of the
	 * player's icon budget on something invisible.
	 */
	@Test
	public void noCellKeepsTheFairyRingsOwnIcon()
	{
		InsetDefinition inset = definitions.getInset();
		int cx = inset.getCellWidth() / 2;
		int cy = inset.getCellHeight() / 2;
		for (RingDefinition ring : definitions.getRings())
		{
			if (ring.getInsetIcons() == null)
			{
				continue;
			}
			for (IconPlacement icon : ring.getInsetIcons())
			{
				boolean atCentre = Math.abs(icon.getX() - cx) <= 5 && Math.abs(icon.getY() - cy) <= 5;
				assertFalse(ring.getCode() + " keeps a Transportation icon under its centre marker",
					icon.getSprite() == MapIcon.TRANSPORTATION.getSpriteId() && atCentre);
			}
		}
	}

	/**
	 * The JSON's sprite dimensions are read against {@code gielinor.png} itself, not against
	 * literals. The projection divides the world box by these numbers, so if they and the image
	 * ever disagree every marker lands in the wrong place — and the disagreement is invisible,
	 * because both halves are individually valid. Asserting literals alone would have passed a
	 * re-render at a new size that edited the JSON to match, which is the one way this can go
	 * wrong in practice.
	 */
	@Test
	public void theMapBoxMatchesTheSpriteThatWasRendered() throws Exception
	{
		MapDefinition map = definitions.getMap();
		assertEquals(508, map.getSpriteWidth());
		assertEquals(312, map.getSpriteHeight());
		assertTrue(map.getWorldX1() > map.getWorldX0());
		assertTrue(map.getWorldY1() > map.getWorldY0());

		BufferedImage sprite;
		try (InputStream in = getClass().getResourceAsStream("/FairyRingMap/gielinor.png"))
		{
			assertNotNull("/FairyRingMap/gielinor.png is not on the classpath", in);
			sprite = ImageIO.read(in);
		}
		assertEquals("gielinor.png is not the width the definitions claim",
			map.getSpriteWidth(), sprite.getWidth());
		assertEquals("gielinor.png is not the height the definitions claim",
			map.getSpriteHeight(), sprite.getHeight());
	}
}
