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
That's the whole "fix": there isn't one to write.

[`WebFormPage`](src/main/java/com/frunoyman/webdriverscope/pages/WebFormPage.java) —
a page object over
[selenium.dev's own web-form demo page](https://www.selenium.dev/selenium/web/web-form.html),
built the way a Spring-managed page object usually looks: `@Autowired
WebDriver driver`, `@FindBy` fields resolved via `PageFactory.initElements`
in a `@PostConstruct`. It's deliberately `@Component @Scope("prototype")`
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

This is deliberately just the driver config + a proof test. No page
objects, no DB layer, no CI reporting — none of that is the point here.
