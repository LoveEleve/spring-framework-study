/*
 * Copyright 2002-2023 the original author or authors.
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

package org.springframework.transaction.interceptor;

import java.lang.reflect.Method;
import java.util.Properties;
import java.util.concurrent.ConcurrentMap;

import io.vavr.control.Try;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.reactive.AwaitKt;
import kotlinx.coroutines.reactive.ReactiveFlowKt;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.BeanFactoryAnnotationUtils;
import org.springframework.core.CoroutinesUtils;
import org.springframework.core.KotlinDetector;
import org.springframework.core.MethodParameter;
import org.springframework.core.NamedThreadLocal;
import org.springframework.core.ReactiveAdapter;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.lang.Nullable;
import org.springframework.transaction.NoTransactionException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.ReactiveTransaction;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.TransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.reactive.TransactionContextManager;
import org.springframework.transaction.support.CallbackPreferringPlatformTransactionManager;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ConcurrentReferenceHashMap;
import org.springframework.util.StringUtils;

/**
 * Base class for transactional aspects, such as the {@link TransactionInterceptor}
 * or an AspectJ aspect.
 *
 * <p>This enables the underlying Spring transaction infrastructure to be used easily
 * to implement an aspect for any aspect system.
 *
 * <p>Subclasses are responsible for calling methods in this class in the correct order.
 *
 * <p>If no transaction name has been specified in the {@link TransactionAttribute},
 * the exposed name will be the {@code fully-qualified class name + "." + method name}
 * (by default).
 *
 * <p>Uses the <b>Strategy</b> design pattern. A {@link PlatformTransactionManager} or
 * {@link ReactiveTransactionManager} implementation will perform the actual transaction
 * management, and a {@link TransactionAttributeSource} (e.g. annotation-based) is used
 * for determining transaction definitions for a particular class or method.
 *
 * <p>A transaction aspect is serializable if its {@code TransactionManager} and
 * {@code TransactionAttributeSource} are serializable.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Stéphane Nicoll
 * @author Sam Brannen
 * @author Mark Paluch
 * @author Sebastien Deleuze
 * @since 1.1
 * @see PlatformTransactionManager
 * @see ReactiveTransactionManager
 * @see #setTransactionManager
 * @see #setTransactionAttributes
 * @see #setTransactionAttributeSource
 */
public abstract class TransactionAspectSupport implements BeanFactoryAware, InitializingBean {

	// NOTE: This class must not implement Serializable because it serves as base
	// class for AspectJ aspects (which are not allowed to implement Serializable)!


	/**
	 * Key to use to store the default transaction manager.
	 */
	private static final Object DEFAULT_TRANSACTION_MANAGER_KEY = new Object();

	private static final String COROUTINES_FLOW_CLASS_NAME = "kotlinx.coroutines.flow.Flow";

	/**
	 * Vavr library present on the classpath?
	 */
	private static final boolean vavrPresent = ClassUtils.isPresent(
			"io.vavr.control.Try", TransactionAspectSupport.class.getClassLoader());

	/**
	 * Reactive Streams API present on the classpath?
	 */
	private static final boolean reactiveStreamsPresent =
			ClassUtils.isPresent("org.reactivestreams.Publisher", TransactionAspectSupport.class.getClassLoader());

	/**
	 * Holder to support the {@code currentTransactionStatus()} method,
	 * and to support communication between different cooperating advices
	 * (e.g. before and after advice) if the aspect involves more than a
	 * single method (as will be the case for around advice).
	 */
	// forcus 用来存储当前线程对应的 TransactionInfo(也即上一个事务方法的 txInfo,可能为null)
	private static final ThreadLocal<TransactionInfo> transactionInfoHolder =
			new NamedThreadLocal<>("Current aspect-driven transaction");


	/**
	 * Subclasses can use this to return the current TransactionInfo.
	 * Only subclasses that cannot handle all operations in one method,
	 * such as an AspectJ aspect involving distinct before and after advice,
	 * need to use this mechanism to get at the current TransactionInfo.
	 * An around advice such as an AOP Alliance MethodInterceptor can hold a
	 * reference to the TransactionInfo throughout the aspect method.
	 * <p>A TransactionInfo will be returned even if no transaction was created.
	 * The {@code TransactionInfo.hasTransaction()} method can be used to query this.
	 * <p>To find out about specific transaction characteristics, consider using
	 * TransactionSynchronizationManager's {@code isSynchronizationActive()}
	 * and/or {@code isActualTransactionActive()} methods.
	 * @return the TransactionInfo bound to this thread, or {@code null} if none
	 * @see TransactionInfo#hasTransaction()
	 * @see org.springframework.transaction.support.TransactionSynchronizationManager#isSynchronizationActive()
	 * @see org.springframework.transaction.support.TransactionSynchronizationManager#isActualTransactionActive()
	 */
	@Nullable
	protected static TransactionInfo currentTransactionInfo() throws NoTransactionException {
		return transactionInfoHolder.get();
	}

	/**
	 * Return the transaction status of the current method invocation.
	 * Mainly intended for code that wants to set the current transaction
	 * rollback-only but not throw an application exception.
	 * @throws NoTransactionException if the transaction info cannot be found,
	 * because the method was invoked outside an AOP invocation context
	 */
	public static TransactionStatus currentTransactionStatus() throws NoTransactionException {
		TransactionInfo info = currentTransactionInfo();
		if (info == null || info.transactionStatus == null) {
			throw new NoTransactionException("No transaction aspect-managed TransactionStatus in scope");
		}
		return info.transactionStatus;
	}


	protected final Log logger = LogFactory.getLog(getClass());

	@Nullable
	private final ReactiveAdapterRegistry reactiveAdapterRegistry;

	@Nullable
	private String transactionManagerBeanName;

