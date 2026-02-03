package com.debug.proxy.JDKDynamicProxy;

/**
 * @Classname UserServiceImpl
 * @Date 2/1/26
 * @Created by ywj
 */
public class UserServiceImpl implements UserService {
	@Override
	public void commit() {
		System.out.println("UserServiceImpl commit");
	}
}
