package com.frunoyman.webdriverscope.pages;

import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.springframework.stereotype.Component;

/** https://www.selenium.dev/selenium/web/resultPage.html — reached via {@link XhtmlTestPage#followLink()}. */
@Component
public class ResultPage extends BasePage {

    @FindBy(id = "greeting")
    private WebElement greeting;

    @Override
    public ResultPage open() {
        driver.get(baseUrl + "resultPage.html");
        return this;
    }

    public String getGreeting() {
        return greeting.getText();
    }
}
