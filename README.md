# selenium-spring-webdriver-scope

A minimal, extracted version of a pattern I've used in two production
Spring Boot + Selenium + TestNG frameworks: a custom Spring bean scope
that keeps `WebDriver` reliably fresh across tests, even when Spring
caches the `ApplicationContext` between test classes.

## The problem

`@SpringBootTest` caches the `ApplicationContext` across test classes that
share the same configuration — a documented, deliberate Spring feature,
because spinning up a full context per class would make a real suite
unbearably slow.

That collides with how Selenium wants to be used. If `WebDriver` is a
normal Spring bean (singleton, or thread-scoped — same problem, since
TestNG/JUnit runners reuse worker threads across classes), the *first*
test that calls `driver.quit()` in teardown kills the browser session for
every test after it that shares the same context/thread. The next
`@Autowired WebDriver` doesn't get a new browser — it gets the same bean
instance back, now pointing at a dead session, and fails with
`invalid session id`.

The usual fixes each cost something:
- **`@DirtiesContext`** — Spring's own official answer: throw away and
  rebuild the whole context after a test. Correct, but exactly the
  expensive thing context caching exists to avoid.
- **`@Scope("prototype")`** — a new bean each time, but Spring does not
  call destroy methods on prototype beans automatically, so you're back to
  manually tracking and quitting every instance anyway.
- **A static `ThreadLocal<WebDriver>` outside Spring entirely** — the most
  common pattern in non-Spring Selenium frameworks, and a perfectly good
  one, but it means the driver isn't a real Spring bean: no `@Autowired`,
  no participation in the container.

## This repo's fix

[`WebdriverScope`](src/main/java/com/frunoyman/webdriverscope/scope/WebdriverScope.java)
extends Spring's own `SimpleThreadScope` and adds one check: before handing
back the cached instance, it looks at `RemoteWebDriver.getSessionId()`.
`quit()` nulls that field out. If it's null, the scope discards the dead
entry and creates a fresh driver instead — keeping the expensive
`ApplicationContext` alive while only ever recycling the cheap part.

```java
@Bean
@Scope("webdriverscope")
@Profile("chrome")
public WebDriver chromeDriver() {
    ...
}
```

## Proof, not just an explanation

[`FirstFormTests`](src/test/java/com/frunoyman/webdriverscope/FirstFormTests.java)
and
[`SecondFormTests`](src/test/java/com/frunoyman/webdriverscope/SecondFormTests.java)
are two separate TestNG classes, sharing one Spring context on purpose.
Every test method:
1. Navigates to [selenium.dev's own web-form demo page](https://www.selenium.dev/selenium/web/web-form.html).
2. Asserts the text field is empty (a real, live, unused browser session —
   not a page state left over from the previous test).
3. Prints the Selenium session id, so a live run visibly shows a
   **different session id per test method**, not one browser being reused
   silently across "atomic" tests.

Run it:

```bash
./gradlew test
```

## A surprise while writing this up

I went to prove the negative case — strip `@Scope("webdriverscope")`,
fall back to plain `singleton`, watch `SecondFormTests` die on
`invalid session id`. It didn't die. All three tests kept passing.

Turns out `spring-boot-test-autoconfigure` already ships a
`WebDriverContextCustomizerFactory` (see
`org.springframework.boot.test.autoconfigure.web.servlet.WebDriverScope`
if it's on your classpath). It auto-detects any `WebDriver`-typed bean in
a Spring Boot test and quietly wraps it in its own scope — same idea as
this repo's, close a dead session and hand back a fresh one — registered
under the name `"webDriver"`. It's been there since early Spring Boot 2.x,
I just never ran into it because our production frameworks always
declared an explicit custom scope, which wins over there being no scope
declared at all.

So: if you're on a recent enough Spring Boot and your `WebDriver` bean has
no scope declared, you may already be covered for free. `WebdriverScope`
in this repo still earns its keep when you want an explicit, visible
scope you control (e.g. to also validate the session differently, log on
recycle, or run on a Spring version old enough not to have the built-in
one) — but check the built-in one first.

## What this is not

This is deliberately just the scope + driver config + a proof test, pulled
out of two real frameworks that also carry PageFactory-style page objects,
gRPC clients, DB layers, and CI reporting — none of which is the point
here. If you're evaluating whether to reuse this: the scope class is
~20 lines and has no dependency on anything else in this repo.
