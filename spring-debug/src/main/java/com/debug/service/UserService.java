package com.debug.service;

import com.debug.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 用户服务 - 演示依赖注入、AOP
 * 
 * 核心调试点：
 * 1. 构造器注入流程
 * 2. AOP 代理创建
 * 3. 方法拦截执行
 */
@Service
public class UserService {

	private final UserRepository userRepository;

	/**
	 * 构造器注入 - Spring 推荐方式
	 * 
	 * 断点位置：
	 * - ConstructorResolver.autowireConstructor()
	 * - AutowiredAnnotationBeanPostProcessor.postProcessProperties()
	 */
	@Autowired
	public UserService(UserRepository userRepository) {
		System.out.println("[依赖注入] UserService 构造器注入 UserRepository");
		this.userRepository = userRepository;
	}

	/**
	 * 保存用户 - 会被AOP拦截
	 */
	public void saveUser(String username) {
		System.out.println("\n[业务方法] UserService.saveUser() 开始执行");
		System.out.println("[业务方法] 保存用户: " + username);
		
		// 调用 Repository
		userRepository.save(username);

		System.out.println("[业务方法] UserService.saveUser() 执行完成\n");
	}

	/**
	 * 查询用户
	 */
	public String getUser(Long id) {
		System.out.println("[业务方法] 查询用户ID: " + id);
		return userRepository.findById(id);
	}
}
