package com.debug.aop_demo_1;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * @Classname BasicAspect
 * @Date 1/30/26
 * @Created by ywj
 */
@Aspect
@Component
public class BasicAspect {
	/**
	 * 定义切点：拦截 CalculatorService 的所有方法
	 */
	@Pointcut("execution(* com.debug.aop_demo_1.CalculatorService.*(..))")
	public void calculatorMethods() {
	}

	/**
	 * 1. 前置通知 @Before
	 * 在目标方法执行之前执行
	 */
	@Before("calculatorMethods()")
	public void before(JoinPoint joinPoint) {
		String methodName = joinPoint.getSignature().getName();
		Object[] args = joinPoint.getArgs();
		System.out.println("【前置通知 @Before】方法名: " + methodName + ", 参数: " + Arrays.toString(args));
	}

	/**
	 * 2. 后置通知 @After
	 * 在目标方法执行之后执行（无论是否抛出异常）
	 * 类似于 finally 块
	 */
	@After("calculatorMethods()")
	public void after(JoinPoint joinPoint) {
		String methodName = joinPoint.getSignature().getName();
		System.out.println("【后置通知 @After】方法名: " + methodName + " 执行完毕");
	}

	/**
	 * 3. 返回通知 @AfterReturning
	 * 在目标方法正常返回后执行（可以获取返回值）
	 * 如果方法抛出异常，则不会执行
	 */
	@AfterReturning(pointcut = "calculatorMethods()", returning = "result")
	public void afterReturning(JoinPoint joinPoint, Object result) {
		String methodName = joinPoint.getSignature().getName();
		System.out.println("【返回通知 @AfterReturning】方法名: " + methodName + ", 返回值: " + result);
	}

	/**
	 * 4. 异常通知 @AfterThrowing
	 * 在目标方法抛出异常后执行（可以获取异常对象）
	 * 如果方法正常返回，则不会执行
	 */
	@AfterThrowing(pointcut = "calculatorMethods()", throwing = "exception")
	public void afterThrowing(JoinPoint joinPoint, Exception exception) {
		String methodName = joinPoint.getSignature().getName();
		System.out.println("【异常通知 @AfterThrowing】方法名: " + methodName
				+ ", 异常类型: " + exception.getClass().getSimpleName()
				+ ", 异常信息: " + exception.getMessage());
	}

	/**
	 * 5. 环绕通知 @Around
	 * 完全控制目标方法的执行
	 * 可以在方法执行前后做任何操作，甚至可以不执行目标方法
	 */
	@Around("calculatorMethods()")
	public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
		String methodName = joinPoint.getSignature().getName();
		System.out.println("【环绕通知 @Around - 前】方法名: " + methodName + " 开始执行");

		Object result = null;
		try {
			// 执行目标方法
			result = joinPoint.proceed();
			System.out.println("【环绕通知 @Around - 后】方法名: " + methodName + " 执行成功");
		} catch (Exception e) {
			System.out.println("【环绕通知 @Around - 异常】方法名: " + methodName + " 执行异常: " + e.getMessage());
			throw e; // 重新抛出异常
		}

		return result;
	}
}
