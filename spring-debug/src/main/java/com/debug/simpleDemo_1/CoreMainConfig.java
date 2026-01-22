package com.debug.simpleDemo_1;

import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;

/**
 * 核心主配置类 - 专注于processConfigBeanDefinitions的核心逻辑
 * 
 * 核心Debug断点：
 * 1. ConfigurationClassPostProcessor.processConfigBeanDefinitions():283 - 方法入口
 * 2. EventListenerMethodProcessor.postProcessBeanFactory() - @EventListener处理
 * 3. ConfigurationClassUtils.checkConfigurationClassCandidate():117 - 检查配置类类型
 * 4. ConfigurationClassParser.parse():181 - 解析阶段
 * 5. ConfigurationClassParser.processImports():794 - @Import处理
 * 6. ConfigurationClassParser.DeferredImportSelectorHandler.process():1051 - 延迟处理
 * 7. ConfigurationClassBeanDefinitionReader.loadBeanDefinitions():126 - 加载阶段
 */
@Configuration  // Full配置类，会被CGLIB代理
@ComponentScan(basePackages = {
    "com.debug.simpleDemo_1.service",
    "com.debug.simpleDemo_1"  // 扫描EventListeners组件
})
@Import({
    DatabaseConfig.class,                    // 普通配置类导入
    MyImportSelector.class,                  // ImportSelector立即处理
    MyDeferredImportSelector.class,          // DeferredImportSelector延迟处理
    MyImportBeanDefinitionRegistrar.class  // ImportBeanDefinitionRegistrar编程式注册
})
@Order(1)  // 配置类排序
public class CoreMainConfig {
    
    /**
     * 普通@Bean方法
     */
    @Bean
    public String mainService() {
        System.out.println("CoreMainConfig.mainService() 被调用");
        return "main-service-bean";
    }
    
    /**
     * 依赖其他Bean的方法 - 演示Full配置类的Bean方法间调用
     */
    @Bean
    public String compositeService() {
        // 在Full配置类中，这个调用会被CGLIB拦截，保证单例
        String main = mainService();
        System.out.println("CoreMainConfig.compositeService() 调用了 mainService(): " + main);
        return "composite-service-bean";
    }
}