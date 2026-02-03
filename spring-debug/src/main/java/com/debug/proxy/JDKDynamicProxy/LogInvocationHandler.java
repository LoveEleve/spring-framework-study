package com.debug.proxy.JDKDynamicProxy;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * @Classname LogInvocationHandler
 * @Date 2/1/26
 * @Created by ywj
 */
public class LogInvocationHandler implements InvocationHandler {
	private Object target;

	public LogInvocationHandler(Object object) {
		this.target = object;
	}

	@Override
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
		// 前置增强
		System.out.println("=== [JDK代理] 方法调用开始 ===");
		System.out.println("方法名: " + method.getName());
		System.out.println("参数: " + Arrays.toString(args));
		long startTime = System.currentTimeMillis();
		// 调用真实方法
		Object result = null;
		try {
			result = method.invoke(target, args);

			// 后置增强
			long endTime = System.currentTimeMillis();
			System.out.println("执行时间: " + (endTime - startTime) + "ms");
			System.out.println("返回值: " + result);

		} catch (Exception e) {
			// 异常增强
			System.out.println("异常处理: " + e.getMessage());
			throw e;
		} finally {
			System.out.println("=== [JDK代理] 方法调用结束 ===\n");
		}

		return result;
	}
}
