package com.frunoyman.webdriverscope.pages;

import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** https://www.selenium.dev/selenium/web/xhtmlTest.html */
@Component
public class XhtmlTestPage extends BasePage {

    @FindBy(tagName = "h1")
    private WebElement heading;

    @FindBy(id = "linkId")
    private WebElement resultLink;

    @Autowired
    private ResultPage resultPage;

    @Override
    public XhtmlTestPage open() {
        driver.get(baseUrl + "xhtmlTest.html");
        return this;
    }

    public String getHeading() {
        return heading.getText();
    }

    public ResultPage followLink() {
        resultLink.click();
        return resultPage;
    }
}
