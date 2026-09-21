package com.frunoyman.webdriverscope.scope;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

/** Registers the scope under the name used by {@code @Scope("webdriverscope")}. */
public class WebdriverScopePostProcessor implements BeanFactoryPostProcessor {
    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        beanFactory.registerScope("webdriverscope", new WebdriverScope());
    }
}
