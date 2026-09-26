package com.fairyringmap;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * What the plugin knows about the account's fairy rings, and how long each piece of it lives.
 * <p>
 * Pure - codes in, answers out, no {@code Widget} - for the same reason {@link MapProjection},
 * {@link LogRow} and {@link LabelPlacement} are: the thing that went wrong here can then be tested
 * without a client. Three bugs in one session were all one shape, a fact with the wrong lifetime,
 * and none of them could have been caught by a test while the facts lived as fields on a 3,000-line
 * widget class.
 *
 * <h3>Two lifetimes</h3>
 *
 * <b>Account facts</b> - which rings are unlocked, which are favourited, and whether the game has
 * told us yet - outlive the interface. The game reports them only when it rebuilds the travel log,
 * so they cannot be re-derived on open; they have to be carried. Forgetting them on close is what
 * made the map open grey on every reopen.
 * <p>
 * <b>View facts</b> - which of the ten favourite rows holds which code - describe the list as it is
 * currently drawn, and are wrong the moment it is not. They are dropped by {@link #interfaceClosed}.
 * Merging the two is what made favourites lose their colour on reopen while everything else kept
 * theirs.
 */
final class RingState
{
	private final Set<String> unlocked = new HashSet<>();
	private final Set<String> favourites = new HashSet<>();
	private final Map<String, Integer> favouriteSlots = new LinkedHashMap<>();

	/**
	 * Whether any read has yet said which rings are unlocked. A set cannot say "not yet known",
	 * which the map needs: it is drawn before the game rebuilds the log, and treating "no answer"
	 * as "all locked" opened it grey.
	 */
	private boolean availabilityKnown;

	/**
	 * Record a read of the favourites block: code to the slot it occupies. Replaces the previous
	 * read outright, so an unfavourited ring loses its colour.
	 */
	void recordFavourites(Map<String, Integer> slots)
	{
		favouriteSlots.clear();
		favouriteSlots.putAll(slots);
		favourites.clear();
		favourites.addAll(slots.keySet());
	}

	/**
	 * Record a read of which rings the log shows as unlocked.
	 * <p>
	 * Availability counts as known only once a read finds <em>something</em> unlocked. Script 8080
	 * can run before the map exists, and before the server has filled the rows; either way every
	 * ring reads locked, and taking that at face value would record "known, and nothing is
	 * unlocked". One unlocked ring is the signal the rows carry real data.
	 */
	void recordAvailability(Map<String, Boolean> read)
	{
		for (Map.Entry<String, Boolean> entry : read.entrySet())
		{
			if (entry.getValue())
			{
				unlocked.add(entry.getKey());
			}
			else
			{
				unlocked.remove(entry.getKey());
			}
		}
		if (!unlocked.isEmpty())
		{
			availabilityKnown = true;
		}
	}

	/** The travel log has closed. Account facts stay; the slot map no longer describes anything. */
	void interfaceClosed()
	{
		favouriteSlots.clear();
	}

	boolean isUnlocked(String code)
	{
		return unlocked.contains(code);
	}

	boolean isFavourite(String code)
	{
		return favourites.contains(code);
	}

	/** Which favourite row holds a code, or null when it is not in the favourites block on screen. */
	Integer favouriteSlot(String code)
	{
		return favouriteSlots.get(code);
	}

	Map<String, Integer> favouriteSlots()
	{
		return Collections.unmodifiableMap(favouriteSlots);
	}

	/**
	 * Whether a marker is drawn as somewhere the player can go.
	 * <p>
	 * Before anything is known every ring counts as reachable rather than locked. Both guesses are
	 * wrong for a moment, but this one is wrong about a handful of locked rings instead of all
	 * forty-two. A ring the log's search excluded is unreachable whatever the account holds: while
	 * a search is in use the map is a picture of the search.
	 */
	boolean reachable(String code, boolean matchedBySearch)
	{
		return (!availabilityKnown || unlocked.contains(code)) && matchedBySearch;
	}
}
