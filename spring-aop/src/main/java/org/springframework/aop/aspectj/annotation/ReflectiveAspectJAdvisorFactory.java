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

import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.aopalliance.aop.Advice;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.DeclareParents;
import org.aspectj.lang.annotation.Pointcut;

import org.springframework.aop.Advisor;
import org.springframework.aop.MethodBeforeAdvice;
import org.springframework.aop.aspectj.AbstractAspectJAdvice;
import org.springframework.aop.aspectj.AspectJAfterAdvice;
import org.springframework.aop.aspectj.AspectJAfterReturningAdvice;
import org.springframework.aop.aspectj.AspectJAfterThrowingAdvice;
import org.springframework.aop.aspectj.AspectJAroundAdvice;
import org.springframework.aop.aspectj.AspectJExpressionPointcut;
import org.springframework.aop.aspectj.AspectJMethodBeforeAdvice;
import org.springframework.aop.aspectj.DeclareParentsAdvisor;
import org.springframework.aop.framework.AopConfigException;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.ConvertingComparator;
import org.springframework.lang.Nullable;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.ReflectionUtils.MethodFilter;
import org.springframework.util.StringUtils;
import org.springframework.util.comparator.InstanceComparator;

/**
 * Factory that can create Spring AOP Advisors given AspectJ classes from
 * classes honoring AspectJ's annotation syntax, using reflection to invoke the
 * corresponding advice methods.
 *
 * @author Rod Johnson
 * @author Adrian Colyer
 * @author Juergen Hoeller
 * @author Ramnivas Laddad
 * @author Phillip Webb
 * @author Sam Brannen
 * @since 2.0
 */
@SuppressWarnings("serial")
public class ReflectiveAspectJAdvisorFactory extends AbstractAspectJAdvisorFactory implements Serializable {

	// Exclude @Pointcut methods
	private static final MethodFilter adviceMethodFilter = ReflectionUtils.USER_DECLARED_METHODS
			.and(method -> (AnnotationUtils.getAnnotation(method, Pointcut.class) == null));

	private static final Comparator<Method> adviceMethodComparator;

	static {
		// Note: although @After is ordered before @AfterReturning and @AfterThrowing,
		// an @After advice method will actually be invoked after @AfterReturning and
		// @AfterThrowing methods due to the fact that AspectJAfterAdvice.invoke(MethodInvocation)
		// invokes proceed() in a `try` block and only invokes the @After advice method
		// in a corresponding `finally` block.
		Comparator<Method> adviceKindComparator = new ConvertingComparator<>(
				new InstanceComparator<>(
						Around.class, Before.class, After.class, AfterReturning.class, AfterThrowing.class),
				(Converter<Method, Annotation>) method -> {
					AspectJAnnotation<?> ann = AbstractAspectJAdvisorFactory.findAspectJAnnotationOnMethod(method);
					return (ann != null ? ann.getAnnotation() : null);
				});
		Comparator<Method> methodNameComparator = new ConvertingComparator<>(Method::getName);
		adviceMethodComparator = adviceKindComparator.thenComparing(methodNameComparator);
	}


	@Nullable
	private final BeanFactory beanFactory;


	/**
	 * Create a new {@code ReflectiveAspectJAdvisorFactory}.
	 */
	public ReflectiveAspectJAdvisorFactory() {
		this(null);
	}

	/**
	 * Create a new {@code ReflectiveAspectJAdvisorFactory}, propagating the given
	 * {@link BeanFactory} to the created {@link AspectJExpressionPointcut} instances,
	 * for bean pointcut handling as well as consistent {@link ClassLoader} resolution.
	 * @param beanFactory the BeanFactory to propagate (may be {@code null}}
	 * @since 4.3.6
	 * @see AspectJExpressionPointcut#setBeanFactory
	 * @see org.springframework.beans.factory.config.ConfigurableBeanFactory#getBeanClassLoader()
	 */
	public ReflectiveAspectJAdvisorFactory(@Nullable BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}

