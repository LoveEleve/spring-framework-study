package com.debug.simpleDebug;

import com.debug.demo.ComprehensiveImportDemo;
import org.springframework.context.annotation.Import;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @Classname EnableAutoConfiguration
 * @Date 1/17/26
 * @Created by ywj
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import(A.class)
public @interface EnableAutoConfiguration {
}
