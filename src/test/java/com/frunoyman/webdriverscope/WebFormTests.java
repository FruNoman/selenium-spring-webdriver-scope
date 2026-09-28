package com.frunoyman.webdriverscope;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.Select;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/** selenium.dev's web form: one short action per test. */
public class WebFormTests extends BaseWebTest {

    @Test
    public void pageHasTitle() {
        driver.get(baseUrl + "web-form.html");

        assertEquals(driver.getTitle(), "Web form");
    }

    @Test
    public void typeIntoTextInput() {
        driver.get(baseUrl + "web-form.html");

        WebElement input = driver.findElement(By.name("my-text"));
        input.sendKeys("hello");

        assertEquals(input.getAttribute("value"), "hello");
    }

    @Test
    public void selectFromDropdown() {
        driver.get(baseUrl + "web-form.html");

        Select select = new Select(driver.findElement(By.name("my-select")));
        select.selectByVisibleText("Two");

        assertEquals(select.getFirstSelectedOption().getAttribute("value"), "2");
    }

    @Test
    public void toggleCheckbox() {
        driver.get(baseUrl + "web-form.html");

        WebElement checkbox = driver.findElement(By.id("my-check-2"));
        assertFalse(checkbox.isSelected());
        checkbox.click();

        assertTrue(checkbox.isSelected());
    }

    @Test
    public void submitForm() {
        driver.get(baseUrl + "web-form.html");

        driver.findElement(By.cssSelector("button[type='submit']")).click();

        assertEquals(driver.findElement(By.id("message")).getText(), "Received!");
    }
}
