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

package org.springframework.scheduling.annotation;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.aop.framework.AopInfrastructureBean;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.config.DestructionAwareBeanPostProcessor;
import org.springframework.beans.factory.config.NamedBeanHolder;
import org.springframework.beans.factory.support.MergedBeanDefinitionPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.EmbeddedValueResolverAware;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.FixedRateTask;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.scheduling.support.ScheduledMethodRunnable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.util.StringValueResolver;

/**
 * Bean post-processor that registers methods annotated with
 * {@link Scheduled @Scheduled} to be invoked by a
 * {@link org.springframework.scheduling.TaskScheduler} according to the
 * "fixedRate", "fixedDelay", or "cron" expression provided via the annotation.
 *
 * <p>This post-processor is automatically registered by Spring's
 * {@code <task:annotation-driven>} XML element and also by the
 * {@link EnableScheduling @EnableScheduling} annotation.
 *
 * <p>Autodetects any {@link SchedulingConfigurer} instances in the container,
 * allowing for customization of the scheduler to be used or for fine-grained
 * control over task registration (e.g. registration of {@link Trigger} tasks).
 * See the {@link EnableScheduling @EnableScheduling} javadocs for complete usage
 * details.
 *
 * @author Mark Fisher
 * @author Juergen Hoeller
 * @author Chris Beams
 * @author Elizabeth Chatman
 * @author Victor Brown
 * @author Sam Brannen
 * @since 3.0
 * @see Scheduled
 * @see EnableScheduling
 * @see SchedulingConfigurer
 * @see org.springframework.scheduling.TaskScheduler
 * @see org.springframework.scheduling.config.ScheduledTaskRegistrar
 * @see AsyncAnnotationBeanPostProcessor
 */
