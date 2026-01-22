package com.debug.simpleDemo_1;

import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 最简单的@EventListener演示
 */
public class SimpleEventDemo {
    
    // 1. 自定义事件
    public static class MyEvent extends ApplicationEvent {
        private final String message;
        
        public MyEvent(Object source, String message) {
            super(source);
            this.message = message;
        }
        
        public String getMessage() {
            return message;
        }
    }
    
    // 2. 事件监听器
    @Component
    public static class MyEventListener {
        
        @EventListener
        public void handleMyEvent(MyEvent event) {
            System.out.println("收到事件: " + event.getMessage());
        }
    }
    
    // 3. 事件发布器
    @Component
    public static class MyEventPublisher {
        
        private final ApplicationEventPublisher eventPublisher;
        
        public MyEventPublisher(ApplicationEventPublisher eventPublisher) {
            this.eventPublisher = eventPublisher;
        }
        
        public void publishEvent(String message) {
            eventPublisher.publishEvent(new MyEvent(this, message));
        }
    }
    
    public static void main(String[] args) {
        System.out.println("========== 简单@EventListener演示 ==========");
        
        // 创建Spring容器
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.register(MyEventListener.class, MyEventPublisher.class);
        context.refresh();
        
        // 获取事件发布器并发布事件
        MyEventPublisher publisher = context.getBean(MyEventPublisher.class);
        publisher.publishEvent("Hello @EventListener!");
        publisher.publishEvent("这是一个简单的事件演示");
        
        context.close();
        System.out.println("========== 演示完成 ==========");
    }
}