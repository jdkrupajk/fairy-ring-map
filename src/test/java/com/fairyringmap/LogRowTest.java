package com.fairyringmap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * The sample row text is verbatim from the cache's fairyring table, "&lt;br&gt;" prefix and stray
 * trailing space included, because those are exactly the details a tidier fake would smooth away.
 */
public class LogRowTest
{
	private static final String MUDSKIPPER = "<br>Asgarnia: Mudskipper Point ";

	@Test
	public void codesComeOutOfTheColouredSpacedMenuTarget()
	{
		assertEquals("AIQ", LogRow.codeFromTarget("<col=ff981f>A I Q</col>"));
		assertEquals("DLS", LogRow.codeFromTarget("D L S"));
	}

	@Test
	public void anEmptyOrNonsenseTargetHasNoCode()
	{
		assertNull(LogRow.codeFromTarget(""));
		assertNull(LogRow.codeFromTarget(null));
		assertNull(LogRow.codeFromTarget("<col=ff981f>Travel log</col>"));
	}

	/**
	 * The dials carry A/D/C/B, I/L/K/J and P/S/R/Q, one letter from each in that order, so 64
	 * codes are possible. Anything using a letter from the wrong dial is not a code at all.
	 */
	@Test
	public void onlyLettersFromTheRightDialFormACode()
	{
		assertTrue(LogRow.isCode("AIQ"));
		assertTrue(LogRow.isCode("DLS"));
		assertFalse(LogRow.isCode("AAA"));
		assertFalse(LogRow.isCode("EIQ"));
		assertFalse(LogRow.isCode("AIT"));
		assertFalse(LogRow.isCode("AI"));
		assertFalse(LogRow.isCode("AIQR"));
		assertFalse(LogRow.isCode(null));
	}

	@Test
	public void aRowWithNoTextIsARingTheAccountCannotUse()
	{
		assertFalse(LogRow.isAvailable(""));
		assertFalse(LogRow.isAvailable(null));
		assertTrue(LogRow.isAvailable(MUDSKIPPER));
	}
}
