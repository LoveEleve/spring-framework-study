# Spring Boot 如何玩转 @DependsOn 和 @Conditional

## 🚀 **Spring Boot的创新之处**

Spring Boot在原生Spring的 `@DependsOn` 和 `@Conditional` 基础上，做了大量的**扩展**和**优化**，让这两个注解变得更加强大和易用。

---

## 🎯 **@Conditional 的Spring Boot扩展**

### **原生Spring vs Spring Boot**

#### **原生Spring - 需要自定义实现**
```java
// 原生Spring需要自己实现Condition接口
public class DatabaseEnabledCondition implements Condition {
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Environment env = context.getEnvironment();
        return "true".equals(env.getProperty("database.enabled"));
    }
}

@Component
@Conditional(DatabaseEnabledCondition.class)  // 需要自定义条件类
public class DatabaseService {
}
```

#### **Spring Boot - 开箱即用的条件注解**
```java
// Spring Boot提供现成的条件注解
@Component
@ConditionalOnProperty(name = "database.enabled", havingValue = "true")  // 🔥 开箱即用
public class DatabaseService {
}
```

---

## 📚 **Spring Boot 条件注解全家桶**

### **1. @ConditionalOnProperty - 基于配置属性**

#### **基本用法**
```java
@Configuration
@ConditionalOnProperty(
    prefix = "feature.cache",           // 属性前缀
    name = "enabled",                   // 属性名 (完整路径: feature.cache.enabled)
    havingValue = "true",               // 期望值
    matchIfMissing = false              // 属性不存在时是否匹配
)
public class CacheConfig {
    
    @Bean
    public CacheManager cacheManager() {
        return new RedisCacheManager();
    }
}
```

#### **配置文件**
```yaml
# application.yml
feature:
  cache:
    enabled: true    # 🔥 控制CacheConfig是否生效
```

#### **高级用法**
```java
@Component
@ConditionalOnProperty(
    name = {"redis.enabled", "cache.type"},     // 多个属性都要满足
    havingValue = "redis",                      // redis.enabled=redis 且 cache.type=redis
    prefix = "app"                              // app.redis.enabled, app.cache.type
)
public class RedisService {
}
```

---

### **2. @ConditionalOnClass - 基于类路径**

#### **检查类是否存在**
```java
@Configuration
@ConditionalOnClass(RedisTemplate.class)  // 🔥 Redis类存在时才配置
public class RedisAutoConfiguration {
    
    @Bean
    @ConditionalOnMissingBean  // 没有用户自定义的RedisTemplate时才创建
    public RedisTemplate<String, Object> redisTemplate() {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        // 配置序列化等
        return template;
    }
}
```

#### **检查多个类**
```java
@Configuration
@ConditionalOnClass({
    DataSource.class,           // JDBC相关
    JdbcTemplate.class,         // Spring JDBC
    PlatformTransactionManager.class  // 事务管理
})
public class JdbcAutoConfiguration {
    // 只有当所有相关类都存在时才自动配置JDBC
}
```

---

### **3. @ConditionalOnBean - 基于Bean存在**

#### **依赖其他Bean**
```java
@Configuration
public class DatabaseConfig {
    
    @Bean
    @ConditionalOnBean(DataSource.class)  // 🔥 有DataSource时才创建
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
    
    @Bean
    @ConditionalOnBean(name = "primaryDataSource")  // 基于Bean名称
    public TransactionManager transactionManager(@Qualifier("primaryDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}
```

---

### **4. @ConditionalOnMissingBean - 基于Bean缺失**

#### **提供默认实现**
```java
@Configuration
public class UserServiceConfig {
    
    @Bean
    @ConditionalOnMissingBean(UserService.class)  // 🔥 没有自定义UserService时提供默认实现
    public UserService defaultUserService() {
        return new DefaultUserService();
    }
    
    @Bean
    @ConditionalOnMissingBean(name = "customUserValidator")
    public UserValidator userValidator() {
        return new DefaultUserValidator();
    }
}
```

**使用场景**：
```java
// 用户可以自定义实现
@Service
public class CustomUserService implements UserService {
    // 自定义实现会覆盖默认实现
}
```

---

