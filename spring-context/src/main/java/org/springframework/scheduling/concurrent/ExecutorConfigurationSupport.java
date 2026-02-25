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

package org.springframework.scheduling.concurrent;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.lang.Nullable;

/**
 * Base class for setting up a {@link java.util.concurrent.ExecutorService}
 * (typically a {@link java.util.concurrent.ThreadPoolExecutor} or
 * {@link java.util.concurrent.ScheduledThreadPoolExecutor}).
 *
 * <p>Defines common configuration settings and common lifecycle handling,
 * inheriting thread customization options (name, priority, etc) from
 * {@link org.springframework.util.CustomizableThreadCreator}.
 *
 * @author Juergen Hoeller
 * @since 3.0
 * @see java.util.concurrent.ExecutorService
 * @see java.util.concurrent.Executors
 * @see java.util.concurrent.ThreadPoolExecutor
 * @see java.util.concurrent.ScheduledThreadPoolExecutor
 */
@SuppressWarnings("serial")
public abstract class ExecutorConfigurationSupport extends CustomizableThreadFactory
		implements BeanNameAware, InitializingBean, DisposableBean {

	protected final Log logger = LogFactory.getLog(getClass());

	private ThreadFactory threadFactory = this;

	private boolean threadNamePrefixSet = false;

	private RejectedExecutionHandler rejectedExecutionHandler = new ThreadPoolExecutor.AbortPolicy();

	private boolean waitForTasksToCompleteOnShutdown = false;

	private long awaitTerminationMillis = 0;

	@Nullable
	private String beanName;

	@Nullable
	private ExecutorService executor;


	/**
	 * Set the ThreadFactory to use for the ExecutorService's thread pool.
	 * Default is the underlying ExecutorService's default thread factory.
	 * <p>In a Java EE 7 or other managed environment with JSR-236 support,
	 * consider specifying a JNDI-located ManagedThreadFactory: by default,
	 * to be found at "java:comp/DefaultManagedThreadFactory".
	 * Use the "jee:jndi-lookup" namespace element in XML or the programmatic
	 * {@link org.springframework.jndi.JndiLocatorDelegate} for convenient lookup.
	 * Alternatively, consider using Spring's {@link DefaultManagedAwareThreadFactory}
	 * with its fallback to local threads in case of no managed thread factory found.
	 * @see java.util.concurrent.Executors#defaultThreadFactory()
	 * @see javax.enterprise.concurrent.ManagedThreadFactory
	 * @see DefaultManagedAwareThreadFactory
	 */
	public void setThreadFactory(@Nullable ThreadFactory threadFactory) {
		this.threadFactory = (threadFactory != null ? threadFactory : this);
	}

	@Override
	public void setThreadNamePrefix(@Nullable String threadNamePrefix) {
		super.setThreadNamePrefix(threadNamePrefix);
		this.threadNamePrefixSet = true;
	}

	/**
	 * Set the RejectedExecutionHandler to use for the ExecutorService.
	 * Default is the ExecutorService's default abort policy.
	 * @see java.util.concurrent.ThreadPoolExecutor.AbortPolicy
	 */
	public void setRejectedExecutionHandler(@Nullable RejectedExecutionHandler rejectedExecutionHandler) {
		this.rejectedExecutionHandler =
				(rejectedExecutionHandler != null ? rejectedExecutionHandler : new ThreadPoolExecutor.AbortPolicy());
	}

	/**
	 * Set whether to wait for scheduled tasks to complete on shutdown,
	 * not interrupting running tasks and executing all tasks in the queue.
	 * <p>Default is {@code false}, shutting down immediately through interrupting
	 * ongoing tasks and clearing the queue. Switch this flag to {@code true} if
	 * you prefer fully completed tasks at the expense of a longer shutdown phase.
	 * <p>Note that Spring's container shutdown continues while ongoing tasks
	 * are being completed. If you want this executor to block and wait for the
	 * termination of tasks before the rest of the container continues to shut
	 * down - e.g. in order to keep up other resources that your tasks may need -,
	 * set the {@link #setAwaitTerminationSeconds "awaitTerminationSeconds"}
	 * property instead of or in addition to this property.
	 * @see java.util.concurrent.ExecutorService#shutdown()
	 * @see java.util.concurrent.ExecutorService#shutdownNow()
	 */
	public void setWaitForTasksToCompleteOnShutdown(boolean waitForJobsToCompleteOnShutdown) {
		this.waitForTasksToCompleteOnShutdown = waitForJobsToCompleteOnShutdown;
	}

	/**
	 * Set the maximum number of seconds that this executor is supposed to block
	 * on shutdown in order to wait for remaining tasks to complete their execution
	 * before the rest of the container continues to shut down. This is particularly
	 * useful if your remaining tasks are likely to need access to other resources
	 * that are also managed by the container.
	 * <p>By default, this executor won't wait for the termination of tasks at all.
	 * It will either shut down immediately, interrupting ongoing tasks and clearing
	 * the remaining task queue - or, if the
	 * {@link #setWaitForTasksToCompleteOnShutdown "waitForTasksToCompleteOnShutdown"}
	 * flag has been set to {@code true}, it will continue to fully execute all
	 * ongoing tasks as well as all remaining tasks in the queue, in parallel to
	 * the rest of the container shutting down.
	 * <p>In either case, if you specify an await-termination period using this property,
	 * this executor will wait for the given time (max) for the termination of tasks.
	 * As a rule of thumb, specify a significantly higher timeout here if you set
	 * "waitForTasksToCompleteOnShutdown" to {@code true} at the same time,
	 * since all remaining tasks in the queue will still get executed - in contrast
	 * to the default shutdown behavior where it's just about waiting for currently
	 * executing tasks that aren't reacting to thread interruption.
	 * @see #setAwaitTerminationMillis
	 * @see java.util.concurrent.ExecutorService#shutdown()
	 * @see java.util.concurrent.ExecutorService#awaitTermination
	 */
	public void setAwaitTerminationSeconds(int awaitTerminationSeconds) {
		this.awaitTerminationMillis = awaitTerminationSeconds * 1000L;
	}

	/**
	 * Variant of {@link #setAwaitTerminationSeconds} with millisecond precision.
	 * @since 5.2.4
	 * @see #setAwaitTerminationSeconds
	 */
	public void setAwaitTerminationMillis(long awaitTerminationMillis) {
		this.awaitTerminationMillis = awaitTerminationMillis;
	}

	@Override
	public void setBeanName(String name) {
		this.beanName = name;
	}


	/**
	 * Calls {@code initialize()} after the container applied all property values.
	 * @see #initialize()
	 */
	@Override
	public void afterPropertiesSet() {
		initialize();
	}

	/**
	 * Set up the ExecutorService.
	 */
	public void initialize() {
		if (logger.isDebugEnabled()) {
			logger.debug("Initializing ExecutorService" + (this.beanName != null ? " '" + this.beanName + "'" : ""));
		}
		if (!this.threadNamePrefixSet && this.beanName != null) {
			setThreadNamePrefix(this.beanName + "-"); // forcus 如果没有设置线程名前缀,那么默认使用线程池对应的beanName
		}
		// forcus 初始化线程池
		this.executor = initializeExecutor(this.threadFactory, this.rejectedExecutionHandler);
	}

	/**
	 * Create the target {@link java.util.concurrent.ExecutorService} instance.
	 * Called by {@code afterPropertiesSet}.
	 * @param threadFactory the ThreadFactory to use
	 * @param rejectedExecutionHandler the RejectedExecutionHandler to use
	 * @return a new ExecutorService instance
	 * @see #afterPropertiesSet()
	 */
	protected abstract ExecutorService initializeExecutor(
			ThreadFactory threadFactory, RejectedExecutionHandler rejectedExecutionHandler);


	/**
	 * Calls {@code shutdown} when the BeanFactory destroys the executor instance.
	 * @see #shutdown()
	 */
	@Override
	public void destroy() {
		shutdown();
	}

	/**
	 * Perform a full shutdown on the underlying ExecutorService,
	 * according to the corresponding configuration settings.
	 * @see #setWaitForTasksToCompleteOnShutdown
	 * @see #setAwaitTerminationMillis
	 * @see java.util.concurrent.ExecutorService#shutdown()
	 * @see java.util.concurrent.ExecutorService#shutdownNow()
	 * @see java.util.concurrent.ExecutorService#awaitTermination
	 */
	// forcus 优雅停机 - 这个方法是在线程池bean被销毁时触发 - 也就是在spring容器关闭时自动调用destroy()->shutdown()
	/*
		什么是优雅停机?
			应用关闭时（如 Kubernetes 滚动更新、手动重启），线程池中可能有：
				正在执行的任务（线程正在 run）
				队列中排队的任务（还没开始执行）
				- 不优雅：直接杀进程，任务执行到一半被中断，数据不一致。
				- 优雅：等正在执行的任务完成，队列中的任务也执行完（或有序取消），然后再关闭。
				在这里涉及到了两个关键配置：
					1. waitForTasksToCompleteOnShutdown：是否等待任务完成(	控制调用 shutdown() 还是 shutdownNow())
					2. awaitTerminationMillis：等待任务完成的最大时间(关闭后阻塞等待多久)
	 */
	public void shutdown() {
		if (logger.isDebugEnabled()) {
			logger.debug("Shutting down ExecutorService" + (this.beanName != null ? " '" + this.beanName + "'" : ""));
		}
		if (this.executor != null) {
			//  如果开启了waitForTasksToCompleteOnShutdown，那么调用shutdown()
			if (this.waitForTasksToCompleteOnShutdown) {
				this.executor.shutdown();
			}
			/*
				否则调用shutdownNow(),关于shutdownNow()之前在线程池文章中也讲解过：其特点为
					1. 不再接受新任务
					2. 中断所有正在执行的线程（发 interrupt 信号）
					3. 清空队列，把队列中未执行的任务作为 List 返回
				然后在这里依次处理每个任务，逐个取消
			 */
			else {
				for (Runnable remainingTask : this.executor.shutdownNow()) {
					cancelRemainingTask(remainingTask);
				}
			}
			// forcus 最终都要执行这个方法
			awaitTerminationIfNecessary(this.executor);
		}
	}

	/**
	 * Cancel the given remaining task which never commended execution,
	 * as returned from {@link ExecutorService#shutdownNow()}.
	 * @param task the task to cancel (typically a {@link RunnableFuture})
	 * @since 5.0.5
	 * @see #shutdown()
	 * @see RunnableFuture#cancel(boolean)
	 */
	protected void cancelRemainingTask(Runnable task) {
		if (task instanceof Future) {
			((Future<?>) task).cancel(true);
		}
	}

	/**
	 * Wait for the executor to terminate, according to the value of the
	 * {@link #setAwaitTerminationSeconds "awaitTerminationSeconds"} property.
	 */
	private void awaitTerminationIfNecessary(ExecutorService executor) {
		// 这里的 awaitTerminationMillis 必须要 > 0
		if (this.awaitTerminationMillis > 0) {
			try {
				// forcus 这里的 awaitTermination() 是jdk线程池的方法，阻塞当前线程（执行 shutdown 的线程），等待线程池中所有任务执行完毕，最多等指定时间。
				/*
					任务全部完成 → 返回 true，正常关闭
					超时 → 返回 false，打 warn 日志，但不会强制关闭！
					被中断 → catch 后恢复中断标志 Thread.currentThread().interrupt()
					---
					但是默认的行为：
						waitForTasksToCompleteOnShutdown = false    // 强制关闭
						awaitTerminationMillis = 0                   // 不等待
					 		-- 容器关闭 → shutdownNow() 中断所有线程 → 不等待 → 直接继续销毁其他 Bean
					这里有个坑：只设 waitForTasks 不设 awaitTermination
						调 shutdown() 后不阻塞等待！ 容器立刻继续销毁其他 Bean（数据库连接池、Redis 连接等）。
						如果异步任务还在执行中依赖这些资源——资源已经被销毁了，任务报错。
				 */
				if (!executor.awaitTermination(this.awaitTerminationMillis, TimeUnit.MILLISECONDS)) {
					// 如果超时了，在这里只是打印warn日志，没有进一步强制关闭。线程池中的任务还会继续跑（因为调的是 shutdown() 不是 shutdownNow()），但容器不再等了，继续销毁其他 Bean。
					if (logger.isWarnEnabled()) {
						logger.warn("Timed out while waiting for executor" +
								(this.beanName != null ? " '" + this.beanName + "'" : "") + " to terminate");
					}
				}
			}
			catch (InterruptedException ex) {
				if (logger.isWarnEnabled()) {
					logger.warn("Interrupted while waiting for executor" +
							(this.beanName != null ? " '" + this.beanName + "'" : "") + " to terminate");
				}
				Thread.currentThread().interrupt();
			}
		}
	}

}
