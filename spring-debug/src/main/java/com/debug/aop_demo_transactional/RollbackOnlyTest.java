package com.debug.aop_demo_transactional;

import com.debug.aop_demo_transactional.dao.UserDao;
import com.debug.aop_demo_transactional.service.OrderService;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * 验证 Global RollbackOnly vs Local RollbackOnly
 *
 * 核心验证点：
 * 1. Global RollbackOnly：内层事务抛异常 → 打在 ConnectionHolder 上 → 外层 catch 住异常也没用 → commit 失败
 * 2. Local RollbackOnly：应用代码主动调用 setRollbackOnly() → 打在 TransactionStatus 上 → commit 时转为 rollback
 * 3. 正常提交：没有任何回滚标记 → commit 成功
 */
public class RollbackOnlyTest {

	public static void main(String[] args) {
		System.out.println("========== Global vs Local RollbackOnly 验证 ==========\n");

		AnnotationConfigApplicationContext context =
				new AnnotationConfigApplicationContext(TransactionalConfig.class);

		OrderService orderService = context.getBean(OrderService.class);
		UserDao userDao = context.getBean(UserDao.class);

		// ========== 场景1：Global RollbackOnly ==========
		System.out.println("====================================================");
		System.out.println("场景1：Global RollbackOnly");
		System.out.println("预期：外层 catch 住内层异常，但 commit 仍然失败");
		System.out.println("====================================================");
		userDao.clear();
		try {
			orderService.testGlobalRollbackOnly();
			System.out.println("[结果] commit 成功（不应该出现这个！）");
		} catch (Exception e) {
			System.out.println("[结果] 异常类型: " + e.getClass().getSimpleName());
			System.out.println("[结果] 异常信息: " + e.getMessage());
		}
		System.out.println("[数据库验证] 数据是否被回滚？");
		userDao.printAll();

		// ========== 场景2：Local RollbackOnly ==========
		System.out.println("\n====================================================");
		System.out.println("场景2：Local RollbackOnly");
		System.out.println("预期：方法正常返回，但数据被回滚，不抛异常");
		System.out.println("====================================================");
		userDao.clear();
		try {
			orderService.testLocalRollbackOnly();
			System.out.println("[结果] 方法正常返回，没有异常");
		} catch (Exception e) {
			System.out.println("[结果] 异常类型: " + e.getClass().getSimpleName());
			System.out.println("[结果] 异常信息: " + e.getMessage());
		}
		System.out.println("[数据库验证] 数据是否被回滚？");
		userDao.printAll();

		// ========== 场景3：正常提交 ==========
		System.out.println("\n====================================================");
		System.out.println("场景3：正常提交（对照组）");
		System.out.println("预期：commit 成功，数据已入库");
		System.out.println("====================================================");
		userDao.clear();
		try {
			orderService.testNormalCommit();
			System.out.println("[结果] commit 成功");
		} catch (Exception e) {
			System.out.println("[结果] 异常类型: " + e.getClass().getSimpleName());
			System.out.println("[结果] 异常信息: " + e.getMessage());
		}
		System.out.println("[数据库验证] 数据是否已入库？");
		userDao.printAll();

		System.out.println("\n========== 测试结束 ==========");
		context.close();
	}
}
