# @DependsOn 和 @Conditional 实战演示

## 🎯 **核心概念快速理解**

### **@DependsOn - 控制初始化顺序**
```java
@Component
@DependsOn("configManager")  // 🔥 确保configManager先初始化
public class DatabaseService {
    // 现在可以安全使用configManager的配置了
}
```

### **@Conditional - 条件化注册**
```java
@Component
@Conditional(DatabaseEnabledCondition.class)  // 🔥 只有条件满足才注册
public class DatabaseService {
    // 只有在数据库功能启用时才会创建这个Bean
}
```

---

## 💻 **实战Demo代码**

### 📁 **Demo文件位置**
```
/data/workspace/spring-framework/spring-debug/demo/SimpleDependsOnDemo.java
```

### 🚀 **Demo核心代码解析**

#### **1. 配置管理器（基础服务）**
```java
@Component("configurationManager")
public static class ConfigurationManager {
    
    @PostConstruct
    public void init() {
        System.out.println("🔧 [1] ConfigurationManager 初始化 - 加载系统配置");
        // 模拟配置加载耗时
        Thread.sleep(100);
    }
    
    public String getConfig(String key) {
        return System.getProperty(key, "default");
    }
}
```

#### **2. 数据库服务（依赖配置管理器 + 条件注册）**
```java
@Component("databaseService")
@DependsOn("configurationManager")           // 🔥 依赖顺序控制
@Conditional(DatabaseEnabledCondition.class) // 🔥 条件化注册
public static class DatabaseService {
    
    @Autowired
    private ConfigurationManager configManager;
    
    @PostConstruct
    public void init() {
        System.out.println("🗄️  [2] DatabaseService 初始化 - 连接数据库");
        String dbUrl = configManager.getConfig("database.url");  // 安全使用配置
        System.out.println("    数据库URL: " + dbUrl);
    }
}
```

#### **3. 自定义条件实现**
```java
public static class DatabaseEnabledCondition implements Condition {
    
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Environment env = context.getEnvironment();
        String enabled = env.getProperty("feature.database.enabled");
        
        boolean result = "true".equals(enabled);
        System.out.println("🔍 条件评估 - DatabaseEnabledCondition: " + 
                         "feature.database.enabled=" + enabled + ", 结果=" + result);
        
        return result;
    }
}
```

---

## 🎬 **Demo运行效果**

### **运行命令**
```bash
cd /data/workspace/spring-framework/spring-debug/demo
java -cp ".:../../../spring-context/build/libs/*:../../../spring-beans/build/libs/*:../../../spring-core/build/libs/*" SimpleDependsOnDemo
```

### **预期输出**
```
=== @DependsOn 和 @Conditional 注解演示 ===

🔍 条件评估 - DatabaseEnabledCondition: feature.database.enabled=true, 结果=true
🔍 条件评估 - CacheEnabledCondition: feature.cache.enabled=false, 结果=false
🔍 条件评估 - DevelopmentEnvironmentCondition: app.environment=development, 结果=true

🔧 [1] ConfigurationManager 初始化 - 加载系统配置
🗄️  [2] DatabaseService 初始化 - 连接数据库
    数据库URL: default
🚀 [3] ApplicationService 初始化 - 启动应用服务
    配置管理器: 已注入
    数据库服务: 已注入
    缓存服务: 未注入
🛠️  [4] DevelopmentOnlyService 初始化 - 开发环境专用功能

=== 容器初始化完成，查看Bean注册情况 ===
✅ 配置管理器 (configurationManager) 已注册: ConfigurationManager
✅ 数据库服务 (databaseService) 已注册: DatabaseService
❌ 缓存服务 (cacheService) 未注册
✅ 应用服务 (applicationService) 已注册: ApplicationService
✅ 开发环境专用服务 (developmentOnlyService) 已注册: DevelopmentOnlyService
```

---

## 🔍 **关键观察点**

### **1. 初始化顺序控制**
```
[1] ConfigurationManager  ← 最先初始化（被依赖）
[2] DatabaseService       ← 依赖ConfigurationManager
[3] ApplicationService    ← 依赖ConfigurationManager和DatabaseService
[4] DevelopmentOnlyService ← 独立初始化
```

**关键**：即使 `ApplicationService` 通过 `@Autowired` 注入了 `ConfigurationManager`，但 `@DependsOn` 确保了更严格的初始化顺序。

### **2. 条件评估结果**
```
DatabaseEnabledCondition: true  → DatabaseService 被注册 ✅
CacheEnabledCondition: false    → CacheService 未注册 ❌
DevelopmentEnvironmentCondition: true → DevelopmentOnlyService 被注册 ✅
```

