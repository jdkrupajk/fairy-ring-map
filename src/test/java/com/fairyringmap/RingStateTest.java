package com.fairyringmap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

/**
 * Each test walks the sequence the client actually produces - open, the game rebuilds the log,
 * close, reopen - because every bug this guards against only appeared across that sequence. A
 * single read followed by an assertion would have passed all three of them.
 */
public class RingStateTest
{
	/** Mudskipper Point, Zanaris and Miscellania: an ordinary ring, a favourite, a locked one. */
	private static final String AIQ = "AIQ";
	private static final String CKS = "CKS";
	private static final String CIP = "CIP";

	private static Map<String, Boolean> read(String... codeThenUnlocked)
	{
		Map<String, Boolean> read = new HashMap<>();
		for (int i = 0; i < codeThenUnlocked.length; i += 2)
		{
			read.put(codeThenUnlocked[i], Boolean.parseBoolean(codeThenUnlocked[i + 1]));
		}
		return read;
	}

	private static Map<String, Integer> slots(Object... codeThenSlot)
	{
		Map<String, Integer> slots = new HashMap<>();
		for (int i = 0; i < codeThenSlot.length; i += 2)
		{
			slots.put((String) codeThenSlot[i], (Integer) codeThenSlot[i + 1]);
		}
		return slots;
	}

	/** The state a session reaches after one ordinary opening of the travel log. */
	private static RingState afterFirstOpening()
	{
		RingState state = new RingState();
		state.recordFavourites(slots(CKS, 0));
		state.recordAvailability(read(AIQ, "true", CKS, "true", CIP, "false"));
		return state;
	}

	@Test
	public void beforeAnyReadEveryRingIsReachable()
	{
		RingState state = new RingState();
		assertTrue(state.reachable(AIQ, true));
		assertTrue(state.reachable(CIP, true));
	}

	/**
	 * Script 8080 can run before the server has filled the rows, when every ring reads locked.
	 * Taking that at face value is what opened the map grey.
	 */
	@Test
	public void aReadThatFindsNothingUnlockedDoesNotCountAsKnowing()
	{
		RingState state = new RingState();
		state.recordAvailability(read(AIQ, "false", CKS, "false", CIP, "false"));
		assertTrue(state.reachable(AIQ, true));
		assertTrue(state.reachable(CIP, true));
	}

	@Test
	public void oneUnlockedRingMakesTheRestOfTheReadBelievable()
	{
		RingState state = afterFirstOpening();
		assertTrue(state.reachable(AIQ, true));
		assertFalse(state.reachable(CIP, true));
	}

	/** The bug that cost a session: every reopen started from "nothing unlocked, nothing favourited". */
	@Test
	public void accountFactsSurviveTheInterfaceClosing()
	{
		RingState state = afterFirstOpening();
		state.interfaceClosed();

		// Reopened, and the game has not rebuilt the log yet.
		assertTrue(state.isUnlocked(AIQ));
		assertTrue(state.isFavourite(CKS));
		assertTrue(state.reachable(AIQ, true));
		assertFalse("a locked ring stays locked, not unknown", state.reachable(CIP, true));
	}

	/**
	 * Which row holds a favourite is only true of the list on screen. Carrying it across a close
	 * would point the filter at a row the server may have refilled with something else.
	 */
	@Test
	public void theFavouriteSlotDoesNotSurviveTheInterfaceClosing()
	{
		RingState state = afterFirstOpening();
		assertEquals(Integer.valueOf(0), state.favouriteSlot(CKS));

		state.interfaceClosed();

		assertNull(state.favouriteSlot(CKS));
		assertTrue(state.favouriteSlots().isEmpty());
	}

	@Test
	public void unfavouritingTakesTheColourAway()
	{
		RingState state = afterFirstOpening();
		state.recordFavourites(slots(AIQ, 0));
		assertFalse(state.isFavourite(CKS));
		assertTrue(state.isFavourite(AIQ));
		assertNull(state.favouriteSlot(CKS));
	}

	@Test
	public void aRingUnlockedMidSessionIsPickedUpByTheNextRead()
	{
		RingState state = afterFirstOpening();
		state.recordAvailability(read(CIP, "true"));
		assertTrue(state.reachable(CIP, true));
		assertTrue("a partial read leaves the rest alone", state.isUnlocked(AIQ));
	}

	/** While a search is in use the map is a picture of the search, whatever the account holds. */
	@Test
	public void aRingTheSearchExcludedIsUnreachableEvenWhenUnlocked()
	{
		RingState state = afterFirstOpening();
		assertFalse(state.reachable(AIQ, false));
		assertFalse(new RingState().reachable(AIQ, false));
	}
}
