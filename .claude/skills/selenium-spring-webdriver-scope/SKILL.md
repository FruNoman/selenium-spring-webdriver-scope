---
name: selenium-spring-webdriver-scope
description: How this repo's Spring bean scope, page objects, profile-based browser/env switching, and parallel-vs-sequential test execution fit together, plus the non-obvious findings from building it (built-in WebDriverScope isn't thread-scoped, the hub's pretty-printed /status JSON breaks naive grep, class-level vs method-level @Profile). Use whenever adding a new page object, a new browser/env combination, wiring up parallel tests, or touching WebDriverConfig/RemoteWebDriverConfig.
---

# selenium-spring-webdriver-scope

A minimal Spring Boot + Selenium + TestNG repo demonstrating one specific
problem (a cached `@SpringBootTest` context handing back a dead
`WebDriver`) and two different fixes for two different situations
(sequential vs parallel test execution). See `README.md` for the full
narrative; this file is the "how do I extend this" reference.

## Two WebDriver scopes, two different jobs — never assume one covers the other

- **Nothing declared** (no `@Scope` at all) → Spring Boot's own
  `spring-boot-test-autoconfigure` ships a `WebDriverContextCustomizerFactory`
  (`org.springframework.boot.test.autoconfigure.web.servlet.WebDriverScope`)
  that auto-detects any `WebDriver`-typed bean in a `@SpringBootTest` and
  transparently recycles a dead session. **Fixes the sequential case**: a
  cached context handing the same dead browser to the next test class.
  Confirmed by decompiling it: `postProcessBeanFactory` only sets scope to
  `"webDriver"` `if (!StringUtils.hasLength(bd.getScope()))` — i.e. it
  backs off the moment any explicit scope is already declared, it never
  fights an explicit one.
- **`@Scope("webdriverscope")`** (this repo's own
  `scope/WebdriverScope.java`, extends `SimpleThreadScope`) → genuinely
  thread-scoped (real `ThreadLocal` underneath), same liveness check
  layered on top. **Fixes the parallel case**: TestNG
  `parallel="methods"`/`"classes"` with multiple threads.

**Never assume the built-in one is "good enough" once parallel execution
enters the picture.** Verified empirically both ways: removing the custom
scope and running 10 TestNG threads (`testParallel` task) against Grid
gives all 10 threads **the same** `RemoteWebDriver` instance (one shared
session id across all of them), and every `quit()` after the first throws
`UnreachableBrowserException`/`RejectedExecutionException`. The built-in
scope's own bytecode explains why: one `synchronized Map<String,Object>`
keyed by bean name, no thread awareness at all. With the custom scope
back, 10 threads → 10 distinct session ids, all pass.

**Rule of thumb for any new `WebDriver`-producing `@Bean`**: if it might
ever run under `parallel=`, give it `@Scope("webdriverscope")`. "Local"
does not imply "sequential" — `WebDriverConfig` (local) carries the same
annotation as `RemoteWebDriverConfig` (grid) for exactly this reason, even
though the plain sequential demo (`./gradlew test`) doesn't strictly need
it.

## Adding a new page object

Every page extends `pages/BasePage.java`:

```java
@Component
@Scope("prototype")
public class SomeNewPage extends BasePage {

    @FindBy(id = "whatever")
    public WebElement someField;

    @Override
    public SomeNewPage open() {
        driver.get(baseUrl + "some-new-page.html");
        return this;
    }

    // methods that use someField
}
```

- `@Component @Scope("prototype")` **on the subclass, not on `BasePage`**
  (`BasePage` is abstract, never a bean itself). `prototype` is not
  optional: a singleton page bean gets its `driver` field wired once, at
  first creation, and keeps pointing at that driver forever — even after
  `WebdriverScope`/the built-in scope hands out a fresh one later. Every
  page needs a fresh instance per request, same underlying reason
  `WebDriver` itself needs recycling.
