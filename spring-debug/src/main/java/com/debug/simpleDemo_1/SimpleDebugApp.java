package com.debug.simpleDemo_1;

import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 简单Debug应用 - 添加@EventListener演示
 * 
 * 核心Debug断点位置：
 * 
 * 1. ConfigurationClassPostProcessor.processConfigBeanDefinitions():283
 *    - 方法入口，观察整体流程
 * 
 * 2. EventListenerMethodProcessor.postProcessBeanFactory()
 *    - @EventListener注解处理
 * 
 * 3. ConfigurationClassUtils.checkConfigurationClassCandidate():117
 *    - 配置类候选者检查，区分Full/Lite配置类
 * 
 * 4. ConfigurationClassParser.parse():181
 *    - 解析阶段入口，观察do-while循环
 * 
 * 5. ConfigurationClassParser.processImports():794
 *    - @Import处理，观察ImportSelector的处理
 * 
 * 6. ConfigurationClassBeanDefinitionReader.loadBeanDefinitions():126
 *    - 加载阶段入口
 * 
 * 7. ConfigurationClassBeanDefinitionReader.loadBeanDefinitionsForBeanMethod():190
 *    - @Bean方法转换为BeanDefinition
 */
public class SimpleDebugApp {
    public static void main(String[] args) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(CoreMainConfig.class);
    }
}