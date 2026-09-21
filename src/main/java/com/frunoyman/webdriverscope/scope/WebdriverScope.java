package com.frunoyman.webdriverscope.scope;

import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.remote.SessionId;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.context.support.SimpleThreadScope;

import java.util.Objects;

/**
 * Thread-scoped, except it also checks whether the cached driver's browser
 * session is still alive. Selenium's RemoteWebDriver.quit() nulls out its
 * own sessionId — that's the signal a test's teardown already killed this
 * instance, so the next get() on the same thread should hand out a fresh
 * driver instead of the dead one Spring would otherwise keep serving (test
 * threads get reused across test classes when Spring caches the
 * ApplicationContext, which is exactly when this bites).
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
        // Deliberately a no-op: Spring would otherwise run this callback
        // when the context itself shuts down, not when a test calls
        // quit() — by then it's too late to matter, and firing it at the
        // wrong time has caused more confusion than it's worth.
    }
}
