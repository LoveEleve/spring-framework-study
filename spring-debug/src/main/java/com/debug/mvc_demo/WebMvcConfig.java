package com.debug.mvc_demo;

import com.debug.mvc_demo.demo.AuthInterceptor;
import com.debug.mvc_demo.demo.LoggingInterceptor;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Servlet WebApplicationContext 配置类（子容器）
 *
 * 这是 DispatcherServlet 的专属容器，只管理 Web 层的 Bean：
 * - Controller（@Controller / @RestController）
 * - HandlerMapping、HandlerAdapter
 * - ViewResolver（视图解析器）
 * - Interceptor（拦截器）
 * - ExceptionResolver（异常解析器）
 * - ControllerAdvice（全局异常处理 / 数据绑定）
 *
 * ====== 父子容器关系 ======
 *
 * RootConfig（父容器）            WebMvcConfig（子容器，本类）
 *   ├── Service                    ├── Controller ← 可以注入父容器的 Service
 *   ├── DAO                        ├── HandlerMapping
 *   ├── DataSource                 ├── HandlerAdapter
 *   └── TransactionManager         ├── ViewResolver
 *                                  └── ControllerAdvice
 *
 * 核心注解说明：
 * - @EnableWebMvc：开启 Spring MVC 支持，等价于 <mvc:annotation-driven/>
 *   内部通过 DelegatingWebMvcConfiguration 注册了一系列核心组件
 *
 * - @ComponentScan：只扫描 @Controller、@RestController 和 @ControllerAdvice，
 *   Service/DAO 等由 RootConfig 管理
 *
 * 调试建议：
 * 1. 在 DelegatingWebMvcConfiguration 的方法上打断点，可以看到 MVC 组件的注册过程
 * 2. 在 WebMvcConfigurationSupport#requestMappingHandlerMapping() 打断点，
 *    可以看到 @RequestMapping 的解析过程
 */
@Configuration
@EnableWebMvc
@ComponentScan(
		basePackages = "com.debug.mvc_demo",
		// 使用 SCI 模式时，只扫描 Web 层组件，避免与 RootConfig 重复注册
		// 包含 Controller + ControllerAdvice（全局异常处理器需要在子容器中）
		includeFilters = @ComponentScan.Filter(
				type = FilterType.ANNOTATION,
				classes = {Controller.class, RestController.class, ControllerAdvice.class}
		),
		useDefaultFilters = false
)
public class WebMvcConfig implements WebMvcConfigurer {

	/**
	 * Register Interceptors for debugging demo.
	 *
	 * Execution order:
	 *   LoggingInterceptor (order=1) → AuthInterceptor (order=2)
	 *
	 * Apply only to /demo/** paths to avoid interfering with existing endpoints.
	 *
	 * Breakpoints:
	 *   1. AbstractHandlerMapping#getHandlerExecutionChain()
	 *      → see how interceptors are assembled into HandlerExecutionChain
	 *   2. MappedInterceptor#matches() → see path pattern matching
	 */
	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(new LoggingInterceptor())
				.addPathPatterns("/demo/**")
				.order(1);

		registry.addInterceptor(new AuthInterceptor())
				.addPathPatterns("/demo/**")
				.order(2);
	}
}
