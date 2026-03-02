package com.debug.boot.verify;

import org.springframework.boot.autoconfigure.*;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.context.annotation.DeferredImportSelector;
import org.springframework.core.Ordered;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/**
 * 第③篇《自动配置核心机制深度分析》验证器
 * 通过 System.out.println 打印运行时数据验证文档结论
 */
@Component
public class AutoConfigVerifier {

    @PostConstruct
    public void verify() {
        System.out.println("\n╔══════════════════════════════════════════════════════════╗");
        System.out.println("║    第③篇验证器：自动配置核心机制深度分析                    ║");
        System.out.println("╚══════════════════════════════════════════════════════════╝\n");

        verify1_SpringFactoriesResourceLocation();
        verify2_ImportCandidatesLocation();
        verify3_CandidateConfigurationsFromImports();
        verify4_AutoConfigurationImportSelectorIsDeferredImportSelector();
        verify5_AutoConfigurationImportSelectorOrder();
        verify6_AutoConfigurationImportFilterInSpringFactories();
        verify7_AutoConfigurationImportListenerInSpringFactories();
        verify8_AutoConfigurationMetadataPath();
        verify9_AutoConfigureOrderDefaultValue();
        verify10_EnabledOverrideProperty();
        verify11_AutoConfigurationAnnotationIsConfiguration();
        verify12_GetImportGroupReturnsAutoConfigurationGroup();

        System.out.println("\n══════════════════════════════════════════════════════════");
        System.out.println("  第③篇验证完成！共 12 个验证点");
        System.out.println("══════════════════════════════════════════════════════════\n");
    }

    /**
     * 验证点1：SpringFactoriesLoader.FACTORIES_RESOURCE_LOCATION = "META-INF/spring.factories"
     * 文档 2.2 节
     */
    private void verify1_SpringFactoriesResourceLocation() {
        System.out.println("┌── 验证点 1：SpringFactoriesLoader.FACTORIES_RESOURCE_LOCATION");
        String location = SpringFactoriesLoader.FACTORIES_RESOURCE_LOCATION;
        System.out.println("│   运行数据: FACTORIES_RESOURCE_LOCATION = \"" + location + "\"");
        System.out.println("│   文档结论: \"META-INF/spring.factories\"");
        boolean pass = "META-INF/spring.factories".equals(location);
        System.out.println("└── 验证结果: " + (pass ? "✅ 通过" : "❌ 失败") + "\n");
    }

    /**
     * 验证点2：ImportCandidates 的文件路径模板 = "META-INF/spring/%s.imports"
     * 文档 3.2 节
     */
    private void verify2_ImportCandidatesLocation() {
        System.out.println("┌── 验证点 2：ImportCandidates 文件路径模板");
        try {
            Field locationField = ImportCandidates.class.getDeclaredField("LOCATION");
            locationField.setAccessible(true);
            String location = (String) locationField.get(null);
            System.out.println("│   运行数据: LOCATION = \"" + location + "\"");
            System.out.println("│   文档结论: \"META-INF/spring/%s.imports\"");
            boolean pass = "META-INF/spring/%s.imports".equals(location);
            System.out.println("└── 验证结果: " + (pass ? "✅ 通过" : "❌ 失败") + "\n");
        } catch (Exception e) {
            System.out.println("└── 验证结果: ❌ 异常: " + e.getMessage() + "\n");
        }
    }

