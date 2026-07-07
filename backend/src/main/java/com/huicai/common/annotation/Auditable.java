package com.huicai.common.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Auditable {

    String operation();

    String module();

    boolean trackSnapshot() default false;
}