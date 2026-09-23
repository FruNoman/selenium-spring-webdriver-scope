package com.frunoyman.webdriverscope.elements;

import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.Point;
import org.openqa.selenium.Rectangle;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.WrapsDriver;
import org.openqa.selenium.WrapsElement;
import org.openqa.selenium.interactions.Coordinates;
import org.openqa.selenium.interactions.Locatable;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

/**
 * Base for custom elements declared with a plain {@code @FindBy}, e.g.
 * {@code @FindBy(id = "table1") Table table} — see {@link ElementFieldDecorator}.
 *
 * It is a {@link WebElement} itself, so everything Selenium accepts a
 * WebElement for (waits, Actions, JS arguments) takes a custom element
 * too. Pure delegation to {@link #root} on purpose: behaviour specific to
 * one kind of element belongs in that element's class, not here.
 *
 * Subclasses need a public {@code (WebElement root)} constructor. They are
 * not Spring beans (no {@code @Component}), but {@code @Autowired} fields
 * still work: the decorator runs {@code autowireBean} on every instance.
 */
public abstract class BaseElement implements WebElement, WrapsElement, WrapsDriver, Locatable {

    protected final WebElement root;

    @Autowired
    protected WebDriver driver;

    protected BaseElement(WebElement root) {
        this.root = root;
    }

    @Override
    public WebElement getWrappedElement() {
        return root;
    }

    @Override
    public WebDriver getWrappedDriver() {
        return driver;
    }

    @Override
    public Coordinates getCoordinates() {
        return ((Locatable) root).getCoordinates();
    }

    @Override
    public void click() {
        root.click();
    }

    @Override
    public void submit() {
        root.submit();
    }

    @Override
    public void sendKeys(CharSequence... keysToSend) {
        root.sendKeys(keysToSend);
    }

    @Override
    public void clear() {
        root.clear();
    }

    @Override
    public String getTagName() {
        return root.getTagName();
    }

    @Override
    public String getDomProperty(String name) {
        return root.getDomProperty(name);
    }

    @Override
    public String getDomAttribute(String name) {
        return root.getDomAttribute(name);
    }

    @Override
    public String getAttribute(String name) {
        return root.getAttribute(name);
    }

    @Override
    public String getAriaRole() {
        return root.getAriaRole();
    }

    @Override
    public String getAccessibleName() {
        return root.getAccessibleName();
    }

    @Override
    public boolean isSelected() {
        return root.isSelected();
    }

    @Override
    public boolean isEnabled() {
        return root.isEnabled();
    }

    @Override
    public String getText() {
        return root.getText();
    }

    @Override
    public List<WebElement> findElements(By by) {
        return root.findElements(by);
    }

    @Override
    public WebElement findElement(By by) {
        return root.findElement(by);
    }

    @Override
    public SearchContext getShadowRoot() {
        return root.getShadowRoot();
    }

    @Override
    public boolean isDisplayed() {
        return root.isDisplayed();
    }

    @Override
    public Point getLocation() {
        return root.getLocation();
    }

    @Override
    public Dimension getSize() {
        return root.getSize();
    }

    @Override
    public Rectangle getRect() {
        return root.getRect();
    }

    @Override
    public String getCssValue(String propertyName) {
        return root.getCssValue(propertyName);
    }

    @Override
    public <X> X getScreenshotAs(OutputType<X> target) {
        return root.getScreenshotAs(target);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(" + root + ")";
    }
}
