# @DependsOn 和 @Conditional 注解详细分析

## 📋 **注解概述**

`@DependsOn` 和 `@Conditional` 是Spring框架中两个非常重要的注解，它们分别用于**控制Bean初始化顺序**和**条件化Bean注册**。

---

## 🔗 **@DependsOn 注解详解**

### 🎯 **基本概念**

`@DependsOn` 注解用于**显式指定Bean的依赖关系**，确保被依赖的Bean在当前Bean之前初始化。

```java
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DependsOn {
    String[] value() default {};
}
```

### 💡 **为什么需要 @DependsOn？**

#### **问题场景**
```java
@Component
public class DatabaseService {
    @PostConstruct
    public void init() {
        // 需要使用配置管理器的配置
        String dbUrl = ConfigurationManager.getConfig("db.url");  // ❌ 可能为null！
    }
}

@Component  
public class ConfigurationManager {
    @PostConstruct
    public void loadConfig() {
        // 加载配置文件
    }
}
```

**问题**：Spring无法自动检测到这种隐式依赖，可能导致 `DatabaseService` 在 `ConfigurationManager` 之前初始化。

#### **解决方案**
```java
@Component
@DependsOn("configurationManager")  // ✅ 显式指定依赖
public class DatabaseService {
    @PostConstruct
    public void init() {
        // 现在可以安全使用配置了
        String dbUrl = ConfigurationManager.getConfig("db.url");
    }
}
```

---

### 🔍 **@DependsOn 使用场景**

#### **1. 静态依赖场景**
```java
@Component
public class StaticResourceManager {
    private static Map<String, String> resources = new HashMap<>();
    
    @PostConstruct
    public void loadResources() {
        resources.put("config", "loaded");
        System.out.println("静态资源加载完成");
    }
    
    public static String getResource(String key) {
        return resources.get(key);
    }
}

@Component
@DependsOn("staticResourceManager")  // 🔥 确保静态资源先加载
public class BusinessService {
    @PostConstruct
    public void init() {
        String config = StaticResourceManager.getResource("config");
        System.out.println("业务服务启动，配置: " + config);
    }
}
```

#### **2. 多重依赖场景**
```java
@Component
@DependsOn({"configManager", "databaseService", "cacheService"})  // 🔥 依赖多个Bean
public class ApplicationService {
    @PostConstruct
    public void init() {
        System.out.println("应用服务启动 - 所有依赖都已就绪");
    }
}
```

#### **3. 配置类级别依赖**
```java
@Configuration
@DependsOn("systemInitializer")  // 🔥 整个配置类都依赖某个Bean
public class DatabaseConfig {
    
    @Bean
    public DataSource dataSource() {
        // systemInitializer 已经初始化完成
        return new HikariDataSource();
    }
}
```

#### **4. @Bean 方法级别依赖**
```java
@Configuration
public class AppConfig {
    
    @Bean
    public ConfigLoader configLoader() {
        return new ConfigLoader();
    }
    
    @Bean
    @DependsOn("configLoader")  // 🔥 Bean方法级别的依赖
    public DatabaseService databaseService() {
        return new DatabaseService();
    }
}
```

---

### ⚡ **@DependsOn 工作原理**

#### **源码分析**
```java
// AnnotationConfigUtils.java (287-290行)
AnnotationAttributes dependsOn = attributesFor(metadata, DependsOn.class);
if (dependsOn != null) {
    abd.setDependsOn(dependsOn.getStringArray("value"));  // 设置依赖Bean名称
}
```

#### **初始化顺序控制**
```java
// AbstractBeanFactory.java - doGetBean方法中
protected <T> T doGetBean(String name, ...) {
    // 1. 检查依赖关系
    String[] dependsOn = mbd.getDependsOn();
    if (dependsOn != null) {
        for (String dep : dependsOn) {
            // 2. 先初始化依赖的Bean
            getBean(dep);  // 🔥 递归初始化依赖Bean
        }
    }
    
    // 3. 初始化当前Bean
    if (mbd.isSingleton()) {
        sharedInstance = getSingleton(beanName, () -> {
            return createBean(beanName, mbd, args);
        });
    }
}
```

---

## 🎯 **@Conditional 注解详解**

### 🎯 **基本概念**

`@Conditional` 注解用于**条件化Bean注册**，只有当指定条件满足时，Bean才会被注册到容器中。

```java
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Conditional {
    Class<? extends Condition>[] value();
}
```

### 💡 **核心接口：Condition**

```java
@FunctionalInterface
public interface Condition {
    boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata);
}
```

---

### 🔍 **@Conditional 使用场景**

#### **1. 基于环境的条件注册**
```java
// 自定义条件实现
public class ProductionEnvironmentCondition implements Condition {
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Environment env = context.getEnvironment();
        String[] activeProfiles = env.getActiveProfiles();
        return Arrays.asList(activeProfiles).contains("production");
    }
}

@Component
@Conditional(ProductionEnvironmentCondition.class)  // 🔥 只在生产环境注册
public class ProductionOnlyService {
    @PostConstruct
    public void init() {
        System.out.println("生产环境专用服务启动");
    }
}
```

