package com.frunoyman.webdriverscope.pages;

import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.springframework.stereotype.Component;

/** https://www.selenium.dev/selenium/web/submitted-form.html — reached via {@link WebFormPage#submit()}. */
@Component
public class SubmittedFormPage extends BasePage {

    @FindBy(id = "message")
    private WebElement message;

    @Override
    public SubmittedFormPage open() {
        driver.get(baseUrl + "submitted-form.html");
        return this;
    }

    public String getMessage() {
        return message.getText();
    }
}
