package com.debug.config;

import org.springframework.context.annotation.*;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;

/**
 * 事务配置类（进阶版）
 * 
 * 需要添加依赖：
 * - spring-tx
 * - spring-jdbc
 * - h2database
 */
@Configuration
@ComponentScan(basePackages = "com.debug")
@EnableAspectJAutoProxy
@EnableTransactionManagement  // 启用事务管理
public class TransactionConfig {

	/**
	 * 数据源配置 - 使用H2内存数据库
	 */
	@Bean
	public DataSource dataSource() {
		System.out.println("[配置] 创建DataSource...");
		return new EmbeddedDatabaseBuilder()
				.setType(EmbeddedDatabaseType.H2)
				.addScript("classpath:schema.sql")
				.build();
	}

	/**
	 * 事务管理器配置
	 */
	@Bean
	public PlatformTransactionManager transactionManager(DataSource dataSource) {
		System.out.println("[配置] 创建TransactionManager...");
		return new DataSourceTransactionManager(dataSource);
	}
}
