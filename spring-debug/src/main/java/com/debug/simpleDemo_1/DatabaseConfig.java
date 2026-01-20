package com.debug.simpleDemo_1;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 数据库配置类 - 通过@Import导入
 * 
 * Debug要点：
 * 1. 这个类会在processImports()中被处理
 * 2. 作为普通配置类递归调用processConfigurationClass()
 * 3. isImported()返回true，在加载阶段会注册自身为BeanDefinition
 */
@Configuration(proxyBeanMethods = false)  // Lite配置类，不会被CGLIB代理
public class DatabaseConfig {
    
    @Bean
    public String dataSource() {
        System.out.println("DatabaseConfig.dataSource() @Bean方法被调用");
        return "datasource-bean";
    }
}