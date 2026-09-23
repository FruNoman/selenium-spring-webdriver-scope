---
name: selenium-spring-webdriver-scope
description: How this repo's Spring bean scope, page objects, profile-based browser/env switching, and parallel-vs-sequential test execution fit together, plus the non-obvious findings from building it (built-in WebDriverScope isn't thread-scoped, the hub's pretty-printed /status JSON breaks naive grep, class-level vs method-level @Profile), and custom @FindBy elements (Table-style components built by ElementFieldDecorator). Use whenever adding a new page object or custom element, a new browser/env combination, wiring up parallel tests, or touching WebDriverConfig/RemoteWebDriverConfig.
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

**Rule of thumb for any new `WebDriver`-producing `@Bean`**: declare it
exactly like the existing four —
`@Scope(value = "webdriverscope", proxyMode = ScopedProxyMode.TARGET_CLASS)`
with return type `RemoteWebDriver` (see next section for why both). "Local"
does not imply "sequential" — `WebDriverConfig` (local) carries the same
annotation as `RemoteWebDriverConfig` (grid) for exactly this reason, even
though the plain sequential demo (`./gradlew test`) doesn't strictly need
it.

## The `WebDriver` bean is a scoped proxy — everything else is a plain singleton

Every `WebDriver` `@Bean` (`WebDriverConfig`, `RemoteWebDriverConfig`) is
`@Scope(value = "webdriverscope", proxyMode = ScopedProxyMode.TARGET_CLASS)`.
Whoever autowires `WebDriver` — pages, elements, `BaseWebTest` — receives
**one CGLIB proxy, not a browser**. Every call on it goes through
`WebdriverScope.get()`, which returns the current thread's driver and
creates a new one if that session was already `quit()`. So the question
"which browser?" is answered **per call**, not when the holder was created.

Consequences, all verified (local `test`, Grid `test`, Grid `testParallel`
with 10 threads → 10 distinct session ids through one shared page):
- **Pages are plain singleton `@Component`s** — no `@Scope("prototype")`.
- **Tests inject pages and the driver with plain `@Autowired`** — no `@Lazy`.
  `BaseWebTest` holds `protected WebDriver driver` (the proxy);
  `quitDriver()` is just `driver.quit()`.
- **Return type `RemoteWebDriver`, not `WebDriver`, on purpose**: the
  proxy is a subclass of the declared type, so casts to
  `JavascriptExecutor`/`TakesScreenshot`/`HasCapabilities` work
  (`SecondFormTests` checks this). With `ScopedProxyMode.INTERFACES` and a
  `WebDriver` return type those casts would throw `ClassCastException`.
  Still not possible: `instanceof ChromeDriver` on the proxy.
- `SecondFormTests.pagesAreSingletonsAndTheDriverProxyIsARealRemoteWebDriver`
  guards both properties — if it fails, someone changed the bean scope.

History, so nobody "fixes" it back: before this, the driver field held
the real browser object, which forced every page to be `prototype`,
every test field to be `@Lazy`, re-created the page on every call and
made public page fields read as `null` through the `@Lazy` CGLIB proxy.
The idea came from the Sphise `automation-tests` framework, whose pages
are singletons because Selenide's `$()` resolves the thread's driver per
call (their `@Scope(proxyMode = TARGET_CLASS)` on the abstract
`PageBaseCore` is actually inert — `@Scope` is not `@Inherited`).

## Adding a new page object

Every page extends `pages/BasePage.java`:

```java
@Component
public class SomeNewPage extends BasePage {

    @FindBy(id = "whatever")
    private WebElement someField;

    @FindBy(css = "table")
    private Table someTable;          // custom element, same @FindBy

    @Override
    public SomeNewPage open() {
        driver.get(baseUrl + "some-new-page.html");
        return this;
    }

    // methods that use someField, someTable
}
```

- `@Component` **on the subclass, not on `BasePage`** (`BasePage` is
  abstract, never a bean itself). Singleton is correct — see the section
  above; don't add `@Scope("prototype")`.