	@Nullable
	private TransactionManager transactionManager;

	@Nullable
	private TransactionAttributeSource transactionAttributeSource;

	@Nullable
	private BeanFactory beanFactory;

	private final ConcurrentMap<Object, TransactionManager> transactionManagerCache =
			new ConcurrentReferenceHashMap<>(4);

	private final ConcurrentMap<Method, ReactiveTransactionSupport> transactionSupportCache =
			new ConcurrentReferenceHashMap<>(1024);


	protected TransactionAspectSupport() {
		if (reactiveStreamsPresent) {
			this.reactiveAdapterRegistry = ReactiveAdapterRegistry.getSharedInstance();
		}
		else {
			this.reactiveAdapterRegistry = null;
		}
	}


	/**
	 * Specify the name of the default transaction manager bean.
	 * <p>This can either point to a traditional {@link PlatformTransactionManager} or a
	 * {@link ReactiveTransactionManager} for reactive transaction management.
	 */
	public void setTransactionManagerBeanName(@Nullable String transactionManagerBeanName) {
		this.transactionManagerBeanName = transactionManagerBeanName;
	}

	/**
	 * Return the name of the default transaction manager bean.
	 */
	@Nullable
	protected final String getTransactionManagerBeanName() {
		return this.transactionManagerBeanName;
	}

	/**
	 * Specify the <em>default</em> transaction manager to use to drive transactions.
	 * <p>This can either be a traditional {@link PlatformTransactionManager} or a
	 * {@link ReactiveTransactionManager} for reactive transaction management.
	 * <p>The default transaction manager will be used if a <em>qualifier</em>
	 * has not been declared for a given transaction or if an explicit name for the
	 * default transaction manager bean has not been specified.
	 * @see #setTransactionManagerBeanName
	 */
	public void setTransactionManager(@Nullable TransactionManager transactionManager) {
		this.transactionManager = transactionManager;
	}

	/**
	 * Return the default transaction manager, or {@code null} if unknown.
	 * <p>This can either be a traditional {@link PlatformTransactionManager} or a
	 * {@link ReactiveTransactionManager} for reactive transaction management.
	 */
	@Nullable
	public TransactionManager getTransactionManager() {
		return this.transactionManager;
	}

	/**
	 * Set properties with method names as keys and transaction attribute
	 * descriptors (parsed via TransactionAttributeEditor) as values:
	 * e.g. key = "myMethod", value = "PROPAGATION_REQUIRED,readOnly".
	 * <p>Note: Method names are always applied to the target class,
	 * no matter if defined in an interface or the class itself.
	 * <p>Internally, a NameMatchTransactionAttributeSource will be
	 * created from the given properties.
	 * @see #setTransactionAttributeSource
	 * @see TransactionAttributeEditor
	 * @see NameMatchTransactionAttributeSource
	 */
	public void setTransactionAttributes(Properties transactionAttributes) {
		NameMatchTransactionAttributeSource tas = new NameMatchTransactionAttributeSource();
		tas.setProperties(transactionAttributes);
		this.transactionAttributeSource = tas;
	}

	/**
	 * Set multiple transaction attribute sources which are used to find transaction
	 * attributes. Will build a CompositeTransactionAttributeSource for the given sources.
	 * @see CompositeTransactionAttributeSource
	 * @see MethodMapTransactionAttributeSource
	 * @see NameMatchTransactionAttributeSource
	 * @see org.springframework.transaction.annotation.AnnotationTransactionAttributeSource
	 */
	public void setTransactionAttributeSources(TransactionAttributeSource... transactionAttributeSources) {
		this.transactionAttributeSource = new CompositeTransactionAttributeSource(transactionAttributeSources);
	}

	/**
	 * Set the transaction attribute source which is used to find transaction
	 * attributes. If specifying a String property value, a PropertyEditor
	 * will create a MethodMapTransactionAttributeSource from the value.
	 * @see TransactionAttributeSourceEditor
	 * @see MethodMapTransactionAttributeSource
	 * @see NameMatchTransactionAttributeSource
	 * @see org.springframework.transaction.annotation.AnnotationTransactionAttributeSource
	 */
	public void setTransactionAttributeSource(@Nullable TransactionAttributeSource transactionAttributeSource) {
		this.transactionAttributeSource = transactionAttributeSource;
	}

	/**
	 * Return the transaction attribute source.
	 */
	@Nullable
	public TransactionAttributeSource getTransactionAttributeSource() {
		return this.transactionAttributeSource;
	}

	/**
	 * Set the BeanFactory to use for retrieving {@code TransactionManager} beans.
	 */
	@Override
	public void setBeanFactory(@Nullable BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}

	/**
	 * Return the BeanFactory to use for retrieving {@code TransactionManager} beans.
	 */
	@Nullable
	protected final BeanFactory getBeanFactory() {
		return this.beanFactory;
	}

	/**
	 * Check that required properties were set.
	 */
	@Override
	public void afterPropertiesSet() {
		if (getTransactionManager() == null && this.beanFactory == null) {
			throw new IllegalStateException(
					"Set the 'transactionManager' property or make sure to run within a BeanFactory " +
					"containing a TransactionManager bean!");
		}
		if (getTransactionAttributeSource() == null) {
			throw new IllegalStateException(
					"Either 'transactionAttributeSource' or 'transactionAttributes' is required: " +
					"If there are no transactional methods, then don't use a transaction aspect.");
		}
	}


