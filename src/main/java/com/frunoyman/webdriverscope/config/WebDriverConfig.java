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

import java.time.Duration;

/**
 * No {@code @Scope} on the beans below on purpose. Spring Boot's own test
 * support (spring-boot-test-autoconfigure's WebDriverContextCustomizerFactory)
 * auto-detects any WebDriver-typed bean in a @SpringBootTest and wraps it in
 * its own scope that discards a quit()'d session and hands back a fresh
 * one — the same idea this repo used to implement by hand. Nothing here
 * has to know about that; it's transparent to this config class.
 *
 * {@code @Lazy} so the browser only actually launches the first time a
 * test asks for it, not at context startup.
 */
@Lazy
@Configuration
public class WebDriverConfig {

    @Value("${implicit.timeout.seconds:10}")
    private int implicitTimeoutSeconds;

    @Bean
    @Profile("chrome")
    public WebDriver chromeDriver() {
        WebDriverManager.chromedriver().setup();
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--window-size=1920,1080");
        WebDriver driver = new ChromeDriver(options);
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(implicitTimeoutSeconds));
        return driver;
    }

    @Bean
    @Profile("firefox")
    public WebDriver firefoxDriver() {
        WebDriverManager.firefoxdriver().setup();
        FirefoxOptions options = new FirefoxOptions();
        WebDriver driver = new FirefoxDriver(options);
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(implicitTimeoutSeconds));
        return driver;
    }
}
