package org.springframework.debug.demo;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.ConfigurationCondition;
import org.springframework.context.annotation.ConfigurationPhase;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.util.Arrays;
import java.util.List;

/**
 * Spring条件评估逐行代码分析演示
 * 
 * 模拟Spring条件评估的核心执行逻辑，逐行展示每个步骤的作用
 */
public class LineByLineAnalysisDemo {

    public static void main(String[] args) {
        System.out.println("🎯 Spring条件评估逐行代码分析演示");
        System.out.println("=" .repeat(50));
        
        // 模拟条件评估过程
        simulateConditionEvaluation();
    }

    /**
     * 模拟Spring条件评估的完整过程
     */
    private static void simulateConditionEvaluation() {
        // 模拟条件实例集合
        List<Condition> conditions = Arrays.asList(
            new DatabaseCondition(),      // 普通条件
            new CacheCondition(),         // 配置条件 - REGISTER_BEAN阶段
            new ProfileCondition()        // 普通条件
        );
        
        // 模拟当前评估阶段
        ConfigurationPhase currentPhase = ConfigurationPhase.PARSE_CONFIGURATION;
        
        System.out.println("📋 条件列表：");
        for (int i = 0; i < conditions.size(); i++) {
            Condition condition = conditions.get(i);
            System.out.println("   " + (i + 1) + ". " + condition.getClass().getSimpleName());
        }
        System.out.println("📍 当前阶段：" + currentPhase);
        System.out.println();
        
        // 🔥 核心代码逐行执行
        System.out.println("🔄 开始逐行执行核心代码：");
        System.out.println("for (Condition condition : conditions) {");
        
        boolean shouldSkip = evaluateConditionsLineByLine(conditions, currentPhase);
        
        System.out.println("}");
        System.out.println();
        System.out.println("🎯 最终结果：" + (shouldSkip ? "跳过注册" : "继续注册"));
    }

    /**
     * 逐行执行条件评估逻辑
     */
    private static boolean evaluateConditionsLineByLine(List<Condition> conditions, ConfigurationPhase phase) {
        
        for (int i = 0; i < conditions.size(); i++) {
            Condition condition = conditions.get(i);
            
            System.out.println("\n🔄 第" + (i + 1) + "轮循环：处理 " + condition.getClass().getSimpleName());
            System.out.println("   📝 第1行：for循环获取condition实例");
            
            // 第2行：初始化阶段变量
            System.out.println("   📝 第2行：ConfigurationPhase requiredPhase = null;");
            ConfigurationPhase requiredPhase = null;
            System.out.println("      → requiredPhase 初始化为 null");
            
            // 第3-5行：类型检查和阶段获取
            System.out.println("   📝 第3行：if (condition instanceof ConfigurationCondition) {");
            if (condition instanceof ConfigurationCondition) {
                System.out.println("      → ✅ 是ConfigurationCondition类型");
                System.out.println("   📝 第4行：requiredPhase = ((ConfigurationCondition) condition).getConfigurationPhase();");
                requiredPhase = ((ConfigurationCondition) condition).getConfigurationPhase();
                System.out.println("      → requiredPhase = " + requiredPhase);
            } else {
                System.out.println("      → ❌ 不是ConfigurationCondition类型，跳过");
                System.out.println("      → requiredPhase 保持为 null");
            }
            System.out.println("   📝 第5行：}");
            
            // 第6行：核心判断逻辑
            System.out.println("   📝 第6行：if ((requiredPhase == null || requiredPhase == phase) && !condition.matches(...)) {");
            
            // 判断1：阶段匹配检查
            boolean phaseMatches = (requiredPhase == null || requiredPhase == phase);
            System.out.println("      🔍 判断1：阶段匹配检查");
            System.out.println("         requiredPhase == null: " + (requiredPhase == null));
            System.out.println("         requiredPhase == phase: " + (requiredPhase == phase));
            System.out.println("         阶段匹配结果: " + phaseMatches);
            
            if (phaseMatches) {
                // 判断2：条件评估
                System.out.println("      🔍 判断2：条件评估");
                boolean conditionMatches = condition.matches(null, null);
                System.out.println("         condition.matches(): " + conditionMatches);
                System.out.println("         !condition.matches(): " + !conditionMatches);
                
                // 组合判断
                boolean shouldEnterIf = phaseMatches && !conditionMatches;
                System.out.println("      🎯 组合判断：(阶段匹配 && 条件不满足) = " + shouldEnterIf);
                
                if (shouldEnterIf) {
                    System.out.println("   📝 第7行：return true;");
                    System.out.println("      → 🚨 条件不满足，立即跳过注册（短路求值）");
                    return true;
                } else {
                    System.out.println("      → ✅ 条件满足，继续下一个条件");
                }
            } else {
                System.out.println("      → ⏭️ 阶段不匹配，跳过此条件评估");
            }
        }
        
        System.out.println("\n📝 循环结束，执行：return false;");
        System.out.println("   → ✅ 所有条件都满足，不跳过注册");
        return false;
    }

