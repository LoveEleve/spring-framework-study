package com.debug.aspect;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.stereotype.Component;

/**
 * 日志切面 - 演示 Spring AOP
 * 
 * AOP 核心概念：
 * - Aspect（切面）：横切关注点的模块化
 * - Pointcut（切点）：匹配连接点的表达式
 * - Advice（通知）：在切点执行的动作
 * - JoinPoint（连接点）：程序执行的某个特定位置
 * 
 * 调试流程：
 * 1. AbstractAutoProxyCreator.postProcessAfterInitialization() - 代理创建入口
 * 2. AbstractAutoProxyCreator.wrapIfNecessary() - 判断是否需要代理
 * 3. JdkDynamicAopProxy.invoke() - JDK动态代理执行
 * 4. ReflectiveMethodInvocation.proceed() - 责任链模式执行拦截器链
 */
@Aspect
@Component
public class LoggingAspect {

	/**
	 * 切点表达式：匹配 service 包下所有类的所有方法
	 */
	@Pointcut("execution(* com.debug.service..*.*(..))")
	public void serviceLayer() {
		// 切点定义
	}

	/**
	 * 前置通知 - 方法执行前
	 * 
	 * 断点：MethodBeforeAdviceInterceptor.invoke()
	 */
	@Before("serviceLayer()")
	public void logBefore(JoinPoint joinPoint) {
		String methodName = joinPoint.getSignature().getName();
		System.out.println("[AOP-前置] 方法执行前: " + methodName);
	}

	/**
	 * 后置通知 - 方法正常返回后
	 * 
	 * 断点：AfterReturningAdviceInterceptor.invoke()
	 */
	@AfterReturning(pointcut = "serviceLayer()", returning = "result")
	public void logAfterReturning(JoinPoint joinPoint, Object result) {
		String methodName = joinPoint.getSignature().getName();
		System.out.println("[AOP-后置] 方法执行后: " + methodName + ", 返回值: " + result);
	}

	/**
	 * 异常通知 - 方法抛出异常后
	 * 
	 * 断点：AfterThrowingAdviceInterceptor.invoke()
	 */
	@AfterThrowing(pointcut = "serviceLayer()", throwing = "ex")
	public void logAfterThrowing(JoinPoint joinPoint, Exception ex) {
		String methodName = joinPoint.getSignature().getName();
		System.out.println("[AOP-异常] 方法异常: " + methodName + ", 异常信息: " + ex.getMessage());
	}

	/**
	 * 最终通知 - 无论方法是否异常都会执行
	 * 
	 * 断点：AspectJAfterAdvice.invoke()
	 */
	@After("serviceLayer()")
	public void logAfter(JoinPoint joinPoint) {
		String methodName = joinPoint.getSignature().getName();
		System.out.println("[AOP-最终] 方法执行完毕: " + methodName);
	}

	/**
	 * 环绕通知 - 最强大的通知类型，可以控制方法执行
	 * 
	 * 断点：AspectJAroundAdvice.invoke()
	 * 核心：ProceedingJoinPoint.proceed() - 继续执行下一个拦截器或目标方法
	 */
	@Around("execution(* com.debug.service..save*(..))")
	public Object logAround(ProceedingJoinPoint pjp) throws Throwable {
		String methodName = pjp.getSignature().getName();
		long startTime = System.currentTimeMillis();

		System.out.println("[AOP-环绕] 方法开始: " + methodName);

		try {
			// 执行目标方法
			Object result = pjp.proceed();

			long elapsedTime = System.currentTimeMillis() - startTime;
			System.out.println("[AOP-环绕] 方法结束: " + methodName + ", 耗时: " + elapsedTime + "ms");

			return result;
		} catch (Throwable throwable) {
			System.out.println("[AOP-环绕] 方法异常: " + methodName);
			throw throwable;
		}
	}
}
