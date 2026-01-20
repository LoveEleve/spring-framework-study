package com.debug.simpleDemo_1;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * 简单Debug应用
 * 
 * 核心Debug断点位置：
 * 
 * 1. ConfigurationClassPostProcessor.processConfigBeanDefinitions():283
 *    - 方法入口，观察整体流程
 * 
 * 2. ConfigurationClassUtils.checkConfigurationClassCandidate():117
 *    - 配置类候选者检查，区分Full/Lite配置类
 * 
 * 3. ConfigurationClassParser.parse():181
 *    - 解析阶段入口，观察do-while循环
 * 
 * 4. ConfigurationClassParser.processImports():794
 *    - @Import处理，观察ImportSelector的处理
 * 
 * 5. ConfigurationClassBeanDefinitionReader.loadBeanDefinitions():126
 *    - 加载阶段入口
 * 
 * 6. ConfigurationClassBeanDefinitionReader.loadBeanDefinitionsForBeanMethod():190
 *    - @Bean方法转换为BeanDefinition
 */
public class SimpleDebugApp {
    
    public static void main(String[] args) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(CoreMainConfig.class);
    }

}