public class ScheduledAnnotationBeanPostProcessor
		implements ScheduledTaskHolder, MergedBeanDefinitionPostProcessor, DestructionAwareBeanPostProcessor,
		Ordered, EmbeddedValueResolverAware, BeanNameAware, BeanFactoryAware, ApplicationContextAware,
		SmartInitializingSingleton, ApplicationListener<ContextRefreshedEvent>, DisposableBean {

	/**
	 * The default name of the {@link TaskScheduler} bean to pick up: {@value}.
	 * <p>Note that the initial lookup happens by type; this is just the fallback
	 * in case of multiple scheduler beans found in the context.
	 * @since 4.2
	 */
	public static final String DEFAULT_TASK_SCHEDULER_BEAN_NAME = "taskScheduler";


	protected final Log logger = LogFactory.getLog(getClass());

	private final ScheduledTaskRegistrar registrar;

	@Nullable
	private Object scheduler;

	@Nullable
	private StringValueResolver embeddedValueResolver;

	@Nullable
	private String beanName;

	@Nullable
	private BeanFactory beanFactory;

	@Nullable
	private ApplicationContext applicationContext;

	private final Set<Class<?>> nonAnnotatedClasses = Collections.newSetFromMap(new ConcurrentHashMap<>(64));

	private final Map<Object, Set<ScheduledTask>> scheduledTasks = new IdentityHashMap<>(16);


	/**
	 * Create a default {@code ScheduledAnnotationBeanPostProcessor}.
	 */
	public ScheduledAnnotationBeanPostProcessor() {
		this.registrar = new ScheduledTaskRegistrar();
	}

	/**
	 * Create a {@code ScheduledAnnotationBeanPostProcessor} delegating to the
	 * specified {@link ScheduledTaskRegistrar}.
	 * @param registrar the ScheduledTaskRegistrar to register {@code @Scheduled}
	 * tasks on
	 * @since 5.1
	 */
	public ScheduledAnnotationBeanPostProcessor(ScheduledTaskRegistrar registrar) {
		Assert.notNull(registrar, "ScheduledTaskRegistrar must not be null");
		this.registrar = registrar;
	}


	@Override
	public int getOrder() {
		return LOWEST_PRECEDENCE;
	}

	/**
	 * Set the {@link org.springframework.scheduling.TaskScheduler} that will invoke
	 * the scheduled methods, or a {@link java.util.concurrent.ScheduledExecutorService}
	 * to be wrapped as a TaskScheduler.
	 * <p>If not specified, default scheduler resolution will apply: searching for a
	 * unique {@link TaskScheduler} bean in the context, or for a {@link TaskScheduler}
	 * bean named "taskScheduler" otherwise; the same lookup will also be performed for
	 * a {@link ScheduledExecutorService} bean. If neither of the two is resolvable,
	 * a local single-threaded default scheduler will be created within the registrar.
	 * @see #DEFAULT_TASK_SCHEDULER_BEAN_NAME
	 */
	public void setScheduler(Object scheduler) {
		this.scheduler = scheduler;
	}

	@Override
	public void setEmbeddedValueResolver(StringValueResolver resolver) {
		this.embeddedValueResolver = resolver;
	}

	@Override
	public void setBeanName(String beanName) {
		this.beanName = beanName;
	}

	/**
	 * Making a {@link BeanFactory} available is optional; if not set,
	 * {@link SchedulingConfigurer} beans won't get autodetected and
	 * a {@link #setScheduler scheduler} has to be explicitly configured.
	 */
	@Override
	public void setBeanFactory(BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}

	/**
	 * Setting an {@link ApplicationContext} is optional: If set, registered
	 * tasks will be activated in the {@link ContextRefreshedEvent} phase;
	 * if not set, it will happen at {@link #afterSingletonsInstantiated} time.
	 */
	@Override
	public void setApplicationContext(ApplicationContext applicationContext) {
		this.applicationContext = applicationContext;
		if (this.beanFactory == null) {
			this.beanFactory = applicationContext;
		}
	}


	@Override
	public void afterSingletonsInstantiated() {
		// Remove resolved singleton classes from cache
		this.nonAnnotatedClasses.clear();

		if (this.applicationContext == null) {
			// Not running in an ApplicationContext -> register tasks early...
			finishRegistration();
		}
	}

	@Override
	public void onApplicationEvent(ContextRefreshedEvent event) {
		if (event.getApplicationContext() == this.applicationContext) {
			// Running in an ApplicationContext -> register tasks this late...
			// giving other ContextRefreshedEvent listeners a chance to perform
			// their work at the same time (e.g. Spring Batch's job registration).
			finishRegistration();
		}
	}

	private void finishRegistration() {
		// forcus-1 显式设置
		if (this.scheduler != null) {
			this.registrar.setScheduler(this.scheduler);
		}

		if (this.beanFactory instanceof ListableBeanFactory) {
			// forcus-2 SchedulingConfigurer 回调
			Map<String, SchedulingConfigurer> beans =
					((ListableBeanFactory) this.beanFactory).getBeansOfType(SchedulingConfigurer.class);
			List<SchedulingConfigurer> configurers = new ArrayList<>(beans.values());
			AnnotationAwareOrderComparator.sort(configurers);
			for (SchedulingConfigurer configurer : configurers) {
				configurer.configureTasks(this.registrar); // forcus  用户可在这里设置 scheduler 以及 手动注册任务
			}
		}
		// forcus-3 从容器中自动查找（前提：有任务 && 还没有 scheduler）
		if (this.registrar.hasTasks() && this.registrar.getScheduler() == null) {
			Assert.state(this.beanFactory != null, "BeanFactory must be set to find scheduler by type");
			try {
				// Search for TaskScheduler bean...
				// forcus  按类型找 TaskScheduler
				this.registrar.setTaskScheduler(resolveSchedulerBean(this.beanFactory, TaskScheduler.class, false));
			}
			catch (NoUniqueBeanDefinitionException ex) {
				if (logger.isTraceEnabled()) {
					logger.trace("Could not find unique TaskScheduler bean - attempting to resolve by name: " +
							ex.getMessage());
				}
				try {
					// forcus 找到多个 → 按名称 "taskScheduler" 找
					this.registrar.setTaskScheduler(resolveSchedulerBean(this.beanFactory, TaskScheduler.class, true));
				}
				catch (NoSuchBeanDefinitionException ex2) {
					if (logger.isInfoEnabled()) {
						logger.info("More than one TaskScheduler bean exists within the context, and " +
								"none is named 'taskScheduler'. Mark one of them as primary or name it 'taskScheduler' " +
								"(possibly as an alias); or implement the SchedulingConfigurer interface and call " +
								"ScheduledTaskRegistrar#setScheduler explicitly within the configureTasks() callback: " +
								ex.getBeanNamesFound());
					}
				}
			}
			catch (NoSuchBeanDefinitionException ex) {
				if (logger.isTraceEnabled()) {
					logger.trace("Could not find default TaskScheduler bean - attempting to find ScheduledExecutorService: " +
							ex.getMessage());
				}
				// Search for ScheduledExecutorService bean next...
				try {
					// forcus 没有 → 降级找 ScheduledExecutorService
					this.registrar.setScheduler(resolveSchedulerBean(this.beanFactory, ScheduledExecutorService.class, false));
				}
				catch (NoUniqueBeanDefinitionException ex2) {
					if (logger.isTraceEnabled()) {
						logger.trace("Could not find unique ScheduledExecutorService bean - attempting to resolve by name: " +
								ex2.getMessage());
					}
					try {
						this.registrar.setScheduler(resolveSchedulerBean(this.beanFactory, ScheduledExecutorService.class, true));
					}
					catch (NoSuchBeanDefinitionException ex3) {
						if (logger.isInfoEnabled()) {
							logger.info("More than one ScheduledExecutorService bean exists within the context, and " +
									"none is named 'taskScheduler'. Mark one of them as primary or name it 'taskScheduler' " +
									"(possibly as an alias); or implement the SchedulingConfigurer interface and call " +
									"ScheduledTaskRegistrar#setScheduler explicitly within the configureTasks() callback: " +
									ex2.getBeanNamesFound());
						}
					}
				}
				catch (NoSuchBeanDefinitionException ex2) {
					if (logger.isTraceEnabled()) {
						logger.trace("Could not find default ScheduledExecutorService bean - falling back to default: " +
								ex2.getMessage());
					}
					// Giving up -> falling back to default scheduler within the registrar...
					logger.info("No TaskScheduler/ScheduledExecutorService bean found for scheduled processing");
				}
			}
		}
		// forcus 兜底（在 scheduleTasks() 中）
		this.registrar.afterPropertiesSet();
	}

	private <T> T resolveSchedulerBean(BeanFactory beanFactory, Class<T> schedulerType, boolean byName) {
		if (byName) {
			T scheduler = beanFactory.getBean(DEFAULT_TASK_SCHEDULER_BEAN_NAME, schedulerType);
			if (this.beanName != null && this.beanFactory instanceof ConfigurableBeanFactory) {
				((ConfigurableBeanFactory) this.beanFactory).registerDependentBean(
						DEFAULT_TASK_SCHEDULER_BEAN_NAME, this.beanName);
			}
			return scheduler;
		}
		else if (beanFactory instanceof AutowireCapableBeanFactory) {
			NamedBeanHolder<T> holder = ((AutowireCapableBeanFactory) beanFactory).resolveNamedBean(schedulerType);
			if (this.beanName != null && beanFactory instanceof ConfigurableBeanFactory) {
				((ConfigurableBeanFactory) beanFactory).registerDependentBean(holder.getBeanName(), this.beanName);
			}
			return holder.getBeanInstance();
		}
		else {
			return beanFactory.getBean(schedulerType);
		}
	}


	@Override
	public void postProcessMergedBeanDefinition(RootBeanDefinition beanDefinition, Class<?> beanType, String beanName) {
	}

	@Override
	public Object postProcessBeforeInitialization(Object bean, String beanName) {
		return bean;
	}

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) {
		/*
			forcus 跳过基础设施bean,在这里有3种类型
				1. AopInfrastructureBean：AOP 基础设施，不是业务 Bean，不可能有 @Scheduled
				2. TaskScheduler:它是调度器本身，不是被调度的任务。如果不跳过，调度器 Bean 初始化时会被扫描，浪费资源
				3. ScheduledExecutorService: 同上，JDK 层面的调度器，不可能有 @Scheduled

		 */
		if (bean instanceof AopInfrastructureBean || bean instanceof TaskScheduler ||
				bean instanceof ScheduledExecutorService) {
			// Ignore AOP infrastructure such as scoped proxies.
			return bean;
		}
		// 穿透代理,获取真实类,因为@Scheduled注解是加在原始类上的,而不是代理类
		Class<?> targetClass = AopProxyUtils.ultimateTargetClass(bean);
		/*
			两层判断：
				1. nonAnnotatedClasses:这个是用来缓存没有@Scheduled注解方法的bean实例的,如果没有那就不需要处理了
				2. 快速判断
					- 是 java. 开头的类（比如 java.lang.String）→ 这是 JDK 自带的类，JDK 的类怎么可能有 Spring 的注解？不可能，直接排除。
					- 不是 java. 开头的类（比如 com.example.MyService）→ 这是用户自己的类，有可能，需要继续扫描
		 */
		if (!this.nonAnnotatedClasses.contains(targetClass) &&
				AnnotationUtils.isCandidateClass(targetClass, Arrays.asList(Scheduled.class, Schedules.class))) {
			// 走到这里,那么说明当前bean实例可能有@Scheduled注解「注意是可能有,不代表一定有」

			// ---

			/*
				forcus 扫描@Scheduled注解标注的方法
				遍历 targetClass 的所有方法（包括父类、接口的方法），对每个方法执行 lambda 回调。回调返回非 null 的方法会被收集到结果 Map 中。
				最终的返回值：
					Map<Method, Set<Scheduled>>
						- key：对应的方法
						- value: 该方法上所有的 @Scheduled 注解集合
				这里一个method可能对应多个@Scheduled注解,这是支持的,因为有的时候可能希望同一个方法按照多种不同的调度策略执行，就需要标注多个 @Scheduled 注解
			 */
			Map<Method, Set<Scheduled>> annotatedMethods = MethodIntrospector.selectMethods(targetClass,
					(MethodIntrospector.MetadataLookup<Set<Scheduled>>) method -> {
						Set<Scheduled> scheduledAnnotations = AnnotatedElementUtils.getMergedRepeatableAnnotations(
								method, Scheduled.class, Schedules.class);
						return (!scheduledAnnotations.isEmpty() ? scheduledAnnotations : null);
					});
			// --- 根据扫描结果来做处理

			// 如果当前bean实例内部没有@Scheduled注解标注的方法,那么缓存到 nonAnnotatedClasses
			if (annotatedMethods.isEmpty()) {
				this.nonAnnotatedClasses.add(targetClass);
				if (logger.isTraceEnabled()) {
					logger.trace("No @Scheduled annotations found on bean class: " + targetClass);
				}
			}
			// forcus 否则,当前bean实例内部有@Scheduled注解标注的方法,那么逐个处理
			// 每个注解调用一次 processScheduled(scheduled, method, bean)，解析 cron/fixedRate/fixedDelay 并注册任务
			else {
				// Non-empty set of methods
				annotatedMethods.forEach((method, scheduledAnnotations) ->
						scheduledAnnotations.forEach(scheduled -> processScheduled(scheduled, method, bean)));
				if (logger.isTraceEnabled()) {
					logger.trace(annotatedMethods.size() + " @Scheduled methods processed on bean '" + beanName +
							"': " + annotatedMethods);
				}
			}
		}
		// forcus 注意,spring调度是不需要代理的,这里是直接返回原始bean对象
		return bean;
	}

	/**
	 * Process the given {@code @Scheduled} method declaration on the given bean.
	 * @param scheduled the {@code @Scheduled} annotation
	 * @param method the method that the annotation has been declared on
	 * @param bean the target bean instance
	 * @see #createRunnable(Object, Method)
	 */
	protected void processScheduled(Scheduled scheduled, Method method, Object bean) {
		try {
			/*
				forcus 创建runnable
				把 bean + method 封装成一个 Runnable（实际类型是 ScheduledMethodRunnable），
				后续调度器每次触发时，就调用 runnable.run() → 反射调用 method.invoke(bean)。
			 */
			Runnable runnable = createRunnable(bean, method);
			// 互斥标记。一旦某种调度方式被解析成功就置为 true，后续再解析到另一种就报错
			boolean processedSchedule = false;
			String errorMessage =
					"Exactly one of the 'cron', 'fixedDelay(String)', or 'fixedRate(String)' attributes is required";
			// 收集本次解析产生的所有 ScheduledTask
			Set<ScheduledTask> tasks = new LinkedHashSet<>(4);

			// Determine initial delay forcus 解析 initialDelay
			// ----
			// 先读数值型
			long initialDelay = convertToMillis(scheduled.initialDelay(), scheduled.timeUnit());
			// 再读字符串型
			String initialDelayString = scheduled.initialDelayString();
			if (StringUtils.hasText(initialDelayString)) {
				// 不能同时设置两种
				Assert.isTrue(initialDelay < 0, "Specify 'initialDelay' or 'initialDelayString', not both");
				// 解析占位符 ${...}
				if (this.embeddedValueResolver != null) {
					initialDelayString = this.embeddedValueResolver.resolveStringValue(initialDelayString);
				}
				if (StringUtils.hasLength(initialDelayString)) {
					try {
						initialDelay = convertToMillis(initialDelayString, scheduled.timeUnit());
					}
					catch (RuntimeException ex) {
						throw new IllegalArgumentException(
								"Invalid initialDelayString value \"" + initialDelayString + "\" - cannot parse into long");
					}
				}
			}

			// Check cron expression forcus 解析 cron
			/*
				NOTE:
					1. cron 不支持 initialDelay。cron 本身就定义了精确的触发时间（秒、分、时...），initialDelay 和它语义冲突，所以直接 Assert 禁止。
					2. CRON_DISABLED = "-"。这是个设计巧妙的功能——你可以这样写：
					   		@Scheduled(cron = "${my.task.cron}")
					   		在配置文件中设 my.task.cron=-，就能动态禁用这个定时任务，不用改代码、不用重新部署。
					3. 支持时区。@Scheduled(cron = "0 0 9 * * ?", zone = "America/New_York") 表示纽约时间每天 9 点执行。
			 */
			String cron = scheduled.cron();
			if (StringUtils.hasText(cron)) {
				String zone = scheduled.zone();
				//  解析占位符
				if (this.embeddedValueResolver != null) {
					cron = this.embeddedValueResolver.resolveStringValue(cron);
					zone = this.embeddedValueResolver.resolveStringValue(zone);
				}
				if (StringUtils.hasLength(cron)) {
					// cron 不支持 initialDelay
					Assert.isTrue(initialDelay == -1, "'initialDelay' not supported for cron triggers");
					processedSchedule = true; // 标记：已解析到一种调度方式
					// 检查是否是禁用标记 "-"
					if (!Scheduled.CRON_DISABLED.equals(cron)) {
						// 创建 CronTrigger（带时区或不带）
						CronTrigger trigger;
						if (StringUtils.hasText(zone)) {
							trigger = new CronTrigger(cron, StringUtils.parseTimeZoneString(zone));
						}
						else {
							trigger = new CronTrigger(cron);
						}
						// 注册到 registrar
						tasks.add(this.registrar.scheduleCronTask(new CronTask(runnable, trigger)));
					}
				}
			}

			// At this point we don't need to differentiate between initial delay set or not anymore
			// cron 处理完后，initialDelay 如果还是 -1（未设置），统一归为 0。
			// 后续 fixedDelay 和 fixedRate 的处理就不用再区分"是否设置了 initialDelay"了。
			if (initialDelay < 0) {
				initialDelay = 0;
			}

			// Check fixed delay forcus 解析 fixedDelay
			long fixedDelay = convertToMillis(scheduled.fixedDelay(), scheduled.timeUnit());
			// 数值型
			if (fixedDelay >= 0) {
				Assert.isTrue(!processedSchedule, errorMessage);  // 互斥检查！如果前面已经解析到了 cron，processedSchedule = true，这里再发现 fixedDelay 也有值 → 抛异常。三种调度方式互斥。
				processedSchedule = true;
				tasks.add(this.registrar.scheduleFixedDelayTask(new FixedDelayTask(runnable, fixedDelay, initialDelay)));
			}
			// 字符串型
			String fixedDelayString = scheduled.fixedDelayString();
			if (StringUtils.hasText(fixedDelayString)) {
				if (this.embeddedValueResolver != null) {
					fixedDelayString = this.embeddedValueResolver.resolveStringValue(fixedDelayString);
				}
				if (StringUtils.hasLength(fixedDelayString)) {
					Assert.isTrue(!processedSchedule, errorMessage); // 互斥检查！
					processedSchedule = true;
					try {
						fixedDelay = convertToMillis(fixedDelayString, scheduled.timeUnit());
					}
					catch (RuntimeException ex) {
						throw new IllegalArgumentException(
								"Invalid fixedDelayString value \"" + fixedDelayString + "\" - cannot parse into long");
					}
					tasks.add(this.registrar.scheduleFixedDelayTask(new FixedDelayTask(runnable, fixedDelay, initialDelay)));
				}
			}

			// Check fixed rate forcus 解析 fixedRate
			long fixedRate = convertToMillis(scheduled.fixedRate(), scheduled.timeUnit());
			if (fixedRate >= 0) {
				Assert.isTrue(!processedSchedule, errorMessage);
				processedSchedule = true;
				tasks.add(this.registrar.scheduleFixedRateTask(new FixedRateTask(runnable, fixedRate, initialDelay)));
			}
			String fixedRateString = scheduled.fixedRateString();
			if (StringUtils.hasText(fixedRateString)) {
				if (this.embeddedValueResolver != null) {
					fixedRateString = this.embeddedValueResolver.resolveStringValue(fixedRateString);
				}
				if (StringUtils.hasLength(fixedRateString)) {
					Assert.isTrue(!processedSchedule, errorMessage);
					processedSchedule = true;
					try {
						fixedRate = convertToMillis(fixedRateString, scheduled.timeUnit());
					}
					catch (RuntimeException ex) {
						throw new IllegalArgumentException(
								"Invalid fixedRateString value \"" + fixedRateString + "\" - cannot parse into long");
					}
					tasks.add(this.registrar.scheduleFixedRateTask(new FixedRateTask(runnable, fixedRate, initialDelay)));
				}
			}

			// Check whether we had any attribute set
			// forcus 如果 cron、fixedDelay、fixedRate 一个都没设（全是默认值），直接报错：
			Assert.isTrue(processedSchedule, errorMessage);

			// Finally register the scheduled tasks
			// forcus 存入 scheduledTasks 映射
			synchronized (this.scheduledTasks) {
				Set<ScheduledTask> regTasks = this.scheduledTasks.computeIfAbsent(bean, key -> new LinkedHashSet<>(4));
				regTasks.addAll(tasks);
			}
		}
		catch (IllegalArgumentException ex) {
			throw new IllegalStateException(
					"Encountered invalid @Scheduled method '" + method.getName() + "': " + ex.getMessage());
		}
	}

	/**
	 * Create a {@link Runnable} for the given bean instance,
	 * calling the specified scheduled method.
	 * <p>The default implementation creates a {@link ScheduledMethodRunnable}.
	 * @param target the target bean instance
	 * @param method the scheduled method to call
	 * @since 5.1
	 * @see ScheduledMethodRunnable#ScheduledMethodRunnable(Object, Method)
	 */
	protected Runnable createRunnable(Object target, Method method) {
		Assert.isTrue(method.getParameterCount() == 0, "Only no-arg methods may be annotated with @Scheduled");
		Method invocableMethod = AopUtils.selectInvocableMethod(method, target.getClass());
		return new ScheduledMethodRunnable(target, invocableMethod);
	}

	private static long convertToMillis(long value, TimeUnit timeUnit) {
		return TimeUnit.MILLISECONDS.convert(value, timeUnit);
	}

	private static long convertToMillis(String value, TimeUnit timeUnit) {
		if (isDurationString(value)) {
			return Duration.parse(value).toMillis();
		}
		return convertToMillis(Long.parseLong(value), timeUnit);
	}

	private static boolean isDurationString(String value) {
		return (value.length() > 1 && (isP(value.charAt(0)) || isP(value.charAt(1))));
	}

	private static boolean isP(char ch) {
		return (ch == 'P' || ch == 'p');
	}


	/**
	 * Return all currently scheduled tasks, from {@link Scheduled} methods
	 * as well as from programmatic {@link SchedulingConfigurer} interaction.
	 * @since 5.0.2
	 */
	@Override
	public Set<ScheduledTask> getScheduledTasks() {
		Set<ScheduledTask> result = new LinkedHashSet<>();
		synchronized (this.scheduledTasks) {
			Collection<Set<ScheduledTask>> allTasks = this.scheduledTasks.values();
			for (Set<ScheduledTask> tasks : allTasks) {
				result.addAll(tasks);
			}
		}
		result.addAll(this.registrar.getScheduledTasks());
		return result;
	}

	@Override
	public void postProcessBeforeDestruction(Object bean, String beanName) {
		Set<ScheduledTask> tasks;
		synchronized (this.scheduledTasks) {
			tasks = this.scheduledTasks.remove(bean);
		}
		if (tasks != null) {
			for (ScheduledTask task : tasks) {
				task.cancel(false);
			}
		}
	}

	@Override
	public boolean requiresDestruction(Object bean) {
		synchronized (this.scheduledTasks) {
			return this.scheduledTasks.containsKey(bean);
		}
	}

	@Override
	public void destroy() {
		synchronized (this.scheduledTasks) {
			Collection<Set<ScheduledTask>> allTasks = this.scheduledTasks.values();
			for (Set<ScheduledTask> tasks : allTasks) {
				for (ScheduledTask task : tasks) {
					task.cancel(false);
				}
			}
			this.scheduledTasks.clear();
		}
		this.registrar.destroy();
	}

}