### **5. @ConditionalOnProfile - 基于环境Profile**

```java
@Configuration
@ConditionalOnProfile("dev")  // 🔥 只在dev环境生效
public class DevConfig {
    
    @Bean
    public MockService mockService() {
        return new MockService();
    }
}

@Configuration
@ConditionalOnProfile({"prod", "staging"})  // 生产或预发环境
public class ProductionConfig {
    
    @Bean
    public MonitoringService monitoringService() {
        return new ProductionMonitoringService();
    }
}
```

---

### **6. @ConditionalOnWebApplication - 基于应用类型**

```java
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class WebMvcConfig {
    // 只在Servlet Web应用中生效
}

@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
public class WebFluxConfig {
    // 只在Reactive Web应用中生效
}
```

---

## 🔄 **@DependsOn 的Spring Boot应用**

### **自动配置中的依赖顺序**

#### **Spring Boot内部使用@DependsOn**
```java
// Spring Boot内部的自动配置示例
@Configuration
@AutoConfigureAfter(DataSourceAutoConfiguration.class)  // 🔥 在DataSource配置之后
@ConditionalOnClass({DataSource.class, JdbcTemplate.class})
public class JdbcTemplateAutoConfiguration {
    
    @Bean
    @Primary
    @DependsOn("dataSource")  // 🔥 显式依赖dataSource Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
```

#### **用户代码中的应用**
```java
@Configuration
public class ApplicationConfig {
    
    @Bean
    public ConfigurationLoader configLoader() {
        return new ConfigurationLoader();
    }
    
    @Bean
    @DependsOn("configLoader")  // 🔥 确保配置加载器先初始化
    public DatabaseConnectionPool connectionPool() {
        return new HikariDataSource();
    }
    
    @Bean
    @DependsOn({"configLoader", "connectionPool"})  // 🔥 依赖多个Bean
    public ApplicationService applicationService() {
        return new ApplicationService();
    }
}
```

---

## 🏗️ **Spring Boot自动配置的高级玩法**

### **@AutoConfigureOrder - 配置类顺序**

```java
@Configuration
@AutoConfigureOrder(Ordered.HIGHEST_PRECEDENCE)  // 🔥 最高优先级
@ConditionalOnClass(DataSource.class)
public class DataSourceAutoConfiguration {
    // 数据源配置 - 最先执行
}

@Configuration
@AutoConfigureOrder(Ordered.LOWEST_PRECEDENCE)   // 🔥 最低优先级
@AutoConfigureAfter(DataSourceAutoConfiguration.class)
public class JpaAutoConfiguration {
    // JPA配置 - 在数据源之后执行
}
```

### **@AutoConfigureBefore 和 @AutoConfigureAfter**

```java
@Configuration
@AutoConfigureBefore(WebMvcAutoConfiguration.class)  // 🔥 在WebMvc配置之前
@ConditionalOnWebApplication
public class SecurityAutoConfiguration {
    // 安全配置需要在WebMvc之前
}

@Configuration
@AutoConfigureAfter({
    DataSourceAutoConfiguration.class,
    HibernateJpaAutoConfiguration.class
})
@ConditionalOnClass(PlatformTransactionManager.class)
public class TransactionAutoConfiguration {
    // 事务配置在数据源和JPA之后
}
```

---

## 🎯 **实际项目中的最佳实践**

### **1. 功能开关模式**

```java
// 配置文件
# application.yml
features:
  payment:
    enabled: true
    provider: alipay
  notification:
    enabled: false
  analytics:
    enabled: true
    provider: google

// 功能配置
@Configuration
@ConditionalOnProperty(name = "features.payment.enabled", havingValue = "true")
public class PaymentConfig {
    
    @Bean
    @ConditionalOnProperty(name = "features.payment.provider", havingValue = "alipay")
    public PaymentService alipayService() {
        return new AlipayService();
    }
    
    @Bean
    @ConditionalOnProperty(name = "features.payment.provider", havingValue = "wechat")
    public PaymentService wechatService() {
        return new WechatPayService();
    }
}

@Service
@ConditionalOnProperty(name = "features.notification.enabled", havingValue = "true")
public class NotificationService {
    // 通知服务只在启用时才注册
}
```

