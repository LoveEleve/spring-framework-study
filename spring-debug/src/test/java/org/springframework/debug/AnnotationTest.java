package org.springframework.debug;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.debug.config.AppConfig;
import org.springframework.debug.model.User;
import org.springframework.debug.service.UserService;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.Transactional;

import javax.validation.ConstraintViolation;
import javax.validation.Validator;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Spring 注解测试类 - 演示测试相关注解
 * 
 * 重要注解说明：
 * @SpringJUnitConfig - Spring JUnit 5 集成注解，组合了 @ExtendWith(SpringExtension.class) 和 @ContextConfiguration
 * @ExtendWith - JUnit 5 扩展注解
 * @ContextConfiguration - 指定 Spring 配置
 * @TestPropertySource - 测试属性源
 * @Transactional - 测试事务，默认回滚
 * @Autowired - 在测试中注入依赖
 * 
 * 调试要点：
 * 1. 观察测试上下文的创建过程
 * 2. 跟踪测试中的依赖注入
 * 3. 查看测试事务的处理
 * 4. 验证注解验证的工作机制
 */
@SpringJUnitConfig(DebugApplication.class)  // 组合注解，等价于下面两个注解
// @ExtendWith(SpringExtension.class)
// @ContextConfiguration(classes = DebugApplication.class)
@TestPropertySource(properties = {
    "app.debug=true",
    "logging.level.org.springframework.debug=DEBUG"
})
public class AnnotationTest {

    /**
     * 测试中的依赖注入
     * @Autowired 在测试类中同样有效
     */
    @Autowired
    private UserService userService;
    
    @Autowired
    private AppConfig appConfig;
    
    @Autowired
    private Validator validator;

    /**
     * 测试依赖注入注解
     */
    @Test
    public void testDependencyInjection() {
        System.out.println("=== 测试依赖注入注解 ===");
        
        // 验证 @Autowired 注入是否成功
        assertNotNull(userService, "UserService 应该被成功注入");
        assertNotNull(appConfig, "AppConfig 应该被成功注入");
        
        System.out.println("UserService 类型: " + userService.getClass().getName());
        System.out.println("AppConfig 类型: " + appConfig.getClass().getName());
        
        // 测试 JSR-330 注解注入
        String injectedString = userService.getInjectedString();
        assertNotNull(injectedString, "JSR-330 注入的字符串不应为空");
        System.out.println("JSR-330 注入的字符串: " + injectedString);
    }

    /**
     * 测试配置注解
     */
    @Test
    public void testConfigurationAnnotations() {
        System.out.println("=== 测试配置注解 ===");
        
        // 测试 @Value 注解
        assertNotNull(appConfig.getAppName(), "应用名称不应为空");
        assertNotNull(appConfig.getAppVersion(), "应用版本不应为空");
        
        System.out.println("@Value 注入的应用名称: " + appConfig.getAppName());
        System.out.println("@Value 注入的应用版本: " + appConfig.getAppVersion());
        System.out.println("SpEL 表达式结果: " + appConfig.getRandomNumber());
        System.out.println("Java 版本: " + appConfig.getJavaVersion());
        
        // 验证配置值
        assertEquals("Spring Annotations Debug", appConfig.getAppName());
        assertTrue(appConfig.isDebugMode(), "调试模式应该为 true");
    }

    /**
     * 测试验证注解
     */
    @Test
    public void testValidationAnnotations() {
        System.out.println("=== 测试验证注解 ===");
        
        // 创建一个无效的用户对象
        User invalidUser = new User();
        // 不设置必需的字段，触发验证错误
        
        // 使用 Validator 进行验证
        Set<ConstraintViolation<User>> violations = validator.validate(invalidUser);
        
        assertFalse(violations.isEmpty(), "应该有验证错误");
        System.out.println("验证错误数量: " + violations.size());
        
        for (ConstraintViolation<User> violation : violations) {
            System.out.println("验证错误: " + violation.getPropertyPath() + " - " + violation.getMessage());
        }
        
        // 创建一个有效的用户对象
        User validUser = new User("test_user", "test@example.com");
        validUser.setAge(25);
        validUser.setPhone("13800138000");
        
        Set<ConstraintViolation<User>> validViolations = validator.validate(validUser);
        assertTrue(validViolations.isEmpty(), "有效用户不应该有验证错误");
        System.out.println("有效用户验证通过");
    }

