package com.debug.simpleDemo_1.service;

import org.springframework.stereotype.Service;

/**
 * 产品服务
 */
@Service
public class ProductService {
    
    public void createProduct(String productName) {
        System.out.println("创建产品: " + productName);
    }
    
    public void updateProduct(String productName) {
        System.out.println("更新产品: " + productName);
    }
}