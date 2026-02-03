package com.debug.proxy.CGLIBDynamicProxy;

import org.springframework.cglib.proxy.MethodInterceptor;
import org.springframework.cglib.proxy.MethodProxy;

import java.lang.reflect.Method;

/**
 * @Classname InternalInterceptor
 * @Date 2/2/26
 * @Created by ywj
 */
public class InternalInterceptor implements MethodInterceptor {
	@Override
	public Object intercept(Object obj, Method method, Object[] args, MethodProxy methodProxy) throws Throwable {
		System.out.println("internal before");
		Object result = methodProxy.invokeSuper(obj, args);
		System.out.println("internal after");
		return result;
	}
}
