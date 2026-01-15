package com.debug.demo;

import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.PostConstruct;
import javax.sql.DataSource;

/**
 * Spring Boot 条件注解演示Demo
 * 
 * 演示Spring Boot如何扩展和优化@Conditional注解：
 * 1. @ConditionalOnProperty - 基于配置属性
 * 2. @ConditionalOnClass - 基于类路径
 * 3. @ConditionalOnBean/@ConditionalOnMissingBean - 基于Bean存在性
 * 4. @ConditionalOnProfile - 基于环境Profile
 * 5. 自定义组合条件注解
 */
@Configuration
@EnableConfigurationProperties({
    SpringBootConditionalDemo.FeatureProperties.class,
    SpringBootConditionalDemo.DatabaseProperties.class
})
public class SpringBootConditionalDemo {

    public static void main(String[] args) {
        System.out.println("=== Spring Boot 条件注解演示 ===\n");
        
        // 模拟Spring Boot应用的配置
        System.setProperty("features.cache.enabled", "true");
        System.setProperty("features.cache.type", "redis");
        System.setProperty("features.payment.enabled", "false");
        System.setProperty("features.analytics.enabled", "true");
        System.setProperty("database.type", "mysql");
        System.setProperty("spring.profiles.active", "dev");
        
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.register(SpringBootConditionalDemo.class);
        context.refresh();
        
        System.out.println("\n=== 条件注解生效情况 ===");
        checkBeanRegistration(context);
        
        context.close();
    }
    
    private static void checkBeanRegistration(AnnotationConfigApplicationContext context) {
        // 检查各种条件注解的生效情况
        checkBean(context, "cacheService", "缓存服务");
        checkBean(context, "redisCacheManager", "Redis缓存管理器");
        checkBean(context, "paymentService", "支付服务");
        checkBean(context, "analyticsService", "分析服务");
        checkBean(context, "devDataSource", "开发环境数据源");
        checkBean(context, "prodDataSource", "生产环境数据源");
        checkBean(context, "defaultUserService", "默认用户服务");
        checkBean(context, "customUserService", "自定义用户服务");
        checkBean(context, "mysqlSpecificService", "MySQL专用服务");
    }
    
    private static void checkBean(AnnotationConfigApplicationContext context, String beanName, String description) {
        try {
            Object bean = context.getBean(beanName);
            System.out.println("✅ " + description + " (" + beanName + ") 已注册");
        } catch (Exception e) {
            System.out.println("❌ " + description + " (" + beanName + ") 未注册");
        }
    }

    // ========================================
    // 配置属性类
    // ========================================

    @ConfigurationProperties(prefix = "features")
    public static class FeatureProperties {
        private Cache cache = new Cache();
        private Payment payment = new Payment();
        private Analytics analytics = new Analytics();
        
        // getters and setters
        public Cache getCache() { return cache; }
        public void setCache(Cache cache) { this.cache = cache; }
        public Payment getPayment() { return payment; }
        public void setPayment(Payment payment) { this.payment = payment; }
        public Analytics getAnalytics() { return analytics; }
        public void setAnalytics(Analytics analytics) { this.analytics = analytics; }
        
        public static class Cache {
            private boolean enabled;
            private String type;
            
            public boolean isEnabled() { return enabled; }
            public void setEnabled(boolean enabled) { this.enabled = enabled; }
            public String getType() { return type; }
            public void setType(String type) { this.type = type; }
        }
        
        public static class Payment {
            private boolean enabled;
            
            public boolean isEnabled() { return enabled; }
            public void setEnabled(boolean enabled) { this.enabled = enabled; }
        }
        
        public static class Analytics {
            private boolean enabled;
            
            public boolean isEnabled() { return enabled; }
            public void setEnabled(boolean enabled) { this.enabled = enabled; }
        }
    }

    @ConfigurationProperties(prefix = "database")
    public static class DatabaseProperties {
        private String type;
        
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
    }

    // ========================================
    // @ConditionalOnProperty 演示
    // ========================================

    /**
     * 基于单个属性的条件注册
     */
    @Component("cacheService")
    @ConditionalOnProperty(name = "features.cache.enabled", havingValue = "true")
    public static class CacheService {
        
        @PostConstruct
        public void init() {
            System.out.println("🔥 CacheService 初始化 - 缓存功能已启用");
        }
        
        public void cache(String key, Object value) {
            System.out.println("缓存数据: " + key + " = " + value);
        }
    }

    /**
     * 基于多个属性的条件注册
     */
    @Component("redisCacheManager")
    @ConditionalOnProperty(
        prefix = "features.cache",
        name = {"enabled", "type"},
        havingValue = "true"  // 只检查enabled=true，type存在即可
    )
    @ConditionalOnProperty(name = "features.cache.type", havingValue = "redis")
    public static class RedisCacheManager {
        
        @PostConstruct
        public void init() {
            System.out.println("🔥 RedisCacheManager 初始化 - Redis缓存管理器");
        }
    }

    /**
     * 条件不满足的Bean - 不会被注册
     */
    @Component("paymentService")
    @ConditionalOnProperty(name = "features.payment.enabled", havingValue = "true")
    public static class PaymentService {
        
        @PostConstruct
        public void init() {
            System.out.println("💰 PaymentService 初始化 - 这个不应该出现！");
        }
    }

    /**
     * matchIfMissing = true 的演示
     */
    @Component("analyticsService")
    @ConditionalOnProperty(
        name = "features.analytics.enabled", 
        havingValue = "true",
        matchIfMissing = true  // 🔥 属性不存在时也匹配
    )
    public static class AnalyticsService {
        
