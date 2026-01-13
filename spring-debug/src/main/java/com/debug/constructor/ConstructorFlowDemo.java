package com.debug.constructor;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * 可视化展示构造器执行流程
 * 
 * @author Spring源码学习者
 */
public class ConstructorFlowDemo {

	public static void main(String[] args) {
		System.out.println("========== 构造器执行流程可视化 ==========\n");

		printStep("【步骤0】准备调用 AnnotationConfigApplicationContext 无参构造器");
		
		// 开始执行
		long startTime = System.currentTimeMillis();
		
		printStep("【步骤1】调用父类 GenericApplicationContext 构造器");
		printSubStep("创建 DefaultListableBeanFactory");
		
		printStep("【步骤2】创建 AnnotatedBeanDefinitionReader");
		printSubStep("创建 ConditionEvaluator（条件评估器）");
		printSubStep("注册内置的后置处理器...");
		
		// 实际创建容器
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
		
		long endTime = System.currentTimeMillis();
		
		printStep("【步骤3】创建 ClassPathBeanDefinitionScanner");
		printSubStep("设置默认过滤器（@Component、@Service等）");
		
		System.out.println("\n========== 构造器执行完成 ==========");
		System.out.println("耗时: " + (endTime - startTime) + "ms\n");

		// 验证创建的对象
		System.out.println("========== 验证创建的对象 ==========\n");
		
		System.out.println("1. IoC容器核心:");
		System.out.println("   类型: " + context.getBeanFactory().getClass().getSimpleName());
		
		System.out.println("\n2. 已注册的BeanDefinition数量: " + context.getBeanDefinitionCount());
		
		System.out.println("\n3. 内置的后置处理器:");
		String[] beanNames = context.getBeanDefinitionNames();
		int count = 0;
		for (String beanName : beanNames) {
			BeanDefinition bd = context.getBeanFactory().getBeanDefinition(beanName);
			if (bd.getRole() == BeanDefinition.ROLE_INFRASTRUCTURE) {
				count++;
				String className = bd.getBeanClassName();
				String simpleName = className != null ? 
					className.substring(className.lastIndexOf('.') + 1) : "Unknown";
				System.out.printf("   [%d] %-50s (%s)%n", 
					count, beanName, simpleName);
			}
		}

		System.out.println("\n========== 容器状态 ==========\n");
		System.out.println("✅ DefaultListableBeanFactory - 已创建");
		System.out.println("✅ AnnotatedBeanDefinitionReader - 已创建");
		System.out.println("✅ ClassPathBeanDefinitionScanner - 已创建");
		System.out.println("✅ 6个核心后置处理器 - 已注册");
		System.out.println("\n⚠️  容器尚未刷新（refresh），Bean尚未实例化");

		context.close();
	}

	private static void printStep(String message) {
		System.out.println("\n" + message);
		System.out.println("─".repeat(60));
	}

	private static void printSubStep(String message) {
		System.out.println("  ├─ " + message);
	}
}
