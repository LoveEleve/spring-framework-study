package com.debug;

import com.debug.config.AppConfig;
import com.debug.service.UserService;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * Spring 源码调试主入口
 * 
 * 调试流程：
 * 1. 容器创建与初始化（refresh方法）
 * 2. Bean的生命周期
 * 3. 依赖注入过程
 * 4. AOP代理创建
 * 5. 事务管理
 * 
 * @author Spring源码学习者
 */
public class SpringSourceDebugApp {

	public static void main(String[] args) {
		System.out.println("========== Spring 源码调试开始 ==========\n");

		// 【断点1】容器创建入口 - 重点关注 refresh() 方法
		// 核心方法：AbstractApplicationContext.refresh()
		// 涉及12个核心步骤，这是Spring容器启动的灵魂
		AnnotationConfigApplicationContext context = 
				new AnnotationConfigApplicationContext(AppConfig.class);

		System.out.println("\n========== 容器启动完成 ==========\n");

		// 【断点2】获取Bean - 跟踪依赖注入流程
		// 核心方法：AbstractBeanFactory.doGetBean()
		UserService userService = context.getBean(UserService.class);

		// 【断点3】调用业务方法 - 观察AOP代理执行
		// 核心：JdkDynamicAopProxy.invoke() 或 CglibAopProxy.intercept()
		userService.saveUser("张三");

		System.out.println("\n========== 程序执行完成 ==========");

		context.close();
	}
}
