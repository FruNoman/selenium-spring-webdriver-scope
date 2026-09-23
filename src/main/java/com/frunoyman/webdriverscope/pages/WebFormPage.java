package com.frunoyman.webdriverscope.pages;

import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Page object for https://www.selenium.dev/selenium/web/web-form.html.
 */
@Component
public class WebFormPage extends BasePage {

    @FindBy(name = "my-text")
    public WebElement textInput;

    @FindBy(css = "button[type='submit']")
    public WebElement submitButton;

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
