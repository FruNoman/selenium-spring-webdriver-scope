package com.frunoyman.webdriverscope;

import com.frunoyman.webdriverscope.pages.BasePage;
import com.frunoyman.webdriverscope.pages.WebFormPage;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.annotations.AfterMethod;
import org.testng.ITestResult;
import org.testng.annotations.BeforeMethod;

import java.lang.reflect.Method;

/**
 * Every test class in this repo extends this one. Same
 * {@code @SpringBootTest(classes = ...)} signature on every class is what
 * makes Spring reuse (cache) a single ApplicationContext across all of
 * them — the exact condition that would turn a plain singleton
 * WebDriver bean into a dead-session trap after the first quit(), if
 * Spring Boot Test's built-in WebDriverContextCustomizerFactory weren't
 * quietly handling that for us (see WebDriverConfig).
 */
@SpringBootTest(classes = DemoApplication.class)
public class BaseWebTest extends AbstractTestNGSpringContextTests {

    private static final Logger log = LoggerFactory.getLogger("test");

    @Autowired
    protected ApplicationContext applicationContext;

    // scoped proxy — always the current thread's live driver
    @Autowired
    protected WebDriver driver;

    /** The page every test starts on. Override for tests of another site/page. */
    protected Class<? extends BasePage> entryPage() {
        return WebFormPage.class;
    }

    /**
     * Runs before the entry page is opened — the place to put the browser
     * into a logged-in state (inject a session cookie / token, or log in
     * through the UI). No-op by default. A subclass's own @BeforeMethod
     * would run only after this class's one, i.e. after open() — too late.
     */
    protected void authenticate() {
    }

    /**
     * One method on purpose: TestNG doesn't guarantee the order of several
     * @BeforeMethods in a class, and the MDC must be set before anything
     * logs. MDC is thread-local and TestNG runs before/test/after on the
     * same thread, so it stays correct under parallel="methods".
     */
    @BeforeMethod(alwaysRun = true)
    public void setUpTest(Method method) {
        MDC.put("test", getClass().getSimpleName() + "." + method.getName());
        log.info("▶ START");
        authenticate();
        applicationContext.getBean(entryPage()).open();
        MDC.put("session", String.valueOf(((RemoteWebDriver) driver).getSessionId()));
        log.info("Opened entry page {} (session {})", entryPage().getSimpleName(), MDC.get("session"));
    }

    @AfterMethod(alwaysRun = true)
    public void tearDownTest(ITestResult result) {
        long ms = result.getEndMillis() - result.getStartMillis();
        switch (result.getStatus()) {
            case ITestResult.SUCCESS -> log.info("✔ PASS (test body {} ms)", ms);
            case ITestResult.SKIP -> log.warn("⏭ SKIP: {}", String.valueOf(result.getThrowable()));
            default -> log.error("✘ FAIL (test body {} ms): {}", ms, String.valueOf(result.getThrowable()), result.getThrowable());
        }
        try {
            driver.quit();
        } finally {
            MDC.clear();
        }
    }
}
