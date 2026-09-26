/*
 * Copyright (c) 2026, Joe Krupa
 * All rights reserved.
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */
package com.fairyringmap;

import java.awt.Color;
import java.util.EnumSet;
import java.util.Set;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup(FairyRingMapConfig.GROUP)
public interface FairyRingMapConfig extends Config
{
	String GROUP = "fairy-ring-map";

	@ConfigItem(
		keyName = "openOnMap",
		name = "Open on the map",
		description = "Show the map as soon as the travel log opens, instead of the usual list",
		position = 1
	)
	default boolean openOnMap()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showOffMapRings",
		name = "Show off-map destinations",
		description = "Show a row of icons under the map for the dungeons, other realms and the "
			+ "player-owned house, which have no place on a surface map",
		position = 2
	)
	default boolean showOffMapRings()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showHoverInset",
		name = "Show a closer look on hover",
		description = "Show a close-up of whichever destination the cursor is over, at five and a half "
			+ "times the map's own scale. Every destination but the player-owned house",
		position = 3
	)
	default boolean showHoverInset()
	{
		return true;
	}

	@ConfigItem(
		keyName = "hideUnavailable",
		name = "Hide locked destinations",
		description = "Leave rings the account cannot use off the map entirely, rather than "
			+ "drawing them greyed out",
		position = 4
	)
	default boolean hideUnavailable()
	{
		return false;
	}

	@ConfigItem(
		keyName = "codeLabels",
		name = "Show codes on the map",
		description = "Draw each destination's three-letter code beside its marker. Every "
			+ "destination is useful for learning the codes; favourites only keeps the map clear",
		position = 5
	)
	default CodeLabels codeLabels()
	{
		return CodeLabels.OFF;
	}

	@ConfigItem(
		keyName = "codeLabelSize",
		name = "Code size",
		description = "Which of the game's fonts the codes are drawn in. There is no point size to "
			+ "scale, so the choices are the fonts the client ships",
		position = 6
	)
	default CodeLabelSize codeLabelSize()
	{
		return CodeLabelSize.SMALL;
	}

	@Alpha
	@ConfigItem(
		keyName = "codeLabelColour",
		name = "Code colour",
		description = "Colour of a code on the map. White reads over both sea and forest; a code "
			+ "on a locked or searched-out ring dims to grey whatever this is set to",
		position = 7
	)
	default Color codeLabelColour()
	{
		return Color.WHITE;
	}

	@Alpha
	@ConfigItem(
		keyName = "codeLabelFavouriteColour",
		name = "Favourite code colour",
		description = "Colour of a favourited destination's code",
		position = 8
	)
	default Color codeLabelFavouriteColour()
	{
		return Color.WHITE;
	}

	@ConfigItem(
		keyName = "codeLabelsMatchRings",
		name = "Codes match ring colour",
		description = "Draw each code in its own ring's colour instead of the two colours above. "
			+ "A locked or searched-out ring still dims to grey either way",
		position = 9
	)
	default boolean codeLabelsMatchRings()
	{
		return false;
	}

	@Alpha
	@ConfigItem(
		keyName = "ringColour",
		name = "Ring colour",
		description = "Colour of an ordinary unlocked marker. Note the map itself is busy: very "
			+ "dark or low-contrast colours disappear against terrain",
		position = 10
	)
	default Color ringColour()
	{
		return new Color(0x3FD9C8);
	}

	@Alpha
	@ConfigItem(
		keyName = "ringFavouriteColour",
		name = "Favourite ring colour",
		description = "Colour of a favourited marker. The default is the red of the travel log's "
			+ "own favourite heart, so the map matches the list beside it",
		position = 11
	)
	default Color ringFavouriteColour()
	{
		return new Color(0xE92100);
	}

	@Alpha
	@ConfigItem(
		keyName = "ringLockedColour",
		name = "Locked ring colour",
		description = "Colour of a destination this account cannot use yet. Its code dims to grey "
			+ "regardless, so the map still says unreachable if this is set to something bright",
		position = 12
	)
	default Color ringLockedColour()
	{
		return new Color(0x6A6A6A);
	}

	// ---------------------------------------------------------------- the close-up's icons

	String ICONS_SECTION = "icons";

	@ConfigSection(
		name = "Icons in the close-up",
		description = "Which of the game's map icons the hover close-up draws, and how many",
		position = 20,
		closedByDefault = true
	)
	String iconsSection = ICONS_SECTION;

	/**
	 * Which kinds of icon may be drawn.
	 * <p>
	 * A {@code Set} of an enum renders as a multi-select list — real, and used by core's
	 * {@code WorldHopperConfig.regionFilter}. It can exclude but it cannot reorder, because the
	 * order is an enum's declared order and that is compiled in. See {@link MapIcon}.
	 * <p>
	 * Defaults to everything. The priority order and {@link #maxInsetIcons()} already do the work of
	 * keeping a busy cell readable, so an empty-by-default list would only hide the feature.
	 */
	@ConfigItem(
		keyName = "insetIcons",
		name = "Icons to show",
		description = "Which map icons the close-up may draw. Icons are kept in a fixed priority "
			+ "order when there is not room for all of them; this list only decides which are "
			+ "eligible at all",
		position = 14,
		section = ICONS_SECTION
	)
	default Set<MapIcon> insetIcons()
	{
		return EnumSet.allOf(MapIcon.class);
	}

	/**
	 * Five by default, from area rather than taste: a cell is 76 x 58 px and an icon at its native
	 * 15 x 15 covers 225 of the 4,408, so five is about a quarter of the close-up given over to icons.
	 * Forty — the ceiling the pool allows — would be several times the whole cell, which is the solid
	 * mat of overlapping circles that made baking them into the sheet unusable.
	 * <p>
	 * Was twelve when a cell was 132 x 100. The cell shrank twice to fit the Plugin Hub's image
	 * check, and a count tuned to the old area would have been three times too many in the new one.
	 * The data has room to spare either way: the busiest cell offers sixteen.
	 */
	@ConfigItem(
		keyName = "maxInsetIcons",
		name = "Most icons at once",
		description = "How many map icons the close-up may draw for one destination. Higher numbers "
			+ "get busy quickly",
		position = 11,
		section = ICONS_SECTION
	)
	@Range(min = 0, max = FairyRingMap.ICON_POOL)
	default int maxInsetIcons()
	{
		return 5;
	}

	/**
	 * Roughly one icon width, so two icons that survive do not sit on top of each other. Left at 12
	 * as the cell shrank, which culls harder in a smaller view — deliberately, since crowding is what
	 * a small cell suffers from.
	 * <p>
	 * Measured as a square rather than a radius — the same test the sheet renderer used — because
	 * the thing being kept apart is a square sprite.
	 */
	@ConfigItem(
		keyName = "insetIconGap",
		name = "Least space between icons",
		description = "Pixels of clearance between icon centres. An icon that would land nearer "
			+ "than this to one already drawn is dropped, and the higher-priority one wins",
		position = 12,
		section = ICONS_SECTION
	)
	@Range(min = 0, max = 40)
	default int insetIconGap()
	{
		return 12;
	}

	/**
	 * Fifteen is the sprites' native size, and native is the only size guaranteed to be crisp —
	 * these are pixel art and the client resamples when a widget's box is not the sprite's own size.
	 * Offered as a setting at all only because the client turned out to scale them: proved in game
	 * 2026-09-08 by drawing sprite 1453 in a 30 px box and getting a 30 px icon.
	 */
	@ConfigItem(
		keyName = "insetIconSize",
		name = "Icon size",
		description = "How big the close-up draws each map icon, in pixels. 15 is the game's own "
			+ "size and the sharpest; smaller fits more in",
		position = 13,
		section = ICONS_SECTION
	)
	@Range(min = 7, max = 24)
	default int insetIconSize()
	{
		return 15;
	}
}
