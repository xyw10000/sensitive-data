package io.github.xyw10000.mybatis.encrypte.annotation;

import java.lang.annotation.*;

/**
 * @author one.xu
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface EncryptField {
    String value() default "";

    /**
     * 参数为map逗号连接需要加密的key
     * @return
     */
    String keys() default "";
}
