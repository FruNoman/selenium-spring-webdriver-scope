package com.frunoyman.webdriverscope.config;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Scope;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.time.Duration;

/**
 * Same two browsers as {@link WebDriverConfig}, run against a Selenium
 * Grid hub instead of launching a local binary. The {@code grid} profile
 * on the class gates the whole config, same split as {@link WebDriverConfig}
 * does for {@code local} — {@code chrome}/{@code firefox} on each
 * {@code @Bean} still picks the browser. Only one of the two configs is
 * ever active, so both are free to name their beans {@code chromeDriver}/
 * {@code firefoxDriver} without colliding.
 *
 * Spring Boot's own built-in WebDriverScope shares ONE instance per bean
 * name across every thread — fine for sequential recycling across
 * cached contexts, wrong for TestNG {@code parallel="methods"}, where
 * multiple threads would fight over the same browser. WebdriverScope
 * extends SimpleThreadScope, so each thread gets its own driver.
 */
@Lazy
@Configuration
@Profile("grid")
public class RemoteWebDriverConfig {

    @Value("${grid.url:http://localhost:4444/wd/hub}")
    private String gridUrl;

    @Value("${implicit.timeout.seconds:10}")
    private int implicitTimeoutSeconds;

    @Bean
    @Scope("webdriverscope")
    @Profile("chrome")
    public WebDriver chromeDriver() throws MalformedURLException {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--window-size=1920,1080");
        WebDriver driver = new RemoteWebDriver(gridUrl(), options);
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(implicitTimeoutSeconds));
        return driver;
    }

    @Bean
    @Scope("webdriverscope")
    @Profile("firefox")
    public WebDriver firefoxDriver() throws MalformedURLException {
        FirefoxOptions options = new FirefoxOptions();
        WebDriver driver = new RemoteWebDriver(gridUrl(), options);
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(implicitTimeoutSeconds));
        return driver;
    }

    private URL gridUrl() throws MalformedURLException {
        return URI.create(gridUrl).toURL();
    }
}
