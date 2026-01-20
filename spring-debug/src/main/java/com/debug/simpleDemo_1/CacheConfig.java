package com.debug.simpleDemo_1;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 缓存配置类 - 通过MyImportSelector导入
 */
@Configuration
public class CacheConfig {
    
    @Bean
    public String cacheManager() {
        System.out.println("CacheConfig.cacheManager() @Bean方法被调用");
        return "cache-manager-bean";
    }
}