/*
 * Copyright (c) 2026, Joe Krupa
 * All rights reserved.
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */
package com.fairyringmap;

import lombok.Getter;
import net.runelite.client.game.SpriteOverride;

/**
 * One entry of {@code /FairyRingMap/SpriteDefinitions.json}.
 * <p>
 * A widget can only draw a sprite the client knows about, so the plugin's PNGs are registered into
 * the client's sprite table under ids of its own. The ids are negative by convention: the game's
 * own sprites are all non-negative, so nothing can collide no matter how many sprites Jagex adds.
 */
@Getter
public class MapSprite implements SpriteOverride
{
	private int spriteId;
	private String fileName;
}
