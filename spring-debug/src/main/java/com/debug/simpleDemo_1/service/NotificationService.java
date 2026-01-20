package com.debug.simpleDemo_1.service;

import org.springframework.stereotype.Component;

/**
 * 通知服务 - 通过@ComponentScan扫描的组件
 * 演示@ComponentScan的立即注册机制
 */
@Component
public class NotificationService {
    
    public String sendNotification(String message) {
        return "Notification sent: " + message;
    }
}