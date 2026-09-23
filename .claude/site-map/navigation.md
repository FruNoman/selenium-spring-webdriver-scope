# Navigation map

Legend: ✅ verified live (date) · plain = read from code · ⚠ unconfirmed.
One section per site. Base URLs come from the active test-env file
`src/test/resources/env/<test.env>.yml` (default: `env/default.yml`).

## selenium.dev test pages — `base.url` = `https://www.selenium.dev/selenium/web/`

| Page | Path | Page object | Facts |
|---|---|---|---|
| Web form | `web-form.html` | `WebFormPage` (default `entryPage()`) | Text input `name=my-text` (unique). Submit: `button[type='submit']`. Form is `method=get action=submitted-form.html`. ✅ 2026-09-23 (tests + MCP) |
| Submitted form | `submitted-form.html` | `SubmittedFormPage` | Reached via `WebFormPage.submit()`. `#message` text = `Received!`; `<title>` of web form = `Web form`. ✅ 2026-09-23 |
| Tables | `tables.html` | — | **Dead end for `Table`**: plain `<table>`s without `thead`/`tbody`. Use the-internet below instead. ✅ 2026-09-23 |

## the-internet — `the-internet.url` = `https://the-internet.herokuapp.com/`

| Page | Path | Page object | Facts |
|---|---|---|---|
| Sortable tables | `tables` | `TablesPage` (`getTable()` → `Table`, `getRawTable()` → `WebElement`) | Two tables: `#table1` and `#table2`, both `class="tablesorter"`, same columns. Use `#table1`. Headers: Last Name, First Name, Email, Due, Web Site, Action (6 `th` in `#table1`). 4 rows; initial Last Name order Smith, Bach, Doe, Conway; after one header click → Bach, Conway, Doe, Smith. ✅ 2026-09-23 |

### Gotchas — the-internet/tables

- **`th.header` is not unique and is added by JS.** The raw HTML has no
  `class="header"`; jQuery tablesorter adds it at init, on both tables. Scope
  every locator with `#table1`. ✅ 2026-09-23 (curl of raw HTML vs MCP DOM)
- **Sort-click race:** a header click right after page load occasionally does
  nothing (1 of 4 first-clicks via MCP, 2026-09-23); a second click sorts.
  `TableElementTests.rowsAreLookedUpAgainAfterTheDomChanges` passed every run
  so far but is exposed to the same race. ⚠ Hypothesis: the click lands before
  tablesorter binds its handlers — waiting for `#table1 th.header` (the class
  tablesorter adds) before clicking should fix it; not yet implemented or proven.

## Open questions

- ⚠ the-internet sort race — confirm the `th.header` readiness signal and move
  the wait into `Table.sortBy` (see above).
