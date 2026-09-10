/*
 * Copyright (c) 2026, Joe Krupa
 * All rights reserved.
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */
package com.fairyringmap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.FontID;
import net.runelite.api.MenuEntry;
import net.runelite.api.Point;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetConfig;
import net.runelite.api.widgets.WidgetType;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;

/**
 * The map that replaces the fairy ring travel log.
 *
 * <h2>Why this is a filter and not a travel button</h2>
 *
 * Teleport Maps' components end a click by running a listener the game already installed, and that
 * listener contains the opcode that talks to the server. Nothing here can do that. Every row of the
 * fairy ring travel log has the op "Use code" with <b>no listener of any kind</b>, in the interface
 * definition and at runtime; the op goes straight to the server. Nor is there a listener anywhere
 * else in the fairy ring interfaces that sends a packet — even the dials' Confirm button only plays
 * a sound and animates, because its travel is a raw op too.
 *
 * <p>So the map cannot travel, and it does not try. It <b>filters</b>: clicking a ring leaves that
 * one destination standing in the native list, at the top, highlighted. The click that travels is
 * then the player's, on the game's own unmodified row.
 *
 * <h2>Everything here is widget work</h2>
 *
 * Show, hide, move, resize, colour. No game scripts are invoked, no game variables are written, no
 * menu actions are created. That is partly principle and partly review: {@code client.runScript} is
 * gated on the Plugin Hub behind an unpublished allowlist of script ids, and a plugin that uses one
 * off the list waits for a manual review that in practice never comes.
 *
 * <h2>Where it draws</h2>
 *
 * The map covers the dial interface rather than the travel log's own panel, which is only 190 px
 * wide — too narrow for forty-two markers to be separately clickable.
 *
 * <p>It is drawn into a layer of its own, hung off the client's <b>floater</b> layer and positioned
 * over the dials. Two earlier attempts explain why that particular layer:
 *
 * <ul>
 *   <li>Drawing inside the travel log's own panel and stretching it towards the dials fails,
 *       because each interface is clipped to its slot. Everything past the panel's 190 px edge is
 *       cut away, so the map was built correctly and never appeared.</li>
 *   <li>Drawing into the modal layer that hosts the dials fails differently: the layer's own
 *       children are painted before the interface opened into it, so the map appeared
 *       <em>behind</em> the dials.</li>
 * </ul>
 *
 * The floater layer is the sibling drawn after that modal one, which is what puts the map in
 * front. Positions inside it come from comparing canvas locations, because the layers do not
 * share an origin.
 *
 * <p>Nothing the game owns is moved or resized. A layer draws nothing and swallows no clicks, so
 * the interface's own close button keeps working with the map up.
 *
 * <h2>Widgets are created once and afterwards only moved</h2>
 *
 * This is the load-bearing structural decision in the class, and it is forced rather than chosen.
 * A dynamic widget cannot be removed from its parent through the RuneLite API:
 * {@code setChildren(null)} throws {@code "only static widgets may have children"}, and
 * {@code deleteAllChildren()} on a widget that is itself a dynamic child compiles, runs and does
 * nothing at all — measured, by counting our layer's children across successive openings of the
 * travel log: 63, 126, 189, 252, 300.
 *
 * <p>Every widget this class makes is therefore permanent for the life of the client session. The
 * only way not to accumulate them is not to make them twice. So the layer's children are built
 * once, in a <b>fixed order</b>, and every later opening re-acquires them <b>by index</b> out of
 * {@link Widget#getDynamicChildren()} and does nothing but move and reconfigure them. The index is
 * a positional handle into an array this class owns exclusively, which is why it is safe where a
 * remembered {@code Widget} reference is not.
 *
 * <p>Two consequences fall out of that and are worth stating because they look like accidents in
 * the code otherwise. Widgets whose existence depends on a config setting are built anyway and
 * merely hidden, so that the child count is a constant and the indices never shift. And listeners
 * are installed at creation, never on a refresh, because a lambda re-installed per opening is a
 * per-opening allocation on a widget that already had a working one.
 */
@Slf4j
@Singleton
public class FairyRingMap
{
	/** Sprite ids registered by the plugin. Negative so they cannot collide with the game's. */
	static final int SPRITE_MAP = -19401;
	static final int SPRITE_RING = -19402;
	static final int SPRITE_RING_HOVER = -19403;
	static final int SPRITE_RING_SELECTED = -19404;
	static final int SPRITE_RING_LOCKED = -19405;
	static final int SPRITE_RING_FAVE = -19406;
	/**
	 * The pre-rendered close-ups, 54 of them tiled 9 x 6 into one 684 x 348 sheet.
	 * <p>
	 * One sprite rather than 54 because a widget draws one sprite, and because the client's sprite
	 * table is a flat map: 54 entries would be 54 registrations, 54 decodes and 54 lookups to
	 * achieve exactly what a negative offset already achieves for free.
	 */
	static final int SPRITE_INSET_SHEET = -19407;

	private static final int ICON_SIZE = 11;
	/** Width reserved for the "Not on the map" caption sharing a line with the strip's icons. */
	private static final int OFF_MAP_LABEL_WIDTH = 92;
	/** Height of the strip carrying the destinations that have no place on a surface map. */
	private static final int STRIP_HEIGHT = 15;
	/** The strip is flush against the map: a gap here read as the strip being a separate thing. */
	private static final int OFF_MAP_GAP = 0;
	/** Frame drawn behind the map and its strip, so the block has an edge instead of ending. */
	private static final int BORDER_WIDTH = 2;

	private static final int TOGGLE_WIDTH = 52;
	private static final int TOGGLE_HEIGHT = 14;
	/** Inset of the toggle from the map's top-right corner. */
	private static final int TOGGLE_INSET = 4;

	/**
	 * The colour of the one row left standing, and of the marker that put it there.
	 * <p>
	 * Deliberately the same value as {@code ring-selected.png} and as the game's own label
	 * orange: the map and the list are two views of one selection, and a white row beside an
	 * orange marker made the player check which was which.
	 */
	private static final int HIGHLIGHT_COLOUR = 0xFF981F;
	private static final int LABEL_COLOUR = 0xFF981F;
	/** Locked destinations keep their tooltip; the colour is what says they are unreachable. */
	private static final String LOCKED_COLOUR_TAG = "<col=8f8f8f>";
	/** Something for a panel to be, on the two panels that are still meant to be seen. */
	private static final int BACKING_COLOUR = 0x1A1A1A;

	/**
	 * Opacity of the blocking panels, on RuneLite's scale where 0 is opaque and 255 is transparent.
	 * <p>
	 * <b>Made invisible by being covered, not by being transparent.</b> That is the whole trick, and
	 * it was arrived at the long way round. Turning the panels transparent to get rid of the dark
	 * wash stopped them blocking: at 255 the dials could be turned through the map again, and at
	 * 254 they still could. A panel the client does not paint registers no click zone, so opacity
	 * and blocking are the same switch and cannot be set independently.
	 * <p>
	 * So the panels go back to a solid 40 — the value that demonstrably blocked — and are instead
	 * sized to sit <b>exactly under the map sprite</b>, which is 508 × 312 of RGB with no alpha
	 * channel and no {@code tRNS} chunk: fully opaque, verified in the file. Nothing of them is
	 * ever seen. What used to show was everything they covered <em>outside</em> the map, which is
	 * now not covered at all, because nothing clickable is out there: the six rotate arms run
	 * x 8–506 and Confirm reaches y 307, all inside the map's x 2–510, y 3–315.
	 */
	private static final int BLOCKER_OPACITY = 40;

	/**
	 * What makes the blocking panels a click target at all.
	 * <p>
	 * {@code setNoClickThrough(true)} on its own does not block — proved twice in game, at two
	 * opacities. It says "do not let a click fall through <em>me</em>", but the client only asks
	 * that of widgets it already considers clickable, and a plain rectangle with no ops and no
	 * click mask is not one. The flag was being read on a widget that was never consulted.
	 * <p>
	 * These four bits are lifted from RuneLite core's own {@code WikiPlugin}, which sets exactly
	 * {@code 79872} alongside {@code setNoClickThrough(true)}. They are the "can be the target of a
	 * use-on" bits, and the reason to choose them over an op is that <b>none of the menu-option
	 * bits are among them</b>: the panel becomes something the client hit-tests without becoming
	 * something that puts an entry in the right-click menu.
	 */
	private static final int BLOCKER_CLICK_MASK = WidgetConfig.USE_GROUND_ITEM
		| WidgetConfig.USE_NPC | WidgetConfig.USE_OBJECT | WidgetConfig.USE_WIDGET;
	private static final int STRIP_OPACITY = 60;
	private static final int TOGGLE_PANEL_OPACITY = 70;

	/**
	 * Where the interface's own close button sits, if it cannot be measured at build time.
	 * <p>
	 * The map is drawn over it, and the click that closes the interface still has to reach it. Our
	 * layer is painted in front of the whole dial interface and no plugin can lift one of the
	 * interface's widgets back out in front — that ordering is fixed, and it is the same reason the
	 * map is drawn where it is. Nor can the button be worked from code: its listener would have to
	 * be fired with {@code client.runScript}, and {@code client.closeInterface} would close the
	 * modal only on this side while the server went on believing it was open.
	 * <p>
	 * So the button is covered by a picture of itself and left to do its own job. The blocking
	 * panel is cut into three pieces around this rectangle, the map draws over it, and a copy of
	 * the button's own sprite is drawn on top — sharing the button's sprite id, so it is the same
	 * image rather than a lookalike. The click lands where the player aimed it, on the game's
	 * widget, and nothing is synthesised.
	 */
	private static final int CLOSE_X = 476;
	private static final int CLOSE_Y = 10;
	private static final int CLOSE_WIDTH = 26;
	private static final int CLOSE_HEIGHT = 23;

	/**
	 * Written into the name of every layer this component creates, so they can be found again.
	 * <p>
	 * A dynamic child does not get an id of its own — {@code getId()} returns its <em>parent's</em>
	 * packed id, which is why the debug line read "layer 10551314 in host 10551314". So a cached
	 * {@code Widget} reference was the only handle on our layer, and when the client rebuilt the
	 * floater the reference went stale while the layer on screen kept its children. Tagging the
	 * name and finding it by scanning the live tree removes the cached reference from the trust
	 * path.
	 */
	private static final String LAYER_TAG = "fairyringmap:layer";

	/**
	 * Clearance between the marker and the inset drawn beside it, so the cursor does not sit on the
	 * thing it just summoned.
	 * <p>
	 * The inset's own size is not a constant: it is the sheet's cell size, read from the definitions
	 * JSON. See {@link #insetWidth()}.
	 */
	private static final int INSET_GAP = 9;

	/**
	 * How many icon widgets the inset keeps ready. Also the ceiling on the "most icons" setting.
	 * <p>
	 * A fixed pool rather than a widget per icon, for the same reason everything else in this class
	 * is created once: a dynamic widget cannot be removed from its parent, so making one per hover
	 * would grow the tree without bound for as long as the client runs. Forty because that is what
	 * the generator caps a cell at, so the pool can always hold every icon the data offers.
	 */
	static final int ICON_POOL = 40;

	/** Index of the sheet inside the inset layer. The pool follows it; the centre marker ends it. */
	private static final int INSET_SLOT_SHEET = 0;
	private static final int INSET_SLOT_FIRST_ICON = 1;
	private static final int INSET_SLOT_CENTRE = INSET_SLOT_FIRST_ICON + ICON_POOL;
	private static final int INSET_SLOTS = INSET_SLOT_CENTRE + 1;

