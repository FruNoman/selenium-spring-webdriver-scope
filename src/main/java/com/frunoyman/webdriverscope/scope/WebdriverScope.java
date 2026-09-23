package com.frunoyman.webdriverscope.scope;

import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.remote.SessionId;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.context.support.SimpleThreadScope;

import java.util.Objects;

/**
 * {@code SimpleThreadScope} already gives each thread its own instance
 * via a ThreadLocal — that's the actual fix for parallel test execution
 * (TestNG {@code parallel="methods"}), not something Spring Boot's
 * built-in WebDriverScope provides: that one shares a single instance
 * per bean name across every thread, guarded by a lock, which makes it
 * fine for sequential recycling across cached contexts but wrong for
 * concurrent threads fighting over the same browser.
 *
 * What this subclass adds on top of the per-thread isolation: discard
 * a thread's cached driver and create a fresh one if its session was
 * already quit() — the same liveness check, just applied per-thread
 * instead of globally.
 */
public class WebdriverScope extends SimpleThreadScope {

    @Override
    public Object get(String name, ObjectFactory<?> objectFactory) {
        Object driver = super.get(name, objectFactory);
        SessionId sessionId = ((RemoteWebDriver) driver).getSessionId();
        if (Objects.isNull(sessionId)) {
            super.remove(name);
            driver = super.get(name, objectFactory);
        }
        return driver;
    }

    @Override
    public void registerDestructionCallback(String name, Runnable callback) {
        // no-op: BaseWebTest.tearDownTest() already calls quit() explicitly,
        // and SimpleThreadScope has no thread-exit hook to run this from anyway.
    }
}