#### **2. 基于类存在的条件**
```java
public class MySQLDriverExistsCondition implements Condition {
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        try {
            context.getClassLoader().loadClass("com.mysql.cj.jdbc.Driver");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}

@Configuration
@Conditional(MySQLDriverExistsCondition.class)  // 🔥 MySQL驱动存在时才注册
public class MySQLConfig {
    
    @Bean
    public DataSource mysqlDataSource() {
        return new HikariDataSource();
    }
}
```

#### **3. 基于Bean存在的条件**
```java
public class DataSourceMissingCondition implements Condition {
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        BeanFactory beanFactory = context.getBeanFactory();
        try {
            beanFactory.getBean(DataSource.class);
            return false;  // DataSource存在，条件不满足
        } catch (NoSuchBeanDefinitionException e) {
            return true;   // DataSource不存在，条件满足
        }
    }
}

@Bean
@Conditional(DataSourceMissingCondition.class)  // 🔥 没有DataSource时才注册默认的
public DataSource defaultDataSource() {
    return new EmbeddedDatabaseBuilder()
        .setType(EmbeddedDatabaseType.H2)
        .build();
}
```

---

### 🚀 **Spring Boot 提供的条件注解**

Spring Boot 基于 `@Conditional` 提供了许多便捷的条件注解：

#### **1. @ConditionalOnProperty**
```java
@Component
@ConditionalOnProperty(
    name = "feature.cache.enabled",     // 属性名
    havingValue = "true",               // 期望值
    matchIfMissing = false              // 属性不存在时是否匹配
)
public class CacheService {
    // 只有当 feature.cache.enabled=true 时才注册
}
```

#### **2. @ConditionalOnClass**
```java
@Configuration
@ConditionalOnClass(RedisTemplate.class)  // Redis类存在时才注册
public class RedisConfig {
    
    @Bean
    public RedisTemplate<String, Object> redisTemplate() {
        return new RedisTemplate<>();
    }
}
```

#### **3. @ConditionalOnMissingBean**
```java
@Bean
@ConditionalOnMissingBean(name = "customUserService")  // 没有customUserService时才注册
public UserService defaultUserService() {
    return new DefaultUserService();
}
```

#### **4. @ConditionalOnProfile**
```java
@Component
@ConditionalOnProfile("dev")  // 只在dev profile下注册
public class DevToolsService {
    // 开发环境专用功能
}
```

#### **5. @ConditionalOnWebApplication**
```java
@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class WebMvcConfigurer {
    // 只在Servlet Web应用中注册
}
```

---

### ⚡ **@Conditional 工作原理**

#### **条件评估时机**
```java
// AnnotatedBeanDefinitionReader.doRegisterBean() (255-257行)
AnnotatedGenericBeanDefinition abd = new AnnotatedGenericBeanDefinition(beanClass);
if (this.conditionEvaluator.shouldSkip(abd.getMetadata())) {
    return;  // 🔥 条件不满足，跳过注册
}
```

#### **ConditionEvaluator 核心逻辑**
```java
public boolean shouldSkip(@Nullable AnnotatedTypeMetadata metadata, @Nullable ConfigurationPhase phase) {
    if (metadata == null || !metadata.isAnnotated(Conditional.class.getName())) {
        return false;  // 没有@Conditional注解，不跳过
    }

    if (phase == null) {
        // 递归检查外部类的条件
        if (metadata instanceof AnnotationMetadata &&
                ConfigurationClassUtils.isConfigurationCandidate((AnnotationMetadata) metadata)) {
            return shouldSkip(metadata, ConfigurationPhase.PARSE_CONFIGURATION);
        }
        return shouldSkip(metadata, ConfigurationPhase.REGISTER_BEAN);
    }

    List<Condition> conditions = new ArrayList<>();
    // 获取所有@Conditional注解中的Condition类
    for (String[] conditionClasses : getConditionClasses(metadata)) {
        for (String conditionClass : conditionClasses) {
            Condition condition = getCondition(conditionClass, this.context.getClassLoader());
            conditions.add(condition);
        }
    }

    AnnotationAwareOrderComparator.sort(conditions);

    for (Condition condition : conditions) {
        ConfigurationPhase requiredPhase = null;
        if (condition instanceof ConfigurationCondition) {
            requiredPhase = ((ConfigurationCondition) condition).getConfigurationPhase();
        }
        
        // 🔥 核心：调用condition.matches()方法
        if ((requiredPhase == null || requiredPhase == phase) && 
            !condition.matches(this.context, metadata)) {
            return true;  // 条件不满足，跳过注册
        }
    }

    return false;  // 所有条件都满足，不跳过
}
```

---

## 🎯 **实际Demo演示**

