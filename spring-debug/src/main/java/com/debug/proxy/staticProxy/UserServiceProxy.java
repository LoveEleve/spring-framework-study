package com.debug.proxy.staticProxy;

/**
 * @Classname UserServiceProxy
 * @Date 2/1/26
 * @Created by ywj
 */
public class UserServiceProxy implements UserService{
	private UserService target;

	public UserServiceProxy(UserService userService) {
		this.target = userService;
	}

	@Override
	public void commit() {
		System.out.println("proxy.enhance.start");
		target.commit();
		System.out.println("proxy.enhance.end");
	}
}
