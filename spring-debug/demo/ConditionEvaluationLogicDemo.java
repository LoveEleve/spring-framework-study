package demo;

import org.springframework.context.annotation.*;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Spring条件评估核心执行逻辑演示
 * 
 * 展示条件评估的完整流程和各种场景
 */
public class ConditionEvaluationLogicDemo {

    public static void main(String[] args) {
        System.out.println("=== Spring条件评估核心执行逻辑演示 ===\n");
        
        // 设置不同的系统属性来模拟不同场景
        testScenario1_AllConditionsMet();
        testScenario2_OneConditionFails();
        testScenario3_PhaseMatching();
        
        // 模拟核心逻辑
        simulateCoreLogic();
    }
    
    /**
     * 场景1：所有条件都满足
     */
    private static void testScenario1_AllConditionsMet() {
        System.out.println("=== 场景1：所有条件都满足 ===");
        
        // 设置属性使所有条件满足
        System.setProperty("database.enabled", "true");
        System.setProperty("cache.enabled", "true");
        System.setProperty("spring.profiles.active", "production");
        
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.register(AllConditionsConfig.class);
        context.refresh();
        
        // 检查结果
        try {
            Object bean = context.getBean("allConditionsBean");
            System.out.println("✅ 结果：Bean注册成功 - " + bean.getClass().getSimpleName());
        } catch (Exception e) {
            System.out.println("❌ 结果：Bean未注册");
        }
        
        context.close();
        System.out.println();
    }
    
    /**
     * 场景2：其中一个条件不满足
     */
    private static void testScenario2_OneConditionFails() {
        System.out.println("=== 场景2：缓存条件不满足 ===");
        
        // 设置缓存条件不满足
        System.setProperty("database.enabled", "true");
        System.setProperty("cache.enabled", "false");  // 🔥 这个条件不满足
        System.setProperty("spring.profiles.active", "production");
        
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.register(AllConditionsConfig.class);
        context.refresh();
        
        // 检查结果
        try {
            Object bean = context.getBean("allConditionsBean");
            System.out.println("✅ 结果：Bean注册成功 - " + bean.getClass().getSimpleName());
        } catch (Exception e) {
            System.out.println("❌ 结果：Bean未注册（缓存条件不满足，短路求值生效）");
        }
        
        context.close();
        System.out.println();
    }
    
    /**
     * 场景3：阶段匹配测试
     */
    private static void testScenario3_PhaseMatching() {
        System.out.println("=== 场景3：阶段匹配测试 ===");
        
        System.setProperty("parse.phase.enabled", "true");
        System.setProperty("register.phase.enabled", "true");
        
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.register(PhaseMatchingConfig.class);
        context.refresh();
        
        try {
            Object bean = context.getBean("phaseMatchingBean");
            System.out.println("✅ 结果：Bean注册成功 - " + bean.getClass().getSimpleName());
        } catch (Exception e) {
            System.out.println("❌ 结果：Bean未注册");
        }
        
        context.close();
        System.out.println();
    }
    
    /**
     * 模拟核心逻辑执行
     */
    private static void simulateCoreLogic() {
        System.out.println("=== 核心逻辑模拟 ===");
        System.out.println("for (Condition condition : conditions) {");
        System.out.println("    ConfigurationPhase requiredPhase = null;");
        System.out.println("    if (condition instanceof ConfigurationCondition) {");
        System.out.println("        requiredPhase = ((ConfigurationCondition) condition).getConfigurationPhase();");
        System.out.println("    }");
        System.out.println("    ");
        System.out.println("    // 🎯 核心判断逻辑");
        System.out.println("    if ((requiredPhase == null || requiredPhase == phase) && ");
        System.out.println("        !condition.matches(this.context, metadata)) {");
        System.out.println("        return true;  // 🔥 短路求值：任一条件不满足立即跳过");
        System.out.println("    }");
        System.out.println("}");
        System.out.println("return false;  // ✅ 所有条件都满足，继续注册");
        
        System.out.println("\n=== 关键理解 ===");
        System.out.println("1. 短路求值：任一条件失败立即返回true（跳过）");
        System.out.println("2. 阶段匹配：只在正确的阶段评估条件");
        System.out.println("3. 类型多态：支持Condition和ConfigurationCondition");
        System.out.println("4. 默认通过：所有条件都满足时返回false（不跳过）");
    }
}

/**
 * 多条件配置类
 */
@Configuration
@Conditional({DatabaseCondition.class, CacheCondition.class, ProfileCondition.class})
class AllConditionsConfig {
    
    @Bean
    public AllConditionsBean allConditionsBean() {
        System.out.println("🔧 创建AllConditionsBean - 所有条件都满足");
        return new AllConditionsBean();
    }
    
    static class AllConditionsBean {}
}

/**
 * 阶段匹配配置类
 */
@Configuration
@Conditional({ParsePhaseCondition.class, RegisterPhaseCondition.class})
class PhaseMatchingConfig {
    
    @Bean
    public PhaseMatchingBean phaseMatchingBean() {
        System.out.println("🔧 创建PhaseMatchingBean - 阶段匹配成功");
        return new PhaseMatchingBean();
    }
    
