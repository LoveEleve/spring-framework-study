package com.debug.repository;

import org.springframework.stereotype.Repository;

/**
 * 用户数据访问层 - 演示 @Repository 注解
 * 
 * @Repository 特性：
 * 1. 继承自 @Component
 * 2. 标识数据访问层
 * 3. 支持持久化异常转换（PersistenceExceptionTranslationPostProcessor）
 */
@Repository
public class UserRepository {

	public UserRepository() {
		System.out.println("[依赖注入] UserRepository 实例创建");
	}

	/**
	 * 保存用户到数据库
	 */
	public void save(String username) {
		System.out.println("  [数据库操作] 插入用户: " + username);
		// 模拟数据库操作
	}

	/**
	 * 根据ID查询用户
	 */
	public String findById(Long id) {
		System.out.println("  [数据库操作] 查询用户ID: " + id);
		return "User_" + id;
	}
}