	/**
	 * General delegate for around-advice-based subclasses, delegating to several other template
	 * methods on this class. Able to handle {@link CallbackPreferringPlatformTransactionManager}
	 * as well as regular {@link PlatformTransactionManager} implementations and
	 * {@link ReactiveTransactionManager} implementations for reactive return types.
	 * @param method the Method being invoked
	 * @param targetClass the target class that we're invoking the method on
	 * @param invocation the callback to use for proceeding with the target invocation
	 * @return the return value of the method, if any
	 * @throws Throwable propagated from the target invocation
	 */
	// forcus 对于事务方法来说,只有这一个Advice,这里就是事务方法的起点
	/*
		method: 目标方法
		targetClass:目标类（非代理类）
		invocation:回调接口，封装了"继续执行拦截器链"的能力
		forcus 虽然这个方法很长，但是是分为了3个分支的
			1. 公共参数获取：txAddr,tm
			2. 分支-1:响应式事务 - webflux
			3. 分支-2：标注事务 - 绝大部分场景走这个分支 「在这里只关注这个场景」
			4. 分支-3：回调式事务 - WebSphere 等特殊容器
	 */
	@Nullable
	protected Object invokeWithinTransaction(Method method, @Nullable Class<?> targetClass,
			final InvocationCallback invocation) throws Throwable {

		// If the transaction attribute is null, the method is non-transactional.
		// forcus-1 获取"PointCut"，用来解析类/方法/接口上的@Transaction注解的,当然通常是标注在方法上的 -- 在这里是 AnnotationTransactionAttributeSource
		// 并且最终返回的就是 RuleBasedTransactionAttribute 对象
		// 我们在@Transaction注解中配置的各种属性最终就被解析封装为这个对象 -- 也就是下面的 txAttr
		TransactionAttributeSource tas = getTransactionAttributeSource();
		final TransactionAttribute txAttr = (tas != null ? tas.getTransactionAttribute(method, targetClass) : null);
		// forcus-2 三级查找策略：但是通常是最后一级,也即是按照类型从spring容器中获取，在这里是 DataSourceTransactionManager -- tm
		final TransactionManager tm = determineTransactionManager(txAttr);

		/*-----------
				上面是公共参数准备,不管调用的是什么类型的传播行为对应的事务方法 都是一样的操作，分歧就在下面
		 		但是目前只关系 REQUIRED 和 REQUIRED_NEW 的场景
		 ------------*/
		// 分支-1 Kotlin 协程 + WebFlux 响应式事务处理 skip
		if (this.reactiveAdapterRegistry != null && tm instanceof ReactiveTransactionManager) {
			boolean isSuspendingFunction = KotlinDetector.isSuspendingFunction(method);
			boolean hasSuspendingFlowReturnType = isSuspendingFunction &&
					COROUTINES_FLOW_CLASS_NAME.equals(new MethodParameter(method, -1).getParameterType().getName());
			if (isSuspendingFunction && !(invocation instanceof CoroutinesInvocationCallback)) {
				throw new IllegalStateException("Coroutines invocation not supported: " + method);
			}
			CoroutinesInvocationCallback corInv = (isSuspendingFunction ? (CoroutinesInvocationCallback) invocation : null);

			ReactiveTransactionSupport txSupport = this.transactionSupportCache.computeIfAbsent(method, key -> {
				Class<?> reactiveType =
						(isSuspendingFunction ? (hasSuspendingFlowReturnType ? Flux.class : Mono.class) : method.getReturnType());
				ReactiveAdapter adapter = this.reactiveAdapterRegistry.getAdapter(reactiveType);
				if (adapter == null) {
					throw new IllegalStateException("Cannot apply reactive transaction to non-reactive return type [" +
							method.getReturnType() + "] with specified transaction manager: " + tm);
				}
				return new ReactiveTransactionSupport(adapter);
			});

			InvocationCallback callback = invocation;
			if (corInv != null) {
				callback = () -> CoroutinesUtils.invokeSuspendingFunction(method, corInv.getTarget(), corInv.getArguments());
			}
			Object result = txSupport.invokeWithinTransaction(method, targetClass, callback, txAttr, (ReactiveTransactionManager) tm);
			if (corInv != null) {
				Publisher<?> pr = (Publisher<?>) result;
				return (hasSuspendingFlowReturnType ? KotlinDelegate.asFlow(pr) :
						KotlinDelegate.awaitSingleOrNull(pr, corInv.getContinuation()));
			}
			return result;
		}

		/*------- forcus-3 普通事务分支 - 目前只关注这个 --------*/
		// 安全类型转换
		PlatformTransactionManager ptm = asPlatformTransactionManager(tm);
		// 生成方法标识字符串，用于日志和事务名称，比如 "com.debug.aop_demo_tx_1.UserService.save"
		final String joinpointIdentification = methodIdentification(method, targetClass, txAttr);

		if (txAttr == null || !(ptm instanceof CallbackPreferringPlatformTransactionManager)) {
			// Standard transaction demarcation with getTransaction and commit/rollback calls.
			// forcus 核心是调用 ptm.getTransaction(txAttr) 开启事务，然后封装成 TransactionInfo 绑定到 ThreadLocal 栈
			// forcus 目前只关系 REQUIRED 和 REQUIRED_NEW
			TransactionInfo txInfo = createTransactionIfNecessary(ptm, txAttr, joinpointIdentification);

			Object retVal;
			try {
				// This is an around advice: Invoke the next interceptor in the chain.
				// This will normally result in a target object being invoked.
				// forcus 继续调用拦截器链的下一个拦截器，在事务aop中只有一个(就是 TransactionInterceptor)
				// 下一个就是目标方法了，比如save()
				// 这里获取到的就是业务方法的返回值
				retVal = invocation.proceedWithInvocation();
			}
			catch (Throwable ex) {
				// forcus 调用业务方法出现了异常 - 前提是业务方法没有“吞掉”异常
				// target invocation exception
				completeTransactionAfterThrowing(txInfo, ex);
				throw ex; // forcus 在这里会继续向上抛
			}
			finally {
				// forcus 在finally代码块中,不管业务代码是 正常返回 / 抛异常 都一定会执行
				/*
					注意一点：这里的 cleanup 在 下面的 commitTransactionAfterReturning(txInfo) 之前，这合理吗？
						- 合理，因为 commit 不依赖 transactionInfoHolder 这个 ThreadLocal，它直接使用 txInfo 局部变量。
						在这里做的事情就是将 transactionInfoHolder 还原
				 */
				cleanupTransactionInfo(txInfo);
			}
			// 用于 Vavr ，暂时不关心
			if (retVal != null && vavrPresent && VavrDelegate.isVavrTry(retVal)) {
				// Set rollback-only in case of Vavr failure matching our rollback rules...
				TransactionStatus status = txInfo.getTransactionStatus();
				if (status != null && txAttr != null) {
					retVal = VavrDelegate.evaluateTryFailure(retVal, txAttr, status);
				}
			}
			// forcus 提交(虽然是 commitxxx(),但是不一定是真正提交，是有可能会回滚的)
			commitTransactionAfterReturning(txInfo);
			return retVal;
		}
		// 分支-3 skip
		else {
			Object result;
			final ThrowableHolder throwableHolder = new ThrowableHolder();

			// It's a CallbackPreferringPlatformTransactionManager: pass a TransactionCallback in.
			try {
				result = ((CallbackPreferringPlatformTransactionManager) ptm).execute(txAttr, status -> {
					TransactionInfo txInfo = prepareTransactionInfo(ptm, txAttr, joinpointIdentification, status);
					try {
						Object retVal = invocation.proceedWithInvocation();
						if (retVal != null && vavrPresent && VavrDelegate.isVavrTry(retVal)) {
							// Set rollback-only in case of Vavr failure matching our rollback rules...
							retVal = VavrDelegate.evaluateTryFailure(retVal, txAttr, status);
						}
						return retVal;
					}
					catch (Throwable ex) {
						if (txAttr.rollbackOn(ex)) {
							// A RuntimeException: will lead to a rollback.
							if (ex instanceof RuntimeException) {
								throw (RuntimeException) ex;
							}
							else {
								throw new ThrowableHolderException(ex);
							}
						}
						else {
							// A normal return value: will lead to a commit.
							throwableHolder.throwable = ex;
							return null;
						}
					}
					finally {
						cleanupTransactionInfo(txInfo);
					}
				});
			}
			catch (ThrowableHolderException ex) {
				throw ex.getCause();
			}
			catch (TransactionSystemException ex2) {
				if (throwableHolder.throwable != null) {
					logger.error("Application exception overridden by commit exception", throwableHolder.throwable);
					ex2.initApplicationException(throwableHolder.throwable);
				}
				throw ex2;
			}
			catch (Throwable ex2) {
				if (throwableHolder.throwable != null) {
					logger.error("Application exception overridden by commit exception", throwableHolder.throwable);
				}
				throw ex2;
			}

			// Check result state: It might indicate a Throwable to rethrow.
			if (throwableHolder.throwable != null) {
				throw throwableHolder.throwable;
			}
			return result;
		}
	}

