/*
 * Copyright 2002-2018 the original author or authors.
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

package org.springframework.aop.framework;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.aopalliance.intercept.Interceptor;
import org.aopalliance.intercept.MethodInterceptor;

import org.springframework.aop.Advisor;
import org.springframework.aop.IntroductionAdvisor;
import org.springframework.aop.IntroductionAwareMethodMatcher;
import org.springframework.aop.MethodMatcher;
import org.springframework.aop.PointcutAdvisor;
import org.springframework.aop.framework.adapter.AdvisorAdapterRegistry;
import org.springframework.aop.framework.adapter.GlobalAdvisorAdapterRegistry;
import org.springframework.lang.Nullable;

/**
 * A simple but definitive way of working out an advice chain for a Method,
 * given an {@link Advised} object. Always rebuilds each advice chain;
 * caching can be provided by subclasses.
 *
 * @author Juergen Hoeller
 * @author Rod Johnson
 * @author Adrian Colyer
 * @since 2.0.3
 */
@SuppressWarnings("serial")
public class DefaultAdvisorChainFactory implements AdvisorChainFactory, Serializable {
	/*
		forcus 特别注意这第一个参数：
			 Advised config：其实就是 proxyFactory对象
	 */
	@Override
	public List<Object> getInterceptorsAndDynamicInterceptionAdvice(
			Advised config, Method method, @Nullable Class<?> targetClass) {

		// This is somewhat tricky... We have to process introductions first,
		// but we need to preserve order in the ultimate list.
		AdvisorAdapterRegistry registry = GlobalAdvisorAdapterRegistry.getInstance();
		/*
			 获取当前bean对象对应的advisor
			 对于我的 aop_demo_1下的 calculatorService对象来说，advisors数组中有6个元素
			 其中第0个是spring默认加的，为了保证每个advice都能拿到 MethodInvocation 对象
		 */
		Advisor[] advisors = config.getAdvisors();
		// 创建拦截器列表 - 与 Advisor数组长度一致
		List<Object> interceptorList = new ArrayList<>(advisors.length);
		Class<?> actualClass = (targetClass != null ? targetClass : method.getDeclaringClass()); // 确定目标类
		Boolean hasIntroductions = null; // 不考虑"引入"逻辑,默认为false
		/*
			forcus 遍历每个 advisor 判断是否匹配当前方法

			为什么要
		 */
		for (Advisor advisor : advisors) {
			// forcus 99.9999%都是走这里的逻辑
			if (advisor instanceof PointcutAdvisor) {
				// Add it conditionally.
				PointcutAdvisor pointcutAdvisor = (PointcutAdvisor) advisor;
				// 首先类一定要匹配(在为bean对象生成代理的时候就已经做过类匹配了,所以这里就不需要再判断了config.isPreFiltered()=true,直接就进入了if内部的逻辑了，不需要重新匹配)
				if (config.isPreFiltered() || pointcutAdvisor.getPointcut().getClassFilter().matches(actualClass)) {
					// 获取方法匹配器,就是 pointcutAdvisor 本身,在这里必须精确匹配当前方法
					MethodMatcher mm = pointcutAdvisor.getPointcut().getMethodMatcher();
					boolean match;
					if (mm instanceof IntroductionAwareMethodMatcher) { // --- skip 不考虑"引入"逻辑
						if (hasIntroductions == null) {
							hasIntroductions = hasMatchingIntroductions(advisors, actualClass);
						}
						match = ((IntroductionAwareMethodMatcher) mm).matches(method, actualClass, hasIntroductions);
					}
					else {
						match = mm.matches(method, actualClass); // forcus 方法匹配逻辑 - 具体的细节暂时不需要关注
					}

					// ----
					// 只有匹配才会进入下面的逻辑
					if (match) {
						/*
							forcus 将 advisor 转换成 MethodInterceptor, 如果某个advisor不是 MethodInterceptor类型,那么才会转换，否则什么都不做
							spring默认提供了3个转换器
							public DefaultAdvisorAdapterRegistry() {
								registerAdvisorAdapter(new MethodBeforeAdviceAdapter());   // 前置通知适配器
								registerAdvisorAdapter(new AfterReturningAdviceAdapter()); // 返回通知适配器
								registerAdvisorAdapter(new ThrowsAdviceAdapter());         // 异常通知适配器
							}


						 */
						MethodInterceptor[] interceptors = registry.getInterceptors(advisor);
						if (mm.isRuntime()) { // 动态匹配,通常用于复杂的切点匹配(但是很少使用到),不用关心这里,99.9%的情况都是 interceptorList.addAll(Arrays.asList(interceptors));
							// Creating a new object instance in the getInterceptors() method
							// isn't a problem as we normally cache created chains.
							for (MethodInterceptor interceptor : interceptors) {
								interceptorList.add(new InterceptorAndDynamicMethodMatcher(interceptor, mm));
							}
						}
						else {
							interceptorList.addAll(Arrays.asList(interceptors));
						}
					}
				}
			}
			else if (advisor instanceof IntroductionAdvisor) {
				IntroductionAdvisor ia = (IntroductionAdvisor) advisor;
				if (config.isPreFiltered() || ia.getClassFilter().matches(actualClass)) {
					Interceptor[] interceptors = registry.getInterceptors(advisor);
					interceptorList.addAll(Arrays.asList(interceptors));
				}
			}
			else {
				Interceptor[] interceptors = registry.getInterceptors(advisor);
				interceptorList.addAll(Arrays.asList(interceptors));
			}
		}

		return interceptorList;
	}

	/**
	 * Determine whether the Advisors contain matching introductions.
	 */
	private static boolean hasMatchingIntroductions(Advisor[] advisors, Class<?> actualClass) {
		for (Advisor advisor : advisors) {
			if (advisor instanceof IntroductionAdvisor) {
				IntroductionAdvisor ia = (IntroductionAdvisor) advisor;
				if (ia.getClassFilter().matches(actualClass)) {
					return true;
				}
			}
		}
		return false;
	}

}
