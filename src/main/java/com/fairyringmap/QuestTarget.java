/*
 * Copyright (c) 2026, Joe Krupa
 * All rights reserved.
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */
package com.fairyringmap;

import java.util.Map;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameState;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.PluginMessage;

/**
 * The last place another plugin asked Shortest Path to route to - in practice Quest Helper's current
 * step, when its "Use 'Shortest Path' plugin" option is on.
 *
 * <h3>Why a broadcast and not Quest Helper itself</h3>
 *
 * Quest Helper is a Hub plugin, so it cannot be a {@code @PluginDependency}, and the world map points
 * it draws are package-private to core. What it does do is post its step's target on the shared
 * event bus, addressed to Shortest Path: {@code PluginMessage("shortestpath", "path", {start,
 * target})} on a new step and {@code "clear"} when the step goes. Any plugin can hear that. The
 * payload does not say who sent it, so this is honestly "the last Shortest Path target", whoever set
 * it. This is the only class that knows the protocol, so if it changes it changes here.
 *
 * <h3>Why it lives for the plugin's whole life</h3>
 *
 * The message is sent when a step starts, which is almost never while the travel log is open. So
 * this listens from start-up to shut-down and holds the answer, and the map reads it whenever it
 * draws. The target is a session fact, like {@link RingState}'s account facts, not interface state.
 *
 * <h3>Why the field is volatile</h3>
 *
 * Measured in game 2026-10-11: {@code path} arrives on the client thread, but {@code clear} arrives
 * on the Swing thread when the quest is stopped from Quest Helper's sidebar. Two writers on two
 * threads, one reader on the client thread. {@code volatile} makes each write visible to the reader
 * at once and keeps the writes in the order they happened. Handing the write to the client thread
 * instead would queue a sidebar {@code clear} behind a newer {@code path} and let it wipe that.
 */
@Slf4j
@Singleton
class QuestTarget
{
	static final String NAMESPACE = "shortestpath";
	static final String PATH = "path";
	static final String CLEAR = "clear";
	static final String TARGET = "target";

	private volatile WorldPoint target;

	@Subscribe
	public void onPluginMessage(PluginMessage message)
	{
		apply(message.getNamespace(), message.getName(), message.getData());
	}

	/**
	 * A target from one login is wrong for the next, which may be another account, and nothing
	 * guarantees a {@code clear} on the way out. The next step started after login sends a new one.
	 */
	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			forget();
		}
	}

	/**
	 * Split from the subscriber so the parse can be tested without building events. Anything that is
	 * not exactly the two messages above is ignored rather than clearing: another plugin's malformed
	 * message should not take away a good target.
	 */
	void apply(String namespace, String name, Map<String, Object> data)
	{
		if (!NAMESPACE.equals(namespace))
		{
			return;
		}

		if (CLEAR.equals(name))
		{
			target = null;
			log.debug("shortest path target cleared");
		}
		else if (PATH.equals(name) && data != null)
		{
			Object point = data.get(TARGET);
			if (point instanceof WorldPoint)
			{
				target = (WorldPoint) point;
				log.debug("shortest path target {}", target);
			}
		}
	}

	/** Null when there is no step to mark. */
	WorldPoint current()
	{
		return target;
	}

	/**
	 * Called on shut-down. Messages sent while the plugin is off are never seen, so whatever was held
	 * would be stale by the time it is switched back on - and no X is better than a wrong one.
	 */
	void forget()
	{
		target = null;
	}
}
