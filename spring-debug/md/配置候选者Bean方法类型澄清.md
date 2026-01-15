# 配置候选者Bean方法类型澄清

## 🎯 问题澄清

你的观察非常敏锐！让我澄清一下关于"Bean方法"的概念。

## 📋 **实际上只有一种Bean方法类型**

根据源码分析，**配置候选者判断中的"Bean方法"实际上只有一种**：

### **唯一的Bean方法类型：@Bean注解的方法**

```java
// ConfigurationClassUtils.java (169-172行)
static boolean hasBeanMethods(AnnotationMetadata metadata) {
    try {
        return metadata.hasAnnotatedMethods(Bean.class.getName());  // 🔥 只检查@Bean注解
    }
    catch (Throwable ex) {
        // ...
    }
}
```

**源码证据**：
- `hasAnnotatedMethods(Bean.class.getName())` 明确只检查 `@Bean` 注解
- 没有检查其他类型的"工厂方法"

---

## 🔍 **容易混淆的概念**

### **@Bean方法 vs @Lookup方法**

| 方法类型 | 注解 | 作用 | 是否算配置候选者 |
|---------|------|------|----------------|
| **@Bean方法** | `@Bean` | 定义Bean | ✅ **是** |
| **@Lookup方法** | `@Lookup` | 方法注入 | ❌ **不是** |

### **@Lookup方法的作用**

```java
public abstract class CommandManager {
    
    @Lookup("myCommand")  // 🔍 这是方法注入，不是Bean定义
    protected abstract Command createCommand();
    
    public Object process(Object commandState) {
        Command command = createCommand();  // 每次调用都返回新实例
        command.setState(commandState);
        return command.execute();
    }
}
```

**@Lookup方法的特点**：
- 用于**方法注入**，不是Bean定义
- 在 `ClassPathScanningCandidateComponentProvider` 中有特殊处理
- 但**不影响配置候选者的判断**

---

## 📊 **配置候选者的正确判断标准**

### **修正后的三个条件**

| 条件 | 说明 | 示例 |
|------|------|------|
| **配置注解** | 标注了 `@Component`、`@ComponentScan`、`@Import`、`@ImportResource` | `@Configuration`、`@Service` |
| **@Bean方法** | 包含 `@Bean` 注解的方法（**只有这一种**） | 工厂Bean定义方法 |
| **非接口** | 必须是具体的类，不能是接口或注解 | 实际的配置类 |

---

## 💡 **实际代码示例**

### **✅ 配置候选者示例**

```java
// 示例1：通过配置注解成为候选者
@Component
public class UserService {
    // 没有@Bean方法，但有@Component注解 → 配置候选者
}

// 示例2：通过@Bean方法成为候选者
public class DatabaseConfig {  // 没有配置注解
    
    @Bean  // 🔥 有@Bean方法 → 配置候选者
    public DataSource dataSource() {
        return new HikariDataSource();
    }
}

// 示例3：两者都有
@Configuration
public class AppConfig {  // 有@Configuration注解
    
    @Bean  // 还有@Bean方法 → 配置候选者
    public UserService userService() {
        return new UserService();
    }
}
```

### **❌ 非配置候选者示例**

```java
// 示例1：普通类（没有配置注解，没有@Bean方法）
public class UtilityClass {
    public static String format(String input) {
        return input.toUpperCase();
    }
}

// 示例2：只有@Lookup方法（不算Bean方法）
public abstract class ServiceLocator {
    
    @Lookup  // 🔍 这不是@Bean方法，不算配置候选者
    protected abstract UserService getUserService();
}

// 示例3：接口（即使有@Bean方法也不算）
public interface ConfigInterface {
    
    @Bean  // 接口不能成为配置候选者
    default UserService userService() {
        return new UserService();
    }
}
```

---

## 🔄 **完整的判断流程**

```java
public static boolean isConfigurationCandidate(AnnotationMetadata metadata) {
    // 1️⃣ 首先排除接口
    if (metadata.isInterface()) {
        return false;  // ❌ 接口不能成为配置候选者
    }

    // 2️⃣ 检查配置注解
    for (String indicator : candidateIndicators) {  // @Component, @ComponentScan, @Import, @ImportResource
        if (metadata.isAnnotated(indicator)) {
            return true;  // ✅ 有配置注解 → 配置候选者
        }
    }

    // 3️⃣ 检查@Bean方法（只有这一种）
    return hasBeanMethods(metadata);  // ✅ 有@Bean方法 → 配置候选者
}

static boolean hasBeanMethods(AnnotationMetadata metadata) {
    return metadata.hasAnnotatedMethods(Bean.class.getName());  // 🔥 只检查@Bean注解
}
```

---

## 🎯 **总结**

### **回答你的问题**

> "Bean方法，我看后面有两种？一种是@Bean注解的方法，另外一种是工厂方法配置是吗？"

**答案：只有一种Bean方法类型**

- ✅ **@Bean注解的方法** - 这是唯一被 `isConfigurationCandidate()` 检查的Bean方法类型
- ❌ **@Lookup方法** - 这不是Bean定义方法，是方法注入，不影响配置候选者判断
- ❌ **其他工厂方法** - 如果没有@Bean注解，不算Bean方法

### **核心要点**

1. **配置候选者判断**只关心 `@Bean` 注解的方法
2. **@Lookup方法**是完全不同的概念，用于方法注入
3. **源码是最准确的**：`hasAnnotatedMethods(Bean.class.getName())` 明确只检查@Bean

感谢你的细致观察！这确实是一个容易混淆的概念点。🎯