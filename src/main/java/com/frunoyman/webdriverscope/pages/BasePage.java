package com.frunoyman.webdriverscope.pages;

import jakarta.annotation.PostConstruct;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.PageFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

/**
 * What every page object extends. Pages are plain singleton
 * {@code @Component}s and tests get them with {@code @Autowired}.
 *
 * {@code driver} is the scoped proxy from WebDriverConfig /
 * RemoteWebDriverConfig, not a browser: every call lands on the current
 * thread's live driver. {@code PageFactory.initElements} runs once, at bean
 * creation, and gives each {@code @FindBy} field a lazy locator that searches
 * through that proxy on every use — so one page instance serves every test
 * and every parallel thread. The flip side: a page must keep no state of its
 * own in fields.
 */
public abstract class BasePage {

    @Autowired
    protected WebDriver driver;

    @Value("${base.url}")
    protected String baseUrl;

    /** Navigates straight to this page; subclasses return themselves. */
    public abstract BasePage open();

    @PostConstruct
    private void initElements() {
        PageFactory.initElements(driver, this);
    }
}
