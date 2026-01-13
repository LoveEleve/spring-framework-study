# Spring 源码调试项目

## 📦 项目说明

本项目提供两个版本的Spring源码调试Demo：

### 1️⃣ **基础版**（开箱即用）✅
- **主类**：`SpringSourceDebugApp.java`
- **功能**：IoC容器、Bean生命周期、依赖注入、AOP
- **依赖**：最小化依赖，无需额外配置

### 2️⃣ **进阶版**（需添加依赖）
- **主类**：`SpringTransactionDebugApp.java`
- **功能**：基础版 + 事务管理
- **依赖**：需要在`spring-debug.gradle`中添加事务相关依赖

---

## 🚀 快速开始

### 运行基础版（推荐新手）

1. 直接运行 `SpringSourceDebugApp.main()`
2. 观察控制台输出，理解Spring启动流程

**核心断点**：
```java
// 1. 容器创建入口
AnnotationConfigApplicationContext context = 
    new AnnotationConfigApplicationContext(AppConfig.class);
    
// 进入：AbstractApplicationContext.refresh() - 第567行

// 2. Bean获取
UserService userService = context.getBean(UserService.class);

// 进入：AbstractBeanFactory.doGetBean() - 第327行
```

---

## 📋 当前依赖配置

`spring-debug.gradle` 当前包含：

```gradle
dependencies {
    // Spring 核心模块
    implementation(project(":spring-context"))
    implementation(project(":spring-aop"))
    implementation(project(":spring-beans"))
    implementation(project(":spring-core"))
    implementation(project(":spring-aspects"))
    
    // 事务和JDBC支持（进阶版需要）
    implementation(project(":spring-tx"))
    implementation(project(":spring-jdbc"))
    
    // AspectJ支持
    implementation "org.aspectj:aspectjweaver:1.9.7"
    
    // JSR注解支持
    implementation "javax.annotation:javax.annotation-api:1.3.2"
    
    // H2数据库（进阶版需要）
    implementation "com.h2database:h2:1.4.200"
}
```

---

## 🔧 构建项目

在Spring Framework根目录执行：

```bash
# 清理并构建
./gradlew clean :spring-debug:build -x test

# 或者构建整个项目（首次需要）
./gradlew build -x test
```

---

## 🎯 学习路径

### Week 1: IoC容器
- 运行 `SpringSourceDebugApp`
- 关注 `refresh()` 方法的12个步骤
- 理解Bean的创建流程

### Week 2: Bean生命周期
- 观察 `LifecycleBean` 的完整生命周期
- 理解 Aware 接口、BeanPostProcessor

### Week 3: 依赖注入
- 跟踪 `UserService` 的构造器注入
- 理解依赖解析和注入流程

### Week 4: AOP原理
- 观察 `LoggingAspect` 的拦截过程
- 理解代理创建和拦截器链

### Week 5: 事务管理（进阶）
- 运行 `SpringTransactionDebugApp`
- 理解 `@Transactional` 的实现原理

---

## 📍 核心断点清单

| 功能 | 断点位置 | 关键类 |
|-----|---------|--------|
| 容器启动 | `AbstractApplicationContext.refresh()` | 567行 |
| Bean创建 | `AbstractBeanFactory.doGetBean()` | 327行 |
| 依赖注入 | `AutowiredAnnotationBeanPostProcessor.postProcessProperties()` | 413行 |
| AOP代理 | `AbstractAutoProxyCreator.postProcessAfterInitialization()` | 293行 |
| 方法拦截 | `JdkDynamicAopProxy.invoke()` | 214行 |
| 事务开启 | `TransactionInterceptor.invoke()` | 119行 |

---

## 📖 详细文档

请查看：`DEBUG_GUIDE.md` - 完整的源码调试指南

---

## ⚠️ 常见问题

### Q1: 编译失败
**A**: 执行 `./gradlew clean :spring-debug:build`

### Q2: 找不到某些类
**A**: 确保已经构建了Spring Framework项目：`./gradlew build`

### Q3: 事务相关代码报错
**A**: 运行基础版 `SpringSourceDebugApp`，事务功能在进阶版中

### Q4: 如何查看AOP代理对象
**A**: 在获取Bean后，查看对象类型：
```java
UserService userService = context.getBean(UserService.class);
System.out.println(userService.getClass()); // 查看是否是代理类
```

---

## 💡 调试技巧

1. **使用条件断点**：只在特定Bean时停止
   ```java
   beanName.equals("userService")
   ```

2. **查看调用栈**：理解方法调用链路

3. **计算表达式**：在调试时动态执行代码

4. **Spring源码包结构**：
   - `spring-beans`: Bean定义和工厂
   - `spring-context`: 应用上下文
   - `spring-aop`: AOP实现
   - `spring-tx`: 事务管理

---

祝您源码学习顺利！🎉