	/*
		forcus 该方法是将 @Aspect 类解析成 Advisor 列表的核心方法
			1. 获取切面类信息
			2. 获取所有通知方法（@Before/@After/@Around 等）
			3. 遍历每个方法，生成 Advisor
			4. 处理 @DeclareParents 引入（Introduction）
	 */
	@Override
	public List<Advisor> getAdvisors(MetadataAwareAspectInstanceFactory aspectInstanceFactory) {
		/*
			1, 获取切面类信息
			比如：aspectClass = BasicAspect.class / aspectName = "basicAspect"
		 */
		Class<?> aspectClass = aspectInstanceFactory.getAspectMetadata().getAspectClass();
		String aspectName = aspectInstanceFactory.getAspectMetadata().getAspectName();
		validate(aspectClass);

		// We need to wrap the MetadataAwareAspectInstanceFactory with a decorator
		// so that it will only instantiate once.
		// forcus 包装工厂,懒加载单例装饰器(确保只会实例化一次)
		MetadataAwareAspectInstanceFactory lazySingletonAspectInstanceFactory =
				new LazySingletonAspectInstanceFactoryDecorator(aspectInstanceFactory);
		// 创建 Advisor列表
		List<Advisor> advisors = new ArrayList<>();
		// forcus 获取切面类中的所有通知方法(是经过过滤与排序的 --> Around > Before > After > AfterReturning > AfterThrowing )
		for (Method method : getAdvisorMethods(aspectClass)) {
			if (method.equals(ClassUtils.getMostSpecificMethod(method, aspectClass))) {
				// Prior to Spring Framework 5.2.7, advisors.size() was supplied as the declarationOrderInAspect
				// to getAdvisor(...) to represent the "current position" in the declared methods list.
				// However, since Java 7 the "current position" is not valid since the JDK no longer
				// returns declared methods in the order in which they are declared in the source code.
				// Thus, we now hard code the declarationOrderInAspect to 0 for all advice methods
				// discovered via reflection in order to support reliable advice ordering across JVM launches.
				// Specifically, a value of 0 aligns with the default value used in
				// AspectJPrecedenceComparator.getAspectDeclarationOrder(Advisor).
				// forcus 真正的生成 Advisor的地方
				Advisor advisor = getAdvisor(
						method, // 对应的通知方法
						lazySingletonAspectInstanceFactory,  // 包装工厂(用于获取切面类的实例)
						0,
						aspectName);
				if (advisor != null) {
					advisors.add(advisor);
				}
			}
		}

		// If it's a per target aspect, emit the dummy instantiating aspect.
		// 针对 per-this / per-target 模式的切面,切面实例是在方法调用时创建，很少使用，99%的情况下都不是烂加载，skip
		if (!advisors.isEmpty() && lazySingletonAspectInstanceFactory.getAspectMetadata().isLazilyInstantiated()) {
			Advisor instantiationAdvisor = new SyntheticInstantiationAdvisor(lazySingletonAspectInstanceFactory);
			advisors.add(0, instantiationAdvisor);
		}

		// Find introduction fields.
		// 处理 @DeclareParents，这个功能使用的非常少，几乎看不见，skip
		for (Field field : aspectClass.getDeclaredFields()) {
			Advisor advisor = getDeclareParentsAdvisor(field);
			if (advisor != null) {
				advisors.add(advisor);
			}
		}

		return advisors;
	}

	private List<Method> getAdvisorMethods(Class<?> aspectClass) {
		List<Method> methods = new ArrayList<>();
		// forcus 遍历切面类的所有方法，结果过滤后添加到 methods 列表中
		/*
			过滤器：adviceMethodFilter
					1. 只要用户声明的方法，排除 Object类的方法
					2. 排除 @Pointcut 标注的方法
				MethodFilter adviceMethodFilter = ReflectionUtils.USER_DECLARED_METHODS
						.and(method -> (AnnotationUtils.getAnnotation(method, Pointcut.class) == null));
		 */
		ReflectionUtils.doWithMethods(aspectClass, methods::add, adviceMethodFilter);
		if (methods.size() > 1) {
			/*
				forcus 按照注解类型排序
					Around > Before > After > AfterReturning > AfterThrowing
			 */
			methods.sort(adviceMethodComparator);
		}
		return methods;
	}

	/**
	 * Build a {@link org.springframework.aop.aspectj.DeclareParentsAdvisor}
	 * for the given introduction field.
	 * <p>Resulting Advisors will need to be evaluated for targets.
	 * @param introductionField the field to introspect
	 * @return the Advisor instance, or {@code null} if not an Advisor
	 */
	@Nullable
	private Advisor getDeclareParentsAdvisor(Field introductionField) {
		DeclareParents declareParents = introductionField.getAnnotation(DeclareParents.class);
		if (declareParents == null) {
			// Not an introduction field
			return null;
		}

		if (DeclareParents.class == declareParents.defaultImpl()) {
			throw new IllegalStateException("'defaultImpl' attribute must be set on DeclareParents");
		}

		return new DeclareParentsAdvisor(
				introductionField.getType(), declareParents.value(), declareParents.defaultImpl());
	}

