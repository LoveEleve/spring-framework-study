package com.debug.demo;

/**
 * 运行所有Import接口演示的主类
 * 
 * 按顺序执行所有演示，展示不同Import接口的特点和用法
 */
public class RunAllImportDemos {

    public static void main(String[] args) {
        System.out.println("===============================================");
        System.out.println("       Spring Import接口完整演示");
        System.out.println("===============================================");
        
        try {
            // 1. ImportSelector演示
            System.out.println("\n\n1️⃣  ImportSelector 演示");
            System.out.println("─".repeat(50));
            ImportSelectorDemo.main(args);
            
            Thread.sleep(1000);
            
            // 2. DeferredImportSelector演示
            System.out.println("\n\n2️⃣  DeferredImportSelector 演示");
            System.out.println("─".repeat(50));
            DeferredImportSelectorDemo.main(args);
            
            Thread.sleep(1000);
            
            // 3. ImportBeanDefinitionRegistrar演示
            System.out.println("\n\n3️⃣  ImportBeanDefinitionRegistrar 演示");
            System.out.println("─".repeat(50));
            ImportBeanDefinitionRegistrarDemo.main(args);
            
            Thread.sleep(1000);
            
            // 4. 综合演示
            System.out.println("\n\n4️⃣  综合演示 - 三种接口协作");
            System.out.println("─".repeat(50));
            ComprehensiveImportDemo.main(args);
            
            Thread.sleep(1000);
            
            // 5. Spring Boot风格自动配置演示
            System.out.println("\n\n5️⃣  Spring Boot风格自动配置演示");
            System.out.println("─".repeat(50));
            SpringBootStyleAutoConfigDemo.main(args);
            
            System.out.println("\n\n===============================================");
            System.out.println("           所有演示执行完成！");
            System.out.println("===============================================");
            
            printSummary();
            
        } catch (Exception e) {
            System.err.println("演示执行出错: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void printSummary() {
        System.out.println("\n📋 演示总结:");
        System.out.println();
        System.out.println("1. ImportSelector");
        System.out.println("   • 立即执行，在配置类解析过程中");
        System.out.println("   • 根据条件动态选择要导入的配置类");
        System.out.println("   • 适用于简单的条件导入场景");
        System.out.println();
        System.out.println("2. DeferredImportSelector");
        System.out.println("   • 延迟执行，在所有常规配置处理完成后");
        System.out.println("   • 支持分组和排序");
        System.out.println("   • Spring Boot自动配置的核心机制");
        System.out.println();
        System.out.println("3. ImportBeanDefinitionRegistrar");
        System.out.println("   • 直接操作BeanDefinitionRegistry");
        System.out.println("   • 提供最大的灵活性和控制力");
        System.out.println("   • 适用于复杂的Bean注册场景");
        System.out.println();
        System.out.println("🔄 执行顺序:");
        System.out.println("   ImportSelector → 常规配置类 → DeferredImportSelector → ImportBeanDefinitionRegistrar");
        System.out.println();
        System.out.println("💡 选择建议:");
        System.out.println("   • 简单条件导入 → ImportSelector");
        System.out.println("   • 需要全局信息 → DeferredImportSelector");
        System.out.println("   • 复杂Bean注册 → ImportBeanDefinitionRegistrar");
    }
}