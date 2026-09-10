# fairy-ring-map — design

Replaces the fairy ring **travel log** with a clickable map of Gielinor. Written in Teleport
Maps' shape (one component class, positions in a definitions JSON, sprites registered from a
`SpriteDefinitions.json`) so it can be lifted into a fork of `MJHylkema/spirit-tree-map`.

Status: **built, unit tested, and verified in game (2026-09-07).** Every behaviour described below
was seen working, apart from the items under *Not done* and the hover inset rebuilt 2026-09-08,
which is built and unit tested but not yet confirmed in game.

---

## The constraint everything else follows from

**Travel log rows have no client-side listener.** `widget.getOnOpListener()` is `null` for every
row in interface 381 `FAIRYRINGS_LOG`, and no clientscript ever attaches one — verified against
the local cache in the interface definition and by grepping all 9,783 disassembled scripts.
"Use code" is a raw server-bound component op. Full evidence in
`D:\AI\PROJECTS\OSRS\cache-tools\README.md`.

So the map **cannot** fire a travel. Teleport Maps' `client.runScript(widget.getOnOpListener())`
works for the mushtree only because that listener chain ends in `if_resume_pausebutton`, a CS2
opcode that sends a packet. Nothing in the fairy ring interfaces sends a packet from a script.

**The map is therefore a filter, not a trigger.** Decided 2026-09-05.

## The interaction

1. `WidgetLoaded` for group 381 → on the client thread, build the map over the dials and show it.
2. Click a ring icon → hide every other entry in **both** row sets, collapse the favourites block,
   pull the chosen row to y = 0 and colour it orange. **The map stays up**, with the chosen marker
   drawn in the same orange.
3. The user clicks the single native row. That is the real, unmodified game click.
4. `MenuOptionClicked` for "Use code" → the map comes down, because the dials are about to
   reconfigure and are what the player wants to look at.
5. "Show map" or "Hide map" clears the selection and undoes every widget change on the way.

Two clicks instead of scanning 55 codes, and the plugin never constructs a server action.

### Why the map no longer hides on a marker click

Changed 2026-09-07. It used to hide at step 2, which was a habit carried over from the design
where the map was going to *replace* the log. It doesn't: the map covers the dials, the log is its
own panel beside them, and **they do not overlap**. Hiding the map at step 2 threw away the view
the player was using for no reason, one click before it was actually in the way.

The map now comes down on the event that means the player has committed — `MenuOptionClicked` with
option `"Use code"` on a component of group 381. That covers both the ordinary rows
(`actions=[Use code, Add Favourite]`) and the ten favourite rows
(`[Use code, Remove Favourite]`) without listing components, and it is an observation of a click
the player made on the game's own widget. Nothing is synthesised.

**The selection is one state shown in two places**, so it is drawn in one colour: `#FF981F`, which
is `ring-selected.png`, the highlighted row, and the game's own label orange. The row used to be
white, which made the player check which of the two things they had picked.

### Marker colours, and where "favourited" comes from

Added 2026-09-07 — favourites get their own colour rather than being read as
something wrong.

Priority: **locked → selected → favourited → plain.** Locked outranks everything because it is the
one state that changes what a click does; selected outranks favourited because there is only ever
one of it and it is the answer to the question just asked.

All five sprites share **one hand-drawn shading ramp**, recoloured rather than redrawn — at 11 px
the ramp is the only thing that makes a marker read as a ring, and two sprites drawn separately
stop looking like the same object in two colours. `cache-tools/tools/recolour-sprite.py` does it:
each pixel is the base colour scaled by a brightness ratio, so reproducing the ratio against a new
base is exact.

**Finding "which codes are favourited" has exactly one route, and it is not obvious.** Verified
against the cache 2026-09-07:

- `Add Favourite` / `Remove Favourite` are **raw server-bound ops with no listener**, exactly like
  `Use code`. Nothing runs client-side when one is clicked.
- Interface 381 has **no `onVarTransmit` on any child**. The only listeners in the whole group are
  four `onLoad`s and some `onMouseOver`s.
- **No clientscript sets a row's text.** All 9,783 were disassembled; only 8080 reads it. The text
  comes from the server, and so does the decision about which row set a destination is drawn in.

So the rendered favourites block **is** the client's only copy of the answer, and the plugin reads
the ten `FAVE_CODE_n` labels back out of it. `LogRow.codeFromTarget` already parses that format —
the labels hold `<col=ff981f>A I S</col>`. The three arrays share an index, so slot *n* gives the
row, its code label and its star together.

Because the answer is only readable from the rendered block, it is refreshed **only while the log
is as the game left it** — the filter itself hides nine of the ten favourite rows, and reading it
mid-filter would report the favourites as gone and draw every one of them locked.

**Read from the row's text, not from whether the row is drawn.** The same trap caught this read a
second time through the search box; see *While a search is in use, the search owns the list*.

### The filter has to be a standing state, not a one-off edit

Added 2026-09-05, after the filter was seen reverting about a second after each selection.

