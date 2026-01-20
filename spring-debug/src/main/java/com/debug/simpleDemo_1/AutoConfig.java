package com.debug.simpleDemo_1;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 自动配置类 - 通过MyDeferredImportSelector延迟导入
 * 
 * Debug要点：
 * 1. 这个配置类是在DeferredImportSelector延迟处理阶段被导入的
 * 2. 体现了延迟导入的机制，确保在所有常规配置处理完后再处理
 */
@Configuration
public class AutoConfig {
    
    @Bean
    public String autoService() {
        System.out.println("AutoConfig.autoService() 被调用 - 延迟导入的配置类");
        return "auto-service-bean";
    }
}