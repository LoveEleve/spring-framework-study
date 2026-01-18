package com.debug.demo;

import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.context.annotation.*;
import org.springframework.core.type.AnnotationMetadata;

import java.lang.annotation.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * 综合演示三种Import接口的使用
 * 
 * 展示ImportSelector、DeferredImportSelector和ImportBeanDefinitionRegistrar
 * 在同一个应用中的协作和执行顺序
 */
public class ComprehensiveImportDemo {

    public static void main(String[] args) {
        System.out.println("=== 综合Import接口演示 ===");
        System.out.println("演示执行顺序：ImportSelector -> 常规配置 -> DeferredImportSelector -> ImportBeanDefinitionRegistrar");
        
        // 设置系统属性
        System.setProperty("app.profile", "production");
        System.setProperty("feature.monitoring", "true");
        System.setProperty("feature.metrics", "true");
        
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(MainAppConfig.class);
        
        // 查看最终结果
        System.out.println("\n=== 最终容器中的Bean ===");
        String[] beanNames = context.getBeanDefinitionNames();
        java.util.Arrays.sort(beanNames);
        for (String beanName : beanNames) {
            if (!beanName.startsWith("org.springframework")) {
                Object bean = context.getBean(beanName);
                System.out.println("  - " + beanName + " : " + bean);
            }
        }
        
        context.close();
    }

    /**
     * 主应用配置类
     * 组合使用三种Import机制
     */
    @Configuration
    @EnableFeatures(
        profile = "production",
        features = {"monitoring", "metrics", "security"}
    )
    @Import({
        // 1. ImportSelector - 立即执行
        EnvironmentConfigSelector.class,
        
        // 2. 常规配置类
        CoreConfig.class,
        
        // 3. DeferredImportSelector - 延迟执行
        AutoConfigurationImportSelector.class
        
        // 4. ImportBeanDefinitionRegistrar - 通过@EnableFeatures注解导入
    })
    static class MainAppConfig {
        
        @Bean
        public String applicationName() {
            System.out.println("[MainAppConfig] 创建 applicationName");
            return "Comprehensive Import Demo";
        }
    }

    /**
     * 启用特性的注解
     * 内部使用ImportBeanDefinitionRegistrar
     */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @Import(FeatureRegistrar.class)
    @interface EnableFeatures {
        String profile() default "default";
        String[] features() default {};
    }

    // ==================== 1. ImportSelector ====================
    
    /**
     * 环境配置选择器 - ImportSelector
     * 立即执行，根据环境选择配置
     */
    static class EnvironmentConfigSelector implements ImportSelector {

        @Override
        public String[] selectImports(AnnotationMetadata importingClassMetadata) {
            System.out.println("\n[1] EnvironmentConfigSelector.selectImports() - 立即执行");
            
            String profile = System.getProperty("app.profile", "default");
            System.out.println("    检测到环境: " + profile);
            
            List<String> imports = new ArrayList<>();
            
            switch (profile) {
                case "production":
                    imports.add(ProductionConfig.class.getName());
                    break;
                case "development":
                    imports.add(DevelopmentConfig.class.getName());
                    break;
                default:
                    imports.add(DefaultConfig.class.getName());
            }
            
            System.out.println("    ImportSelector选择的配置: " + imports);
            return imports.toArray(new String[0]);
        }

        @Override
        public Predicate<String> getExclusionFilter() {
            return className -> className.contains("Exclude");
        }
    }

    // ==================== 2. 常规配置类 ====================
    
    /**
     * 核心配置类 - 常规配置
     * 在ImportSelector之后，DeferredImportSelector之前处理
     */
    @Configuration
    static class CoreConfig {
        
        @Bean
        public String coreService() {
            System.out.println("[2] CoreConfig 创建 coreService - 常规配置处理");
            return "Core Service";
        }
        
        @Bean
        public String databaseConnection() {
            System.out.println("[2] CoreConfig 创建 databaseConnection");
            return "Database Connection Pool";
        }
    }

    // ==================== 3. DeferredImportSelector ====================
    
    /**
     * 自动配置导入选择器 - DeferredImportSelector
     * 延迟执行，在所有常规配置处理完成后执行
     */
    static class AutoConfigurationImportSelector implements DeferredImportSelector {

        @Override
        public String[] selectImports(AnnotationMetadata importingClassMetadata) {
            System.out.println("\n[3] AutoConfigurationImportSelector.selectImports() - 延迟执行");
            System.out.println("    此时所有常规配置已处理完成，可以看到全局状态");
            
            List<String> imports = new ArrayList<>();
            
            // 检查特性开关
            if (Boolean.parseBoolean(System.getProperty("feature.monitoring", "false"))) {
                imports.add(MonitoringAutoConfig.class.getName());
            }
            
            if (Boolean.parseBoolean(System.getProperty("feature.metrics", "false"))) {
                imports.add(MetricsAutoConfig.class.getName());
            }
            
            System.out.println("    DeferredImportSelector选择的配置: " + imports);
            return imports.toArray(new String[0]);
        }

