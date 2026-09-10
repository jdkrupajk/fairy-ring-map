/*
 * Copyright (c) 2026, Joe Krupa
 * All rights reserved.
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */
package com.fairyringmap;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.runelite.api.gameval.InterfaceID;

/**
 * Maps a fairy ring code to the travel log row that carries it.
 *
 * The log has a static row for all 64 possible codes, whether or not a ring exists, and the game
 * gives each one a name in the gameval constants. Listing them out is verbose but it is the only
 * way to reach them through named constants: deriving the component id by adding a child index to
 * the interface id is exactly what the plugin guidelines tell you not to do, and reflection over
 * the constants is forbidden outright. The payoff is that a rename upstream breaks the build
 * rather than the plugin.
 *
 * Generated, not typed - see cache-tools/tools in the sibling project folder.
 */
public final class FairyRingRows
{
	private static final Map<String, Integer> ROWS;

	static
	{
		Map<String, Integer> rows = new LinkedHashMap<>(64);
		rows.put("AIP", InterfaceID.FairyringsLog.AIP);
		rows.put("AIQ", InterfaceID.FairyringsLog.AIQ);
		rows.put("AIR", InterfaceID.FairyringsLog.AIR);
		rows.put("AIS", InterfaceID.FairyringsLog.AIS);
		rows.put("AJP", InterfaceID.FairyringsLog.AJP);
		rows.put("AJQ", InterfaceID.FairyringsLog.AJQ);
		rows.put("AJR", InterfaceID.FairyringsLog.AJR);
		rows.put("AJS", InterfaceID.FairyringsLog.AJS);
		rows.put("AKP", InterfaceID.FairyringsLog.AKP);
		rows.put("AKQ", InterfaceID.FairyringsLog.AKQ);
		rows.put("AKR", InterfaceID.FairyringsLog.AKR);
		rows.put("AKS", InterfaceID.FairyringsLog.AKS);
		rows.put("ALP", InterfaceID.FairyringsLog.ALP);
		rows.put("ALQ", InterfaceID.FairyringsLog.ALQ);
		rows.put("ALR", InterfaceID.FairyringsLog.ALR);
		rows.put("ALS", InterfaceID.FairyringsLog.ALS);
		rows.put("BIP", InterfaceID.FairyringsLog.BIP);
		rows.put("BIQ", InterfaceID.FairyringsLog.BIQ);
		rows.put("BIR", InterfaceID.FairyringsLog.BIR);
		rows.put("BIS", InterfaceID.FairyringsLog.BIS);
		rows.put("BJP", InterfaceID.FairyringsLog.BJP);
		rows.put("BJQ", InterfaceID.FairyringsLog.BJQ);
		rows.put("BJR", InterfaceID.FairyringsLog.BJR);
		rows.put("BJS", InterfaceID.FairyringsLog.BJS);
		rows.put("BKP", InterfaceID.FairyringsLog.BKP);
		rows.put("BKQ", InterfaceID.FairyringsLog.BKQ);
		rows.put("BKR", InterfaceID.FairyringsLog.BKR);
		rows.put("BKS", InterfaceID.FairyringsLog.BKS);
		rows.put("BLP", InterfaceID.FairyringsLog.BLP);
		rows.put("BLQ", InterfaceID.FairyringsLog.BLQ);
		rows.put("BLR", InterfaceID.FairyringsLog.BLR);
		rows.put("BLS", InterfaceID.FairyringsLog.BLS);
		rows.put("CIP", InterfaceID.FairyringsLog.CIP);
		rows.put("CIQ", InterfaceID.FairyringsLog.CIQ);
		rows.put("CIR", InterfaceID.FairyringsLog.CIR);
		rows.put("CIS", InterfaceID.FairyringsLog.CIS);
		rows.put("CJP", InterfaceID.FairyringsLog.CJP);
		rows.put("CJQ", InterfaceID.FairyringsLog.CJQ);
		rows.put("CJR", InterfaceID.FairyringsLog.CJR);
		rows.put("CJS", InterfaceID.FairyringsLog.CJS);
		rows.put("CKP", InterfaceID.FairyringsLog.CKP);
		rows.put("CKQ", InterfaceID.FairyringsLog.CKQ);
		rows.put("CKR", InterfaceID.FairyringsLog.CKR);
		rows.put("CKS", InterfaceID.FairyringsLog.CKS);
		rows.put("CLP", InterfaceID.FairyringsLog.CLP);
		rows.put("CLQ", InterfaceID.FairyringsLog.CLQ);
		rows.put("CLR", InterfaceID.FairyringsLog.CLR);
		rows.put("CLS", InterfaceID.FairyringsLog.CLS);
		rows.put("DIP", InterfaceID.FairyringsLog.DIP);
		rows.put("DIQ", InterfaceID.FairyringsLog.DIQ);
		rows.put("DIR", InterfaceID.FairyringsLog.DIR);
		rows.put("DIS", InterfaceID.FairyringsLog.DIS);
		rows.put("DJP", InterfaceID.FairyringsLog.DJP);
		rows.put("DJQ", InterfaceID.FairyringsLog.DJQ);
		rows.put("DJR", InterfaceID.FairyringsLog.DJR);
		rows.put("DJS", InterfaceID.FairyringsLog.DJS);
		rows.put("DKP", InterfaceID.FairyringsLog.DKP);
		rows.put("DKQ", InterfaceID.FairyringsLog.DKQ);
		rows.put("DKR", InterfaceID.FairyringsLog.DKR);
		rows.put("DKS", InterfaceID.FairyringsLog.DKS);
		rows.put("DLP", InterfaceID.FairyringsLog.DLP);
		rows.put("DLQ", InterfaceID.FairyringsLog.DLQ);
		rows.put("DLR", InterfaceID.FairyringsLog.DLR);
		rows.put("DLS", InterfaceID.FairyringsLog.DLS);
		ROWS = Collections.unmodifiableMap(rows);
	}

