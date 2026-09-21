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

So this repo isn't "here's my custom scope" anymore. It's a clean demo of
the thing you actually get for free — with a plain `@Bean` and no scope
annotation at all.

## What's here

[`WebDriverConfig`](src/main/java/com/frunoyman/webdriverscope/config/WebDriverConfig.java) —
a completely ordinary `@Bean` per browser, no scope, no custom code.
That's the whole "fix": there isn't one to write. `@Profile("local")` on
the class gates the whole config, `@Profile("chrome")` /
`@Profile("firefox")` on each `@Bean` picks the browser — a bean only
gets registered when both match.

[`RemoteWebDriverConfig`](src/main/java/com/frunoyman/webdriverscope/config/RemoteWebDriverConfig.java) —
the same two browsers, but over Selenium Grid (`RemoteWebDriver` +
`grid.url`), gated by `@Profile("grid")` on the class instead of
`local`. Same bean names (`chromeDriver`/`firefoxDriver`), same
`@Profile("chrome")`/`@Profile("firefox")` split on the methods — only
one of the two config classes is ever active, so there's no collision.
Switching between local and Grid, or between browsers, is purely a
matter of which profiles are active:

```bash
./gradlew test                                        # chrome, local (default)
./gradlew test -Dspring.profiles.active=firefox,local  # firefox, local
./gradlew test -Dspring.profiles.active=chrome,grid    # chrome, Grid
./gradlew test -Dspring.profiles.active=firefox,grid \
    -Dgrid.url=http://my-hub:4444/wd/hub               # firefox, Grid, custom hub
```

No test, no page object, no code anywhere references which config
supplied the `WebDriver` bean — that's the point.

[`BasePage`](src/main/java/com/frunoyman/webdriverscope/pages/BasePage.java) —
what every page object extends: `@Autowired protected WebDriver driver`
plus a `@PostConstruct` that runs `PageFactory.initElements(driver, this)`
so `@FindBy` fields on the subclass resolve. No `LoadableComponent`, no
navigation/assertion lifecycle — just the driver wiring every page needs.

[`WebFormPage`](src/main/java/com/frunoyman/webdriverscope/pages/WebFormPage.java) —
a page object over
[selenium.dev's own web-form demo page](https://www.selenium.dev/selenium/web/web-form.html),
extending `BasePage`. It's deliberately `@Component @Scope("prototype")`
— a singleton page bean would get its `driver` field wired once, at
first creation, and keep pointing at that driver forever, even after
`WebDriverScope` recycles it. `prototype` means a fresh `WebFormPage`
(with a fresh `driver` reference) every time one is requested. Test
classes then inject it with `@Lazy @Autowired`, same reasoning as the
`driver` field itself: without `@Lazy`, the *injection point* would
freeze to the first prototype instance ever created.

[`FirstFormTests`](src/test/java/com/frunoyman/webdriverscope/FirstFormTests.java)
and
[`SecondFormTests`](src/test/java/com/frunoyman/webdriverscope/SecondFormTests.java)
are two separate TestNG classes, sharing one Spring context on purpose.
Every test method:
1. Opens the form page via `WebFormPage`.
2. Asserts the text field is empty (a real, live, unused browser session —
   not a page state left over from the previous test).
3. Prints the Selenium session id, so a live run visibly shows a
   **different session id per test method**, not one browser being reused
   silently across "atomic" tests.

The browsers run with a visible window (not headless) — the point of
this repo is to *watch* a fresh Chrome window open, get used, and close
per test, so headless would defeat the demo.

Run it:

```bash
./gradlew test
```

## Running it on Selenium Grid, for real

[`docker-compose.grid.yml`](docker-compose.grid.yml) spins up a real
hub + node topology, not the single all-in-one `standalone-chrome`
image — one `selenium/hub` container routing to separate
`selenium/node-chrome` and `selenium/node-firefox` containers, the same
shape a CI pipeline or a Kubernetes deployment scales out by adding more
node containers/pods.

```bash
docker compose -f docker-compose.grid.yml up -d
# wait for it: curl http://localhost:4444/status until "ready":true
./gradlew test -Dspring.profiles.active=chrome,grid
./gradlew test -Dspring.profiles.active=firefox,grid
docker compose -f docker-compose.grid.yml down -v
```

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

- A Spring version, or a non-Boot Spring Test setup, old enough not to
  carry `spring-boot-test-autoconfigure`'s fix.
- You want the driver managed the same way in **production** code too
  (the built-in one only exists on the test classpath), e.g. a
  long-running service that recycles WebDriver sessions outside of tests.
- You want different liveness logic than a null session id — e.g.
  pinging the driver, capping session age, or logging every recycle.

For a plain Spring Boot + Selenium + TestNG/JUnit test suite, though:
check for `spring-boot-test-autoconfigure` on your classpath before
writing this yourself. There's a good chance you already have it.

## What this is not

This is deliberately just the driver config, a page object, and a proof
test. No DB layer, no CI reporting, no PageFactory hierarchy beyond one
page — none of that is the point here.
