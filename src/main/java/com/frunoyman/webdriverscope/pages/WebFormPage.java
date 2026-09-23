package com.frunoyman.webdriverscope.pages;

import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * Page object for https://www.selenium.dev/selenium/web/web-form.html.
 *
 * {@code prototype} scope on purpose: this bean is created fresh every
 * time it's requested from the container, so its {@code driver} field
 * (inherited from {@link BasePage}) always gets wired to whatever
 * WebDriver instance is currently live — a singleton page bean would
 * keep its first-injected driver forever, even after that driver gets
 * recycled by WebDriverScope.
 */
@Component
@Scope("prototype")
public class WebFormPage extends BasePage {

    @FindBy(name = "my-text")
    public WebElement textInput;

    @FindBy(css = "button[type='submit']")
    public WebElement submitButton;

    // Prototype too, so each WebFormPage gets its own instance wired to
    // the same thread's driver — no @Lazy needed here.
    @Autowired
    private SubmittedFormPage submittedFormPage;

    @Override
    public WebFormPage open() {
        driver.get(baseUrl + "web-form.html");
        return this;
    }

    public String getTextFieldValue() {
        return textInput.getAttribute("value");
    }

    public WebFormPage typeIntoTextField(String text) {
        textInput.sendKeys(text);
        return this;
    }

    public SubmittedFormPage submit() {
        submitButton.click();
        return submittedFormPage;
    }
}
