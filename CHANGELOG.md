# Changelog

All notable changes to this project are documented here.

## [1.0.0] - 2026-11-22

First public release.

### Features

- Client-side index of MineColonies rack contents, built from the NBT of already loaded chunks and
  refreshed whenever a rack GUI is opened. No server installation, no custom packets.
- Search window (`R`): every known item with its total amount and the racks that hold it (distance,
  `X / Y / Z`, warehouse and double-rack flags).
- Through-wall outlines: cyan racks, nearest rack golden, world labels and a corner HUD list of the
  closest results.
- Green frames around slots that hold the searched item inside the rack window (and any other
  container GUI).
- The container list scrolls with the mouse wheel and shows "1-7 of 23" when not everything fits.
- Overview outlines stay live: opening a container with the searched item adds it to the highlight
  without searching again, and boxes close to the camera are clipped instead of disappearing.
- Vanilla storage: chests, trapped chests, barrels, shulker boxes, hoppers, droppers, dispensers,
  furnaces, smokers, blast furnaces and brewing stands are indexed when opened. Results show the block
  name next to the coordinates.
- Click a rack in the result list to isolate it, click again to highlight all matches.
- "Clear highlight" removes the outline, the slot frames and closes the window.
- Keyboard navigation: `Up`/`Down` move the selection while typing, `Enter` confirms and closes the
  window.
- The last query is remembered per world and restored when the window is reopened; the `x` button
  clears the input field.
- The world highlight and the slot frames are applied only on confirmation (`Enter` or a click on an
  item); opening the window does not change the current highlight.
- Persistent per-world index in `config/rackfinder/racks-<world>.json`.
- Client commands: `/rackfinder open`, `target`, `dump`, `rescan`, `save`, `clear`.
- Client config: scan radius/interval, highlight limits, HUD list, slot frames, autosave.
- Headless self-test (`./gradlew runServer`) covering rack NBT parsing, stack merging, NBT-aware item
  grouping, search and JSON persistence.