	/**
	 * Clear the transaction manager cache.
	 */
	protected void clearTransactionManagerCache() {
		this.transactionManagerCache.clear();
		this.beanFactory = null;
	}

	/**
	 * Determine the specific transaction manager to use for the given transaction.
	 */
	@Nullable
	protected TransactionManager determineTransactionManager(@Nullable TransactionAttribute txAttr) {
		// Do not attempt to lookup tx manager if no tx attributes are set
		if (txAttr == null || this.beanFactory == null) {
			return getTransactionManager();
		}

		String qualifier = txAttr.getQualifier();
		if (StringUtils.hasText(qualifier)) {
			return determineQualifiedTransactionManager(this.beanFactory, qualifier);
		}
		else if (StringUtils.hasText(this.transactionManagerBeanName)) {
			return determineQualifiedTransactionManager(this.beanFactory, this.transactionManagerBeanName);
		}
		else {
			TransactionManager defaultTransactionManager = getTransactionManager();
			if (defaultTransactionManager == null) {
				defaultTransactionManager = this.transactionManagerCache.get(DEFAULT_TRANSACTION_MANAGER_KEY);
				if (defaultTransactionManager == null) {
					defaultTransactionManager = this.beanFactory.getBean(TransactionManager.class);
					this.transactionManagerCache.putIfAbsent(
							DEFAULT_TRANSACTION_MANAGER_KEY, defaultTransactionManager);
				}
			}
			return defaultTransactionManager;
		}
	}

	private TransactionManager determineQualifiedTransactionManager(BeanFactory beanFactory, String qualifier) {
		TransactionManager txManager = this.transactionManagerCache.get(qualifier);
		if (txManager == null) {
			txManager = BeanFactoryAnnotationUtils.qualifiedBeanOfType(
					beanFactory, TransactionManager.class, qualifier);
			this.transactionManagerCache.putIfAbsent(qualifier, txManager);
		}
		return txManager;
	}


	@Nullable
	private PlatformTransactionManager asPlatformTransactionManager(@Nullable Object transactionManager) {
		if (transactionManager == null || transactionManager instanceof PlatformTransactionManager) {
			return (PlatformTransactionManager) transactionManager;
		}
		else {
			throw new IllegalStateException(
					"Specified transaction manager is not a PlatformTransactionManager: " + transactionManager);
		}
	}

	private String methodIdentification(Method method, @Nullable Class<?> targetClass,
			@Nullable TransactionAttribute txAttr) {

		String methodIdentification = methodIdentification(method, targetClass);
		if (methodIdentification == null) {
			if (txAttr instanceof DefaultTransactionAttribute) {
				methodIdentification = ((DefaultTransactionAttribute) txAttr).getDescriptor();
			}
			if (methodIdentification == null) {
				methodIdentification = ClassUtils.getQualifiedMethodName(method, targetClass);
			}
		}
		return methodIdentification;
	}

