package com.frunoyman.webdriverscope.pages;

import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.springframework.stereotype.Component;

/** https://www.selenium.dev/selenium/web/formPage.html */
@Component
public class FormPage extends BasePage {

    @FindBy(id = "email")
    private WebElement email;

    @FindBy(id = "checky")
    private WebElement checkbox;

    @Override
    public FormPage open() {
        driver.get(baseUrl + "formPage.html");
        return this;
    }

    public FormPage typeEmail(String value) {
        email.sendKeys(value);
        return this;
    }

    public String getEmail() {
        return email.getAttribute("value");
    }

    public FormPage checkCheckbox() {
        checkbox.click();
        return this;
    }

    public boolean isCheckboxChecked() {
        return checkbox.isSelected();
    }
}