    // ========== 模拟条件类 ==========

    /**
     * 普通条件：数据库条件
     */
    static class DatabaseCondition implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            System.out.println("         🔍 DatabaseCondition.matches() 被调用");
            System.out.println("         💡 检查数据库配置是否存在...");
            boolean result = true; // 模拟条件满足
            System.out.println("         ✅ 数据库配置存在，返回 " + result);
            return result;
        }
    }

    /**
     * 配置条件：缓存条件（REGISTER_BEAN阶段）
     */
    static class CacheCondition implements ConfigurationCondition {
        @Override
        public ConfigurationPhase getConfigurationPhase() {
            return ConfigurationPhase.REGISTER_BEAN;
        }

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            System.out.println("         🔍 CacheCondition.matches() 被调用");
            System.out.println("         💡 检查缓存配置是否启用...");
            boolean result = true; // 模拟条件满足
            System.out.println("         ✅ 缓存配置启用，返回 " + result);
            return result;
        }
    }

    /**
     * 普通条件：环境条件
     */
    static class ProfileCondition implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            System.out.println("         🔍 ProfileCondition.matches() 被调用");
            System.out.println("         💡 检查当前环境是否为production...");
            boolean result = false; // 模拟条件不满足
            System.out.println("         ❌ 当前不是production环境，返回 " + result);
            return result;
        }
    }

    // ========== 辅助方法 ==========

    /**
     * 打印代码分析总结
     */
    private static void printAnalysisSummary() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("📊 逐行代码分析总结");
        System.out.println("=".repeat(60));
        
        System.out.println("🔍 第1行：for (Condition condition : conditions)");
        System.out.println("   作用：遍历所有条件实例，采用短路求值策略");
        
        System.out.println("\n🔍 第2行：ConfigurationPhase requiredPhase = null;");
        System.out.println("   作用：初始化阶段变量，默认为null（普通条件）");
        
        System.out.println("\n🔍 第3-5行：类型检查和阶段获取");
        System.out.println("   作用：区分普通Condition和ConfigurationCondition");
        System.out.println("   设计：支持多态，向后兼容");
        
        System.out.println("\n🔍 第6行：核心判断逻辑");
        System.out.println("   判断1：(requiredPhase == null || requiredPhase == phase)");
        System.out.println("   判断2：!condition.matches(this.context, metadata)");
        System.out.println("   组合：阶段匹配 && 条件不满足 → 跳过");
        
        System.out.println("\n🔍 第7行：return true;");
        System.out.println("   作用：短路返回，立即跳过注册");
        
        System.out.println("\n🎯 设计精髓：");
        System.out.println("   ⚡ 短路求值：任一条件失败立即退出");
        System.out.println("   🎯 阶段感知：只在正确时机评估条件");
        System.out.println("   🔄 类型多态：支持两种条件接口");
        System.out.println("   🚀 性能优化：避免不必要的评估");
    }
}