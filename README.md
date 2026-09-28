# selenium-spring-webdriver-scope

A minimal, runnable example of a Spring Boot + Selenium + TestNG gotcha
I hit years ago in two production frameworks — and what actually fixes
it, which turned out not to be what I built back then.

## The problem

`@SpringBootTest` caches the `ApplicationContext` across test classes that
share the same configuration — a documented, deliberate Spring feature,
because spinning up a full context per class would make a real suite
unbearably slow.

That collides with how Selenium wants to be used. If `WebDriver` is just
a plain Spring bean (default scope: singleton), the *first* test that
calls `driver.quit()` in teardown kills the browser session for every
test after it that shares the same cached context. The next
`@Autowired WebDriver` doesn't get a new browser — it gets the same bean
instance back, now pointing at a dead session, and fails with
`invalid session id`.

Back in 2018–2019 I "fixed" this by writing my own Spring bean scope: a
`SimpleThreadScope` subclass that checks `RemoteWebDriver.getSessionId()`
before handing back a cached instance, discards it if it's `null` (i.e.
already `quit()`), and creates a fresh one. It worked, and I carried that
pattern into every Spring+Selenium framework I built since.

## The twist

While writing this repo up, I went to prove the negative case — remove
the custom scope, fall back to plain `singleton`, watch a second test
class fail on `invalid session id`. It didn't fail. Every test kept
passing.