**关键**：条件评估在Bean注册阶段进行，不满足条件的Bean根本不会被创建。

---

## 🎯 **实际应用场景**

### **场景1：微服务启动顺序控制**
```java
@Component
@DependsOn({"configCenter", "serviceRegistry"})
public class BusinessService {
    // 确保配置中心和服务注册中心先启动
}
```

### **场景2：环境相关的功能开关**
```java
@Component
@Conditional(ProductionEnvironmentCondition.class)
public class ProductionMonitoringService {
    // 只在生产环境启用监控服务
}

@Component  
@Conditional(DevelopmentEnvironmentCondition.class)
public class DevelopmentToolsService {
    // 只在开发环境启用开发工具
}
```

### **场景3：功能特性开关**
```java
@Component
@ConditionalOnProperty(name = "feature.payment.enabled", havingValue = "true")
public class PaymentService {
    // 通过配置控制支付功能的启用
}
```

---

## 💡 **源码级别的理解**

### **@DependsOn 处理流程**
```java
// 1. 注册阶段：记录依赖关系
AnnotationAttributes dependsOn = attributesFor(metadata, DependsOn.class);
if (dependsOn != null) {
    abd.setDependsOn(dependsOn.getStringArray("value"));  // 设置依赖Bean名称
}

// 2. 创建阶段：先创建依赖Bean
String[] dependsOn = mbd.getDependsOn();
if (dependsOn != null) {
    for (String dep : dependsOn) {
        getBean(dep);  // 🔥 递归创建依赖Bean
    }
}
```

### **@Conditional 处理流程**
```java
// 1. 注册阶段：条件评估
AnnotatedGenericBeanDefinition abd = new AnnotatedGenericBeanDefinition(beanClass);
if (this.conditionEvaluator.shouldSkip(abd.getMetadata())) {
    return;  // 🔥 条件不满足，跳过注册
}

// 2. 条件评估核心逻辑
for (Condition condition : conditions) {
    if (!condition.matches(this.context, metadata)) {
        return true;  // 条件不满足，跳过注册
    }
}
```

---

## 🔧 **调试技巧**

### **1. 查看Bean注册情况**
```java
// 获取所有Bean定义
String[] beanNames = context.getBeanDefinitionNames();
for (String name : beanNames) {
    System.out.println("注册的Bean: " + name);
}
```

### **2. 查看依赖关系**
```java
// 获取Bean的依赖信息
BeanDefinition bd = context.getBeanDefinition("applicationService");
String[] dependsOn = bd.getDependsOn();
if (dependsOn != null) {
    System.out.println("依赖的Bean: " + Arrays.toString(dependsOn));
}
```

### **3. 条件评估日志**
```java
// 在Condition实现中添加详细日志
public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
    String property = context.getEnvironment().getProperty("feature.enabled");
    boolean result = "true".equals(property);
    
    System.out.println("条件评估: property=" + property + ", result=" + result);
    return result;
}
```

---

## ⚠️ **常见陷阱**

### **1. 循环依赖**
```java
// ❌ 错误：会导致循环依赖异常
@Component
@DependsOn("serviceB")
public class ServiceA { }

@Component
@DependsOn("serviceA") 
public class ServiceB { }
```

### **2. 条件逻辑错误**
```java
// ❌ 错误：条件逻辑过于复杂
public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
    // 复杂的条件判断，容易出错
    return complexConditionLogic();
}

// ✅ 正确：简单清晰的条件逻辑
public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
    return "true".equals(context.getEnvironment().getProperty("feature.enabled"));
}
```

### **3. 忘记处理可选依赖**
```java
// ✅ 正确：处理可选依赖
@Autowired(required = false)  // 🔥 关键：设置为可选
private CacheService cacheService;

if (cacheService != null) {
    cacheService.cache(data);
}
```

---

## 🎯 **总结**

### **@DependsOn 核心价值**
- ✅ **解决隐式依赖**：Spring无法自动检测的依赖关系
- ✅ **控制启动顺序**：确保关键组件按正确顺序初始化
- ✅ **处理静态依赖**：静态方法、静态变量的依赖关系

### **@Conditional 核心价值**
- ✅ **环境适配**：不同环境注册不同Bean
- ✅ **功能开关**：通过配置控制功能启用/禁用
- ✅ **资源优化**：避免不必要的Bean创建

### **最佳实践**
1. **优先使用构造器注入**替代@DependsOn
2. **保持条件逻辑简单**，避免复杂判断
3. **充分测试**不同条件下的行为
4. **合理使用可选依赖**处理条件Bean

这两个注解是Spring框架中控制Bean生命周期的强大工具，正确使用它们可以让应用更加灵活和健壮！🚀