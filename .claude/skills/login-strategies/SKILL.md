---
name: login-strategies
description: Choosing and implementing how tests get a logged-in browser — real UI login vs. skipping it by injecting a server session cookie, a token into localStorage/sessionStorage, IdP SSO cookies, or basic auth. Covers how to find out where the app keeps auth state, the injection mechanics and gotchas (domain-first rule, cookie attributes, CookieManager/localhost bug, parallel users, expiry), and where it plugs into BaseWebTest. Use when a new app needs authentication in tests, when login makes the suite slow or flaky, or when onboarding a new project.
---

# Login strategies: UI vs. session/token injection

Reference implementation of the hardest variant (OIDC server session via
Keycloak, raw-HTTP login + `JSESSIONID` injection):
`~/IdeaProjects/selenium-keycloak-session-skip` — its `SKILL.md` and
`auth/KeycloakSessionHelper.java`. Verified there, not in this repo.

## The rule: log in through the UI once, skip it everywhere else

- **One login-flow test class** (positive + wrong-password negative) proves
  the real UI login works. It's the only place the login form is touched.
- **Every other test starts already authenticated** by injection. If login
  breaks, one class fails with an obvious cause — not the whole suite with
  twenty unrelated-looking failures. Injection is also 2–10× faster and
  removes the most common source of flakiness (the login page itself).
- Each protected area keeps one **negative test without a session**
  (`deleteAllCookies()` / clear storage → expect the login page), proving it's
  really protected, not reachable because some state leaked.

## Step 1 — find out where the app keeps auth state (don't guess)

Log in once by hand in the `selenium` MCP browser, then inspect:

| Check | How (selenium MCP) | Means |
|---|---|---|
| Cookies | `get_cookies` | an opaque id (`JSESSIONID`, `SESSION`, `connect.sid`, `_app_session`) → **server session** |
| Web storage | `execute_script "return JSON.stringify({l: {...localStorage}, s: {...sessionStorage}})"` | a JWT (`eyJ…`) / `access_token` key → **SPA token** |
| Login traffic | `diagnostics {type:"network"}` during login | redirects to `/auth/realms/…`, `login.microsoftonline.com`, `*.okta.com` → **OIDC/SSO**; a plain `POST /login` or `/api/auth` → **form/API login** |
| Frontend code (if available) | grep for `keycloak-js`, `oidc-client`, `msal`, `localStorage.setItem` | confirms SPA token vs. server session |

Write the finding into `site-map` (it's a fact about the app).

## Step 2 — pick the strategy

| Where auth lives | Strategy | How the credential is obtained |
|---|---|---|
| Server session, simple form/API login | **Cookie injection** | HTTP `POST` to the login endpoint (with CSRF token if the form has one) → take the session cookie from `Set-Cookie` |
| Server session behind OIDC (Spring OAuth2 Client, etc.) | **Cookie injection after a raw-HTTP OIDC dance** | follow redirects app → IdP → form POST → callback by hand; the app's backend creates a real session (see Keycloak repo). A token from the IdP alone is **not** enough — it never reaches the app |
| SPA keeps JWT in localStorage/sessionStorage | **Storage injection** | token from the IdP directly (ROPC `password` grant, or the app's own `/api/login`) → `localStorage.setItem` under the exact key and JSON shape the app uses |
| IdP SSO cookie on the IdP's domain | **IdP cookie seeding** | seed `KEYCLOAK_IDENTITY`/`KEYCLOAK_SESSION`/`AUTH_SESSION_ID` (names vary by version) on the IdP domain; the OIDC redirect then completes silently. Fragile — last resort |
| HTTP Basic auth | URL credentials or a header | `https://user:pass@host/` (Chrome), or set `Authorization` via CDP/BiDi network interception |
| None of the above works | **Ask the developers** | a test-only login endpoint / backdoor token for test envs is often a one-hour change on their side and beats any clever workaround |

## Step 3 — injection mechanics (the gotchas are the point)

- **Domain first.** Selenium can only set a cookie / storage for the origin
  currently loaded. Land on a cheap page of the app's origin first — a public
  `/ping`/`/health`, a static asset, or even a 404 page — then inject, then
  open the real entry page. Never land on the login page itself if it
  redirects away.
- **Cookie attributes must match** what the server set: `domain` (host-only
  vs `.example.com`), `path`, `secure` (HTTPS), `httpOnly` (Selenium can set
  it), `sameSite`. A wrong domain silently yields "not logged in".
- **Raw-HTTP login: manage cookies yourself.** `java.net.CookieManager`
  rewrites a bare host like `localhost` to `localhost.local` and silently
  drops cookies → a confusing 400 several hops later (hit for real in the
  Keycloak repo). Keep a `Map<String,String>` jar, send `Cookie` by hand,
  follow redirects manually (`HttpClient.Redirect.NEVER`).
- **Storage injection needs the app's exact contract**: key name, and often
  a JSON object (`{access_token, refresh_token, expires_at}`) rather than a
  bare JWT. Copy the shape from a real login (Step 1), then `refresh` the
  page so the app reads it on boot.
- **Expiry.** Tokens/sessions time out. Obtain per test (simple, always
  fresh) or cache per user per thread with an expiry check — never a static
  cached token shared by the whole run.
- **Parallel runs.** Many sessions of one user are fine for Keycloak and
  most apps. Apps that allow only one session per user (the newest kicks the
  others) need a **user pool**: one user per TestNG thread.
- **Credentials** come from properties / environment (`-D`/env on CI), never
  hardcoded in code.

## Step 4 — plug it into this framework

`BaseWebTest.openEntryPage()` calls `authenticate()` **before** opening
`entryPage()`. (A subclass's own `@BeforeMethod` would run after the base
class's one — i.e. after the entry page was already opened, too late.)

```java
public abstract class AuthenticatedWebTest extends BaseWebTest {

    @Autowired
    private SessionLogin sessionLogin;        // your strategy, a @Component

    @Override
    protected void authenticate() {
        sessionLogin.loginAs(Users.DEFAULT);  // land on origin → inject
    }
}
```

Cookie variant of the strategy body:
```java
driver.get(appUrl + "ping");                         // domain first
driver.manage().addCookie(new Cookie.Builder("JSESSIONID", sessionId)
        .domain(host).path("/").isHttpOnly(true).build());
```
Storage variant:
```java
driver.get(appUrl);                                  // domain first
((JavascriptExecutor) driver).executeScript(
        "localStorage.setItem(arguments[0], arguments[1])", "auth", tokenJson);
```
(`driver` is the scoped proxy — casting to `JavascriptExecutor` works, see
the scope skill.) The strategy bean is a stateless singleton like pages:
per-test data (user, token) stays in local variables, never in fields.

The UI login itself stays a page object (`LoginPage.loginAs(user)`), used by
the login-flow test class and as a fallback strategy while injection isn't
built yet.