	/**
	 * Convenience method to return a String representation of this Method
	 * for use in logging. Can be overridden in subclasses to provide a
	 * different identifier for the given method.
	 * <p>The default implementation returns {@code null}, indicating the
	 * use of {@link DefaultTransactionAttribute#getDescriptor()} instead,
	 * ending up as {@link ClassUtils#getQualifiedMethodName(Method, Class)}.
	 * @param method the method we're interested in
	 * @param targetClass the class that the method is being invoked on
	 * @return a String representation identifying this method
	 * @see org.springframework.util.ClassUtils#getQualifiedMethodName
	 */
	@Nullable
	protected String methodIdentification(Method method, @Nullable Class<?> targetClass) {
		return null;
	}

	/**
	 * Create a transaction if necessary based on the given TransactionAttribute.
	 * <p>Allows callers to perform custom TransactionAttribute lookups through
	 * the TransactionAttributeSource.
	 * @param txAttr the TransactionAttribute (may be {@code null})
	 * @param joinpointIdentification the fully qualified method name
	 * (used for monitoring and logging purposes)
	 * @return a TransactionInfo object, whether a transaction was created.
	 * The {@code hasTransaction()} method on TransactionInfo can be used to
	 * tell if there was a transaction created.
	 * @see #getTransactionAttributeSource()
	 */
	@SuppressWarnings("serial")
	protected TransactionInfo createTransactionIfNecessary(@Nullable PlatformTransactionManager tm,
			@Nullable TransactionAttribute txAttr, final String joinpointIdentification) {

		// If no name specified, apply method identification as transaction name.
		if (txAttr != null && txAttr.getName() == null) {
			txAttr = new DelegatingTransactionAttribute(txAttr) {
				@Override
				public String getName() {
					return joinpointIdentification;
				}
			};
		}

		TransactionStatus status = null;
		if (txAttr != null) {
			if (tm != null) {
				// forcus 根据传播行为来判断是：开启新事务 还是 加入已有事务 还是 不用事务 还是 其他...
				status = tm.getTransaction(txAttr);
			}
			else {
				if (logger.isDebugEnabled()) {
					logger.debug("Skipping transactional joinpoint [" + joinpointIdentification +
							"] because no transaction manager has been configured");
				}
			}
		}

		// forcus 创建 txInfo对象
		return prepareTransactionInfo(tm, txAttr, joinpointIdentification, status);
	}

	/**
	 * Prepare a TransactionInfo for the given attribute and status object.
	 * @param txAttr the TransactionAttribute (may be {@code null})
	 * @param joinpointIdentification the fully qualified method name
	 * (used for monitoring and logging purposes)
	 * @param status the TransactionStatus for the current transaction
	 * @return the prepared TransactionInfo object
	 */
	protected TransactionInfo prepareTransactionInfo(@Nullable PlatformTransactionManager tm,
			@Nullable TransactionAttribute txAttr, String joinpointIdentification,
			@Nullable TransactionStatus status) {
		// forcus 创建 TransactionInfo 对象
		/*
			构造后的对象属性值：
			{
				transactionManager ：	DataSourceTransactionManager@xxx - 事务管理器
				transactionAttribute ： DelegatingTransactionAttribute@xxx - 事务属性
				joinpointIdentification ： String - 方法标识，用于日志
				transactionStatus : null - 还没设置,在后续设置
				oldTransactionInfo ： null - 在下面设置
			}
		 */
		TransactionInfo txInfo = new TransactionInfo(tm, txAttr, joinpointIdentification);

		/*
			两个分支：根据 txAttr是否为 null 来决定是否设置 transactionStatus
		 */

		// forcus 对于事务方法来说 txAttr是不会为null的
		if (txAttr != null) {
			// We need a transaction for this method...
			if (logger.isTraceEnabled()) {
				logger.trace("Getting transaction for [" + txInfo.getJoinpointIdentification() + "]");
			}
			// The transaction manager will flag an error if an incompatible tx already exists.
			// forcus 将 status 赋值到 info 中
			// 后面通过：txInfo.hasTransaction() = transactionStatus != null = true
			// 的意义：后续 commitTransactionAfterReturning() 和 completeTransactionAfterThrowing() 都会通过这个判断来决定是否执行提交/回滚
			txInfo.newTransactionStatus(status);
		}
		else {
			// The TransactionInfo.hasTransaction() method will return false. We created it only
			// to preserve the integrity of the ThreadLocal stack maintained in this class.
			if (logger.isTraceEnabled()) {
				logger.trace("No need to create transaction for [" + joinpointIdentification +
						"]: This method is not transactional.");
			}
		}

		// We always bind the TransactionInfo to the thread, even if we didn't create
		// a new transaction here. This guarantees that the TransactionInfo stack
		// will be managed correctly even if no transaction was created by this aspect.
		// forcus 无论如何都入栈
		// note : 无论是否创建了事务，都要绑定到线程
		txInfo.bindToThread();
		return txInfo;
	}

	/**
	 * Execute after successful completion of call, but not after an exception was handled.
	 * Do nothing if we didn't create a transaction.
	 * @param txInfo information about the current transaction
	 */
	protected void commitTransactionAfterReturning(@Nullable TransactionInfo txInfo) {
		// 这里除了 txInfo不应该为null，txInfo中的 txStatus都不应该为null
		// 这里针对的是非事务方法中 txAttr == null 时未调用 newTransactionStatus()l,此时对应的是一个“空壳”方法，不需要commit()
		if (txInfo != null && txInfo.getTransactionStatus() != null) {
			if (logger.isTraceEnabled()) {
				logger.trace("Completing transaction for [" + txInfo.getJoinpointIdentification() + "]");
			}
			// 通过事务管理器来提交 - AbstractPlatformTransactionManager.commit()
			txInfo.getTransactionManager().commit(txInfo.getTransactionStatus());
		}
	}

