# Spring 源码调试指南

## 🎯 调试目标

通过断点调试深入理解 Spring 框架核心流程：
1. **IoC 容器启动流程**
2. **Bean 生命周期**
3. **依赖注入原理**
4. **AOP 代理创建与执行**
5. **事务管理机制**

---

## 📍 核心断点位置

### 1️⃣ 容器启动流程（refresh 方法）

**入口**：`AbstractApplicationContext.refresh()` - 第 567 行

这是 Spring 容器启动的灵魂方法，包含 12 个核心步骤：

```java
public void refresh() throws BeansException, IllegalStateException {
    synchronized (this.startupShutdownMonitor) {
        // 1. 准备刷新上下文环境
        prepareRefresh();

        // 2. 获取并刷新 BeanFactory
        ConfigurableListableBeanFactory beanFactory = obtainFreshBeanFactory();

        // 3. 对 BeanFactory 进行功能填充
        prepareBeanFactory(beanFactory);

        try {
            // 4. 子类覆盖方法做额外的处理
            postProcessBeanFactory(beanFactory);

            // 5. 【重要】激活各种 BeanFactory 处理器
            // 处理 @Configuration、@ComponentScan 等注解
            invokeBeanFactoryPostProcessors(beanFactory);

            // 6. 【重要】注册 Bean 后置处理器
            // 如 AOP、事务等后置处理器
            registerBeanPostProcessors(beanFactory);

            // 7. 初始化消息源（国际化）
            initMessageSource();

            // 8. 初始化事件派发器
            initApplicationEventMulticaster();

            // 9. 子类扩展点：初始化特殊 Bean
            onRefresh();

            // 10. 注册监听器
            registerListeners();

            // 11. 【重点】实例化所有单例 Bean
            finishBeanFactoryInitialization(beanFactory);

            // 12. 完成刷新，发布事件
            finishRefresh();
        }
        catch (BeansException ex) {
            destroyBeans();
            cancelRefresh(ex);
            throw ex;
        }
    }
}
```

**关键断点**：
- `AbstractApplicationContext.refresh()` - 第 567 行
- `AbstractApplicationContext.invokeBeanFactoryPostProcessors()` - 第 731 行
- `AbstractApplicationContext.finishBeanFactoryInitialization()` - 第 863 行

---

### 2️⃣ Bean 创建流程

**入口**：`AbstractBeanFactory.doGetBean()` - 第 327 行

Bean 获取的完整流程：

```
doGetBean()
  ├─ getSingleton() - 从缓存获取（三级缓存）
  ├─ markBeanAsCreated() - 标记为正在创建
  ├─ createBean() - 创建 Bean
  │   └─ doCreateBean()
  │       ├─ createBeanInstance() - 实例化（反射调用构造器）
  │       ├─ populateBean() - 属性填充（依赖注入）
  │       └─ initializeBean() - 初始化
  │           ├─ invokeAwareMethods() - Aware 接口回调
  │           ├─ applyBeanPostProcessorsBeforeInitialization() - 前置处理
  │           ├─ invokeInitMethods() - 初始化方法
  │           └─ applyBeanPostProcessorsAfterInitialization() - 后置处理（AOP代理）
  └─ getSingleton() - 加入单例池
```

**关键断点**：
- `AbstractBeanFactory.doGetBean()` - 第 327 行
- `AbstractAutowireCapableBeanFactory.createBean()` - 第 519 行
- `AbstractAutowireCapableBeanFactory.doCreateBean()` - 第 563 行
- `AbstractAutowireCapableBeanFactory.populateBean()` - 第 1650 行
- `AbstractAutowireCapableBeanFactory.initializeBean()` - 第 1788 行

---

### 3️⃣ 依赖注入流程

**构造器注入**：
- `ConstructorResolver.autowireConstructor()` - 第 243 行
- 推导构造器参数类型
- 从容器中查找匹配的 Bean

**字段注入**：
- `AutowiredAnnotationBeanPostProcessor.postProcessProperties()` - 第 413 行
- 解析 @Autowired、@Value 注解
- 反射设置字段值

**Setter注入**：
- `AbstractAutowireCapableBeanFactory.applyPropertyValues()` - 第 1737 行

