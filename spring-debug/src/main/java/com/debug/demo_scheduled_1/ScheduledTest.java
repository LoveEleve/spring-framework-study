package com.debug.demo_scheduled_1;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * 定时任务调度 Demo
 *
 * 运行后观察控制台输出：
 * 1. 三种调度方式的执行频率差异
 * 2. 线程名 —— 注意默认只有【一个线程】执行所有任务
 */
public class ScheduledTest {

	public static void main(String[] args) throws InterruptedException {
		AnnotationConfigApplicationContext ctx =
				new AnnotationConfigApplicationContext(ScheduledConfig.class);

		System.out.println("===== 定时任务已启动，观察15秒 =====");

		// 让主线程等待 15 秒，观察调度输出
		Thread.sleep(15000);

		System.out.println("===== 关闭容器 =====");
		ctx.close();
	}
}
