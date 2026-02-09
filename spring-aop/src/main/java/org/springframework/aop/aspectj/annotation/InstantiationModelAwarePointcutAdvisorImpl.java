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

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.lang.reflect.Method;

import org.aopalliance.aop.Advice;
import org.aspectj.lang.reflect.PerClauseKind;

import org.springframework.aop.Pointcut;
import org.springframework.aop.aspectj.AspectJExpressionPointcut;
import org.springframework.aop.aspectj.AspectJPrecedenceInformation;
import org.springframework.aop.aspectj.InstantiationModelAwarePointcutAdvisor;
import org.springframework.aop.aspectj.annotation.AbstractAspectJAdvisorFactory.AspectJAnnotation;
import org.springframework.aop.support.DynamicMethodMatcherPointcut;
import org.springframework.aop.support.Pointcuts;
import org.springframework.lang.Nullable;

/**
 * Internal implementation of AspectJPointcutAdvisor.
 *
 * <p>Note that there will be one instance of this advisor for each target method.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @since 2.0
 */
@SuppressWarnings("serial")
final class InstantiationModelAwarePointcutAdvisorImpl
		implements InstantiationModelAwarePointcutAdvisor, AspectJPrecedenceInformation, Serializable {
	/*
		含义: 空的 Advice 占位符
		作用: 当无法创建真正的 Advice 时返回
	 */
	private static final Advice EMPTY_ADVICE = new Advice() {};

	/*
		从注解中解析出来的原始切点表达式对象
		比如@Before("calculatorMethods()") -> expression = "calculatorMethods()"
	 */
	private final AspectJExpressionPointcut declaredPointcut;
	/*
		含义：声明通知方法的类
		eg:com.debug.aop_demo_1.BasicAspec
	 */
	private final Class<?> declaringClass;
	/*
		含义: 通知方法名
		示例: "around", "before", "after" 等
	 */
	private final String methodName;
	/*
		含义: 通知方法的参数类型数组
		示例: [ProceedingJoinPoint.class] 或 [JoinPoint.class]
	 */
	private final Class<?>[] parameterTypes;
	/*
		含义: 通知方法的反射对象
		作用: 创建 Advice 时传入，最终用于反射调用
		示例: BasicAspect.around(ProceedingJoinPoint)
	 */
	private transient Method aspectJAdviceMethod;
	/*
		含义: Advice 工厂
		作用: 调用 getAdvice() 创建 Advice 对象
		示例: ReflectiveAspectJAdvisorFactory
	 */
	private final AspectJAdvisorFactory aspectJAdvisorFactory;
	/*
		含义: 切面实例工厂
		作用: 获取切面实例、元数据、优先级
		示例: LazySingletonAspectInstanceFactoryDecorator
	 */
	private final MetadataAwareAspectInstanceFactory aspectInstanceFactory;
	/*
		含义: 在切面内的声明顺序
		作用: 同切面内多个通知的排序
	 */
	private final int declarationOrder;
	/*
		含义: 切面 Bean 的名称
		示例: "basicAspect"
	 */
	private final String aspectName;
	/*
		Spring AOP 实际使用的切点,用于匹配目标方法
		非懒加载时 = declaredPointcut (通常都是非懒加载)
	 */
	private final Pointcut pointcut;
	// 是否懒加载 Advice(单例切面 = false,99.9%是false)
	private final boolean lazy;
	/*
		forcus
			含义：已经实例化的通知对象
			作用：执行通知逻辑的核心对象
				1. @Around -> AspectJAroundAdvice -> MethodInterceptor -> invoke(MethodInvocation)
				2. @Before -> AspectJMethodBeforeAdvice -> MethodBeforeAdvice -> before(MethodInvocation)
				3. @After -> AspectJAfterAdvice -> MethodInterceptor -> invoke(MethodInvocation)
				4. @AfterReturning -> AspectJAfterReturningAdvice -> AfterReturningAdvice -> afterReturning(MethodInvocation)
				5. @AfterThrowing -> AspectJAfterThrowingAdvice -> ThrowsAdvice -> afterThrowing(MethodInvocation)
	 */
	@Nullable
	private Advice instantiatedAdvice;
	/*
		 含义: 是否是前置通知
		 作用: 懒计算，用于排序
	 */
	@Nullable
	private Boolean isBeforeAdvice;
	/*
		含义: 是否是后置通知
		作用: 懒计算，用于排序
	 */
	@Nullable
	private Boolean isAfterAdvice;

	/*
		构造方法可以分为下面阶段：
			1. 保存基本属性
			2. 判断是否是懒加载(99.9%的情况下为非懒加载)：
	 */
	public InstantiationModelAwarePointcutAdvisorImpl(AspectJExpressionPointcut declaredPointcut,
			Method aspectJAdviceMethod, AspectJAdvisorFactory aspectJAdvisorFactory,
			MetadataAwareAspectInstanceFactory aspectInstanceFactory, int declarationOrder, String aspectName) {
		// === 一些属性的保存 ===
		this.declaredPointcut = declaredPointcut; // 保存切点表达式对象,用于匹配目标方法

		this.declaringClass = aspectJAdviceMethod.getDeclaringClass(); // 切面类class
		this.methodName = aspectJAdviceMethod.getName(); // 要调用的通知方法名称
		this.parameterTypes = aspectJAdviceMethod.getParameterTypes(); // 参数类型 -> [ProceedingJoinPoint.class]
		this.aspectJAdviceMethod = aspectJAdviceMethod; // 通知方法的Method引用，创建Advice时传入,最终通过反射调用
		this.aspectJAdvisorFactory = aspectJAdvisorFactory; // 调用其 getAdvice()方法来创建具体的Advice
		this.aspectInstanceFactory = aspectInstanceFactory; // 用于获取切面实例
		this.declarationOrder = declarationOrder; // 同一切面内多个通知的排序依据
		this.aspectName = aspectName; // 切面名称

		// 懒加载(skip)
		if (aspectInstanceFactory.getAspectMetadata().isLazilyInstantiated()) {
			// Static part of the pointcut is a lazy type.
			Pointcut preInstantiationPointcut = Pointcuts.union(
					aspectInstanceFactory.getAspectMetadata().getPerClausePointcut(), this.declaredPointcut);

			// Make it dynamic: must mutate from pre-instantiation to post-instantiation state.
			// If it's not a dynamic pointcut, it may be optimized out
			// by the Spring AOP infrastructure after the first evaluation.
			this.pointcut = new PerTargetInstantiationModelPointcut(
					this.declaredPointcut, preInstantiationPointcut, aspectInstanceFactory);
			this.lazy = true;
		}
		// forcus 非懒加载(99.9%的情况都是走这里的)
		else {
			// A singleton aspect.
			/*
				直接把声明的切点表达式作为 Spring AOP 实际使用的切点。没有任何额外包装。
				后续 Spring 在创建代理时，会调用 getPointcut() 获取此切点，
				用它来匹配目标类和方法。
			 */
			this.pointcut = this.declaredPointcut;
			this.lazy = false; // 标记为懒加载
			/*
				forcus 这个是 真正执行通知逻辑的对象，它将在业务代码中写的通知方法 包装为 spring aop 能识别和调用的方式
				spring 需要的是 AspectJAroundAdvice(实现了 MethodInterceptor )
				5种 Advice:
					@Around -> AspectJAroundAdvice -> MethodInterceptor -> invoke(MethodInvocation)
					@Before -> AspectJMethodBeforeAdvice -> MethodBeforeAdvice -> before(MethodInvocation)
					@After -> AspectJAfterAdvice -> MethodInterceptor -> invoke(MethodInvocation)
					@AfterReturning -> AspectJAfterReturningAdvice -> AfterReturningAdvice -> afterReturning(MethodInvocation)
					@AfterThrowing -> AspectJAfterThrowingAdvice -> MethodInterceptor -> invoke(MethodInvocation)
			 */
			this.instantiatedAdvice = instantiateAdvice(this.declaredPointcut);
		}
	}


	/**
	 * The pointcut for Spring AOP to use.
	 * Actual behaviour of the pointcut will change depending on the state of the advice.
	 */
	@Override
	public Pointcut getPointcut() {
		return this.pointcut;
	}

	@Override
	public boolean isLazy() {
		return this.lazy;
	}

	@Override
	public synchronized boolean isAdviceInstantiated() {
		return (this.instantiatedAdvice != null);
	}

	/**
	 * Lazily instantiate advice if necessary.
	 */
	@Override
	public synchronized Advice getAdvice() {
		if (this.instantiatedAdvice == null) {
			this.instantiatedAdvice = instantiateAdvice(this.declaredPointcut);
		}
		return this.instantiatedAdvice;
	}
	// forcus 创建各种Advice - getAdvice()
	private Advice instantiateAdvice(AspectJExpressionPointcut pointcut) {
		Advice advice = this.aspectJAdvisorFactory.getAdvice(this.aspectJAdviceMethod, pointcut,
				this.aspectInstanceFactory, this.declarationOrder, this.aspectName);
		return (advice != null ? advice : EMPTY_ADVICE);
	}

	/**
	 * This is only of interest for Spring AOP: AspectJ instantiation semantics
	 * are much richer. In AspectJ terminology, all a return of {@code true}
	 * means here is that the aspect is not a SINGLETON.
	 */
	@Override
	public boolean isPerInstance() {
		return (getAspectMetadata().getAjType().getPerClause().getKind() != PerClauseKind.SINGLETON);
	}

	/**
	 * Return the AspectJ AspectMetadata for this advisor.
	 */
	public AspectMetadata getAspectMetadata() {
		return this.aspectInstanceFactory.getAspectMetadata();
	}

	public MetadataAwareAspectInstanceFactory getAspectInstanceFactory() {
		return this.aspectInstanceFactory;
	}

	public AspectJExpressionPointcut getDeclaredPointcut() {
		return this.declaredPointcut;
	}

	@Override
	public int getOrder() {
		return this.aspectInstanceFactory.getOrder();
	}

	@Override
	public String getAspectName() {
		return this.aspectName;
	}

	@Override
	public int getDeclarationOrder() {
		return this.declarationOrder;
	}

	@Override
	public boolean isBeforeAdvice() {
		if (this.isBeforeAdvice == null) {
			determineAdviceType();
		}
		return this.isBeforeAdvice;
	}

	@Override
	public boolean isAfterAdvice() {
		if (this.isAfterAdvice == null) {
			determineAdviceType();
		}
		return this.isAfterAdvice;
	}

	/**
	 * Duplicates some logic from getAdvice, but importantly does not force
	 * creation of the advice.
	 */
	private void determineAdviceType() {
		AspectJAnnotation<?> aspectJAnnotation =
				AbstractAspectJAdvisorFactory.findAspectJAnnotationOnMethod(this.aspectJAdviceMethod);
		if (aspectJAnnotation == null) {
			this.isBeforeAdvice = false;
			this.isAfterAdvice = false;
		}
		else {
			switch (aspectJAnnotation.getAnnotationType()) {
				case AtPointcut:
				case AtAround:
					this.isBeforeAdvice = false;
					this.isAfterAdvice = false;
					break;
				case AtBefore:
					this.isBeforeAdvice = true;
					this.isAfterAdvice = false;
					break;
				case AtAfter:
				case AtAfterReturning:
				case AtAfterThrowing:
					this.isBeforeAdvice = false;
					this.isAfterAdvice = true;
					break;
			}
		}
	}


	private void readObject(ObjectInputStream inputStream) throws IOException, ClassNotFoundException {
		inputStream.defaultReadObject();
		try {
			this.aspectJAdviceMethod = this.declaringClass.getMethod(this.methodName, this.parameterTypes);
		}
		catch (NoSuchMethodException ex) {
			throw new IllegalStateException("Failed to find advice method on deserialization", ex);
		}
	}

	@Override
	public String toString() {
		return "InstantiationModelAwarePointcutAdvisor: expression [" + getDeclaredPointcut().getExpression() +
				"]; advice method [" + this.aspectJAdviceMethod + "]; perClauseKind=" +
				this.aspectInstanceFactory.getAspectMetadata().getAjType().getPerClause().getKind();
	}


	/**
	 * Pointcut implementation that changes its behaviour when the advice is instantiated.
	 * Note that this is a <i>dynamic</i> pointcut; otherwise it might be optimized out
	 * if it does not at first match statically.
	 */
	private static final class PerTargetInstantiationModelPointcut extends DynamicMethodMatcherPointcut {

		private final AspectJExpressionPointcut declaredPointcut;

		private final Pointcut preInstantiationPointcut;

		@Nullable
		private LazySingletonAspectInstanceFactoryDecorator aspectInstanceFactory;

		public PerTargetInstantiationModelPointcut(AspectJExpressionPointcut declaredPointcut,
				Pointcut preInstantiationPointcut, MetadataAwareAspectInstanceFactory aspectInstanceFactory) {

			this.declaredPointcut = declaredPointcut;
			this.preInstantiationPointcut = preInstantiationPointcut;
			if (aspectInstanceFactory instanceof LazySingletonAspectInstanceFactoryDecorator) {
				this.aspectInstanceFactory = (LazySingletonAspectInstanceFactoryDecorator) aspectInstanceFactory;
			}
		}

		@Override
		public boolean matches(Method method, Class<?> targetClass) {
			// We're either instantiated and matching on declared pointcut,
			// or uninstantiated matching on either pointcut...
			return (isAspectMaterialized() && this.declaredPointcut.matches(method, targetClass)) ||
					this.preInstantiationPointcut.getMethodMatcher().matches(method, targetClass);
		}

		@Override
		public boolean matches(Method method, Class<?> targetClass, Object... args) {
			// This can match only on declared pointcut.
			return (isAspectMaterialized() && this.declaredPointcut.matches(method, targetClass, args));
		}

		private boolean isAspectMaterialized() {
			return (this.aspectInstanceFactory == null || this.aspectInstanceFactory.isMaterialized());
		}
	}

}