	/**
	 * Handle a throwable, completing the transaction.
	 * We may commit or roll back, depending on the configuration.
	 * @param txInfo information about the current transaction
	 * @param ex throwable encountered
	 */
	protected void completeTransactionAfterThrowing(@Nullable TransactionInfo txInfo, Throwable ex) {
		// forcus 必须是要事务方法(如果是非事务方法,那么当前方法相当于什么都不做，异常继续向上抛)
		if (txInfo != null && txInfo.getTransactionStatus() != null) {
			if (logger.isTraceEnabled()) {
				logger.trace("Completing transaction for [" + txInfo.getJoinpointIdentification() +
						"] after exception: " + ex);
			}
			// forcus 核心方法,根据异常的类型来决定异常是回滚还是继续提交 -- rollbackOn()「txAttr是@Transaction注解解析出来的属性对象,在这里不可能为null,防御性编程」
			// 核心方法是 rollbackOn() -- 核心原理就是优先匹配子类(深度竞争算法)
			/*
				forcus 只有该方法匹配上了,也即返回true,才会进入到if分支中
				在这里需要注意的一点是：如果任何一个异常都没有匹配到,那么默认的逻辑是,只会回滚 (ex instanceof RuntimeException || ex instanceof Error)
				对于 checked exception (比如 IOException、SQLException )，那么默认是不会回滚的,而是提交!
			 */
			if (txInfo.transactionAttribute != null && txInfo.transactionAttribute.rollbackOn(ex)) {
				try {
					// forcus 真正的回滚
					txInfo.getTransactionManager().rollback(txInfo.getTransactionStatus());
				}
				catch (TransactionSystemException ex2) {
					logger.error("Application exception overridden by rollback exception", ex);
					ex2.initApplicationException(ex);
					throw ex2;
				}
				catch (RuntimeException | Error ex2) {
					logger.error("Application exception overridden by rollback exception", ex);
					throw ex2;
				}
			}
			else {
				// We don't roll back on this exception.
				// Will still roll back if TransactionStatus.isRollbackOnly() is true.
				try {
					txInfo.getTransactionManager().commit(txInfo.getTransactionStatus());
				}
				catch (TransactionSystemException ex2) {
					logger.error("Application exception overridden by commit exception", ex);
					ex2.initApplicationException(ex);
					throw ex2;
				}
				catch (RuntimeException | Error ex2) {
					logger.error("Application exception overridden by commit exception", ex);
					throw ex2;
				}
			}
		}
	}

	/**
	 * Reset the TransactionInfo ThreadLocal.
	 * <p>Call this in all cases: exception or normal return!
	 * @param txInfo information about the current transaction (may be {@code null})
	 */
	protected void cleanupTransactionInfo(@Nullable TransactionInfo txInfo) {
		// txInfo都走到这里了，不可能为null,在这里是防御性编程
		if (txInfo != null) {
			txInfo.restoreThreadLocalStatus();
		}
	}


	/**
	 * Opaque object used to hold transaction information. Subclasses
	 * must pass it back to methods on this class, but not see its internals.
	 */
	protected static final class TransactionInfo {

		@Nullable
		private final PlatformTransactionManager transactionManager;

		@Nullable
		private final TransactionAttribute transactionAttribute;

		private final String joinpointIdentification;

		@Nullable
		private TransactionStatus transactionStatus;

		@Nullable
		private TransactionInfo oldTransactionInfo;

		public TransactionInfo(@Nullable PlatformTransactionManager transactionManager,
				@Nullable TransactionAttribute transactionAttribute, String joinpointIdentification) {

			this.transactionManager = transactionManager;
			this.transactionAttribute = transactionAttribute;
			this.joinpointIdentification = joinpointIdentification;
		}

		public PlatformTransactionManager getTransactionManager() {
			Assert.state(this.transactionManager != null, "No PlatformTransactionManager set");
			return this.transactionManager;
		}

		@Nullable
		public TransactionAttribute getTransactionAttribute() {
			return this.transactionAttribute;
		}

		/**
		 * Return a String representation of this joinpoint (usually a Method call)
		 * for use in logging.
		 */
		public String getJoinpointIdentification() {
			return this.joinpointIdentification;
		}

		public void newTransactionStatus(@Nullable TransactionStatus status) {
			this.transactionStatus = status;
		}

		@Nullable
		public TransactionStatus getTransactionStatus() {
			return this.transactionStatus;
		}

		/**
		 * Return whether a transaction was created by this aspect,
		 * or whether we just have a placeholder to keep ThreadLocal stack integrity.
		 */
		public boolean hasTransaction() {
			return (this.transactionStatus != null);
		}
		// forcus 保存 txInfo
		private void bindToThread() {
			// Expose current TransactionStatus, preserving any existing TransactionStatus
			// for restoration after this transaction is complete.
			// 获取 旧事务方法对应的 txInfo(可能为null)，保存到当前事务对应的 txInfo中的 oldTransactionInfo (形成一个事务栈)
			this.oldTransactionInfo = transactionInfoHolder.get();
			// 绑定到对应的tl中
			transactionInfoHolder.set(this);
		}
		// forcus 对应上面的 bindToThread()操作，在这里做的是相反的操作
		private void restoreThreadLocalStatus() {
			// Use stack to restore old transaction TransactionInfo.
			// Will be null if none was set.
			transactionInfoHolder.set(this.oldTransactionInfo);
		}

