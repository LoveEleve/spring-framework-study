package com.debug.processor;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

/**
 * 自定义 BeanPostProcessor - Bean后置处理器
 * 
 * 作用：在Bean初始化前后进行自定义处理
 * 
 * 应用场景：
 * 1. AOP代理创建（AbstractAutoProxyCreator）
 * 2. 注解处理（AutowiredAnnotationBeanPostProcessor）
 * 3. 属性赋值（CommonAnnotationBeanPostProcessor）
 * 
 * 核心流程：
 * - AbstractAutowireCapableBeanFactory.initializeBean()
 *   -> applyBeanPostProcessorsBeforeInitialization()
 *   -> invokeInitMethods()
 *   -> applyBeanPostProcessorsAfterInitialization()
 */
@Component
public class CustomBeanPostProcessor implements BeanPostProcessor {

	/**
	 * 初始化前置处理
	 * 
	 * 在以下时机之前执行：
	 * - @PostConstruct
	 * - InitializingBean.afterPropertiesSet()
	 * - 自定义 init-method
	 */
	@Override
	public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
		if (beanName.equals("lifecycleBean")) {
			System.out.println("[后置处理器] postProcessBeforeInitialization: " + beanName);
		}
		return bean;
	}

	/**
	 * 初始化后置处理
	 * 
	 * 在所有初始化方法执行完后调用
	 * AOP 代理就是在这里创建的！
	 * 
	 * 关键实现：
	 * - AbstractAutoProxyCreator.postProcessAfterInitialization()
	 * - 创建代理包装原始Bean
	 */
	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
		if (beanName.equals("lifecycleBean")) {
			System.out.println("[后置处理器] postProcessAfterInitialization: " + beanName);
		}
		return bean;
	}
}