The game re-lays-out its own list from clientscript **8080**, which hides every row and then
un-hides, positions and sizes the ones matching the current search and sort. It runs on its own —
after the interface settles, after a favourite changes, after a sort — and it overwrites anything
the plugin did to the rows. A filter applied once therefore survives only until the next rebuild.

So the plugin subscribes to `ScriptPostFired` for 8080 and re-applies. Hooking the script rather
than enumerating the triggers means the filter survives whatever caused the redraw. This is
observation only: `ScriptPostFired` reports what the client has already run, and nothing here
invokes a script.

8080 has no name upstream. Every path that redraws the list reaches it — the four callers are 402
(RuneLite's `FAIRYRINGS_SORT_UPDATE`, the sort button's handler), 8084, 8156 and 8191 — verified
by disassembling all 9,783 clientscripts and grepping for callers.

The structural consequence is subtler than the hook. The plugin records each row's state so it can
undo itself. Once the game rebuilds the list, those records describe a layout that no longer
exists, and writing them back would stamp stale positions over a correct one. So "undo" splits in
two: **restore** when the plugin is reversing its own work, **forget** when the game has already
done it. That is `forgetRowEdits(boolean restore)`.

### Why the filtering is done by hand, not through the search box

The first implementation wrote `VarClientID.FAIRYRINGS_SEARCHSTRING` and called
`client.runScript(ScriptID.FAIRYRINGS_SORT_UPDATE, ...)` to make the game redraw its own list.
It was elegant — the game did the layout — and it is **removed**, on review-risk grounds.

`client.runScript` is an API the Plugin Hub gates. The reviewers' public rulings are consistent
and blunt: *"Remove the usage of `client.runScript`"* (PR 14785), *"you cannot invoke
`ScriptID.CHAT_SEND`"* (15073), *"you cannot invoke `ScriptID.CHAT_PROMPT_INIT`"* (15459),
*"`ScriptID.COLLECTION_DRAW_LIST` is not an approved scriptid for invocation in hub plugins"*
(14932). There is an allowlist of script ids; it is not published, and nothing suggests
`FAIRYRINGS_SORT_UPDATE` is on it. This is the same class of problem that killed the Varlamore
tracker's first submission — `client.hopToWorld` pushed it to manual review and it auto-closed
after a week.

The replacement does the filtering with widget calls only: hide every visible row except the one
clicked, move that row and its code label to the top, resize the scroll area. Every change is
recorded and undone on the way out. This is the ordinary, auto-reviewable way to change an
interface, and it is what the other fairy ring plugins on the Hub already do to these same rows.

It is also strictly more capable. The game clips its search string to thirty characters, and four
destinations share their first thirty (`ALR`/`DIP`, `CIR`/`BLS`, and two both starting
`<br>kebos lowlands south of mo`), so the game's own filter could never have narrowed those to a
single row. Doing it by hand has no such limit.

## Widgets are created once and afterwards only moved

Added 2026-09-07. This is the load-bearing structural decision in `FairyRingMap`, and it is
forced rather than chosen.

**A dynamic widget cannot be removed from its parent through the RuneLite API.**
`setChildren(null)` throws `only static widgets may have children`. `deleteAllChildren()` on a
widget that is *itself* a dynamic child compiles, runs, and does nothing — measured by counting
our layer's children across successive openings of the travel log: 63, 126, 189, 252, 300.

So every widget the plugin makes is permanent for the life of the client session, and the only
way not to accumulate them is not to make them twice.

The class therefore splits what used to be one `build()` into two:

- **`createChildren(layer)`** — runs once per client session. Makes all 66 widgets in a **fixed
  order** and gives them their permanent properties: type, sprite, font, listeners. It knows
  nothing about where the dials are.
- **`layout(slots, dials)`** — runs on every opening and on every resize. Re-acquires each widget
  **by index** out of `layer.getDynamicChildren()` and writes positions and sizes.

**The index is the handle.** That is the point worth keeping: a remembered `Widget` reference goes
stale when the client rebuilds the tree, but an array index into a layer this class owns
exclusively does not. `claimLayer` finds the layer by its name tag; `adopt` then decides by child
count whether it is ours to reuse (`0` → create, `slotCount()` → reuse, anything else → retire the
layer by dropping its tag, which is only reachable after editing this class with the client still
running).

Two consequences look like accidents in the code otherwise, so they are stated here:

- **Config-dependent widgets are built anyway and merely hidden.** The off-map strip exists even
  when it is switched off, because a child count that varies with a setting would shift every
  later index.
- **Listeners are installed at creation, never on a refresh.** A ring marker's definition never
  changes; only what is drawn on it does. `refreshAvailability` used to re-install three lambdas
  per marker per call.

The direct win is that `onBeforeRender` can now answer a window resize by simply calling `build()`
again — re-laying-out is cheap in a way that recreating never was.

## The blocking panels are hidden by being covered, not by being transparent

Added 2026-09-07, after two failed attempts at the same problem.

Three panels stop the dials being turned through the map. They used to be drawn as a dark wash over
the whole interface, which had to go: it did not read as a frame, and the panel is cut into
three pieces around the close button, so it showed an X-shaped hole exactly where the button is.

