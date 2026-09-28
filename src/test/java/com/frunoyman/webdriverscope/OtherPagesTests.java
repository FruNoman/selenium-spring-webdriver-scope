package com.frunoyman.webdriverscope;

import com.frunoyman.webdriverscope.pages.FormPage;
import com.frunoyman.webdriverscope.pages.JavascriptPage;
import com.frunoyman.webdriverscope.pages.ResultPage;
import com.frunoyman.webdriverscope.pages.XhtmlTestPage;
import org.springframework.beans.factory.annotation.Autowired;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/** A few other selenium.dev test pages: one short action per test. */
public class OtherPagesTests extends BaseWebTest {

    @Autowired
    private XhtmlTestPage xhtmlTestPage;

    @Autowired
    private FormPage formPage;

    @Autowired
    private JavascriptPage javascriptPage;

    @Test
    public void readHeading() {
        xhtmlTestPage.open();

        assertEquals(xhtmlTestPage.getHeading(), "XHTML Might Be The Future");
    }

    @Test
    public void followLink() {
        ResultPage result = xhtmlTestPage.open().followLink();

        assertEquals(result.getGreeting(), "Success!");
    }

    @Test
    public void typeEmail() {
        formPage.open().typeEmail("qa@example.com");

        assertEquals(formPage.getEmail(), "qa@example.com");
    }

    @Test
    public void checkCheckbox() {
        formPage.open().checkCheckbox();

        assertTrue(formPage.isCheckboxChecked());
    }

    @Test
    public void clickRunsJavascript() {
        javascriptPage.open().clickField();

        assertEquals(javascriptPage.getFieldValue(), "Clicked");
    }
}
