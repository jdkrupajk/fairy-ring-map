package com.fairyringmap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;

/** The texts are fairy ring clues as the Clue Scroll plugin stores them. */
public class ClueLocationsTest
{
	@Test
	public void theCodeIsTheFirstThreeLettersOfAFairyRingClue()
	{
		assertEquals("AIR", ClueLocations.codeFrom("A I R 2 3 3 1"));
		assertEquals("BJR", ClueLocations.codeFrom("B J R 1 1 2 3"));
	}

	@Test
	public void spacingAndCaseDoNotMatter()
	{
		assertEquals("DJR", ClueLocations.codeFrom("djr 1 2 3 4"));
	}

	@Test
	public void lettersThatNoDialCanShowAreNotACode()
	{
		// Dial one runs A-D, dial two I-L, dial three P-S.
		assertNull(ClueLocations.codeFrom("A A A 1 2 3 4"));
		assertNull(ClueLocations.codeFrom("E I P 1 2 3 4"));
	}

	@Test
	public void noTextIsNoCode()
	{
		assertNull(ClueLocations.codeFrom(null));
		assertNull(ClueLocations.codeFrom("A I"));
	}
}