**`setNoClickThrough(true)` on its own does not block.** Proved in game at three opacities — 255,
254 and 40 — with the panels correctly sized and positioned each time, confirmed from the debug
trace. The flag says "do not let a click fall through *me*", but the client only asks that of
widgets it already considers clickable, and **a plain rectangle with no ops and no click mask is
not one**. The flag was being read on a widget that was never consulted.

Core's own `WikiPlugin` shows the pattern: it sets `setClickMask(79872)` *alongside*
`setNoClickThrough(true)`. Decoded, 79872 is `USE_GROUND_ITEM | USE_NPC | USE_OBJECT | USE_WIDGET`
— the "can be the target of a use-on" bits. The reason to choose those over giving the panel an op
is that **none of the menu-option bits are among them**: the panel becomes something the client
hit-tests without becoming something that puts an entry in the right-click menu.

The panels are also sized to tile **exactly the map sprite's rectangle**, cut around the close
button. `gielinor.png` is 508 × 312 of RGB with no alpha channel and no `tRNS` chunk, so it is
fully opaque and nothing of them is ever seen — which is what let the opacity go back to a solid 40
without the dark wash coming back.

What used to show was everything they covered *outside* the map. That is now not covered at all,
which costs nothing: everything clickable on the dial interface is already inside the map's
rectangle — the six rotate arms run x 8–506 and Confirm reaches y 307, against a map at x 2–510,
y 3–315.

All three pieces are hidden together in `showMap(false)`. Only one used to be tracked, which is why
a dark wash with a hole in it stayed on screen after "Hide map" — and, less visibly, why the dials
stayed unclickable with the map down.

## Availability is read on the list builder, not on a timer

Added 2026-09-07, fixing `AJP` (Varlamore: Avium Savannah) drawing as Locked on an account that has
it.

A row only carries text once the destination is unlocked, so the interface is the source of truth
for availability — no shipped table of quest and diary requirements to go stale. But the server
fills those rows in *after* the widget loads, so the read has to be timed.

The old timing was a five-tick retry budget ending in `availabilityResolved = any`: retrying
stopped the moment **one** ring came back unlocked, so every row the server sent a fraction later
was never looked at again and stayed Locked for the session.

The fix is to stop guessing when the data arrives and hook the event that *is* the data arriving:
`ScriptPostFired` for **8080**, the game's own list builder, which is what writes the rows. No
budget, no resolved flag, no `GameTick` subscription — and a ring unlocked mid-session is picked up
for free.

**The remaining cause was found in game and then explained from the cache, 2026-09-07.**
Every *favourited* destination read as locked. The reason is a fact about the interface that no
amount of reading clientscripts would have produced on its own:

> **Nothing in the cache sets a travel log row's text.** All 9,783 clientscripts were disassembled
> and grepped; only script 8080 so much as *reads* it. Row text arrives straight from the server.

So the client has no model of "which rings are unlocked" at all — it renders what it is sent. And
what the server sends is **two parallel row sets, and a destination is in exactly one of them**:

| | 64 ordinary rows (`AIP`…`DLS`) | 10 favourite rows (`FAVE_1`…`FAVE_10`) |
|---|---|---|
| Parent | `CONTENTS` | `FAVES`, itself a child of `CONTENTS` |
| Ops | `Use code`, `Add Favourite` | `Use code`, `Remove Favourite` |
| Carries text when | unlocked **and not favourited** | unlocked **and favourited** |

Favourite a destination and the server **blanks its ordinary row** and fills a favourite row
instead. 8080 then hides the blanked row, because its only visibility test is
`if_gettext(row).length > 0`. Reading availability off the ordinary row alone therefore reports
every favourite as locked — which is exactly what was on screen.

Two consequences run through the whole class:

- **`isUnlocked(code)` looks in both places.** Present in the favourites block ⇒ unlocked, no text
  read needed.
- **The filter has to be able to leave a favourites-block row standing**, because for a favourited
  destination that is the only row there is.

*What I got wrong first, and why:* the earlier read of 8080 was correct — it does decide purely on
text length and the search string, and it never reads a favourite flag — and I concluded from that
that favouriting could not blank a row. The flaw was assuming the client was the only actor.
Testing it directly against several destinations showed the observation stood; the disassembly then
explained it rather than contradicting it. **A script dump bounds what the client does, not what
the interface does.**

The component mapping was checked at the same time and is fine: DB table 89 column 4
(`text component`) against every `InterfaceID.FairyringsLog.<CODE>` constant, all 64, no mismatches.

## Two bugs that both came from a dynamic child having no id of its own

The finding from 2026-09-05 — **`getId()` on a dynamic child returns its parent's packed id** —
turned out to have a second victim, found 2026-09-07.

`rowChanges`, the undo record, was a `Map<Integer, RowState>` keyed on `widget.getId()`. The game
draws each visible entry's three-letter code as a **dynamic child of `CONTENTS`**, so all fifty-odd
of those labels report the same id — and so does `CONTENTS` itself. The map therefore recorded the
*first* label and silently discarded every later one **and the scroll container's own height**.

