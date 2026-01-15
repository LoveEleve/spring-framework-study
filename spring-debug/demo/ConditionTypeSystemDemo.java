package org.springframework.debug.demo;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.ConfigurationCondition;
import org.springframework.context.annotation.ConfigurationPhase;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.util.Arrays;
import java.util.List;

/**
 * Spring条件类型体系演示
 * 
 * 详细展示Condition和ConfigurationCondition的区别，
 * 以及Spring如何通过instanceof进行类型检查
 */
public class ConditionTypeSystemDemo {

    public static void main(String[] args) {
        System.out.println("🎯 Spring条件类型体系演示");
        System.out.println("=" .repeat(50));
        
        // 演示不同类型的条件
        demonstrateConditionTypes();
        
        // 演示类型检查逻辑
        demonstrateTypeChecking();
        
        // 演示实际应用场景
        demonstrateRealWorldScenarios();
    }

    /**
     * 演示不同类型的条件
     */
    private static void demonstrateConditionTypes() {
        System.out.println("\n📋 1. 条件类型展示");
        System.out.println("-".repeat(30));
        
        // 创建不同类型的条件实例
        List<Condition> conditions = Arrays.asList(
            new SimpleCondition(),           // 普通Condition
            new DatabaseCondition(),         // ConfigurationCondition (PARSE_CONFIGURATION)
            new CacheCondition(),            // ConfigurationCondition (REGISTER_BEAN)
            new ProfileCondition(),          // 普通Condition
            new FeatureCondition()           // 普通Condition
        );
        
        System.out.println("📦 创建的条件实例：");
        for (int i = 0; i < conditions.size(); i++) {
            Condition condition = conditions.get(i);
            String type = condition instanceof ConfigurationCondition ? 
                "ConfigurationCondition" : "Condition";
            System.out.println("   " + (i + 1) + ". " + condition.getClass().getSimpleName() + 
                             " → " + type);
        }
    }

    /**
     * 演示Spring内部的类型检查逻辑
     */
    private static void demonstrateTypeChecking() {
        System.out.println("\n🔍 2. 类型检查逻辑演示");
        System.out.println("-".repeat(30));
        
        List<Condition> conditions = Arrays.asList(
            new SimpleCondition(),
            new DatabaseCondition(),
            new CacheCondition()
        );
        
        // 模拟当前评估阶段
        ConfigurationPhase currentPhase = ConfigurationPhase.PARSE_CONFIGURATION;
        System.out.println("📍 当前评估阶段：" + currentPhase);
        System.out.println();
        
        // 🔥 模拟Spring内部的类型检查逻辑
        for (int i = 0; i < conditions.size(); i++) {
            Condition condition = conditions.get(i);
            
            System.out.println("🔄 第" + (i + 1) + "个条件：" + condition.getClass().getSimpleName());
            
            // 🔥 关键代码：类型检查
            ConfigurationPhase requiredPhase = null;
            System.out.println("   📝 ConfigurationPhase requiredPhase = null;");
            
            System.out.println("   📝 if (condition instanceof ConfigurationCondition) {");
            if (condition instanceof ConfigurationCondition) {
                System.out.println("      → ✅ 是ConfigurationCondition类型");
                System.out.println("   📝 requiredPhase = ((ConfigurationCondition) condition).getConfigurationPhase();");
                
                requiredPhase = ((ConfigurationCondition) condition).getConfigurationPhase();
                System.out.println("      → requiredPhase = " + requiredPhase);
            } else {
                System.out.println("      → ❌ 不是ConfigurationCondition类型");
                System.out.println("      → requiredPhase 保持为 null");
            }
            System.out.println("   📝 }");
            
            // 阶段匹配检查
            boolean phaseMatches = (requiredPhase == null || requiredPhase == currentPhase);
            System.out.println("   🎯 阶段匹配检查：(requiredPhase == null || requiredPhase == phase)");
            System.out.println("      → " + (requiredPhase == null) + " || " + (requiredPhase == currentPhase) + 
                             " = " + phaseMatches);
            
            if (phaseMatches) {
                System.out.println("      → ✅ 阶段匹配，可以评估条件");
            } else {
                System.out.println("      → ❌ 阶段不匹配，跳过此条件");
            }
            
            System.out.println();
        }
    }

    /**
     * 演示实际应用场景
     */
    private static void demonstrateRealWorldScenarios() {
        System.out.println("\n💡 3. 实际应用场景");
        System.out.println("-".repeat(30));
        
        System.out.println("📊 条件类型使用统计：");
        System.out.println("   🔥 Condition (85%)：简单条件，任意阶段评估");
        System.out.println("      - 功能开关条件");
        System.out.println("      - 环境检查条件");
        System.out.println("      - 属性存在条件");
        
        System.out.println("   📊 ConfigurationCondition (15%)：复杂条件，指定阶段评估");
        System.out.println("      - 自动配置条件");
        System.out.println("      - 框架级条件");
        System.out.println("      - 依赖检查条件");
        
        System.out.println("\n🎯 选择指南：");
        System.out.println("   ✅ 大多数情况：使用 Condition");
        System.out.println("   🚀 复杂场景：使用 ConfigurationCondition");
        System.out.println("   🔧 需要阶段控制：使用 ConfigurationCondition");
        
        // 演示选择逻辑
        demonstrateConditionSelection();
    }

