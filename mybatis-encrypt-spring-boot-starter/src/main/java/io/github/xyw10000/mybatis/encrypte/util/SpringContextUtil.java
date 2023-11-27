package io.github.xyw10000.mybatis.encrypte.util;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * @author one.xu
 */
@Component
public class SpringContextUtil implements ApplicationContextAware {
    private static ApplicationContext applicationContext;

    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        SpringContextUtil.applicationContext = applicationContext;
    }

    public static ApplicationContext getApplicationContext() {
        return applicationContext;
    }


    public static <T> T getBean(String name) throws BeansException {
        return (T) applicationContext.getBean(name);
    }


    public static <T> T getBean(Class<?> clz) throws BeansException {
        return (T) applicationContext.getBean(clz);
    }
}