Restoring then un-hid one label, left the rest hidden, and left `CONTENTS` still sized to a
one-row list. That is what "the travel log goes blank after Show map" was.

It is now `Map<Widget, RowState>`. `Widget` does not override `equals`, so keying on the object is
an identity map, which is what was wanted all along.

`RowState` also gained **height** (the favourites block is collapsed, not hidden) and **scroll
position**, and its restore calls `revalidateScroll()`. Putting a scroll container's height back
without the offset, or without that call, leaves a correct list shown through the wrong window.

## The filter, now that there are two row sets

Both sets are hidden, then whichever one holds the target is put back at the very top:

- **Ordinary row** → `FAVES` collapsed to zero height, row moved to y = 0.
- **Favourite row** → left where it is inside `FAVES`, at y = 0, block shrunk to that one row.

Collapsing rather than hiding, because hiding `FAVES` leaves the empty bordered box the game drew
for it — which is what "all favorites go blank" was.

The target's own star is always left visible, so **Add Favourite** and **Remove Favourite** both
still work on the one destination on screen, and unfavouriting it transitions cleanly: the server
moves it back to the ordinary set, 8080 fires, and the filter re-applies against the other row.

Superseded on the way: moving the row below the favourites block (2026-09-07 morning), which fixed
an overlap and created a worse bug — `FAVES` can be taller than the 215 px viewport, so the row
landed below the fold looking like a filter that had silently failed.

## Hover is state the plugin holds, not a colour written onto a widget

Added 2026-09-07, fixing a marker that flashed white and reverted within half a second.

The mouse-over listener used to call `setSpriteId(SPRITE_RING_HOVER)` directly. That stores the
fact *in the widget* — and `refreshAvailability()` repaints all 55 markers from scratch on every
run of script 8080, which the game fires roughly twice a second while the log is open. The repaint
was not wrong; it simply had no way to know.

So hover is a field, and **one function maps model to sprite**: `spriteFor(icon)` layers the
transient state (hovered) over `baseSprite(icon)`, which is the durable one (locked, selected,
favourited, plain). Every paint in the class goes through it, so an unconditional repaint
reproduces the hover instead of destroying it.

This is the third instance of one rule and it is now the class's organising principle:

> **Anything the plugin knows about the interface has to be re-derivable, because the client will
> overwrite the interface without warning.** A stale `Widget` reference, an id-keyed undo map, a
> selection lost across a rebuild, and now a hover colour — same failure, same cure: hold the fact
> in the plugin and make the drawing a pure function of it.

A **locked marker deliberately does not react to hover.** The hover colour means "this is the one
you are pointing at, and you can have it"; showing it where a click will do nothing is a promise
the map cannot keep. The inset still opens, because where a ring you have not unlocked goes is the
more interesting half of the question.

### The travel log lights the map, read from the menu rather than from a listener

Added 2026-09-07. Hovering a row in the log lights its marker and pops the inset — the inverse of
what the map already did, so a code you already know can be found on the map.

The obvious implementation is `setOnMouseOverListener` on each of the 74 rows, and it is wrong
twice over. **A widget holds one listener per event**, so installing ours would discard the one
script 8080 already put there and the rows would lose the game's own hover colour; and 8080
reinstalls its listeners on every rebuild, so the plugin would be in a standing race with the
client over a single field.

The client already computes the answer for its own purposes: what is under the cursor is what it
built this frame's menu from. So the plugin reads `client.getMenu().getMenuEntries()` on
`ClientTick`, which fires after that menu is built. Nothing is modified and no entry is added —
this is observation, the same posture as `ScriptPostFired`. A right-click menu freezes the entries
at the moment it opened, so the read is skipped while one is open rather than acting on an answer
that has stopped describing the cursor.

The row is matched by **component id**, which is available only because the 64 code rows and the
ten favourite rows are static widgets and so carry ids of their own. The three-letter labels beside
them are dynamic children of `CONTENTS` and all report its id — the same finding that broke the
undo map — so they could never be told apart this way. Favourite rows are resolved through
`favouriteSlots` instead of a table, because which of the ten slots holds which destination is
whatever the server last sent.

## While a search is in use, the search owns the list

Added 2026-09-07, replacing a set of tangled search-and-selection bugs with a rule.

Two separate faults met here. The first was diagnosed and is worth keeping because the shape
recurs: `refreshFavourites()` decided a favourite slot was real from `FAVE_CODE_n.isHidden()`, and
**hiding is how script 8080 implements the search**. Typing anything therefore emptied
`favouriteSlots`; `isUnlocked()` then found only the blanked ordinary row each favourite leaves
behind, and every favourite on the map turned grey.

> Visibility is a property of what the player typed. Text is a property of what the server sent.
> The script's own test for "this slot holds a destination" is the row's text length, and the
> search never touches text — the match is computed into a local and spent on `if_sethide`. So the
> read is now text-based, and the map describes the account rather than the current view.

