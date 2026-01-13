package com.debug.service;

import com.debug.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 带事务的用户服务（进阶版）
 * 
 * 事务调试流程：
 * 1. TransactionInterceptor.invoke() - 事务拦截器入口
 * 2. TransactionAspectSupport.invokeWithinTransaction() - 事务切面支持
 * 3. AbstractPlatformTransactionManager.getTransaction() - 获取事务
 * 4. DataSourceTransactionManager.doBegin() - 开启事务
 * 5. 执行目标方法
 * 6. AbstractPlatformTransactionManager.commit/rollback() - 提交或回滚
 */
@Service
public class TransactionalUserService {

	private final UserRepository userRepository;

	@Autowired
	public TransactionalUserService(UserRepository userRepository) {
		System.out.println("[依赖注入] TransactionalUserService 构造器注入");
		this.userRepository = userRepository;
	}

	/**
	 * 带事务的保存方法
	 * 
	 * 关键断点：
	 * - TransactionInterceptor.invoke()
	 * - AbstractPlatformTransactionManager.getTransaction()
	 * - DataSourceTransactionManager.doBegin()
	 * - AbstractPlatformTransactionManager.commit()
	 */
	@Transactional(rollbackFor = Exception.class)
	public void saveUserWithTransaction(String username) {
		System.out.println("\n[事务方法] 保存用户: " + username);
		userRepository.save(username);
		System.out.println("[事务方法] 保存完成\n");
	}

	/**
	 * 只读事务
	 */
	@Transactional(readOnly = true)
	public String getUserWithTransaction(Long id) {
		System.out.println("[事务方法] 查询用户ID: " + id);
		return userRepository.findById(id);
	}
}