- `BasePage` supplies `@Autowired protected WebDriver driver` (the proxy) +
  a `@PostConstruct` that runs `ElementFieldDecorator.initElements(driver,
  this, beanFactory)` so `@FindBy` fields on the subclass resolve — both
  plain `WebElement`s and custom elements (see "Custom elements" below).
  Locators are created once and search through the proxy, so they always
  hit the current thread's browser. No `LoadableComponent`, no
  `load()`/`isLoaded()` lifecycle — deliberately left out, see README's
  comparison with the richer `sem`/`backend-automation-framework` version.
- **Page objects must be stateless — stricter now than before.** One page
  instance is shared by every test *and every parallel thread*. `@FindBy`
  fields (lazy locators) and `driver` (the proxy) are safe; any other
  field — a cached value, a "last entered text", a counter — is shared
  mutable state that parallel tests will overwrite under each other. Keep
  per-test state in the test method, pass it into page methods.
- **Fields private, access through methods** — plain encapsulation now
  (the `@Lazy`-proxy `null`-field trap is gone), but still the convention:
  expose behaviour (`getColumn(...)`, `submit()`), getters only when needed.
- **Every page implements `open()`** — `BasePage` declares
  `public abstract BasePage open()` and injects `@Value("${base.url}")
  protected String baseUrl` (the site root, trailing slash included).
  Each subclass overrides `open()` with a covariant return type (itself)
  and does `driver.get(baseUrl + "<its path>")`. Pages never hardcode a
  full URL (a page of another site gets its own property, e.g.
  `TablesPage` + `the-internet.url`).
- **The entry point is opened by the test base, not by tests** —
  `BaseWebTest`'s `@BeforeMethod openEntryPage()` calls
  `getBean(entryPage()).open()` before every test. `entryPage()` returns
  `WebFormPage.class` by default; a test class for another page/site
  overrides it (`TableElementTests` returns `TablesPage.class`). TestNG runs
  `@BeforeMethod` on the same thread as its `@Test`, so this stays
  correct under `parallel="methods"`. Tests start already on the entry
  page; `open()` on other pages is there for jumping straight to them.
- **Transitions: inject the next page with plain `@Autowired`** — e.g.
  `WebFormPage` has `@Autowired private SubmittedFormPage submittedFormPage`
  and `submit()` clicks, then returns it. Both are singletons, so a "back"
  transition (two pages autowiring each other) is fine too — Spring
  resolves field-injection cycles between singletons.

## Custom elements: same `@FindBy`, `WebElement` and components side by side

Code lives in `src/main/java/.../elements/`. Goal: **the standard Selenium
annotations are never replaced or wrapped** — `@FindBy`/`@FindBys`/`@FindAll`
populate a custom element exactly like a `WebElement`:

```java
@FindBy(id = "table1") private Table table;        // custom element
@FindBy(id = "table1") private WebElement rawTable; // plain, unchanged
@FindBy(css = "tbody tr") private List<Row> rows;   // list of custom elements
@FindBy(css = "thead th") private List<WebElement> headers; // plain list
```

### How it works

- **`BaseElement`** — abstract base, `implements WebElement, WrapsElement,
  WrapsDriver, Locatable`. Holds `protected final WebElement root`
  (a Selenium locator proxy, re-finds the node on every call) received
  through the constructor, and **only delegates** every `WebElement`
  method to it. Because it *is* a `WebElement`, Selenium's own API takes it
  as-is: `ExpectedConditions.visibilityOf(table)`, `Actions`, JS args
  (`WrapsElement` lets them unwrap to the real node).
