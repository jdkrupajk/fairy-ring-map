/*
 * Copyright (c) 2026, Joe Krupa
 * All rights reserved.
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */
package com.fairyringmap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.cluescrolls.ClueScrollService;
import net.runelite.client.plugins.cluescrolls.clues.ClueScroll;
import net.runelite.client.plugins.cluescrolls.clues.LocationClueScroll;
import net.runelite.client.plugins.cluescrolls.clues.LocationsClueScroll;

/**
 * Where the active clue step says to go, read from RuneLite's own Clue Scroll plugin.
 *
 * <h3>Why through the service and not the plugin</h3>
 *
 * A plugin that declares {@code @PluginDependency} on another gets only what that plugin chooses to
 * expose. On RuneLite master (2026-09-17, "require plugins to explicitly provide their services")
 * that became the rule rather than a convention: the Clue Scroll plugin exposes exactly one thing,
 * {@link ClueScrollService}, and the plugin instance itself is no longer injectable. Reaching the
 * instance some other way would work today and would be going around the boundary the client's
 * developers just built. This is the only class that touches a Clue Scroll type, so if the
 * dependency ever has to go it goes in one place.
 *
 * <h3>Why {@code null} is passed as the plugin</h3>
 *
 * {@code getLocation(ClueScrollPlugin)} still asks for the plugin, which a service consumer cannot
 * supply. Nearly every clue ignores it. Two variants read it - an anagram whose location depends
 * on Dragon Slayer II progress, and the Eluned cipher (Prifddinas or Lletya) - and those throw.
 * They are skipped: an unplaced clue is a missing highlight, a guessed one is a wrong one.
 */
@Slf4j
@Singleton
class ClueLocations
{
	private final ClueScrollService clueScrollService;

	@Inject
	private ClueLocations(ClueScrollService clueScrollService)
	{
		this.clueScrollService = clueScrollService;
	}

	/**
	 * Every place the active clue step could be. Empty when there is no clue, when the Clue Scroll
	 * plugin is turned off - its shutdown clears the clue - or when the step has no fixed place.
	 * More than one for a coordinate clue with a mirror, a three-step cryptic, or a hot-cold clue
	 * that has not been narrowed to one spot.
	 */
	List<WorldPoint> current()
	{
		ClueScroll clue = clueScrollService.getClue();
		WorldPoint[] points;
		try
		{
			if (clue instanceof LocationsClueScroll)
			{
				points = ((LocationsClueScroll) clue).getLocations(null);
			}
			else if (clue instanceof LocationClueScroll)
			{
				points = ((LocationClueScroll) clue).getLocations(null);
			}
			else
			{
				return Collections.emptyList();
			}
		}
		catch (NullPointerException e)
		{
			log.debug("clue {} needs the Clue Scroll plugin itself to place it; skipped",
				clue.getClass().getSimpleName());
			return Collections.emptyList();
		}

		List<WorldPoint> found = new ArrayList<>();
		if (points != null)
		{
			for (WorldPoint point : points)
			{
				if (point != null)
				{
					found.add(point);
				}
			}
		}
		return found;
	}
}
