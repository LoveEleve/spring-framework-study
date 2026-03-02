package com.debug.mvc_demo;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;

/**
 * Root WebApplicationContext 配置类（父容器）
 *
 * 在标准的 Spring MVC 架构中，存在父子容器的概念：
 *
 * ┌──────────────────────────────────────────────┐
 * │         Root WebApplicationContext           │ ← 父容器（本类配置）
 * │   管理 Service、DAO、DataSource 等非Web层Bean   │
 * │   由 ContextLoaderListener 创建               │
 * ├──────────────────────────────────────────────┤
 * │       Servlet WebApplicationContext          │ ← 子容器（WebMvcConfig 配置）
 * │   管理 Controller、ViewResolver、Interceptor   │
 * │   管理 ControllerAdvice（全局异常处理）          │
 * │   由 DispatcherServlet 创建                   │
 * └──────────────────────────────────────────────┘
 *
 * 子容器可以访问父容器中的 Bean，但父容器不能访问子容器中的 Bean。
 * 这就是为什么 Controller 可以注入 Service，但 Service 不能注入 Controller。
 *
 * 调试建议：
 * 1. 在 ContextLoader#initWebApplicationContext() 打断点 → 看父容器如何创建
 * 2. 在 FrameworkServlet#initWebApplicationContext() 打断点 → 看子容器如何创建并关联父容器
 * 3. 在 AbstractBeanFactory#getBean() 打断点 → 看子容器如何从父容器查找 Bean
 */
@Configuration
@ComponentScan(
		basePackages = "com.debug.mvc_demo",
		// 排除 @Controller 和 @ControllerAdvice，它们由子容器（WebMvcConfig）管理
		excludeFilters = @ComponentScan.Filter(
				type = FilterType.ANNOTATION,
				classes = {Controller.class, ControllerAdvice.class}
		)
)
public class RootConfig {

	// 这里可以配置 Service、DataSource、事务管理器等
	// 例如：
	// @Bean
	// public DataSource dataSource() { ... }
	//
	// @Bean
	// public PlatformTransactionManager transactionManager() { ... }
}
