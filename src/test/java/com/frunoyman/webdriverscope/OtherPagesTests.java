package com.frunoyman.webdriverscope;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/** A few other selenium.dev test pages: one short action per test. */
public class OtherPagesTests extends BaseWebTest {

    @Test
    public void readHeading() {
        driver.get(baseUrl + "xhtmlTest.html");

        assertEquals(driver.findElement(By.tagName("h1")).getText(), "XHTML Might Be The Future");
    }

    @Test
    public void followLink() {
        driver.get(baseUrl + "xhtmlTest.html");

        driver.findElement(By.id("linkId")).click();

        assertTrue(driver.getCurrentUrl().endsWith("resultPage.html"), driver.getCurrentUrl());
    }

    @Test
    public void typeEmail() {
        driver.get(baseUrl + "formPage.html");

        WebElement email = driver.findElement(By.id("email"));
        email.sendKeys("qa@example.com");

        assertEquals(email.getAttribute("value"), "qa@example.com");
    }

    @Test
    public void checkCheckbox() {
        driver.get(baseUrl + "formPage.html");

        WebElement checkbox = driver.findElement(By.id("checky"));
        checkbox.click();

        assertTrue(checkbox.isSelected());
    }

    @Test
    public void clickRunsJavascript() {
        driver.get(baseUrl + "javascriptPage.html");

        WebElement field = driver.findElement(By.id("clickField"));
        field.click();

        assertEquals(field.getAttribute("value"), "Clicked");
    }
}
