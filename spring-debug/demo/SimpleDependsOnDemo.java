package com.debug.demo;

import org.springframework.context.annotation.*;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

/**
 * 简化版 @DependsOn 和 @Conditional 演示Demo
 * 
 * 演示核心概念：
 * 1. @DependsOn：控制Bean的初始化顺序
 * 2. @Conditional：条件化Bean注册
 */
@Configuration
@ComponentScan(basePackages = "com.debug.demo")
public class SimpleDependsOnDemo {

    public static void main(String[] args) {
        System.out.println("=== @DependsOn 和 @Conditional 注解演示 ===\n");
        
        // 设置系统属性来控制条件注解
        System.setProperty("feature.database.enabled", "true");
        System.setProperty("feature.cache.enabled", "false");
        System.setProperty("app.environment", "development");
        
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.register(SimpleDependsOnDemo.class);
        context.refresh();
        
        System.out.println("\n=== 容器初始化完成，查看Bean注册情况 ===");
        
        // 查看哪些Bean被注册了
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
     */
    @Component("configurationManager")
    public static class ConfigurationManager {
        
        @PostConstruct
        public void init() {
            System.out.println("🔧 [1] ConfigurationManager 初始化 - 加载系统配置");
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        @PreDestroy
        public void destroy() {
            System.out.println("🔧 ConfigurationManager 销毁");
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
    @Conditional(DatabaseEnabledCondition.class)  // 🔥 条件：只有启用数据库功能才注册
    public static class DatabaseService {
        
        @Autowired
        private ConfigurationManager configManager;
        
        @PostConstruct
        public void init() {
            System.out.println("🗄️  [2] DatabaseService 初始化 - 连接数据库");
            String dbUrl = configManager.getConfig("database.url");
            System.out.println("    数据库URL: " + dbUrl);
            
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        @PreDestroy
        public void destroy() {
            System.out.println("🗄️  DatabaseService 销毁");
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
    @Conditional(CacheEnabledCondition.class)  // 🔥 条件：缓存功能未启用，不会注册
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
     */
    @Component("applicationService")
    @DependsOn({"configurationManager", "databaseService"})  // 🔥 依赖多个Bean
    public static class ApplicationService {
        
        @Autowired
        private ConfigurationManager configManager;
        
        @Autowired
        private DatabaseService databaseService;
        
        // 缓存服务是可选的
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
    }

    /**
     * 开发环境专用服务
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

    // ========================================
    // 自定义条件实现
    // ========================================

    /**
     * 数据库启用条件
     */
    public static class DatabaseEnabledCondition implements Condition {
        
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            Environment env = context.getEnvironment();
            String enabled = env.getProperty("feature.database.enabled");
            
            boolean result = "true".equals(enabled);
            System.out.println("🔍 条件评估 - DatabaseEnabledCondition: " + 
                             "feature.database.enabled=" + enabled + ", 结果=" + result);
            
            return result;
        }
    }

    /**
     * 缓存启用条件
     */
    public static class CacheEnabledCondition implements Condition {
        
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            Environment env = context.getEnvironment();
            String enabled = env.getProperty("feature.cache.enabled");
            
            boolean result = "true".equals(enabled);
            System.out.println("🔍 条件评估 - CacheEnabledCondition: " + 
                             "feature.cache.enabled=" + enabled + ", 结果=" + result);
            
            return result;
        }
    }

    /**
     * 开发环境条件
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
}