package com.frunoyman.webdriverscope;

import com.frunoyman.webdriverscope.pages.WebFormPage;
import org.openqa.selenium.JavascriptExecutor;
import org.springframework.beans.factory.annotation.Autowired;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;

/**
 * A second, separate test class — this is the actual proof. By the time
 * TestNG gets here, BaseWebTest's teardown has already quit() the driver
 * FirstFormTests used, and Spring has reused the same cached
 * ApplicationContext (same @SpringBootTest signature) for this class.
 * Without the built-in WebDriverScope, autowiring WebDriver here would
 * hand back that same, already-dead session.
 */
public class SecondFormTests extends BaseWebTest {

    @Autowired
    private WebFormPage webFormPage;

    @Test
    public void getsARealLiveDriverFromADifferentTestClass() {
        assertEquals(webFormPage.getTextFieldValue(), "",
                "a dead cached driver would have failed before reaching this assertion at all");

        System.out.println("SecondFormTests session: " + driver);
    }

    @Test
    public void pagesAreSingletonsAndTheDriverProxyIsARealRemoteWebDriver() {
        assertSame(applicationContext.getBean(WebFormPage.class), applicationContext.getBean(WebFormPage.class),
                "pages no longer need to be prototype — the driver proxy does the per-thread work");
        assertEquals(((JavascriptExecutor) driver).executeScript("return document.title"), "Web form",
                "the proxy must stay castable to the interfaces RemoteWebDriver implements");
    }
}