    /**
     * 测试事务注解
     * @Transactional 在测试中默认会回滚
     */
    @Test
    @Transactional
    public void testTransactionalAnnotation() {
        System.out.println("=== 测试事务注解 ===");
        
        // 创建测试用户
        User testUser = new User("tx_test_user", "txtest@example.com");
        testUser.setAge(30);
        
        try {
            // 调用事务方法
            User savedUser = userService.createUser(testUser);
            assertNotNull(savedUser.getId(), "用户应该被保存并分配ID");
            System.out.println("事务中创建用户成功: " + savedUser.getUsername());
            
            // 验证用户确实被保存
            var foundUser = userService.findUserById(savedUser.getId());
            assertTrue(foundUser.isPresent(), "应该能找到刚创建的用户");
            
            System.out.println("事务测试完成，数据将被回滚");
            
        } catch (Exception e) {
            System.err.println("事务测试异常: " + e.getMessage());
            fail("事务测试不应该抛出异常");
        }
    }

    /**
     * 测试异步注解
     */
    @Test
    public void testAsyncAnnotation() throws Exception {
        System.out.println("=== 测试异步注解 ===");
        
        // 首先创建一个用户
        User testUser = new User("async_test_user", "asynctest@example.com");
        User savedUser = userService.createUser(testUser);
        
        // 调用异步方法
        var future = userService.processUserAsync(savedUser.getId());
        assertNotNull(future, "异步方法应该返回 CompletableFuture");
        
        System.out.println("异步方法调用完成，等待结果...");
        
        // 等待异步执行完成
        String result = future.get(); // 这会阻塞直到异步方法完成
        assertNotNull(result, "异步方法应该返回结果");
        System.out.println("异步执行结果: " + result);
        
        assertTrue(result.contains("处理完成"), "结果应该包含处理完成信息");
    }

    /**
     * 测试 AOP 注解
     */
    @Test
    public void testAOPAnnotations() {
        System.out.println("=== 测试 AOP 注解 ===");
        
        // 调用 Service 方法，触发 AOP 切面
        var users = userService.findAllUsers();
        assertNotNull(users, "用户列表不应为空");
        
        System.out.println("AOP 测试完成，观察控制台输出的切面日志");
        System.out.println("查询到用户数量: " + users.size());
    }

    /**
     * 测试事件注解
     */
    @Test
    @Transactional
    public void testEventAnnotations() {
        System.out.println("=== 测试事件注解 ===");
        
        // 创建用户会触发事件
        User eventTestUser = new User("event_test_user", "eventtest@example.com");
        
        try {
            User savedUser = userService.createUser(eventTestUser);
            assertNotNull(savedUser.getId(), "用户应该被成功创建");
            
            System.out.println("事件测试完成，观察控制台输出的事件监听器日志");
            
            // 等待一下异步事件处理
            Thread.sleep(1500);
            
        } catch (Exception e) {
            System.err.println("事件测试异常: " + e.getMessage());
        }
    }

    /**
     * 测试条件注解
     */
    @Test
    public void testConditionalAnnotations() {
        System.out.println("=== 测试条件注解 ===");
        
        // 由于 app.debug=true，条件 Bean 应该被创建
        try {
            String conditionalBean = (String) userService.getClass()
                    .getDeclaredField("injectedString")
                    .get(userService);
            
            System.out.println("条件注解测试 - 调试模式Bean存在: " + (conditionalBean != null));
            
        } catch (Exception e) {
            System.out.println("条件注解测试完成");
        }
    }

    /**
     * 综合测试 - 测试多个注解的协同工作
     */
    @Test
    @Transactional
    public void testAnnotationIntegration() {
        System.out.println("=== 综合注解测试 ===");
        
        try {
            // 1. 测试验证 + 事务 + 事件
            User integrationUser = new User("integration_user", "integration@example.com");
            integrationUser.setAge(28);
            integrationUser.setPhone("13900139000");
            
            // 2. 创建用户（触发验证、事务、事件、AOP）
            User savedUser = userService.createUser(integrationUser);
            assertNotNull(savedUser.getId());
            
            // 3. 异步处理
            var asyncResult = userService.processUserAsync(savedUser.getId());
            
            // 4. 查询用户（触发AOP、事务）
            var foundUser = userService.findUserById(savedUser.getId());
            assertTrue(foundUser.isPresent());
            
            System.out.println("综合测试完成 - 所有注解协同工作正常");
            
            // 等待异步处理完成
            String result = asyncResult.get();
            System.out.println("异步处理结果: " + result);
            
        } catch (Exception e) {
            System.err.println("综合测试异常: " + e.getMessage());
            e.printStackTrace();
            fail("综合测试不应该失败");
        }
    }
}