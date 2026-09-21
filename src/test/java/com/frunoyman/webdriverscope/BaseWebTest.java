package com.frunoyman.webdriverscope;

import org.openqa.selenium.WebDriver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.annotations.AfterMethod;

/**
 * Every test class in this repo extends this one. Same
 * {@code @SpringBootTest(classes = ...)} signature on every class is what
 * makes Spring reuse (cache) a single ApplicationContext across all of
 * them — the exact condition that turns a plain singleton/thread-scoped
 * WebDriver bean into a dead-session trap after the first quit().
 */
@SpringBootTest(classes = DemoApplication.class)
public class BaseWebTest extends AbstractTestNGSpringContextTests {

    @Lazy
    @Autowired
    protected ApplicationContext applicationContext;

    @AfterMethod(alwaysRun = true)
    public void quitDriver() {
        applicationContext.getBean(WebDriver.class).quit();
    }
}
