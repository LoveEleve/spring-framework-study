package com.debug.simpleDemo_1.service;

import org.springframework.stereotype.Service;

/**
 * 普通组件 - 通过@ComponentScan扫描
 * 
 * Debug要点：
 * 1. 通过@ComponentScan立即扫描并注册
 * 2. 在ClassPathBeanDefinitionScanner.doScan()中处理
 * 3. 立即调用registry.registerBeanDefinition()注册
 */
@Service
public class ProductService {
    
    public String getProduct() {
        return "product-from-service";
    }
}