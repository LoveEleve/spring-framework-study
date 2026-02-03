package com.debug.proxy.CGLIBDynamicProxy;

import org.springframework.cglib.proxy.CallbackFilter;

import java.lang.reflect.Method;

/**
 * @Classname MethodFilter
 * @Date 2/2/26
 * @Created by ywj
 */
public class MethodFilter implements CallbackFilter {
	@Override
	public int accept(Method method) {
		String methodName = method.getName();
		// 返回值对应 Callback 数组的索引
		if (methodName.equals("commit")) {
			return 0;  // 使用 CommitInterceptor
		} else if (methodName.equals("internalMethod")) {
			return 1;  // 使用 InternalInterceptor
		} else {
			return 2;  // 使用 NoOp（不拦截）
		}
	}
}