The second fault was a family of oddities that could not be pinned down — a code label going empty
beside a filtered row, differing by whether the destination was favourited and whether the search
matched by code or by wildcard. Rather than chase them, the rule that dissolves them:

**A search and a map selection are two answers to the same question, and the search wins.** While
the search box is non-empty the plugin does not filter at all, leaves the list exactly as the game
built it, and greys out every marker the search excluded. The map becomes a picture of the search.

Fixing only the first fault would have made this worse in a way that looks right: with the
favourites read corrected, the map would show 55 usable markers beside a log narrowed to three
rows, with nothing on screen saying why.

`filterActive` is deliberately **left standing rather than cleared**, so clearing the search box
brings the selection's filter back on the next rebuild instead of silently losing it. Selecting a
marker during a search records the selection and colours it but does not filter, for the same
reason.

The search string is read from `VarClientID.FAIRYRINGS_SEARCHSTRING` and never written. Writing it
is what would need `client.runScript` to make the list follow, and that is the API the Hub gates —
see *Why the filtering is done by hand*.

## The hover inset is a window onto a sheet of pre-rendered close-ups

Rebuilt 2026-09-08. The clipping-window mechanism did not change — it was proven the session before
and is the reason this was worth doing in two steps. What changed is the picture behind the window.

**The mechanism.** A picture is placed inside a small layer at a negative offset. The layer clips,
so it is a window onto that picture, and the window is moved by sliding the picture rather than by
cropping anything. One sprite, one widget, no per-frame work.

**What the picture used to be.** The shipped world map, at 0.1534 px/tile — so the "close-up"
magnified nothing, and it had no pixels at all for the thirteen destinations that sit thousands of
tiles north of the surface. Magnifying by shipping a higher-resolution world map does not scale:
Java holds a decoded PNG at `w x h x 4` bytes, so 1.5 px/tile over a 2816 x 1728 tile world is
about **44 MB resident** for a window that only ever shows 76 x 58 of it.

**What it is now.** A sheet of **54 pre-rendered close-ups**, 76 x 58 each at 1 px/tile, tiled
9 x 6 into 684 x 348 — **952,128 bytes resident**, and every pixel of it is a pixel the window can
show. Five and a half times the map's own scale, and the off-map destinations rendered from their own
regions like anywhere else. 54 is 9 x 6 exactly, so the grid wastes no cells.

**The cell size was set by the Plugin Hub, not by taste.** The hub counts a bundled image as
`width * height * 4` bytes flat, whatever the PNG's own colour depth — so palette-quantising cannot
move the number, and only the dimensions can.

The threshold is not published, so it was bracketed by submitting. Measured 2026-09-10 against
`runelite/plugin-hub` PR 16339:

| cell | sheet | bytes | result |
|---|---|---|---|
| 132 x 100 | 1056 x 700 | 2,956,800 | rejected |
| 112 x 85 | 1008 x 510 | 2,056,320 | rejected |
| 109 x 83 | 981 x 498 | 1,954,152 | rejected |
| 95 x 73 | 855 x 438 | 1,497,960 | rejected |
| 88 x 67 | 792 x 402 | 1,273,536 | rejected |
| **76 x 58** | **684 x 348** | **952,128** | **accepted** |

So the limit lies in **[952,128, 1,273,536)**. Note that 2,056,320 is itself under 2 MiB and still
failed, so it is neither 2 MiB nor 2,000,000; **1 MiB (1,048,576) fits every observation**. The only
untried size below the bracket is 80 x 60 at 1,036,800, which is 1.09x the accepted window — not
worth a submission.

The shipped world map (508 x 312, 633,984) passes and is the other half of the budget, so leave
headroom if it is ever re-rendered larger.

The cost is window area, not detail — magnification stays at 1 px/tile throughout. 76 tiles still
spans a whole town, which is what the close-up is for.

The arithmetic got *simpler*, which is the sign the sheet was the right shape:

```java
place(insetImage, -(cell % cols) * cellW, -(cell / cols) * cellH);
```

There is no projection in the inset at all any more. The projection happens once, at render time,
in the tool that made the sheet.

**Scale was chosen after seeing both rendered.** 2 px/tile is clearer per tile but the window
then covers 66 x 50 tiles, which loses the shape of Zanaris and the Abyss and shows only the core of
a dense town. Same sheet size either way.

### Where the window is placed, and the one destination that has none

The inset is positioned from the **marker widget**, not re-derived from the projection. They agree
for a surface ring, but the twelve off-map destinations have no projected position — they are drawn
in the strip below the map — and asking the widget where it is covers both with no second case.

`showInset` no longer refuses off-surface rings. It refuses exactly one thing: a ring with no
`insetCell`, which is `DIQ` and only `DIQ`.

**`insetCell` is a boxed `Integer` on purpose.** An `int` would be 0 for a ring the JSON gives no
cell, and 0 is a real cell — `AIQ`'s. The player-owned house would have silently shown Mudskipper
Point, which is the worst of the three possible outcomes because it looks like it works.

### The centre marker replaces the game's own icon