	private static final Map<String, Integer> FAVES;

	/** {@link #ROWS} inverted, so a widget found in the wild can be named. */
	private static final Map<Integer, String> BY_COMPONENT;

	static
	{
		Map<String, Integer> faves = new LinkedHashMap<>(64);
		faves.put("AIP", InterfaceID.FairyringsLog.AIP_FAVE);
		faves.put("AIQ", InterfaceID.FairyringsLog.AIQ_FAVE);
		faves.put("AIR", InterfaceID.FairyringsLog.AIR_FAVE);
		faves.put("AIS", InterfaceID.FairyringsLog.AIS_FAVE);
		faves.put("AJP", InterfaceID.FairyringsLog.AJP_FAVE);
		faves.put("AJQ", InterfaceID.FairyringsLog.AJQ_FAVE);
		faves.put("AJR", InterfaceID.FairyringsLog.AJR_FAVE);
		faves.put("AJS", InterfaceID.FairyringsLog.AJS_FAVE);
		faves.put("AKP", InterfaceID.FairyringsLog.AKP_FAVE);
		faves.put("AKQ", InterfaceID.FairyringsLog.AKQ_FAVE);
		faves.put("AKR", InterfaceID.FairyringsLog.AKR_FAVE);
		faves.put("AKS", InterfaceID.FairyringsLog.AKS_FAVE);
		faves.put("ALP", InterfaceID.FairyringsLog.ALP_FAVE);
		faves.put("ALQ", InterfaceID.FairyringsLog.ALQ_FAVE);
		faves.put("ALR", InterfaceID.FairyringsLog.ALR_FAVE);
		faves.put("ALS", InterfaceID.FairyringsLog.ALS_FAVE);
		faves.put("BIP", InterfaceID.FairyringsLog.BIP_FAVE);
		faves.put("BIQ", InterfaceID.FairyringsLog.BIQ_FAVE);
		faves.put("BIR", InterfaceID.FairyringsLog.BIR_FAVE);
		faves.put("BIS", InterfaceID.FairyringsLog.BIS_FAVE);
		faves.put("BJP", InterfaceID.FairyringsLog.BJP_FAVE);
		faves.put("BJQ", InterfaceID.FairyringsLog.BJQ_FAVE);
		faves.put("BJR", InterfaceID.FairyringsLog.BJR_FAVE);
		faves.put("BJS", InterfaceID.FairyringsLog.BJS_FAVE);
		faves.put("BKP", InterfaceID.FairyringsLog.BKP_FAVE);
		faves.put("BKQ", InterfaceID.FairyringsLog.BKQ_FAVE);
		faves.put("BKR", InterfaceID.FairyringsLog.BKR_FAVE);
		faves.put("BKS", InterfaceID.FairyringsLog.BKS_FAVE);
		faves.put("BLP", InterfaceID.FairyringsLog.BLP_FAVE);
		faves.put("BLQ", InterfaceID.FairyringsLog.BLQ_FAVE);
		faves.put("BLR", InterfaceID.FairyringsLog.BLR_FAVE);
		faves.put("BLS", InterfaceID.FairyringsLog.BLS_FAVE);
		faves.put("CIP", InterfaceID.FairyringsLog.CIP_FAVE);
		faves.put("CIQ", InterfaceID.FairyringsLog.CIQ_FAVE);
		faves.put("CIR", InterfaceID.FairyringsLog.CIR_FAVE);
		faves.put("CIS", InterfaceID.FairyringsLog.CIS_FAVE);
		faves.put("CJP", InterfaceID.FairyringsLog.CJP_FAVE);
		faves.put("CJQ", InterfaceID.FairyringsLog.CJQ_FAVE);
		faves.put("CJR", InterfaceID.FairyringsLog.CJR_FAVE);
		faves.put("CJS", InterfaceID.FairyringsLog.CJS_FAVE);
		faves.put("CKP", InterfaceID.FairyringsLog.CKP_FAVE);
		faves.put("CKQ", InterfaceID.FairyringsLog.CKQ_FAVE);
		faves.put("CKR", InterfaceID.FairyringsLog.CKR_FAVE);
		faves.put("CKS", InterfaceID.FairyringsLog.CKS_FAVE);
		faves.put("CLP", InterfaceID.FairyringsLog.CLP_FAVE);
		faves.put("CLQ", InterfaceID.FairyringsLog.CLQ_FAVE);
		faves.put("CLR", InterfaceID.FairyringsLog.CLR_FAVE);
		faves.put("CLS", InterfaceID.FairyringsLog.CLS_FAVE);
		faves.put("DIP", InterfaceID.FairyringsLog.DIP_FAVE);
		faves.put("DIQ", InterfaceID.FairyringsLog.DIQ_FAVE);
		faves.put("DIR", InterfaceID.FairyringsLog.DIR_FAVE);
		faves.put("DIS", InterfaceID.FairyringsLog.DIS_FAVE);
		faves.put("DJP", InterfaceID.FairyringsLog.DJP_FAVE);
		faves.put("DJQ", InterfaceID.FairyringsLog.DJQ_FAVE);
		faves.put("DJR", InterfaceID.FairyringsLog.DJR_FAVE);
		faves.put("DJS", InterfaceID.FairyringsLog.DJS_FAVE);
		faves.put("DKP", InterfaceID.FairyringsLog.DKP_FAVE);
		faves.put("DKQ", InterfaceID.FairyringsLog.DKQ_FAVE);
		faves.put("DKR", InterfaceID.FairyringsLog.DKR_FAVE);
		faves.put("DKS", InterfaceID.FairyringsLog.DKS_FAVE);
		faves.put("DLP", InterfaceID.FairyringsLog.DLP_FAVE);
		faves.put("DLQ", InterfaceID.FairyringsLog.DLQ_FAVE);
		faves.put("DLR", InterfaceID.FairyringsLog.DLR_FAVE);
		faves.put("DLS", InterfaceID.FairyringsLog.DLS_FAVE);
		FAVES = Collections.unmodifiableMap(faves);

		Map<Integer, String> byComponent = new LinkedHashMap<>(64);
		for (Map.Entry<String, Integer> entry : ROWS.entrySet())
		{
			byComponent.put(entry.getValue(), entry.getKey());
		}
		BY_COMPONENT = Collections.unmodifiableMap(byComponent);
	}

