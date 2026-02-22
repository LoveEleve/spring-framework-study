package com.debug.aop_demo_transactional;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.annotation.PostConstruct;
import javax.sql.DataSource;

/**
 * 事务配置类 - MySQL版本
 */
@Configuration
@ComponentScan("com.debug.aop_demo_transactional")
@EnableTransactionManagement
public class TransactionalConfig {

    /**
     * 配置MySQL数据源
     * 请根据你的Docker MySQL配置修改URL、用户名、密码
     */
    @Bean
    public DataSource dataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        // Docker MySQL默认端口3306，数据库名：testdb
        dataSource.setUrl("jdbc:mysql://localhost:3388/testdb?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true");
        dataSource.setUsername("root");
        dataSource.setPassword("root");  // Docker MySQL密码
        return dataSource;
    }

    /**
     * 配置事务管理器
     */
    @Bean
    public DataSourceTransactionManager transactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    /**
     * JdbcTemplate（用于建表等操作）
     */
    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    /**
     * 初始化：创建表
     */
    @PostConstruct
    public void initDatabase() {
        System.out.println("========== MySQL Database Info ==========");
        System.out.println("URL: jdbc:mysql://localhost:3306/testdb");
        System.out.println("用户名: root");
        System.out.println("========================================");
    }
}