Sprite 1504 (Transportation) sits at the centre of every cell, because it *is* the fairy ring being
hovered. The generator drops it within 5 px of centre and the plugin draws its own marker there
instead, through `spriteFor(icon)` — the same function the map's marker goes through, so the inset
cannot disagree with the map about whether a destination is locked, favourited or selected. The
game's icon could not have said any of that.

Only the centre one is dropped. A spirit tree or a canoe station elsewhere in the cell is an
ordinary 1504 and survives. Four cells contain no icons at all and show a bare close-up: the Abyssal
Area, Yu'biusk, Gorak's Plane, and north of the Arceuus Library.

## The icons are widgets over the sheet, not pixels in it

This reverses an earlier decision and it is the one that shaped everything else.

Baking the icons into the sheet works and looks fine, and it was built first. It is a dead end for
one reason: **no config setting can re-cull a pixel.** Once they are in the PNG, "show fewer",
"show only these kinds" and "draw them smaller" are all impossible, and the priority order can only
ever govern what survives rendering — never what is drawn on top at display time.

So the sheet carries the map and nothing else, `InsetSheet --emit-icons` writes each cell's icon
positions into the definitions JSON, and the plugin draws them as widgets.

**A fixed pool of 40 icon widgets, as children of the inset layer.** Children of the *inset*, not of
the map layer, for two reasons that both matter: the layer clips, so an icon near a cell's edge is
trimmed for free; and they live in the inset's own index space, so adding forty of them moves no
slot in the main layer and the create-once/index-as-handle contract is untouched. Fixed size for the
usual reason — a dynamic widget cannot be removed from its parent, so a pool that grew with a
setting would be a slow leak.

### Three filters, in this order

1. **Eligibility** — is this kind switched on at all.
2. **Spacing** — walking down the priority order, an icon landing within the minimum gap of one
   already accepted is dropped. Because the walk is in priority order, the survivor of a collision
   is always the more useful of the two.
3. **Count** — take the first N of what is left.

Spacing before count, not after. The other way round fills the budget with a cluster of
high-priority icons stacked on one building and leaves nothing for the rest of the cell.

### Draw order is the reverse of survival order

Widgets paint in child order, so the last one written is nearest the front. Survival ranks the most
useful icon first; assigning them to the pool in that order would put the bank underneath whatever
overlapped it. So the accepted list is walked backwards into the pool. The priority list therefore
governs **both** which icons are seen and which is on top — which is the whole point of keeping them
out of the PNG.

### Ranked by sprite, and the order is compiled in

About 150 map area ids collapse onto **76 sprites** — areas 58, 964, 990, 1011 and 1020 are all
1504. The area id is an identifier; the sprite is what a person recognises, so a setting has to be
about sprites.

The cache can name none of them: `AreaDefinition.getName()` is null for all 76 and `getCategory()`
is the area id plus a constant. The names came from pixel-matching the cache's sprites against the
wiki's copies — see `cache-tools/src/IconNames.java`.

`MapIcon`'s **declared order is the priority list**, in six tiers: Navigation, Access, Activities,
Skilling, Shops, Niche. An enum's `ordinal()` is a compile-time constant, which makes it the
cheapest possible ranking key and also the reason the user **cannot reorder it** — only exclude.
RuneLite has no ordered-list config type and no slider; a drag-and-drop list needs a `PluginPanel`
and a `NavigationButton` of its own, which is a sidebar this plugin does not otherwise need and a
reviewer would have to look at by hand. That is a deliberate trade for staying inside automated
review.

Two placements are deliberate choices rather than defaults, 2026-09-08:

- **Water source (1487) sits at the bottom of Shops, not in Skilling.** At 71 of the 629 emitted
  positions it is the commonest icon on the sheet by half again, so where it ranks decides more of
  what a busy cell looks like than any other single choice. Bottom of Shops rather than Niche so it
  still survives in a sparse cell, where it costs nothing.
- **The centre Transportation icon is suppressed** and replaced by the plugin's own marker.

### An unknown sprite is drawn, not dropped

`MapIcon.forSprite` returning null means the game added an icon after this build. It is drawn
anyway and ranked last: drawn, so the gap is visible and gets fixed; last, so it cannot crowd out an
icon the player asked for. A unit test fails on it, with the sprite id in the message.

### Icon size is a setting because the client turned out to scale

Proved in game 2026-09-08 by drawing sprite 1453 in a 30 px box and getting a 30 px icon. Native is
15 x 15 and is the only size guaranteed crisp — these are pixel art — so 15 is the default, and
smaller is offered because it is by some distance the strongest clutter control available. The
default of 8 icons is arithmetic, not taste: a cell is 112 x 85 = 9,520 px and an icon at native
size covers 225, so eight is about a fifth of the close-up given to icons. Forty would be over
everything, which is the mat of overlapping circles that made baking them in unusable.

### Nothing is bundled

A widget's `spriteId` resolves against the same sprite table `cache-tools` reads out of cache index
8 — proved in game 2026-09-08 by drawing sprites 1453 and 1504 on a bare widget. So the icons are
live cache reads and survive a graphics update for free. The fallback, had that failed, was a
76-icon strip PNG under negative ids of our own, which would have frozen them at the shipped
version.

