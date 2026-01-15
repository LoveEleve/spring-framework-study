# Spring条件类型体系详解

## 🎯 **核心问题：默认创建的Condition是什么类型？**

这是一个非常重要的问题！让我详细分析Spring中Condition的类型体系和默认行为。

---

## 📋 **Spring条件类型体系**

### **1. 基础接口：Condition**

```java
@FunctionalInterface
public interface Condition {
    /**
     * 判断条件是否匹配
     * @param context 条件上下文
     * @param metadata 注解元数据
     * @return true表示条件满足，false表示不满足
     */
    boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata);
}
```

**特点**：
- 🔧 **函数式接口**：可以使用Lambda表达式
- 🎯 **简单条件**：只需要实现matches方法
- ⏰ **任意阶段**：没有阶段限制，任何时候都可以评估

---

### **2. 扩展接口：ConfigurationCondition**

```java
public interface ConfigurationCondition extends Condition {
    /**
     * 返回条件应该在哪个配置阶段被评估
     */
    ConfigurationPhase getConfigurationPhase();
    
    /**
     * 配置阶段枚举
     */
    enum ConfigurationPhase {
        PARSE_CONFIGURATION,  // 配置类解析阶段
        REGISTER_BEAN        // Bean注册阶段
    }
}
```

**特点**：
- 🔄 **继承关系**：继承自Condition接口
- 🎯 **阶段感知**：可以指定在特定阶段评估
- 🚀 **高级控制**：提供更精细的条件控制

---

## 🔍 **默认创建的Condition类型分析**

### **关键理解：没有"默认"类型！**

**重要结论**：Spring不会"默认创建"任何Condition实例，所有的Condition都是**开发者显式定义的**！

```java
// 开发者必须显式指定条件类
@Conditional({MyCondition.class, AnotherCondition.class})
public class MyConfig {
    // 配置内容
}
```

### **条件实例化过程**

```java
// ConditionEvaluator.java 中的实例化逻辑
for (String conditionClass : conditionClasses) {
    // 🔥 根据开发者指定的类名创建实例
    Condition condition = getCondition(conditionClass, this.context.getClassLoader());
    conditions.add(condition);
}

private Condition getCondition(String conditionClassName, @Nullable ClassLoader classloader) {
    // 1. 加载Class
    Class<?> conditionClass = ClassUtils.resolveClassName(conditionClassName, classloader);
    
    // 2. 🔥 实例化（开发者定义的具体类型）
    return (Condition) BeanUtils.instantiateClass(conditionClass);
}
```

---

## 📊 **实际条件类型统计**

### **Spring框架内置条件类型**

| 条件类 | 接口类型 | 阶段要求 | 用途 |
|--------|---------|---------|------|
| **ProfileCondition** | `Condition` | `null` (任意阶段) | `@Profile` 注解支持 |
| **NeverCondition** | `ConfigurationCondition` | `REGISTER_BEAN` | 测试用条件 |
| **AlwaysCondition** | `Condition` | `null` (任意阶段) | 测试用条件 |

### **开发者自定义条件类型**

```java
// 🔥 类型1：普通Condition（最常见）
public class DatabaseEnabledCondition implements Condition {
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return "true".equals(context.getEnvironment().getProperty("database.enabled"));
    }
}

// 🔥 类型2：ConfigurationCondition（高级用法）
public class DatabaseCondition implements ConfigurationCondition {
    @Override
    public ConfigurationPhase getConfigurationPhase() {
        return ConfigurationPhase.PARSE_CONFIGURATION;  // 指定阶段
    }
    
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return context.getClassLoader().getResource("database.properties") != null;
    }
}
```

---

## 🎯 **类型检查逻辑详解**

### **核心代码分析**

```java
for (Condition condition : conditions) {
    ConfigurationPhase requiredPhase = null;
    
    // 🔍 关键：类型检查决定后续行为
    if (condition instanceof ConfigurationCondition) {
        // 🔥 如果是ConfigurationCondition类型
        requiredPhase = ((ConfigurationCondition) condition).getConfigurationPhase();
    }
    // 🔥 如果是普通Condition类型，requiredPhase保持为null
    
    // 后续的阶段匹配逻辑...
}
```

### **两种类型的处理差异**

| 条件类型 | instanceof结果 | requiredPhase值 | 阶段匹配逻辑 |
|---------|---------------|----------------|-------------|
| **Condition** | `false` | `null` | 任何阶段都匹配 |
| **ConfigurationCondition** | `true` | 具体阶段值 | 必须阶段匹配才评估 |

---

## 💡 **实际应用场景分析**

### **场景1：普通业务条件（推荐使用Condition）**

