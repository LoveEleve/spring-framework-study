package com.debug.simpleDemo_1.service;

import org.springframework.stereotype.Service;

/**
 * 通知服务
 */
@Service
public class NotificationService {
    
    public void sendNotification(String message) {
        System.out.println("发送通知: " + message);
    }
}