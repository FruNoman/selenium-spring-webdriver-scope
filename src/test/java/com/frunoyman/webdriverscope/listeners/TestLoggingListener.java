package com.frunoyman.webdriverscope.listeners;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.testng.IConfigurationListener;
import org.testng.ITestListener;
import org.testng.ITestNGMethod;
import org.testng.ITestResult;

/**
 * Test lifecycle logging, kept out of test code. Registered through
 * META-INF/services/org.testng.ITestNGListener, so no test class or base
 * class refers to it.
 *
 * MDC "test" (Class.method) is set in beforeConfiguration of the first
 * per-method configuration, not in onTestStart: TestNG calls onTestStart
 * only after @BeforeMethod has run (verified), which would leave the
 * setup's logs — opening the entry page, logging in — untagged. The
 * two-argument beforeConfiguration receives the test method a
 * configuration runs for. MDC is thread-local and TestNG runs a test's
 * configurations and body on one thread, so this holds for
 * parallel="methods". It is cleared when the thread moves on to
 * class/suite-level configuration.
 */
public class TestLoggingListener implements IConfigurationListener, ITestListener {

    private static final Logger log = LoggerFactory.getLogger("test");
    private static final String TEST = "test";

    @Override
    public void beforeConfiguration(ITestResult config, ITestNGMethod testMethod) {
        if (testMethod == null) {
            MDC.remove(TEST);
            return;
        }
        String name = testMethod.getRealClass().getSimpleName() + "." + testMethod.getMethodName();
        if (!name.equals(MDC.get(TEST))) {
            MDC.put(TEST, name);
            log.info("▶ START");
        }
    }

    @Override
    public void onConfigurationFailure(ITestResult config, ITestNGMethod testMethod) {
        log.error("✘ {} failed: {}", config.getMethod().getMethodName(),
                String.valueOf(config.getThrowable()), config.getThrowable());
    }

    @Override
    public void onTestSuccess(ITestResult result) {
        log.info("✔ PASS (test body {} ms)", duration(result));
    }

    @Override
    public void onTestFailure(ITestResult result) {
        log.error("✘ FAIL (test body {} ms): {}", duration(result),
                String.valueOf(result.getThrowable()), result.getThrowable());
    }

    @Override
    public void onTestSkipped(ITestResult result) {
        log.warn("⏭ SKIP: {}", String.valueOf(result.getThrowable()));
    }

    private static long duration(ITestResult result) {
        return result.getEndMillis() - result.getStartMillis();
    }
}
