package com.debug.aop_demo_tx_1;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class TxTest {

	public static void main(String[] args) {
		AnnotationConfigApplicationContext ctx =
				new AnnotationConfigApplicationContext(TxConfig.class);

		UserService userService = ctx.getBean(UserService.class);
		userService.clear();

		// 1. 正常提交
		System.out.println("===== 测试1: 正常提交 =====");
		userService.save("张三");
		System.out.println("数据库记录数: " + userService.count());  // 预期: 1

		// 2. 异常回滚
		System.out.println("\n===== 测试2: 异常回滚 =====");
		try {
			userService.saveAndFail("李四");
		} catch (RuntimeException e) {
			System.out.println("捕获异常: " + e.getMessage());
		}
		System.out.println("数据库记录数: " + userService.count());  // 预期: 仍然是1（李四被回滚了）

		ctx.close();
	}
}