## Two programs write the definitions JSON, and the second one is easy to forget

`tools/generate-definitions.py` writes the rings and the grid; `InsetSheet --emit-icons` then adds
each ring's `insetIcons`. Running the generator alone — the natural thing after a game update —
silently drops every icon position, leaving a plugin that draws a perfectly good close-up with
nothing on it and no symptom to notice.

The fix is not documentation, it is a test: `everyRingWithACellCarriesItsIconPositions` turns a
silent data loss into a build failure. The regeneration recipe in `cache-tools/README.md` states the
ordering, and `--icons none --emit-icons` is deliberately one run so the sheet and the positions
cannot come from two different caches.

## Show map / Hide map is symmetric

Changed 2026-09-07. Either direction clears the selection, undoes every edit and restores the stock
list. The button is the one control meaning "I am done with what is on screen", so it read badly
when one press of it cleared the filter and the other left a list narrowed to one row for no
visible reason.

## Toggling a favourite closes and reopens both interfaces

Found 2026-09-07, from a debug trace, after two rounds of fixing the wrong thing.

Adding or removing a favourite does not redraw the travel log. The game **closes interface 398 and
381 and reopens them**:

```
log rebuilt: filterActive=true selected=DIS unlocked=true faveSlot=null
interface 398 closed; clearing selection DIS
interface 381 closed; clearing selection none
travel log opened
availability: 50 of 55 unlocked; favourites [DKP, CIQ, CKR, AIQ, AKP, CJQ, DLQ, DIS]
```

`onWidgetClosed` fires, `reset()` clears the selection, and the reopened log is stock. Re-applying
the filter harder on `ScriptPostFired` — which is what the two previous attempts did — could never
have worked, because the state it needed had already been thrown away.

So the selection is **parked on the way out and picked up again** if the log comes back within two
game ticks. Bounded on purpose: a genuine close (travelling, walking away) is not followed by a
reopen, and a selection that reappeared the next time a fairy ring was used would be a surprise
rather than a convenience. Losing it costs one click on a marker; keeping it too long costs a list
that is mysteriously narrowed. The map's shown/hidden state rides along with it, so the round trip
is invisible.

*Worth generalising:* three separate bugs this session were the plugin holding state the client had
already invalidated — a stale `Widget` reference, an id-keyed undo map, and now a selection across
an interface rebuild. **Anything remembered about an interface has to be re-derived or explicitly
carried; nothing survives by default.**

## Data

`src/main/resources/com/fairyringmap/FairyRingDefinitions.json` — generated by
`D:\AI\PROJECTS\OSRS\cache-tools\tools\generate-definitions.py`. Two sources:

- **DB table 89 `fairyring`** in the game cache — coordinates (`COL_DEST_COORD`, a coordgrid),
  and the region prefix the game itself prints in the log.
- **RuneLite core's `FairyRing` enum** (BSD-2) — the 55 working codes and their destination
  names, so labels read the same as the rest of RuneLite.

The wiki's `Map:Fairy_rings` GeoJSON is **not used**. The game's own table is the better source
and carries no licence question.

**55 working codes of 64 possible.** The 9 dead: `AIP BIR BJQ CJP CJS CLQ DJQ DJS DKQ`.

**42 of the 55 are on the surface map.** The other 13 are dungeons, "other realms", or the
player-owned house, which sit in the same coordinate space thousands of tiles north of anything
drawable. They get a labelled strip below the map rather than a fake position.

The strip sits **below** the map, not over its bottom edge. Overlaying it bought 17 px of map
height at the cost of covering the south coast, which is the part of a map you navigate by. It
fits because the map is pushed up by the strip's height instead of being centred:
312 + 4 + 15 = 331 against the dial interface's 334.

### The map sprite

`.../FairyRingMap/gielinor.png`, 432 × 250, generated from the local game cache by
`cache-tools` `MapDump` — RuneLite's own `MapImageDumper`, the same tool the wiki used for its
tiles, so the provenance is clean and BSD-2 compatible. Wiki imagery is CC BY-NC-SA 3.0 and
cannot be bundled.

World box: **x 1088–3904, y 2400–4032** (2816 × 1632 tiles → 0.1534 px/tile). Regenerate with:

```
run.cmd MapDump "%USERPROFILE%\.runelite\jagexcache\oldschool\LIVE" "%USERPROFILE%\.runelite\cache\xtea.json" 0 1088 2400 3904 4032 432 gielinor.png
```

`docs/projection-check.png` is the same box at 3× with every surface ring plotted — the visual
proof that the projection is right.

**Why the whole surface world rather than the rings' bounding box.** The first render was the
fairy ring bounding box plus sixty tiles, which is the smallest correct answer and the wrong one:
it cropped off Zeah's west coast, Fossil Island, the deep Wilderness and most of the recognisable
coastline, so the image read as a broken crop rather than as Gielinor. A map you navigate by
landmark has to contain the landmarks. The cache's own region bounds are x 1024–4031, y 2240–4223;
the box above is that trimmed to where surface content actually is.

