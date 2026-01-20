package com.debug.simpleDemo_1;

import org.springframework.context.annotation.DeferredImportSelector;
import org.springframework.core.annotation.Order;
import org.springframework.core.type.AnnotationMetadata;

/**
 * DeferredImportSelector - 延迟处理，最后执行
 * 
 * Debug要点：
 * 1. 在processImports()中被识别为DeferredImportSelector类型
 * 2. 被添加到deferredImportSelectorHandler中延迟处理
 * 3. 在所有常规配置类解析完后，通过deferredImportSelectorHandler.process()处理
 * 4. 支持分组和排序机制
 */
@Order(100)  // 低优先级，最后执行
public class MyDeferredImportSelector implements DeferredImportSelector {
    
    @Override
    public String[] selectImports(AnnotationMetadata importingClassMetadata) {
        System.out.println("MyDeferredImportSelector.selectImports() 被调用 - 延迟处理阶段");
        System.out.println("导入类: " + importingClassMetadata.getClassName());
        
        return new String[]{
            AutoConfig.class.getName()
        };
    }
    
    /**
     * 自定义分组 - 演示DeferredImportSelector分组机制
     */
    @Override
    public Class<? extends Group> getImportGroup() {
        return MyDeferredImportGroup.class;
    }
    
    /**
     * 自定义分组处理器
     */
    public static class MyDeferredImportGroup implements Group {
        
        @Override
        public void process(AnnotationMetadata metadata, DeferredImportSelector selector) {
            System.out.println("MyDeferredImportGroup.process() 被调用");
            System.out.println("处理选择器: " + selector.getClass().getSimpleName());
        }
        
        @Override
        public Iterable<Entry> selectImports() {
            System.out.println("MyDeferredImportGroup.selectImports() 被调用");
            
            return java.util.Arrays.asList(
                new Entry(AnnotationMetadata.introspect(MyDeferredImportSelector.class), 
                         AutoConfig.class.getName())
            );
        }
    }
}