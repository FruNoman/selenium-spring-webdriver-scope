package com.frunoyman.webdriverscope;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

/**
 * A second, separate test class — this is the actual proof. By the time
 * TestNG gets here, BaseWebTest's teardown has already quit() the driver
 * FirstFormTests used, and Spring has reused the same cached
 * ApplicationContext (same @SpringBootTest signature) for this class.
 * Without WebdriverScope's liveness check, autowiring WebDriver here would
 * hand back that same, already-dead session.
 */
public class SecondFormTests extends BaseWebTest {

    @Lazy
    @Autowired
    private WebDriver driver;

    @Test
    public void getsARealLiveDriverFromADifferentTestClass() {
        driver.get("https://www.selenium.dev/selenium/web/web-form.html");
        WebElement textInput = driver.findElement(By.name("my-text"));

        assertEquals(textInput.getAttribute("value"), "",
                "a dead cached driver would have failed before reaching this assertion at all");

        System.out.println("SecondFormTests session: " + driver);
    }
}
