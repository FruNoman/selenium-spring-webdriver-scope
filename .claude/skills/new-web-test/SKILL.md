---
name: new-web-test
description: Write a new Selenium/TestNG UI test in this repo — from a plain-language scenario or a test case from a TMS (Qase/TestRail). Covers reusing existing pages/elements, walking the flow live through the `selenium` MCP server, writing page objects and custom elements by this repo's rules, registering the class in the TestNG suite, and verifying by actually running it. Use when asked to add, create, automate or port a test.
---

# Adding a UI test

Framework rules (scoped driver proxy, singleton stateless pages, `@FindBy` +
`BaseElement`, `open()`/`entryPage()`) live in the `selenium-spring-webdriver-scope`
skill — read it first; this skill is the *workflow*, it doesn't repeat them.

## 0. Reuse before you write

1. Read the `site-map` skill (`references/navigation.md`): which site/page, its
   URL property, known locators and gotchas. Don't re-derive what's there.
2. Search what exists — pages, elements, similar tests:
   ```bash
   ls src/main/java/com/frunoyman/webdriverscope/pages src/main/java/com/frunoyman/webdriverscope/elements
   grep -rn "@FindBy" src/main/java | grep -i "<keyword>"
   grep -rln "<feature>" src/test/java
   ```
   Extend an existing page/element instead of creating a near-duplicate. Add a
   method to an existing test class when the feature already has one.

## 1. Get the scenario straight

- From a TMS case: take the steps + expected results **verbatim** (Qase via its
  MCP / TestRail via REST — neither is configured in this repo yet; if not
  available, ask the user to paste the case). Each expected result becomes an
  assertion; don't invent checks the case doesn't ask for.
- From a free-text request: restate it as numbered steps + expected results and
  confirm only if something is genuinely ambiguous.

## 2. Walk the flow live — `selenium` MCP (`@angiejones/mcp-selenium`)

Observe the real UI before writing a line. Verified behaviour (2026-09-23):

- `start_browser {browser:"chrome", options:{headless:true}}` → `navigate` →
  interact → `close_session` at the end (always close).
- Locators are Selenium strategies (`css`/`xpath`/`id`/`name`/`tag`/`class`) —
  what works here goes into `@FindBy` 1:1, no translation.
- `get_element_text` returns the **first match only**. For lists/counts use
  `execute_script` (`return [...document.querySelectorAll('…')].map(e=>e.textContent)`).
- **Check locator uniqueness** before using it:
  `execute_script "return document.querySelectorAll('<css>').length"`. A
  "looks right" selector often matches more (`th.header` hits both tables on
  the-internet/tables).
- **Never trust "Element clicked" — verify the effect** (read the DOM after
  the action). A click that returned OK but changed nothing is how the
  tablesorter race was found (see `site-map`).
- The `accessibility://current` resource gives a page overview but is big
  (~33 KB for a small page) — use it once for orientation, not per step.
- `diagnostics {type:"console"|"errors"|"network"}` for JS errors behind a
  "nothing happened".

**Write every verified fact into `site-map` immediately** (URL, locator,
gotcha) — a browser session's findings are gone when it closes.

## 3. Page objects and elements

Follow `selenium-spring-webdriver-scope` → "Adding a new page object" /
"Custom elements". Checklist:
- page: `@Component` (singleton), `extends BasePage`, private `@FindBy` fields,
  `open()` built from a URL property (new site → new property in
  `application.properties`), no state in fields;
- repeated widget (table, dropdown, dialog) → `BaseElement` subclass with a
  public `(WebElement root)` constructor, nested locators relative (`.//`);
- transitions: next page via `@Autowired`, returned from the action method.

## 4. Test class

```java
public class SomeFeatureTests extends BaseWebTest {

    @Autowired
    private SomePage somePage;

    @Override
    protected Class<? extends BasePage> entryPage() {   // only if not WebFormPage
        return SomePage.class;
    }

    @Test
    public void doesTheThing() {
        assertEquals(somePage.doSomething().getResult(), "expected",
                "what the case expects, in words");
    }
}
```

- Plain `@Autowired` (no `@Lazy`); the test starts already on `entryPage()`.
- Every assertion gets a message saying what was expected.
- Tests run `parallel="methods"` — each method gets its own browser; methods
  must not depend on each other's order or state.
- **Register the class in `src/test/resources/testng.xml`** — a class that
  isn't listed there never runs under `./gradlew test`, while
  `--tests` still runs it, so the omission is easy to miss.

## 5. Build incrementally; stop after ~4 failed attempts

Add one step, run it (`run-test` skill, single method), confirm, then the next
— the file on disk should always reflect how far the flow is proven. If the
same step/locator/failure is still unresolved after about 4 of your own
attempts (runs, rewrites or reasoning passes), stop and report what you tried
and observed, then ask.

## 6. Done means run

1. Single test: `./gradlew test --tests '<fqcn>.<method>'`
2. Full suite: `./gradlew test` (must include the new class — step 4).
Report the actual result. Then close the loop: `site-map` updated? anything in
this skill wrong or missing → `self-analysis`.
