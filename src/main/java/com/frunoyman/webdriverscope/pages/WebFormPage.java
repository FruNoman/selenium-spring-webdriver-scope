package com.frunoyman.webdriverscope.pages;

import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.ui.Select;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** https://www.selenium.dev/selenium/web/web-form.html */
@Component
public class WebFormPage extends BasePage {

    @FindBy(name = "my-text")
    private WebElement textInput;

    @FindBy(name = "my-select")
    private WebElement dropdown;

    @FindBy(id = "my-check-2")
    private WebElement uncheckedCheckbox;

    @FindBy(css = "button[type='submit']")
    private WebElement submitButton;

    // the page a transition leads to is injected like any other bean
    @Autowired
    private SubmittedFormPage submittedFormPage;

    @Override
    public WebFormPage open() {
        driver.get(baseUrl + "web-form.html");
        return this;
    }

    public String getTitle() {
        return driver.getTitle();
    }

    public WebFormPage typeText(String text) {
        textInput.sendKeys(text);
        return this;
    }

    public String getText() {
        return textInput.getAttribute("value");
    }

    public WebFormPage selectOption(String visibleText) {
        new Select(dropdown).selectByVisibleText(visibleText);
        return this;
    }

    public String getSelectedValue() {
        return new Select(dropdown).getFirstSelectedOption().getAttribute("value");
    }

    public WebFormPage toggleCheckbox() {
        uncheckedCheckbox.click();
        return this;
    }

    public boolean isCheckboxChecked() {
        return uncheckedCheckbox.isSelected();
    }

    public SubmittedFormPage submit() {
        submitButton.click();
        return submittedFormPage;
    }
}
