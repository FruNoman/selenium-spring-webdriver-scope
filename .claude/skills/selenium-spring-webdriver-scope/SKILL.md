---
name: selenium-spring-webdriver-scope
description: How this repo's TestNG + Spring + WebDriver link works — the thread-scoped WebdriverScope with a scoped proxy, profile-based browser/runner switching, and two small test classes run sequentially or in parallel (testng.xml), locally or on Grid (profile) — plus the non-obvious findings from building it (built-in WebDriverScope isn't thread-scoped, the hub's pretty-printed /status JSON breaks naive grep, class-level vs method-level @Profile). Use whenever touching WebDriverConfig/RemoteWebDriverConfig/WebdriverScope/BaseWebTest, adding a browser/runner combination, or changing how tests run in parallel.
---

# selenium-spring-webdriver-scope

Workflow skills come from the `web-automation` plugin
([qa-automation-toolkit](https://github.com/FruNoman/qa-automation-toolkit),
enabled in `.claude/settings.json`): `run-test`, `self-analysis` + its Stop hook + the `selenium` MCP. The
page-object / site-map / login skills don't apply here: this repo
deliberately has no pages, no elements and no site map.

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
scope and running 10 TestNG threads (`parallel="methods"`) against Grid
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
though the sequential run (`./gradlew test`) doesn't strictly need it.

## The `WebDriver` bean is a scoped proxy — everything else is a plain singleton

Every `WebDriver` `@Bean` (`WebDriverConfig`, `RemoteWebDriverConfig`) is
`@Scope(value = "webdriverscope", proxyMode = ScopedProxyMode.TARGET_CLASS)`.
Whoever autowires `WebDriver` — here only `BaseWebTest` — receives
**one CGLIB proxy, not a browser**. Every call on it goes through
`WebdriverScope.get()`, which returns the current thread's driver and
creates a new one if that session was already `quit()`. So the question
"which browser?" is answered **per call**, not when the holder was created.

Consequences, all verified 2026-09-28 (local and Grid, `parallel="none"`
and `"methods"` with 10 threads → 10 distinct session ids through one field):
- **The test injects the driver with plain `@Autowired`** — no `@Lazy`.
  `BaseWebTest` holds `protected WebDriver driver` (the proxy);
  `tearDownTest()` quits it with a plain `driver.quit()`, and the next call
  on that thread gets a new session.
- **Return type `RemoteWebDriver`, not `WebDriver`, on purpose**: the
  proxy is a subclass of the declared type, so casts to
  `JavascriptExecutor`/`TakesScreenshot`/`HasCapabilities` work
  (`WebdriverScope` casts to `RemoteWebDriver` for `getSessionId()`). With `ScopedProxyMode.INTERFACES` and a
  `WebDriver` return type those casts would throw `ClassCastException`.
  Still not possible: `instanceof ChromeDriver` on the proxy.

History, so nobody "fixes" it back: before this, the driver field held
the real browser object, which forced every page to be `prototype`,
every test field to be `@Lazy`, re-created the page on every call and
made public page fields read as `null` through the `@Lazy` CGLIB proxy.
The idea came from the Sphise `automation-tests` framework, whose pages
are singletons because Selenide's `$()` resolves the thread's driver per
call (their `@Scope(proxyMode = TARGET_CLASS)` on the abstract
`PageBaseCore` is actually inert — `@Scope` is not `@Inherited`).

## Logging: SLF4J/Logback, MDC per test

- `src/test/resources/logback-spring.xml`: console at INFO; `build/logs/run.log`
  (whole run) and `build/logs/tests/<Class.method>.log` (one per test,
  SiftingAppender on MDC `test`) at DEBUG. Every line carries
  `[thread] [Class.method]`, so parallel output stays readable.
- **Test lifecycle logging lives in a TestNG listener, never in test code**:
  `listeners/TestLoggingListener` (registered via
  `src/test/resources/META-INF/services/org.testng.ITestNGListener`, so no
  class refers to it) sets MDC `test`, logs `▶ START`, `✔ PASS` / `✘ FAIL`
  (+ stack) / `⏭ SKIP`, and `✘ <config> failed` for a failing
  `@BeforeMethod`. `BaseWebTest` only drives the browser.
- **MDC is set in `beforeConfiguration(ITestResult, ITestNGMethod)`, not in
  `onTestStart`** — verified: TestNG calls `onTestStart` only *after*
  `@BeforeMethod`, so setup logs would be
  untagged. The two-arg `beforeConfiguration` receives the test method a
  per-method configuration runs for (null for class/suite level → MDC
  cleared there). Thread-local + same-thread before/test/after → correct
  under `parallel="methods"`.
- The browser session id is logged by `WebdriverScope` when it creates a
  driver (`Browser session started: <id>`) — a driver event, not a test one.
- ⚠ Found via these logs (2026-09-23), not fixed yet: when `@BeforeMethod`
  fails before the browser started, `tearDownTest()`'s `driver.quit()` makes
  the scope create a browser only to quit it.
- **Not `EventFiringDecorator`/`WebDriverListener`** for driver-level logging:
  it returns a proxy that is not a `RemoteWebDriver`, which would break the
  `TARGET_CLASS` scoped proxy.
- Selenium's CDP "Unable to find version" WARN went away with the 4.49 bump —
  but only because a `selenium-devtools-v153` module arrived: the core stayed
  **4.19.1**, since Spring Boot's BOM downgrades every transitive Selenium
  module to its managed version. Pin it with `ext['selenium.version']` in
  build.gradle and check `./gradlew dependencies --configuration
  testRuntimeClasspath | grep selenium-api` (no `->`). Found 2026-09-24.
- Verified 2026-09-23: parallel, per-test files; a failing test body →
  FAIL + stack; a failing `@BeforeMethod` → `setUpTest failed` + stack + SKIP;
  browsers quit in both cases.

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

## Configuration: YAML, two axes, both Spring profiles

All config is YAML in `src/test/resources/`: `application.yml`
(`spring.profiles.active: chrome,local`, `base.url`,
`implicit.timeout.seconds`), `application-local.yml`, `application-grid.yml`
(`grid.url`). Any value can be overridden with an env var or `-D`
(`GRID_URL` → `grid.url`, `BASE_URL` → `base.url`). `build.gradle` forwards
`-Dspring.*` and `-Dgrid.*` into the test JVM; a new `-D` namespace → extend
that prefix list. (Per-test-environment `env/<name>.yml` files existed once
and were removed on 2026-09-28 as out of scope for this demo.)

## Sequential vs parallel: one suite, switched in testng.xml

Two test classes, 5 tests each: `WebFormTests` (web-form.html) and
`OtherPagesTests` (xhtmlTest/formPage/javascriptPage.html). Each test opens
its page itself and does one short action; `BaseWebTest` only quits the
browser in `@AfterMethod`. No page objects, no helpers — on purpose.

One suite, `src/test/resources/testng.xml`, one Gradle task `test`.
Parallelism is the suite's `parallel` attribute (`none` / `methods` /
`classes`, `thread-count="10"`); local vs Grid is the Spring profile. The
two are independent — no separate parallel suite or task (a `testParallel`
task + `testng-parallel.xml` existed until 2026-09-28 and were removed).

