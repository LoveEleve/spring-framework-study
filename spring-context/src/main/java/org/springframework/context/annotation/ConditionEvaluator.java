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

package org.springframework.context.annotation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ConfigurationCondition.ConfigurationPhase;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.env.Environment;
import org.springframework.core.env.EnvironmentCapable;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.MultiValueMap;

/**
 * Internal class used to evaluate {@link Conditional} annotations.
 *
 * @author Phillip Webb
 * @author Juergen Hoeller
 * @since 4.0
 */
class ConditionEvaluator {

	private final ConditionContextImpl context;


	/**
	 * Create a new {@link ConditionEvaluator} instance.
	 */
	public ConditionEvaluator(@Nullable BeanDefinitionRegistry registry,
							  @Nullable Environment environment, @Nullable ResourceLoader resourceLoader) {

		this.context = new ConditionContextImpl(registry, environment, resourceLoader);
	}


	/**
	 * Determine if an item should be skipped based on {@code @Conditional} annotations.
	 * The {@link ConfigurationPhase} will be deduced from the type of item (i.e. a
	 * {@code @Configuration} class will be {@link ConfigurationPhase#PARSE_CONFIGURATION})
	 * @param metadata the meta data
	 * @return if the item should be skipped
	 */
	public boolean shouldSkip(AnnotatedTypeMetadata metadata) {
		return shouldSkip(metadata, null);
	}

	/**
	 * Determine if an item should be skipped based on {@code @Conditional} annotations.
	 * @param metadata the meta data forcus bean's meta data
	 * @param phase the phase of the call
	 * @return if the item should be skipped
	 */
	public boolean shouldSkip(@Nullable AnnotatedTypeMetadata metadata, @Nullable ConfigurationPhase phase) {
		// 如果没有 bean's meta data 或者 meta data 上没有 Conditional 注解，直接返回 false
		// 返回false则代表不需要被跳过，当前bean可以被继续注册
		if (metadata == null || !metadata.isAnnotated(Conditional.class.getName())) {
			return false;
		}
		// forcus 非常重要的一段代码,是Spring/SpringBoot的核心(精妙设计)
		/*
			这是Spring的 "双阶段" 条件评估策略
			 step-1: 在解析配置类的时候进行条件评估，forcus PARSE_CONFIGURATION
			  - 影响范围,整个配置类及其所有内容
			 step-2: 在注册单个Bean时进行条件评估 forcus REGISTER_BEAN
			  - 影响范围,单个bean
			==
			forcus 如果phase == null,则说明是智能选择
			==
			 - 是配置候选者 -->  shouldSkip(metadata, PARSE_CONFIGURATION)
			 - 是普通bean -->  shouldSkip(metadata, REGISTER_BEAN)
			==
			配置候选者是什么呢？
			 成为配置候选者的3个条件:
			  - 标注了 @Component / @ComponentScan / @Import / @ImportResource
			  	- 这些注解保存在 candidateIndicators集合中
			  - 或者 类中有@Bean标注的方法(这个类可以没有被上面的注解标注,也能成为配置候选者)
		 */
		if (phase == null) {
			if (metadata instanceof AnnotationMetadata &&
					ConfigurationClassUtils.isConfigurationCandidate((AnnotationMetadata) metadata)) {
				return shouldSkip(metadata, ConfigurationPhase.PARSE_CONFIGURATION);
			}
			return shouldSkip(metadata, ConfigurationPhase.REGISTER_BEAN);
		}
		// forcus 获取meta data 中的所有条件实例(保存在 conditions 集合中)
		List<Condition> conditions = new ArrayList<>();
		/*
			@Conditional(A.class,B.class.C.class)
			1. getConditionClasses(metadata) : 这里返回的是 List<String[]> --> 提取类名 [A,B,C]
			2. 内部for()循环的处理,对于每一个类名,都会封装成一个 Condition 对象，然后添加到 conditions 集合中去
		 */
		for (String[] conditionClasses : getConditionClasses(metadata)) {
			for (String conditionClass : conditionClasses) {
				Condition condition = getCondition(conditionClass, this.context.getClassLoader());
				conditions.add(condition);
			}
		}
		// 支持优先级排序
		AnnotationAwareOrderComparator.sort(conditions);
		// forcus 依次处理所有条件实例
		/*
			forcus 这里涉及到了两个类型，不同的条件类型,会有不同的行为处理
			 - Condition ：此时的 requiredPhase = null
			 - ConfigurationCondition : 该类型内部有一个枚举类,定义两个阶段：PARSE_CONFIGURATION / REGISTER_BEAN
			   - 可以通过 getConfigurationPhase() 来返回当前 ConfigurationCondition对象的 Phase
		 */
		for (Condition condition : conditions) {
			ConfigurationPhase requiredPhase = null;
			if (condition instanceof ConfigurationCondition) {
				requiredPhase = ((ConfigurationCondition) condition).getConfigurationPhase();
			}
			/*
				- (requiredPhase == null || requiredPhase == phase)
				 - requiredPhase == null : 没有要求,任何阶段都可以进行评估 --> 调用condition.matches()来进行条件匹配
				 - requiredPhase!=null , requiredPhase == phase : 阶段匹配 -->  调用condition.matches()来进行条件匹配
				 - requiredPhase!=null , requiredPhase != phase : 阶段不匹配,跳过, 不会调用condition.matches()来进行匹配
				 forcus 当 condition.matches(this.context, metadata) 返回false时,代表匹配失败,也就是条件不满足,不能继续注册，所以返回true(代表should skip)
				 forcus 而条件变量是跟着某个类的，比如在类上标注相关注解和在@Bean方法上标注相关注解，所以这里和类也有关系
				 forcus 比如如果是在标注了这些注解@Component / @ComponentScan / @Import / @ImportResource的类上，标记了@Conditionxxx,那么就是配置阶段
				 forcus 否则就是Bean阶段
			 */
			if ((requiredPhase == null || requiredPhase == phase) && !condition.matches(this.context, metadata)) {
				return true;
			}
		}

		return false;
	}

