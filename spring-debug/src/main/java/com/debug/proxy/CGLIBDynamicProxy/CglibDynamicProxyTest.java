package com.debug.proxy.CGLIBDynamicProxy;

import org.springframework.cglib.proxy.Callback;
import org.springframework.cglib.proxy.Enhancer;
import org.springframework.cglib.proxy.NoOp;

/**
 * @Classname CglibDynamicProxyTest
 * @Date 2/2/26
 * @Created by ywj
 */
public class CglibDynamicProxyTest {
	public static void main(String[] args) {
		test_2();
	}
	public static void test_0(){}
	public static void test_1(){
		Enhancer enhancer = new Enhancer();
		enhancer.setSuperclass(UserService.class);
		enhancer.setCallback(new TransactionInterceptor());
		UserService proxy = (UserService) enhancer.create();
		proxy.commit();
		proxy.finalMethod();
	}
	public static void test_2(){
		Enhancer enhancer = new Enhancer();
		enhancer.setSuperclass(UserService.class);
		enhancer.setCallbacks(new Callback[]{
				new CommitInterceptor(), // index = 0
				new InternalInterceptor(), // index = 1
				NoOp.INSTANCE // index = 2
		});

		enhancer.setCallbackFilter(new MethodFilter());
		UserService proxy = (UserService) enhancer.create();
		proxy.commit();
		proxy.internalMethod();
		proxy.noProxyMethod();

	}


}
