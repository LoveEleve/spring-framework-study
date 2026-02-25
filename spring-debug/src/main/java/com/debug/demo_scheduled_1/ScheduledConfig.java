package com.debug.demo_scheduled_1;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 调度配置类
 *
 * @EnableScheduling 开启定时任务支持
 * 类比 @EnableAsync，会注册 ScheduledAnnotationBeanPostProcessor
 */
@Configuration
@ComponentScan("com.debug.demo_scheduled_1")
@EnableScheduling
@EnableTransactionManagement
public class ScheduledConfig {

}
