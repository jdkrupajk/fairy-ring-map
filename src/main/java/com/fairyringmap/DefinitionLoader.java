/*
 * Copyright (c) 2026, Joe Krupa
 * All rights reserved.
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */
package com.fairyringmap;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Reads the plugin's JSON resources out of the jar.
 * <p>
 * A class of its own rather than a method on the plugin because both the plugin and the map need
 * it, and having the map ask the plugin would put a cycle in the object graph that the injector
 * cannot build.
 * <p>
 * The injected {@link Gson} is used rather than a new one, so the plugin inherits the client's
 * configuration — and because creating your own is called out in the plugin guidelines.
 */
@Singleton
public class DefinitionLoader
{
	private final Gson gson;

	@Inject
	private DefinitionLoader(Gson gson)
	{
		this.gson = gson;
	}

	/**
	 * Failures are thrown, not swallowed. A map with no rings on it looks like a broken interface;
	 * a plugin that refuses to start says what is wrong in the log.
	 */
	public <T> T load(Class<T> type, String path)
	{
		try (InputStream in = DefinitionLoader.class.getResourceAsStream(path))
		{
			if (in == null)
			{
				throw new IllegalStateException("missing resource " + path);
			}
			try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8))
			{
				return gson.fromJson(reader, type);
			}
		}
		catch (IOException e)
		{
			throw new IllegalStateException("could not read " + path, e);
		}
	}
}
