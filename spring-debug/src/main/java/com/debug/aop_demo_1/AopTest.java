package com.debug.aop_demo_1;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * @Classname AopTest
 * @Date 1/30/26
 * @Created by ywj
 */
public class AopTest {
	public static void main(String[] args) {
		// 创建 Spring 容器
		AnnotationConfigApplicationContext context =
				new AnnotationConfigApplicationContext(AppConfig.class);

		// 获取代理对象
		CalculatorService calculator = context.getBean(CalculatorService.class);

		// ========== 测试场景1：正常执行 ==========
		System.out.println("\n" + "=".repeat(80));
		System.out.println("【测试场景1】正常执行的方法");
		System.out.println("=".repeat(80));
		try {
			int result = calculator.add(10, 5);
			System.out.println("✅ 最终结果: " + result);
		} catch (Exception e) {
			System.out.println("❌ 捕获异常: " + e.getMessage());
		}

		// ========== 测试场景2：抛出异常 ==========
		System.out.println("\n" + "=".repeat(80));
		System.out.println("【测试场景2】抛出异常的方法");
		System.out.println("=".repeat(80));
		try {
			int result = calculator.divide(10, 0);
			System.out.println("✅ 最终结果: " + result);
		} catch (Exception e) {
			System.out.println("❌ 捕获异常: " + e.getClass().getSimpleName() + " - " + e.getMessage());
		}

		context.close();
	}
}
