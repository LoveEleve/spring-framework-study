package com.debug.aop_demo_async_1;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.Future;
import org.springframework.scheduling.annotation.AsyncResult;

@Service
public class OrderService {

	/**
	 * 异步方法 - 无返回值
	 * 断点：AsyncExecutionInterceptor.invoke()
	 */
	@Async("asyncExecutor")
	public void sendNotification(String orderNo) {
		System.out.println("[sendNotification] 线程: " + Thread.currentThread().getName()
				+ " | 发送通知: 订单" + orderNo + "已创建");
		try {
			Thread.sleep(1000);  // 模拟耗时操作
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		System.out.println("[sendNotification] 线程: " + Thread.currentThread().getName()
				+ " | 通知发送完成");
	}

	/**
	 * 异步方法 - 有返回值
	 */
	@Async("asyncExecutor")
	public Future<String> calculateScore(String orderNo) {
		System.out.println("[calculateScore] 线程: " + Thread.currentThread().getName()
				+ " | 计算订单" + orderNo + "的积分");
		try {
			Thread.sleep(500);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		return new AsyncResult<>("订单" + orderNo + "积分: 100");
	}

	/**
	 * 异步方法 - 抛异常（观察异常丢失问题）
	 */
	@Async("asyncExecutor")
	public void asyncWithException() {
		System.out.println("[asyncWithException] 线程: " + Thread.currentThread().getName());
		throw new RuntimeException("异步方法中的异常 —— 调用方感知不到！");
	}

	/**
	 * 普通同步方法（对比用）
	 */
	public void syncMethod() {
		System.out.println("[syncMethod] 线程: " + Thread.currentThread().getName()
				+ " | 这是同步方法");
	}
}