    /**
     * 验证点3：从 .imports 文件加载的候选类数量 = 144
     * 文档 3.2 节
     */
    private void verify3_CandidateConfigurationsFromImports() {
        System.out.println("┌── 验证点 3：.imports 文件中的候选自动配置类数量");
        ImportCandidates candidates = ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader());
        int count = 0;
        for (String c : candidates) {
            count++;
        }
        System.out.println("│   运行数据: .imports 文件中共 " + count + " 个自动配置类");
        System.out.println("│   文档结论: 144 个");
        boolean pass = count == 144;
        System.out.println("└── 验证结果: " + (pass ? "✅ 通过" : "❌ 失败（实际=" + count + "）") + "\n");
    }

    /**
     * 验证点4：AutoConfigurationImportSelector 是 DeferredImportSelector
     * 文档 4.1 节、7.1 节
     */
    private void verify4_AutoConfigurationImportSelectorIsDeferredImportSelector() {
        System.out.println("┌── 验证点 4：AutoConfigurationImportSelector 是 DeferredImportSelector");
        boolean isDeferredImportSelector = DeferredImportSelector.class.isAssignableFrom(AutoConfigurationImportSelector.class);
        System.out.println("│   运行数据: DeferredImportSelector.isAssignableFrom(ACIS) = " + isDeferredImportSelector);
        System.out.println("│   文档结论: true（实现了 DeferredImportSelector 接口）");
        System.out.println("└── 验证结果: " + (isDeferredImportSelector ? "✅ 通过" : "❌ 失败") + "\n");
    }

    /**
     * 验证点5：AutoConfigurationImportSelector 的 Order = Ordered.LOWEST_PRECEDENCE - 1
     * 文档 4.1 节
     */
    private void verify5_AutoConfigurationImportSelectorOrder() {
        System.out.println("┌── 验证点 5：AutoConfigurationImportSelector 的 Order 值");
        try {
            AutoConfigurationImportSelector selector = new AutoConfigurationImportSelector();
            int order = selector.getOrder();
            int expected = Ordered.LOWEST_PRECEDENCE - 1;
            System.out.println("│   运行数据: getOrder() = " + order);
            System.out.println("│   文档结论: Ordered.LOWEST_PRECEDENCE - 1 = " + expected);
            boolean pass = order == expected;
            System.out.println("└── 验证结果: " + (pass ? "✅ 通过" : "❌ 失败") + "\n");
        } catch (Exception e) {
            System.out.println("└── 验证结果: ❌ 异常: " + e.getMessage() + "\n");
        }
    }

    /**
     * 验证点6：spring.factories 中注册了 3 个 AutoConfigurationImportFilter
     * 文档 5.6 节
     */
    private void verify6_AutoConfigurationImportFilterInSpringFactories() {
        System.out.println("┌── 验证点 6：spring.factories 中的 AutoConfigurationImportFilter");
        List<String> filterNames = SpringFactoriesLoader.loadFactoryNames(
                AutoConfigurationImportFilter.class, getClass().getClassLoader());
        System.out.println("│   运行数据: 注册的过滤器数量 = " + filterNames.size());
        for (String name : filterNames) {
            String shortName = name.substring(name.lastIndexOf('.') + 1);
            System.out.println("│     - " + shortName);
        }
        boolean hasOnClass = filterNames.stream().anyMatch(n -> n.contains("OnClassCondition"));
        boolean hasOnBean = filterNames.stream().anyMatch(n -> n.contains("OnBeanCondition"));
        boolean hasOnWeb = filterNames.stream().anyMatch(n -> n.contains("OnWebApplicationCondition"));
        System.out.println("│   文档结论: 3 个（OnClassCondition, OnBeanCondition, OnWebApplicationCondition）");
        boolean pass = filterNames.size() == 3 && hasOnClass && hasOnBean && hasOnWeb;
        System.out.println("└── 验证结果: " + (pass ? "✅ 通过" : "❌ 失败") + "\n");
    }

    /**
     * 验证点7：spring.factories 中注册了 ConditionEvaluationReportAutoConfigurationImportListener
     * 文档 8.2 节
     */
    private void verify7_AutoConfigurationImportListenerInSpringFactories() {
        System.out.println("┌── 验证点 7：spring.factories 中的 AutoConfigurationImportListener");
        List<String> listenerNames = SpringFactoriesLoader.loadFactoryNames(
                AutoConfigurationImportListener.class, getClass().getClassLoader());
        System.out.println("│   运行数据: 注册的监听器数量 = " + listenerNames.size());
        for (String name : listenerNames) {
            String shortName = name.substring(name.lastIndexOf('.') + 1);
            System.out.println("│     - " + shortName);
        }
        boolean hasCondEvalReport = listenerNames.stream()
                .anyMatch(n -> n.contains("ConditionEvaluationReportAutoConfigurationImportListener"));
        System.out.println("│   文档结论: ConditionEvaluationReportAutoConfigurationImportListener");
        System.out.println("└── 验证结果: " + (hasCondEvalReport ? "✅ 通过" : "❌ 失败") + "\n");
    }

    /**
     * 验证点8：AutoConfigurationMetadataLoader.PATH = "META-INF/spring-autoconfigure-metadata.properties"
     * 文档 5.3 节
     */
    private void verify8_AutoConfigurationMetadataPath() {
        System.out.println("┌── 验证点 8：AutoConfigurationMetadataLoader.PATH");
        try {
            Class<?> loaderClass = Class.forName("org.springframework.boot.autoconfigure.AutoConfigurationMetadataLoader");
            Field pathField = loaderClass.getDeclaredField("PATH");
            pathField.setAccessible(true);
            String path = (String) pathField.get(null);
            System.out.println("│   运行数据: PATH = \"" + path + "\"");
            System.out.println("│   文档结论: \"META-INF/spring-autoconfigure-metadata.properties\"");
            boolean pass = "META-INF/spring-autoconfigure-metadata.properties".equals(path);
            System.out.println("└── 验证结果: " + (pass ? "✅ 通过" : "❌ 失败") + "\n");
        } catch (Exception e) {
            System.out.println("└── 验证结果: ❌ 异常: " + e.getMessage() + "\n");
        }
    }

    /**
     * 验证点9：AutoConfigureOrder.DEFAULT_ORDER = 0
     * 文档 6.2 节
     */
    private void verify9_AutoConfigureOrderDefaultValue() {
        System.out.println("┌── 验证点 9：AutoConfigureOrder.DEFAULT_ORDER");
        int defaultOrder = AutoConfigureOrder.DEFAULT_ORDER;
        System.out.println("│   运行数据: DEFAULT_ORDER = " + defaultOrder);
        System.out.println("│   文档结论: 0");
        boolean pass = defaultOrder == 0;
        System.out.println("└── 验证结果: " + (pass ? "✅ 通过" : "❌ 失败") + "\n");
    }

    /**
     * 验证点10：EnableAutoConfiguration.ENABLED_OVERRIDE_PROPERTY = "spring.boot.enableautoconfiguration"
     * 文档 4.3 节
     */
    private void verify10_EnabledOverrideProperty() {
        System.out.println("┌── 验证点 10：EnableAutoConfiguration.ENABLED_OVERRIDE_PROPERTY");
        String prop = EnableAutoConfiguration.ENABLED_OVERRIDE_PROPERTY;
        System.out.println("│   运行数据: ENABLED_OVERRIDE_PROPERTY = \"" + prop + "\"");
        System.out.println("│   文档结论: \"spring.boot.enableautoconfiguration\"");
        boolean pass = "spring.boot.enableautoconfiguration".equals(prop);
        System.out.println("└── 验证结果: " + (pass ? "✅ 通过" : "❌ 失败") + "\n");
    }

    /**
     * 验证点11：@AutoConfiguration 上标注了 @Configuration(proxyBeanMethods = false)
     * 文档 3.4 节
     */
    private void verify11_AutoConfigurationAnnotationIsConfiguration() {
        System.out.println("┌── 验证点 11：@AutoConfiguration 标注 @Configuration(proxyBeanMethods=false)");
        boolean hasConfiguration = AutoConfiguration.class.isAnnotationPresent(
                org.springframework.context.annotation.Configuration.class);
        org.springframework.context.annotation.Configuration configAnno =
                AutoConfiguration.class.getAnnotation(org.springframework.context.annotation.Configuration.class);
        boolean proxyBeanMethods = configAnno != null && configAnno.proxyBeanMethods();
        System.out.println("│   运行数据: 标注 @Configuration = " + hasConfiguration);
        System.out.println("│   运行数据: proxyBeanMethods = " + proxyBeanMethods);
        System.out.println("│   文档结论: @Configuration(proxyBeanMethods = false)");
        boolean pass = hasConfiguration && !proxyBeanMethods;
        System.out.println("└── 验证结果: " + (pass ? "✅ 通过" : "❌ 失败") + "\n");
    }

    /**
     * 验证点12：AutoConfigurationImportSelector.getImportGroup() 返回 AutoConfigurationGroup
     * 文档 7.5 节
     */
    private void verify12_GetImportGroupReturnsAutoConfigurationGroup() {
        System.out.println("┌── 验证点 12：getImportGroup() 返回的 Group 类名");
        try {
            AutoConfigurationImportSelector selector = new AutoConfigurationImportSelector();
            Class<? extends DeferredImportSelector.Group> groupClass = selector.getImportGroup();
            String groupName = groupClass != null ? groupClass.getSimpleName() : "null";
            System.out.println("│   运行数据: getImportGroup() = " + groupName);
            System.out.println("│   文档结论: AutoConfigurationGroup");
            boolean pass = groupName.equals("AutoConfigurationGroup");
            System.out.println("└── 验证结果: " + (pass ? "✅ 通过" : "❌ 失败") + "\n");
        } catch (Exception e) {
            System.out.println("└── 验证结果: ❌ 异常: " + e.getMessage() + "\n");
        }
    }
}
