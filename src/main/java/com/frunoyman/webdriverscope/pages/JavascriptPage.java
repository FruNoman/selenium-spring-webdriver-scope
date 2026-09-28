package com.frunoyman.webdriverscope.pages;

import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.springframework.stereotype.Component;

/** https://www.selenium.dev/selenium/web/javascriptPage.html */
@Component
public class JavascriptPage extends BasePage {

    // onclick sets its own value to "Clicked"
    @FindBy(id = "clickField")
    private WebElement clickField;

    @Override
    public JavascriptPage open() {
        driver.get(baseUrl + "javascriptPage.html");
        return this;
    }

    public JavascriptPage clickField() {
        clickField.click();
        return this;
    }

    public String getFieldValue() {
        return clickField.getAttribute("value");
    }
}
