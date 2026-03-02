package com.debug.boot.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 简单的测试 Controller，用于验证 Spring Boot 启动是否正常
 */
@RestController
public class HelloController {

    @GetMapping("/hello")
    public String hello() {
        return "Hello from Spring Boot Debug!";
    }

    @GetMapping("/")
    public String index() {
        return "Spring Boot Debug Application is running!";
    }
}
