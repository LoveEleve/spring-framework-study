package com.debug.aop_demo_1;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * @Classname AppConfig
 * @Date 1/30/26
 * @Created by ywj
 */
@Configuration
@EnableAspectJAutoProxy
@ComponentScan("com.debug.aop_demo_1")
public class AppConfig {
}