	// forcus 关键方法,为每个方法创建Advisor (创建单个Advisor的核心方法)
	/*
		Method candidateAdviceMethod：// 候选的通知方法
		MetadataAwareAspectInstanceFactory aspectInstanceFactory,  // 切面实例工厂
		int declarationOrderInAspect,           // 在切面中的声明顺序
		String aspectName                       // 切面名称
	 */
	@Override
	@Nullable
	public Advisor getAdvisor(Method candidateAdviceMethod, MetadataAwareAspectInstanceFactory aspectInstanceFactory,
			int declarationOrderInAspect, String aspectName) {

		validate(aspectInstanceFactory.getAspectMetadata().getAspectClass());
		// forcus-1 创建切点表达式
		AspectJExpressionPointcut expressionPointcut = getPointcut(
				candidateAdviceMethod, aspectInstanceFactory.getAspectMetadata().getAspectClass());
		// 如果没有切点（不是通知方法），返回 null
		if (expressionPointcut == null) {
			return null;
		}
		// forcus 创建 Advisor
		try {
			return new InstantiationModelAwarePointcutAdvisorImpl(
					expressionPointcut, // 上面创建的切点表达式
					candidateAdviceMethod, // 需要被调用的通知方法
					this, // Advisor 工厂(ReflectiveAspectJAdvisorFactory@0x5000)
					aspectInstanceFactory,  // 切面实例工厂
					declarationOrderInAspect, // 声明顺序
					aspectName);  // 切面名称
		}
		catch (IllegalArgumentException | IllegalStateException ex) {
			if (logger.isDebugEnabled()) {
				logger.debug("Ignoring incompatible advice method: " + candidateAdviceMethod, ex);
			}
			return null;
		}
	}
	/*
		入参：
			candidateAdviceMethod: 要调用的通知方法
			candidateAspectClass：切面对应的class
	 */
	@Nullable
	private AspectJExpressionPointcut getPointcut(Method candidateAdviceMethod, Class<?> candidateAspectClass) {
		// forcus 在方法上查找 AspectJ 注解(@Around/@Before/@After/@AfterReturning/@AfterThrowing/@PointCut等注解)
		/*
			AspectJAnnotation 对象结构
				 private final A annotation;                    // 原始注解（如 @Before("xxx")）
				 private final AspectJAnnotationType annotationType;  // AtBefore / ... / ...
				 private final String pointcutExpression;       // "execution(* ...)"
				 private final String argumentNames;            // 参数名（如 "arg1,arg2"），对于 @Before()来说
		 */
		AspectJAnnotation<?> aspectJAnnotation =
				AbstractAspectJAdvisorFactory.findAspectJAnnotationOnMethod(candidateAdviceMethod);
		// 如果没有注解，说明不是通知方法，返回null
		if (aspectJAnnotation == null) {
			return null;
		}
		// forcus 创建切点表达式对象，该对象是spring-aop中,最核心的切点实现类
		// forcus 核心职责为：根据AspectJ表达式来判断某个方法是否是需要被拦截
		AspectJExpressionPointcut ajexp =
				new AspectJExpressionPointcut(
						candidateAspectClass, // 切面类(用于声明切点所在的类,通常就是切面类)
						new String[0], // 参数名(空数组)
						new Class<?>[0]); // 参数类型(空数组)
		// 设置切点表达式
		// eg: execution(* com.debug.aop_demo_1.CalculatorService.*(..))
		ajexp.setExpression(aspectJAnnotation.getPointcutExpression());
		if (this.beanFactory != null) {
			ajexp.setBeanFactory(this.beanFactory);
		}
		return ajexp;
	}

