package com.debug.aop_demo_async_1;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@ComponentScan("com.debug.aop_demo_async_1")
@EnableAsync  // 开启异步支持，类似 @EnableTransactionManagement
public class AsyncConfig {

	/**
	 * 自定义线程池
	 * 如果不配置，Spring 默认使用 SimpleAsyncTaskExecutor（每次创建新线程，不复用！生产中必须避免）
	 */
	@Bean("asyncExecutor")
	public Executor asyncExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(2);
		executor.setMaxPoolSize(5);
		executor.setQueueCapacity(10);
		executor.setThreadNamePrefix("async-");
		executor.initialize();
		return executor;
	}
}