	private FairyRingRows()
	{
	}

	/**
	 * The component id of the log row for a code, or null if the code is not one of the 64.
	 */
	public static Integer componentFor(String code)
	{
		return code == null ? null : ROWS.get(code);
	}

	/** Every code the interface has a row for, in the order the interface declares them. */
	public static Map<String, Integer> all()
	{
		return ROWS;
	}

	/**
	 * The code drawn in a component, or null if the component is not one of the 64 rows.
	 * <p>
	 * The inverse of {@link #componentFor(String)}, and it is safe in a way the forward direction
	 * does not make obvious: these 64 rows are <b>static</b> widgets, so each one really does carry
	 * its own packed id. The dynamic children of {@code CONTENTS} — the three-letter code labels —
	 * all report the parent's id instead, so they can never be identified this way. Anything that
	 * starts from a widget and wants to know which destination it is must therefore start from the
	 * row, not from the label beside it.
	 */
	public static String codeForComponent(int component)
	{
		return BY_COMPONENT.get(component);
	}

	/**
	 * The favourite star drawn beside a row.
	 * <p>
	 * A visible entry in the log is three widgets, not one: this static row, this static star, and
	 * a dynamic label holding the three-letter code. Hiding only the row would leave its star
	 * floating in the gap, so anything that hides a row has to hide its star with it.
	 */
	public static Integer faveFor(String code)
	{
		return code == null ? null : FAVES.get(code);
	}

