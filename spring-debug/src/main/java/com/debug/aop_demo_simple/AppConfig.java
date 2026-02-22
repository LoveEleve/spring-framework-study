package com.debug.aop_demo_simple;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * @Classname AppConfig
 * @Date 1/30/26
 * @Created by ywj
 */
@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true , exposeProxy = true)  // 强制 JDK 代理
@ComponentScan("com.debug.aop_demo_1")
public class AppConfig {
}
