package com.debug.circulDemo.multi;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 多循环依赖 + AOP代理 配置类
 * 
 * 注意：需要配置数据源才能让@Transactional生效
 * 这里仅作为演示代码，实际运行需要配置DataSource和TransactionManager
 * 
 * @author debug
 */
@Configuration
@ComponentScan("com.debug.circulDemo.multi")
@EnableTransactionManagement
public class MultiCircularWithProxyConfig {
    
    // 实际运行时需要配置：
    // 1. DataSource
    // 2. PlatformTransactionManager
    // 这里省略，仅演示代理创建逻辑
}
