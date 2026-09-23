package com.frunoyman.webdriverscope.pages;

import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * Page object for https://www.selenium.dev/selenium/web/submitted-form.html —
 * where {@link WebFormPage#submit()} lands. Same {@code prototype} scope
 * as every other page, for the reason explained on {@link WebFormPage}.
 */
@Component
@Scope("prototype")
public class SubmittedFormPage extends BasePage {

    @FindBy(id = "message")
    public WebElement message;

    @Override
    public SubmittedFormPage open() {
        driver.get(baseUrl + "submitted-form.html");
        return this;
    }

    public String getMessage() {
        return message.getText();
    }
}
