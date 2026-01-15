package demo;

import org.springframework.context.annotation.*;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * Spring条件评估机制演示
 * 
 * 这个Demo展示了ConditionEvaluator中智能阶段选择的工作原理
 */
public class ConditionEvaluatorDemo {

    public static void main(String[] args) {
        System.out.println("=== Spring条件评估机制演示 ===\n");
        
        // 创建应用上下文
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        
        // 设置系统属性来控制条件
        System.setProperty("feature.database.enabled", "true");
        System.setProperty("feature.cache.enabled", "false");
        System.setProperty("feature.analytics.enabled", "true");
        
        // 注册配置类
        context.register(
            DatabaseConfig.class,      // 配置类级别条件
            ServiceConfig.class,       // Bean级别条件
            ConditionalConfig.class    // 混合条件
        );
        
        // 刷新容器，触发条件评估
        context.refresh();
        
        // 检查Bean注册情况
        System.out.println("=== Bean注册结果 ===");
        System.out.println("DatabaseConfig相关Bean:");
        checkBean(context, "dataSource");
        checkBean(context, "jdbcTemplate");
        checkBean(context, "transactionManager");
        
        System.out.println("\nServiceConfig相关Bean:");
        checkBean(context, "cacheService");
        checkBean(context, "analyticsService");
        checkBean(context, "coreService");
        
        System.out.println("\nConditionalConfig相关Bean:");
        checkBean(context, "conditionalBean");
        
        context.close();
    }
    
    private static void checkBean(AnnotationConfigApplicationContext context, String beanName) {
        try {
            Object bean = context.getBean(beanName);
            System.out.println("✅ " + beanName + ": " + bean.getClass().getSimpleName());
        } catch (Exception e) {
            System.out.println("❌ " + beanName + ": 未注册 (条件不满足)");
        }
    }
}

/**
 * 场景1：配置类级别的条件控制
 * 
 * 这个配置类展示了PARSE_CONFIGURATION阶段的条件评估
 * 如果条件不满足，整个配置类都不会被解析
 */
@Configuration
@ConditionalOnProperty(name = "feature.database.enabled", havingValue = "true")
class DatabaseConfig {
    
    @Bean
    public DataSource dataSource() {
        System.out.println("🔧 创建DataSource Bean");
        return new DataSource();
    }
    
    @Bean
    public JdbcTemplate jdbcTemplate() {
        System.out.println("🔧 创建JdbcTemplate Bean");
        return new JdbcTemplate();
    }
    
    @Bean
    public TransactionManager transactionManager() {
        System.out.println("🔧 创建TransactionManager Bean");
        return new TransactionManager();
    }
    
    // 模拟类
    static class DataSource {}
    static class JdbcTemplate {}
    static class TransactionManager {}
}

/**
 * 场景2：Bean级别的精确控制
 * 
 * 这个配置类展示了REGISTER_BEAN阶段的条件评估
 * 每个Bean独立评估条件
 */
@Configuration
class ServiceConfig {
    
    @Bean
    @ConditionalOnProperty(name = "feature.cache.enabled", havingValue = "true")
    public CacheService cacheService() {
        System.out.println("🔧 创建CacheService Bean");
        return new CacheService();
    }
    
    @Bean
    @ConditionalOnProperty(name = "feature.analytics.enabled", havingValue = "true")
    public AnalyticsService analyticsService() {
        System.out.println("🔧 创建AnalyticsService Bean");
        return new AnalyticsService();
    }
    
    @Bean  // 无条件注册
    public CoreService coreService() {
        System.out.println("🔧 创建CoreService Bean (无条件)");
        return new CoreService();
    }
    
    // 模拟类
    static class CacheService {}
    static class AnalyticsService {}
    static class CoreService {}
}

/**
 * 场景3：自定义条件实现
 * 
 * 展示ConfigurationCondition的使用
 */
@Configuration
class ConditionalConfig {
    
    @Bean
    @Conditional(CustomCondition.class)
    public ConditionalBean conditionalBean() {
        System.out.println("🔧 创建ConditionalBean Bean");
        return new ConditionalBean();
    }
    
    static class ConditionalBean {}
}

/**
 * 自定义条件实现
 * 
 * 展示如何实现ConfigurationCondition接口
 */
class CustomCondition implements ConfigurationCondition {
    
    @Override
    public ConfigurationPhase getConfigurationPhase() {
        // 指定在Bean注册阶段评估
        return ConfigurationPhase.REGISTER_BEAN;
    }
    
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        // 自定义条件逻辑：检查是否启用了分析功能
        String analyticsEnabled = context.getEnvironment().getProperty("feature.analytics.enabled");
        boolean result = "true".equals(analyticsEnabled);
        
        System.out.println("🔍 CustomCondition评估: feature.analytics.enabled=" + 
                          analyticsEnabled + " → " + (result ? "满足" : "不满足"));
        
        return result;
    }
}

/**
 * 条件评估流程模拟
 * 
 * 这个类模拟了ConditionEvaluator.shouldSkip()的核心逻辑
 */
class ConditionEvaluatorSimulator {
    
    /**
     * 模拟智能阶段选择逻辑
     */
    public static void simulatePhaseSelection() {
        System.out.println("\n=== 条件评估阶段选择模拟 ===");
        
        // 模拟不同类型的元数据
        System.out.println("1. 配置类 (@Configuration + @Bean方法):");
        System.out.println("   → isConfigurationCandidate() = true");
        System.out.println("   → 选择阶段: PARSE_CONFIGURATION");
        System.out.println("   → 影响范围: 整个配置类");
        
        System.out.println("\n2. 普通组件类 (@Component):");
        System.out.println("   → isConfigurationCandidate() = true");
        System.out.println("   → 选择阶段: PARSE_CONFIGURATION");
        System.out.println("   → 影响范围: 整个组件类");
        
        System.out.println("\n3. 普通Bean方法 (@Bean):");
        System.out.println("   → isConfigurationCandidate() = false");
        System.out.println("   → 选择阶段: REGISTER_BEAN");
        System.out.println("   → 影响范围: 单个Bean");
        
        System.out.println("\n=== 核心代码逻辑 ===");
        System.out.println("if (phase == null) {");
        System.out.println("    if (metadata instanceof AnnotationMetadata &&");
        System.out.println("            ConfigurationClassUtils.isConfigurationCandidate((AnnotationMetadata) metadata)) {");
        System.out.println("        return shouldSkip(metadata, ConfigurationPhase.PARSE_CONFIGURATION);");
        System.out.println("    }");
        System.out.println("    return shouldSkip(metadata, ConfigurationPhase.REGISTER_BEAN);");
        System.out.println("}");
    }
}