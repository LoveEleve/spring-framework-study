package com.debug.aop_demo_transactional;

import com.debug.aop_demo_transactional.entity.User;
import com.debug.aop_demo_transactional.service.UserService;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.Arrays;
import java.util.List;

/**
 * 事务测试类
 */
public class TransactionalTest {

    public static void main(String[] args) {
        System.out.println("========== Spring 事务演示 ==========\n");

        // 创建Spring容器
        AnnotationConfigApplicationContext context = 
                new AnnotationConfigApplicationContext(TransactionalConfig.class);

        UserService userService = context.getBean(UserService.class);

        // 测试1：简单事务提交
        test1_SimpleTransaction(userService);

        // 测试2：事务回滚
        test2_TransactionRollback(userService);

        context.close();
        System.out.println("\n========== 测试结束 ==========");
    }

    /**
     * 测试1：简单事务提交
     */
    private static void test1_SimpleTransaction(UserService userService) {
        System.out.println("\n----- 测试1：简单事务提交 -----");
        
        // 清空数据
        userService.clearData();

        // 保存单个用户（应该成功）
        User user1 = new User(null, "张三", 25);
        userService.saveUser(user1);

        // 查看结果
        System.out.println("\n测试1结果:");
        userService.printAllUsers();
    }

    /**
     * 测试2：事务回滚
     */
    private static void test2_TransactionRollback(UserService userService) {
        System.out.println("\n----- 测试2：事务回滚演示 -----");
        System.out.println("说明：批量保存3个用户，第3个用户年龄为-1（非法），触发异常，事务回滚\n");

        // 清空数据
        userService.clearData();

        // 准备3个用户，第3个年龄为负数（会触发异常）
        User user1 = new User(null, "李四", 30);
        User user2 = new User(null, "王五", 28);
        User user3 = new User(null, "赵六", -1);  // 年龄非法，会触发异常

        List<User> users = Arrays.asList(user1, user2, user3);

        try {
            // 执行批量保存（会失败回滚）
            userService.saveUsers(users);
        } catch (IllegalArgumentException e) {
            System.out.println("\n[预期异常] " + e.getMessage());
            System.out.println("[事务状态] 应该已回滚，数据库中无数据");
        }

        // 查看结果（应该为空，因为事务回滚了）
        System.out.println("\n测试2结果（应该为空，因为事务回滚）:");
        userService.printAllUsers();
    }
}
