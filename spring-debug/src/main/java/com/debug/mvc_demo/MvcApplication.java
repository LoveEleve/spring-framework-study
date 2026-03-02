package com.debug.mvc_demo;

import java.io.File;

import org.apache.catalina.Context;
import org.apache.catalina.Wrapper;
import org.apache.catalina.startup.Tomcat;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;

/**
 * Spring MVC 源码调试启动类
 *
 * 提供两种启动方式，分别对应不同的学习阶段：
 *
 * ====== 方式一：标准 SCI 方式（推荐，更贴近真实环境）======
 * 使用 Servlet 3.0 的 ServletContainerInitializer 机制，
 * 让 Spring 自动发现 MyWebAppInitializer 并完成所有注册工作。
 * 这是 Spring MVC 在传统 Web 容器（Tomcat/Jetty）中的标准启动方式。
 *
 * ====== 方式二：手动注册方式（简单直观）======
 * 手动创建 DispatcherServlet 并注册到 Tomcat，
 * 跳过 SCI 机制，适合初次学习时理解核心流程。
 *
 * ====== 与 Spring Boot 的对比 ======
 *
 * 传统方式（方式一）：                         Spring Boot 方式：
 * Tomcat 启动                                SpringApplication.run()
 *   → SCI 机制发现 SpringServletContainer...     → 创建 ApplicationContext
 *     → 找到 MyWebAppInitializer                   → 自动配置 DispatcherServlet
 *       → 创建 Root/Servlet 两个容器                  → 创建嵌入式 Tomcat
 *         → 注册 DispatcherServlet                      → 注册 DispatcherServlet
 *
 * 区别：传统方式是"容器找 Spring"，Spring Boot 是"Spring 创建容器"。
 * 理解了方式一，就能明白 Spring Boot 的自动配置到底省略了哪些步骤。
 *
 * 启动后访问：
 *   http://localhost:8080/hello
 *   http://localhost:8080/greet?name=Spring
 *   http://localhost:8080/user/1
 */
public class MvcApplication {

	public static void main(String[] args) throws Exception {

		// ====== 选择启动方式 ======
		// true  = 使用标准 SCI 方式（推荐，带 Root/Servlet 父子容器 + Filter + Listener）
		// false = 使用手动注册方式（简单，适合初次学习）
		boolean useSciMode = true;

		if (useSciMode) {
			startWithSCI();
		} else {
			startManually();
		}
	}

	/**
	 * ====== 方式一：标准 SCI 方式（推荐）======
	 *
	 * 完整模拟 Servlet 3.0 容器启动 Spring MVC 的过程：
	 * 1. Tomcat 启动
	 * 2. 通过 SCI 机制调用 SpringServletContainerInitializer#onStartup()
	 * 3. Spring 发现 MyWebAppInitializer，自动注册：
	 *    - ContextLoaderListener → 创建 Root 容器（父容器，管理 Service/DAO）
	 *    - DispatcherServlet     → 创建 Servlet 容器（子容器，管理 Controller）
	 *    - CharacterEncodingFilter → 字符编码过滤器
	 *
	 * 调试要点：
	 * 1. SpringServletContainerInitializer#onStartup() → SCI 机制如何找到 WebApplicationInitializer
	 * 2. AbstractContextLoaderInitializer#registerContextLoaderListener() → Root 容器创建
	 * 3. AbstractDispatcherServletInitializer#registerDispatcherServlet() → DispatcherServlet 注册
	 * 4. ContextLoader#initWebApplicationContext() → Root 容器初始化
	 * 5. FrameworkServlet#initWebApplicationContext() → Servlet 容器初始化 + 关联父容器
	 */
	private static void startWithSCI() throws Exception {
		Tomcat tomcat = new Tomcat();
		tomcat.setPort(8080);
		tomcat.getConnector();

		// 使用 addWebapp 代替 addContext，这样 Tomcat 才会触发 SCI 机制
		// 需要一个 docBase 目录，这里创建一个临时目录
		File docBase = new File(System.getProperty("java.io.tmpdir"), "spring-mvc-debug");
		if (!docBase.exists()) {
			docBase.mkdirs();
		}
		Context context = tomcat.addWebapp("", docBase.getAbsolutePath());

		// 使用 addWebapp() 后，Tomcat 会自动通过 SPI 机制（META-INF/services）
		// 发现 SpringServletContainerInitializer，然后扫描 classpath 找到
		// MyWebAppInitializer（WebApplicationInitializer 的实现类），自动完成注册。
		//
		// 注意：不要再手动调用 context.addServletContainerInitializer()，
		// 否则 SCI 会被触发两次，导致 DispatcherServlet 重复注册报错！
		//
		// 这正是 Servlet 3.0 规范的标准流程：
		// 1. Tomcat 通过 SPI 找到 SpringServletContainerInitializer
		// 2. @HandlesTypes(WebApplicationInitializer.class) 让 Tomcat 扫描实现类
		// 3. 调用 onStartup()，Spring 自动注册 DispatcherServlet、Filter、Listener

		tomcat.start();
		System.out.println("==========================================================");
		System.out.println("  Spring MVC 源码调试环境已启动！（标准 SCI 模式）");
		System.out.println("  ✅ 已启用父子容器模式（Root + Servlet）");
		System.out.println("  ✅ 已注册 CharacterEncodingFilter");
		System.out.println("  ✅ 已通过 SCI 机制自动注册 DispatcherServlet");
		System.out.println("");
		System.out.println("  访问地址:");
		System.out.println("    http://localhost:8080/hello");
		System.out.println("    http://localhost:8080/greet?name=Spring");
		System.out.println("    http://localhost:8080/user/1");
		System.out.println("==========================================================");

		tomcat.getServer().await();
	}

	/**
	 * ====== 方式二：手动注册方式（简单直观）======
	 *
	 * 手动创建 DispatcherServlet 并注册到 Tomcat，
	 * 跳过 SCI 机制，只有一个 ApplicationContext（没有父子容器）。
	 * 适合初次学习时理解 DispatcherServlet 的核心处理流程。
	 *
	 * 调试要点：
	 * 1. DispatcherServlet#init()           → Servlet 初始化
	 * 2. DispatcherServlet#initStrategies() → 初始化九大组件
	 * 3. DispatcherServlet#doDispatch()     → 请求分发（最核心）
	 */
	private static void startManually() throws Exception {
		Tomcat tomcat = new Tomcat();
		tomcat.setPort(8080);
		tomcat.getConnector();

		Context context = tomcat.addContext("", System.getProperty("java.io.tmpdir"));

		AnnotationConfigWebApplicationContext appContext = new AnnotationConfigWebApplicationContext();
		appContext.register(WebMvcConfig.class);

		DispatcherServlet dispatcherServlet = new DispatcherServlet(appContext);

		Wrapper wrapper = Tomcat.addServlet(context, "dispatcher", dispatcherServlet);
		wrapper.setLoadOnStartup(1);
		wrapper.addMapping("/");

		tomcat.start();
		System.out.println("==========================================================");
		System.out.println("  Spring MVC 源码调试环境已启动！（手动注册模式）");
		System.out.println("  ⚠️  单容器模式（无父子容器）");
		System.out.println("  ⚠️  无 Filter / Listener");
		System.out.println("");
		System.out.println("  访问地址: http://localhost:8080/hello");
		System.out.println("==========================================================");

		tomcat.getServer().await();
	}
}