**关键断点**：
- `AutowiredAnnotationBeanPostProcessor.postProcessProperties()` - 第 413 行
- `DefaultListableBeanFactory.doResolveDependency()` - 第 1335 行

---

### 4️⃣ AOP 代理创建流程

**入口**：`AbstractAutoProxyCreator.postProcessAfterInitialization()` - 第 293 行

AOP 代理创建流程：

```
postProcessAfterInitialization()
  ├─ wrapIfNecessary() - 判断是否需要代理
  │   ├─ getAdvicesAndAdvisorsForBean() - 获取匹配的增强器
  │   └─ createProxy() - 创建代理
  │       ├─ ProxyFactory.getProxy()
  │       └─ 选择代理方式：
  │           ├─ JdkDynamicAopProxy（接口）
  │           └─ CglibAopProxy（类）
  └─ return proxy
```

**代理执行流程**：
- `JdkDynamicAopProxy.invoke()` - 第 214 行
- `ReflectiveMethodInvocation.proceed()` - 第 182 行（责任链模式）
- 依次执行拦截器链中的增强器

**关键断点**：
- `AbstractAutoProxyCreator.postProcessAfterInitialization()` - 第 293 行
- `AbstractAutoProxyCreator.wrapIfNecessary()` - 第 344 行
- `JdkDynamicAopProxy.invoke()` - 第 214 行
- `ReflectiveMethodInvocation.proceed()` - 第 182 行

---

### 5️⃣ 事务管理流程

**入口**：`TransactionInterceptor.invoke()` - 第 119 行

事务执行流程：

```
TransactionInterceptor.invoke()
  └─ invokeWithinTransaction()
      ├─ getTransactionAttribute() - 获取事务属性
      ├─ determineTransactionManager() - 确定事务管理器
      ├─ createTransactionIfNecessary() - 创建事务
      │   └─ getTransaction()
      │       ├─ doGetTransaction() - 获取事务对象
      │       ├─ isExistingTransaction() - 判断是否存在事务
      │       └─ doBegin() - 开启事务（获取连接、设置自动提交false）
      ├─ invocation.proceedWithInvocation() - 执行目标方法
      ├─ completeTransactionAfterThrowing() - 异常回滚
      └─ commitTransactionAfterReturning() - 提交事务
```

**关键断点**：
- `TransactionInterceptor.invoke()` - 第 119 行
- `TransactionAspectSupport.invokeWithinTransaction()` - 第 388 行
- `AbstractPlatformTransactionManager.getTransaction()` - 第 368 行
- `DataSourceTransactionManager.doBegin()` - 第 265 行
- `AbstractPlatformTransactionManager.commit()` - 第 711 行

---

## 🔍 调试步骤

### Step 1: 容器启动流程

1. 在 `SpringSourceDebugApp.main()` 第 23 行打断点
2. 进入 `AnnotationConfigApplicationContext` 构造方法
3. 进入 `AbstractApplicationContext.refresh()` - **核心方法**
4. 重点关注以下步骤：
   - `invokeBeanFactoryPostProcessors()` - 处理配置类
   - `registerBeanPostProcessors()` - 注册后置处理器
   - `finishBeanFactoryInitialization()` - 实例化单例Bean

### Step 2: Bean 生命周期

1. 在 `LifecycleBean` 的构造器打断点
2. 观察以下回调顺序：
   - 构造器
   - Aware 接口回调
   - BeanPostProcessor 前置处理
   - @PostConstruct
   - InitializingBean.afterPropertiesSet()
   - BeanPostProcessor 后置处理

### Step 3: 依赖注入

1. 在 `UserService` 构造器打断点
2. 进入 `ConstructorResolver.autowireConstructor()`
3. 观察如何解析构造器参数
4. 观察如何从容器中查找 UserRepository

### Step 4: AOP 代理创建

1. 在 `AbstractAutoProxyCreator.postProcessAfterInitialization()` 打断点
2. 观察 UserService 被包装成代理对象的过程
3. 查看生成的代理类（Cglib 或 JDK 动态代理）

### Step 5: AOP 方法拦截

