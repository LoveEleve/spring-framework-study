/*
 * Copyright 2002-2020 the original author or authors.
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

package org.springframework.core.task;

/**
 * A callback interface for a decorator to be applied to any {@link Runnable}
 * about to be executed.
 *
 * <p>Note that such a decorator is not necessarily being applied to the
 * user-supplied {@code Runnable}/{@code Callable} but rather to the actual
 * execution callback (which may be a wrapper around the user-supplied task).
 *
 * <p>The primary use case is to set some execution context around the task's
 * invocation, or to provide some monitoring/statistics for task execution.
 *
 * <p><b>NOTE:</b> Exception handling in {@code TaskDecorator} implementations
 * may be limited. Specifically in case of a {@code Future}-based operation,
 * the exposed {@code Runnable} will be a wrapper which does not propagate
 * any exceptions from its {@code run} method.
 *
 * forcus 注意
 * 	当你调用 executor.submit(task) 时，JDK 内部会把 task 包装成 FutureTask，而 FutureTask.run() 在执行时，如果抛出了异常，那么会被“吞掉”,也即会保存到future中
 * 	外部通过future.get()就能得到异常，所以在 TaskDecorator 里 try-catch 包装 runnable.run()，对 submit() 路径是抓不到异常的。
 * 	也即 TaskDecorator 的核心用途是上下文传递和监控，不是异常处理。
 *
 * @author Juergen Hoeller
 * @since 4.3
 * @see TaskExecutor#execute(Runnable)
 * @see SimpleAsyncTaskExecutor#setTaskDecorator
 * @see org.springframework.core.task.support.TaskExecutorAdapter#setTaskDecorator
 */
@FunctionalInterface
public interface TaskDecorator {

	/**
	 * Decorate the given {@code Runnable}, returning a potentially wrapped
	 * {@code Runnable} for actual execution, internally delegating to the
	 * original {@link Runnable#run()} implementation.
	 * @param runnable the original {@code Runnable}
	 * @return the decorated {@code Runnable}
	 */
	/*
		TaskDecorator 示例：
		需要注意的是：decorate() 方法本身在调用方线程执行，所以闭包能捕获调用方线程的 ThreadLocal 值。而返回的新 Runnable 的 run() 在异步线程执行，在其中恢复上下文。
			 executor.setTaskDecorator(runnable -> {
				//在这里的runnable参数就是业务方法(异步方法-@Async方法)
				//并且需要注意的是,该方法是在主线程(也就是提交任务的线程)，此时读取的都是主线程的 ThreadLocal，能拿到对应的值
				//上面的3个变量（mdcMap、requestAttributes、securityContext）是局部变量，被下面的 lambda 闭包捕获
			// ===== 调用方线程执行（捕获上下文）=====
			Map<String, String> mdcMap = MDC.getCopyOfContextMap();
			RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
			SecurityContext securityContext = SecurityContextHolder.getContext();

			// ===== 这里返回一个新的runnable ======
			// 它将代替原来的runnable被提交到线程池中，注意,这个lambda的run()方法是在异步线程(线程池中的worker线程)执行的
			// 这里有个关键点就是：java的lambda/匿名内部类可以捕获外层方法的局部变量,此时异步线程也能够访问到
			return () -> {
				// ===== 异步线程执行（恢复上下文）=====
				try {
					// forcus 注意，在spring5.x版本,TaskDecorator 只有接口，你得自己写 lambda 实现
					// 在这里给出的一个例子:是有明显问题的，那就是如果异步线程本身设置了自己的值，那么在这里就直接覆盖掉了,正确应该是和TTL一样:capture -> backup -> replay -> restore
					// 其实原理我已经在TTL的文章讲解过了,在这里只是为了了解 TaskDecorator
					// forcus 但是在spring 6.1(springboot 3.2+)中引入了ContextPropagatingTaskDecorator来解决这个问题(后续有机会在讲解吧)
					if (mdcMap != null) MDC.setContextMap(mdcMap); // 把traceId设到异步线程的MDC中
					RequestContextHolder.setRequestAttributes(requestAttributes);  // 把request设到异步线程中
					SecurityContextHolder.setContext(securityContext);  // 把用户信息设到异步线程中
					// ====== 异步线程的 ThreadLocal 中也有了和主线程一样的值。接下来执行业务逻辑
					runnable.run();
				} finally {
					// forcus 必须清理,因为线程池中的worker线程是复用的!!
					MDC.clear();
					RequestContextHolder.resetRequestAttributes();
					SecurityContextHolder.clearContext();
				}
			};
			});
	 */
	Runnable decorate(Runnable runnable);

}