    static class PhaseMatchingBean {}
}

/**
 * 数据库条件 - ConfigurationCondition实现
 */
class DatabaseCondition implements ConfigurationCondition {
    
    @Override
    public ConfigurationPhase getConfigurationPhase() {
        return ConfigurationPhase.PARSE_CONFIGURATION;  // 🔥 配置解析阶段评估
    }
    
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String enabled = context.getEnvironment().getProperty("database.enabled");
        boolean result = "true".equals(enabled);
        
        System.out.println("🔍 DatabaseCondition评估: database.enabled=" + enabled + 
                          " → " + (result ? "满足" : "不满足") + 
                          " (阶段: PARSE_CONFIGURATION)");
        
        return result;
    }
}

/**
 * 缓存条件 - ConfigurationCondition实现
 */
class CacheCondition implements ConfigurationCondition {
    
    @Override
    public ConfigurationPhase getConfigurationPhase() {
        return ConfigurationPhase.REGISTER_BEAN;  // 🔥 Bean注册阶段评估
    }
    
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String enabled = context.getEnvironment().getProperty("cache.enabled");
        boolean result = "true".equals(enabled);
        
        System.out.println("🔍 CacheCondition评估: cache.enabled=" + enabled + 
                          " → " + (result ? "满足" : "不满足") + 
                          " (阶段: REGISTER_BEAN)");
        
        return result;
    }
}

/**
 * 环境条件 - 普通Condition实现
 */
class ProfileCondition implements Condition {
    
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String[] profiles = context.getEnvironment().getActiveProfiles();
        boolean result = profiles.length > 0 && "production".equals(profiles[0]);
        
        System.out.println("🔍 ProfileCondition评估: active.profiles=" + 
                          (profiles.length > 0 ? profiles[0] : "none") + 
                          " → " + (result ? "满足" : "不满足") + 
                          " (阶段: 任意)");
        
        return result;
    }
}

/**
 * 解析阶段条件
 */
class ParsePhaseCondition implements ConfigurationCondition {
    
    @Override
    public ConfigurationPhase getConfigurationPhase() {
        return ConfigurationPhase.PARSE_CONFIGURATION;
    }
    
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String enabled = context.getEnvironment().getProperty("parse.phase.enabled");
        boolean result = "true".equals(enabled);
        
        System.out.println("🔍 ParsePhaseCondition评估: parse.phase.enabled=" + enabled + 
                          " → " + (result ? "满足" : "不满足"));
        
        return result;
    }
}

/**
 * 注册阶段条件
 */
class RegisterPhaseCondition implements ConfigurationCondition {
    
    @Override
    public ConfigurationPhase getConfigurationPhase() {
        return ConfigurationPhase.REGISTER_BEAN;
    }
    
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String enabled = context.getEnvironment().getProperty("register.phase.enabled");
        boolean result = "true".equals(enabled);
        
        System.out.println("🔍 RegisterPhaseCondition评估: register.phase.enabled=" + enabled + 
                          " → " + (result ? "满足" : "不满足"));
        
        return result;
    }
}

/**
 * 条件评估逻辑模拟器
 */
class ConditionEvaluationSimulator {
    
    /**
     * 模拟完整的条件评估流程
     */
    public static boolean simulateEvaluation(Condition[] conditions, ConfigurationPhase currentPhase) {
        System.out.println("\n=== 模拟条件评估流程 ===");
        System.out.println("当前阶段: " + currentPhase);
        System.out.println("条件数量: " + conditions.length);
        
        for (int i = 0; i < conditions.length; i++) {
            Condition condition = conditions[i];
            System.out.println("\n🔄 第" + (i + 1) + "轮：" + condition.getClass().getSimpleName());
            
            // 获取条件的阶段要求
            ConfigurationPhase requiredPhase = null;
            if (condition instanceof ConfigurationCondition) {
                requiredPhase = ((ConfigurationCondition) condition).getConfigurationPhase();
                System.out.println("   阶段要求: " + requiredPhase);
            } else {
                System.out.println("   阶段要求: null (普通条件)");
            }
            
            // 检查阶段匹配
            boolean phaseMatches = (requiredPhase == null || requiredPhase == currentPhase);
            System.out.println("   阶段匹配: " + phaseMatches);
            
            if (phaseMatches) {
                // 模拟条件评估（这里简化为随机结果）
                boolean conditionMatches = Math.random() > 0.3; // 70%概率满足
                System.out.println("   条件评估: " + (conditionMatches ? "满足" : "不满足"));
                
                // 核心判断逻辑
                if (!conditionMatches) {
                    System.out.println("   🚨 结果: 条件不满足，立即跳过注册（短路求值）");
                    return true; // 跳过注册
                } else {
                    System.out.println("   ✅ 结果: 条件满足，继续下一个条件");
                }
            } else {
                System.out.println("   ⏭️  结果: 阶段不匹配，跳过此条件");
            }
        }
        
        System.out.println("\n🎯 最终结果: 所有相关条件都满足，继续注册");
        return false; // 不跳过，继续注册
    }
}