The cost of a wider box at a fixed sprite width is scale, and scale is what decides whether two
markers can be clicked apart. It turned out to be nearly free: widening the world by 33 % and the
sprite from 380 to 432 px leaves **7 marker pairs under 12 px and 4 under 8 px — exactly the old
numbers**. Box and sprite were sized together so that both axes land on 0.1534 px/tile and the box
centre falls on the sprite centre exactly, which is what the projection tests pin.

### Why the map is not drawn in the log panel

The native log panel is **190 × 261**; `CONTENTS` is 163 × 215. Measured icon crowding for the
42 surface rings:

| Map width | px/tile | pairs < 12 px apart | pairs < 8 px |
|---|---|---|---|
| 190 | 0.078 | 28 | 10 |
| 285 | 0.118 | 10 | 6 |
| **380** | **0.157** | **7** | **4** |
| 500 | 0.206 | 4 | 1 |

At 190 a third of the rings overlap. Enlarging the panel was tried and abandoned — each interface
is clipped to its slot, so a stretched panel is simply cut off — and the map moved to a layer of
its own over the 512 × 334 dial interface instead. That is where the 432 px width comes from: it
is what fits over the dials with margin, not what fits in the log. Every dimension is JSON, so
retuning is a re-render.

`AJS`/`CIP` (Miscellania and the penguins next to it) are 18 tiles apart and will always
collide — they need a hand nudge, so the definition schema carries an optional pixel offset.

## Known conflict — document in the README

`fairy-ring-organizer` (YaBoiStery) and `fairy-ring-favourites` (h1pstr) both mutate the same log
widgets. This plugin hides the whole `CONTENTS` container in map mode and restores it in list
mode, so their changes are invisible while the map is up and intact underneath. The one real
clash is the search string: both of theirs and ours write the list. Decision: **when either is
detected we still work, because we only ever set the search varc and re-run the game's own
rebuild** — whatever they do to the rows happens inside that rebuild. Say so in the README, and
tell users to report layout oddities with the other plugin's name.

## Built

| File | What it holds |
|---|---|
| `FairyRingMap.java` | The component. Widget work, the filter, the panel resize. |
| `FairyRingMapPlugin.java` | Lifecycle, sprite registration, event bus. |
| `MapProjection.java` | World tile to map pixel. Pure, 15 tests. |
| `LogRow.java` | The game's search-filter rules, mirrored. Pure, 10 tests. |
| `FairyRingRows.java` | Code to log-row component, via gameval constants. Generated. |
| `DefinitionLoader.java` | JSON out of the jar. Its own class so the injector has no cycle. |
| `MapIcon.java` | The 76 map icons in priority order. Declared order *is* the ranking. |
| `IconPlacement.java` | One icon inside one inset cell: sprite, and where. |
| `InsetDefinition.java` | The inset sheet's grid geometry, read from the same JSON the generator wrote. |

36 tests green. `gradlew.bat build` clean. The jar carries no `META-INF/services` entry.

Three things worth remembering, because they are not obvious from the code:

- **Widget work triggered from a widget listener is deferred through `clientThread.invokeLater`**,
  because the client calls those listeners in the middle of running its own script.
- **The favourites block `FAVES` is a child of `CONTENTS`, not a sibling**, sitting at its top with
  a height the game sizes from how many favourites exist. Moving a filtered row to y = 0 therefore
  puts it *underneath* the favourites rather than above them; the row goes to the bottom edge of
  `FAVES` instead.
- **A visible log entry is three widgets, not one**: the static row, its static favourite star, and
  a dynamic label holding the three-letter code. Anything that hides or moves a row has to take all
  three, or the leftovers float in the gap.

## Not done

- [ ] **The player-owned house gets no close-up, and could get nine.** `DIQ` is the one
      destination with no inset cell, because DB table 89 gives it coordgrid 0 — where it goes is
      per-player. But the game *prints the house's location in the row text*, and there are nine
      possible locations, so nine extra cells would cover it. Two things are needed and neither is
      verified: the exact string the server writes into that row (nothing in the cache sets a log
      row's text, so it can only be read off a live client), and a wiki-checked list of the nine
      locations with coordinates, since the cache has none for this ring. **Appending is free** —
      cells 54–62 in an 8x8 grid, sheet 1056x800, ~3.38 MB resident — because the grid is row-major
      and `cols` does not change, so every existing cell index keeps its meaning.
- [ ] The "Show map" toggle now overlaps the map's top-right corner by about 9 px, because the map
      grew from 380 to 432 px inside a 512 px interface while the toggle stayed under the close
      button. Cosmetic; decide in game whether to move it.
- [ ] Quantise `gielinor.png` to a palette; 180 KB is larger than it needs to be.
- [ ] Hand nudges for `AJS`/`CIP` once it is clear how bad the overlap looks. The schema already
      carries `offsetX` / `offsetY`.
- [ ] A real icon. `icon.png` is drawn, not designed.
- [ ] Hub submission: public repo, then a PR to `runelite/plugin-hub` pinning the commit.