### 📁 **Demo文件位置**
```
/data/workspace/spring-framework/spring-debug/demo/DependsOnAndConditionalDemo.java
```

### 🚀 **Demo运行效果**

#### **1. 设置系统属性**
```java
System.setProperty("feature.database.enabled", "true");   // 启用数据库
System.setProperty("feature.cache.enabled", "false");     // 禁用缓存
System.setProperty("app.environment", "development");     // 开发环境
```

#### **2. 预期初始化顺序**
```
🔧 [1] ConfigurationManager 初始化 - 加载系统配置
🗄️  [2] DatabaseService 初始化 - 连接数据库
🚀 [3] ApplicationService 初始化 - 启动应用服务
🛠️  [4] DevelopmentOnlyService 初始化 - 开发环境专用功能
```

#### **3. Bean注册结果**
```
✅ 配置管理器 (configurationManager) 已注册
✅ 数据库服务 (databaseService) 已注册
❌ 缓存服务 (cacheService) 未注册          // 条件不满足
✅ 应用服务 (applicationService) 已注册
✅ 开发环境专用服务 (developmentOnlyService) 已注册
```

---

## 📊 **两个注解的对比**

| 特性 | @DependsOn | @Conditional |
|------|------------|-------------|
| **主要作用** | 控制初始化顺序 | 控制是否注册 |
| **生效时机** | Bean创建时 | Bean注册时 |
| **影响范围** | 已注册的Bean | 决定Bean是否注册 |
| **使用场景** | 隐式依赖、静态依赖 | 环境适配、功能开关 |
| **性能影响** | 影响启动顺序 | 影响容器大小 |

---

## 🔧 **最佳实践**

### **@DependsOn 最佳实践**

#### **1. 避免循环依赖**
```java
// ❌ 错误：循环依赖
@Component
@DependsOn("serviceB")
public class ServiceA { }

@Component  
@DependsOn("serviceA")
public class ServiceB { }
```

#### **2. 优先使用构造器注入**
```java
// ✅ 推荐：使用构造器注入替代@DependsOn
@Component
public class ServiceA {
    private final ServiceB serviceB;
    
    public ServiceA(ServiceB serviceB) {  // Spring会自动处理依赖顺序
        this.serviceB = serviceB;
    }
}
```

#### **3. 明确依赖关系**
```java
// ✅ 好的实践：明确注释依赖原因
@Component
@DependsOn("configurationLoader")  // 需要配置加载完成后才能初始化
public class DatabaseConnectionPool {
    // ...
}
```

### **@Conditional 最佳实践**

#### **1. 条件逻辑简单化**
```java
// ✅ 推荐：简单清晰的条件逻辑
public class DatabaseEnabledCondition implements Condition {
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return "true".equals(context.getEnvironment().getProperty("database.enabled"));
    }
}
```

#### **2. 使用Spring Boot提供的条件注解**
```java
// ✅ 推荐：使用现成的条件注解
@ConditionalOnProperty(name = "database.enabled", havingValue = "true")
public class DatabaseService { }

// ❌ 不推荐：重复造轮子
@Conditional(DatabaseEnabledCondition.class)
public class DatabaseService { }
```

#### **3. 条件注解组合使用**
```java
@Component
@ConditionalOnClass(RedisTemplate.class)           // Redis类存在
@ConditionalOnProperty(name = "redis.enabled")     // 配置启用
@ConditionalOnMissingBean(CacheManager.class)      // 没有其他缓存管理器
public class RedisCacheManager {
    // 多个条件同时满足才注册
}
```

---

## ⚠️ **注意事项**

### **@DependsOn 注意事项**

1. **性能影响**：过多的依赖关系会影响启动性能
2. **调试困难**：复杂的依赖链难以调试
3. **隐式依赖**：应该优先考虑显式的构造器注入

### **@Conditional 注意事项**

1. **条件评估开销**：复杂的条件逻辑会影响启动性能
2. **调试困难**：Bean未注册时需要检查条件逻辑
3. **测试复杂性**：需要模拟不同的条件环境进行测试

---

## 🎯 **总结**

### **@DependsOn 核心价值**
- ✅ **解决隐式依赖**：处理Spring无法自动检测的依赖关系
- ✅ **控制初始化顺序**：确保关键组件按正确顺序启动
- ✅ **静态依赖支持**：处理静态方法、静态变量的依赖

### **@Conditional 核心价值**
- ✅ **环境适配**：根据不同环境注册不同的Bean
- ✅ **功能开关**：通过配置控制功能的启用/禁用
- ✅ **自动配置**：Spring Boot自动配置的核心机制

### **使用建议**
1. **优先使用构造器注入**替代@DependsOn
2. **优先使用Spring Boot条件注解**替代自定义@Conditional
3. **保持条件逻辑简单**，避免复杂的条件判断
4. **充分测试**不同条件下的Bean注册情况

这两个注解是Spring框架中非常强大的工具，正确使用它们可以让应用更加灵活和健壮！🚀