        @Override
        public Class<? extends Group> getImportGroup() {
            return AutoConfigurationGroup.class;
        }
    }

    /**
     * 自动配置分组
     */
    static class AutoConfigurationGroup implements DeferredImportSelector.Group {
        private final List<Entry> imports = new ArrayList<>();

        @Override
        public void process(AnnotationMetadata metadata, DeferredImportSelector selector) {
            System.out.println("[3] AutoConfigurationGroup.process()");
            String[] importClassNames = selector.selectImports(metadata);
            for (String importClassName : importClassNames) {
                imports.add(new Entry(metadata, importClassName));
            }
        }

        @Override
        public Iterable<Entry> selectImports() {
            System.out.println("[3] AutoConfigurationGroup.selectImports() - 返回最终导入列表");
            // 可以在这里进行排序和过滤
            imports.sort((e1, e2) -> e1.getImportClassName().compareTo(e2.getImportClassName()));
            return imports;
        }
    }

    // ==================== 4. ImportBeanDefinitionRegistrar ====================
    
    /**
     * 特性注册器 - ImportBeanDefinitionRegistrar
     * 直接注册BeanDefinition，提供最大灵活性
     */
    static class FeatureRegistrar implements ImportBeanDefinitionRegistrar {

        @Override
        public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, 
                                          BeanDefinitionRegistry registry, 
                                          BeanNameGenerator importBeanNameGenerator) {
            
            System.out.println("\n[4] FeatureRegistrar.registerBeanDefinitions() - 直接注册Bean");
            
            // 获取注解属性
            Map<String, Object> attributes = importingClassMetadata
                .getAnnotationAttributes(EnableFeatures.class.getName());
            
            if (attributes != null) {
                String profile = (String) attributes.get("profile");
                String[] features = (String[]) attributes.get("features");
                
                System.out.println("    环境: " + profile);
                System.out.println("    特性: " + java.util.Arrays.toString(features));
                
                // 为每个特性注册对应的Bean
                for (String feature : features) {
                    registerFeatureBean(registry, feature, profile);
                }
                
                // 注册特性管理器
                registerFeatureManager(registry, features);
            }
        }

        private void registerFeatureBean(BeanDefinitionRegistry registry, String feature, String profile) {
            String beanName = feature + "Feature";
            
            BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(FeatureBean.class);
            builder.addConstructorArgValue(feature);
            builder.addConstructorArgValue(profile);
            builder.addConstructorArgValue(true); // enabled
            
            registry.registerBeanDefinition(beanName, builder.getBeanDefinition());
            System.out.println("    注册特性Bean: " + beanName);
        }

        private void registerFeatureManager(BeanDefinitionRegistry registry, String[] features) {
            BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(FeatureManager.class);
            builder.addConstructorArgValue(features);
            
            registry.registerBeanDefinition("featureManager", builder.getBeanDefinition());
            System.out.println("    注册特性管理器: featureManager");
        }
    }

    // ==================== 配置类定义 ====================
    
    @Configuration
    static class ProductionConfig {
        @Bean
        public String productionDataSource() {
            System.out.println("[Production] 创建生产环境数据源");
            return "Production DataSource";
        }
    }

    @Configuration
    static class DevelopmentConfig {
        @Bean
        public String developmentDataSource() {
            System.out.println("[Development] 创建开发环境数据源");
            return "Development DataSource";
        }
    }

    @Configuration
    static class DefaultConfig {
        @Bean
        public String defaultDataSource() {
            System.out.println("[Default] 创建默认数据源");
            return "Default DataSource";
        }
    }

    @Configuration
    static class MonitoringAutoConfig {
        @Bean
        public String monitoringService() {
            System.out.println("[AutoConfig] 创建监控服务");
            return "Monitoring Service";
        }
    }

    @Configuration
    static class MetricsAutoConfig {
        @Bean
        public String metricsCollector() {
            System.out.println("[AutoConfig] 创建指标收集器");
            return "Metrics Collector";
        }
    }

    // ==================== 业务类定义 ====================
    
    /**
     * 特性Bean
     */
    static class FeatureBean {
        private final String name;
        private final String profile;
        private final boolean enabled;

        public FeatureBean(String name, String profile, boolean enabled) {
            this.name = name;
            this.profile = profile;
            this.enabled = enabled;
            System.out.println("[Feature] 创建特性: " + name + " (profile=" + profile + ", enabled=" + enabled + ")");
        }

        @Override
        public String toString() {
            return "Feature{name='" + name + "', profile='" + profile + "', enabled=" + enabled + "}";
        }
    }

    /**
     * 特性管理器
     */
    static class FeatureManager {
        private final String[] features;

        public FeatureManager(String[] features) {
            this.features = features;
            System.out.println("[FeatureManager] 管理特性: " + java.util.Arrays.toString(features));
        }

        @Override
        public String toString() {
            return "FeatureManager{features=" + java.util.Arrays.toString(features) + "}";
        }
    }
}