package com.debug.demo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

/**
 * @DependsOn 和 @Conditional 注解演示Demo
 * 
 * 本Demo展示了两个重要注解的作用：
 * 1. @DependsOn：控制Bean的初始化顺序
 * 2. @Conditional：条件化Bean注册
 */
@Configuration
@ComponentScan(basePackages = "com.debug.demo")
public class DependsOnAndConditionalDemo {

    public static void main(String[] args) {
        System.out.println("=== @DependsOn 和 @Conditional 注解演示 ===\n");
        
        // 设置系统属性来控制条件注解
        System.setProperty("feature.database.enabled", "true");
        System.setProperty("feature.cache.enabled", "false");
        System.setProperty("app.environment", "development");
        
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.register(DependsOnAndConditionalDemo.class);
        context.refresh();
        
        System.out.println("\n=== 容器初始化完成，查看Bean注册情况 ===");
        
        // 查看哪些Bean被注册了
        String[] beanNames = context.getBeanNamesForType(Object.class);
        System.out.println("注册的Bean数量: " + beanNames.length);
        
        // 查看特定的Bean
        checkBeanExists(context, "configurationManager", "配置管理器");
        checkBeanExists(context, "databaseService", "数据库服务");
        checkBeanExists(context, "cacheService", "缓存服务");
        checkBeanExists(context, "applicationService", "应用服务");
        checkBeanExists(context, "developmentOnlyService", "开发环境专用服务");
        
        context.close();
    }
    
    private static void checkBeanExists(AnnotationConfigApplicationContext context, String beanName, String description) {
        try {
            Object bean = context.getBean(beanName);
            System.out.println("✅ " + description + " (" + beanName + ") 已注册: " + bean.getClass().getSimpleName());
        } catch (Exception e) {
            System.out.println("❌ " + description + " (" + beanName + ") 未注册");
        }
    }

    // ========================================
    // @DependsOn 演示：控制Bean初始化顺序
    // ========================================

    /**
     * 配置管理器 - 最先初始化
     * 这个Bean需要最先初始化，因为其他Bean都依赖它
     */
    @Component("configurationManager")
    public static class ConfigurationManager {
        
        @PostConstruct
        public void init() {
            System.out.println("🔧 [1] ConfigurationManager 初始化 - 加载系统配置");
            // 模拟配置加载
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        @PreDestroy
        public void destroy() {
            System.out.println("🔧 ConfigurationManager 销毁 - 保存配置");
        }
        
        public String getConfig(String key) {
            return System.getProperty(key, "default");
        }
    }

    /**
     * 数据库服务 - 依赖配置管理器
     * 使用 @DependsOn 确保 ConfigurationManager 先初始化
     */
    @Component("databaseService")
    @DependsOn("configurationManager")  // 🔥 关键：确保configurationManager先初始化
    @ConditionalOnProperty(name = "feature.database.enabled", havingValue = "true")  // 🔥 条件：只有启用数据库功能才注册
    public static class DatabaseService {
        
        @Autowired
        private ConfigurationManager configManager;
        
        @PostConstruct
        public void init() {
            System.out.println("🗄️  [2] DatabaseService 初始化 - 连接数据库");
            String dbUrl = configManager.getConfig("database.url");
            System.out.println("    数据库URL: " + dbUrl);
            
            // 模拟数据库连接
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        @PreDestroy
        public void destroy() {
            System.out.println("🗄️  DatabaseService 销毁 - 关闭数据库连接");
        }
        
        public void saveData(String data) {
            System.out.println("保存数据: " + data);
        }
    }

    /**
     * 缓存服务 - 也依赖配置管理器
     * 这个Bean由于条件不满足，不会被注册
     */
    @Component("cacheService")
    @DependsOn("configurationManager")
    @ConditionalOnProperty(name = "feature.cache.enabled", havingValue = "true")  // 🔥 条件：缓存功能未启用，不会注册
    public static class CacheService {
        
