package com.debug.proxy.staticProxy;

/**
 * @Classname StaticProxyTest
 * @Date 2/1/26
 * @Created by ywj
 */
public class StaticProxyTest {
	public static void main(String[] args) {
		UserServiceImpl userService = new UserServiceImpl();
		UserServiceProxy serviceProxy = new UserServiceProxy(userService);
		serviceProxy.commit();
	}
}
