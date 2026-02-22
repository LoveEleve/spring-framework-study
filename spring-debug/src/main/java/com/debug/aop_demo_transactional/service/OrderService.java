package com.debug.aop_demo_transactional.service;

import com.debug.aop_demo_transactional.dao.UserDao;
import com.debug.aop_demo_transactional.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

/**
 * 订单服务 - 用于验证 Global vs Local RollbackOnly
 *
 * 场景说明：
 * OrderService.createOrder()（外层事务）调用 UserService.deductBalance()（内层 REQUIRED 加入外层事务）
 * 内层抛异常 → 外层 catch 住 → 外层尝试 commit → 验证是否会抛 UnexpectedRollbackException
 */
@Service
public class OrderService {

	@Autowired
	private UserService userService;

	@Autowired
	private UserDao userDao;

	/**
	 * 场景1：验证 Global RollbackOnly
	 *
	 * 外层开事务 → 调用内层（REQUIRED，加入外层事务）→ 内层抛异常
	 * → Spring 在内层打 Global RollbackOnly → 外层 catch 住异常
	 * → 外层尝试 commit → 检测到 Global RollbackOnly → 抛 UnexpectedRollbackException
	 */
	@Transactional
	public void testGlobalRollbackOnly() {
		System.out.println("\n[外层] 开始创建订单，先插入一条用户数据...");
		userDao.save(new User(null, "外层插入的用户", 30));

		System.out.println("[外层] 调用内层 deductBalance()，内层会抛异常...");
		try {
			userService.deductBalance();  // 内层抛 RuntimeException，Spring 打 Global 标记
		} catch (Exception e) {
			System.out.println("[外层] catch 住了内层异常: " + e.getMessage());
			System.out.println("[外层] 我以为 catch 住就没事了，继续执行...");
		}

		System.out.println("[外层] 插入第二条用户数据...");
		userDao.save(new User(null, "外层第二次插入", 25));

		System.out.println("[外层] 方法正常返回，等待 Spring 调用 commit...");
		// 这里方法正常返回，Spring 会尝试 commit
		// 但 commit 时会检查 Global RollbackOnly → true → 转为 rollback → 抛 UnexpectedRollbackException
	}

	/**
	 * 场景2：验证 Local RollbackOnly
	 *
	 * 在当前事务中主动调用 setRollbackOnly()，标记本地回滚
	 * 方法正常返回 → commit 时检查 Local RollbackOnly → 转为 rollback（但不抛异常）
	 */
	@Transactional
	public void testLocalRollbackOnly() {
		System.out.println("\n[外层] 开始执行，插入一条用户数据...");
		userDao.save(new User(null, "Local测试用户", 20));

		System.out.println("[外层] 主动调用 setRollbackOnly()，标记本地回滚...");
		TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();

		System.out.println("[外层] 方法正常返回，等待 Spring 调用 commit...");
		// commit 时检查 Local RollbackOnly → true → processRollback(unexpected=false) → 回滚，不抛异常
	}

	/**
	 * 场景3：对比 - 正常提交（无任何回滚标记）
	 */
	@Transactional
	public void testNormalCommit() {
		System.out.println("\n[外层] 正常流程，插入一条用户数据...");
		userDao.save(new User(null, "正常提交用户", 35));
		System.out.println("[外层] 方法正常返回，等待 Spring 调用 commit...");
	}
}
