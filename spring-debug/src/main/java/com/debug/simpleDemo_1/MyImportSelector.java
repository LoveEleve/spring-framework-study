package com.debug.simpleDemo_1;

import org.springframework.context.annotation.ImportSelector;
import org.springframework.core.type.AnnotationMetadata;

/**
 * ImportSelector - 立即处理
 * 
 * Debug要点：
 * 1. 在processImports()中被识别为ImportSelector类型
 * 2. 立即调用selectImports()方法
 * 3. 返回的类名会递归调用processImports()处理
 */
public class MyImportSelector implements ImportSelector {
    
    @Override
    public String[] selectImports(AnnotationMetadata importingClassMetadata) {
        System.out.println("MyImportSelector.selectImports() 被调用");
        System.out.println("导入类: " + importingClassMetadata.getClassName());
        
        // 返回要导入的配置类
        return new String[]{
            CacheConfig.class.getName()
        };
    }
}