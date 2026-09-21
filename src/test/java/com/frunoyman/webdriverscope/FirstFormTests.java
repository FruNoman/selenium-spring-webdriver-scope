package com.frunoyman.webdriverscope;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Same @SpringBootTest signature as SecondFormTests, on purpose — that's
 * what makes Spring cache one ApplicationContext across both classes.
 */
public class FirstFormTests extends BaseWebTest {

    @Lazy
    @Autowired
    private WebDriver driver;

    @Test
    public void textFieldStartsEmptyAndAcceptsInput() {
        driver.get("https://www.selenium.dev/selenium/web/web-form.html");
        WebElement textInput = driver.findElement(By.name("my-text"));

        assertEquals(textInput.getAttribute("value"), "", "a fresh session should start with an empty field");

        textInput.sendKeys("first test");
        assertEquals(textInput.getAttribute("value"), "first test");

        System.out.println("FirstFormTests session: " + driver);
    }

    @Test
    public void secondMethodAlsoGetsAFreshSession() {
        driver.get("https://www.selenium.dev/selenium/web/web-form.html");
        WebElement textInput = driver.findElement(By.name("my-text"));

        // If the scope handed back the previous method's already-quit
        // driver instead of a fresh one, this line throws instead of
        // asserting anything — the whole point of this repo.
        assertTrue(textInput.getAttribute("value").isEmpty(),
                "leftover text from the previous test method would show up here otherwise");

        System.out.println("FirstFormTests session: " + driver);
    }
}
