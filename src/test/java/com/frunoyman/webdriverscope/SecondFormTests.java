package com.frunoyman.webdriverscope;

import com.frunoyman.webdriverscope.pages.WebFormPage;
import org.openqa.selenium.WebDriver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

/**
 * A second, separate test class — this is the actual proof. By the time
 * TestNG gets here, BaseWebTest's teardown has already quit() the driver
 * FirstFormTests used, and Spring has reused the same cached
 * ApplicationContext (same @SpringBootTest signature) for this class.
 * Without the built-in WebDriverScope, autowiring WebDriver here would
 * hand back that same, already-dead session.
 */
public class SecondFormTests extends BaseWebTest {

    @Lazy
    @Autowired
    private WebDriver driver;

    @Test
    public void getsARealLiveDriverFromADifferentTestClass() {
        WebFormPage form = new WebFormPage(driver).open();

        assertEquals(form.getTextFieldValue(), "",
                "a dead cached driver would have failed before reaching this assertion at all");

        System.out.println("SecondFormTests session: " + driver);
    }
}
