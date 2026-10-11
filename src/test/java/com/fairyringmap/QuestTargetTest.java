/*
 * Copyright (c) 2026, Joe Krupa
 * All rights reserved.
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */
package com.fairyringmap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

/**
 * The points are ones Quest Helper actually sent in game on 2026-10-11, including an instance
 * coordinate as {@code start}, which is why only {@code target} is read.
 */
public class QuestTargetTest
{
	private static final WorldPoint INSTANCE_START = new WorldPoint(10701, 9315, 1);
	private static final WorldPoint FIRST = new WorldPoint(2568, 2295, 0);
	private static final WorldPoint SECOND = new WorldPoint(3605, 3215, 0);

	private static Map<String, Object> path(Object target)
	{
		Map<String, Object> data = new HashMap<>();
		data.put("start", INSTANCE_START);
		data.put("target", target);
		return data;
	}

	@Test
	public void aPathSetsTheTarget()
	{
		QuestTarget quest = new QuestTarget();
		quest.apply("shortestpath", "path", path(FIRST));
		assertEquals(FIRST, quest.current());
	}

	@Test
	public void aStepChangeIsAClearThenAPath()
	{
		QuestTarget quest = new QuestTarget();
		quest.apply("shortestpath", "path", path(FIRST));
		quest.apply("shortestpath", "clear", null);
		quest.apply("shortestpath", "path", path(SECOND));
		assertEquals(SECOND, quest.current());
	}

	@Test
	public void aClearRemovesIt()
	{
		QuestTarget quest = new QuestTarget();
		quest.apply("shortestpath", "path", path(FIRST));
		quest.apply("shortestpath", "clear", null);
		assertNull(quest.current());
	}

	@Test
	public void anotherNamespaceIsIgnored()
	{
		QuestTarget quest = new QuestTarget();
		quest.apply("shortestpath", "path", path(FIRST));
		quest.apply("inventorysetups", "clear", null);
		quest.apply("someplugin", "path", path(SECOND));
		assertEquals(FIRST, quest.current());
	}

	@Test
	public void aMalformedPathKeepsTheLastGoodTarget()
	{
		QuestTarget quest = new QuestTarget();
		quest.apply("shortestpath", "path", path(FIRST));
		quest.apply("shortestpath", "path", path("2568,2295"));
		quest.apply("shortestpath", "path", path(null));
		quest.apply("shortestpath", "path", null);
		quest.apply("shortestpath", "somethingelse", path(SECOND));
		assertEquals(FIRST, quest.current());
	}

	@Test
	public void forgettingDropsIt()
	{
		QuestTarget quest = new QuestTarget();
		quest.apply("shortestpath", "path", path(FIRST));
		quest.forget();
		assertNull(quest.current());
	}
}
