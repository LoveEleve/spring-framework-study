package com.debug.aop_demo_1;

import org.springframework.stereotype.Service;

/**
 * @Classname CalculatorService
 * @Date 1/30/26
 * @Created by ywj
 */
@Service
public class CalculatorService {
	public int add(int a, int b) {
		System.out.println(">>> 执行目标方法: add(" + a + ", " + b + ")");
		int result = a + b;
		return result;
	}

	public int divide(int a, int b) {
		System.out.println(">>> 执行目标方法: divide(" + a + ", " + b + ")");
		return a / b; // 当 b=0 时会抛出 ArithmeticException
	}
}