### **2. 多环境配置**

```java
// 开发环境配置
@Configuration
@ConditionalOnProfile("dev")
public class DevConfig {
    
    @Bean
    @Primary  // 🔥 开发环境使用内存数据库
    public DataSource devDataSource() {
        return new EmbeddedDatabaseBuilder()
            .setType(EmbeddedDatabaseType.H2)
            .build();
    }
    
    @Bean
    public MockExternalService mockService() {
        return new MockExternalService();
    }
}

// 生产环境配置
@Configuration
@ConditionalOnProfile("prod")
public class ProdConfig {
    
    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSource prodDataSource() {
        return DataSourceBuilder.create().build();
    }
    
    @Bean
    @DependsOn("prodDataSource")  // 🔥 确保数据源先初始化
    public HealthCheckService healthCheck() {
        return new DatabaseHealthCheck();
    }
}
```

### **3. 微服务配置**

```java
@Configuration
@ConditionalOnProperty(name = "microservice.discovery.enabled", havingValue = "true")
public class ServiceDiscoveryConfig {
    
    @Bean
    @ConditionalOnProperty(name = "microservice.discovery.type", havingValue = "eureka")
    public EurekaClient eurekaClient() {
        return new EurekaClient();
    }
    
    @Bean
    @ConditionalOnProperty(name = "microservice.discovery.type", havingValue = "consul")
    public ConsulClient consulClient() {
        return new ConsulClient();
    }
    
    @Bean
    @DependsOn({"eurekaClient", "consulClient"})  // 🔥 依赖服务发现客户端
    @ConditionalOnBean({EurekaClient.class, ConsulClient.class})
    public ServiceRegistry serviceRegistry() {
        return new ServiceRegistry();
    }
}
```

---

## 🔍 **Spring Boot源码中的应用示例**

### **DataSourceAutoConfiguration**
```java
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass({ DataSource.class, EmbeddedDatabaseType.class })
@ConditionalOnMissingBean(type = "javax.sql.DataSource")
@EnableConfigurationProperties(DataSourceProperties.class)
@Import({ DataSourcePoolMetadataProvidersConfiguration.class,
         DataSourceInitializationConfiguration.class })
public class DataSourceAutoConfiguration {

    @Configuration(proxyBeanMethods = false)
    @Conditional(EmbeddedDatabaseCondition.class)
    @ConditionalOnMissingBean({ DataSource.class, XADataSource.class })
    @Import(EmbeddedDataSourceConfiguration.class)
    protected static class EmbeddedDatabaseConfiguration {
    }

    @Configuration(proxyBeanMethods = false)
    @Conditional(PooledDataSourceCondition.class)
    @ConditionalOnMissingBean({ DataSource.class, XADataSource.class })
    @Import({ DataSourceConfiguration.Hikari.class,
             DataSourceConfiguration.Tomcat.class,
             DataSourceConfiguration.Dbcp2.class,
             DataSourceConfiguration.OracleUcp.class,
             DataSourceConfiguration.Generic.class })
    protected static class PooledDataSourceConfiguration {
    }
}
```

### **WebMvcAutoConfiguration**
```java
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = Type.SERVLET)
@ConditionalOnClass({ Servlet.class, DispatcherServlet.class, WebMvcConfigurer.class })
@ConditionalOnMissingBean(WebMvcConfigurationSupport.class)
@AutoConfigureOrder(Ordered.HIGHEST_PRECEDENCE + 10)
@AutoConfigureAfter({ DispatcherServletAutoConfiguration.class,
                     TaskExecutionAutoConfiguration.class,
                     ValidationAutoConfiguration.class })
public class WebMvcAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(HiddenHttpMethodFilter.class)
    @ConditionalOnProperty(prefix = "spring.mvc.hiddenmethod.filter", name = "enabled", matchIfMissing = false)
    public OrderedHiddenHttpMethodFilter hiddenHttpMethodFilter() {
        return new OrderedHiddenHttpMethodFilter();
    }
}
```

---

## 🎨 **自定义条件注解**

### **创建组合条件注解**

