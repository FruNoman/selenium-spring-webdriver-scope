package com.frunoyman.webdriverscope.pages;

import jakarta.annotation.PostConstruct;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.PageFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.openqa.selenium.WebDriver;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * Page object for https://www.selenium.dev/selenium/web/web-form.html.
 *
 * {@code prototype} scope on purpose: this bean is created fresh every
 * time it's requested from the container, so its {@code driver} field
 * always gets wired to whatever WebDriver instance is currently live —
 * a singleton page bean would keep its first-injected driver forever,
 * even after that driver gets recycled by WebDriverScope.
 */
@Component
@Scope("prototype")
public class WebFormPage {

    private static final String URL = "https://www.selenium.dev/selenium/web/web-form.html";

    @Autowired
    protected WebDriver driver;

    @FindBy(name = "my-text")
    public WebElement textInput;

    @PostConstruct
    private void init() {
        PageFactory.initElements(this.driver, this);
    }

    public WebFormPage open() {
        driver.get(URL);
        return this;
    }

    public String getTextFieldValue() {
        return textInput.getAttribute("value");
    }

    public WebFormPage typeIntoTextField(String text) {
        textInput.sendKeys(text);
        return this;
    }
}