		@Override
		public String toString() {
			return (this.transactionAttribute != null ? this.transactionAttribute.toString() : "No transaction");
		}
	}


	/**
	 * Simple callback interface for proceeding with the target invocation.
	 * Concrete interceptors/aspects adapt this to their invocation mechanism.
	 */
	@FunctionalInterface
	protected interface InvocationCallback {

		@Nullable
		Object proceedWithInvocation() throws Throwable;
	}


	/**
	 * Coroutines-supporting extension of the callback interface.
	 */
	protected interface CoroutinesInvocationCallback extends InvocationCallback {

		Object getTarget();

		Object[] getArguments();

		default Object getContinuation() {
			Object[] args = getArguments();
			return args[args.length - 1];
		}
	}


	/**
	 * Internal holder class for a Throwable in a callback transaction model.
	 */
	private static class ThrowableHolder {

		@Nullable
		public Throwable throwable;
	}


	/**
	 * Internal holder class for a Throwable, used as a RuntimeException to be
	 * thrown from a TransactionCallback (and subsequently unwrapped again).
	 */
	@SuppressWarnings("serial")
	private static class ThrowableHolderException extends RuntimeException {

		public ThrowableHolderException(Throwable throwable) {
			super(throwable);
		}

		@Override
		public String toString() {
			return getCause().toString();
		}
	}


	/**
	 * Inner class to avoid a hard dependency on the Vavr library at runtime.
	 */
	private static class VavrDelegate {

		public static boolean isVavrTry(Object retVal) {
			return (retVal instanceof Try);
		}

		public static Object evaluateTryFailure(Object retVal, TransactionAttribute txAttr, TransactionStatus status) {
			return ((Try<?>) retVal).onFailure(ex -> {
				if (txAttr.rollbackOn(ex)) {
					status.setRollbackOnly();
				}
			});
		}
	}

	/**
	 * Inner class to avoid a hard dependency on Kotlin at runtime.
	 */
	private static class KotlinDelegate {

		private static Object asFlow(Publisher<?> publisher) {
			return ReactiveFlowKt.asFlow(publisher);
		}

		@SuppressWarnings({"unchecked", "deprecation"})
		@Nullable
		private static Object awaitSingleOrNull(Publisher<?> publisher, Object continuation) {
			return AwaitKt.awaitSingleOrNull(publisher, (Continuation<Object>) continuation);
		}
	}


	/**
	 * Delegate for Reactor-based management of transactional methods with a
	 * reactive return type.
	 */
	private class ReactiveTransactionSupport {

		private final ReactiveAdapter adapter;

		public ReactiveTransactionSupport(ReactiveAdapter adapter) {
			this.adapter = adapter;
		}

		public Object invokeWithinTransaction(Method method, @Nullable Class<?> targetClass,
				InvocationCallback invocation, @Nullable TransactionAttribute txAttr, ReactiveTransactionManager rtm) {

			String joinpointIdentification = methodIdentification(method, targetClass, txAttr);

			// For Mono and suspending functions not returning kotlinx.coroutines.flow.Flow
			if (Mono.class.isAssignableFrom(method.getReturnType()) || (KotlinDetector.isSuspendingFunction(method) &&
					!COROUTINES_FLOW_CLASS_NAME.equals(new MethodParameter(method, -1).getParameterType().getName()))) {

				return TransactionContextManager.currentContext().flatMap(context ->
						createTransactionIfNecessary(rtm, txAttr, joinpointIdentification).flatMap(it -> {
							try {
								// Need re-wrapping until we get hold of the exception through usingWhen.
								return Mono.<Object, ReactiveTransactionInfo>usingWhen(
										Mono.just(it),
										txInfo -> {
											try {
												return (Mono<?>) invocation.proceedWithInvocation();
											}
											catch (Throwable ex) {
												return Mono.error(ex);
											}
										},
										this::commitTransactionAfterReturning,
										(txInfo, err) -> Mono.empty(),
										this::rollbackTransactionOnCancel)
										.onErrorResume(ex ->
												completeTransactionAfterThrowing(it, ex).then(Mono.error(ex)));
							}
							catch (Throwable ex) {
								// target invocation exception
								return completeTransactionAfterThrowing(it, ex).then(Mono.error(ex));
							}
						})).contextWrite(TransactionContextManager.getOrCreateContext())
						.contextWrite(TransactionContextManager.getOrCreateContextHolder());
			}

			// Any other reactive type, typically a Flux
			return this.adapter.fromPublisher(TransactionContextManager.currentContext().flatMapMany(context ->
					createTransactionIfNecessary(rtm, txAttr, joinpointIdentification).flatMapMany(it -> {
						try {
							// Need re-wrapping until we get hold of the exception through usingWhen.
							return Flux
									.usingWhen(
											Mono.just(it),
											txInfo -> {
												try {
													return this.adapter.toPublisher(invocation.proceedWithInvocation());
												}
												catch (Throwable ex) {
													return Mono.error(ex);
												}
											},
											this::commitTransactionAfterReturning,
											(txInfo, ex) -> Mono.empty(),
											this::rollbackTransactionOnCancel)
									.onErrorResume(ex ->
											completeTransactionAfterThrowing(it, ex).then(Mono.error(ex)));
						}
						catch (Throwable ex) {
							// target invocation exception
							return completeTransactionAfterThrowing(it, ex).then(Mono.error(ex));
						}
					})).contextWrite(TransactionContextManager.getOrCreateContext())
					.contextWrite(TransactionContextManager.getOrCreateContextHolder()));
		}

