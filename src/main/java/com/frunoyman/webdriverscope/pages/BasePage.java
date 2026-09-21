package com.frunoyman.webdriverscope.pages;

import jakarta.annotation.PostConstruct;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.PageFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Common base for every page object in this repo. Every subclass must
 * also be {@code @Component @Scope("prototype")} — see {@link WebFormPage}
 * for why: a singleton page bean would keep the {@code driver} it got
 * wired with at first creation forever, even after WebDriverScope
 * recycles that driver for a later test.
 */
public abstract class BasePage {

    @Autowired
    protected WebDriver driver;

    @PostConstruct
    private void init() {
        PageFactory.initElements(this.driver, this);
    }
}
