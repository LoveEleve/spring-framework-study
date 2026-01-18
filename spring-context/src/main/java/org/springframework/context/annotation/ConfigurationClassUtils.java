/*
 * Copyright 2002-2021 the original author or authors.
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

package org.springframework.context.annotation;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.aop.framework.AopInfrastructureBean;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.context.event.EventListenerFactory;
import org.springframework.core.Conventions;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * Utilities for identifying {@link Configuration} classes.
 *
 * @author Chris Beams
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @since 3.1
 */
abstract class ConfigurationClassUtils {

	public static final String CONFIGURATION_CLASS_FULL = "full";

	public static final String CONFIGURATION_CLASS_LITE = "lite";

	public static final String CONFIGURATION_CLASS_ATTRIBUTE =
			Conventions.getQualifiedAttributeName(ConfigurationClassPostProcessor.class, "configurationClass");

	private static final String ORDER_ATTRIBUTE =
			Conventions.getQualifiedAttributeName(ConfigurationClassPostProcessor.class, "order");


	private static final Log logger = LogFactory.getLog(ConfigurationClassUtils.class);

	private static final Set<String> candidateIndicators = new HashSet<>(8);

	static {
		candidateIndicators.add(Component.class.getName());
		candidateIndicators.add(ComponentScan.class.getName());
		candidateIndicators.add(Import.class.getName());
		candidateIndicators.add(ImportResource.class.getName());
	}


	/**
	 * Check whether the given bean definition is a candidate for a configuration class
	 * (or a nested component class declared within a configuration/component class,
	 * to be auto-registered as well), and mark it accordingly.
	 * @param beanDef the bean definition to check
	 * @param metadataReaderFactory the current factory in use by the caller
	 * @return whether the candidate qualifies as (any kind of) configuration class
	 */
	/*
		forcus 方法概述：
			判断一个 BeanDefinition 是否为配置类候选者，并标记其类型（Full/Lite）
	 */
	public static boolean checkConfigurationClassCandidate(
			BeanDefinition beanDef, MetadataReaderFactory metadataReaderFactory) {

		String className = beanDef.getBeanClassName();
		// forcus-1 没有类名 或者 xxx
		/*
			这里需要关注的一点是: beanDef.getFactoryMethodName()
			 - 什么是 Factory Method (也即所谓的工厂方法)
			 - 在Spring中,创建Bean的方式有很多种：
			  1. 直接通过构造方法调用(Spring调用的，不是new xxx()调用的!，如下)：此时beanDef.getFactoryMethodName()为null
					@Component
					public class UserService {
						// Spring 直接调用构造函数创建实例
					}
			  2. 通过@Bean方法创建: 有工厂方法，返回true, userService这个bean的factoryMethodName为"userService"
			     Spring不是调用构造函数来创建userService实例，而是通过调用AppConfig.userService()方法来创建实例
					@Configuration
					public class AppConfig {

						@Bean  // 这个方法就是"工厂方法"
						public UserService userService() {
							return new UserService();
						}
					}
		    回到这里,为什么有 FactoryMethodName 的就返回呢？ 也即不是配置类呢？
		     - 因为通过@Bean方法创建的Bean，是不可能为配置类的(如果加上@Configuration注解是会报错的)

		 */
		if (className == null || beanDef.getFactoryMethodName() != null) {
			return false;
		}

		// forcus-2 下面这段代码的核心目的：获取Bean定义的注解元数据(AnnotationMetadata)
		AnnotationMetadata metadata;
		// forcus-2.1 复用已解析的元数据（最快）
		/*
			beanDef 是 AnnotatedBeanDefinition 类型，并且 className 与元数据中的类名一致
			直接复用 BeanDefinition 中已经解析好的元数据
			{ forcus 通过 @Component、@Configuration 等注解扫描得到的 Bean }
		 */
		if (beanDef instanceof AnnotatedBeanDefinition &&
				className.equals(((AnnotatedBeanDefinition) beanDef).getMetadata().getClassName())) {
			// Can reuse the pre-parsed metadata from the given BeanDefinition...
			metadata = ((AnnotatedBeanDefinition) beanDef).getMetadata();
		}
		// forcus-2.2 从已加载的 Class 对象获取元数据（次快）
		else if (beanDef instanceof AbstractBeanDefinition && ((AbstractBeanDefinition) beanDef).hasBeanClass()) {
			// Check already loaded Class if present...
			// since we possibly can't even load the class file for this Class.
			Class<?> beanClass = ((AbstractBeanDefinition) beanDef).getBeanClass();
			// forcus 排除 Spring 基础设施类
			if (BeanFactoryPostProcessor.class.isAssignableFrom(beanClass) ||
					BeanPostProcessor.class.isAssignableFrom(beanClass) ||
					AopInfrastructureBean.class.isAssignableFrom(beanClass) ||
					EventListenerFactory.class.isAssignableFrom(beanClass)) {
				return false; // 这些类不可能是配置类候选者
			}
			// forcus 基于已加载的 Class 对象，通过反射读取注解信息
			/*
				// 编程方式注册
				AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
				context.register(AppConfig.class);  // Class 已加载
				context.refresh();
			 */
			metadata = AnnotationMetadata.introspect(beanClass);
		}
		// forcus-2.3 通过 ASM 读取字节码获取元数据（最慢但最通用）
		else {
			try {
				/*
					使用 ASM 字节码操作框架直接读取 .class 文件
					不需要加载类到 JVM，避免触发类加载和静态初始化
					 场景:
					  - 类路径扫描时
					  - 需要快速检查大量类的注解信息，但不想加载所有类
				 */
				MetadataReader metadataReader = metadataReaderFactory.getMetadataReader(className);
				metadata = metadataReader.getAnnotationMetadata();
			}
			catch (IOException ex) {
				if (logger.isDebugEnabled()) {
					logger.debug("Could not find class file for introspecting configuration annotations: " +
							className, ex);
				}
				return false;
			}
		}

		// forcus-3 下面这段代码的核心目的:判断并且标记配置类的类型(Full / Lite)，并且设置其执行顺序
		/*
			为什么配置类还要区分不同的类型呢？ --> 再spring中区分为 Full 和 Lite两种类型
			forcus spring的主要目的是为了：性能优化 和 代理行为控制
			 - Full配置类: 条件为：@Configuration + proxyBeanMethods=true
			   - 特点: 该配置类会被CGLIB代理增强，支持Bean方法间的调用拦截,保证单例语义
			 - Lite配置类：@Configuration( proxyBeanMethods=false) / 或者其他注解(@Component/@ComponentScan/@Import/@ImportResource/包含@Bean方法)
			   - 不会被CGLIB代理，不会支持Bean方法间的调用拦截，不能保证单例语义

			 ===

			 处理差异: 在 ConfigurationClassPostProcessor 中，只有Full配置类会被特殊处理(被增强)
		 */
		// 获取类元数据上的 @Configuration 注解
		Map<String, Object> config = metadata.getAnnotationAttributes(Configuration.class.getName());
		// forcus 如果有@Configuration注解 并且 proxyBeanMethods=true(默认值就为true),那么该配置类就是一个Full配置类
		if (config != null && !Boolean.FALSE.equals(config.get("proxyBeanMethods"))) {
			beanDef.setAttribute(CONFIGURATION_CLASS_ATTRIBUTE, CONFIGURATION_CLASS_FULL);
		}
		// forcus 否则为Lite配置类
		else if (config != null || isConfigurationCandidate(metadata)) {
			beanDef.setAttribute(CONFIGURATION_CLASS_ATTRIBUTE, CONFIGURATION_CLASS_LITE);
		}
		else {
			return false; // forcus 否则根本不是一个配置类
		}

		// It's a full or lite configuration candidate... Let's determine the order value, if any.
		Integer order = getOrder(metadata);
		if (order != null) {
			beanDef.setAttribute(ORDER_ATTRIBUTE, order);
		}

		return true; // 最终返回true，代表这是一个配置类
	}

