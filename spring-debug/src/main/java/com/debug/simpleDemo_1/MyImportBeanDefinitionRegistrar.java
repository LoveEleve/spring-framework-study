package com.debug.simpleDemo_1;

import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;

/**
 * ImportBeanDefinitionRegistrar - 编程式注册BeanDefinition
 * 
 * Debug要点：
 * 1. 在processImports()中被识别为ImportBeanDefinitionRegistrar类型
 * 2. 被收集到configClass.importBeanDefinitionRegistrars中
 * 3. 在加载阶段通过loadBeanDefinitionsFromRegistrars()调用
 * 4. 允许编程式注册BeanDefinition，非常灵活
 */
public class MyImportBeanDefinitionRegistrar implements ImportBeanDefinitionRegistrar {
    
    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, 
                                      BeanDefinitionRegistry registry) {
        System.out.println("MyImportBeanDefinitionRegistrar.registerBeanDefinitions() 被调用");
        System.out.println("导入类: " + importingClassMetadata.getClassName());
        
        // 编程式注册第一个Bean
        GenericBeanDefinition beanDef1 = new GenericBeanDefinition();
        beanDef1.setBeanClass(String.class);
        beanDef1.getConstructorArgumentValues().addGenericArgumentValue("programmatic-bean-1");
        registry.registerBeanDefinition("programmaticBean1", beanDef1);
        
        // 编程式注册第二个Bean
        GenericBeanDefinition beanDef2 = new GenericBeanDefinition();
        beanDef2.setBeanClass(String.class);
        beanDef2.getConstructorArgumentValues().addGenericArgumentValue("programmatic-bean-2");
        registry.registerBeanDefinition("programmaticBean2", beanDef2);
        
        System.out.println("编程式注册了2个Bean: programmaticBean1, programmaticBean2");
    }
}