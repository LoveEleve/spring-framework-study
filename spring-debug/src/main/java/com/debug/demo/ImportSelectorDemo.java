package com.debug.demo;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.context.annotation.ImportSelector;

import java.util.function.Predicate;

/**
 * ImportSelector接口演示
 * 
 * ImportSelector允许根据条件动态选择要导入的配置类
 * 执行时机：在配置类解析过程中立即执行
 */
public class ImportSelectorDemo {

    public static void main(String[] args) {
        System.out.println("=== ImportSelector Demo ===");
        
        // 设置系统属性来演示条件导入
        System.setProperty("database.type", "mysql");
        System.setProperty("cache.enabled", "true");
        
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(AppConfig.class);
        
        // 查看容器中的Bean
        System.out.println("\n注册的Bean:");
        for (String beanName : context.getBeanDefinitionNames()) {
            if (!beanName.startsWith("org.springframework")) {
                System.out.println("  - " + beanName + " : " + context.getBean(beanName).getClass().getSimpleName());
            }
        }
        
        context.close();
    }

    /**
     * 主配置类，使用@Import导入DatabaseConfigSelector
     */
    @Configuration
    @Import(DatabaseConfigSelector.class)
    static class AppConfig {
        
        @Bean
        public String appName() {
            return "ImportSelector Demo App";
        }
    }

    /**
     * 数据库配置选择器
     * 根据系统属性选择不同的数据库配置
     */
    static class DatabaseConfigSelector implements ImportSelector {

        @Override
        public String[] selectImports(AnnotationMetadata importingClassMetadata) {
            System.out.println("\n=== DatabaseConfigSelector.selectImports() 被调用 ===");
            System.out.println("导入配置类: " + importingClassMetadata.getClassName());
            
            // 获取系统属性
            String databaseType = System.getProperty("database.type", "h2");
            boolean cacheEnabled = Boolean.parseBoolean(System.getProperty("cache.enabled", "false"));
            
            System.out.println("数据库类型: " + databaseType);
            System.out.println("缓存启用: " + cacheEnabled);
            
            // 根据条件选择要导入的配置类
            java.util.List<String> imports = new java.util.ArrayList<>();
            
            // 根据数据库类型选择配置
            switch (databaseType.toLowerCase()) {
                case "mysql":
                    imports.add(MySQLConfig.class.getName());
                    break;
                case "postgresql":
                    imports.add(PostgreSQLConfig.class.getName());
                    break;
                default:
                    imports.add(H2Config.class.getName());
            }
            
            // 根据缓存配置选择
            if (cacheEnabled) {
                imports.add(CacheConfig.class.getName());
            }
            
            // 总是导入通用配置
            imports.add(CommonConfig.class.getName());
            
            String[] result = imports.toArray(new String[0]);
            System.out.println("选择导入的配置类:");
            for (String className : result) {
                System.out.println("  - " + className);
            }
            
            return result;
        }

        @Override
        public Predicate<String> getExclusionFilter() {
            // 排除测试相关的类
            return className -> className.contains("Test");
        }
    }

    /**
     * MySQL数据库配置
     */
    @Configuration
    static class MySQLConfig {
        
        @Bean
        public String dataSource() {
            return "MySQL DataSource";
        }
        
        @Bean
        public String jdbcTemplate() {
            return "MySQL JdbcTemplate";
        }
    }

    /**
     * PostgreSQL数据库配置
     */
    @Configuration
    static class PostgreSQLConfig {
        
        @Bean
        public String dataSource() {
            return "PostgreSQL DataSource";
        }
        
        @Bean
        public String jdbcTemplate() {
            return "PostgreSQL JdbcTemplate";
        }
    }

    /**
     * H2数据库配置（默认）
     */
    @Configuration
    static class H2Config {
        
        @Bean
        public String dataSource() {
            return "H2 DataSource";
        }
        
        @Bean
        public String jdbcTemplate() {
            return "H2 JdbcTemplate";
        }
    }

    /**
     * 缓存配置
     */
    @Configuration
    static class CacheConfig {
        
        @Bean
        public String cacheManager() {
            return "Redis Cache Manager";
        }
        
        @Bean
        public String cacheTemplate() {
            return "Cache Template";
        }
    }

    /**
     * 通用配置
     */
    @Configuration
    static class CommonConfig {
        
        @Bean
        public String transactionManager() {
            return "DataSource Transaction Manager";
        }
        
        @Bean
        public String auditService() {
            return "Audit Service";
        }
    }
}