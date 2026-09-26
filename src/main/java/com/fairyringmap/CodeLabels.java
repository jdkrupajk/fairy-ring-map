/*
 * Copyright (c) 2026, Joe Krupa
 * All rights reserved.
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */
package com.fairyringmap;

/**
 * Which destinations draw their three-letter code beside their marker.
 * <p>
 * Three states rather than a boolean because the useful answer is not "all or nothing". The map
 * exists so the code does not have to be known — you click a place, not a code — so labelling all
 * forty-two is clutter for anyone who already has what they came for. But two smaller cases are
 * worth serving: someone learning the codes wants them all, and someone who has favourited eight
 * destinations wants exactly those eight, which is a set small enough to never crowd.
 * <p>
 * Off by default for that reason: the feature earns its place by being asked for.
 */
public enum CodeLabels
{
	OFF("Off"),
	FAVOURITES("Favourites only"),
	ALL("Every destination");

	private final String label;

	CodeLabels(String label)
	{
		this.label = label;
	}

	/** RuneLite renders an enum config item by its {@code toString}, not by its constant name. */
	@Override
	public String toString()
	{
		return label;
	}
}
