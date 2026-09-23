package com.frunoyman.webdriverscope.elements;

import org.openqa.selenium.SearchContext;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindAll;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.FindBys;
import org.openqa.selenium.support.PageFactory;
import org.openqa.selenium.support.pagefactory.DefaultElementLocatorFactory;
import org.openqa.selenium.support.pagefactory.DefaultFieldDecorator;
import org.openqa.selenium.support.pagefactory.ElementLocator;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.AbstractList;
import java.util.List;

/**
 * Lets the standard {@code @FindBy}/{@code @FindBys}/{@code @FindAll}
 * annotations populate {@link BaseElement} subclasses and
 * {@code List<SomeElement>} exactly like they populate {@code WebElement}
 * and {@code List<WebElement>}, which still go through Selenium's own
 * {@link DefaultFieldDecorator} unchanged.
 *
 * Only annotated fields are touched. Plain PageFactory would also
 * decorate an un-annotated {@code WebElement} field with an id-or-name
 * locator built from its name — including {@link BaseElement#root}.
 */
public class ElementFieldDecorator extends DefaultFieldDecorator {

    private final AutowireCapableBeanFactory beanFactory;
    // log name of the element whose fields are being decorated; null for a page
    private final String parentName;

    public ElementFieldDecorator(SearchContext searchContext, AutowireCapableBeanFactory beanFactory) {
        this(searchContext, beanFactory, null);
    }

    private ElementFieldDecorator(SearchContext searchContext, AutowireCapableBeanFactory beanFactory,
                                  String parentName) {
        super(new DefaultElementLocatorFactory(searchContext));
        this.beanFactory = beanFactory;
        this.parentName = parentName;
    }

    /** {@code PageFactory.initElements}, with custom elements searched for inside {@code searchContext}. */
    public static void initElements(SearchContext searchContext, Object target, AutowireCapableBeanFactory beanFactory) {
        PageFactory.initElements(new ElementFieldDecorator(searchContext, beanFactory), target);
    }

    @Override
    public Object decorate(ClassLoader loader, Field field) {
        if (!isAnnotated(field)) {
            return null;
        }
        // TablesPage.table, then TablesPage.table.rows[1].cells[0] for nested ones
        String name = (parentName != null ? parentName : field.getDeclaringClass().getSimpleName())
                + "." + field.getName();
        if (BaseElement.class.isAssignableFrom(field.getType())) {
            WebElement root = LoggingElement.wrap(proxyForLocator(loader, factory.createLocator(field)), name);
            return create(field.getType().asSubclass(BaseElement.class), root, name);
        }
        Class<? extends BaseElement> listElementType = listElementType(field);
        if (listElementType != null) {
            return proxyForElementList(loader, factory.createLocator(field), listElementType, name);
        }
        Object decorated = super.decorate(loader, field);
        if (decorated instanceof WebElement element) {
            return LoggingElement.wrap(element, name);
        }
        if (decorated instanceof List<?> elements) {
            @SuppressWarnings("unchecked")
            List<WebElement> webElements = (List<WebElement>) elements;
            return LoggingElement.wrapList(webElements, name);
        }
        return decorated;
    }

    private <T extends BaseElement> T create(Class<T> type, WebElement root, String name) {
        T element;
        try {
            element = type.getConstructor(WebElement.class).newInstance(root);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(type.getName() + " needs a public (WebElement root) constructor", e);
        }
        beanFactory.autowireBean(element);
        // nested @FindBy fields are searched for inside this element, not the whole page
        PageFactory.initElements(new ElementFieldDecorator(element, beanFactory, name), element);
        return element;
    }

    /**
     * Same contract as Selenium's own {@code List<WebElement>} proxy: the
     * elements are looked up again on every call, so the list never goes
     * stale. Each found element is wrapped only when it's actually read,
     * so {@code size()} creates nothing and {@code get(i)} creates one.
     */
    private List<?> proxyForElementList(ClassLoader loader, ElementLocator locator, Class<? extends BaseElement> type,
                                        String name) {
        InvocationHandler handler = (proxy, method, args) -> {
            List<WebElement> found = locator.findElements();
            List<BaseElement> elements = new AbstractList<>() {
                @Override
                public BaseElement get(int index) {
                    String itemName = name + "[" + index + "]";
                    return create(type, LoggingElement.wrap(found.get(index), itemName), itemName);
                }

                @Override
                public int size() {
                    return found.size();
                }
            };
            try {
                return method.invoke(elements, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        };
        return (List<?>) Proxy.newProxyInstance(loader, new Class<?>[]{List.class}, handler);
    }

    private static boolean isAnnotated(Field field) {
        return field.isAnnotationPresent(FindBy.class)
                || field.isAnnotationPresent(FindBys.class)
                || field.isAnnotationPresent(FindAll.class);
    }

    private static Class<? extends BaseElement> listElementType(Field field) {
        if (field.getType() != List.class || !(field.getGenericType() instanceof ParameterizedType listType)) {
            return null;
        }
        Type argument = listType.getActualTypeArguments()[0];
        if (argument instanceof Class<?> type && BaseElement.class.isAssignableFrom(type)) {
            return type.asSubclass(BaseElement.class);
        }
        return null;
    }
}
