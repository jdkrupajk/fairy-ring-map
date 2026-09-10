/*
 * Copyright (c) 2026, Joe Krupa
 * All rights reserved.
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */
package com.fairyringmap;

import com.google.inject.Provides;
import javax.inject.Inject;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@PluginDescriptor(
	name = "Fairy Ring Map",
	description = "Pick a fairy ring destination off a map of Gielinor instead of by its code",
	tags = {"fairy", "ring", "teleport", "travel", "map", "transport"}
)
public class FairyRingMapPlugin extends Plugin
{
	private static final String SPRITE_DEFINITIONS = "/FairyRingMap/SpriteDefinitions.json";

	@Inject
	private DefinitionLoader definitionLoader;

	@Inject
	private EventBus eventBus;

	@Inject
	private SpriteManager spriteManager;

	@Inject
	private ClientThread clientThread;

	@Inject
	private FairyRingMap map;

	private MapSprite[] sprites;

	@Provides
	FairyRingMapConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(FairyRingMapConfig.class);
	}

	@Override
	protected void startUp()
	{
		// The plugin's PNGs have to be in the client's sprite table before any widget can draw
		// them. addSpriteOverrides hops to the client thread itself, so this is safe from here.
		sprites = definitionLoader.load(MapSprite[].class, SPRITE_DEFINITIONS);
		spriteManager.addSpriteOverrides(sprites);

		eventBus.register(map);
	}

	@Override
	protected void shutDown()
	{
		eventBus.unregister(map);

		if (sprites != null)
		{
			spriteManager.removeSpriteOverrides(sprites);
			sprites = null;
		}

		// startUp and shutDown run on the AWT thread that toggled the plugin, not the client
		// thread, so anything that touches widgets has to be handed over rather than called here.
		clientThread.invokeLater(map::reset);
	}
}
