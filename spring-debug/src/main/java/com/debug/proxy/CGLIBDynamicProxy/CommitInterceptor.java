package com.debug.proxy.CGLIBDynamicProxy;

import org.springframework.cglib.proxy.MethodInterceptor;
import org.springframework.cglib.proxy.MethodProxy;

import java.lang.reflect.Method;

/**
 * @Classname CommitInterceptor
 * @Date 2/2/26
 * @Created by ywj
 */
// 专门针对commit()的拦截器
public class CommitInterceptor implements MethodInterceptor {
	@Override
	public Object intercept(Object obj, Method method, Object[] args, MethodProxy methodProxy) throws Throwable {
		System.out.println("commit before");
		Object result = methodProxy.invokeSuper(obj, args);
		System.out.println("commit after");
		return result;
	}
}
