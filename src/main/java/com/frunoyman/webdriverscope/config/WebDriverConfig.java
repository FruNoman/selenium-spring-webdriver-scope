package com.frunoyman.webdriverscope.config;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.remote.RemoteWebDriver;
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
import org.springframework.context.annotation.ScopedProxyMode;

import java.time.Duration;

/**
 * Local, in-process browsers. The {@code local} profile on the class
 * gates the whole config; {@code chrome}/{@code firefox} on each
 * {@code @Bean} picks the browser. Spring only registers a bean when
 * both the class-level and method-level {@code @Profile} match, so
 * {@code chrome,local} gets {@link #chromeDriver()} and
 * {@code firefox,local} gets {@link #firefoxDriver()}.
 * {@link RemoteWebDriverConfig} mirrors this same split for
 * {@code grid} — swap the env profile, nothing else changes.
 *
 * {@code @Scope("webdriverscope")} here for the same reason as in
 * {@link RemoteWebDriverConfig}: this thread-scoped custom scope is
 * what makes {@code parallel="methods"} safe (each TestNG thread gets
 * its own local browser instead of fighting over one). Spring Boot's
 * built-in WebDriverScope alone would be enough for a purely
 * *sequential* local suite, but "local" doesn't imply "sequential" —
 * nothing here should silently break the moment someone adds
 * `parallel=` to testng.xml for a local run, the same way it did for
 * Grid (see the README).
 *
 * {@code @Lazy} so the browser only actually launches the first time a
 * test asks for it, not at context startup.
 *
 * {@code proxyMode = TARGET_CLASS}: whoever autowires {@code WebDriver}
 * (pages, elements, tests) gets one CGLIB proxy, not a browser. Every
 * call on it goes through {@code WebdriverScope.get()}, i.e. lands on the
 * current thread's live driver — so the holders themselves can be plain
 * singletons. Return type is {@code RemoteWebDriver}, not
 * {@code WebDriver}, so the proxy is a {@code RemoteWebDriver} subclass
 * and casts to {@code JavascriptExecutor}/{@code TakesScreenshot} work.
 */
@Lazy
@Configuration
@Profile("local")
public class WebDriverConfig {

    @Value("${implicit.timeout.seconds:10}")
    private int implicitTimeoutSeconds;

    @Bean
    @Scope(value = "webdriverscope", proxyMode = ScopedProxyMode.TARGET_CLASS)
    @Profile("chrome")
    public RemoteWebDriver chromeDriver() {
        WebDriverManager.chromedriver().setup();
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--window-size=1920,1080");
        RemoteWebDriver driver = new ChromeDriver(options);
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(implicitTimeoutSeconds));
        return driver;
    }

    @Bean
    @Scope(value = "webdriverscope", proxyMode = ScopedProxyMode.TARGET_CLASS)
    @Profile("firefox")
    public RemoteWebDriver firefoxDriver() {
        WebDriverManager.firefoxdriver().setup();
        FirefoxOptions options = new FirefoxOptions();
        RemoteWebDriver driver = new FirefoxDriver(options);
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(implicitTimeoutSeconds));
        return driver;
    }
}
