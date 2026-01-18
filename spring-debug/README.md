# Spring Framework Debug 示例

本目录包含了Spring Framework核心机制的详细演示和文档。

## 目录结构

```
spring-debug/
├── md/                          # 文档目录
│   └── Import接口详解.md         # Import接口详细文档
└── demo/                        # 代码演示目录
    ├── ImportSelectorDemo.java                    # ImportSelector演示
    ├── DeferredImportSelectorDemo.java           # DeferredImportSelector演示
    ├── ImportBeanDefinitionRegistrarDemo.java    # ImportBeanDefinitionRegistrar演示
    ├── ComprehensiveImportDemo.java              # 综合演示
    ├── SpringBootStyleAutoConfigDemo.java       # Spring Boot风格自动配置演示
    └── RunAllImportDemos.java                    # 运行所有演示的主类
```

## Import接口演示

### 1. ImportSelector演示
**文件**: `ImportSelectorDemo.java`

演示ImportSelector接口的基本用法：
- 根据系统属性动态选择配置类
- 条件导入不同的数据库配置
- 展示立即执行的特点

**运行方式**:
```bash
java org.springframework.debug.demo.ImportSelectorDemo
```

### 2. DeferredImportSelector演示
**文件**: `DeferredImportSelectorDemo.java`

演示DeferredImportSelector接口的特性：
- 延迟执行机制
- 分组和排序功能
- 自动配置场景应用

**运行方式**:
```bash
java org.springframework.debug.demo.DeferredImportSelectorDemo
```

### 3. ImportBeanDefinitionRegistrar演示
**文件**: `ImportBeanDefinitionRegistrarDemo.java`

演示ImportBeanDefinitionRegistrar接口的能力：
- 直接注册BeanDefinition
- 动态创建Bean配置
- 自定义注解驱动的Bean注册

**运行方式**:
```bash
java org.springframework.debug.demo.ImportBeanDefinitionRegistrarDemo
```

### 4. 综合演示
**文件**: `ComprehensiveImportDemo.java`

展示三种Import接口在同一应用中的协作：
- 执行顺序演示
- 接口间的配合使用
- 实际应用场景模拟

**运行方式**:
```bash
java org.springframework.debug.demo.ComprehensiveImportDemo
```

### 5. Spring Boot风格自动配置演示
**文件**: `SpringBootStyleAutoConfigDemo.java`

模拟Spring Boot的自动配置机制：
- 条件注解的使用
- 自动配置类的加载
- 配置属性驱动的Bean创建

**运行方式**:
```bash
java org.springframework.debug.demo.SpringBootStyleAutoConfigDemo
```

### 6. 一键运行所有演示
**文件**: `RunAllImportDemos.java`

按顺序运行所有演示，并提供总结：

**运行方式**:
```bash
java org.springframework.debug.demo.RunAllImportDemos
```

## 核心概念

### Import接口对比

| 接口 | 执行时机 | 返回值 | 主要用途 | 复杂度 |
|------|----------|--------|----------|--------|
| ImportSelector | 立即 | String[] | 动态选择配置类 | 简单 |
| DeferredImportSelector | 延迟 | String[] | 自动配置 | 中等 |
| ImportBeanDefinitionRegistrar | 延迟 | void | 直接注册Bean | 复杂 |

### 执行顺序

```
1. ImportSelector (立即执行)
   ↓
2. 常规配置类处理
   ↓  
3. DeferredImportSelector (延迟执行)
   ↓
4. ImportBeanDefinitionRegistrar (Bean注册阶段)
```

### 选择建议

- **简单条件导入** → 使用 `ImportSelector`
- **需要全局信息** → 使用 `DeferredImportSelector`  
- **复杂Bean注册** → 使用 `ImportBeanDefinitionRegistrar`

## 系统属性配置

各个演示支持通过系统属性进行配置：

### ImportSelector演示
```properties
database.type=mysql|postgresql|h2
cache.enabled=true|false
```

### DeferredImportSelector演示
```properties
auto.config.enabled=true|false
feature.web=true|false
feature.security=true|false
feature.data=true|false
```

### Spring Boot风格演示
```properties
app.datasource.enabled=true|false
app.datasource.type=mysql|postgresql
app.cache.enabled=true|false
app.web.enabled=true|false
app.security.enabled=true|false
```

## 调试技巧

1. **启用调试日志**:
   ```properties
   logging.level.org.springframework.context.annotation=DEBUG
   ```

2. **观察执行顺序**: 每个演示都会输出详细的执行日志

3. **修改系统属性**: 通过修改System.setProperty()调用来测试不同场景

4. **断点调试**: 在关键方法设置断点观察执行流程

## 扩展练习

1. **自定义条件注解**: 实现类似@ConditionalOnProperty的自定义条件
2. **配置属性绑定**: 结合@ConfigurationProperties使用
3. **多环境配置**: 实现基于Profile的配置选择
4. **插件化架构**: 使用Import接口实现插件系统

## 参考文档

- [Import接口详解](md/Import接口详解.md) - 详细的接口文档和最佳实践
- Spring Framework官方文档
- Spring Boot自动配置原理

---

**注意**: 这些演示基于Spring Framework源码环境，展示了Spring容器启动过程中Import接口的实际工作机制。