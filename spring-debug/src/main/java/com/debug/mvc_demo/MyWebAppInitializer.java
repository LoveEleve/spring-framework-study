package com.debug.mvc_demo;

import javax.servlet.Filter;

import com.debug.mvc_demo.demo.AuthFilter;
import com.debug.mvc_demo.demo.LoggingFilter;
import org.springframework.web.filter.CharacterEncodingFilter;
import org.springframework.web.servlet.support.AbstractAnnotationConfigDispatcherServletInitializer;

/**
 * 标准的 Spring MVC 初始化器（基于 Servlet 3.0 SCI 机制）
 *
 * 继承体系（这是你学习源码的重点）：
 *
 *   WebApplicationInitializer                          ← 顶层接口
 *     └─ AbstractContextLoaderInitializer              ← 负责创建 Root 容器 + ContextLoaderListener
 *         └─ AbstractDispatcherServletInitializer      ← 负责创建 DispatcherServlet + Filter 注册
 *             └─ AbstractAnnotationConfigDispatcherServletInitializer  ← 注解配置版本（本类继承）
 *
 * ====== 自动发现机制 ======
 *
 * Servlet 3.0 规范定义了 ServletContainerInitializer（SCI）机制：
 * 1. Tomcat 启动时，通过 SPI 机制（META-INF/services）找到 SpringServletContainerInitializer
 * 2. SpringServletContainerInitializer 上标注了 @HandlesTypes(WebApplicationInitializer.class)
 * 3. Tomcat 扫描 classpath，找到所有 WebApplicationInitializer 的实现类（包括本类）
 * 4. 调用 SpringServletContainerInitializer#onStartup()，它会实例化并调用本类的 onStartup()
 *
 * 这就是为什么你不需要 web.xml，Spring MVC 也能自动注册 DispatcherServlet 的原因！
 *
 * ====== 核心调试路径 ======
 *
 * 1. SpringServletContainerInitializer#onStartup()            → SCI 入口
 * 2. AbstractContextLoaderInitializer#onStartup()              → 注册 ContextLoaderListener
 *    → registerContextLoaderListener() → 创建 Root WebApplicationContext
 * 3. AbstractDispatcherServletInitializer#registerDispatcherServlet() → 注册 DispatcherServlet
 *    → createServletApplicationContext() → 创建 Servlet WebApplicationContext
 *    → createDispatcherServlet() → 创建 DispatcherServlet 实例
 *    → getServletFilters() → 注册 Filter
 *
 * ====== 与 Spring Boot 的关系 ======
 *
 * Spring Boot 并没有使用 SCI 机制，而是通过自己的自动配置来完成类似的事情：
 * - DispatcherServletAutoConfiguration → 自动注册 DispatcherServlet
 * - ServletWebServerApplicationContext → 自动创建嵌入式 Tomcat
 * 但理解了这里的 SCI 机制，你就能明白 Spring Boot "省掉了什么"。
 */
public class MyWebAppInitializer extends AbstractAnnotationConfigDispatcherServletInitializer {

	/**
	 * 指定 Root WebApplicationContext 的配置类（父容器）
	 * 管理 Service、DAO 等非 Web 层 Bean
	 *
	 * 调试：在 AbstractContextLoaderInitializer#registerContextLoaderListener() 打断点
	 */
	@Override
	protected Class<?>[] getRootConfigClasses() {
		return new Class<?>[]{RootConfig.class};
	}

	/**
	 * 指定 Servlet WebApplicationContext 的配置类（子容器）
	 * 管理 Controller、ViewResolver、Interceptor 等 Web 层 Bean
	 *
	 * 调试：在 AbstractAnnotationConfigDispatcherServletInitializer#createServletApplicationContext() 打断点
	 */
	@Override
	protected Class<?>[] getServletConfigClasses() {
		return new Class<?>[]{WebMvcConfig.class};
	}

	/**
	 * 指定 DispatcherServlet 的 URL 映射
	 * "/" 表示拦截所有请求（但不包括 *.jsp）
	 *
	 * 调试：在 AbstractDispatcherServletInitializer#registerDispatcherServlet() 打断点
	 */
	@Override
	protected String[] getServletMappings() {
		return new String[]{"/"};
	}

	/**
	 * 注册 Filter（会自动映射到 DispatcherServlet 上）
	 *
	 * 调试：在 AbstractDispatcherServletInitializer#registerServletFilter() 打断点
	 * 可以看到 Filter 是如何注册到 ServletContext 中的
	 *
	 * Filter chain execution order (determined by array order):
	 *   1. CharacterEncodingFilter → encoding
	 *   2. LoggingFilter → request/response logging (see document ⑧)
	 *   3. AuthFilter → authentication check, can short-circuit (see document ⑧)
	 *
	 * Test short-circuit: curl -H "X-Auth: reject" http://localhost:8080/demo/param?name=test
	 */
	@Override
	protected Filter[] getServletFilters() {
		// 字符编码过滤器，解决中文乱码问题
		CharacterEncodingFilter encodingFilter = new CharacterEncodingFilter();
		encodingFilter.setEncoding("UTF-8");
		encodingFilter.setForceEncoding(true);

		// Demo filters for source code debugging
		// Array order = Filter chain order
		LoggingFilter loggingFilter = new LoggingFilter();
		AuthFilter authFilter = new AuthFilter();

		return new Filter[]{encodingFilter, loggingFilter, authFilter};
	}
}