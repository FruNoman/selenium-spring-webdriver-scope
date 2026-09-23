package com.frunoyman.webdriverscope.elements;

import org.openqa.selenium.WebElement;
import org.openqa.selenium.WrapsElement;
import org.openqa.selenium.interactions.Locatable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Wraps every {@code @FindBy} element so each interaction is logged under
 * the field it came from — {@code [WebFormPage.submitButton] click}.
 * Actions (click, typing, clear, submit) at INFO, reads at DEBUG. Typed
 * text is masked when the field name contains "password".
 *
 * Done here rather than with Selenium's EventFiringDecorator on purpose:
 * that decorator returns a proxy which is not a RemoteWebDriver, which
 * would break the TARGET_CLASS scoped proxy the WebDriver beans rely on.
 */
final class LoggingElement implements InvocationHandler {

    private static final Logger log = LoggerFactory.getLogger("ui");
    private static final Set<String> ACTIONS = Set.of("click", "sendKeys", "clear", "submit");
    private static final Set<String> SILENT = Set.of(
            "toString", "hashCode", "equals", "getWrappedElement", "getCoordinates");

    private final WebElement target;
    private final String name;

    private LoggingElement(WebElement target, String name) {
        this.target = target;
        this.name = name;
    }

    static WebElement wrap(WebElement target, String name) {
        return (WebElement) Proxy.newProxyInstance(LoggingElement.class.getClassLoader(),
                new Class<?>[]{WebElement.class, WrapsElement.class, Locatable.class},
                new LoggingElement(target, name));
    }

    /**
     * Same "look up again on every call" contract as the list it wraps:
     * one lookup per call (snapshot), elements wrapped as they're read.
     */
    @SuppressWarnings("unchecked")
    static List<WebElement> wrapList(List<WebElement> target, String name) {
        InvocationHandler handler = (proxy, method, args) -> {
            List<WebElement> found = new ArrayList<>(target);
            List<WebElement> view = new AbstractList<>() {
                @Override
                public WebElement get(int index) {
                    return wrap(found.get(index), name + "[" + index + "]");
                }

                @Override
                public int size() {
                    return found.size();
                }
            };
            return call(view, method, args);
        };
        return (List<WebElement>) Proxy.newProxyInstance(LoggingElement.class.getClassLoader(),
                new Class<?>[]{List.class}, handler);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String action = method.getName();
        switch (action) {
            case "toString":
                return name;
            case "getWrappedElement":
                return target instanceof WrapsElement wraps ? wraps.getWrappedElement() : target;
            case "getCoordinates":
                return ((Locatable) target).getCoordinates();
            default:
                break;
        }
        if (!SILENT.contains(action)) {
            String line = "[" + name + "] " + action + describe(action, args);
            if (ACTIONS.contains(action)) {
                log.info(line);
            } else {
                log.debug(line);
            }
        }
        return call(target, method, args);
    }

    private String describe(String action, Object[] args) {
        if (args == null || args.length == 0) {
            return "";
        }
        if (action.equals("sendKeys")) {
            if (name.toLowerCase(Locale.ROOT).contains("password")) {
                return " '***'";
            }
            CharSequence[] keys = (CharSequence[]) args[0];
            return " '" + Arrays.stream(keys).map(String::valueOf).collect(Collectors.joining()) + "'";
        }
        return " " + Arrays.stream(args).map(String::valueOf).collect(Collectors.joining(", "));
    }

    private static Object call(Object target, Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