- **`ElementFieldDecorator extends DefaultFieldDecorator`** — the single
  piece of machinery, no factories. Per field:
  1. no `@FindBy`/`@FindBys`/`@FindAll` → returns `null`, field untouched.
     (Plain PageFactory would decorate an un-annotated `WebElement` field
     with an id-or-name locator from its name — including `root`.)
  2. type is a `BaseElement` subclass → `proxyForLocator` + `create()`.
  3. `List<X extends BaseElement>` → a `java.lang.reflect.Proxy` `List`
     that re-runs `locator.findElements()` on **every** call and wraps
     elements lazily via an `AbstractList` view (`size()` creates nothing,
     `get(i)` creates one). Same "never stale" contract as Selenium's
     `List<WebElement>`.
  4. anything else (`WebElement`, `List<WebElement>`) → `super.decorate()`.
- **`create()`**: `type.getConstructor(WebElement.class).newInstance(root)`
  → `beanFactory.autowireBean(element)` → recursive
  `initElements(element, element, beanFactory)`, i.e. nested `@FindBy`s
  are searched **inside** the element (`tbody tr` finds only this table's
  rows). Careful: an XPath starting with `//` still searches the whole
  document — use `.//` for "inside this element".
- **Elements are not Spring beans** — no `@Component`/`@Scope` on them.
  `autowireBean` still injects `@Autowired` fields (`BaseElement` gets
  `WebDriver driver` — the same scoped proxy pages get); add more
  `@Autowired` fields to a subclass if needed. Elements on a page are
  created once with the page, so they are shared across threads too:
  same stateless rule.

### Adding a new element — `Table` as the reference

```java
public class Table extends BaseElement {

    @FindBy(css = "thead th")
    private List<WebElement> headers;   // plain WebElements inside

    @FindBy(css = "tbody tr")
    private List<Row> rows;             // nested custom elements

    public Table(WebElement root) {     // required: public (WebElement) ctor
        super(root);
    }

    public List<String> getHeaders() {
        return headers.stream().map(WebElement::getText).toList();
    }

    public List<String> getColumn(String header) {
        int index = columnIndex(header);
        return rows.stream().map(row -> row.getCellText(index)).toList();
    }

    public Table sortBy(String header) {
        headers.get(columnIndex(header)).click();
        return this;
    }
    // ...
}
```

Rules:
- extend `BaseElement`, keep a **public `(WebElement root)` constructor**
  (missing one → `IllegalStateException` naming the class at page init);
- nested locators are relative to the element (`css` as-is, XPath with `.//`);
- **stateless**, like pages: the element and every `List<...>` re-locate
  on each call, so never cache found elements or texts in fields;
- behaviour specific to one widget lives in that widget's class — never
  override `click()`/`isSelected()` etc. in `BaseElement` (the old
  `backend-automation-framework` `WebComponent` did, which broke plain
  checkboxes and changed every click to `Actions`);
- no generic elements (`Foo<T>` fields resolved through type variables)
  until there's a real need — the old framework carried that machinery
  unused.

Verified by `TableElementTests` against
`https://the-internet.herokuapp.com/tables` (`the-internet.url` property,
`TablesPage`): same node through `Table` and `WebElement`, headers/rows/
column reading, rows re-located after a click-to-sort changes the DOM,
and `WebDriver` injected into a nested `Row`.

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
3. **A proxied driver only has the type the proxy was built from** —
   a `@Lazy` or `ScopedProxyMode.INTERFACES` proxy over a `WebDriver`
   return type implements `WebDriver` only, so
   `((RemoteWebDriver) driver).getSessionId()` or `(JavascriptExecutor) driver`
   throws `ClassCastException`. That's why the beans return
   `RemoteWebDriver` with `TARGET_CLASS` (casts to `RemoteWebDriver` and
   its interfaces work); `instanceof ChromeDriver` still never will. Use `driver.toString()` instead when you
   need to show/log a session id — `ChromeDriver`/`RemoteWebDriver`'s own
   `toString()` already includes it.
4. **GitHub Actions' `ubuntu-latest` runners already have Docker, `docker
   compose`, and `jq` preinstalled** — no setup steps needed for any of
   the Grid workflow beyond `actions/checkout` and `actions/setup-java`.