	@SuppressWarnings("unchecked")
	private List<String[]> getConditionClasses(AnnotatedTypeMetadata metadata) {
		MultiValueMap<String, Object> attributes = metadata.getAllAnnotationAttributes(Conditional.class.getName(), true);
		Object values = (attributes != null ? attributes.get("value") : null);
		return (List<String[]>) (values != null ? values : Collections.emptyList());
	}

	private Condition getCondition(String conditionClassName, @Nullable ClassLoader classloader) {
		Class<?> conditionClass = ClassUtils.resolveClassName(conditionClassName, classloader);
		return (Condition) BeanUtils.instantiateClass(conditionClass);
	}


	/**
	 * Implementation of a {@link ConditionContext}.
	 */
	private static class ConditionContextImpl implements ConditionContext {

		@Nullable
		private final BeanDefinitionRegistry registry;

		@Nullable
		private final ConfigurableListableBeanFactory beanFactory;

		private final Environment environment;

		private final ResourceLoader resourceLoader;

		@Nullable
		private final ClassLoader classLoader;

		public ConditionContextImpl(@Nullable BeanDefinitionRegistry registry,
									@Nullable Environment environment, @Nullable ResourceLoader resourceLoader) {

			this.registry = registry;
			this.beanFactory = deduceBeanFactory(registry);
			this.environment = (environment != null ? environment : deduceEnvironment(registry));
			this.resourceLoader = (resourceLoader != null ? resourceLoader : deduceResourceLoader(registry));
			this.classLoader = deduceClassLoader(resourceLoader, this.beanFactory);
		}

		@Nullable
		private ConfigurableListableBeanFactory deduceBeanFactory(@Nullable BeanDefinitionRegistry source) {
			if (source instanceof ConfigurableListableBeanFactory) {
				return (ConfigurableListableBeanFactory) source;
			}
			if (source instanceof ConfigurableApplicationContext) {
				return (((ConfigurableApplicationContext) source).getBeanFactory());
			}
			return null;
		}

		private Environment deduceEnvironment(@Nullable BeanDefinitionRegistry source) {
			if (source instanceof EnvironmentCapable) {
				return ((EnvironmentCapable) source).getEnvironment();
			}
			return new StandardEnvironment();
		}

		private ResourceLoader deduceResourceLoader(@Nullable BeanDefinitionRegistry source) {
			if (source instanceof ResourceLoader) {
				return (ResourceLoader) source;
			}
			return new DefaultResourceLoader();
		}

		@Nullable
		private ClassLoader deduceClassLoader(@Nullable ResourceLoader resourceLoader,
											  @Nullable ConfigurableListableBeanFactory beanFactory) {

			if (resourceLoader != null) {
				ClassLoader classLoader = resourceLoader.getClassLoader();
				if (classLoader != null) {
					return classLoader;
				}
			}
			if (beanFactory != null) {
				return beanFactory.getBeanClassLoader();
			}
			return ClassUtils.getDefaultClassLoader();
		}

		@Override
		public BeanDefinitionRegistry getRegistry() {
			Assert.state(this.registry != null, "No BeanDefinitionRegistry available");
			return this.registry;
		}

		@Override
		@Nullable
		public ConfigurableListableBeanFactory getBeanFactory() {
			return this.beanFactory;
		}

		@Override
		public Environment getEnvironment() {
			return this.environment;
		}

		@Override
		public ResourceLoader getResourceLoader() {
			return this.resourceLoader;
		}

		@Override
		@Nullable
		public ClassLoader getClassLoader() {
			return this.classLoader;
		}
	}

}
