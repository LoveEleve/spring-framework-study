package com.debug.circulDemo.multi;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * 多循环依赖Demo启动类
 * 
 * 场景演示：
 * - ServiceA 被 ServiceB 和 ServiceC 同时循环依赖
 * - A → B, A → C
 * - B → A
 * - C → A
 * 
 * 三级缓存的作用：
 * 1. 第一次B获取A时，调用ObjectFactory.getObject()，创建早期引用（可能是代理）
 * 2. 把结果放入二级缓存，移除三级缓存
 * 3. 第二次C获取A时，直接从二级缓存获取，确保返回同一个对象
 * 
 * @author debug
 */
public class MultiCircularApp {

    public static void main(String[] args) {
        AnnotationConfigApplicationContext context = 
            new AnnotationConfigApplicationContext(MultiCircularConfig.class);

        System.out.println("===== 容器启动完成 =====\n");

        ServiceA serviceA = context.getBean(ServiceA.class);
        ServiceB serviceB = context.getBean(ServiceB.class);
        ServiceC serviceC = context.getBean(ServiceC.class);

        System.out.println("验证循环依赖：");
        System.out.println("serviceA.serviceB = " + serviceA.getServiceB());
        System.out.println("serviceA.serviceC = " + serviceA.getServiceC());
        System.out.println();
        
        System.out.println("验证对象一致性（关键！）：");
        System.out.println("serviceB.serviceA = " + serviceB.getServiceA());
        System.out.println("serviceC.serviceA = " + serviceC.getServiceA());
        System.out.println();
        
        // 验证B和C持有的是同一个A实例
        boolean isSameA = serviceB.getServiceA() == serviceC.getServiceA();
        System.out.println("serviceB.serviceA == serviceC.serviceA ? " + isSameA);
        System.out.println("✅ 二级缓存确保多次获取返回同一个对象！");
        
        context.close();
    }
}
