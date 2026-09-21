package com.frunoyman.webdriverscope.config;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Scope;

import java.time.Duration;

/**
 * {@code @Lazy} so the browser only actually launches the first time a
 * test asks for it, not at context startup.
 */
@Lazy
@Configuration
public class WebDriverConfig {

    @Value("${implicit.timeout.seconds:10}")
    private int implicitTimeoutSeconds;

    @Bean
    @Scope("webdriverscope")
    @Profile("chrome")
    public WebDriver chromeDriver() {
        WebDriverManager.chromedriver().setup();
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new", "--window-size=1920,1080");
        WebDriver driver = new ChromeDriver(options);
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(implicitTimeoutSeconds));
        return driver;
    }

    @Bean
    @Scope("webdriverscope")
    @Profile("firefox")
    public WebDriver firefoxDriver() {
        WebDriverManager.firefoxdriver().setup();
        FirefoxOptions options = new FirefoxOptions();
        options.addArguments("--headless");
        WebDriver driver = new FirefoxDriver(options);
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(implicitTimeoutSeconds));
        return driver;
    }
}