Turns out `spring-boot-test-autoconfigure` already ships exactly this
fix, built in: `WebDriverContextCustomizerFactory`
(`org.springframework.boot.test.autoconfigure.web.servlet.WebDriverScope`
if it's on your classpath). It auto-detects any `WebDriver`-typed bean in
a `@SpringBootTest` and transparently wraps it in its own scope — same
idea as my hand-rolled one: discard a dead session, hand back a fresh
driver. No annotation, no config, nothing to opt into. It's been there
since early Spring Boot 2.x, and it works with TestNG the same as JUnit,
since it hooks in at the Spring TestContext framework level, not the
runner.

So a purely sequential run needs none of that — a plain `@Bean`, no
scope annotation, and it's still correct. The custom scope comes back later
in this README, for a problem the built-in one doesn't touch at all:
running tests in parallel.

## What's here

The whole repo is the driver wiring, a handful of page objects and two test classes:

```
scope/WebdriverScope.java         SimpleThreadScope + "discard a quit() session"
config/WebDriverConfig.java       local browsers   (@Profile("local"))
config/RemoteWebDriverConfig.java Grid browsers    (@Profile("grid"))
pages/BasePage.java               @Autowired WebDriver + PageFactory.initElements
pages/*Page.java                  one @Component per selenium.dev test page
BaseWebTest.java                  TestNG + Spring: @Autowired WebDriver, quit() after each test
WebFormTests.java                 5 tests on web-form.html
OtherPagesTests.java              5 tests on other selenium.dev test pages
testng.xml                        parallel="none" | "methods" | "classes"
```

[`WebDriverConfig`](src/main/java/com/frunoyman/webdriverscope/config/WebDriverConfig.java) —
one `@Bean` per browser, each
`@Scope(value = "webdriverscope", proxyMode = ScopedProxyMode.TARGET_CLASS)`.
`@Profile("local")` on the class gates the whole config,
`@Profile("chrome")`/`@Profile("firefox")` on each `@Bean` picks the
browser — a bean only gets registered when both match.

[`RemoteWebDriverConfig`](src/main/java/com/frunoyman/webdriverscope/config/RemoteWebDriverConfig.java) —
the same two browsers over Selenium Grid (`RemoteWebDriver` + `grid.url`),
gated by `@Profile("grid")` instead of `local`. Same bean names, same
browser split — only one of the two config classes is ever active.
Local vs Grid, Chrome vs Firefox is purely a matter of active profiles:

```bash
./gradlew test                                        # chrome, local (default)
./gradlew test -Dspring.profiles.active=firefox,local  # firefox, local
./gradlew test -Dspring.profiles.active=chrome,grid    # chrome, Grid
./gradlew test -Dspring.profiles.active=firefox,grid \
    -Dgrid.url=http://my-hub:4444/wd/hub               # firefox, Grid, custom hub
```

[`BaseWebTest`](src/test/java/com/frunoyman/webdriverscope/BaseWebTest.java) —
extends `AbstractTestNGSpringContextTests`, which is what lets a TestNG
class take `@Autowired` fields from a cached Spring context. It holds
`@Autowired protected WebDriver driver`: that field is a **scoped proxy**,
not a browser. Every call on it goes through `WebdriverScope.get()` and
lands on the current thread's live driver — a fresh one if the previous
session was `quit()`. `@AfterMethod` quits the browser.

[`BasePage`](src/main/java/com/frunoyman/webdriverscope/pages/BasePage.java) —
what every page object extends: `@Autowired protected WebDriver driver`
(the same scoped proxy), `@Value("${base.url}") baseUrl`, an abstract
`open()`, and a `@PostConstruct` that runs
`PageFactory.initElements(driver, this)` so `@FindBy` fields resolve.
Pages ([`WebFormPage`](src/main/java/com/frunoyman/webdriverscope/pages/WebFormPage.java),
`SubmittedFormPage`, `XhtmlTestPage`, `ResultPage`, `FormPage`,
`JavascriptPage`) are **plain singleton `@Component`s**. That's safe only
because of the proxy: `initElements` runs once, but each `@FindBy` field
is a lazy locator that searches through `driver` on every use — i.e.
through the current thread's browser. One page instance serves every test
and every parallel thread, so a page must keep no state of its own in
fields. A transition returns the next page, which is itself injected with
`@Autowired` (`WebFormPage.submit()` → `SubmittedFormPage`).

[`WebFormTests`](src/test/java/com/frunoyman/webdriverscope/WebFormTests.java)
and [`OtherPagesTests`](src/test/java/com/frunoyman/webdriverscope/OtherPagesTests.java) —
5 tests each. A test `@Autowired`s the pages it needs, calls `open()`
and does one short thing (type, select, click, submit, follow a link):

```java
@Autowired
private WebFormPage webFormPage;

@Test
public void submitForm() {
    SubmittedFormPage submitted = webFormPage.open().submit();
    assertEquals(submitted.getMessage(), "Received!");
}
```

Two independent switches, no code changes:

- **Where and which browser** — Spring profile: `chrome,local` (default in
  `application.yml`), `firefox,local`, `chrome,grid`, `firefox,grid`.
- **How many threads** — `parallel` in
  [`testng.xml`](src/test/resources/testng.xml): `none` (one thread),
  `methods` (up to `thread-count="10"` tests at once) or `classes` (one
  thread per class). Committed as `methods`.

```bash
./gradlew test                                        # local, as testng.xml says
docker compose -f docker-compose.grid.yml up -d --wait
./gradlew test -Dspring.profiles.active=chrome,grid    # Grid, as testng.xml says
```

Every log line carries `[thread] [Class.method]` and each new browser logs
`Browser session started: <id>`, so a run shows 10 distinct sessions — on
`Test worker` with `parallel="none"`, on `TestNG-test-demo-1..10` with
`parallel="methods"`.

The browsers run with a visible window (not headless) — the point locally
is to *watch* a fresh Chrome window open, get used and close per test.

## Running it on Selenium Grid, for real

[`docker-compose.grid.yml`](docker-compose.grid.yml) spins up a real
hub + node topology, not the single all-in-one `standalone-chrome`
image — one `selenium/hub` container routing to separate
`selenium/node-chrome` and `selenium/node-firefox` containers, the same
shape a CI pipeline or a Kubernetes deployment scales out by adding more
node containers/pods.

```bash
docker compose -f docker-compose.grid.yml up -d --wait   # returns once hub + nodes are healthy
./gradlew test -Dspring.profiles.active=chrome,grid
./gradlew test -Dspring.profiles.active=firefox,grid
docker compose -f docker-compose.grid.yml down -v
```

**Capacity: each node has 10 slots** (`SE_NODE_MAX_SESSIONS=10` +
`SE_NODE_OVERRIDE_MAX_SESSIONS=true`), matching `thread-count="10"`. The
docker-selenium default is **1 slot per node** (the Firefox node had exactly
that until this was added) —
`parallel="methods"` then still passes, but the Grid hands out browsers one
at a time and the run is silently sequential. Check the slots with
`curl -s localhost:4444/status | jq '[.value.nodes[] | {browser: .slots[0].stereotype.browserName, slots: (.slots|length)}]'`,
and watch sessions live at http://localhost:4444/ui.

The node images ship their own virtual display (Xvfb), so the
non-headless `ChromeOptions`/`FirefoxOptions` in `RemoteWebDriverConfig`
work unmodified even on a CI runner with no real screen — "visible
window" happens inside the node container, not on the host running the
tests.

## Running it on GitHub Actions

[`.github/workflows/grid-tests.yml`](.github/workflows/grid-tests.yml)
runs the exact sequence above as a CI job: start the Grid via
`docker-compose.grid.yml`, poll `/status` until ready, run
`./gradlew test -Dspring.profiles.active=$BROWSER,grid`, upload the
test report, tear the Grid down — `if: always()` so teardown and the
report upload happen even if the tests fail.

It fires automatically on every push to `master` and on every pull
request. To run it manually and pick the browser yourself:

1. Push this repo to GitHub (or use the fork/copy you already have).
2. Open the **Actions** tab.
3. Select **Grid tests** in the left-hand workflow list.
4. Click **Run workflow** (top right) — GitHub shows a dropdown for the
   `browser` input (`chrome` or `firefox`); pick one and confirm.
5. Watch the job; the **Grid tests** run's **Summary** page has a
   `test-report` artifact download if you want the full HTML report
   from that run.

No self-hosted runner, no pre-existing Grid to point at — GitHub's
standard `ubuntu-latest` runners already have Docker and `docker compose`
preinstalled, so the workflow's Grid is entirely disposable: created at
the start of the job, gone at the end, isolated from every other job
running concurrently.

## When you'd still want a custom scope

Turns out: sooner than I first thought. I originally wrote this section
assuming the built-in scope makes the custom one obsolete. It doesn't —
they solve two different problems.

[`WebdriverScope`](src/main/java/com/frunoyman/webdriverscope/scope/WebdriverScope.java)
is back in this repo, `@Scope("webdriverscope")` on `RemoteWebDriverConfig`'s
beans, because running the tests in parallel exposed the gap: run them
with TestNG `parallel="methods" thread-count="10"`
against the built-in scope and all 10 threads get handed **the same**
`RemoteWebDriver` instance — I checked the bytecode: Boot's
`WebDriverScope` keeps one instance per bean name in a single
synchronized map, with no notion of "thread." It's built to fix a
*sequential* problem (a cached context handing back a dead session to
the *next* test), not a *concurrent* one (ten threads sharing one
browser *right now*). Tried it: 10 threads, 1 shared session id, then
all 10 `quit()` calls collided —
`UnreachableBrowserException`/`RejectedExecutionException`.

`WebdriverScope` extends `SimpleThreadScope`, which is genuinely
thread-scoped (a `ThreadLocal` under the hood) — that's what actually
fixes it, and it's exactly what I built into `sem` and
`backend-automation-framework` without ever being told this by anyone;
turns out it earns its place for a reason the built-in scope doesn't
cover at all. Same test, same Grid, this scope active:

```bash
docker compose -f docker-compose.grid.yml up -d
# testng.xml: parallel="methods"
./gradlew test -Dspring.profiles.active=chrome,grid
```

10 threads, 10 distinct session ids, no collisions.

So, updated answer — reach for a custom `SimpleThreadScope`-based scope
when:

- You run tests in parallel within one JVM (TestNG `parallel="methods"`
  or `"classes"`, JUnit 5 parallel execution) — the built-in scope alone
  will silently hand every thread the same browser.
- A Spring version, or a non-Boot Spring Test setup, old enough not to
  carry `spring-boot-test-autoconfigure`'s fix at all.
- You want the driver managed the same way in **production** code too
  (the built-in one only exists on the test classpath).
- You want different liveness logic than a null session id.

For a plain **sequential** Spring Boot + Selenium suite, the built-in
scope alone is genuinely enough — no annotation needed. The moment you
add `parallel=`, you need both: the built-in one keeps recycling dead
sessions across cached contexts, `WebdriverScope` keeps threads from
stepping on each other.

## What this is not

This is deliberately just the driver config, the scope, plain
`PageFactory` page objects and two small test classes. No custom elements,
no DB layer, no test-env files — none of that is the point here.
