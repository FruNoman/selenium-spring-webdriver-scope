package com.frunoyman.webdriverscope.scope;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WebdriverScopeConfig {
    @Bean
    public static BeanFactoryPostProcessor webdriverScopePostProcessor() {
        return new WebdriverScopePostProcessor();
    }
}
