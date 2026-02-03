package com.debug.proxy.CGLIBDynamicProxy;

import org.springframework.cglib.proxy.MethodInterceptor;
import org.springframework.cglib.proxy.MethodProxy;

import java.lang.reflect.Method;

/**
 * @Classname TransactionInterceptor
 * @Date 2/2/26
 * @Created by ywj
 */
public class TransactionInterceptor implements MethodInterceptor {
	@Override
	public Object intercept(Object obj, Method method, Object[] args, MethodProxy methodProxy) throws Throwable {
		System.out.println(">>> [CGLIB代理] 开启事务");
		System.out.println(">>> 调用方法: " + method.getName());
		Object result = null;
		try {
			// 调用父类方法（目标方法）
			result = methodProxy.invokeSuper(obj, args);

			// 后置增强
			System.out.println(">>> [CGLIB代理] 提交事务");

		} catch (Exception e) {
			// 异常增强
			System.out.println(">>> [CGLIB代理] 回滚事务");
			throw e;
		}

		return result;
	}
}
