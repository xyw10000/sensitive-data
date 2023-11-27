package io.github.xyw10000.mybatis.encrypte.annotation;

import java.lang.annotation.*;

/**
 * @author one.xu
 */
@Target({ElementType.TYPE,ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface EnableEncrypt {
    /**
     * 参数为map逗号连接需要加密的key
     * @return
     */
    String keys() default "";
}
