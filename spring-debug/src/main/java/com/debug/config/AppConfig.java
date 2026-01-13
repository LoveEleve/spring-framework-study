package com.debug.config;

import org.springframework.context.annotation.*;

/**
 * Spring 配置类 - 纯注解配置
 * 
 * 关键注解：
 * @Configuration - 标识配置类
 * @ComponentScan - 组件扫描
 * @EnableAspectJAutoProxy - 启用AOP
 */
@Configuration
@ComponentScan(basePackages = "com.debug")
@EnableAspectJAutoProxy(exposeProxy = true)  // 暴露代理对象，解决内部调用问题
public class AppConfig {

	/**
	 * 自定义 Bean - 观察单例Bean的创建过程
	 * 
	 * 关键断点：
	 * - AbstractAutowireCapableBeanFactory.createBean()
	 * - AbstractAutowireCapableBeanFactory.doCreateBean()
	 * - AbstractAutowireCapableBeanFactory.populateBean()  // 属性填充
	 * - AbstractAutowireCapableBeanFactory.initializeBean() // 初始化
	 */
	@Bean
	public CustomBean customBean() {
		System.out.println("[配置] 创建CustomBean...");
		return new CustomBean("自定义Bean实例");
	}

	/**
	 * 内部类：用于演示Bean生命周期
	 */
	public static class CustomBean {
		private String name;

		public CustomBean(String name) {
			this.name = name;
			System.out.println("[生命周期] CustomBean 构造方法执行: " + name);
		}

		public String getName() {
			return name;
		}
	}
}
