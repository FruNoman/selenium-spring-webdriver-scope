---
name: run-test
description: Run this repo's Selenium/TestNG tests — one method, one class, the whole suite, locally or on Selenium Grid, sequential or parallel — and diagnose a failure without weakening the test. Use when asked to run, re-run, check or debug tests.
---

# Running tests

## Commands

| What | Command |
|---|---|
| Whole suite (testng.xml, `parallel="methods"` ×3) | `./gradlew test` |
| One class | `./gradlew test --tests '*TableElementTests'` |
| One method | `./gradlew test --tests 'com.frunoyman.webdriverscope.TableElementTests.readsHeadersAndRows'` |
| Firefox / Grid | add `-Dspring.profiles.active=firefox,local` / `chrome,grid` |
| Parallel Grid demo (10 threads) | `./gradlew testParallel -Dspring.profiles.active=chrome,grid` |
| Force a re-run of unchanged code | add `--rerun-tasks` |

- `--tests` works because `build.gradle` drops the suite XML when a
  `--tests` filter is present (verified 2026-09-23). Without a filter the
  suite XML decides — so a class missing from `testng.xml` silently never
  runs in the full suite.
- **Gradle caches test results**: running the same command again with no
  code change prints `UP-TO-DATE` and runs nothing. Use `--rerun-tasks`
  when re-running for flakiness or after an environment change.
- Only `-Dspring.*` and `-Dgrid.*` reach the test JVM (see `build.gradle`).

## Grid

```bash
docker compose -f docker-compose.grid.yml up -d
until curl -s localhost:4444/status | grep -qE '"ready" *: *true'; do sleep 2; done
# ... run with -Dspring.profiles.active=chrome,grid
docker compose -f docker-compose.grid.yml down
```
The hub's `/status` is pretty-printed JSON — match with the regex above (or
`jq`), never `grep '"ready":true'`. Always bring the Grid down afterwards.

## Output

- Console: `PASSED`/`FAILED` per method; tests print session ids to stdout.
- HTML report: `build/reports/tests/test/index.html`; raw XML:
  `build/test-results/test/`.
- Different session ids across tests = the driver scope is working.

## Diagnosing a failure

1. **Classify first:**
   - element not found / wrong element → locator (check uniqueness and the
     real DOM with the `selenium` MCP — `new-web-test` §2);
   - action "succeeded" but nothing changed → timing/race (page JS not ready);
     wait for a readiness signal, never `Thread.sleep`;
   - `NullPointerException` on a page field → someone read a page field
     directly or made a page stateful — see the scope skill;
   - `SessionNotCreated`/connection refused → environment (browser/driver,
     Grid down, external site down) — say so, don't "fix" the test;
   - genuine product behaviour differing from the expectation → report it.
2. **Never make a test pass** by deleting/loosening an assertion, adding a
   sleep, or catching the exception.
3. A flaky test: re-run the single method 3× with `--rerun-tasks` and report
   the pass ratio, not just the last result.
4. Re-run after the fix and report the actual output. Record any new gotcha
   in `site-map`.