	/*
		candidateAdviceMethod：通知方法
		expressionPointcut：切点表达式
		aspectInstanceFactory：切面实例工厂
		declarationOrder：声明顺序
		aspectName：切面名称
	 */
	@Override
	@Nullable
	public Advice getAdvice(Method candidateAdviceMethod, AspectJExpressionPointcut expressionPointcut,
			MetadataAwareAspectInstanceFactory aspectInstanceFactory, int declarationOrder, String aspectName) {

		Class<?> candidateAspectClass = aspectInstanceFactory.getAspectMetadata().getAspectClass();
		validate(candidateAspectClass);
		// 为什么需要再校验一次呢？因为getAdvice()是public方法,可能会被外界调用,所以再次校验
		// 确保当前处理的方法一定是通知方法
		AspectJAnnotation<?> aspectJAnnotation =
				AbstractAspectJAdvisorFactory.findAspectJAnnotationOnMethod(candidateAdviceMethod);
		if (aspectJAnnotation == null) {
			return null;
		}

		// If we get here, we know we have an AspectJ method.
		// Check that it's an AspectJ-annotated class
		// 确保通知方法声明在@Aspect类中,否则抛出异常
		if (!isAspect(candidateAspectClass)) {
			throw new AopConfigException("Advice must be declared inside an aspect type: " +
					"Offending method '" + candidateAdviceMethod + "' in class [" +
					candidateAspectClass.getName() + "]");
		}

		if (logger.isDebugEnabled()) {
			logger.debug("Found AspectJ method: " + candidateAdviceMethod);
		}

		// ------ forcus 下面就是创建的核心逻辑了 ------
		AbstractAspectJAdvice springAdvice;
		/*
			forcus 注意,被@Pointcut标注的方法,不会创建为advice
			然后就是根据不同的类型创建不同的Advice,传入构造方法的参数为：
				candidateAdviceMethod ：
				expressionPointcut：
				aspectInstanceFactory：
		 */
		switch (aspectJAnnotation.getAnnotationType()) {
			case AtPointcut:
				if (logger.isDebugEnabled()) {
					logger.debug("Processing pointcut '" + candidateAdviceMethod.getName() + "'");
				}
				return null;
			case AtAround:
				springAdvice = new AspectJAroundAdvice(
						candidateAdviceMethod, expressionPointcut, aspectInstanceFactory);
				break;
			case AtBefore:
				springAdvice = new AspectJMethodBeforeAdvice(
						candidateAdviceMethod, expressionPointcut, aspectInstanceFactory);
				break;
			case AtAfter:
				springAdvice = new AspectJAfterAdvice(
						candidateAdviceMethod, expressionPointcut, aspectInstanceFactory);
				break;
			case AtAfterReturning:
				springAdvice = new AspectJAfterReturningAdvice(
						candidateAdviceMethod, expressionPointcut, aspectInstanceFactory);
				AfterReturning afterReturningAnnotation = (AfterReturning) aspectJAnnotation.getAnnotation();
				if (StringUtils.hasText(afterReturningAnnotation.returning())) {
					springAdvice.setReturningName(afterReturningAnnotation.returning());
				}
				break;
			case AtAfterThrowing:
				springAdvice = new AspectJAfterThrowingAdvice(
						candidateAdviceMethod, expressionPointcut, aspectInstanceFactory);
				AfterThrowing afterThrowingAnnotation = (AfterThrowing) aspectJAnnotation.getAnnotation();
				if (StringUtils.hasText(afterThrowingAnnotation.throwing())) {
					springAdvice.setThrowingName(afterThrowingAnnotation.throwing());
				}
				break;
			default:
				throw new UnsupportedOperationException(
						"Unsupported advice type on method: " + candidateAdviceMethod);
		}
		// forcus 配置Advice的其他基本属性
		// Now to configure the advice...
		springAdvice.setAspectName(aspectName); // 设置切面名称 - eg:“basicAspect”
		springAdvice.setDeclarationOrder(declarationOrder); // 设置声明顺序
		/*
			forcus 通过 ParameterNameDiscoverer 获取通知方法的参数名
			比如： ["joinPoint"]

		 */
		String[] argNames = this.parameterNameDiscoverer.getParameterNames(candidateAdviceMethod);
		if (argNames != null) {
			springAdvice.setArgumentNamesFromStringArray(argNames);
		}
		// forcus 参数绑定,最复杂的部分,具体的细节展示不深入了解了，主要就是计算 参数的位置，以及 相关映射之类的
		/*
			核心职责是：提前计算好通知方法的每个参数应该从哪里获取值，把运行时开销降到最低。
		 */
		springAdvice.calculateArgumentBindings();

		return springAdvice;
	}


	/**
	 * Synthetic advisor that instantiates the aspect.
	 * Triggered by per-clause pointcut on non-singleton aspect.
	 * The advice has no effect.
	 */
	@SuppressWarnings("serial")
	protected static class SyntheticInstantiationAdvisor extends DefaultPointcutAdvisor {

		public SyntheticInstantiationAdvisor(final MetadataAwareAspectInstanceFactory aif) {
			super(aif.getAspectMetadata().getPerClausePointcut(), (MethodBeforeAdvice)
					(method, args, target) -> aif.getAspectInstance());
		}
	}

}
