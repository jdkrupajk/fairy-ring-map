package com.fairyringmap;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class FairyRingMapPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(FairyRingMapPlugin.class);
		RuneLite.main(args);
	}
}
