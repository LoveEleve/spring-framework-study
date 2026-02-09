/*
 * Copyright 2002-2024 the original author or authors.
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

package org.springframework.aop.aspectj.annotation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.aspectj.lang.reflect.PerClauseKind;

import org.springframework.aop.Advisor;
import org.springframework.aop.framework.AopConfigException;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * Helper for retrieving @AspectJ beans from a BeanFactory and building
 * Spring Advisors based on them, for use with auto-proxying.
 *
 * @author Juergen Hoeller
 * @since 2.0.2
 * @see AnnotationAwareAspectJAutoProxyCreator
 */
public class BeanFactoryAspectJAdvisorsBuilder {

	private static final Log logger = LogFactory.getLog(BeanFactoryAspectJAdvisorsBuilder.class);

	private final ListableBeanFactory beanFactory;

	private final AspectJAdvisorFactory advisorFactory;

	@Nullable
	private volatile List<String> aspectBeanNames;

	private final Map<String, List<Advisor>> advisorsCache = new ConcurrentHashMap<>();

	private final Map<String, MetadataAwareAspectInstanceFactory> aspectFactoryCache = new ConcurrentHashMap<>();


	/**
	 * Create a new BeanFactoryAspectJAdvisorsBuilder for the given BeanFactory.
	 * @param beanFactory the ListableBeanFactory to scan
	 */
	public BeanFactoryAspectJAdvisorsBuilder(ListableBeanFactory beanFactory) {
		this(beanFactory, new ReflectiveAspectJAdvisorFactory(beanFactory));
	}

	/**
	 * Create a new BeanFactoryAspectJAdvisorsBuilder for the given BeanFactory.
	 * @param beanFactory the ListableBeanFactory to scan
	 * @param advisorFactory the AspectJAdvisorFactory to build each Advisor with
	 */
	public BeanFactoryAspectJAdvisorsBuilder(ListableBeanFactory beanFactory, AspectJAdvisorFactory advisorFactory) {
		Assert.notNull(beanFactory, "ListableBeanFactory must not be null");
		Assert.notNull(advisorFactory, "AspectJAdvisorFactory must not be null");
		this.beanFactory = beanFactory;
		this.advisorFactory = advisorFactory;
	}


