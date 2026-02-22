package com.debug.aop_demo_async_1;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.concurrent.Future;

public class AsyncTest {

	public static void main(String[] args) throws Exception {
		AnnotationConfigApplicationContext ctx =
				new AnnotationConfigApplicationContext(AsyncConfig.class);

		OrderService orderService = ctx.getBean(OrderService.class);

		// 验证是代理对象
		System.out.println("Bean类型: " + orderService.getClass().getName());
		System.out.println("主线程: " + Thread.currentThread().getName());
		System.out.println();

		// ===== 测试1: 异步无返回值 =====
		System.out.println("===== 测试1: 异步无返回值 =====");
		orderService.sendNotification("2024001");
		System.out.println("[main] sendNotification() 已返回（不等待异步完成）");
		System.out.println();

		// ===== 测试2: 异步有返回值 =====
		System.out.println("===== 测试2: 异步有返回值 =====");
		Future<String> future = orderService.calculateScore("2024001");
		System.out.println("[main] calculateScore() 已返回 Future");
		String result = future.get();  // 阻塞等待结果
		System.out.println("[main] 获取结果: " + result);
		System.out.println();

		// ===== 测试3: 异步异常丢失 =====
		System.out.println("===== 测试3: 异步异常（调用方无感知） =====");
		orderService.asyncWithException();
		System.out.println("[main] asyncWithException() 已返回（异常在异步线程中，主线程感知不到）");
		System.out.println();

		// ===== 测试4: 同步方法对比 =====
		System.out.println("===== 测试4: 同步方法对比 =====");
		orderService.syncMethod();

		// 等待异步任务完成再关闭
		Thread.sleep(2000);
		System.out.println("\n===== 程序执行完成 =====");
		ctx.close();
	}
}
