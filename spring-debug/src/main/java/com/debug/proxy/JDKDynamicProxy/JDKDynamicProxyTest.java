package com.debug.proxy.JDKDynamicProxy;

import java.lang.reflect.Proxy;

/**
 * @Classname JDKDynamicProxyTest
 * @Date 2/1/26
 * @Created by ywj
 */
public class JDKDynamicProxyTest {
	public static void main(String[] args) {
		UserServiceImpl realService = new UserServiceImpl();
		UserService proxyInstance = (UserService) Proxy.newProxyInstance(
				realService.getClass().getClassLoader(),
				realService.getClass().getInterfaces(),
				new LogInvocationHandler(realService)
		);

		proxyInstance.commit();
		System.out.println("代理类: " + proxyInstance.getClass().getName());
		System.out.println("是否是代理类: " + Proxy.isProxyClass(proxyInstance.getClass()));
	}
}
