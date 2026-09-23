package com.frunoyman.webdriverscope.pages;

import com.frunoyman.webdriverscope.elements.ElementFieldDecorator;
import jakarta.annotation.PostConstruct;
import org.openqa.selenium.WebDriver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;

/**
 * Common base for every page object in this repo. Pages are plain
 * singleton {@code @Component}s: {@code driver} is the scoped proxy from
 * WebDriverConfig/RemoteWebDriverConfig, which resolves the current
 * thread's live browser on every call, so one page instance serves every
 * test and every thread. The flip side: a page is shared across parallel
 * threads, so it must not keep any state of its own in fields.
 */
public abstract class BasePage {

    @Autowired
    protected WebDriver driver;

    @Autowired
    private AutowireCapableBeanFactory beanFactory;

    @Value("${base.url}")
    protected String baseUrl;

    /**
     * Navigates straight to this page. Subclasses narrow the return type
     * to themselves so calls can keep chaining.
     */
    public abstract BasePage open();

    @PostConstruct
    private void init() {
        ElementFieldDecorator.initElements(driver, this, beanFactory);
    }
}
