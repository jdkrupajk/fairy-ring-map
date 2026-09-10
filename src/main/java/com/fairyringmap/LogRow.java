/*
 * Copyright (c) 2026, Joe Krupa
 * All rights reserved.
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */
package com.fairyringmap;

import net.runelite.client.util.Text;

/**
 * The string handling around a travel log row.
 * <p>
 * Small, but the two facts it encodes are ones the rest of the plugin would otherwise have to
 * guess at: what a row's menu target looks like once the game has coloured and spaced the code
 * inside it, and what an unusable destination looks like from the outside.
 * <p>
 * No RuneLite state is touched here, so it is all unit testable.
 */
public final class LogRow
{
	private LogRow()
	{
	}

	/**
	 * The three-letter code out of a row's menu target.
	 * <p>
	 * The list builder sets each row's target to its code wrapped in a colour tag and spaced out,
	 * so {@code "<col=ff981f>A I Q</col>"} becomes {@code "AIQ"}. Returns null for anything that
	 * is not a code, including the empty targets of rows the account has not unlocked.
	 */
	public static String codeFromTarget(String menuTarget)
	{
		if (menuTarget == null)
		{
			return null;
		}
		String code = Text.removeTags(menuTarget).replace(" ", "").toUpperCase();
		return isCode(code) ? code : null;
	}

	/**
	 * Whether a string is one of the 64 possible codes.
	 * <p>
	 * The dials carry A/D/C/B, I/L/K/J and P/S/R/Q, so every code is one letter from each set in
	 * that order. Fifty-five of the sixty-four have a ring; this only checks the shape.
	 */
	public static boolean isCode(String code)
	{
		return code != null
			&& code.length() == 3
			&& "ABCD".indexOf(code.charAt(0)) >= 0
			&& "IJKL".indexOf(code.charAt(1)) >= 0
			&& "PQRS".indexOf(code.charAt(2)) >= 0;
	}

	/**
	 * Whether the account can travel to this row.
	 * <p>
	 * The list builder shows a row only when the server has given it text, so an empty row is an
	 * unavailable one — this is the same raw length test the script makes, deliberately, rather
	 * than a tidier one that might disagree with it. Reading availability off the interface avoids
	 * keeping our own copy of every ring's quest and diary requirements, which would rot.
	 */
	public static boolean isAvailable(String rowText)
	{
		return rowText != null && !rowText.isEmpty();
	}
}
