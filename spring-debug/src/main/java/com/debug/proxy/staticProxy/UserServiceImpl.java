package com.debug.proxy.staticProxy;

/**
 * @Classname UserServiceImpl
 * @Date 2/1/26
 * @Created by ywj
 */
public class UserServiceImpl implements UserService {
	@Override
	public void commit() {
		System.out.println("UserServiceImpl.commit");
	}
}
