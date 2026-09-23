package com.frunoyman.webdriverscope;

import com.frunoyman.webdriverscope.pages.BasePage;
import com.frunoyman.webdriverscope.pages.WebFormPage;
import org.openqa.selenium.WebDriver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

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

    @Autowired
    protected ApplicationContext applicationContext;

    // scoped proxy — always the current thread's live driver
    @Autowired
    protected WebDriver driver;

    /** The page every test starts on. Override for tests of another site/page. */
    protected Class<? extends BasePage> entryPage() {
        return WebFormPage.class;
    }

    @BeforeMethod(alwaysRun = true)
    public void openEntryPage() {
        applicationContext.getBean(entryPage()).open();
    }

    @AfterMethod(alwaysRun = true)
    public void quitDriver() {
        driver.quit();
    }
}
