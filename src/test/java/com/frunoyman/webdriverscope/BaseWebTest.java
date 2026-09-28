package com.frunoyman.webdriverscope;

import org.openqa.selenium.WebDriver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.annotations.AfterMethod;

/**
 * The whole TestNG ↔ Spring ↔ WebDriver link in one place.
 *
 * {@code AbstractTestNGSpringContextTests} lets TestNG tests take
 * {@code @Autowired} fields; the context is built once and cached for every
 * test class. {@code driver} is the scoped proxy from WebDriverConfig /
 * RemoteWebDriverConfig — injected once, but every call lands on the
 * current thread's live browser. After {@code quit()} the next call on the
 * same thread gets a brand new session (WebdriverScope), so one field
 * serves every test, sequential or parallel, local or Grid.
 */
@SpringBootTest(classes = DemoApplication.class)
public class BaseWebTest extends AbstractTestNGSpringContextTests {

    @Autowired
    protected WebDriver driver;

    @Value("${base.url}")
    protected String baseUrl;

    // Logging (MDC, START/PASS/FAIL) lives in listeners.TestLoggingListener.
    @AfterMethod(alwaysRun = true)
    public void tearDownTest() {
        driver.quit();
    }
}