	/**
	 * Look for AspectJ-annotated aspect beans in the current bean factory,
	 * and return to a list of Spring AOP Advisors representing them.
	 * <p>Creates a Spring Advisor for each AspectJ advice method.
	 * @return the list of {@link org.springframework.aop.Advisor} beans
	 * @see #isEligibleBean
	 */
	// forcus 找到所有的
	public List<Advisor> buildAspectJAdvisors() {
		// 第一次查找的时候该集合是为null的
		List<String> aspectNames = this.aspectBeanNames;

		if (aspectNames == null) {
			synchronized (this) {
				aspectNames = this.aspectBeanNames;
				if (aspectNames == null) {
					List<Advisor> advisors = new ArrayList<>();
					aspectNames = new ArrayList<>();
					// forcus-1 获取容器中所有的beanName
					/*
						有两个参数需要关注一下：
							1. includeNonSingletons：true,包括非单例
							2. allowEagerInit：false，不触发bean的实例化
							对于aop_demo_1 来说, 我的beanNames = [appConfig,basicAspect,calculatorService,spring内部的beanName]
					 */
					String[] beanNames = BeanFactoryUtils.beanNamesForTypeIncludingAncestors(
							this.beanFactory, Object.class, true, false);
					// 依次处理每个beanName
					for (String beanName : beanNames) {
						// 检查是否符合条件（可被子类重写过滤）,用于过滤某些切面(默认都是返回true的)
						// 这里使用的比较少,skip
						if (!isEligibleBean(beanName)) {
							continue;
						}
						// We must be careful not to instantiate beans eagerly as in this case they
						// would be cached by the Spring container but would not have been weaved.
						// 获取bean的类型
						Class<?> beanType = this.beanFactory.getType(beanName, false);
						if (beanType == null) {
							continue;
						}
						// forcus-2 判断当前bean是否是@Aspect类
						// 逻辑为：检查当前bean所对应的类上是否标注了@Aspect注解
						if (this.advisorFactory.isAspect(beanType)) {
							// forcus 当前bean是切面类
							try {
								// forcus 创建切面元数据
								/*
									public class AspectMetadata {
												private final String aspectName;        // "basicAspect"
												private final Class<?> aspectClass;     // BasicAspect.class
												private transient AjType<?> ajType;     // AspectJ 类型信息
												private final Pointcut perClausePointcut;  // 单例时为 Pointcut.TRUE
									}
								 */
								AspectMetadata amd = new AspectMetadata(beanType, beanName);
								/*
									切面实例化模型,99%的情况下，切面对应的都是单例(默认就是单例的),其他情况暂时不关心
									切面是单例的含义是：所有的对象都共享同一个切面bean实例
								 */
								if (amd.getAjType().getPerClause().getKind() == PerClauseKind.SINGLETON) {
									// forcus 创建切面实例工厂
									/*
										public class BeanFactoryAspectInstanceFactory implements MetadataAwareAspectInstanceFactory {
											private final BeanFactory beanFactory;     // Spring BeanFactory
											private final String name;                  // "basicAspect"
											private final AspectMetadata aspectMetadata; // 上面创建的元数据
										}
									 */
									//
									MetadataAwareAspectInstanceFactory factory =
											new BeanFactoryAspectInstanceFactory(this.beanFactory, beanName);
									// forcus 解析切面类,生成Advisor列表
									/*
										1. 扫描当前切面类中所有带有 @Before、@After、@Around、@AfterReturning、@AfterThrowing 注解的方法
										2. 每个方法生成一个 Advisor
										对于我的 basicAspect 来说, 会生成 5 个 Advisor
									 */
									List<Advisor> classAdvisors = this.advisorFactory.getAdvisors(factory);
									// forcus 缓存结果 ，这里缓存的是 切面类 --> 切面类对应的Advisor列表(就是在切面类内部定义的通知方法)
									/*
										 Map<String, List<Advisor>> advisorsCache = new ConcurrentHashMap<>()
										┌─────────────────────────────────────────────────────────────┐
										│  aspectBeanNames = ["basicAspect"]                          │
										│                                                             │
										│  advisorsCache = {                                          │
										│      "basicAspect" → [Advisor1, Advisor2, Advisor3,        │
										│                       Advisor4, Advisor5]                   │
										│  }                                                          │
										└─────────────────────────────────────────────────────────────┘

									 */
									if (this.beanFactory.isSingleton(beanName)) {
										this.advisorsCache.put(beanName, classAdvisors); // forcus 缓存 Advisor
									}
									else {
										this.aspectFactoryCache.put(beanName, factory); // 缓存工厂，不需要关心,通常用于原型bean
									}
									advisors.addAll(classAdvisors);
								}
								// 暂时不关心
								else {
									// Per target or per this.
									if (this.beanFactory.isSingleton(beanName)) {
										throw new IllegalArgumentException("Bean with name '" + beanName +
												"' is a singleton, but aspect instantiation model is not singleton");
									}
									MetadataAwareAspectInstanceFactory factory =
											new PrototypeAspectInstanceFactory(this.beanFactory, beanName);
									this.aspectFactoryCache.put(beanName, factory);
									advisors.addAll(this.advisorFactory.getAdvisors(factory));
								}
								// 记录所有切面 bean的名字
								aspectNames.add(beanName);
							}
							catch (IllegalArgumentException | IllegalStateException | AopConfigException ex) {
								if (logger.isDebugEnabled()) {
									logger.debug("Ignoring incompatible aspect [" + beanType.getName() + "]: " + ex);
								}
							}
						}
					}
					// 最后将切面类的类名保存到成员变量中
					this.aspectBeanNames = aspectNames;
					return advisors;
				}
			}
		}

		if (aspectNames.isEmpty()) {
			return Collections.emptyList();
		}

		// 后续调用,走这里的逻辑,因为 aspectBeanNames 因为不为null了
		List<Advisor> advisors = new ArrayList<>();
		for (String aspectName : aspectNames) {
			List<Advisor> cachedAdvisors = this.advisorsCache.get(aspectName);
			if (cachedAdvisors != null) {
				advisors.addAll(cachedAdvisors);
			}
			else {
				MetadataAwareAspectInstanceFactory factory = this.aspectFactoryCache.get(aspectName);
				advisors.addAll(this.advisorFactory.getAdvisors(factory));
			}
		}
		return advisors;
	}

	/**
	 * Return whether the aspect bean with the given name is eligible.
	 * @param beanName the name of the aspect bean
	 * @return whether the bean is eligible
	 */
	protected boolean isEligibleBean(String beanName) {
		return true;
	}

}
