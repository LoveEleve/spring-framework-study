package com.debug.aop_demo_transactional;

import com.debug.aop_demo_transactional.entity.User;
import com.debug.aop_demo_transactional.service.UserService;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * 演示TransactionSynchronization - 事务提交后执行回调
 */
public class TransactionSynchronizationTest {

    public static void main(String[] args) {
        AnnotationConfigApplicationContext context = 
            new AnnotationConfigApplicationContext(TransactionalConfig.class);
        
        UserService userService = context.getBean(UserService.class);
        
        System.out.println("========== 演示事务提交后执行回调 ==========\n");
        
        User user = new User();
        user.setName("张三");
        user.setAge(25);
        
        // 调用带回调的方法
        userService.saveUserWithCallback(user);
        
        System.out.println("\n========== 完成 ==========");
        
        context.close();
    }
}