```java
// 自定义组合条件注解
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ConditionalOnClass(RedisTemplate.class)
@ConditionalOnProperty(name = "spring.redis.enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnBean(RedisConnectionFactory.class)
public @interface ConditionalOnRedis {
}

// 使用组合注解
@Configuration
@ConditionalOnRedis  // 🔥 一个注解包含多个条件
public class RedisConfig {
    
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        return new RedisTemplate<>();
    }
}
```

### **自定义复杂条件**

```java
// 自定义条件实现
public class DatabaseTypeCondition implements Condition {
    
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Environment env = context.getEnvironment();
        String dbType = env.getProperty("spring.datasource.type");
        
        // 复杂的条件逻辑
        if ("mysql".equalsIgnoreCase(dbType)) {
            return checkMySQLDriver(context);
        } else if ("postgresql".equalsIgnoreCase(dbType)) {
            return checkPostgreSQLDriver(context);
        }
        
        return false;
    }
    
    private boolean checkMySQLDriver(ConditionContext context) {
        try {
            context.getClassLoader().loadClass("com.mysql.cj.jdbc.Driver");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}

@Configuration
@Conditional(DatabaseTypeCondition.class)
public class DatabaseSpecificConfig {
    // 基于数据库类型的特定配置
}
```

---

## 📊 **性能优化和最佳实践**

### **1. 条件评估优化**

```java
// ❌ 避免：复杂的条件逻辑
@ConditionalOnExpression("#{environment.getProperty('complex.condition') == 'true' && @someBean.isEnabled()}")
public class ComplexConditionConfig {
}

// ✅ 推荐：简单的属性条件
@ConditionalOnProperty(name = "simple.condition.enabled", havingValue = "true")
public class SimpleConditionConfig {
}
```

### **2. 条件注解顺序**

```java
@Configuration
@ConditionalOnClass(DataSource.class)           // 🔥 先检查类存在（快速失败）
@ConditionalOnProperty(name = "db.enabled")     // 再检查属性
@ConditionalOnBean(ConnectionFactory.class)     // 最后检查Bean存在（最耗时）
public class OptimizedConfig {
}
```

### **3. 避免循环依赖**

```java
// ❌ 错误：可能导致循环依赖
@Bean
@DependsOn("serviceB")
public ServiceA serviceA() {
    return new ServiceA();
}

@Bean
@DependsOn("serviceA")
public ServiceB serviceB() {
    return new ServiceB();
}

// ✅ 正确：使用构造器注入
@Bean
public ServiceA serviceA(ServiceB serviceB) {  // Spring自动处理依赖顺序
    return new ServiceA(serviceB);
}

@Bean
public ServiceB serviceB() {
    return new ServiceB();
}
```

---

## 🎯 **总结**

### **Spring Boot的创新价值**

#### **1. 开箱即用的条件注解**
- ✅ **@ConditionalOnProperty**: 基于配置属性
- ✅ **@ConditionalOnClass**: 基于类路径
- ✅ **@ConditionalOnBean**: 基于Bean存在
- ✅ **@ConditionalOnProfile**: 基于环境Profile
- ✅ **@ConditionalOnWebApplication**: 基于应用类型

#### **2. 自动配置的依赖管理**
- ✅ **@AutoConfigureOrder**: 配置类执行顺序
- ✅ **@AutoConfigureBefore/@AutoConfigureAfter**: 相对顺序控制
- ✅ **@DependsOn**: 显式Bean依赖关系

#### **3. 最佳实践模式**
- ✅ **功能开关**: 通过配置控制功能启用
- ✅ **环境适配**: 不同环境不同配置
- ✅ **自动配置**: 智能的默认配置
- ✅ **条件组合**: 复杂条件的优雅表达

### **核心优势**

1. **简化开发**: 从自定义Condition到开箱即用的注解
2. **提高可维护性**: 声明式的条件配置
3. **增强灵活性**: 多种条件组合方式
4. **优化性能**: 智能的条件评估顺序
5. **降低复杂度**: 自动处理依赖关系

Spring Boot通过这些扩展，让 `@DependsOn` 和 `@Conditional` 从底层的技术工具变成了**应用架构设计的重要组件**，极大地提升了开发效率和代码质量！🚀