	/**
	 * Slots in the layer's dynamic-children array, in the order {@link #createChildren} makes them.
	 * <p>
	 * The array index is the handle on each widget, so this order is part of the class's contract
	 * with itself: inserting a widget anywhere but the end silently re-points every later slot.
	 * Draw order is the same order, which is why the markers come after the map and the inset comes
	 * after the markers.
	 */
	private static final int SLOT_BLOCK_LEFT = 0;
	private static final int SLOT_BLOCK_RIGHT = 1;
	private static final int SLOT_BLOCK_BELOW_CLOSE = 2;
	private static final int SLOT_BORDER = 3;
	private static final int SLOT_MAP = 4;
	private static final int SLOT_STRIP_BAR = 5;
	private static final int SLOT_OFF_MAP_LABEL = 6;
	private static final int SLOT_TOGGLE_PANEL = 7;
	private static final int SLOT_TOGGLE = 8;
	private static final int SLOT_CLOSE_FACADE = 9;
	private static final int SLOT_FIRST_ICON = 10;

	/**
	 * The client has several top-level layouts — fixed, resizable, spectator — and each has its own
	 * pair of layers: one that modal interfaces open into, and one drawn in front of it. Pairing them
	 * explicitly means the map lands in front whichever layout the player is using, and the constants
	 * come from the game's own names rather than from arithmetic on interface ids.
	 */
	private static final int[][] MODAL_TO_FLOATER = {
		{InterfaceID.ToplevelOsrsStretch.MAINMODAL, InterfaceID.ToplevelOsrsStretch.FLOATER},
		{InterfaceID.Toplevel.MAINMODAL, InterfaceID.Toplevel.FLOATER},
		{InterfaceID.ToplevelPreEoc.MAINMODAL, InterfaceID.ToplevelPreEoc.FLOATER},
		{InterfaceID.ToplevelDisplay.MAINMODAL, InterfaceID.ToplevelDisplay.FLOATER},
		{InterfaceID.ToplevelOsm.MAINMODAL, InterfaceID.ToplevelOsm.FLOATER},
		{InterfaceID.ToplevelSpectator.MAINMODAL, InterfaceID.ToplevelSpectator.FLOATER},
	};

	/**
	 * The clientscript that lays the travel log out: it hides every row, then un-hides, positions
	 * and sizes the ones that match the current search and sort.
	 * <p>
	 * The plugin has to know about it because the game re-runs it on its own — after a favourite
	 * is added, after the interface settles, after a sort — and it overwrites anything we did to
	 * the rows. Hooking the script means the filter is re-applied whatever caused the rebuild,
	 * rather than the plugin having to enumerate the triggers.
	 * <p>
	 * It is also the one moment at which a row's text is known to be current, which is what makes
	 * it the right place to read availability from. See {@link #refreshAvailability()}.
	 * <p>
	 * Not a named constant upstream. It is reached by every path that redraws the list: the four
	 * scripts that invoke it are 402 (which RuneLite names {@code FAIRYRINGS_SORT_UPDATE}, the
	 * sort button's own handler), 8084, 8156 and 8191. Verified by disassembling all 9,783
	 * clientscripts in the cache and grepping for callers.
	 * <p>
	 * This is an observation only — {@code ScriptPostFired} says what the client already ran. The
	 * plugin never invokes a script.
	 */
	private static final int SCRIPT_LOG_REBUILD = 8080;

	/**
	 * The op on a travel log row that travels. Both the ordinary rows and the ten favourite rows
	 * carry it — {@code actions=[Use code, Add Favourite]} and {@code [Use code, Remove Favourite]}
	 * respectively — so matching the option name covers both without listing components.
	 */
	private static final String USE_CODE = "Use code";

	/** Slack under the one row left standing, so the scroll container is not flush against it. */
	private static final int BELOW_ROW_GAP = 4;

	/** How long a selection survives the interface being closed. Two ticks is 1.2 seconds. */
	private static final int CARRY_TICKS = 2;

	private final Client client;
	private final ClientThread clientThread;
	private final FairyRingMapConfig config;
	private final FairyRingDefinitions definitions;
	private final MapProjection projection;

	private final List<RingIcon> icons = new ArrayList<>();
	/**
	 * Codes currently in the log's favourites block, and which of its ten slots each one occupies.
	 * <p>
	 * A favourited destination is drawn <em>only</em> in that block: the server blanks its ordinary
	 * row and fills a favourite row instead. So this is not decoration — it is the map's only way
	 * to know a favourited ring is unlocked at all, and the filter's only way to find the row it
	 * has to leave standing.
	 */
	private final Map<String, Integer> favouriteSlots = new LinkedHashMap<>();
	/**
	 * Widgets hidden or shifted to leave one row standing; undone on every new selection.
	 * <p>
	 * <b>Keyed on the widget, not on {@code getId()}.</b> A dynamic child returns its
	 * <em>parent's</em> packed id, so every one of the fifty-odd code labels the game creates
	 * inside {@code CONTENTS} reports the same number — and so does {@code CONTENTS} itself. Keying
	 * on the id therefore recorded the first label and silently dropped every later one, including
	 * the scroll container's own height. Restoring then left fifty labels hidden and the container
	 * still sized to one row, which is what "the travel log goes blank after Show map" was.
	 * {@code Widget} does not override {@code equals}, so a map keyed on it is an identity map.
	 */
	private final Map<Widget, RowState> rowChanges = new LinkedHashMap<>();

	private Widget container;
	private Widget blockLeft;
	private Widget blockRight;
	private Widget blockBelowClose;
	private Widget border;
	private Widget mapSprite;
	private Widget stripBar;
	private Widget offMapLabel;
	private Widget toggleButton;
	private Widget closeFacade;
	private Widget inset;
	private Widget insetBorder;
	private Widget insetImage;
	/**
	 * The marker drawn at the centre of the inset, which is the destination being hovered.
	 * <p>
	 * It replaces the game's own Transportation icon (sprite 1504), which sits at the centre of
	 * every cell for the same reason — it <em>is</em> the fairy ring. Drawing our own marker there
	 * instead buys back the most contested pixels on the sheet and keeps the inset's centre in the
	 * map's visual language rather than the game map's. Only the centre one is suppressed: other
	 * Transportation icons in the same cell — a spirit tree, a canoe station — are ordinary icons.
	 */
	private Widget insetCentre;
	/**
	 * The inset layer's children, held as the array rather than as forty fields.
	 * <p>
	 * Index {@link #INSET_SLOT_SHEET} is the sheet, {@link #INSET_SLOT_FIRST_ICON} onwards is the
	 * icon pool, {@link #INSET_SLOT_CENTRE} is the centre marker.
	 */
	private Widget[] insetIcons;


	/** Top-left of the map inside the layer, so a marker's pixel can be turned back into a place. */
	private int mapOriginX;
	private int mapOriginY;
	private Widget highlightedRow;
	private int highlightedColour;
	private RingDefinition selected;

	/**
	 * Which destination the cursor is over, and where that was learned.
	 *
	 * <h3>Hover has to be state, not a brush stroke</h3>
	 *
	 * The mouse-over listener used to write the bright sprite straight onto the marker. That is a
	 * fact stored in the widget, and the widget is repainted from scratch by
	 * {@link #refreshAvailability()} on every run of script 8080 — roughly twice a second while the
	 * log is open. So the marker flashed and reverted, and no amount of hover handling could have
	 * fixed it: the repaint was not wrong, it simply did not know.
	 * <p>
	 * So hover is held here and every paint is derived from it, through the single function
	 * {@link #spriteFor(RingIcon)}. The general form of this is the rule the whole class keeps
	 * running into — <b>anything the plugin knows about the interface has to be re-derivable,
	 * because the client will overwrite the interface without warning</b> — and the cure is always
	 * the same: hold the fact in the plugin and make the drawing a pure function of it.
	 *
	 * <h3>Two sources, one answer</h3>
	 *
	 * A destination can be hovered on the map, through our own listeners, or in the travel log,
	 * read out of the menu each client tick. They are kept apart so neither can strand the other —
	 * the map listener fires a leave event, the log read simply stops finding a row — and the map
	 * wins when both are somehow set, because the cursor cannot be in two places and our own
	 * listener is the one with an explicit end.
	 */
	private RingDefinition hovered;

	private boolean mapShown;
	/** Whether the off-map strip is being drawn this session, which the config decides. */
	private boolean stripShown;

	/** Where the dials were on the canvas when the layer was last positioned, so a move shows up. */
	private int dialsCanvasX = Integer.MIN_VALUE;
	private int dialsCanvasY = Integer.MIN_VALUE;
	private int laidOutWidth;
	private int laidOutHeight;

	/** Guards against the filter's own widget calls provoking the rebuild that re-runs it. */
	private boolean applyingFilter;

	/**
	 * The selection, carried across the interface teardown the game performs on a favourite change.
	 * <p>
	 * Toggling a favourite does not redraw the log — it <b>closes interface 398 and 381 and reopens
	 * them</b>, which the debug trace caught in the act:
	 * <pre>
	 * log rebuilt: filterActive=true selected=DIS
	 * interface 398 closed; clearing selection DIS
	 * interface 381 closed; clearing selection none
	 * travel log opened
	 * </pre>
	 * So {@link #reset()} fired and the selection was gone before anything could be re-applied. Two
	 * rounds were spent re-applying harder on {@code ScriptPostFired}, which could never have
	 * worked: the state it needed had already been thrown away.
	 * <p>
	 * The selection is therefore parked on the way out and picked up again if the log comes back
	 * within {@link #CARRY_TICKS} ticks. Bounded on purpose: a genuine close — travelling, or
	 * walking away — is not followed by a reopen, and a selection that reappeared the next time a
	 * fairy ring was used would be a surprise rather than a convenience. Losing it costs one click
	 * on the marker; keeping it too long costs a list that is mysteriously narrowed.
	 */
	private RingDefinition carriedSelection;
	private boolean carriedMapShown;
	private int carriedAtTick = Integer.MIN_VALUE;

	/**
	 * Whether the list is currently narrowed to {@link #selected}.
	 * <p>
	 * Separate from {@code selected} because that also decides which marker is drawn as picked,
	 * and the marker stays picked after the player goes back to the full list.
	 */
	private boolean filterActive;