	/**
	 * The ten rows inside the favourites block at the top of the log.
	 * <p>
	 * These are a second, parallel set of rows — not the same widgets as the 64 code rows, and not
	 * a view of them. The server decides which set a destination is drawn in: favourite it and the
	 * server blanks the code row's text and fills one of these instead. So a favourited destination
	 * is <b>only</b> here, and anything that reads or manipulates the log has to look in both
	 * places or it will conclude a favourited ring does not exist.
	 */
	public static int[] faveBlockRows()
	{
		return new int[]{
			InterfaceID.FairyringsLog.FAVE_1,
			InterfaceID.FairyringsLog.FAVE_2,
			InterfaceID.FairyringsLog.FAVE_3,
			InterfaceID.FairyringsLog.FAVE_4,
			InterfaceID.FairyringsLog.FAVE_5,
			InterfaceID.FairyringsLog.FAVE_6,
			InterfaceID.FairyringsLog.FAVE_7,
			InterfaceID.FairyringsLog.FAVE_8,
			InterfaceID.FairyringsLog.FAVE_9,
			InterfaceID.FairyringsLog.FAVE_10,
		};
	}

	/** The stars beside those ten rows, carrying "Remove Favourite". */
	public static int[] faveBlockStars()
	{
		return new int[]{
			InterfaceID.FairyringsLog.FAVE_ICON_1,
			InterfaceID.FairyringsLog.FAVE_ICON_2,
			InterfaceID.FairyringsLog.FAVE_ICON_3,
			InterfaceID.FairyringsLog.FAVE_ICON_4,
			InterfaceID.FairyringsLog.FAVE_ICON_5,
			InterfaceID.FairyringsLog.FAVE_ICON_6,
			InterfaceID.FairyringsLog.FAVE_ICON_7,
			InterfaceID.FairyringsLog.FAVE_ICON_8,
			InterfaceID.FairyringsLog.FAVE_ICON_9,
			InterfaceID.FairyringsLog.FAVE_ICON_10,
		};
	}

	/**
	 * The ten code labels inside the favourites block at the top of the log.
	 * <p>
	 * These are how the plugin finds out <em>which</em> destinations are favourited, and they are
	 * the only route to it. Nothing client-side records a favourite: "Add Favourite" and "Remove
	 * Favourite" are raw server-bound ops with no listener, interface 381 has no
	 * {@code onVarTransmit} on any child, and <b>no clientscript anywhere sets a log row's text</b>
	 * — all 9,783 were disassembled and only script 8080 so much as reads it. Row text arrives
	 * straight from the server, and so does the decision about which of the two sets a destination
	 * is drawn in. So the rendered favourites block <em>is</em> the client's copy of the answer.
	 * <p>
	 * Ten because the interface declares ten, which is the game's own cap. The three arrays share
	 * an index: slot {@code n} is {@code faveBlockRows()[n]}, {@code faveBlockCodeLabels()[n]},
	 * {@code faveBlockStars()[n]}.
	 */
	public static int[] faveBlockCodeLabels()
	{
		return new int[]{
			InterfaceID.FairyringsLog.FAVE_CODE_1,
			InterfaceID.FairyringsLog.FAVE_CODE_2,
			InterfaceID.FairyringsLog.FAVE_CODE_3,
			InterfaceID.FairyringsLog.FAVE_CODE_4,
			InterfaceID.FairyringsLog.FAVE_CODE_5,
			InterfaceID.FairyringsLog.FAVE_CODE_6,
			InterfaceID.FairyringsLog.FAVE_CODE_7,
			InterfaceID.FairyringsLog.FAVE_CODE_8,
			InterfaceID.FairyringsLog.FAVE_CODE_9,
			InterfaceID.FairyringsLog.FAVE_CODE_10,
		};
	}
}
