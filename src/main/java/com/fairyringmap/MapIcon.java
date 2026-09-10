/*
 * Copyright (c) 2026, Joe Krupa
 * All rights reserved.
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */
package com.fairyringmap;

import java.util.HashMap;
import java.util.Map;
import lombok.Getter;

/**
 * The game's map icons, in the order they are worth keeping.
 *
 * <h2>Declared order is the priority list</h2>
 *
 * An enum constant's {@code ordinal()} is its position in this file, fixed at compile time. That
 * makes it the cheapest possible priority key — ranking two icons is an int comparison — and it is
 * also the reason the order <b>cannot be a setting</b>. A user who wants a different order would
 * need an ordered-list config type, and RuneLite has none: its config panel renders checkboxes,
 * spinners, text fields, colours, combo boxes, keybinds and a multi-select list, and nothing that
 * reorders. A drag-and-drop list is buildable — core ships
 * {@code net.runelite.client.ui.components.DragAndDropReorderPane} — but only inside a
 * {@code PluginPanel} with a {@code NavigationButton} of its own, which is a sidebar panel this
 * plugin does not otherwise need and a reviewer would have to look at by hand.
 *
 * <p>So the order is compiled in and the setting is <b>exclusion only</b>: the player can turn an
 * icon off, not move it up. That is a real limitation and it is a deliberate trade for staying
 * inside automated review.
 *
 * <h2>Order governs two different things</h2>
 *
 * <b>Survival</b> — when a cell offers more icons than the settings allow, the ones nearest the top
 * of this list are kept. <b>Draw order</b> — an icon nearer the top is drawn <em>later</em>, so it
 * lands on top of the ones below it. Those pull in opposite directions in the code, which is why
 * the accepted list is reversed when it is handed to the widgets: widgets paint in child order, so
 * last drawn is nearest the front.
 *
 * <h2>Keyed on sprite, not on area</h2>
 *
 * About 150 map area ids collapse onto these 76 sprites — areas 58, 964, 990, 1011 and 1020 are all
 * sprite 1504. The area id is an identifier; the sprite is the thing a person recognises and
 * therefore the thing a setting should be about. The cache cannot name any of them:
 * {@code AreaDefinition.getName()} is null for all 76 and {@code getCategory()} is the area id plus
 * a constant. The names below were recovered by pixel-matching the cache's sprites against the OSRS
 * Wiki's copies — see {@code cache-tools/src/IconNames.java} and
 * {@code cache-tools/data/map-icon-names.tsv}.
 *
 * @see RingDefinition#getInsetIcons()
 */
@Getter
public enum MapIcon
{
	// ---- Navigation. Where you can go from here, and the things you always want to find.
	BANK(1453, "Bank"),
	ALTAR(1467, "Altar"),
	TRANSPORTATION(1504, "Transportation"),
	DOCKING_POINT(7285, "Docking point"),
	HOUSE_PORTAL(1505, "House portal"),
	DEATHS_OFFICE(2216, "Death's Office"),

	// ---- Access. Ways out of the square that are not roads.
	DUNGEON_MAP_LINK(1534, "Dungeon map link"),
	DUNGEON(1460, "Dungeon"),
	AGILITY_SHORTCUT(1518, "Agility shortcut"),
	QUEST_START(1454, "Quest start"),

	// ---- Activities. Content you would travel here to do.
	SLAYER_MASTER(1499, "Slayer Master"),
	TASK_MASTER(1522, "Task Master"),
	MINIGAME(1486, "Minigame"),
	POLL_BOOTH(1512, "Poll booth"),
	CLAN_HUB(2961, "Clan Hub"),
	VALE_TOTEM_SITE(4944, "Vale totem site"),
	BARRACUDA_TRIAL(4946, "Barracuda Trial"),
	SALVAGING_SPOT(4947, "Salvaging spot"),
	SALVAGING_STATION(7282, "Salvaging station"),
	PORT_TASK_BOARD(7281, "Port task board"),
	SHIPWRIGHT(7284, "Shipwright"),

	// ---- Skilling. Fixed resources and the machines that go with them.
	ANVIL(1458, "Anvil"),
	FURNACE(1457, "Furnace"),
	COOKING_RANGE(1488, "Cooking range"),
	FISHING_SPOT(1474, "Fishing spot"),
	MINING_SITE(1456, "Mining site"),
	FARMING_PATCH(1501, "Farming patch"),
	RARE_TREES(1482, "Rare trees"),
	SAWMILL(1516, "Sawmill"),
	WINDMILL(1491, "Windmill"),
	SANDPIT(1521, "Sandpit"),
	SPINNING_WHEEL(1483, "Spinning wheel"),
	POTTERY_WHEEL(1490, "Pottery wheel"),
	LOOM(1507, "Loom"),
	AGILITY_TRAINING(1497, "Agility training"),
	HUNTER_TRAINING(1511, "Hunter training"),
	THIEVING_ACTIVITY(4942, "Thieving activity"),