	/**
	 * Check the given metadata for a configuration class candidate
	 * (or nested component class declared within a configuration/component class).
	 * @param metadata the metadata of the annotated class
	 * @return {@code true} if the given class is to be registered for
	 * configuration class processing; {@code false} otherwise
	 */
	public static boolean isConfigurationCandidate(AnnotationMetadata metadata) {
		// Do not consider an interface or an annotation...
		if (metadata.isInterface()) {
			return false;
		}

		// Any of the typical annotations found?
		for (String indicator : candidateIndicators) {
			if (metadata.isAnnotated(indicator)) {
				return true;
			}
		}

		// Finally, let's look for @Bean methods...
		return hasBeanMethods(metadata);
	}

	static boolean hasBeanMethods(AnnotationMetadata metadata) {
		try {
			return metadata.hasAnnotatedMethods(Bean.class.getName());
		}
		catch (Throwable ex) {
			if (logger.isDebugEnabled()) {
				logger.debug("Failed to introspect @Bean methods on class [" + metadata.getClassName() + "]: " + ex);
			}
			return false;
		}
	}

	/**
	 * Determine the order for the given configuration class metadata.
	 * @param metadata the metadata of the annotated class
	 * @return the {@code @Order} annotation value on the configuration class,
	 * or {@code Ordered.LOWEST_PRECEDENCE} if none declared
	 * @since 5.0
	 */
	@Nullable
	public static Integer getOrder(AnnotationMetadata metadata) {
		Map<String, Object> orderAttributes = metadata.getAnnotationAttributes(Order.class.getName());
		return (orderAttributes != null ? ((Integer) orderAttributes.get(AnnotationUtils.VALUE)) : null);
	}

	/**
	 * Determine the order for the given configuration class bean definition,
	 * as set by {@link #checkConfigurationClassCandidate}.
	 * @param beanDef the bean definition to check
	 * @return the {@link Order @Order} annotation value on the configuration class,
	 * or {@link Ordered#LOWEST_PRECEDENCE} if none declared
	 * @since 4.2
	 */
	public static int getOrder(BeanDefinition beanDef) {
		Integer order = (Integer) beanDef.getAttribute(ORDER_ATTRIBUTE);
		return (order != null ? order : Ordered.LOWEST_PRECEDENCE);
	}

}
