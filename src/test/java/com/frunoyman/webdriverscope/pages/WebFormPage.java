package com.frunoyman.webdriverscope.pages;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

/**
 * Page object for https://www.selenium.dev/selenium/web/web-form.html —
 * wraps the one field this demo cares about.
 */
public class WebFormPage {

    private static final String URL = "https://www.selenium.dev/selenium/web/web-form.html";
    private static final By TEXT_INPUT = By.name("my-text");

    private final WebDriver driver;

    public WebFormPage(WebDriver driver) {
        this.driver = driver;
    }

    public WebFormPage open() {
        driver.get(URL);
        return this;
    }

    public String getTextFieldValue() {
        return textField().getAttribute("value");
    }

    public WebFormPage typeIntoTextField(String text) {
        textField().sendKeys(text);
        return this;
    }

    private WebElement textField() {
        return driver.findElement(TEXT_INPUT);
    }
}
