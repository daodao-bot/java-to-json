package com.github.daodao.tojson.annotation;

import java.lang.annotation.*;

/**
 * 使用 javax.annotation.processing.AbstractProcessor 在编译期补充一个 toJson 方法
 */
@Documented
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.SOURCE)
public @interface ToJson {

}
