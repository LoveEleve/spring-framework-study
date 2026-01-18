package com.debug.demo;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.context.annotation.DeferredImportSelector;

import java.util.*;

/**
 * DeferredImportSelector接口演示
 * 
 * DeferredImportSelector在所有常规配置类处理完成后才执行
 * 支持分组和排序，常用于自动配置场景
 */
public class DeferredImportSelectorDemo {

    public static void main(String[] args) {
        System.out.println("=== DeferredImportSelector Demo ===");
        
        // 设置系统属性
        System.setProperty("auto.config.enabled", "true");
        System.setProperty("feature.web", "true");
        System.setProperty("feature.security", "true");
        System.setProperty("feature.data", "false");
        
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(MainConfig.class);
        
        // 查看容器中的Bean
        System.out.println("\n最终注册的Bean:");
        String[] beanNames = context.getBeanDefinitionNames();
        Arrays.sort(beanNames);
        for (String beanName : beanNames) {
            if (!beanName.startsWith("org.springframework")) {
                Object bean = context.getBean(beanName);
                System.out.println("  - " + beanName + " : " + bean);
            }
        }
        
        context.close();
    }

    /**
     * 主配置类
     */
    @Configuration
    @Import({
        RegularConfig.class,           // 常规配置类
        AutoConfigurationSelector.class // 延迟导入选择器
    })
    static class MainConfig {
        
        @Bean
        public String mainService() {
            return "Main Service";
        }
    }

    /**
     * 常规配置类（会先于DeferredImportSelector处理）
     */
    @Configuration
    static class RegularConfig {
        
        @Bean
        public String regularService() {
            System.out.println("RegularConfig: 创建 regularService");
            return "Regular Service";
        }
    }

    /**
     * 自动配置选择器
     * 实现DeferredImportSelector，在所有常规配置处理完成后执行
     */
    static class AutoConfigurationSelector implements DeferredImportSelector {

        @Override
        public String[] selectImports(AnnotationMetadata importingClassMetadata) {
            System.out.println("\n=== AutoConfigurationSelector.selectImports() 被调用 ===");
            System.out.println("此时所有常规配置类已经处理完成");
            
            // 检查系统属性
            boolean autoConfigEnabled = Boolean.parseBoolean(System.getProperty("auto.config.enabled", "false"));
            if (!autoConfigEnabled) {
                System.out.println("自动配置被禁用");
                return new String[0];
            }
            
            List<String> imports = new ArrayList<>();
            
            // 根据特性开关选择配置
            if (Boolean.parseBoolean(System.getProperty("feature.web", "false"))) {
                imports.add(WebAutoConfig.class.getName());
            }
            
            if (Boolean.parseBoolean(System.getProperty("feature.security", "false"))) {
                imports.add(SecurityAutoConfig.class.getName());
            }
            
            if (Boolean.parseBoolean(System.getProperty("feature.data", "false"))) {
                imports.add(DataAutoConfig.class.getName());
            }
            
            System.out.println("自动配置选择的类:");
            for (String className : imports) {
                System.out.println("  - " + className);
            }
            
            return imports.toArray(new String[0]);
        }

        @Override
        public Class<? extends Group> getImportGroup() {
            // 使用自定义分组
            return AutoConfigurationGroup.class;
        }
    }

    /**
     * 自动配置分组
     * 用于对延迟导入进行分组和排序
     */
    static class AutoConfigurationGroup implements DeferredImportSelector.Group {
        
        private final List<Entry> imports = new ArrayList<>();

        @Override
        public void process(AnnotationMetadata metadata, DeferredImportSelector selector) {
            System.out.println("\n=== AutoConfigurationGroup.process() 被调用 ===");
            System.out.println("处理选择器: " + selector.getClass().getSimpleName());
            
            // 获取要导入的类
            String[] importClassNames = selector.selectImports(metadata);
            
            // 创建Entry并添加到列表
            for (String importClassName : importClassNames) {
                imports.add(new Entry(metadata, importClassName));
            }
        }

        @Override
        public Iterable<Entry> selectImports() {
            System.out.println("\n=== AutoConfigurationGroup.selectImports() 被调用 ===");
            
            // 可以在这里进行排序和过滤
            imports.sort((e1, e2) -> {
                // 按类名排序，确保确定的加载顺序
                return e1.getImportClassName().compareTo(e2.getImportClassName());
            });
            
            System.out.println("最终导入顺序:");
            for (Entry entry : imports) {
                System.out.println("  - " + entry.getImportClassName());
            }
            
            return imports;
        }
    }

    /**
     * Web自动配置
     */
    @Configuration
    static class WebAutoConfig {
        
        @Bean
        public String webMvcConfigurer() {
            System.out.println("WebAutoConfig: 创建 webMvcConfigurer");
            return "Web MVC Configurer";
        }
        
        @Bean
        public String restTemplate() {
            System.out.println("WebAutoConfig: 创建 restTemplate");
            return "Rest Template";
        }
    }

    /**
     * 安全自动配置
     */
    @Configuration
    static class SecurityAutoConfig {
        
        @Bean
        public String securityFilterChain() {
            System.out.println("SecurityAutoConfig: 创建 securityFilterChain");
            return "Security Filter Chain";
        }
        
        @Bean
        public String authenticationManager() {
            System.out.println("SecurityAutoConfig: 创建 authenticationManager");
            return "Authentication Manager";
        }
    }

    /**
     * 数据访问自动配置
     */
    @Configuration
    static class DataAutoConfig {
        
        @Bean
        public String entityManagerFactory() {
            System.out.println("DataAutoConfig: 创建 entityManagerFactory");
            return "Entity Manager Factory";
        }
        
        @Bean
        public String transactionManager() {
            System.out.println("DataAutoConfig: 创建 transactionManager");
            return "JPA Transaction Manager";
        }
    }
}