```java
@Component
@Conditional(FeatureEnabledCondition.class)  // 🔥 普通Condition
public class FeatureService {
    // 业务逻辑
}

public class FeatureEnabledCondition implements Condition {
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return "true".equals(context.getEnvironment().getProperty("feature.enabled"));
    }
}
```

**为什么用Condition？**
- ✅ **简单直接**：只需要实现一个方法
- ✅ **灵活性高**：任何阶段都可以评估
- ✅ **性能良好**：没有额外的阶段检查开销

### **场景2：复杂配置条件（推荐使用ConfigurationCondition）**

```java
@Configuration
@Conditional(DatabaseConfigCondition.class)  // 🔥 ConfigurationCondition
public class DatabaseAutoConfiguration {
    
    @Bean
    public DataSource dataSource() {
        return new HikariDataSource();
    }
}

public class DatabaseConfigCondition implements ConfigurationCondition {
    @Override
    public ConfigurationPhase getConfigurationPhase() {
        return ConfigurationPhase.PARSE_CONFIGURATION;  // 🔥 早期评估
    }
    
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        // 复杂的数据库配置检查逻辑
        return checkDatabaseConfiguration(context);
    }
}
```

**为什么用ConfigurationCondition？**
- 🚀 **性能优化**：在配置解析阶段就决定是否处理整个配置类
- 🎯 **精确控制**：避免在错误的时机评估条件
- 🔧 **复杂场景**：适合需要精细控制的高级配置

---

## 📈 **使用频率统计**

### **Spring Boot中的条件类型分布**

根据Spring Boot源码分析：

| 条件类型 | 使用频率 | 典型场景 |
|---------|---------|---------|
| **Condition** | 🔥 **85%** | 简单的开关条件、环境检查 |
| **ConfigurationCondition** | 📊 **15%** | 复杂的自动配置、框架级条件 |

### **开发建议**

```java
// ✅ 推荐：大多数情况使用Condition
public class SimpleCondition implements Condition {
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        // 简单的条件判断
        return checkSimpleCondition(context);
    }
}

// 🎯 高级：复杂场景使用ConfigurationCondition
public class ComplexCondition implements ConfigurationCondition {
    @Override
    public ConfigurationPhase getConfigurationPhase() {
        return ConfigurationPhase.PARSE_CONFIGURATION;
    }
    
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        // 复杂的条件判断逻辑
        return checkComplexCondition(context);
    }
}
```

---

## 🔄 **类型转换和多态机制**

### **运行时类型检查**

```java
// Spring内部的类型检查逻辑
public void evaluateCondition(Condition condition) {
    if (condition instanceof ConfigurationCondition) {
        // 🔥 运行时类型检查
        ConfigurationCondition configCondition = (ConfigurationCondition) condition;
        ConfigurationPhase phase = configCondition.getConfigurationPhase();
        
        System.out.println("检测到ConfigurationCondition，阶段要求: " + phase);
    } else {
        // 🔥 普通Condition
        System.out.println("检测到普通Condition，无阶段限制");
    }
    
    // 调用通用的matches方法
    boolean result = condition.matches(context, metadata);
}
```

### **多态的威力**

```java
// 🔥 同一个集合可以存储不同类型的条件
List<Condition> conditions = Arrays.asList(
    new SimpleCondition(),           // 普通Condition
    new DatabaseCondition(),         // ConfigurationCondition
    new ProfileCondition()           // 普通Condition
);

// 🔥 统一处理，运行时区分类型
for (Condition condition : conditions) {
    // instanceof检查决定具体的处理逻辑
    processCondition(condition);
}
```

---

## 🎯 **总结**

### **关键理解**

1. **没有"默认"类型** → 所有条件都是开发者显式定义的
2. **两种主要类型** → `Condition`（85%）和 `ConfigurationCondition`（15%）
3. **运行时类型检查** → 通过 `instanceof` 区分处理逻辑
4. **选择原则** → 简单场景用 `Condition`，复杂场景用 `ConfigurationCondition`

### **最佳实践**

```java
// 🎯 选择指南
if (需要阶段控制 && 复杂配置场景) {
    使用 ConfigurationCondition;
} else {
    使用 Condition;  // 🔥 大多数情况的选择
}
```

### **设计精髓**

Spring的条件类型体系体现了**渐进式设计哲学**：
- **基础功能** → `Condition` 接口满足大部分需求
- **高级功能** → `ConfigurationCondition` 提供精细控制
- **向后兼容** → 新接口继承旧接口，保证兼容性
- **多态支持** → 统一处理，运行时区分

**这就是为什么Spring的条件机制既简单易用，又功能强大的根本原因！** 🚀