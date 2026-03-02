package com.debug.boot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot 源码调试启动类
 * <p>
 * 调试要点：
 * 1. SpringApplication.run() - Spring Boot 启动入口
 * 2. 自动配置机制 - @EnableAutoConfiguration
 * 3. 嵌入式 Tomcat 启动流程
 * 4. DispatcherServlet 自动注册
 * <p>
 * Spring Framework 核心模块（spring-context、spring-web、spring-webmvc 等）
 * 直接引用本地源码，断点可直接跳入 .java 源码
 * <p>
 * Spring Boot 模块（spring-boot、spring-boot-autoconfigure）
 * 从本地编译安装的 Maven 仓库引入，附带 sources jar，也可打断点调试
 */
@SpringBootApplication
public class BootDebugApplication {

    public static void main(String[] args) {
        // 在这里打断点，开始调试 Spring Boot 启动流程
        SpringApplication.run(BootDebugApplication.class, args);
    }
}