	// ---- Shops.
	GENERAL_STORE(1448, "General store"),
	SWORD_SHOP(1449, "Sword shop"),
	MAGIC_SHOP(1450, "Magic shop"),
	AXE_SHOP(1451, "Axe shop"),
	STAFF_SHOP(1461, "Staff shop"),
	PLATEBODY_SHOP(1462, "Platebody shop"),
	SCIMITAR_SHOP(1464, "Scimitar shop"),
	ARCHERY_SHOP(1465, "Archery shop"),
	GEM_SHOP(1470, "Gem shop"),
	CRAFTING_SHOP(1471, "Crafting shop"),
	CANDLE_SHOP(1472, "Candle shop"),
	FISHING_SHOP(1473, "Fishing shop"),
	FOOD_SHOP(1484, "Food shop"),
	COOKERY_SHOP(1485, "Cookery shop"),
	SILVER_SHOP(1494, "Silver shop"),
	SPICE_SHOP(1496, "Spice shop"),
	VEGETABLE_SHOP(1498, "Vegetable shop"),
	FARMING_SHOP(1506, "Farming shop"),
	HUNTER_SHOP(1513, "Hunter shop"),
	PET_SHOP(1523, "Pet shop"),
	FORESTRY_SHOP(4941, "Forestry shop"),
	ESTATE_AGENT(1515, "Estate Agent"),
	/**
	 * Moved down out of Skilling, 2026-09-08, at Joe's call.
	 * <p>
	 * It is the commonest icon on the whole sheet — 71 of the 629 emitted positions, half again as
	 * many as the runner-up — so where it ranks decides more of what a busy cell looks like than any
	 * other single choice here. A fairy ring inset answers "where am I landing", and a fountain is
	 * rarely the answer. Placed at the bottom of Shops rather than in Niche so it still survives in
	 * a sparse cell, where it costs nothing and is occasionally what you wanted.
	 */
	WATER_SOURCE(1487, "Water source"),

	// ---- Niche. Single-purpose traders and set dressing.
	SILK_TRADER(1477, "Silk trader"),
	FUR_TRADER(1495, "Fur trader"),
	WINE_TRADER(1685, "Wine trader"),
	DYE_TRADER(1691, "Dye trader"),
	HOLIDAY_ITEM_TRADER(1684, "Holiday item trader"),
	BOUNTY_HUNTER_TRADER(1524, "Bounty Hunter trader"),
	JUNK_CHECKER(1686, "Junk checker"),
	HERBALIST(1468, "Herbalist"),
	TANNERY(1481, "Tannery"),
	BREWERY(1508, "Brewery"),
	DAIRY_CHURN(1509, "Dairy churn"),
	DAIRY_COW(1690, "Dairy cow"),
	GARDEN_SUPPLIER(1692, "Garden supplier"),
	PUB(1479, "Pub"),
	HUNTER_TUTOR(1672, "Hunter Tutor"),
	BOND_TUTOR(1693, "Bond Tutor");

	/**
	 * The game's own sprite id, drawn straight out of the client's sprite table.
	 * <p>
	 * Nothing is bundled. A widget's {@code spriteId} resolves against the same table
	 * {@code cache-tools} reads out of cache index 8 — proved in game 2026-09-08 by drawing sprites
	 * 1453 and 1504 on a bare widget — so the icons stay whatever the current game says they are and
	 * survive a graphics update for free. The alternative was a 76-icon strip PNG under negative ids
	 * of our own, which would have frozen them at the version we shipped.
	 */
	private final int spriteId;

	private final String displayName;

	MapIcon(int spriteId, String displayName)
	{
		this.spriteId = spriteId;
		this.displayName = displayName;
	}

	/**
	 * Shown in the config's multi-select list.
	 * <p>
	 * Overridden rather than left to RuneLite's own title-casing of the constant name, because the
	 * casing carries meaning here: "Slayer Master" and "Bounty Hunter trader" are proper nouns in
	 * the game and read as typos when flattened.
	 */
	@Override
	public String toString()
	{
		return displayName;
	}

	private static final Map<Integer, MapIcon> BY_SPRITE = new HashMap<>();

	static
	{
		for (MapIcon icon : values())
		{
			BY_SPRITE.put(icon.spriteId, icon);
		}
	}

	/**
	 * The icon a sprite id stands for, or {@code null} if this build has never heard of it.
	 * <p>
	 * Null is reachable only if the game adds a map icon after this file was written, since the
	 * positions in the definitions JSON are generated from the same cache the client runs on. An
	 * unknown icon is deliberately <b>drawn anyway and ranked last</b> rather than dropped: drawn,
	 * so the gap is visible and gets fixed; last, so it cannot crowd out an icon the player asked
	 * for.
	 */
	static MapIcon forSprite(int spriteId)
	{
		return BY_SPRITE.get(spriteId);
	}
}
