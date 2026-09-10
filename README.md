# Fairy Ring Map

Replaces the fairy ring **travel log** with a clickable map of Gielinor, so you pick a destination
by where it is rather than by remembering that Mudskipper Point is `AIQ`.

Click a ring on the map and the travel log filters itself down to that one destination. Then click
it, the way you always would.

## What it does, precisely

1. Open a fairy ring. Where the travel log's list would be, you get a map of the surface world with
   a marker on every ring you can use. Rings you have not unlocked are greyed out.
2. Hover a marker to see the destination. Click it, and the list collapses to that one entry,
   moved to the top and highlighted.
3. Click that entry to travel. That click is the game's own, on the game's own row.
4. `Map` / `List` in the title bar switches back and forth.

Thirteen destinations — the dungeons, the "other realms" and your player-owned house — are not on
a surface map at all, so they sit in a labelled row underneath it.

## What it does not do

**It does not travel for you, and it cannot.** Every row in the travel log carries the op
"Use code" with no client-side script behind it: the click goes straight to the server. There is no
listener anywhere in the fairy ring interfaces for a plugin to run — even the dials' Confirm button
only plays a sound and animates, because its travel is a raw op too. So this plugin narrows the
list down for you and stops there. It never sets the dials, never builds a click, and never sends
anything to the server.

Everything it does is show, hide, move and colour widgets. It invokes no game scripts, writes no
game variables, and creates no menu actions.

## Playing with other fairy ring plugins

Two other Hub plugins already change these same widgets:

- **fairy ring organizer** (YaBoiStery) — sorts, renames and recolours the log rows
- **fairy ring favourites** (h1pstr) — relabels them

Both should be fine alongside this one, by design rather than by luck. This plugin never edits row
text or row order. In map mode it hides the whole list container, so whatever they have done to the
rows is out of sight and untouched underneath; picking a destination hides the other rows and moves
that one to the top, and everything hidden or moved is recorded and put back when you leave.

The one visible overlap is the log panel, which this plugin widens while the map is up and puts
back when it is not. If you see a layout oddity, please say which other fairy ring plugin you had
enabled — that is the interaction most likely to surprise.

RuneLite's own **Fairy Rings** plugin needs no special handling — this plugin does not touch the
search box or the game's filter at all.

## Configuration

| Setting | Default | What it does |
|---|---|---|
| Open on the map | on | Show the map as soon as the travel log opens |
| Show off-map destinations | on | The row of dungeons and other realms under the map |
| Hide locked destinations | off | Leave rings you cannot use off the map entirely |

## Where the map and the data come from

Both are generated from your own game cache, not copied from anywhere.

- **The map image** is rendered by RuneLite's `MapImageDumper` (`runelite/cache`, BSD-2) — the same
  tool the OSRS Wiki uses to make its map tiles, run against the local cache. Wiki imagery is
  CC BY-NC-SA and could not be bundled here.
- **The coordinates** come from the game's own `fairyring` database table, which carries a
  destination tile for every code.
- **The codes and destination names** come from RuneLite core's `FairyRing` enum (BSD-2), so the
  labels read the same as the rest of RuneLite.

55 of the 64 possible codes have a ring; 42 of those are on the surface map. The generator and the
map renderer live in the sibling `cache-tools` project, and `DESIGN.md` records how to re-run them.

`docs/projection-check.png` is the same world box rendered four times larger with every surface
ring plotted on it. It is not a screenshot — it is how the world-to-pixel projection was checked,
and it is worth regenerating if the map box ever moves.

## Building

```
gradlew.bat build
```

27 unit tests cover the parts that can be tested without a client: the world-to-pixel projection,
the code and availability parsing, and the shipped definitions file. Everything else needs the game.

## Licence

BSD 2-Clause. See `LICENSE`.