- `BasePage` supplies `@Autowired protected WebDriver driver` +
  a `@PostConstruct` that runs `PageFactory.initElements(driver, this)` so
  `@FindBy` fields on the subclass resolve. No `LoadableComponent`, no
  `load()`/`isLoaded()` lifecycle — deliberately left out, see README's
  comparison with the richer `sem`/`backend-automation-framework` version
  for the tradeoff (their version has page-navigation-assertion baked in,
  at the cost of being a heavier base and needing its own `@Lazy` on the
  driver field, which this repo's version doesn't need).
- **Test-side injection must be `@Lazy @Autowired`, never plain
  `@Autowired`** — same reasoning as the page's own prototype scope: a
  non-lazy field on a test class (itself effectively used like a
  singleton across `@Test` methods within a run) would freeze to the
  first-ever-resolved page instance. `@Lazy` makes it a proxy that calls
  `getBean()` fresh on every method invocation.
- **Page objects must be stateless** — a direct consequence of the
  bullet above: every call through the `@Lazy` proxy (e.g.
  `webFormPage.typeIntoTextField(...)` then `webFormPage.getTextFieldValue()`)
  lands on a *different* `prototype` instance. That's harmless as long as
  a page only holds `@FindBy` fields (lazy locators, re-resolved against
  the live DOM on each access) and the inherited `driver` (the same
  thread's instance either way). Any other field — a cached value, a
  "last entered text", a counter — silently resets between calls. If a
  test genuinely needs one instance for its whole body, take a real one
  up front with `applicationContext.getBean(SomePage.class)` (or keep the
  `this` returned by a fluent method) instead of adding state to the page.
- **Every page implements `open()`** — `BasePage` declares
  `public abstract BasePage open()` and injects `@Value("${base.url}")
  protected String baseUrl` (the site root, trailing slash included).
  Each subclass overrides `open()` with a covariant return type (itself)
  and does `driver.get(baseUrl + "<its path>")`. Pages never hardcode a
  full URL.
- **The entry point is opened by the test base, not by tests** —
  `BaseWebTest`'s `@BeforeMethod openEntryPage()` calls
  `getBean(WebFormPage.class).open()` before every test. TestNG runs
  `@BeforeMethod` on the same thread as its `@Test`, so this stays
  correct under `parallel="methods"`. Tests start already on the entry
  page; `open()` on other pages is there for jumping straight to them.
- **Transitions: inject the next page with plain `@Autowired`** — e.g.
  `WebFormPage` has `@Autowired private SubmittedFormPage submittedFormPage`
  and `submit()` clicks, then returns it. No `@Lazy` needed here (unlike on
  test classes): the target is prototype too, so every `WebFormPage`
  instance gets its own `SubmittedFormPage` wired to the same thread's
  driver, and its `@FindBy` fields are lazy, so creating it before
  navigation is fine. Watch for cycles: two pages autowiring each other
  as prototypes fail at startup — make one side `@Lazy` if a "back"
  transition is ever needed.

## Profiles: env axis and browser axis are separate, never combined into one expression

`@Profile("chrome & local")`-style expressions were tried and explicitly
rejected — not idiomatic to how `sem`/`backend-automation-framework` do
it, and less readable at a glance. The actual pattern:

- **Class-level `@Profile`** = the environment axis (`"local"` on
  `WebDriverConfig`, `"grid"` on `RemoteWebDriverConfig`). Gates whether
  the whole config class's beans are even candidates.
- **Method-level `@Profile`** = the browser axis (`"chrome"` /
  `"firefox"` on each `@Bean`).
- Spring only registers a bean when **both** match — `chrome,local`
  resolves `WebDriverConfig.chromeDriver()`, `firefox,grid` resolves
  `RemoteWebDriverConfig.firefoxDriver()`. Adding a third env (say
  `"docker"`) or third browser (`"edge"`) means: a new config class (env)
  or a new `@Bean` method with the same env-class's `@Profile` (browser) —
  never a compound expression on either side.
- Both config classes are free to reuse the same bean names
  (`chromeDriver`/`firefoxDriver`) because only one config class is ever
  active at a time (only one env profile), so there's no registration
  collision.

## Properties: one file per env profile, `grid.url` overridable from the command line

`src/test/resources/application.properties` sets the *default*
`spring.profiles.active` (currently `chrome,local`) and `base.url`, the
site root every page's `open()` appends its own path to (see "Every page
implements `open()`" above). `base.url` isn't in `build.gradle`'s forwarded
`-D` prefix list yet — add `base.` there if it needs overriding per run.
`application-local.properties` / `application-grid.properties` are the
per-env overrides — right now `application-grid.properties` only carries
`grid.url`, but this is where any future env-specific property belongs
(e.g. a different implicit-wait for a slower CI Grid).

`build.gradle`'s `test`/`testParallel` tasks forward any `-Dspring.*` or
`-Dgrid.*` system property straight into the test JVM
(`systemProperties.putAll(System.properties.findAll { ... })`) — this is
what lets `./gradlew test -Dspring.profiles.active=firefox,grid
-Dgrid.url=http://my-hub:4444/wd/hub` override both without touching any
properties file. If a new command-line-overridable setting is added,
extend that `findAll` prefix list rather than special-casing one more
property name.

## Parallel execution: a completely separate suite, task, and Grid capacity — not a flag on the existing one

Two independent paths, deliberately not merged:

- `src/test/resources/testng.xml` (`parallel="methods" thread-count="3"`) +
  the default `test` Gradle task — the sequential-style demo classes
  (`FirstFormTests`/`SecondFormTests`), now also run with light
  parallelism to prove the built-in scope's sequential-recycling promise
  still holds even under a modest thread count.
- `src/test/resources/testng-parallel.xml` (`parallel="methods"
  thread-count="10"`) + the dedicated `testParallel` Gradle task +
  `ParallelGridTests` (10 flat `@Test` methods) — the real proof of
  concurrent, distinct sessions. Kept in its own suite/task specifically
  so it never accidentally shares configuration with the main demo.

Running `testParallel` against Grid needs actual node capacity:
`docker-compose.grid.yml`'s chrome node sets
`SE_NODE_MAX_SESSIONS=10`/`SE_NODE_OVERRIDE_MAX_SESSIONS=true` — without
raising this, TestNG threads would queue waiting for a free Grid slot
instead of actually running concurrently, silently defeating the point of
the test (it would still pass, just sequentially, hiding whether the
scope itself is correct).

## Non-obvious findings worth re-checking if any of this looks broken again

1. **`selenium/hub`'s `/status` returns pretty-printed JSON**
   (`"ready": true`, space after the colon) — unlike the
   `selenium/standalone-chrome` all-in-one image, which happened to
   return compact JSON (`"ready":true`) in earlier ad-hoc local testing.
   A `grep -q '"ready":true'` readiness check silently never matches
   against the real hub image and times out after the full budget,
   *while the Grid is actually ready the whole time* — always parse
   Grid's `/status` with `jq`, never string-match the JSON directly.
2. **`docker compose ... suites '...testng.xml'` in `build.gradle`
   overrides Gradle's own `--tests` class filter** — with an explicit
   TestNG suite XML configured, `./gradlew test --tests SomeClass` runs
   the *whole* suite regardless (or fails oddly trying to filter a class
   that isn't in it). To run one class in isolation for debugging,
   temporarily comment out the `suites` line rather than trusting
   `--tests`.
3. **A `@Lazy @Autowired` field cannot be cast to the concrete driver
   class** — `((RemoteWebDriver) driver).getSessionId()` on a `@Lazy
   WebDriver driver` field throws `ClassCastException`, because the lazy
   injection proxy only implements the declared type (`WebDriver`), never
   the runtime concrete class. Use `driver.toString()` instead when you
   need to show/log a session id — `ChromeDriver`/`RemoteWebDriver`'s own
   `toString()` already includes it.
4. **GitHub Actions' `ubuntu-latest` runners already have Docker, `docker
   compose`, and `jq` preinstalled** — no setup steps needed for any of
   the Grid workflow beyond `actions/checkout` and `actions/setup-java`.