    /**
     * 演示条件选择逻辑
     */
    private static void demonstrateConditionSelection() {
        System.out.println("\n🔧 4. 条件选择决策树");
        System.out.println("-".repeat(30));
        
        String[] scenarios = {
            "简单的功能开关",
            "环境变量检查", 
            "复杂的自动配置",
            "框架级依赖检查",
            "Bean存在性检查"
        };
        
        String[] recommendations = {
            "Condition - 简单直接",
            "Condition - 无需阶段控制",
            "ConfigurationCondition - 需要早期评估",
            "ConfigurationCondition - 精确阶段控制",
            "ConfigurationCondition - Bean注册阶段评估"
        };
        
        for (int i = 0; i < scenarios.length; i++) {
            System.out.println("   场景" + (i + 1) + "：" + scenarios[i]);
            System.out.println("   推荐：" + recommendations[i]);
            System.out.println();
        }
    }

    // ========== 条件实现示例 ==========

    /**
     * 简单条件：实现Condition接口
     */
    static class SimpleCondition implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            // 模拟简单的条件判断
            return true;
        }
    }

    /**
     * 数据库条件：实现ConfigurationCondition接口
     * 在配置解析阶段评估
     */
    static class DatabaseCondition implements ConfigurationCondition {
        @Override
        public ConfigurationPhase getConfigurationPhase() {
            return ConfigurationPhase.PARSE_CONFIGURATION;  // 🔥 配置解析阶段
        }

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            // 模拟数据库配置检查
            return true;
        }
    }

    /**
     * 缓存条件：实现ConfigurationCondition接口
     * 在Bean注册阶段评估
     */
    static class CacheCondition implements ConfigurationCondition {
        @Override
        public ConfigurationPhase getConfigurationPhase() {
            return ConfigurationPhase.REGISTER_BEAN;  // 🔥 Bean注册阶段
        }

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            // 模拟缓存配置检查
            return true;
        }
    }

    /**
     * 环境条件：实现Condition接口
     */
    static class ProfileCondition implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            // 模拟环境检查
            return true;
        }
    }

    /**
     * 功能条件：实现Condition接口
     */
    static class FeatureCondition implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            // 模拟功能开关检查
            return true;
        }
    }

    // ========== 辅助方法 ==========

    /**
     * 模拟Spring内部的条件处理逻辑
     */
    private static void processCondition(Condition condition, ConfigurationPhase currentPhase) {
        System.out.println("🔄 处理条件：" + condition.getClass().getSimpleName());
        
        // 类型检查
        if (condition instanceof ConfigurationCondition) {
            ConfigurationCondition configCondition = (ConfigurationCondition) condition;
            ConfigurationPhase requiredPhase = configCondition.getConfigurationPhase();
            
            System.out.println("   类型：ConfigurationCondition");
            System.out.println("   要求阶段：" + requiredPhase);
            System.out.println("   当前阶段：" + currentPhase);
            
            if (requiredPhase == currentPhase) {
                System.out.println("   结果：✅ 阶段匹配，执行条件评估");
            } else {
                System.out.println("   结果：❌ 阶段不匹配，跳过条件评估");
            }
        } else {
            System.out.println("   类型：Condition");
            System.out.println("   阶段要求：无限制");
            System.out.println("   结果：✅ 执行条件评估");
        }
        
        System.out.println();
    }

    /**
     * 打印类型体系总结
     */
    private static void printTypeSystemSummary() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("📊 Spring条件类型体系总结");
        System.out.println("=".repeat(60));
        
        System.out.println("🔍 基础接口：Condition");
        System.out.println("   特点：函数式接口，简单易用");
        System.out.println("   用途：大多数条件判断场景");
        System.out.println("   阶段：任意阶段都可以评估");
        
        System.out.println("\n🔍 扩展接口：ConfigurationCondition");
        System.out.println("   特点：继承Condition，增加阶段控制");
        System.out.println("   用途：复杂配置场景，需要精确控制");
        System.out.println("   阶段：PARSE_CONFIGURATION 或 REGISTER_BEAN");
        
        System.out.println("\n🎯 核心设计思想：");
        System.out.println("   📈 渐进式设计：基础功能 → 高级功能");
        System.out.println("   🔄 向后兼容：新接口继承旧接口");
        System.out.println("   🚀 多态支持：统一处理，运行时区分");
        System.out.println("   ⚡ 性能优化：阶段控制避免不必要评估");
    }
}