1. 在 `userService.saveUser()` 调用处打断点
2. 进入 `JdkDynamicAopProxy.invoke()` 或 `CglibAopProxy.intercept()`
3. 进入 `ReflectiveMethodInvocation.proceed()`
4. 观察拦截器链的执行：
   - LoggingAspect 的环绕通知
   - TransactionInterceptor 事务拦截器
   - 目标方法执行

### Step 6: 事务管理

1. 在 `TransactionInterceptor.invoke()` 打断点
2. 观察事务开启过程（doBegin）
3. 观察目标方法执行
4. 观察事务提交（commit）

---

## 📚 源码学习路径推荐

### 第一阶段：IoC 容器（2-3周）

**核心类**：
- `BeanFactory` - Bean 工厂接口
- `ApplicationContext` - 应用上下文
- `BeanDefinition` - Bean 定义
- `BeanPostProcessor` - Bean 后置处理器

**核心流程**：
1. 容器启动：`refresh()` 方法
2. Bean 创建：`getBean()` -> `createBean()` -> `doCreateBean()`
3. 依赖注入：`populateBean()` -> `autowireByName/Type()`
4. 初始化：`initializeBean()` -> `invokeInitMethods()`

### 第二阶段：AOP（2-3周）

**核心类**：
- `Advisor` - 增强器
- `Pointcut` - 切点
- `Advice` - 通知
- `AopProxy` - AOP 代理

**核心流程**：
1. 代理创建：`AbstractAutoProxyCreator`
2. 切点匹配：`AspectJExpressionPointcut`
3. 代理执行：`JdkDynamicAopProxy` / `CglibAopProxy`
4. 拦截器链：`ReflectiveMethodInvocation.proceed()`

### 第三阶段：事务管理（1-2周）

**核心类**：
- `PlatformTransactionManager` - 事务管理器
- `TransactionDefinition` - 事务定义
- `TransactionStatus` - 事务状态
- `TransactionInterceptor` - 事务拦截器

**核心流程**：
1. 事务开启：`getTransaction()` -> `doBegin()`
2. 传播行为：7种事务传播行为的实现
3. 事务提交：`commit()` -> `doCommit()`
4. 事务回滚：`rollback()` -> `doRollback()`

### 第四阶段：Spring MVC（2-3周）

**核心类**：
- `DispatcherServlet` - 前端控制器
- `HandlerMapping` - 处理器映射
- `HandlerAdapter` - 处理器适配器
- `ViewResolver` - 视图解析器

---

## 💡 调试技巧

1. **使用条件断点**：
   ```java
   // 在 beanName 为 "userService" 时暂停
   beanName.equals("userService")
   ```

2. **查看调用栈**：
   - 理解方法调用链路
   - 找到核心入口方法

3. **计算表达式**：
   - 在调试时计算变量值
   - 执行方法查看结果

4. **修改变量**：
   - 动态修改变量值观察影响

5. **远程调试**：
   ```bash
   java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005 -jar app.jar
   ```

---

## 🎓 学习建议

1. **先理解设计模式**：
   - 工厂模式（BeanFactory）
   - 单例模式（Bean作用域）
   - 代理模式（AOP）
   - 模板方法（JdbcTemplate）
   - 观察者模式（事件机制）
   - 责任链模式（拦截器链）

2. **画流程图**：
   - 将复杂流程可视化
   - 标注关键方法和参数

3. **做笔记**：
   - 记录关键类和方法
   - 记录设计思路和技巧

4. **写博客**：
   - 输出倒逼输入
   - 加深理解和记忆

5. **坚持不懈**：
   - Spring 源码复杂庞大
   - 需要耐心和毅力
   - 一点一滴积累

---

## 🚀 运行项目

```bash
# 编译项目（在 spring-framework 根目录）
./gradlew :spring-debug:build

# 运行主类
java -cp spring-debug/build/classes/java/main:... com.debug.SpringSourceDebugApp

# 或直接在 IDE 中运行 SpringSourceDebugApp.main()
```

---

## 📖 推荐资源

1. **官方文档**：https://docs.spring.io/spring-framework/docs/current/reference/html/
2. **源码仓库**：https://github.com/spring-projects/spring-framework
3. **《Spring 源码深度解析》** - 郝佳
4. **《Spring 技术内幕》** - 计文柯

---

祝您源码学习顺利！💪
