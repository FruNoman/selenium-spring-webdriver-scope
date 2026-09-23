---
name: site-map
description: The living map of the web apps this repo tests — for each site/page: URL property, how to reach it, page object, verified locators, and UI gotchas (races, duplicate matches, dead ends). Read it before exploring a page or writing a test; write back every navigation/locator fact you establish, dated and marked ✅ verified or ⚠ unconfirmed.
---

# The site map — read it, then feed it

`references/navigation.md` holds everything known about the sites under test.
Two obligations, in order:

## 1. Read before exploring

Before opening the browser (the `selenium` MCP) or grepping for "how do I get
to X", read `references/navigation.md`. If the answer is there, use it — a
live walk is the most expensive source and can record a worse answer than the
one already written.

## 2. Write back after investigating — every time, immediately

Any fact you establish goes into the map **as soon as it's verified**, not at
the end (a crashed session or an early stop loses unwritten findings):
- a page's URL / property / page object, or that none exists yet;
- a locator that works — and its uniqueness count;
- a UI gotcha that cost time (race, hidden duplicate, iframe, JS-added class);
- a dead end ("page X has no thead — unusable for Table");
- a ⚠ confirmed or disproved.

### How to write an entry

- **Source + trust:** `✅ verified live <YYYY-MM-DD>` for what you saw in the
  browser or a passing test; plain text for what's read from code; **⚠** for
  an inference still needing a check. Never promote a guess to a fact.
- **Replace, don't append** — when a ⚠ resolves or the UI moved, rewrite the
  line; no contradictory pairs.
- **Keep the shape** — one section per site, same table layout.
- **Subagents never edit `references/`** — they report findings as text; only
  the session talking to the user writes the file (parallel writers have
  silently overwritten each other's edits before).

### Where facts come from, cheapest first

1. `references/navigation.md`.
2. Code: page objects (`pages/`), elements (`elements/`), URL properties in
   `src/test/resources/application.properties`.
3. The TMS case text (Qase/TestRail), when available.
4. Live via the `selenium` MCP — authoritative, most expensive; write the
   result down immediately.
