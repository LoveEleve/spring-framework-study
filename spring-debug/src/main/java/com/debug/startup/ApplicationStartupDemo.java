package com.debug.startup;

import com.debug.config.AppConfig;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.metrics.ApplicationStartup;
import org.springframework.core.metrics.StartupStep;

import java.util.ArrayList;
import java.util.List;

/**
 * ApplicationStartup 启动监控机制演示
 * 
 * 这是 Spring 5.3 引入的重要特性，用于监控和分析应用启动性能。
 * 
 * @author Spring源码学习者
 */
public class ApplicationStartupDemo {

	public static void main(String[] args) {
		System.out.println("========== ApplicationStartup 监控演示 ==========\n");

		// 创建自定义的 ApplicationStartup 实现
		CustomApplicationStartup customStartup = new CustomApplicationStartup();

		// 创建容器并设置自定义的 ApplicationStartup
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
		context.setApplicationStartup(customStartup);
		
		// 注册配置类并刷新容器
		context.register(AppConfig.class);
		context.refresh();

		System.out.println("\n========== 容器启动完成 ==========\n");

		// 打印启动步骤统计信息
		customStartup.printStatistics();

		context.close();
	}

	/**
	 * 自定义 ApplicationStartup 实现
	 * 用于收集和分析启动步骤的性能数据
	 */
	static class CustomApplicationStartup implements ApplicationStartup {

		private final List<StepRecord> records = new ArrayList<>();

		@Override
		public StartupStep start(String name) {
			long startTime = System.currentTimeMillis();
			System.out.println("[启动步骤] 开始: " + name);

			return new CustomStartupStep(name, startTime, records);
		}

		/**
		 * 打印启动统计信息
		 */
		public void printStatistics() {
			System.out.println("========== 启动步骤统计 ==========");
			System.out.println("总步骤数: " + records.size());
			System.out.println("\n详细信息：");

			long totalTime = 0;
			for (StepRecord record : records) {
				System.out.printf("%-50s 耗时: %4d ms%n", 
					record.name, record.duration);
				totalTime += record.duration;
			}

			System.out.println("\n总耗时: " + totalTime + " ms");
			System.out.println("=====================================");
		}
	}

	/**
	 * 自定义 StartupStep 实现
	 */
	static class CustomStartupStep implements StartupStep {

		private final String name;
		private final long startTime;
		private final List<StepRecord> records;
		private long id = 0;

		public CustomStartupStep(String name, long startTime, List<StepRecord> records) {
			this.name = name;
			this.startTime = startTime;
			this.records = records;
		}

		@Override
		public String getName() {
			return name;
		}

		@Override
		public long getId() {
			return id;
		}

		@Override
		public Long getParentId() {
			return null;
		}

		@Override
		public StartupStep tag(String key, String value) {
			System.out.println("  [标签] " + key + " = " + value);
			return this;
		}

		@Override
		public StartupStep tag(String key, java.util.function.Supplier<String> value) {
			return tag(key, value.get());
		}

		@Override
		public Tags getTags() {
			return () -> java.util.Collections.emptyIterator();
		}

		@Override
		public void end() {
			long duration = System.currentTimeMillis() - startTime;
			System.out.println("[启动步骤] 结束: " + name + " (耗时: " + duration + "ms)\n");
			
			// 记录步骤信息
			records.add(new StepRecord(name, duration));
		}
	}

	/**
	 * 步骤记录
	 */
	static class StepRecord {
		String name;
		long duration;

		StepRecord(String name, long duration) {
			this.name = name;
			this.duration = duration;
		}
	}
}