	@Inject
	private FairyRingMap(Client client, ClientThread clientThread, FairyRingMapConfig config,
		DefinitionLoader definitionLoader)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.config = config;
		this.definitions = definitionLoader.load(
			FairyRingDefinitions.class, "/FairyRingMap/FairyRingDefinitions.json");
		this.projection = MapProjection.of(definitions.getMap());
	}

	// ------------------------------------------------------------------ events

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() != InterfaceID.FAIRYRINGS_LOG)
		{
			return;
		}

		boolean startOnMap = config.openOnMap();

		// Built either way: someone who prefers to open on the list still needs the toggle.
		clientThread.invokeLater(() -> build(startOnMap));
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		if (event.getGroupId() == InterfaceID.FAIRYRINGS_LOG
			|| event.getGroupId() == InterfaceID.FAIRYRINGS)
		{
			reset();
		}
	}

	/**
	 * React to the game rebuilding its own travel log: re-read which rings are unlocked, and put
	 * the filter back.
	 * <p>
	 * Both jobs hang off this one script for the same reason — it is what writes the rows. Reading
	 * availability anywhere else is reading a list the server may not have filled in yet, which is
	 * how {@code AJP} came to be drawn as locked on an account that has it: availability used to be
	 * polled on a tick budget that stopped as soon as <em>any</em> ring resolved, so a row the
	 * server sent a moment later was never looked at again. An event that fires exactly when the
	 * data changes needs no budget and no retry, and it picks up a ring unlocked mid-session for
	 * free.
	 */
	@Subscribe
	public void onScriptPostFired(ScriptPostFired event)
	{
		if (event.getScriptId() != SCRIPT_LOG_REBUILD || applyingFilter)
		{
			return;
		}

		// The rebuild has already overwritten every row, so the records describe a layout that no
		// longer exists: drop them rather than write them back over a fresh, correct one. Done
		// before anything else because both of the steps below read the log as the game left it,
		// and a stale record set would make the favourites block look filtered.
		forgetRowEdits(false);

		if (container != null && !icons.isEmpty())
		{
			refreshAvailability();
		}

		// The search owns the list while it is in use — see searchActive(). filterActive is left
		// standing rather than cleared, so clearing the box brings the selection's filter back
		// instead of silently losing it.
		if (!filterActive || searchActive())
		{
			return;
		}
		applyFilter(false);
	}

	/**
	 * Take the map down when the player actually uses a code.
	 * <p>
	 * The map and the travel log do not overlap — the map covers the dials, the log is its own panel
	 * beside them — so there was never a reason for picking a destination on the map to hide it. It
	 * now stays up, with the chosen marker drawn in the selection colour, and comes down only when
	 * the player commits: clicks "Use code" on a row, at which point the dials are about to
	 * reconfigure and are what they will want to look at.
	 * <p>
	 * This is an observation of a click the player made on the game's own widget. Nothing is
	 * synthesised, no menu entry is added, and the op still goes straight to the server as it
	 * always did.
	 */
	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (!mapShown || !USE_CODE.equals(event.getMenuOption()))
		{
			return;
		}
		Widget clicked = event.getWidget();
		if (clicked == null
			|| WidgetUtil.componentToInterface(clicked.getId()) != InterfaceID.FAIRYRINGS_LOG)
		{
			return;
		}
		showMap(false);
	}

	/**
	 * Follow the dials when the client is resized.
	 * <p>
	 * The layer is positioned by subtracting two canvas locations, which is a measurement, not a
	 * binding: resizing the window moves the interface underneath it and the layer stays where it
	 * was. Watching for the move each frame costs two integer comparisons; the alternative, a
	 * layout mode that follows the parent, is not available because the layer's parent is a
	 * client layer rather than the interface it is tracking.
	 */
	@Subscribe
	public void onBeforeRender(BeforeRender event)
	{
		if (container == null || container.isHidden())
		{
			return;
		}

		Widget dials = client.getWidget(InterfaceID.Fairyrings.ROOT_RECT0);
		Point dialsAt = dials == null ? null : dials.getCanvasLocation();
		if (dialsAt == null)
		{
			return;
		}

		// The cheap test first, because this runs every frame: has the interface moved at all?
		// Keying on the dials' own canvas position is enough — the layer we draw into is a sibling
		// of the one they sit in, so the two always move together with the window.
		if (dialsAt.getX() == dialsCanvasX && dialsAt.getY() == dialsCanvasY
			&& dials.getWidth() == laidOutWidth && dials.getHeight() == laidOutHeight)
		{
			return;
		}

		if (dials.getWidth() != laidOutWidth || dials.getHeight() != laidOutHeight)
		{
			// The interface changed size, not just position, and every child offset inside the
			// layer was computed against the old one. Re-lay-out rather than shift. This costs
			// nothing now that a rebuild creates no widgets.
			build(mapShown);
			return;
		}

		Widget host = floaterFor(dials);
		Point hostAt = host == null ? null : host.getCanvasLocation();
		if (hostAt == null)
		{
			return;
		}
		if (host.getId() != container.getParentId())
		{
			build(mapShown);
			return;
		}

		dialsCanvasX = dialsAt.getX();
		dialsCanvasY = dialsAt.getY();
		placeContainer(dialsAt.getX() - hostAt.getX(), dialsAt.getY() - hostAt.getY(),
			dials.getWidth(), dials.getHeight());
	}

	// ------------------------------------------------------------------ build

	/**
	 * Put the map over the dials: claim the layer, make its children if they do not exist yet, and
	 * lay them out.
	 * <p>
	 * Called on every opening of the travel log and again whenever the dial interface changes size.
	 * Only the first call in a client session creates anything.
	 */
	private void build(boolean openOnMap)
	{
		boolean startOnMap = openOnMap;
		Widget dials = client.getWidget(InterfaceID.Fairyrings.ROOT_RECT0);
		if (dials == null)
		{
			log.debug("dial interface not present; leaving the native list alone");
			return;
		}

		Widget host = floaterFor(dials);
		Point hostAt = host == null ? null : host.getCanvasLocation();
		Point dialsAt = dials.getCanvasLocation();
		if (hostAt == null || dialsAt == null)
		{
			log.debug("no layer to draw the map into; leaving the native list alone");
			return;
		}

		// Find our layer by reading the live widget tree rather than trusting a field. Every
		// floater is swept, not just this one, because switching between fixed and resizable mode
		// moves the interface to a different layout's pair and would strand the old layer visible.
		container = claimLayer(host);
		container.setHidden(false);

		Widget[] slots = adopt(container);
		if (slots == null)
		{
			log.debug("layer has no children after adoption; leaving the native list alone");
			container = null;
			return;
		}

		dialsCanvasX = dialsAt.getX();
		dialsCanvasY = dialsAt.getY();
		placeContainer(dialsAt.getX() - hostAt.getX(), dialsAt.getY() - hostAt.getY(),
			dials.getWidth(), dials.getHeight());

		layout(slots, dials);

		if (carriedSelection != null && client.getTickCount() - carriedAtTick <= CARRY_TICKS)
		{
			selected = carriedSelection;
			filterActive = true;
			startOnMap = carriedMapShown;
		}
		carriedSelection = null;

		showMap(startOnMap);
		refreshAvailability();

		if (filterActive)
		{
			applyFilter(false);
		}

		container.revalidate();
		log.debug("map laid out: {} icons in a layer of host {} holding {} children; dials {}x{} "
				+ "at {}; map at {},{}, starting on {}",
			icons.size(), host.getId(), slots.length,
			dials.getWidth(), dials.getHeight(), dialsAt, mapOriginX, mapOriginY,
			startOnMap ? "map" : "list");
		logRenderState();
	}

	/**
	 * The one layer we draw into.
	 * <p>
	 * Sweeps every floater for layers carrying {@link #LAYER_TAG}. The first usable one in the host
	 * is claimed; every other one is hidden but keeps its tag, because switching layout mode and
	 * back should find the old layer again rather than leave a new one behind each time.
	 * <p>
	 * A layer whose child count is not the count this code builds can never be adopted by index, so
	 * it loses its tag as well and stops being found. That is only reachable after editing this
	 * class with the client still running; in a released build the count never varies.
	 */
	private Widget claimLayer(Widget host)
	{
		Widget claimed = null;

		for (int[] pair : MODAL_TO_FLOATER)
		{
			Widget floater = client.getWidget(pair[1]);
			Widget[] dynamic = floater == null ? null : floater.getDynamicChildren();
			if (dynamic == null)
			{
				continue;
			}
			for (Widget layer : dynamic)
			{
				if (!LAYER_TAG.equals(layer.getName()))
				{
					continue;
				}

				boolean usable = isAdoptable(layer);
				if (claimed == null && usable && floater.getId() == host.getId())
				{
					claimed = layer;
					continue;
				}

				layer.setHidden(true);
				layer.revalidate();
				if (!usable)
				{
					log.debug("retiring a layer of {} children; this build wants {}",
						childCount(layer), slotCount());
					layer.setName("");
				}
			}
		}

		if (claimed == null)
		{
			claimed = host.createChild(-1, WidgetType.LAYER);
			claimed.setName(LAYER_TAG);
		}
		return claimed;
	}

	/** Empty, or already holding exactly the set of children this class builds. */
	private boolean isAdoptable(Widget layer)
	{
		int have = childCount(layer);
		return have == 0 || have == slotCount();
	}

	private static int childCount(Widget widget)
	{
		Widget[] children = widget.getDynamicChildren();
		return children == null ? 0 : children.length;
	}

	/**
	 * The layer's children, made on the first opening of a client session and re-found by index on
	 * every one after it.
	 */
	private Widget[] adopt(Widget layer)
	{
		if (childCount(layer) != slotCount())
		{
			log.debug("creating {} children in a fresh layer", slotCount());
			createChildren(layer);
		}
		return layer.getDynamicChildren();
	}

	/** How many children the layer holds when this class is done with it. */
	private int slotCount()
	{
		return SLOT_FIRST_ICON + definitions.getRings().size() + 2;
	}

	private int slotInsetBorder()
	{
		return SLOT_FIRST_ICON + definitions.getRings().size();
	}

	private int slotInset()
	{
		return slotInsetBorder() + 1;
	}


	/**
	 * Make every widget the map needs, once, in {@linkplain #SLOT_BLOCK_LEFT slot} order.
	 * <p>
	 * Geometry is deliberately absent: nothing here knows where the dials are, and every position
	 * is written by {@link #layout} instead, which is what a later opening re-runs on its own. The
	 * split is the whole point — creation is the part that cannot be undone, so it happens as
	 * rarely as possible and carries as little as possible.
	 */
	private void createChildren(Widget layer)
	{
		blocker(layer);
		blocker(layer);
		blocker(layer);

		// Behind the map, so only the two pixels sticking out past it are ever seen. Decoration
		// only: it spans the close button's column, and anything blocking there would swallow the
		// one click the map is required to let through.
		panel(layer, STRIP_OPACITY);

		graphic(layer, SPRITE_MAP);

		panel(layer, STRIP_OPACITY);
		text(layer, "Not on the map", 0);

		panel(layer, TOGGLE_PANEL_OPACITY);
		Widget toggle = text(layer, "", 1);
		toggle.setHasListener(true);
		toggle.setOnOpListener((JavaScriptCallback) e -> toggle());

		graphic(layer, -1);

		for (RingDefinition ring : definitions.getRings())
		{
			createIcon(layer, ring);
		}

		// The inset is made after the markers so it draws over them.
		Widget insetFrame = panel(layer, 0);
		insetFrame.setHidden(true);

		Widget window = layer.createChild(-1, WidgetType.LAYER);
		window.setHidden(true);
		window.revalidate();
		graphic(window, SPRITE_INSET_SHEET);

		// The icon pool. Fixed size, because a widget cannot be removed from its parent once made
		// and a pool that grew with a setting would be a slow leak. They are children of the inset
		// layer rather than of the map layer for two reasons that both matter: the layer clips, so
		// an icon near a cell's edge is trimmed for free; and they live in the inset's own index
		// space, so adding forty of them moves no slot in the main layer.
		for (int i = 0; i < ICON_POOL; i++)
		{
			graphic(window, -1).setHidden(true);
		}

		// The centre marker is made last so it is drawn last, which is what puts it in front of
		// every icon. Its sprite is written per-hover rather than fixed here — see showInset.
		graphic(window, SPRITE_RING);
	}

	/**
	 * Re-point every field at its slot and put it where the current dial interface wants it.
	 * <p>
	 * Everything in here is idempotent and cheap. Being able to say that is what lets
	 * {@link #onBeforeRender} respond to a window resize by simply calling {@link #build} again.
	 */
	private void layout(Widget[] slots, Widget dials)
	{
		blockLeft = slots[SLOT_BLOCK_LEFT];
		blockRight = slots[SLOT_BLOCK_RIGHT];
		blockBelowClose = slots[SLOT_BLOCK_BELOW_CLOSE];
		border = slots[SLOT_BORDER];
		mapSprite = slots[SLOT_MAP];
		stripBar = slots[SLOT_STRIP_BAR];
		offMapLabel = slots[SLOT_OFF_MAP_LABEL];
		toggleButton = slots[SLOT_TOGGLE];
		closeFacade = slots[SLOT_CLOSE_FACADE];
		insetBorder = slots[slotInsetBorder()];
		inset = slots[slotInset()];
		// The inset layer's own children are re-acquired by index for the same reason the layer's
		// are: an index into an array this class owns exclusively survives a client rebuild where a
		// remembered reference does not.
		Widget[] insetParts = inset.getDynamicChildren();
		if (insetParts != null && insetParts.length == INSET_SLOTS)
		{
			insetImage = insetParts[INSET_SLOT_SHEET];
			insetCentre = insetParts[INSET_SLOT_CENTRE];
			insetIcons = insetParts;
		}
		else
		{
			// Only reachable after editing this class with the client still running, which changes
			// the pool size under a layer that already exists. Named rather than left to fail as a
			// null: the symptom is an inset that draws nothing, which looks like every other way a
			// widget can go missing.
			log.debug("inset layer holds {} children; this build wants {}",
				insetParts == null ? 0 : insetParts.length, INSET_SLOTS);
			insetImage = null;
			insetCentre = null;
			insetIcons = null;
		}

		int width = dials.getWidth();
		int height = dials.getHeight();
		Rect close = closeButton();

		stripShown = config.showOffMapRings() && offMapCount() > 0;

		// The strip sits below the map rather than over its bottom edge. Overlaying it bought
		// seventeen pixels of map height at the cost of covering the south coast, which is exactly
		// the part of a map you navigate by. The map is pushed up by the strip's height instead of
		// being centred, which the interface has room for: 312 + 15 against 334.
		int stripBlock = stripShown ? STRIP_HEIGHT + OFF_MAP_GAP : 0;
		int mapX = (width - mapWidth()) / 2;
		int mapY = Math.max(0, (height - mapHeight() - stripBlock) / 2);

		// One frame around the map and its strip together, drawn first so it sits behind both and
		// only its outer edge shows. Clamped into the interface because the map is 508 of 512 wide,
		// so a two-pixel frame would otherwise start at x = -1 and be clipped asymmetrically.
		int frameX = Math.max(0, mapX - BORDER_WIDTH);
		int frameY = Math.max(0, mapY - BORDER_WIDTH);
		box(border,
			frameX, frameY,
			Math.min(width - frameX, mapWidth() + 2 * BORDER_WIDTH),
			Math.min(height - frameY, mapHeight() + stripBlock + 2 * BORDER_WIDTH));

		// Deliberately not blocking: the one place the map is allowed to be clicked through is the
		// close button's column, and the map covers it. The three panels above do the blocking.
		box(mapSprite, mapX, mapY, mapWidth(), mapHeight());
		mapOriginX = mapX;
		mapOriginY = mapY;

		layoutBlockers(mapX, mapY, close);

		int stripY = mapY + mapHeight() + OFF_MAP_GAP;
		box(stripBar, mapX, stripY, mapWidth(), STRIP_HEIGHT);
		box(offMapLabel, mapX, stripY, OFF_MAP_LABEL_WIDTH, STRIP_HEIGHT);

		layoutIcons(slots, mapX, mapY, stripY);
		layoutToggle(slots, close);
		layoutCloseFacade(close);

		box(insetBorder, 0, 0, insetWidth() + 2, insetHeight() + 2);
		box(inset, 0, 0, insetWidth(), insetHeight());
		if (insetImage != null)
		{
			// The picture is the whole sheet, at its own size. The window sees one cell of it
			// because the layer clips, and which cell is decided entirely by where the picture is
			// slid to — see showInset.
			box(insetImage, 0, 0, definitions.getInset().getSheetWidth(),
				definitions.getInset().getSheetHeight());
		}
		if (insetCentre != null)
		{
			// Dead centre of the window, and it never moves: the sheet was rendered with the
			// destination at the centre of its cell, so the ring is always this pixel.
			box(insetCentre, (insetWidth() - ICON_SIZE) / 2, (insetHeight() - ICON_SIZE) / 2,
				ICON_SIZE, ICON_SIZE);
		}

		// Re-derive the inset from the hover, rather than leaving it wherever it last was.
		//
		// The boxing above sets the inset's *size* and parks it at the origin; only showInset knows
		// its position and whether it should be seen at all. Without this line those two facts are
		// the one thing left in the class that survives a rebuild unexamined — and because every
		// widget here is permanent for the client session, "unexamined" means "still showing the
		// last destination, now in the map's top-left corner".
		//
		// Two ways in, both reachable on default settings. Reopening the travel log runs reset(),
		// which clears the hover but never hid the widget, and build() then unhides the whole layer
		// with openOnMap true. Resizing the client window while hovering a marker calls build()
		// again, and the close-up jumps to the corner while the cursor is still on the ring.
		//
		// This is the class's own organising rule applied to the last thing not obeying it:
		// anything the plugin knows about the interface has to be re-derivable, because the client
		// will overwrite the interface without warning.
		showInset(hovered);

	}

	/**
	 * The three panels that stop the dials being worked through the map.
	 * <p>
	 * They tile the map's own rectangle, cut around the interface's close button, and nothing else.
	 * Two constraints meet here:
	 * <ul>
	 *   <li><b>They must be painted to block</b> — see {@link #BLOCKER_OPACITY} — so they cannot be
	 *       made transparent, only hidden behind something.</li>
	 *   <li><b>The map is the only thing that can hide them</b>, and it is opaque, so anything of
	 *       them outside its rectangle is a visible dark wash. Which is what it was.</li>
	 * </ul>
	 * Confining them to the map costs nothing because everything clickable on the dial interface is
	 * already inside it: the six rotate arms run x 8–506 and Confirm reaches y 307, against a map
	 * at x 2–510, y 3–315.
	 * <p>
	 * The close button's column above the button's own bottom edge is deliberately left open. That
	 * is the one click the map has to let through, and the map draws a copy of the button over the
	 * hole so it is still visible.
	 */
	private void layoutBlockers(int mapX, int mapY, Rect close)
	{
		int right = mapX + mapWidth();
		int bottom = mapY + mapHeight();

		box(blockLeft, mapX, mapY, Math.min(close.x, right) - mapX, bottom - mapY);

		int rightStart = Math.max(close.right(), mapX);
		box(blockRight, rightStart, mapY, right - rightStart, bottom - mapY);

		int belowTop = Math.max(mapY, close.bottom());
		box(blockBelowClose, close.x, belowTop, close.width, bottom - belowTop);
	}

	/**
	 * Place every marker, and rebuild the definition-to-widget pairing.
	 * <p>
	 * Surface rings go where the projection puts them. The dungeons, the other realms and the
	 * player-owned house share the world coordinate space but sit thousands of tiles outside
	 * anything a surface map can show, so they get a labelled strip of their own rather than an
	 * invented position.
	 */
	private void layoutIcons(Widget[] slots, int mapX, int mapY, int stripY)
	{
		icons.clear();

		int stripX = mapX + OFF_MAP_LABEL_WIDTH;
		int stripWidth = mapWidth() - OFF_MAP_LABEL_WIDTH;
		int offMapTotal = offMapCount();
		int spacing = offMapTotal == 0 ? 0 : stripWidth / offMapTotal;
		int offMapSeen = 0;

		List<RingDefinition> rings = definitions.getRings();
		for (int i = 0; i < rings.size(); i++)
		{
			RingDefinition ring = rings.get(i);
			Widget widget = slots[SLOT_FIRST_ICON + i];

			int centreX;
			int centreY;
			if (ring.isOnSurface())
			{
				centreX = mapX + projection.pixelX(ring.getWorldX()) + ring.getOffsetX();
				centreY = mapY + projection.pixelY(ring.getWorldY()) + ring.getOffsetY();
			}
			else
			{
				centreX = stripX + offMapSeen * spacing + spacing / 2;
				centreY = stripY + STRIP_HEIGHT / 2;
				offMapSeen++;
			}

			// Positions are centres; widgets are placed by their top-left corner.
			box(widget, centreX - ICON_SIZE / 2, centreY - ICON_SIZE / 2, ICON_SIZE, ICON_SIZE);
			icons.add(new RingIcon(ring, widget));
		}
	}

	/**
	 * The map toggle, in the map's own top-right corner.
	 * <p>
	 * It used to hang off the interface's close button, which was fine while the map was small and
	 * wrong once it filled the interface: the button sits at x 476 of 512, so a 52-wide label
	 * centred under it ran off the right edge and was clipped, and it landed on the map anyway.
	 * Anchoring to the map instead means it cannot leave the interface however the map is resized.
	 * <p>
	 * It gets a panel of its own because it now sits on coastline rather than on the dials'
	 * background, and orange text on a shoreline is not readable.
	 */
	private void layoutToggle(Widget[] slots, Rect close)
	{
		int x = close.x - TOGGLE_WIDTH - TOGGLE_INSET * 2;
		int y = close.y + (close.height - TOGGLE_HEIGHT) / 2;

		box(slots[SLOT_TOGGLE_PANEL], x - 2, y - 1, TOGGLE_WIDTH + 4, TOGGLE_HEIGHT + 2);
		box(toggleButton, x, y, TOGGLE_WIDTH, TOGGLE_HEIGHT);
		updateToggleButton();
	}

	/**
	 * Point the close-button copy at the real button's own sprite, and put it over it.
	 * <p>
	 * It carries no listener and blocks nothing: the click goes through it, through the gap left in
	 * the blocking panels, and lands on the game's own button.
	 */
	private void layoutCloseFacade(Rect close)
	{
		Widget real = client.getWidget(InterfaceID.Fairyrings.ROOT_GRAPHIC27);
		if (real == null)
		{
			log.debug("no close button to copy; the map will cover it");
			closeFacade.setSpriteId(-1);
			return;
		}
		closeFacade.setSpriteId(real.getSpriteId());
		box(closeFacade, close.x, close.y, close.width, close.height);
	}

	/** Lay the layer exactly over the dials, so every child position is an offset from their corner. */
	private void placeContainer(int x, int y, int width, int height)
	{
		container.setOriginalX(x);
		container.setOriginalY(y);
		container.setOriginalWidth(width);
		container.setOriginalHeight(height);
		container.revalidate();

		laidOutWidth = width;
		laidOutHeight = height;
	}

	/**
	 * Hide every layer of ours anywhere in the tree, children and all.
	 * <p>
	 * Hidden rather than emptied: the children are the ones the next opening adopts, and a dynamic
	 * child cannot be removed from its parent anyway. Hiding the layer takes its whole subtree out
	 * of both the draw pass and the hit test, which is the entire requirement.
	 * <p>
	 * Found by tag rather than by remembered reference, for the reason given on {@link #LAYER_TAG}.
	 */
	private void hideContainers()
	{
		for (int[] pair : MODAL_TO_FLOATER)
		{
			Widget floater = client.getWidget(pair[1]);
			Widget[] dynamic = floater == null ? null : floater.getDynamicChildren();
			if (dynamic == null)
			{
				continue;
			}
			for (Widget child : dynamic)
			{
				if (LAYER_TAG.equals(child.getName()))
				{
					child.setHidden(true);
					child.revalidate();
				}
			}
		}
		container = null;
		dialsCanvasX = Integer.MIN_VALUE;
		dialsCanvasY = Integer.MIN_VALUE;
	}

	// ------------------------------------------------------------------ widget factories

	/**
	 * A panel that swallows clicks without drawing anything, so the interface underneath cannot be
	 * worked blind. See {@link #INVISIBLE} for why it is transparent rather than hidden.
	 */
	private Widget blocker(Widget parent)
	{
		Widget panel = parent.createChild(-1, WidgetType.RECTANGLE);
		panel.setFilled(true);
		panel.setTextColor(BACKING_COLOUR);
		panel.setOpacity(BLOCKER_OPACITY);
		panel.setClickMask(BLOCKER_CLICK_MASK);
		panel.setNoClickThrough(true);
		panel.revalidate();
		return panel;
	}

	/** A filled rectangle that is meant to be seen: something for text to sit on. */
	private Widget panel(Widget parent, int opacity)
	{
		Widget panel = parent.createChild(-1, WidgetType.RECTANGLE);
		panel.setFilled(true);
		panel.setTextColor(BACKING_COLOUR);
		panel.setOpacity(opacity);
		panel.revalidate();
		return panel;
	}

	private Widget text(Widget parent, String content, int xAlignment)
	{
		Widget widget = parent.createChild(-1, WidgetType.TEXT);
		// A text widget with no font renders nothing at all, silently.
		widget.setFontId(FontID.PLAIN_12);
		widget.setText(content);
		widget.setTextColor(LABEL_COLOUR);
		widget.setTextShadowed(true);
		widget.setXTextAlignment(xAlignment);
		widget.setYTextAlignment(1);
		widget.revalidate();
		return widget;
	}

	private Widget graphic(Widget parent, int spriteId)
	{
		Widget widget = parent.createChild(-1, WidgetType.GRAPHIC);
		widget.setSpriteId(spriteId);
		widget.revalidate();
		return widget;
	}

	/**
	 * One marker, with the three listeners it keeps for the life of the client session.
	 * <p>
	 * Installed here rather than on every availability refresh because the ring a marker stands for
	 * never changes: only what is drawn on it does. Everything the listeners need is captured from
	 * the definition, which is immutable and loaded once.
	 */
	private Widget createIcon(Widget parent, RingDefinition ring)
	{
		Widget widget = graphic(parent, SPRITE_RING);

		// The code, then the destination. The off-map strip is a row of identical icons with
		// nothing but this tooltip to tell them apart, and the code is what the dials are set to.
		widget.setName(hoverName(ring, true));
		widget.setHasListener(true);
		// Deliberately no mouse-over or mouse-leave listener. Hover is read from the menu instead,
		// in onClientTick, because a per-widget listener cannot answer the question once markers
		// overlap - and at this scale several of them always do.
		widget.setOnOpListener((JavaScriptCallback) e -> select(ring));
		widget.revalidate();
		return widget;
	}

	private static void box(Widget widget, int x, int y, int width, int height)
	{
		widget.setOriginalX(x);
		widget.setOriginalY(y);
		widget.setOriginalWidth(Math.max(0, width));
		widget.setOriginalHeight(Math.max(0, height));
		widget.revalidate();
	}

	// ------------------------------------------------------------------ geometry helpers

	/** Where the close button is, measured if possible and taken from the cache dump if not. */
	private Rect closeButton()
	{
		Widget close = client.getWidget(InterfaceID.Fairyrings.ROOT_GRAPHIC27);
		if (close == null || close.getWidth() <= 0)
		{
			return new Rect(CLOSE_X, CLOSE_Y, CLOSE_WIDTH, CLOSE_HEIGHT);
		}
		return new Rect(close.getRelativeX(), close.getRelativeY(), close.getWidth(), close.getHeight());
	}

	/** Immutable box, purely to keep the blocker arithmetic readable. */
	private static final class Rect
	{
		private final int x;
		private final int y;
		private final int width;
		private final int height;

		private Rect(int x, int y, int width, int height)
		{
			this.x = x;
			this.y = y;
			this.width = width;
			this.height = height;
		}

		private int right()
		{
			return x + width;
		}

		private int bottom()
		{
			return y + height;
		}
	}

	/**
	 * The layer drawn in front of the one the dial interface opened into.
	 * <p>
	 * Falls back to that same layer if the pairing is not recognised: the map then draws behind the
	 * dials, which is wrong but visible, rather than not at all.
	 */
	private Widget floaterFor(Widget dials)
	{
		Widget modal = dials.getParent();
		if (modal == null)
		{
			return null;
		}

		for (int[] pair : MODAL_TO_FLOATER)
		{
			if (pair[0] == modal.getId())
			{
				Widget floater = client.getWidget(pair[1]);
				if (floater != null)
				{
					return floater;
				}
			}
		}

		log.debug("unrecognised modal layer {}; drawing into it directly", modal.getId());
		return modal;
	}

	/**
	 * Where the widgets we just made ended up, and whether the client knows about our sprites. Both
	 * failures look identical from in front of the screen — nothing appears — so the only cheap way
	 * to tell them apart is to say so in the log.
	 */
	private void logRenderState()
	{
		if (!log.isDebugEnabled())
		{
			return;
		}
		log.debug("sprites registered: map={} ring={}",
			client.getSpriteOverrides().containsKey(SPRITE_MAP),
			client.getSpriteOverrides().containsKey(SPRITE_RING));
		log.debug("container hidden={} at {} {}x{}; map at {} {}x{}; strip shown={}",
			container.isHidden(), container.getCanvasLocation(),
			container.getWidth(), container.getHeight(),
			mapSprite.getCanvasLocation(), mapSprite.getWidth(), mapSprite.getHeight(),
			stripShown);
	}

	/** What the menu shows for a marker. Greyed rather than withheld when the ring is locked. */
	private String hoverName(RingDefinition ring, boolean available)
	{
		String name = ring.getCode() + " - " + ring.getDestination();
		return available ? name : LOCKED_COLOUR_TAG + name + "</col>";
	}

	private int offMapCount()
	{
		return (int) definitions.getRings().stream().filter(r -> !r.isOnSurface()).count();
	}

	// ------------------------------------------------------------------ hover inset

	/**
	 * A closer look at wherever the cursor is.
	 *
	 * <h3>The mechanism, which did not change</h3>
	 *
	 * A picture is placed inside a small layer at a negative offset. The layer clips, so it is a
	 * <b>window</b> onto that picture, and the window is moved by sliding the picture rather than by
	 * cropping anything. One sprite, one widget, no per-frame work.
	 *
	 * <h3>What did change: the picture behind the window</h3>
	 *
	 * It used to be the shipped world map. That map is 0.18 px/tile, so the "close-up" magnified
	 * nothing, and it has no pixels at all for the thirteen destinations that sit thousands of tiles
	 * north of the surface. Magnifying by shipping a higher-resolution world map does not scale:
	 * Java holds a decoded PNG at {@code w * h * 4} bytes, and 1.5 px/tile over the world is about
	 * 44 MB resident for a window that only ever shows 76 x 58 of it.
	 *
	 * <p>So the picture is now a <b>sheet of 54 pre-rendered close-ups</b>, each 76 x 58 at
	 * 1 px/tile — an eightfold magnification, and the off-map destinations rendered from their own
	 * regions like anywhere else. 684 x 348 is about 0.95 MB, and every pixel of it is a pixel the
	 * window can actually show.
	 *
	 * <p>The arithmetic got <em>simpler</em> rather than harder, which is the sign the sheet was the
	 * right shape. Sliding to a cell is two multiplications on the cell index. There is no
	 * projection here at all any more: the projection is baked into the picture at render time, by
	 * the tool that made it.
	 *
	 * <h3>Where the window is placed on screen</h3>
	 *
	 * Beside the marker, read from the marker <b>widget</b> rather than re-derived from the
	 * projection. The two agree for a surface ring, but the off-map destinations have no projected
	 * position at all — they are drawn in the strip below the map — and asking the widget where it
	 * is works for both without a second case.
	 */
	private void showInset(RingDefinition ring)
	{
		if (inset == null || insetBorder == null || insetImage == null)
		{
			return;
		}

		// Off-surface is no longer a reason to refuse: those twelve have cells of their own now. The
		// only refusal left is a destination with no cell, which is the player-owned house and only
		// the player-owned house — its coordgrid is 0 because where it goes is per-player.
		RingIcon icon = ring == null ? null : iconFor(ring);
		boolean show = icon != null && ring.getInsetCell() != null && config.showHoverInset();
		inset.setHidden(!show);
		insetBorder.setHidden(!show);
		if (!show)
		{
			return;
		}

		int cellW = insetWidth();
		int cellH = insetHeight();

		// The marker's centre, in the layer's coordinates — which the inset shares, being a sibling.
		int markerX = icon.widget.getOriginalX() + ICON_SIZE / 2;
		int markerY = icon.widget.getOriginalY() + ICON_SIZE / 2;

		// Above the marker where there is room, below it otherwise, and never off the map. Clamped
		// to the map's rectangle rather than the layer's so an off-map marker in the strip still
		// gets an inset over the map instead of over the dials' background.
		int x = clamp(markerX - cellW / 2, mapOriginX, mapOriginX + mapWidth() - cellW);
		int y = markerY - cellH - INSET_GAP;
		if (y < mapOriginY)
		{
			y = markerY + INSET_GAP;
		}
		y = clamp(y, mapOriginY, mapOriginY + mapHeight() - cellH);

		place(insetBorder, x - 1, y - 1);
		place(inset, x, y);

		// Slide the sheet so the wanted cell lands under the window. This is the whole of what used
		// to be the projection.
		int cell = ring.getInsetCell();
		int cols = definitions.getInset().getCols();
		place(insetImage, -(cell % cols) * cellW, -(cell / cols) * cellH);

		// The centre of the cell is the destination itself, so it gets the same marker the map is
		// drawing for it — through the same function, so the inset cannot disagree with the map
		// about whether a ring is locked, selected or favourited.
		if (insetCentre != null)
		{
			insetCentre.setSpriteId(spriteFor(icon));
		}

		layoutInsetIcons(ring);
	}

	/**
	 * Draw the map icons over the close-up, in priority order, as far as the settings allow.
	 *
	 * <h3>Three filters, applied in this order and only this order</h3>
	 *
	 * <ol>
	 *   <li><b>Eligibility</b> — is this kind of icon switched on at all.</li>
	 *   <li><b>Spacing</b> — walking down the priority order, an icon that would land within the
	 *       minimum gap of one already accepted is dropped. Because the walk is in priority order,
	 *       the icon that survives a collision is always the more useful of the two.</li>
	 *   <li><b>Count</b> — take the first N of what is left.</li>
	 * </ol>
	 *
	 * Spacing before count, not after. The other way round fills the budget with a cluster of
	 * high-priority icons stacked on the same building and then has nothing left for the rest of the
	 * cell.
	 *
	 * <h3>Draw order is the reverse of survival order</h3>
	 *
	 * Widgets paint in child order, so the last one written is nearest the front. Survival ranks the
	 * most useful icon first; assigning them to the pool in that order would put the bank underneath
	 * whatever happened to overlap it. So the accepted list is walked backwards on the way into the
	 * pool, and the priority list ends up governing both which icons are seen and which is on top —
	 * which is the whole reason for keeping the icons out of the PNG.
	 */
	private void layoutInsetIcons(RingDefinition ring)
	{
		if (insetIcons == null)
		{
			return;
		}

		List<IconPlacement> placements = ring.getInsetIcons();
		Set<MapIcon> allowed = config.insetIcons();
		int gap = config.insetIconGap();
		int max = Math.min(config.maxInsetIcons(), ICON_POOL);
		int size = config.insetIconSize();

		List<IconPlacement> ranked = new ArrayList<>();
		if (placements != null && max > 0)
		{
			for (IconPlacement placement : placements)
			{
				MapIcon kind = MapIcon.forSprite(placement.getSprite());
				// A sprite this build has never heard of is kept rather than dropped: the data and
				// the client come from the same cache, so an unknown icon means a game update this
				// enum has not caught up with, and hiding it would hide the evidence.
				if (kind != null && !allowed.contains(kind))
				{
					continue;
				}
				ranked.add(placement);
			}
			// Priority first, then distance from the centre. The generator already wrote them
			// nearest-first, and a stable sort keeps that as the tie-break within one kind.
			ranked.sort(Comparator.comparingInt(FairyRingMap::rankOf));
		}

		int drawn = 0;
		List<IconPlacement> accepted = new ArrayList<>();
		for (IconPlacement placement : ranked)
		{
			if (drawn >= max)
			{
				break;
			}
			if (tooClose(accepted, placement, gap))
			{
				continue;
			}
			accepted.add(placement);
			drawn++;
		}

		// Backwards into the pool, so the highest-priority icon is written last and drawn on top.
		for (int i = 0; i < ICON_POOL; i++)
		{
			Widget widget = insetIcons[INSET_SLOT_FIRST_ICON + i];
			if (i >= accepted.size())
			{
				widget.setHidden(true);
				continue;
			}
			IconPlacement placement = accepted.get(accepted.size() - 1 - i);
			widget.setSpriteId(placement.getSprite());
			box(widget, placement.getX() - size / 2, placement.getY() - size / 2, size, size);
			widget.setHidden(false);
		}
	}

	/**
	 * Where an icon sits in the priority order. Unknown sprites rank last, behind every named one.
	 */
	private static int rankOf(IconPlacement placement)
	{
		MapIcon kind = MapIcon.forSprite(placement.getSprite());
		return kind == null ? MapIcon.values().length : kind.ordinal();
	}

	/**
	 * Whether an icon would land on top of one already accepted.
	 * <p>
	 * A square test rather than a radius, because the thing being kept apart is a square sprite, and
	 * because it is the same test the sheet renderer used when the icons were still baked in — two
	 * different answers to "are these too close" would have been a difference nobody could see.
	 */
	private static boolean tooClose(List<IconPlacement> accepted, IconPlacement candidate, int gap)
	{
		if (gap <= 0)
		{
			return false;
		}
		for (IconPlacement other : accepted)
		{
			if (Math.abs(other.getX() - candidate.getX()) < gap
				&& Math.abs(other.getY() - candidate.getY()) < gap)
			{
				return true;
			}
		}
		return false;
	}

	/** The window's size, which is one cell of the sheet. Data, not a constant — see the JSON. */
	private int insetWidth()
	{
		return definitions.getInset().getCellWidth();
	}

	private int insetHeight()
	{
		return definitions.getInset().getCellHeight();
	}

	private static void place(Widget widget, int x, int y)
	{
		widget.setOriginalX(x);
		widget.setOriginalY(y);
		widget.revalidate();
	}

	private static int clamp(int value, int min, int max)
	{
		return Math.max(min, Math.min(max, value));
	}

	private void updateToggleButton()
	{
		if (toggleButton == null)
		{
			return;
		}
		toggleButton.setText(mapShown ? "Hide map" : "Show map");
		toggleButton.setAction(0, mapShown ? "Hide map" : "Show map");
	}

	// ------------------------------------------------------------------ interaction

	/**
	 * Repaint against the hover, if it changed.
	 * <p>
	 * Repainting all 55 markers rather than the two that changed is deliberate: it is 55 integer
	 * writes, it happens only on a transition, and it means there is exactly one code path that
	 * decides what a marker looks like. The alternative — patching the old and new markers — is the
	 * same class of bug this exists to avoid, a second place that knows how a marker should be drawn.
	 */
	private void setHovered(RingDefinition now)
	{
		if (now == hovered)
		{
			return;
		}
		hovered = now;
		repaintIcons();
		// Shown for locked destinations too: where a ring you cannot reach yet actually goes is
		// the more interesting half of the question.
		showInset(now);
	}

	/**
	 * What the cursor is on, read from the menu — a marker on the map, or a row in the travel log.
	 *
	 * <h3>Why the menu is the only source, for both</h3>
	 *
	 * The travel log's rows could never use listeners: a widget holds <b>one</b> listener per event,
	 * so installing ours would discard the one script 8080 already put there, and the script
	 * reinstalls its own on every rebuild — a standing race over a single field.
	 * <p>
	 * The map's markers <em>did</em> use listeners, and that was wrong for a different reason found
	 * in game 2026-09-10. <b>The cursor can be inside several markers at once</b>, and it routinely
	 * is: {@code CIP} and {@code AJS} are eighteen tiles apart and overlap at any scale the panel can
	 * hold. A single remembered marker cannot describe that, so the sequence
	 * {@code enter(CIP), enter(AJS), leave(AJS)} cleared the hover entirely while the cursor was
	 * still inside {@code CIP} — and no fresh {@code enter} was coming, because the client already
	 * considered the cursor inside it. The close-up simply vanished until the cursor left and
	 * returned. The same fault also let the plugin light one marker while the game's tooltip named
	 * the other, which is what made it visible.
	 * <p>
	 * Tracking a set of hovered markers would fix the disappearance and not the disagreement. The
	 * client already resolves overlap for its own purposes — that is exactly what it built this
	 * frame's menu from, and what the tooltip shows. So the plugin reads that answer instead of
	 * computing a second one. <b>One source, and it agrees with the tooltip by construction.</b>
	 * <p>
	 * This is pure observation: the menu is not modified and no entry is added. {@code ClientTick}
	 * fires after the menu is built, which is why menu work in RuneLite conventionally happens there.
	 * <p>
	 * A log row is matched by <b>component id</b>, which works only because the 64 code rows and the
	 * ten favourite rows are static widgets and so carry ids of their own. A <b>marker</b> cannot be:
	 * it is a dynamic child and reports its parent's id, the same finding that broke the undo map two
	 * sessions ago. It is matched on <b>widget identity</b> instead — {@code MenuEntry.getWidget()}
	 * returns the very object this class created, and {@code Widget} does not override
	 * {@code equals}, so {@code ==} is the whole comparison.
	 */
	@Subscribe
	public void onClientTick(ClientTick event)
	{
		if (container == null || !mapShown || icons.isEmpty())
		{
			setHovered(null);
			return;
		}

		// A right-click menu freezes the entries at the moment it opened, so they stop describing
		// the cursor. Leave the hover where it is rather than acting on a stale answer.
		if (client.isMenuOpen())
		{
			return;
		}

		// The LAST matching entry, not the first. RuneLite's menu array is ordered with the
		// left-click default at the end, so where two markers overlap this is the one the game's
		// own tooltip names and the one a click would take.
		RingDefinition found = null;
		for (MenuEntry entry : client.getMenu().getMenuEntries())
		{
			RingDefinition ring = ringForEntry(entry);
			if (ring != null)
			{
				found = ring;
			}
		}

		setHovered(found);
	}

	/**
	 * The destination a menu entry belongs to, or null if the entry is neither one of our markers
	 * nor a travel log row.
	 * <p>
	 * The two are found by different means, and the difference is forced rather than chosen. A
	 * marker is a <b>dynamic</b> child of our layer, so {@code getId()} gives its parent's packed id
	 * and every marker reports the same number — it can only be matched on <b>object identity</b>.
	 * A log row is a <b>static</b> widget with an id of its own, and identity is not available for
	 * it because the client rebuilds those widgets underneath us.
	 * <p>
	 * Favourite rows are matched through {@link #favouriteSlots} rather than a table, because which
	 * of the ten slots holds which destination is not fixed — it is whatever the server last sent.
	 */
	private RingDefinition ringForEntry(MenuEntry entry)
	{
		Widget widget = entry.getWidget();
		if (widget == null)
		{
			return null;
		}

		// Our own markers first. Widget does not override equals, so this is an identity test
		// against the very objects createIcon made.
		for (RingIcon icon : icons)
		{
			if (icon.widget == widget)
			{
				return icon.ring;
			}
		}

		if (WidgetUtil.componentToInterface(widget.getId()) != InterfaceID.FAIRYRINGS_LOG)
		{
			return null;
		}

		String code = FairyRingRows.codeForComponent(widget.getId());
		if (code == null)
		{
			int[] faveRows = FairyRingRows.faveBlockRows();
			for (Map.Entry<String, Integer> fave : favouriteSlots.entrySet())
			{
				if (faveRows[fave.getValue()] == widget.getId())
				{
					code = fave.getKey();
					break;
				}
			}
		}
		return code == null ? null : ringFor(code);
	}

	private RingDefinition ringFor(String code)
	{
		for (RingIcon icon : icons)
		{
			if (icon.ring.getCode().equals(code))
			{
				return icon.ring;
			}
		}
		return null;
	}

	private void select(RingDefinition ring)
	{
		// Queued rather than run inline: this is called from a widget listener, which the client
		// invokes in the middle of drawing the interface.
		clientThread.invokeLater(() ->
		{
			if (!isUnlocked(ring.getCode()))
			{
				return;
			}

			selected = ring;
			filterActive = true;
			// The map stays up. What changes is the marker: it takes the selection colour, and the
			// row left standing in the log is given the same one, so the two views agree.
			repaintIcons();
			if (!searchActive())
			{
				applyFilter(true);
			}
		});
	}

	/**
	 * The Show map / Hide map button. Either direction drops the selection and puts the log back.
	 * <p>
	 * Symmetric on purpose. The button is the one control that means "I am done with what is on
	 * screen", so it reads badly if one press of it clears the filter and the other leaves a list
	 * still narrowed to one row with no visible reason. Everything the plugin did to the log is
	 * undone, the marker goes back to its ordinary colour, and the player gets the stock list.
	 * <p>
	 * Deferred in both directions because this runs inside a widget listener, which the client
	 * invokes in the middle of drawing the interface.
	 */
	private void toggle()
	{
		boolean show = !mapShown;
		clientThread.invokeLater(() ->
		{
			filterActive = false;
			forgetRowEdits(true);
			selected = null;
			showMap(show);
			// Availability can have changed since the map was built — a quest finished, a diary
			// completed — and this is also what takes the selection colour back off the marker.
			refreshAvailability();
		});
	}

	/**
	 * Show or hide everything that belongs to the map.
	 * <p>
	 * The blocking panels go with it, and that is not cosmetic: they are what stops the dials being
	 * turned through the map, so leaving them up with the map down would leave the interface
	 * unusable. All three pieces are tracked here for that reason — only one used to be, which is
	 * why a dark wash with an X-shaped hole in it stayed on screen after "Hide map".
	 */
	private void showMap(boolean show)
	{
		mapShown = show;

		setHidden(blockLeft, !show);
		setHidden(blockRight, !show);
		setHidden(blockBelowClose, !show);
		setHidden(border, !show);
		setHidden(mapSprite, !show);
		setHidden(closeFacade, !show);
		setHidden(stripBar, !show || !stripShown);
		setHidden(offMapLabel, !show || !stripShown);


		for (RingIcon icon : icons)
		{
			icon.widget.setHidden(iconHidden(icon));
		}
		if (!show)
		{
			// Cleared rather than just un-drawn: the markers are hidden, so no leave event is
			// coming for whichever one the cursor was over, and a hover left set would come back
			// with the map.
			hovered = null;
			showInset(null);
		}

		// The travel log is deliberately left alone. It was hidden here when the map was going to
		// replace it; the map ended up over the dials instead, which do not overlap the log panel
		// at all, so hiding the list only produced an empty panel next to a working map — and made
		// "the log went blank" a symptom of every other bug in this class.
		updateToggleButton();
	}

	private static void setHidden(Widget widget, boolean hidden)
	{
		if (widget != null)
		{
			widget.setHidden(hidden);
		}
	}

	/**
	 * Three independent reasons a marker is not drawn: the map is down, the player asked for locked
	 * rings to be left off, or it belongs to the off-map strip and the strip is switched off.
	 */
	private boolean iconHidden(RingIcon icon)
	{
		return !mapShown || icon.hiddenByConfig || (!icon.ring.isOnSurface() && !stripShown);
	}

	/**
	 * Leave one destination standing in the log: the one picked on the map, at the very top.
	 * <p>
	 * Doing this by hand rather than through the log's search box is not only the auto-reviewable
	 * route, it is the more capable one. The game clips its search string to thirty characters and
	 * four destinations share their first thirty, so its own filter could never narrow those to a
	 * single row.
	 * <p>
	 * <b>There are two sets of rows and the target can be in either.</b> The 64 ordinary rows live
	 * in {@code CONTENTS}; ten favourite rows live in {@code FAVES}, which is a child of
	 * {@code CONTENTS} drawn above them. The server puts a destination in exactly one set — favourite
	 * it and its ordinary row is blanked — so the filter hides both sets and then puts back whichever
	 * one holds the target:
	 * <ul>
	 *   <li><b>Target is an ordinary row</b> — collapse {@code FAVES} to zero height and move the
	 *       row to y = 0. Collapsing rather than hiding, because a hidden block still leaves the
	 *       empty bordered box the game drew for it.</li>
	 *   <li><b>Target is a favourite</b> — leave it where it is inside {@code FAVES}, at y = 0, and
	 *       shrink the block to just that row.</li>
	 * </ul>
	 * Either way the answer ends up at the top of the panel with nothing above it, which is what
	 * moving the row below a full favourites block failed to do: the block can be taller than the
	 * 215 px viewport, and the row then landed below the fold looking like a filter that had failed.
	 * <p>
	 * The target's own star is always left visible, so "Add favourite" and "Remove Favourite" both
	 * still work on the one destination on screen.
	 * <p>
	 * Called both when a marker is clicked and again after every rebuild of the game's own list, so
	 * it has to be idempotent: it reads the layout it finds rather than assuming the one it left.
	 *
	 * @param undoFirst whether our own edits are still standing and have to be reversed first. False
	 *                  when the game has just rebuilt the list — it has already overwritten every
	 *                  row, so the recorded positions describe a layout that no longer exists and
	 *                  writing them back would corrupt a good one.
	 */
	private void applyFilter(boolean undoFirst)
	{
		Widget contents = client.getWidget(InterfaceID.FairyringsLog.CONTENTS);
		Widget faves = client.getWidget(InterfaceID.FairyringsLog.FAVES);
		if (selected == null || contents == null || !isUnlocked(selected.getCode()))
		{
			log.debug("filter not applied: selected={} contents={} unlocked={}",
				selected == null ? "none" : selected.getCode(), contents != null,
				selected != null && isUnlocked(selected.getCode()));
			return;
		}

		String code = selected.getCode();
		Integer slot = favouriteSlots.get(code);
		Widget target = entryRow(code);
		if (target == null)
		{
			log.debug("filter not applied: no row for {} (fave slot {})", code, slot);
			return;
		}

		applyingFilter = true;
		try
		{
			forgetRowEdits(undoFirst);

			// Pair the target with the code label the game drew beside it before moving anything:
			// for an ordinary row the shared y is the only handle on that pairing, and it stops
			// being true the moment the row moves.
			Widget targetCode = slot == null
				? codeLabelFor(contents, target, code)
				: client.getWidget(FairyRingRows.faveBlockCodeLabels()[slot]);
			Widget targetStar = slot == null
				? faveWidget(code)
				: client.getWidget(FairyRingRows.faveBlockStars()[slot]);

			hideEveryEntryExcept(contents, target, targetCode, targetStar);

			int top = 0;
			moveTo(target, top);
			moveTo(targetCode, top);
			moveTo(targetStar, top);
			// Moving a hidden widget leaves it hidden. Stating the end state rather than assuming
			// the path to it makes the filter idempotent: it is re-applied after every rebuild of
			// the game's list, against a layout it did not create.
			show(target);
			show(targetCode);
			show(targetStar);

			if (faves != null && faves.getParentId() == contents.getId())
			{
				// The block is collapsed rather than hidden either way: when the target is inside
				// it, to the height of that one row; when it is not, to nothing.
				resizeTo(faves, slot == null ? 0 : target.getHeight() + BELOW_ROW_GAP);
			}

			// The list is one row long now, so the scrollbar should say so, and anything scrolled
			// to before would leave the panel looking empty.
			rememberRow(contents);
			contents.setScrollHeight(target.getHeight() + BELOW_ROW_GAP);
			contents.setScrollY(0);
			contents.revalidateScroll();

			highlightedRow = target;
			highlightedColour = target.getTextColor();
			target.setTextColor(HIGHLIGHT_COLOUR);
		}
		finally
		{
			applyingFilter = false;
		}
	}

	/**
	 * Hide every drawn log entry apart from one — both row sets, and for each row the star and the
	 * code label that belong to it.
	 * <p>
	 * A visible entry is three widgets, not one. Hiding only the row leaves its star and its code
	 * floating in the gap where the row used to be.
	 */
	private void hideEveryEntryExcept(Widget contents, Widget target, Widget targetCode,
		Widget targetStar)
	{
		int[] faveRows = FairyRingRows.faveBlockRows();
		int[] faveCodes = FairyRingRows.faveBlockCodeLabels();
		int[] faveStars = FairyRingRows.faveBlockStars();
		for (int slot = 0; slot < faveRows.length; slot++)
		{
			Widget row = client.getWidget(faveRows[slot]);
			if (row == null || row == target)
			{
				continue;
			}
			hideUnless(client.getWidget(faveCodes[slot]), targetCode);
			hideUnless(client.getWidget(faveStars[slot]), targetStar);
			hide(row);
		}

		for (Map.Entry<String, Integer> entry : FairyRingRows.all().entrySet())
		{
			Widget row = client.getWidget(entry.getValue());
			if (row == null || row == target)
			{
				continue;
			}
			// Guarded rather than trusted: the target's label is resolved before anything moves,
			// so if any other row's lookup returns it, that lookup is wrong and hiding it would
			// blank the one entry the filter exists to show.
			hideUnless(codeLabelFor(contents, row, entry.getKey()), targetCode);
			hideUnless(faveWidget(entry.getKey()), targetStar);
			hide(row);
		}
	}

	/** Hide a widget unless it is the one the filter is keeping. */
	private void hideUnless(Widget widget, Widget keep)
	{
		if (widget != keep)
		{
			hide(widget);
		}
	}

	/** The counterpart to {@link #hide}, recording the same way so the undo is symmetric. */
	private void show(Widget widget)
	{
		if (widget == null || !widget.isHidden())
		{
			return;
		}
		rememberRow(widget);
		widget.setHidden(false);
	}

	/** The favourite star beside a row, which has to be hidden along with it. */
	private Widget faveWidget(String code)
	{
		Integer component = FairyRingRows.faveFor(code);
		return component == null ? null : client.getWidget(component);
	}

	/**
	 * A visible row is drawn as two widgets: the static row carrying the destination and the op,
	 * and a label the game creates beside it holding the three-letter code.
	 *
	 * <h3>Pair them by the label's own text, not by where it sits</h3>
	 *
	 * This used to return the first dynamic child sharing the row's {@code relativeY}, which is a
	 * pairing inferred from a coincidence of layout rather than read from the widget. It has two
	 * ways to go wrong and both are silent: any other dynamic child that happens to share that y
	 * wins because it is found first, and the pairing stops being true the instant anything moves.
	 * The visible cost was a filtered row with an empty space where its code should be — the label
	 * had been hidden on some <em>other</em> row's behalf, and moving it afterwards does not bring
	 * it back.
	 * <p>
	 * The label carries the answer itself: its text is the code, spaced and colour-tagged, which is
	 * what {@link LogRow#codeFromTarget(String)} already parses. Matching on that is an identity
	 * rather than an inference, and it survives every move.
	 * <p>
	 * The positional match is kept as a fallback for the one case text cannot cover — a row the
	 * game draws without a code, which the player-owned house shows it is willing to do.
	 */
	private Widget codeLabelFor(Widget contents, Widget row, String code)
	{
		Widget[] dynamic = contents.getDynamicChildren();
		if (dynamic == null)
		{
			return null;
		}

		Widget byPosition = null;
		for (Widget child : dynamic)
		{
			if (code != null && code.equals(LogRow.codeFromTarget(child.getText())))
			{
				return child;
			}
			if (byPosition == null && child.getRelativeY() == row.getRelativeY())
			{
				byPosition = child;
			}
		}
		return byPosition;
	}

	// ------------------------------------------------------------------ availability

	/**
	 * Grey out the rings this account cannot use.
	 * <p>
	 * The game only gives a row text once the destination is unlocked, so the interface already
	 * knows the answer. Reading it there beats shipping our own table of quest, diary and Sailing
	 * requirements, which would be wrong within a month.
	 * <p>
	 * Called from {@link #build}, from the toggle, and on every run of the game's own list builder.
	 * There is no retry budget and no resolved flag: the previous version stopped retrying the
	 * moment one ring came back unlocked, which left every row the server filled in a fraction
	 * later reading as Locked for the rest of the session.
	 */
	private void refreshAvailability()
	{
		refreshFavourites();

		// Only meaningful while the log is as the game left it: our own filter hides rows too, and
		// reading it mid-filter would report the whole map as filtered out by a search nobody typed.
		boolean readSearch = rowChanges.isEmpty();
		boolean searching = readSearch && searchActive();

		for (RingIcon icon : icons)
		{
			icon.available = isUnlocked(icon.ring.getCode());
			if (readSearch)
			{
				icon.matched = !searching || rowMatchesSearch(icon.ring.getCode());
			}

			icon.hiddenByConfig = !icon.available && config.hideUnavailable();
			icon.widget.setHidden(iconHidden(icon));
			icon.widget.setSpriteId(spriteFor(icon));

			// A locked ring keeps its menu entry, greyed. Knowing which destination a marker is
			// and that you cannot reach it yet is more useful than an unnamed dot, and it is how
			// the map doubles as a checklist of what is still to unlock. Clicking one does
			// nothing: select() will not act on a row the game has left empty.
			icon.widget.setAction(0, icon.available ? "Show" : "Locked");
			icon.widget.setName(hoverName(icon.ring, icon.available));
		}
	}

	/**
	 * Which destinations are favourited, read back out of the log's favourites block.
	 * <p>
	 * There is no other source. Nothing client-side records a favourite: "Add Favourite" and
	 * "Remove Favourite" are raw server-bound ops with no listener, interface 381 carries no
	 * {@code onVarTransmit} on any child, and the list builder never reads a favourite flag — it
	 * fills ten fixed rows from an enum and puts the code in the label beside each. So the block as
	 * rendered is the client's only copy of the answer, and reading the labels back is reading it
	 * at the source rather than inferring it.
	 *
	 * <h3>Why a slot's reality is read from its text and not from whether it is drawn</h3>
	 *
	 * This used to skip any slot whose code label was hidden, which is a different question from
	 * the one being asked. <b>Script 8080 hides favourite rows the current search does not match</b>
	 * — hiding is how the search is implemented — so typing anything in the search box emptied this
	 * map, {@link #isUnlocked(String)} then found only a blanked ordinary row for each favourite,
	 * and every favourite on the map turned grey.
	 * <p>
	 * The script's own test for "this slot holds a destination" is the row's text length, and the
	 * search does not touch text: the match is computed into a local and spent on
	 * {@code if_sethide}. So the text is a property of what the server sent and the visibility is a
	 * property of what the player typed, and only the first of those is the question here. Reading
	 * the row's text and taking the code from the label beside it keeps this map a description of
	 * the account rather than of the current view.
	 */
	private void refreshFavourites()
	{
		// Only ever read while the log is as the game left it. Our own filter hides nine of the ten
		// favourite rows, so reading the block mid-filter would report the favourites as gone and
		// then draw every one of them as locked.
		if (!rowChanges.isEmpty())
		{
			return;
		}

		favouriteSlots.clear();
		int[] rows = FairyRingRows.faveBlockRows();
		int[] labels = FairyRingRows.faveBlockCodeLabels();
		for (int slot = 0; slot < labels.length; slot++)
		{
			Widget row = client.getWidget(rows[slot]);
			if (row == null || !LogRow.isAvailable(row.getText()))
			{
				continue;
			}
			Widget label = client.getWidget(labels[slot]);
			String code = label == null ? null : LogRow.codeFromTarget(label.getText());
			if (code != null)
			{
				favouriteSlots.put(code, slot);
			}
			// A slot the server filled but we cannot name is left out rather than guessed at. It was
			// worth worrying about — the player-owned house's row text is written dynamically by the
			// server, so it was the one destination whose code label might not be filled like the
			// other nine — and it reads correctly, confirmed in game 2026-09-07.
		}
	}

	/**
	 * Whether the player has typed something into the log's search box.
	 *
	 * <h3>Why the map mirrors the search instead of competing with it</h3>
	 *
	 * A search and a map selection are two answers to the same question, and while both are running
	 * the log shows one of them and the map shows the other. That disagreement is where the search
	 * bugs lived: with the favourites read fixed, the map would go on showing 55 usable markers
	 * beside a log narrowed to three rows, which is worse than the old bug because it looks correct.
	 * <p>
	 * So the search wins outright. While one is in use the plugin stops filtering, leaves the game's
	 * own list exactly as the game built it, and greys out every marker the search excluded — the
	 * map becomes a picture of the search. Clearing the box hands control back, and a selection made
	 * in the meantime re-applies on the next rebuild, so nothing is lost.
	 * <p>
	 * Reading the varc is observation. The plugin never writes it; writing it is what would need
	 * {@code client.runScript} to make the list follow, and that is the API the Hub gates.
	 */
	private boolean searchActive()
	{
		String search = client.getVarcStrValue(VarClientID.FAIRYRINGS_SEARCHSTRING);
		return search != null && !search.isEmpty();
	}

	/**
	 * Whether the search left this destination's row on screen.
	 * <p>
	 * Visibility is the right test here and the wrong one in {@link #refreshFavourites()}, which is
	 * worth stating because the two look alike. Hiding <em>is</em> how script 8080 implements the
	 * search, so "is it drawn" is exactly the question when the question is about the search — and
	 * exactly the wrong question when it is about what the account owns.
	 */
	private boolean rowMatchesSearch(String code)
	{
		Widget row = entryRow(code);
		return row != null && !row.isHidden();
	}

	/** The row a code is actually drawn in: its favourite row if it has one, its own row if not. */
	private Widget entryRow(String code)
	{
		Integer slot = favouriteSlots.get(code);
		return slot == null
			? rowWidget(code)
			: client.getWidget(FairyRingRows.faveBlockRows()[slot]);
	}

	/**
	 * Whether the account can travel to a code.
	 * <p>
	 * Two places to look, because the server draws a destination in exactly one of them. An
	 * ordinary row carries text only while the destination is unlocked <em>and not favourited</em>;
	 * favourite it and the server blanks that row and fills a favourites-block row instead. Reading
	 * only the ordinary row is why every favourite came up Locked.
	 */
	private boolean isUnlocked(String code)
	{
		if (favouriteSlots.containsKey(code))
		{
			return true;
		}
		Widget row = rowWidget(code);
		return row != null && LogRow.isAvailable(row.getText());
	}

	/** Repaint every marker without re-reading the interface. */
	private void repaintIcons()
	{
		for (RingIcon icon : icons)
		{
			icon.widget.setSpriteId(spriteFor(icon));
		}
	}

	/**
	 * The one function that decides what a marker looks like. Every paint goes through it.
	 * <p>
	 * Split from {@link #baseSprite(RingIcon)} rather than folded into it because the two answer
	 * different questions: {@code baseSprite} is what the destination <em>is</em>, this is what it
	 * looks like <em>now</em>. Keeping the transient layer on the outside is what lets the periodic
	 * repaint be unconditional and still not stamp on a hover.
	 * <p>
	 * <b>A locked marker deliberately does not react to hover.</b> The hover colour means "this is
	 * the one you are pointing at, and you can have it"; showing it on a destination a click will
	 * not take is a promise the map cannot keep. The inset still opens, because where a ring you
	 * have not unlocked goes is worth knowing.
	 */
	private int spriteFor(RingIcon icon)
	{
		if (!icon.available || !icon.matched)
		{
			return SPRITE_RING_LOCKED;
		}
		if (icon.ring == hovered)
		{
			return SPRITE_RING_HOVER;
		}
		return baseSprite(icon);
	}

	/**
	 * What colour a marker is drawn in, in priority order: locked, selected, favourited, plain.
	 * Hovering swaps to the bright sprite and back to whichever of these it was.
	 * <p>
	 * The order is the order in which the information is worth having. Locked outranks everything
	 * because it is the one state that changes what a click does. Selected outranks favourited
	 * because there is only ever one of it and it is the answer to the question just asked. All
	 * four share one hand-drawn shading ramp, recoloured rather than redrawn — at eleven pixels the
	 * ramp is the only thing that makes a marker read as a ring, so two sprites drawn separately
	 * stop looking like the same object in two colours. See {@code tools/recolour-sprite.py}.
	 */
	private int baseSprite(RingIcon icon)
	{
		if (!icon.available)
		{
			return SPRITE_RING_LOCKED;
		}
		if (icon.ring == selected)
		{
			return SPRITE_RING_SELECTED;
		}
		if (favouriteSlots.containsKey(icon.ring.getCode()))
		{
			return SPRITE_RING_FAVE;
		}
		return SPRITE_RING;
	}

	private Widget rowWidget(String code)
	{
		Integer component = FairyRingRows.componentFor(code);
		return component == null ? null : client.getWidget(component);
	}

	private RingIcon iconFor(RingDefinition ring)
	{
		for (RingIcon icon : icons)
		{
			if (icon.ring == ring)
			{
				return icon;
			}
		}
		return null;
	}

	// ------------------------------------------------------------------ geometry

	private int mapWidth()
	{
		return definitions.getMap().getSpriteWidth();
	}

	private int mapHeight()
	{
		return definitions.getMap().getSpriteHeight();
	}

	// ------------------------------------------------------------------ undo

	private void hide(Widget widget)
	{
		if (widget == null || widget.isHidden())
		{
			return;
		}
		rememberRow(widget);
		widget.setHidden(true);
	}

	private void moveTo(Widget widget, int y)
	{
		if (widget == null)
		{
			return;
		}
		rememberRow(widget);
		widget.setOriginalY(y);
		widget.revalidate();
	}

	private void resizeTo(Widget widget, int height)
	{
		if (widget == null)
		{
			return;
		}
		rememberRow(widget);
		widget.setOriginalHeight(height);
		widget.revalidate();
	}

	/**
	 * Record a row's state the first time it is touched, so every change can be undone. The game
	 * rebuilds this list itself whenever the player sorts or searches, so leaving rows hidden
	 * behind us would only be masked by luck.
	 */
	private void rememberRow(Widget widget)
	{
		rowChanges.computeIfAbsent(widget, RowState::new);
	}

	/**
	 * Drop the record of everything the filter touched, optionally putting it back on the way.
	 * <p>
	 * The two cases are genuinely different and conflating them is what made the filter fragile.
	 * When the plugin is undoing its own work — going back to the map, closing the interface — the
	 * recorded values are still the truth and get written back. When the <em>game</em> has just
	 * rebuilt the list, they are a description of a layout that no longer exists: restoring them
	 * would stamp stale positions over a fresh, correct one. Then the only right move is to
	 * forget.
	 */
	private void forgetRowEdits(boolean restore)
	{
		if (restore)
		{
			for (RowState state : rowChanges.values())
			{
				state.restore();
			}
			if (highlightedRow != null)
			{
				highlightedRow.setTextColor(highlightedColour);
			}
		}
		rowChanges.clear();
		highlightedRow = null;
	}

	/**
	 * Put everything back. Called when either fairy ring interface closes and when the plugin
	 * stops, so a player who turns the plugin off mid-session gets the stock log back without
	 * relogging.
	 * <p>
	 * The layer and its children survive this: they are hidden, not discarded, and the next opening
	 * adopts them. Only the fields pointing at them are dropped, because the fields are the part
	 * that can go stale.
	 */
	public void reset()
	{
		// Park the selection before anything is cleared. Guarded on selected being set, because the
		// game closes both fairy ring interfaces and this runs twice — the second call must not
		// overwrite what the first saved with the nothing it now sees.
		if (selected != null && filterActive)
		{
			carriedSelection = selected;
			carriedMapShown = mapShown;
			carriedAtTick = client.getTickCount();
		}

		forgetRowEdits(true);
		hideContainers();

		icons.clear();
		favouriteSlots.clear();
		selected = null;
		hovered = null;
		filterActive = false;
		blockLeft = null;
		blockRight = null;
		blockBelowClose = null;
		border = null;
		mapSprite = null;
		stripBar = null;
		offMapLabel = null;
		toggleButton = null;
		closeFacade = null;
		inset = null;
		insetBorder = null;
		insetImage = null;
		insetCentre = null;
		insetIcons = null;
		mapShown = false;
		stripShown = false;
	}

	/**
	 * What a widget looked like before the filter touched it.
	 * <p>
	 * Height is recorded as well as position because the favourites block is collapsed rather than
	 * hidden, and scroll position as well as scroll height because {@code CONTENTS} is a scroll
	 * container: putting the height back without the offset, or without
	 * {@link Widget#revalidateScroll()}, leaves the panel showing a window onto the wrong part of a
	 * list that is otherwise correct.
	 */
	private static final class RowState
	{
		private final Widget widget;
		private final boolean hidden;
		private final int y;
		private final int height;
		private final int scrollHeight;
		private final int scrollY;

		private RowState(Widget widget)
		{
			this.widget = widget;
			this.hidden = widget.isHidden();
			this.y = widget.getOriginalY();
			this.height = widget.getOriginalHeight();
			this.scrollHeight = widget.getScrollHeight();
			this.scrollY = widget.getScrollY();
		}

		private void restore()
		{
			widget.setHidden(hidden);
			widget.setOriginalY(y);
			widget.setOriginalHeight(height);
			widget.setScrollHeight(scrollHeight);
			widget.setScrollY(scrollY);
			widget.revalidate();
			widget.revalidateScroll();
		}
	}

	/** A definition paired with the widget drawing it. */
	private static final class RingIcon
	{
		private final RingDefinition ring;
		private final Widget widget;
		private boolean available;
		private boolean hiddenByConfig;
		/**
		 * Whether the log's search box, if one is in use, left this destination's row on screen.
		 * True when no search is running, so the ordinary case needs no special handling.
		 */
		private boolean matched = true;

		private RingIcon(RingDefinition ring, Widget widget)
		{
			this.ring = ring;
			this.widget = widget;
		}
	}
}
