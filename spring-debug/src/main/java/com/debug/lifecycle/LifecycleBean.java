package com.debug.lifecycle;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.*;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

/**
 * Bean 完整生命周期演示
 * 
 * Spring Bean 生命周期（11个阶段）：
 * 
 * 1. 实例化（Instantiation）
 *    - InstantiationAwareBeanPostProcessor.postProcessBeforeInstantiation()
 *    - 构造器执行
 *    - InstantiationAwareBeanPostProcessor.postProcessAfterInstantiation()
 * 
 * 2. 属性赋值（Populate）
 *    - InstantiationAwareBeanPostProcessor.postProcessProperties()
 *    - 属性填充
 * 
 * 3. Aware接口回调
 *    - BeanNameAware.setBeanName()
 *    - BeanClassLoaderAware.setBeanClassLoader()
 *    - BeanFactoryAware.setBeanFactory()
 *    - ApplicationContextAware.setApplicationContext()
 * 
 * 4. 初始化前置处理
 *    - BeanPostProcessor.postProcessBeforeInitialization()
 *    - @PostConstruct 注解方法
 * 
 * 5. 初始化
 *    - InitializingBean.afterPropertiesSet()
 *    - 自定义 init-method
 * 
 * 6. 初始化后置处理
 *    - BeanPostProcessor.postProcessAfterInitialization()
 *    - AOP 代理创建
 * 
 * 7. 使用（Bean可用）
 * 
 * 8. 销毁前
 *    - @PreDestroy 注解方法
 * 
 * 9. 销毁
 *    - DisposableBean.destroy()
 *    - 自定义 destroy-method
 */
@Component
public class LifecycleBean implements BeanNameAware, BeanFactoryAware, 
		ApplicationContextAware, InitializingBean, DisposableBean {

	private String beanName;
	private BeanFactory beanFactory;
	private ApplicationContext applicationContext;

	/**
	 * 1. 构造器执行
	 */
	public LifecycleBean() {
		System.out.println("\n[生命周期-1] 构造器执行");
	}

	/**
	 * 2. BeanNameAware 回调
	 */
	@Override
	public void setBeanName(String name) {
		this.beanName = name;
		System.out.println("[生命周期-2] BeanNameAware.setBeanName(): " + name);
	}

	/**
	 * 3. BeanFactoryAware 回调
	 */
	@Override
	public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
		this.beanFactory = beanFactory;
		System.out.println("[生命周期-3] BeanFactoryAware.setBeanFactory()");
	}

	/**
	 * 4. ApplicationContextAware 回调
	 */
	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
		System.out.println("[生命周期-4] ApplicationContextAware.setApplicationContext()");
	}

	/**
	 * 5. @PostConstruct 初始化方法
	 * 
	 * 由 CommonAnnotationBeanPostProcessor 处理
	 */
	@PostConstruct
	public void postConstruct() {
		System.out.println("[生命周期-5] @PostConstruct 初始化方法");
	}

	/**
	 * 6. InitializingBean 接口方法
	 */
	@Override
	public void afterPropertiesSet() throws Exception {
		System.out.println("[生命周期-6] InitializingBean.afterPropertiesSet()");
	}

	/**
	 * 业务方法
	 */
	public void doSomething() {
		System.out.println("[生命周期-使用] Bean 正在使用中...");
	}

	/**
	 * 7. @PreDestroy 销毁前方法
	 */
	@PreDestroy
	public void preDestroy() {
		System.out.println("[生命周期-7] @PreDestroy 销毁前方法");
	}

	/**
	 * 8. DisposableBean 接口方法
	 */
	@Override
	public void destroy() throws Exception {
		System.out.println("[生命周期-8] DisposableBean.destroy()\n");
	}
}
