package com.debug;

import com.debug.config.TransactionConfig;
import com.debug.service.TransactionalUserService;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * Spring 事务源码调试（进阶版）
 * 
 * 需要在 spring-debug.gradle 中添加事务和JDBC依赖后才能运行：
 * - implementation(project(":spring-tx"))
 * - implementation(project(":spring-jdbc"))
 * - implementation "com.h2database:h2:1.4.200"
 * 
 * @author Spring源码学习者
 */
public class SpringTransactionDebugApp {

	public static void main(String[] args) {
		System.out.println("========== Spring 事务源码调试开始 ==========\n");

		// 容器创建
		AnnotationConfigApplicationContext context = 
				new AnnotationConfigApplicationContext(TransactionConfig.class);

		System.out.println("\n========== 容器启动完成 ==========\n");

		// 获取事务服务Bean
		TransactionalUserService userService = context.getBean(TransactionalUserService.class);

		// 【断点】调用带事务的方法
		// 核心：TransactionInterceptor.invoke()
		userService.saveUserWithTransaction("张三");

		System.out.println("\n========== 程序执行完成 ==========");

		context.close();
	}
}
