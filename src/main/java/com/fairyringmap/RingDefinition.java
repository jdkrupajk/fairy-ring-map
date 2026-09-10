/*
 * Copyright (c) 2026, Joe Krupa
 * All rights reserved.
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */
package com.fairyringmap;

import java.util.List;
import lombok.Getter;

/**
 * One fairy ring destination.
 * <p>
 * Codes and destination names come from RuneLite core's {@code FairyRing} enum (BSD-2); the
 * coordinates come from the game cache's {@code fairyring} database table.
 * Neither was typed by hand — see {@code cache-tools/tools/generate-definitions.py}.
 * <p>
 * {@link #onSurface} is false for the thirteen destinations that are not drawable on a surface
 * map: dungeons, the "other realms", and the player-owned house. They share the world coordinate
 * space but sit thousands of tiles north of anything the map covers (Zanaris is at y 4434), so
 * they carry no coordinates at all rather than a plausible-looking lie.
 */
@Getter
public class RingDefinition
{
	private String code;
	private String destination;
	private boolean onSurface;

	private int worldX;
	private int worldY;

	/**
	 * Optional pixel nudge applied after projection, for rings too close together to click apart.
	 * Miscellania (CIP) and the penguins beside it (AJS) are eighteen tiles apart and collide at
	 * any scale the panel can hold.
	 */
	private int offsetX;
	private int offsetY;

	/**
	 * Which cell of {@code inset-sheet.png} holds this destination's close-up, or {@code null} if it
	 * has none.
	 * <p>
	 * <b>Boxed on purpose.</b> An {@code int} would be 0 for a ring the JSON gives no cell, and 0 is
	 * a real cell — {@code AIQ}'s. The one destination with no cell would therefore silently show
	 * Mudskipper Point rather than nothing, which is the worst of the three possible outcomes: it
	 * looks like it works. {@code Integer} makes "absent" a value the code has to handle.
	 * <p>
	 * Exactly one destination is absent: {@code DIQ}, the player-owned house. DB table 89 gives it
	 * coordgrid 0 because where it goes is per-player, so there is nothing to render. That is the
	 * correct answer rather than a gap to fill — 54 insets, not 55.
	 * <p>
	 * The other twelve off-map destinations <em>do</em> have cells. They are real places in the same
	 * coordinate space, just thousands of tiles north of anything the surface map covers, so a sheet
	 * rendered per-destination reaches them where a single world sprite cannot.
	 */
	private Integer insetCell;

	/**
	 * Every map icon inside this destination's inset cell, nearest the centre first.
	 * <p>
	 * Nearest-first is the order the generator wrote and the order the cap was applied in, so it is
	 * also a usable tie-break at display time: two icons of the same kind competing for the same
	 * space resolve towards the ring. Empty for the four cells that genuinely contain no icons —
	 * the Abyssal Area, Yu'biusk, Gorak's Plane and north of the Arceuus Library — and null for
	 * {@code DIQ}, which has no cell at all.
	 */
	private List<IconPlacement> insetIcons;
}
