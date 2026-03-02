package com.debug.boot.verify;

import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.autoconfigure.AutoConfigurationImportFilter;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConfigurationCondition;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;

/**
 * 第④篇《条件注解体系深度分析》验证器
 * 通过 System.out.println 打印运行时数据，验证文档中的每个关键结论
 */
@Component
public class ConditionAnnotationVerifier {

    @PostConstruct
    public void verify() {
        System.out.println("\n========================================");
        System.out.println("  第④篇验证器：条件注解体系深度分析");
        System.out.println("========================================\n");

        // 验证点 1：Condition 是函数式接口
        boolean isFunctionalInterface = Condition.class.isAnnotationPresent(FunctionalInterface.class);
        System.out.println("验证点 1 | Condition 是 @FunctionalInterface");
        System.out.println("  运行数据: " + isFunctionalInterface);
        System.out.println("  文档结论: true（2.1 节）");
        System.out.println("  验证结果: " + (isFunctionalInterface ? "✅ 通过" : "❌ 失败"));

        // 验证点 2：@ConditionalOnClass 的元注解 @Conditional 指向 OnClassCondition
        Annotation[] conditionalOnClassAnnotations = ConditionalOnClass.class.getAnnotations();
        String conditionalTarget = "";
        for (Annotation a : conditionalOnClassAnnotations) {
            if (a instanceof org.springframework.context.annotation.Conditional) {
                Class<? extends Condition>[] value = ((org.springframework.context.annotation.Conditional) a).value();
                if (value.length > 0) {
                    conditionalTarget = value[0].getSimpleName();
                }
            }
        }
        System.out.println("\n验证点 2 | @ConditionalOnClass 的 @Conditional 指向 OnClassCondition");
        System.out.println("  运行数据: " + conditionalTarget);
        System.out.println("  文档结论: OnClassCondition（2.5 节 / 5.1 节）");
        System.out.println("  验证结果: " + ("OnClassCondition".equals(conditionalTarget) ? "✅ 通过" : "❌ 失败"));

        // 验证点 3：@ConditionalOnBean 的 @Conditional 指向 OnBeanCondition
        String onBeanTarget = "";
        for (Annotation a : ConditionalOnBean.class.getAnnotations()) {
            if (a instanceof org.springframework.context.annotation.Conditional) {
                Class<? extends Condition>[] value = ((org.springframework.context.annotation.Conditional) a).value();
                if (value.length > 0) {
                    onBeanTarget = value[0].getSimpleName();
                }
            }
        }
        System.out.println("\n验证点 3 | @ConditionalOnBean 的 @Conditional 指向 OnBeanCondition");
        System.out.println("  运行数据: " + onBeanTarget);
        System.out.println("  文档结论: OnBeanCondition（2.5 节 / 6.1 节）");
        System.out.println("  验证结果: " + ("OnBeanCondition".equals(onBeanTarget) ? "✅ 通过" : "❌ 失败"));

        // 验证点 4：@ConditionalOnMissingBean 也指向 OnBeanCondition（同一个实现类）
        String onMissingBeanTarget = "";
        for (Annotation a : ConditionalOnMissingBean.class.getAnnotations()) {
            if (a instanceof org.springframework.context.annotation.Conditional) {
                Class<? extends Condition>[] value = ((org.springframework.context.annotation.Conditional) a).value();
                if (value.length > 0) {
                    onMissingBeanTarget = value[0].getSimpleName();
                }
            }
        }
        System.out.println("\n验证点 4 | @ConditionalOnMissingBean 也指向 OnBeanCondition");
        System.out.println("  运行数据: " + onMissingBeanTarget);
        System.out.println("  文档结论: OnBeanCondition（6.1 节）");
        System.out.println("  验证结果: " + ("OnBeanCondition".equals(onMissingBeanTarget) ? "✅ 通过" : "❌ 失败"));

        // 验证点 5：SpringBootCondition.matches() 是 final 方法
        boolean matchesIsFinal = false;
        try {
            java.lang.reflect.Method matchesMethod = SpringBootCondition.class.getMethod("matches",
                    org.springframework.context.annotation.ConditionContext.class,
                    org.springframework.core.type.AnnotatedTypeMetadata.class);
            matchesIsFinal = java.lang.reflect.Modifier.isFinal(matchesMethod.getModifiers());
        } catch (Exception e) {
            // ignore
        }
        System.out.println("\n验证点 5 | SpringBootCondition.matches() 是 final 方法");
        System.out.println("  运行数据: " + matchesIsFinal);
        System.out.println("  文档结论: true（3.1 节）");
        System.out.println("  验证结果: " + (matchesIsFinal ? "✅ 通过" : "❌ 失败"));

        // 验证点 6：OnClassCondition 继承 FilteringSpringBootCondition
        boolean onClassExtendsFiltering = false;
        try {
            Class<?> onClassCondition = Class.forName("org.springframework.boot.autoconfigure.condition.OnClassCondition");
            onClassExtendsFiltering = AutoConfigurationImportFilter.class.isAssignableFrom(onClassCondition);
        } catch (Exception e) {
            // ignore
        }
        System.out.println("\n验证点 6 | OnClassCondition 实现了 AutoConfigurationImportFilter（通过 FilteringSpringBootCondition）");
        System.out.println("  运行数据: " + onClassExtendsFiltering);
        System.out.println("  文档结论: true（4.2 节 / 5.2 节）");
        System.out.println("  验证结果: " + (onClassExtendsFiltering ? "✅ 通过" : "❌ 失败"));

        // 验证点 7：OnClassCondition 的 @Order = HIGHEST_PRECEDENCE
        int onClassOrder = Integer.MAX_VALUE;
        try {
            Class<?> onClassCondition = Class.forName("org.springframework.boot.autoconfigure.condition.OnClassCondition");
            Order orderAnno = onClassCondition.getAnnotation(Order.class);
            if (orderAnno != null) {
                onClassOrder = orderAnno.value();
            }
        } catch (Exception e) {
            // ignore
        }
        System.out.println("\n验证点 7 | OnClassCondition 的 @Order = Ordered.HIGHEST_PRECEDENCE");
        System.out.println("  运行数据: " + onClassOrder + " (HIGHEST_PRECEDENCE=" + Ordered.HIGHEST_PRECEDENCE + ")");
        System.out.println("  文档结论: Ordered.HIGHEST_PRECEDENCE（5.2 节 / 8.4 节）");
        System.out.println("  验证结果: " + (onClassOrder == Ordered.HIGHEST_PRECEDENCE ? "✅ 通过" : "❌ 失败"));

        // 验证点 8：OnWebApplicationCondition 的 @Order = HIGHEST_PRECEDENCE + 20
        int onWebOrder = Integer.MAX_VALUE;
        try {
            Class<?> onWebCondition = Class.forName("org.springframework.boot.autoconfigure.condition.OnWebApplicationCondition");
            Order orderAnno = onWebCondition.getAnnotation(Order.class);
            if (orderAnno != null) {
                onWebOrder = orderAnno.value();
            }
        } catch (Exception e) {
            // ignore
        }
        System.out.println("\n验证点 8 | OnWebApplicationCondition 的 @Order = HIGHEST_PRECEDENCE + 20");
        System.out.println("  运行数据: " + onWebOrder + " (HIGHEST_PRECEDENCE+20=" + (Ordered.HIGHEST_PRECEDENCE + 20) + ")");
        System.out.println("  文档结论: Ordered.HIGHEST_PRECEDENCE + 20（8.2 节 / 8.4 节）");
        System.out.println("  验证结果: " + (onWebOrder == Ordered.HIGHEST_PRECEDENCE + 20 ? "✅ 通过" : "❌ 失败"));

        // 验证点 9：OnBeanCondition 的 @Order = LOWEST_PRECEDENCE
        int onBeanOrder = Integer.MIN_VALUE;
        try {
            Class<?> onBeanCondition = Class.forName("org.springframework.boot.autoconfigure.condition.OnBeanCondition");
            Order orderAnno = onBeanCondition.getAnnotation(Order.class);
            if (orderAnno != null) {
                onBeanOrder = orderAnno.value();
            }
        } catch (Exception e) {
            // ignore
        }
        System.out.println("\n验证点 9 | OnBeanCondition 的 @Order = Ordered.LOWEST_PRECEDENCE");
        System.out.println("  运行数据: " + onBeanOrder + " (LOWEST_PRECEDENCE=" + Ordered.LOWEST_PRECEDENCE + ")");
        System.out.println("  文档结论: Ordered.LOWEST_PRECEDENCE（6.3 节 / 8.4 节）");
        System.out.println("  验证结果: " + (onBeanOrder == Ordered.LOWEST_PRECEDENCE ? "✅ 通过" : "❌ 失败"));

        // 验证点 10：OnBeanCondition 实现了 ConfigurationCondition
        boolean onBeanIsConfigCondition = false;
        try {
            Class<?> onBeanCondition = Class.forName("org.springframework.boot.autoconfigure.condition.OnBeanCondition");
            onBeanIsConfigCondition = ConfigurationCondition.class.isAssignableFrom(onBeanCondition);
        } catch (Exception e) {
            // ignore
        }
        System.out.println("\n验证点 10 | OnBeanCondition 实现了 ConfigurationCondition");
        System.out.println("  运行数据: " + onBeanIsConfigCondition);
        System.out.println("  文档结论: true（6.3 节 / 2.4 节）");
        System.out.println("  验证结果: " + (onBeanIsConfigCondition ? "✅ 通过" : "❌ 失败"));

        // 验证点 11：OnBeanCondition.getConfigurationPhase() = REGISTER_BEAN
        String phase = "";
        try {
            Class<?> onBeanCondition = Class.forName("org.springframework.boot.autoconfigure.condition.OnBeanCondition");
            java.lang.reflect.Constructor<?> ctor = onBeanCondition.getDeclaredConstructor();
            ctor.setAccessible(true);
            Object instance = ctor.newInstance();
            java.lang.reflect.Method getPhase = onBeanCondition.getMethod("getConfigurationPhase");
            getPhase.setAccessible(true);
            Object result = getPhase.invoke(instance);
            phase = result.toString();
        } catch (Exception e) {
            // ignore
        }
        System.out.println("\n验证点 11 | OnBeanCondition.getConfigurationPhase() = REGISTER_BEAN");
        System.out.println("  运行数据: " + phase);
        System.out.println("  文档结论: REGISTER_BEAN（6.3 节）");
        System.out.println("  验证结果: " + ("REGISTER_BEAN".equals(phase) ? "✅ 通过" : "❌ 失败"));

        // 验证点 12：OnPropertyCondition 的 @Order = HIGHEST_PRECEDENCE + 40
        int onPropertyOrder = Integer.MIN_VALUE;
        try {
            Class<?> onPropertyCondition = Class.forName("org.springframework.boot.autoconfigure.condition.OnPropertyCondition");
            Order orderAnno = onPropertyCondition.getAnnotation(Order.class);
            if (orderAnno != null) {
                onPropertyOrder = orderAnno.value();
            }
        } catch (Exception e) {
            // ignore
        }
        System.out.println("\n验证点 12 | OnPropertyCondition 的 @Order = HIGHEST_PRECEDENCE + 40");
        System.out.println("  运行数据: " + onPropertyOrder + " (HIGHEST_PRECEDENCE+40=" + (Ordered.HIGHEST_PRECEDENCE + 40) + ")");
        System.out.println("  文档结论: Ordered.HIGHEST_PRECEDENCE + 40（7.3 节）");
        System.out.println("  验证结果: " + (onPropertyOrder == Ordered.HIGHEST_PRECEDENCE + 40 ? "✅ 通过" : "❌ 失败"));

        // 验证点 13：ConditionEvaluationReport.BEAN_NAME = "autoConfigurationReport"
        String reportBeanName = "";
        try {
            Field beanNameField = ConditionEvaluationReport.class.getDeclaredField("BEAN_NAME");
            beanNameField.setAccessible(true);
            reportBeanName = (String) beanNameField.get(null);
        } catch (Exception e) {
            // ignore
        }
        System.out.println("\n验证点 13 | ConditionEvaluationReport.BEAN_NAME = 'autoConfigurationReport'");
        System.out.println("  运行数据: \"" + reportBeanName + "\"");
        System.out.println("  文档结论: \"autoConfigurationReport\"（9.2 节）");
        System.out.println("  验证结果: " + ("autoConfigurationReport".equals(reportBeanName) ? "✅ 通过" : "❌ 失败"));

        // 验证点 14：SearchStrategy 枚举有 3 个值：CURRENT, ANCESTORS, ALL
        SearchStrategy[] strategies = SearchStrategy.values();
        boolean hasThreeStrategies = strategies.length == 3
                && strategies[0] == SearchStrategy.CURRENT
                && strategies[1] == SearchStrategy.ANCESTORS
                && strategies[2] == SearchStrategy.ALL;
        System.out.println("\n验证点 14 | SearchStrategy 枚举有 3 个值：CURRENT, ANCESTORS, ALL");
        System.out.println("  运行数据: " + java.util.Arrays.toString(strategies));
        System.out.println("  文档结论: [CURRENT, ANCESTORS, ALL]（6.2 节）");
        System.out.println("  验证结果: " + (hasThreeStrategies ? "✅ 通过" : "❌ 失败"));

        // 验证点 15：@ConditionalOnProperty 的 matchIfMissing 默认值 = false
        boolean matchIfMissingDefault = true; // 预设为 true 以检测
        try {
            java.lang.reflect.Method m = ConditionalOnProperty.class.getDeclaredMethod("matchIfMissing");
            matchIfMissingDefault = (boolean) m.getDefaultValue();
        } catch (Exception e) {
            // ignore
        }
        System.out.println("\n验证点 15 | @ConditionalOnProperty.matchIfMissing() 默认值 = false");
        System.out.println("  运行数据: " + matchIfMissingDefault);
        System.out.println("  文档结论: false（7.1 节）");
        System.out.println("  验证结果: " + (!matchIfMissingDefault ? "✅ 通过" : "❌ 失败"));

        System.out.println("\n========================================");
        System.out.println("  第④篇验证完成");
        System.out.println("========================================\n");
    }
}
