/*
 * Copyright 2002-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.aopalliance.aop;

/**
 * Tag interface for Advice. Implementations can be any type
 * of advice, such as Interceptors.
 *
 * @author Rod Johnson
 * @version $Id: Advice.java,v 1.1 2004/03/19 17:02:16 johnsonr Exp $
 */
/*
	forcus 接口标记，代表这是一个通知
		真正有方法定义的核心子接口有3个：MethodInterceptor / MethodBeforeAdvice / AfterReturningAdvice.其他的子类口都是用于分类的
		1. MethodInterceptor:环绕拦截器
			方法拦截器，控制整个方法的执行流程(可以修改一切,比如参数，返回值，异常)，能够实现所有其他类型的通知

			{
				eg:
				public class MyInterceptor implements MethodInterceptor {
					@Override
					public Object invoke(MethodInvocation mi) throws Throwable {
						// 1. 前置处理
						System.out.println("方法开始: " + mi.getMethod().getName());

						// 2. 可以修改参数
						Object[] args = mi.getArguments();

						// 3. 调用目标方法（必须手动调用！）
						Object result = mi.proceed();

						// 4. 可以修改返回值
						if (result instanceof Integer) {
							result = (Integer) result * 2;
						}

						// 5. 后置处理
						System.out.println("方法结束，返回: " + result);

						return result;
					}
				}
			}
		2. MethodBeforeAdvice:前置通知
				回调风格,框架控制流程，你只写前置逻辑,核心逻辑是调用我们写的被@Before标注的方法
		3. AfterReturningAdvice:返回后通知
				回调风格，框架控制流程，你只写后置逻辑，核心逻辑是调用我们写的被@AfterReturning标注的方法

	AbstractAspectJAdvice：所有 AspectJ风格通知的基类，有5个非常重要的子类
		1. AspectJAroundAdvice -> MethodInterceptor
		2. AspectJMethodBeforeAdvice -> MethodBeforeAdvice
		3. AspectJAfterAdvice -> MethodInterceptor
		4. AspectJAfterReturningAdvice -> AfterReturningAdvice
		5. AspectJAfterThrowingAdvice -> MethodInterceptor

 */
public interface Advice {

}