		@SuppressWarnings("serial")
		private Mono<ReactiveTransactionInfo> createTransactionIfNecessary(ReactiveTransactionManager tm,
				@Nullable TransactionAttribute txAttr, final String joinpointIdentification) {

			// If no name specified, apply method identification as transaction name.
			if (txAttr != null && txAttr.getName() == null) {
				txAttr = new DelegatingTransactionAttribute(txAttr) {
					@Override
					public String getName() {
						return joinpointIdentification;
					}
				};
			}

			final TransactionAttribute attrToUse = txAttr;
			Mono<ReactiveTransaction> tx = (attrToUse != null ? tm.getReactiveTransaction(attrToUse) : Mono.empty());
			return tx.map(it -> prepareTransactionInfo(tm, attrToUse, joinpointIdentification, it)).switchIfEmpty(
					Mono.defer(() -> Mono.just(prepareTransactionInfo(tm, attrToUse, joinpointIdentification, null))));
		}

		private ReactiveTransactionInfo prepareTransactionInfo(@Nullable ReactiveTransactionManager tm,
				@Nullable TransactionAttribute txAttr, String joinpointIdentification,
				@Nullable ReactiveTransaction transaction) {

			ReactiveTransactionInfo txInfo = new ReactiveTransactionInfo(tm, txAttr, joinpointIdentification);
			if (txAttr != null) {
				// We need a transaction for this method...
				if (logger.isTraceEnabled()) {
					logger.trace("Getting transaction for [" + txInfo.getJoinpointIdentification() + "]");
				}
				// The transaction manager will flag an error if an incompatible tx already exists.
				txInfo.newReactiveTransaction(transaction);
			}
			else {
				// The TransactionInfo.hasTransaction() method will return false. We created it only
				// to preserve the integrity of the ThreadLocal stack maintained in this class.
				if (logger.isTraceEnabled()) {
					logger.trace("Don't need to create transaction for [" + joinpointIdentification +
							"]: This method isn't transactional.");
				}
			}

			return txInfo;
		}

		private Mono<Void> commitTransactionAfterReturning(@Nullable ReactiveTransactionInfo txInfo) {
			if (txInfo != null && txInfo.getReactiveTransaction() != null) {
				if (logger.isTraceEnabled()) {
					logger.trace("Completing transaction for [" + txInfo.getJoinpointIdentification() + "]");
				}
				return txInfo.getTransactionManager().commit(txInfo.getReactiveTransaction());
			}
			return Mono.empty();
		}

		private Mono<Void> rollbackTransactionOnCancel(@Nullable ReactiveTransactionInfo txInfo) {
			if (txInfo != null && txInfo.getReactiveTransaction() != null) {
				if (logger.isTraceEnabled()) {
					logger.trace("Rolling back transaction for [" + txInfo.getJoinpointIdentification() + "] after cancellation");
				}
				return txInfo.getTransactionManager().rollback(txInfo.getReactiveTransaction());
			}
			return Mono.empty();
		}

		private Mono<Void> completeTransactionAfterThrowing(@Nullable ReactiveTransactionInfo txInfo, Throwable ex) {
			if (txInfo != null && txInfo.getReactiveTransaction() != null) {
				if (logger.isTraceEnabled()) {
					logger.trace("Completing transaction for [" + txInfo.getJoinpointIdentification() +
							"] after exception: " + ex);
				}
				if (txInfo.transactionAttribute != null && txInfo.transactionAttribute.rollbackOn(ex)) {
					return txInfo.getTransactionManager().rollback(txInfo.getReactiveTransaction()).onErrorMap(ex2 -> {
								logger.error("Application exception overridden by rollback exception", ex);
								if (ex2 instanceof TransactionSystemException) {
									((TransactionSystemException) ex2).initApplicationException(ex);
								}
								return ex2;
							}
					);
				}
				else {
					// We don't roll back on this exception.
					// Will still roll back if TransactionStatus.isRollbackOnly() is true.
					return txInfo.getTransactionManager().commit(txInfo.getReactiveTransaction()).onErrorMap(ex2 -> {
								logger.error("Application exception overridden by commit exception", ex);
								if (ex2 instanceof TransactionSystemException) {
									((TransactionSystemException) ex2).initApplicationException(ex);
								}
								return ex2;
							}
					);
				}
			}
			return Mono.empty();
		}
	}


	/**
	 * Opaque object used to hold transaction information for reactive methods.
	 */
	private static final class ReactiveTransactionInfo {

		@Nullable
		private final ReactiveTransactionManager transactionManager;

		@Nullable
		private final TransactionAttribute transactionAttribute;

		private final String joinpointIdentification;

		@Nullable
		private ReactiveTransaction reactiveTransaction;

		public ReactiveTransactionInfo(@Nullable ReactiveTransactionManager transactionManager,
				@Nullable TransactionAttribute transactionAttribute, String joinpointIdentification) {

			this.transactionManager = transactionManager;
			this.transactionAttribute = transactionAttribute;
			this.joinpointIdentification = joinpointIdentification;
		}

		public ReactiveTransactionManager getTransactionManager() {
			Assert.state(this.transactionManager != null, "No ReactiveTransactionManager set");
			return this.transactionManager;
		}

		@Nullable
		public TransactionAttribute getTransactionAttribute() {
			return this.transactionAttribute;
		}

		/**
		 * Return a String representation of this joinpoint (usually a Method call)
		 * for use in logging.
		 */
		public String getJoinpointIdentification() {
			return this.joinpointIdentification;
		}

		public void newReactiveTransaction(@Nullable ReactiveTransaction transaction) {
			this.reactiveTransaction = transaction;
		}

		@Nullable
		public ReactiveTransaction getReactiveTransaction() {
			return this.reactiveTransaction;
		}

		@Override
		public String toString() {
			return (this.transactionAttribute != null ? this.transactionAttribute.toString() : "No transaction");
		}
	}

}
