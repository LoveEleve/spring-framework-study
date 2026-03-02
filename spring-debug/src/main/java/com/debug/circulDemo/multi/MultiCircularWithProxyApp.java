package com.debug.circulDemo.multi;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * 多循环依赖 + AOP代理 Demo启动类
 * 
 * 核心演示：为什么需要三级缓存？
 * 
 * 场景：
 * - ServiceAWithProxy 被 @Transactional 代理
 * - ServiceBWithProxy 和 ServiceCWithProxy 同时循环依赖 A
 * 
 * 三级缓存的作用：
 * 1. ObjectFactory 延迟决定是否创建代理
 * 2. 第一次调用时创建代理，放入二级缓存
 * 3. 后续直接从二级缓存获取，确保同一代理对象
 * 
 * @author debug
 */
public class MultiCircularWithProxyApp {

    public static void main(String[] args) {
        AnnotationConfigApplicationContext context = 
            new AnnotationConfigApplicationContext(MultiCircularWithProxyConfig.class);

        System.out.println("\n===== 容器启动完成 =====\n");

        ServiceAWithProxy serviceA = context.getBean(ServiceAWithProxy.class);
        ServiceBWithProxy serviceB = context.getBean(ServiceBWithProxy.class);
        ServiceCWithProxy serviceC = context.getBean(ServiceCWithProxy.class);

        System.out.println("验证代理对象：");
        System.out.println("serviceA.getClass() = " + serviceA.getClass().getName());
        boolean isProxy = serviceA.getClass().getName().contains("$Proxy") || 
                          serviceA.getClass().getName().contains("CGLIB");
        System.out.println("是否是代理对象？ " + (isProxy ? "✅ 是代理" : "❌ 不是代理"));
        System.out.println();

        System.out.println("验证循环依赖：");
        System.out.println("serviceA.serviceB = " + serviceA.getServiceB());
        System.out.println("serviceA.serviceC = " + serviceA.getServiceC());
        System.out.println();

        System.out.println("验证B和C持有的A是否一致（关键！）：");
        System.out.println("serviceB.serviceA = " + serviceB.getServiceA());
        System.out.println("serviceC.serviceA = " + serviceC.getServiceA());
        System.out.println();

        // 验证B和C持有的是同一个代理对象
        boolean isSameA = serviceB.getServiceA() == serviceC.getServiceA();
        System.out.println("serviceB.serviceA == serviceC.serviceA ? " + isSameA);
        
        // 验证B和C持有的是代理对象，而不是原始对象
        boolean isSameAsContainer = serviceB.getServiceA() == serviceA;
        System.out.println("serviceB.serviceA == 容器中的serviceA ? " + isSameAsContainer);
        
        if (isSameA && isSameAsContainer) {
            System.out.println("✅ 三级缓存确保：B和C持有的是同一个代理对象，与容器中的一致！");
        } else {
            System.out.println("❌ 出现了对象不一致的问题！");
        }

        context.close();
    }
}