        @Autowired
        private ConfigurationManager configManager;
        
        @PostConstruct
        public void init() {
            System.out.println("💾 [X] CacheService 初始化 - 这个不应该出现！");
        }
        
        public void cacheData(String key, Object value) {
            System.out.println("缓存数据: " + key + " = " + value);
        }
    }

    /**
     * 应用服务 - 依赖多个其他服务
     * 使用 @DependsOn 确保所有依赖的服务都先初始化
     */
    @Component("applicationService")
    @DependsOn({"configurationManager", "databaseService"})  // 🔥 依赖多个Bean
    // 注意：不依赖cacheService，因为它可能不存在
    public static class ApplicationService {
        
        @Autowired
        private ConfigurationManager configManager;
        
        @Autowired
        private DatabaseService databaseService;
        
        // 缓存服务是可选的，使用required=false
        @Autowired(required = false)
        private CacheService cacheService;
        
        @PostConstruct
        public void init() {
            System.out.println("🚀 [3] ApplicationService 初始化 - 启动应用服务");
            System.out.println("    配置管理器: " + (configManager != null ? "已注入" : "未注入"));
            System.out.println("    数据库服务: " + (databaseService != null ? "已注入" : "未注入"));
            System.out.println("    缓存服务: " + (cacheService != null ? "已注入" : "未注入"));
        }
        
        @PreDestroy
        public void destroy() {
            System.out.println("🚀 ApplicationService 销毁");
        }
        
        public void processRequest(String request) {
            System.out.println("处理请求: " + request);
            databaseService.saveData(request);
            if (cacheService != null) {
                cacheService.cacheData("request", request);
            }
        }
    }

    // ========================================
    // @Conditional 高级演示：自定义条件
    // ========================================

    /**
     * 开发环境专用服务
     * 使用自定义条件注解
     */
    @Component("developmentOnlyService")
    @Conditional(DevelopmentEnvironmentCondition.class)  // 🔥 自定义条件
    public static class DevelopmentOnlyService {
        
        @PostConstruct
        public void init() {
            System.out.println("🛠️  [4] DevelopmentOnlyService 初始化 - 开发环境专用功能");
        }
        
        public void debugInfo() {
            System.out.println("这是开发环境专用的调试信息");
        }
    }

    /**
     * 自定义条件实现
     * 只有在开发环境下才会注册Bean
     */
    public static class DevelopmentEnvironmentCondition implements Condition {
        
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            Environment env = context.getEnvironment();
            String environment = env.getProperty("app.environment");
            
            boolean isDevelopment = "development".equals(environment);
            System.out.println("🔍 条件评估 - DevelopmentEnvironmentCondition: " + 
                             "app.environment=" + environment + ", 结果=" + isDevelopment);
            
            return isDevelopment;
        }
    }

    // ========================================
    // 更多 @Conditional 变体演示
    // ========================================

    /**
     * 基于类存在的条件Bean
     */
    @Bean
    @ConditionalOnClass(name = "com.mysql.cj.jdbc.Driver")  // MySQL驱动存在时才注册
    public String mysqlDataSource() {
        System.out.println("🔍 MySQL驱动存在，注册MySQL数据源");
        return "MySQL DataSource";
    }

    /**
     * 基于Bean缺失的条件Bean
     */
    @Bean
    @ConditionalOnMissingBean(name = "mysqlDataSource")  // MySQL数据源不存在时才注册
    public String defaultDataSource() {
        System.out.println("🔍 MySQL数据源不存在，注册默认数据源");
        return "Default H2 DataSource";
    }

    /**
     * 基于属性值的条件Bean
     */
    @Bean
    @ConditionalOnProperty(
        name = "logging.level", 
        havingValue = "debug", 
        matchIfMissing = false  // 属性不存在时不匹配
    )
    public String debugLogger() {
        System.out.println("🔍 启用调试日志");
        return "Debug Logger";
    }
}