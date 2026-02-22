package com.debug.aop_demo_transactional.service;

import com.debug.aop_demo_transactional.dao.UserDao;
import com.debug.aop_demo_transactional.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.util.List;

/**
 * 用户服务层 - 演示事务功能
 */
@Service
public class UserService {

    @Autowired
    private UserDao userDao;

    /**
     * 简单保存 - 有事务
     */
    @Transactional
    public void saveUser(User user) {
        System.out.println("[Service] 开始保存用户: " + user.getName());
        userDao.save(user);
        System.out.println("[Service] 保存完成");
    }

    /**
     * 保存多个用户 - 演示事务回滚
     */
    @Transactional
    public void saveUsers(List<User> users) {
        System.out.println("[Service] 开始批量保存，共 " + users.size() + " 个用户");
        
        for (int i = 0; i < users.size(); i++) {
            User user = users.get(i);
            System.out.println("[Service] 处理第 " + (i + 1) + " 个用户: " + user.getName());
            
            if (user.getAge() < 0) {
                throw new IllegalArgumentException("用户年龄不能为负数: " + user.getName());
            }
            
            userDao.save(user);
        }
        
        System.out.println("[Service] 批量保存完成");
    }

    /**
     * 演示：事务提交后执行操作（如发送消息）
     * 
     * 核心点：
     * 1. 注册的回调不会立即执行
     * 2. Spring会在事务提交后自动调用afterCommit()
     * 3. 如果事务回滚，afterCommit()不会被调用
     */
    @Transactional
    public void saveUserWithCallback(User user) {
        System.out.println("[Service] 开始保存用户");
        
        // 保存数据
        userDao.save(user);
        
        // 注册事务同步回调
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                System.out.println("[回调] 事务已提交，现在发送消息");
                // kafkaTemplate.send("order-topic", order);  // 实际场景
            }
        });
        
        System.out.println("[Service] 方法执行完毕");
    }

    /**
     * 内层方法 - REQUIRED（默认），加入外层事务
     * 模拟扣款失败，抛出 RuntimeException
     * Spring 发现不是新事务 → 不能直接 rollback → 调用 doSetRollbackOnly() → 在 ConnectionHolder 上打 Global 标记
     */
    @Transactional
    public void deductBalance() {
        System.out.println("[内层] deductBalance() 开始执行...");
        System.out.println("[内层] 模拟扣款失败，抛出 RuntimeException...");
        throw new RuntimeException("余额不足，扣款失败！");
    }

    /**
     * 查询所有用户
     */
    public List<User> findAllUsers() {
        return userDao.findAll();
    }

    /**
     * 打印所有用户
     */
    public void printAllUsers() {
        userDao.printAll();
    }

    /**
     * 清空数据
     */
    public void clearData() {
        userDao.clear();
    }
}