Verified 2026-09-28: local `none` 10/10 on `Test worker`, local `methods`
10/10, Grid `none` 10/10, Grid `methods` 10/10 on
`TestNG-test-demo-1..10`, 10 distinct session ids.

Running `parallel="methods"` against Grid needs actual node capacity:
`docker-compose.grid.yml`'s chrome node sets
`SE_NODE_MAX_SESSIONS=10`/`SE_NODE_OVERRIDE_MAX_SESSIONS=true` — without
raising this, TestNG threads would queue waiting for a free Grid slot
instead of actually running concurrently, silently defeating the point of
the test (it would still pass, just sequentially, hiding whether the
scope itself is correct). The firefox node has the same 10 (it was left at the default 1 until 2026-09-28 — `firefox,grid` then silently ran one test at a time).

## Non-obvious findings worth re-checking if any of this looks broken again

1. **`selenium/hub`'s `/status` returns pretty-printed JSON**
   (`"ready": true`, space after the colon) — unlike the
   `selenium/standalone-chrome` all-in-one image, which happened to
   return compact JSON (`"ready":true`) in earlier ad-hoc local testing.
   A `grep -q '"ready":true'` readiness check silently never matches
   against the real hub image and times out after the full budget,
   *while the Grid is actually ready the whole time* — always parse
   Grid's `/status` with `jq`, never string-match the JSON directly.
2. **An explicit TestNG suite XML overrides Gradle's `--tests` filter** —
   `./gradlew test --tests SomeClass` used to run the *whole* suite. Fixed
   in `build.gradle`: the `test` task's `doFirst` clears the suite XML when
   a `--tests` filter is present (verified 2026-09-23 for a method, a class
   and the unfiltered suite). Consequence: a class missing from
   `testng.xml` still runs with `--tests` but never in the full suite.
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