        @PostConstruct
        public void init() {
            System.out.println("📊 AnalyticsService 初始化 - 分析服务");
        }
    }

    // ========================================
    // @ConditionalOnProfile 演示
    // ========================================

    /**
     * 开发环境专用配置
     */
    @Component("devDataSource")
    @ConditionalOnProfile("dev")  // 🔥 只在dev profile下生效
    public static class DevDataSource {
        
        @PostConstruct
        public void init() {
            System.out.println("🛠️  DevDataSource 初始化 - 开发环境内存数据库");
        }
        
        public String getUrl() {
            return "jdbc:h2:mem:devdb";
        }
    }

    /**
     * 生产环境专用配置
     */
    @Component("prodDataSource")
    @ConditionalOnProfile({"prod", "staging"})  // 🔥 生产或预发环境
    public static class ProdDataSource {
        
        @PostConstruct
        public void init() {
            System.out.println("🏭 ProdDataSource 初始化 - 生产环境数据库");
        }
        
        public String getUrl() {
            return "jdbc:mysql://prod-server:3306/appdb";
        }
    }

    // ========================================
    // @ConditionalOnBean/@ConditionalOnMissingBean 演示
    // ========================================

    /**
     * 用户自定义服务（优先级高）
     */
    @Component("customUserService")
    @ConditionalOnProperty(name = "user.service.custom", havingValue = "true", matchIfMissing = false)
    public static class CustomUserService implements UserService {
        
        @PostConstruct
        public void init() {
            System.out.println("👤 CustomUserService 初始化 - 自定义用户服务");
        }
        
        @Override
        public String getServiceType() {
            return "Custom";
        }
    }

    /**
     * 默认用户服务（只在没有自定义服务时生效）
     */
    @Component("defaultUserService")
    @ConditionalOnMissingBean(UserService.class)  // 🔥 没有UserService实现时才注册
    public static class DefaultUserService implements UserService {
        
        @PostConstruct
        public void init() {
            System.out.println("👤 DefaultUserService 初始化 - 默认用户服务");
        }
        
        @Override
        public String getServiceType() {
            return "Default";
        }
    }

    public interface UserService {
        String getServiceType();
    }

    // ========================================
    // @ConditionalOnClass 演示（模拟）
    // ========================================

    /**
     * 基于类存在的条件注册
     * 注意：这里模拟MySQL驱动存在的情况
     */
    @Component("mysqlSpecificService")
    @Conditional(MySQLDriverExistsCondition.class)  // 🔥 自定义条件：MySQL驱动存在
    @ConditionalOnProperty(name = "database.type", havingValue = "mysql")
    public static class MySQLSpecificService {
        
        @PostConstruct
        public void init() {
            System.out.println("🗄️  MySQLSpecificService 初始化 - MySQL专用服务");
        }
    }

    /**
     * 自定义条件：检查MySQL驱动是否存在
     */
    public static class MySQLDriverExistsCondition implements Condition {
        
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            try {
                // 尝试加载MySQL驱动类
                context.getClassLoader().loadClass("com.mysql.cj.jdbc.Driver");
                System.out.println("🔍 条件评估 - MySQL驱动存在: true");
                return true;
            } catch (ClassNotFoundException e) {
                System.out.println("🔍 条件评估 - MySQL驱动存在: false");
                return false;
            }
        }
    }

    // ========================================
    // 自定义组合条件注解
    // ========================================

    /**
     * 自定义组合条件注解
     */
    @Target({ ElementType.TYPE, ElementType.METHOD })
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @ConditionalOnProperty(name = "features.cache.enabled", havingValue = "true")
    @ConditionalOnProperty(name = "features.cache.type", havingValue = "redis")
    @Conditional(RedisAvailableCondition.class)
    public @interface ConditionalOnRedisCache {
    }

    /**
     * Redis可用性条件
     */
    public static class RedisAvailableCondition implements Condition {
        
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            // 模拟检查Redis是否可用
            boolean available = true;  // 假设Redis可用
            System.out.println("🔍 条件评估 - Redis可用性: " + available);
            return available;
        }
    }

    /**
     * 使用组合条件注解
     */
    @Component("redisSpecificService")
    @ConditionalOnRedisCache  // 🔥 使用自定义组合条件
    public static class RedisSpecificService {
        
        @PostConstruct
        public void init() {
            System.out.println("🔴 RedisSpecificService 初始化 - Redis专用服务");
        }
    }

    // ========================================
    // 复杂条件演示
    // ========================================

    /**
     * 复杂的自定义条件
     */
    public static class ComplexBusinessCondition implements Condition {
        
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            Environment env = context.getEnvironment();
            
            // 复杂的业务逻辑判断
            boolean cacheEnabled = "true".equals(env.getProperty("features.cache.enabled"));
            boolean analyticsEnabled = "true".equals(env.getProperty("features.analytics.enabled"));
            boolean isDev = "dev".equals(env.getProperty("spring.profiles.active"));
            
            boolean result = cacheEnabled && analyticsEnabled && isDev;
            
            System.out.println("🔍 复杂条件评估:");
            System.out.println("  - 缓存启用: " + cacheEnabled);
            System.out.println("  - 分析启用: " + analyticsEnabled);
            System.out.println("  - 开发环境: " + isDev);
            System.out.println("  - 最终结果: " + result);
            
            return result;
        }
    }

    @Component("complexBusinessService")
    @Conditional(ComplexBusinessCondition.class)
    public static class ComplexBusinessService {
        
        @PostConstruct
        public void init() {
            System.out.println("🏢 ComplexBusinessService 初始化 - 复杂业务服务");
        }
    }
}