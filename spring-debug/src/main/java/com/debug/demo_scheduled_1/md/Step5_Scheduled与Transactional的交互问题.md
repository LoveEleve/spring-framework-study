# Step 5：@Scheduled + @Transactional 的交互问题

> 本文基于 Spring 5.x 源码，深入分析 `@Scheduled` 和 `@Transactional` 同时使用时的代理与事务问题。

---

## 一、核心问题

在实际项目中，我们经常需要在定时任务中操作数据库，比如：

```java
@Component
public class OrderCleanupTask {
    
    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void cleanExpiredOrders() {
        // 清理过期订单...
        orderRepository.deleteExpiredOrders();
    }
}
```

这段代码看起来很自然，但隐藏着一个非常容易踩的坑：

> **`@Transactional` 在 `@Scheduled` 方法上可能不生效！**

要理解这个问题，需要搞清楚以下几个核心问题：

1. `ScheduledMethodRunnable` 中的 `target` 是原始对象还是代理对象？
2. `method.invoke(target)` 能否触发 `@Transactional` 的 AOP 拦截？
3. 什么情况下事务会生效 / 不生效？
4. 正确使用 `@Scheduled + @Transactional` 的几种方式

---

## 二、前置知识回顾

### 2.1 Spring AOP 代理的基本原理

Spring AOP 是 **基于代理** 的。当一个 Bean 上有 `@Transactional`、`@Async` 等注解时，Spring 会为它创建一个 **代理对象**：

```
┌────────────────────────────────────────────────────────────┐
│                   Spring 容器                               │
│                                                            │
│  ┌──────────────────────┐                                  │
│  │  原始 Bean (target)  │  ← 真正的业务代码                │
│  │  OrderCleanupTask    │                                  │
│  └──────────┬───────────┘                                  │
│             │                                              │
│             │ 被代理包裹                                    │
│             ▼                                              │
│  ┌──────────────────────────────────────┐                  │
│  │  代理 Bean (proxy)                   │                  │
│  │  OrderCleanupTask$$CGLIB$$xxx        │                  │
│  │                                      │                  │
│  │  cleanExpiredOrders() {              │                  │
│  │      开启事务                         │ ← 代理增强      │
│  │      target.cleanExpiredOrders()     │ ← 调用原始方法   │
│  │      提交事务                         │ ← 代理增强      │
│  │  }                                   │                  │
│  └──────────────────────────────────────┘                  │
│                                                            │
│  容器中注册的是代理 Bean，不是原始 Bean                      │
└────────────────────────────────────────────────────────────┘
```

**核心原则**：只有通过 **代理对象** 调用方法，AOP 拦截器（如 `TransactionInterceptor`）才会生效。直接通过 **原始对象** 或 **this** 调用方法，事务不会生效。

### 2.2 事务拦截的触发条件

```
方法调用 → 代理对象(proxy) → TransactionInterceptor.invoke()
                                    │
                                    ├── 获取事务属性（@Transactional 注解）
                                    ├── 开启事务
                                    ├── 调用目标方法 target.method()
                                    ├── 正常完成 → 提交事务
                                    └── 抛出异常 → 回滚事务
```

**关键**：触发事务的前提是调用链经过了代理对象。如果绕过代理直接调用，`TransactionInterceptor` 根本不会被触发。

---

## 三、源码深度分析：@Scheduled 如何调用目标方法

### 3.1 Step 1：BPP 扫描阶段 —— `postProcessAfterInitialization`

在 `ScheduledAnnotationBeanPostProcessor.postProcessAfterInitialization()` 中：

```java
// 源码位置：ScheduledAnnotationBeanPostProcessor.java

@Override
public Object postProcessAfterInitialization(Object bean, String beanName) {
    // 1. 跳过基础设施 Bean
    if (bean instanceof AopInfrastructureBean || bean instanceof TaskScheduler ||
            bean instanceof ScheduledExecutorService) {
        return bean;
    }
    
    // 2. 穿透代理，获取真实类
    //    ⭐ 注意：这里用 AopProxyUtils.ultimateTargetClass(bean) 获取的是「真实类」
    //    因为 @Scheduled 注解是加在原始类上的，代理类上找不到
    Class<?> targetClass = AopProxyUtils.ultimateTargetClass(bean);
    
    // 3. 在真实类上扫描 @Scheduled 注解
    Map<Method, Set<Scheduled>> annotatedMethods = MethodIntrospector.selectMethods(targetClass, ...);
    
    // 4. 逐个处理注解方法
    annotatedMethods.forEach((method, scheduledAnnotations) ->
        scheduledAnnotations.forEach(scheduled -> processScheduled(scheduled, method, bean)));
    //                                                                              ^^^^
    //                                                          ⭐ 注意：传入的是 bean，不是 target！
    
    return bean;  // ⭐ 直接返回原始 bean，不创建新代理
}
```

#### 💡 关键点 1：`bean` 参数是什么？

`postProcessAfterInitialization` 是在 Bean 初始化**之后**被调用的。在 Spring 的 Bean 生命周期中：

```
Bean 实例化 → 属性注入 → 初始化前置处理 → 初始化 → 初始化后置处理
                                                        │
                                            AOP 代理创建（如果需要）
                                                        │
                                            ScheduledAnnotationBPP
                                            postProcessAfterInitialization()
```

**问题来了**：`ScheduledAnnotationBPP` 的 `postProcessAfterInitialization` 和 AOP 代理创建的 BPP（如 `InfrastructureAdvisorAutoProxyCreator`），谁先执行？

⚠️ **重要纠正**：这里的执行顺序**不是**简单的 order 值比较！

先看各 BPP 的默认 order 值：

| BPP | 默认 order 值 | 来源 |
|-----|---------|------|
| `InfrastructureAdvisorAutoProxyCreator`（事务代理） | `Ordered.LOWEST_PRECEDENCE` | 继承自 `ProxyProcessorSupport`（`private int order = Ordered.LOWEST_PRECEDENCE`） |
| `AsyncAnnotationBeanPostProcessor`（异步代理） | `Ordered.LOWEST_PRECEDENCE` | 同上，继承自 `ProxyProcessorSupport` |
| `ScheduledAnnotationBeanPostProcessor` | `Ordered.LOWEST_PRECEDENCE` | 自己的 `getOrder()` 返回 |

**三者默认 order 值都是 `LOWEST_PRECEDENCE`（Integer.MAX_VALUE）！**

那么它们的实际执行顺序由什么决定呢？答案在 `PostProcessorRegistrationDelegate` 中：

```java
// PostProcessorRegistrationDelegate.java - BPP 注册逻辑

// 第一批：实现 PriorityOrdered 接口的 BPP → 最先注册执行
sortPostProcessors(priorityOrderedPostProcessors, beanFactory);
registerBeanPostProcessors(beanFactory, priorityOrderedPostProcessors);

// 第二批：实现 Ordered 接口的 BPP → 其次注册执行
sortPostProcessors(orderedPostProcessors, beanFactory);
registerBeanPostProcessors(beanFactory, orderedPostProcessors);

// 第三批：普通 BPP → 最后注册执行
registerBeanPostProcessors(beanFactory, nonOrderedPostProcessors);
```

三者都实现了 `Ordered` 接口（**不是** `PriorityOrdered`），所以都在第二批中。
当 order 值相同时，**执行顺序取决于 BeanDefinition 的注册先后顺序**。

在 Spring 的典型启动流程中：
1. `@EnableTransactionManagement` → 通过 `AutoProxyRegistrar` → 注册 `InfrastructureAdvisorAutoProxyCreator` 的 BeanDefinition
2. `@EnableScheduling` → 注册 `ScheduledAnnotationBeanPostProcessor` 的 BeanDefinition

由于 `@EnableTransactionManagement` 的配置类通常先被处理（或与 `@EnableScheduling` 同时但注册更早），所以 `InfrastructureAdvisorAutoProxyCreator` 的 BeanDefinition **先注册**，因此在排序后**先执行**。

> 💡 **补充说明：@EnableAsync 的情况**
>
> `AsyncAnnotationBeanPostProcessor` 默认 order 也是 `LOWEST_PRECEDENCE`，但可以通过 `@EnableAsync(order = Ordered.HIGHEST_PRECEDENCE)` 自定义。
> 如果设置为 `HIGHEST_PRECEDENCE`，异步 BPP 就会在事务 BPP **之前**执行，这意味着 `@Async` 代理会包在 `@Transactional` 代理外面。

```java
// ScheduledAnnotationBeanPostProcessor.java
@Override
public int getOrder() {
    return LOWEST_PRECEDENCE;  // Integer.MAX_VALUE
}

// ProxyProcessorSupport.java（InfrastructureAdvisorAutoProxyCreator 的祖先类）
private int order = Ordered.LOWEST_PRECEDENCE;  // 默认也是 Integer.MAX_VALUE
```

**结论**：

> 在标准 Spring 配置下，`InfrastructureAdvisorAutoProxyCreator` 的 `postProcessAfterInitialization` 
> **先于** `ScheduledAnnotationBPP` 执行（因为 BeanDefinition 注册更早）。
> 因此，如果 Bean 有 `@Transactional`，传入 `ScheduledAnnotationBPP` 的 `bean` 参数 **已经是代理对象** 了。

#### 💡 关键点 2：`AopProxyUtils.ultimateTargetClass(bean)` 做了什么？

```java
// AopProxyUtils.java
public static Class<?> ultimateTargetClass(Object candidate) {
    Assert.notNull(candidate, "Candidate object must not be null");
    Object current = candidate;
    Class<?> result = null;
    // 循环穿透代理层，直到找到最终的目标类
    while (current instanceof TargetClassAware) {
        result = ((TargetClassAware) current).getTargetClass();
        current = getSingletonTarget(current);  // 获取下一层目标
    }
    if (result == null) {
        // 如果是 CGLIB 代理，返回父类（即原始类）
        result = (AopUtils.isCglibProxy(candidate) ? 
                  candidate.getClass().getSuperclass() : candidate.getClass());
    }
    return result;
}
```

这段代码会 **穿透所有代理层**，获取最底层的原始类。因为 `@Scheduled` 注解是写在原始类上的，代理类上找不到。

### 3.2 Step 2：创建 Runnable —— `createRunnable`

```java
// ScheduledAnnotationBeanPostProcessor.java

protected Runnable createRunnable(Object target, Method method) {
    Assert.isTrue(method.getParameterCount() == 0, 
                  "Only no-arg methods may be annotated with @Scheduled");
    // ⭐ 选择可调用的方法（处理接口方法 → 实现类方法的映射）
    Method invocableMethod = AopUtils.selectInvocableMethod(method, target.getClass());
    // ⭐ 创建 ScheduledMethodRunnable，传入 target 和 method
    return new ScheduledMethodRunnable(target, invocableMethod);
}
```

这里的 `target` 就是前面 `processScheduled(scheduled, method, bean)` 中的 `bean`。

**那么问题来了：`target.getClass()` 会返回什么？**

- 如果 `bean` 是代理对象（CGLIB），`target.getClass()` 返回 `OrderCleanupTask$$CGLIB$$xxx`
- `AopUtils.selectInvocableMethod(method, target.getClass())` 会在 **代理类** 上找到对应的方法

```java
// AopUtils.java
public static Method selectInvocableMethod(Method method, @Nullable Class<?> targetType) {
    if (targetType == null) {
        return method;
    }
    Method methodToUse = MethodIntrospector.selectInvocableMethod(method, targetType);
    if (Modifier.isPrivate(methodToUse.getModifiers()) && 
        !Modifier.isStatic(methodToUse.getModifiers()) &&
        SpringProxy.class.isAssignableFrom(targetType)) {
        throw new IllegalStateException(...);  // 私有方法不能通过代理调用
    }
    return methodToUse;
}
```

### 3.3 Step 3：任务执行 —— `ScheduledMethodRunnable.run()`

```java
// ScheduledMethodRunnable.java

public class ScheduledMethodRunnable implements Runnable {

    private final Object target;   // ⭐ 这里存的是什么？
    private final Method method;

    @Override
    public void run() {
        try {
            ReflectionUtils.makeAccessible(this.method);
            this.method.invoke(this.target);   // ⭐ 直接反射调用！
        }
        catch (InvocationTargetException ex) {
            ReflectionUtils.rethrowRuntimeException(ex.getTargetException());
        }
        catch (IllegalAccessException ex) {
            throw new UndeclaredThrowableException(ex);
        }
    }
}
```

**最关键的一行**：`this.method.invoke(this.target)`

这是一个 **Java 反射调用**。那么问题来了：

> 如果 `target` 是代理对象，`method.invoke(proxy)` 能触发 AOP 拦截吗？

---

## 四、核心问题的答案：反射调用代理对象能否触发 AOP？

### 4.1 CGLIB 代理的情况

CGLIB 代理的原理是 **生成目标类的子类**：

```java
// CGLIB 代理类（伪代码）
class OrderCleanupTask$$CGLIB$$xxx extends OrderCleanupTask {
    
    private MethodInterceptor[] callbacks;  // 包含 TransactionInterceptor
    
    @Override
    public void cleanExpiredOrders() {
        // 不是直接调用 super.cleanExpiredOrders()
        // 而是走 CGLIB 的拦截器链
        callbacks[index].intercept(this, method, args, methodProxy);
    }
}
```

当我们调用 `method.invoke(proxy)` 时：
- `proxy` 是 CGLIB 代理对象（`OrderCleanupTask$$CGLIB$$xxx` 的实例）
- `method` 是 `cleanExpiredOrders` 方法
- 反射调用实际上就是调用 **代理类重写的方法**

```
method.invoke(proxy)
    ↓
proxy.cleanExpiredOrders()       // 调用的是 CGLIB 子类的重写方法
    ↓
DynamicAdvisedInterceptor.intercept()   // ⭐ 拦截器链被触发！
    ↓
TransactionInterceptor.invoke()  // ⭐ 事务拦截器生效！
    ↓
target.cleanExpiredOrders()      // 最终执行原始方法
```

> ✅ **结论：CGLIB 代理下，`method.invoke(proxy)` 可以触发 AOP 拦截，事务生效。**

### 4.2 JDK 动态代理的情况

JDK 动态代理的原理是 **实现目标接口**：

```java
// 假设有接口
public interface OrderCleanupService {
    void cleanExpiredOrders();
}

// JDK 代理类（伪代码）
class $Proxy123 implements OrderCleanupService {
    
    private InvocationHandler h;  // JdkDynamicAopProxy
    
    @Override
    public void cleanExpiredOrders() {
        // 所有方法调用都委托给 InvocationHandler
        h.invoke(this, method, args);
    }
}
```

当调用 `method.invoke(proxy)` 时：
- 反射调用代理对象的方法 → 触发 `InvocationHandler.invoke()` → AOP 拦截生效

> ✅ **结论：JDK 动态代理下，`method.invoke(proxy)` 同样可以触发 AOP 拦截，事务生效。**

### 4.3 那为什么说"可能不生效"？

关键在于 `target` 到底是不是代理对象。让我们重新审视整个流程：

```
场景分析：bean 有 @Scheduled 和 @Transactional
    
    ┌─────────────────────────────────────────────────────────────┐
    │ BPP 执行顺序（按注册先后顺序，order 值相同时取决于 BD 注册顺序）│
    │                                                             │
    │ 1. InfrastructureAdvisorAutoProxyCreator (BD 注册更早)       │
    │    → 发现 @Transactional → 创建代理对象                     │
    │    → 返回 proxy (CGLIB 或 JDK)                              │
    │                                                             │
    │ 2. ScheduledAnnotationBPP (order=LOWEST_PRECEDENCE)        │
    │    → 此时收到的 bean 参数 = proxy（代理对象）                │
    │    → AopProxyUtils.ultimateTargetClass(bean) 穿透获取真实类  │
    │    → 在真实类上找到 @Scheduled                               │
    │    → createRunnable(bean, method) → 传入 proxy              │
    │    → new ScheduledMethodRunnable(proxy, method)             │
    │                                                             │
    │ 结论：target = proxy = 代理对象                              │
    │       method.invoke(proxy) → ✅ AOP 生效 → ✅ 事务生效       │
    └─────────────────────────────────────────────────────────────┘
```

**在标准的 Spring 配置下（同一个类同时有 `@Scheduled` 和 `@Transactional`），事务是可以生效的！**

但是，有几种情况事务 **不会生效**：

---

## 五、事务不生效的场景（重点！⭐）

### 5.1 场景一：`@Scheduled` 方法调用同类中的 `@Transactional` 方法

```java
@Component
public class OrderCleanupTask {
    
    @Scheduled(cron = "0 0 2 * * ?")
    public void scheduledCleanup() {
        // ⭐ 这是 this 调用，不是代理调用！
        this.cleanExpiredOrders();
    }
    
    @Transactional
    public void cleanExpiredOrders() {
        orderRepository.deleteExpiredOrders();
    }
}
```

**分析**：

```
调度器调用 → method.invoke(proxy)
                   ↓
    proxy.scheduledCleanup()      // ⭐ 走代理，但 scheduledCleanup 没有 @Transactional
                   ↓                  所以 TransactionInterceptor 不拦截它
    target.scheduledCleanup()     // 调用原始对象的方法
                   ↓
    this.cleanExpiredOrders()     // ⭐ this = target（原始对象），不是 proxy！
                   ↓
    直接执行方法体                 // ❌ 事务不生效！
```

这是经典的 **"自调用"问题**（self-invocation）。Spring AOP 基于代理，`this` 指向的是原始对象，不经过代理，事务自然不会生效。

```
    ┌─────────────────────────────────────────────┐
    │           代理对象 (proxy)                    │
    │  ┌───────────────────────────────────────┐  │
    │  │ scheduledCleanup()                    │  │
    │  │   → 无 @Transactional，不拦截          │  │
    │  │   → 直接调用 target.scheduledCleanup() │  │
    │  └───────────────────────────────────────┘  │
    └─────────────────────────────────────────────┘
                         │
                         ▼
    ┌─────────────────────────────────────────────┐
    │           原始对象 (target)                   │
    │  ┌───────────────────────────────────────┐  │
    │  │ scheduledCleanup() {                  │  │
    │  │     this.cleanExpiredOrders();  ←───────── this = target，不是 proxy
    │  │ }                                     │  │
    │  │                                       │  │
    │  │ cleanExpiredOrders() {                │  │
    │  │     // ❌ 没有经过代理，事务不生效      │  │
    │  │     orderRepository.deleteExpiredOrders() │
    │  │ }                                     │  │
    │  └───────────────────────────────────────┘  │
    └─────────────────────────────────────────────┘
```

### 5.2 场景二：Bean 没有被 AOP 代理

如果 `@Scheduled` 所在的类没有任何 AOP 增强（没有 `@Transactional`、没有切面匹配），那么这个 Bean 就是原始对象，不是代理对象：

```java
@Component
public class SimpleTask {
    
    @Scheduled(fixedRate = 1000)
    public void doWork() {
        // 这个类没有 @Transactional，没有被任何切面匹配
        // 所以 bean = 原始对象
        // ScheduledMethodRunnable 中的 target = 原始对象
    }
}
```

这种情况下 `target` 是原始对象，反射调用不经过任何代理。但这本身也不需要事务，所以不是问题。

### 5.3 场景三：BPP 执行顺序异常

如果某些自定义 BPP 改变了执行顺序，导致 `ScheduledAnnotationBPP` 在 AOP 代理创建**之前**执行，那么 `bean` 就是原始对象而非代理对象。

> 在标准 Spring 配置下这不会发生，因为 `ScheduledAnnotationBPP` 的 order 值为 `LOWEST_PRECEDENCE`。

### 5.4 场景四：ScopedProxy（作用域代理）

Spring 的 `@Scope` + `proxyMode` 会创建作用域代理。在测试用例 `cronTaskWithScopedProxy` 中可以看到：

```java
// ScheduledAnnotationBeanPostProcessorTests.java

@Test
void cronTaskWithScopedProxy() {
    // ...
    ScheduledMethodRunnable runnable = (ScheduledMethodRunnable) task.getRunnable();
    Object targetObject = runnable.getTarget();
    // ⭐ target 是原始 Bean，不是作用域代理
    assertThat(targetObject).isEqualTo(
        context.getBean(ScopedProxyUtils.getTargetBeanName("target")));
}
```

作用域代理的情况比较特殊，`target` 指向的是内部的目标 Bean，而不是外部的作用域代理。

---

## 六、完整的调用链路图

### 6.1 场景 A：同一个方法上同时有 @Scheduled 和 @Transactional（✅ 生效）

```
┌──────────────────────────────────────────────────────────────────────┐
│                                                                      │
│  ① Bean 创建                                                         │
│     InfrastructureAdvisorAutoProxyCreator                            │
│     发现 @Transactional → 创建 CGLIB 代理 proxy                      │
│                                                                      │
│  ② 任务注册                                                          │
│     ScheduledAnnotationBPP.postProcessAfterInitialization(proxy)     │
│     → AopProxyUtils.ultimateTargetClass(proxy) = OrderCleanupTask   │
│     → 在 OrderCleanupTask 上找到 @Scheduled                          │
│     → createRunnable(proxy, method)                                  │
│     → new ScheduledMethodRunnable(proxy, method)                     │
│                                                                      │
│  ③ 任务执行                                                          │
│     调度线程 → ScheduledMethodRunnable.run()                          │
│     → method.invoke(proxy)                                           │
│     → proxy.cleanExpiredOrders()         // CGLIB 子类方法            │
│     → DynamicAdvisedInterceptor.intercept()                          │
│     → TransactionInterceptor.invoke()    // ✅ 事务拦截               │
│       → 开启事务                                                     │
│       → target.cleanExpiredOrders()      // 调用原始方法              │
│       → 提交/回滚事务                                                │
│                                                                      │
│  结论：✅ 事务生效                                                    │
└──────────────────────────────────────────────────────────────────────┘
```

### 6.2 场景 B：@Scheduled 方法调用同类中的 @Transactional 方法（❌ 不生效）

```
┌──────────────────────────────────────────────────────────────────────┐
│                                                                      │
│  ① Bean 创建                                                         │
│     InfrastructureAdvisorAutoProxyCreator                            │
│     发现 cleanExpiredOrders() 上有 @Transactional → 创建代理         │
│                                                                      │
│  ② 任务注册                                                          │
│     ScheduledAnnotationBPP.postProcessAfterInitialization(proxy)     │
│     → createRunnable(proxy, scheduledCleanup_method)                 │
│     → new ScheduledMethodRunnable(proxy, scheduledCleanup_method)    │
│                                                                      │
│  ③ 任务执行                                                          │
│     调度线程 → ScheduledMethodRunnable.run()                          │
│     → method.invoke(proxy)                                           │
│     → proxy.scheduledCleanup()                                       │
│     → TransactionInterceptor 检查: scheduledCleanup 没有 @Tx → 跳过  │
│     → target.scheduledCleanup()                                      │
│         → this.cleanExpiredOrders()    // ⭐ this = target!           │
│         → 直接执行，没有经过代理      // ❌ 事务不生效                 │
│                                                                      │
│  结论：❌ 事务不生效                                                  │
└──────────────────────────────────────────────────────────────────────┘
```

---

## 七、正确使用 @Scheduled + @Transactional 的方式

### 7.1 方式一：直接在 @Scheduled 方法上加 @Transactional ✅

```java
@Component
public class OrderCleanupTask {
    
    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void cleanExpiredOrders() {
        // ✅ 事务生效！
        // 因为 ScheduledMethodRunnable 中的 target = proxy
        // method.invoke(proxy) → 代理拦截 → TransactionInterceptor 生效
        orderRepository.deleteExpiredOrders();
    }
}
```

**原理**：调度器直接调用的就是带 `@Transactional` 的方法，且 `target` 是代理对象，AOP 拦截器会生效。

**注意**：整个 `@Scheduled` 方法体都在同一个事务中，如果方法执行时间很长，事务也会很长，需要注意事务超时。

### 7.2 方式二：分离调度层和业务层（推荐 ⭐）

```java
// 调度层：只负责触发
@Component
public class OrderCleanupScheduler {
    
    @Autowired
    private OrderCleanupService orderCleanupService;
    
    @Scheduled(cron = "0 0 2 * * ?")
    public void trigger() {
        // ✅ 通过注入的代理对象调用，事务生效！
        orderCleanupService.cleanExpiredOrders();
    }
}

// 业务层：负责事务和业务逻辑
@Service
public class OrderCleanupService {
    
    @Autowired
    private OrderRepository orderRepository;
    
    @Transactional
    public void cleanExpiredOrders() {
        // ✅ 这个方法通过代理对象调用，事务一定生效
        orderRepository.deleteExpiredOrders();
    }
}
```

**原理分析**：

```
调度线程 → ScheduledMethodRunnable.run()
    → method.invoke(scheduler)           // scheduler 可能没有被代理（没有 @Tx）
    → scheduler.trigger()                // 直接执行 trigger 方法体
    → orderCleanupService.cleanExpiredOrders()   
    //  ↑ orderCleanupService 是通过 @Autowired 注入的
    //  ↑ 它是 OrderCleanupService 的代理对象！
    //  ↑ 所以这个调用会经过 TransactionInterceptor
    → proxy.cleanExpiredOrders()         // ✅ AOP 拦截器链生效
    → TransactionInterceptor → 开启事务
    → target.cleanExpiredOrders()        // 执行真正的业务逻辑
    → 提交事务
```

**优点**：
1. 职责分离：调度逻辑和业务逻辑解耦
2. 事务一定生效：通过注入的 Bean 调用，一定走代理
3. 可测试性好：业务层可以独立测试
4. 事务粒度可控：可以在业务层精细控制事务边界

### 7.3 方式三：使用 `TransactionTemplate` 编程式事务 ✅

```java
@Component
public class OrderCleanupTask {
    
    @Autowired
    private TransactionTemplate transactionTemplate;
    
    @Autowired
    private OrderRepository orderRepository;
    
    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanExpiredOrders() {
        // ✅ 编程式事务，不依赖代理
        transactionTemplate.execute(status -> {
            orderRepository.deleteExpiredOrders();
            return null;
        });
    }
}
```

**原理**：`TransactionTemplate` 直接通过 `PlatformTransactionManager` 管理事务，不依赖 AOP 代理机制。

**优点**：
1. 不依赖代理，绝对可靠
2. 事务边界清晰
3. 可以在一个方法中开启多个事务

**缺点**：
1. 代码侵入性强
2. 需要手动管理

### 7.4 方式四：自注入（self-injection）✅

```java
@Component
public class OrderCleanupTask {
    
    @Autowired
    @Lazy  // ⭐ 必须加 @Lazy 避免循环依赖
    private OrderCleanupTask self;
    
    @Scheduled(cron = "0 0 2 * * ?")
    public void scheduledCleanup() {
        // ✅ 通过自注入的代理对象调用
        self.cleanExpiredOrders();
    }
    
    @Transactional
    public void cleanExpiredOrders() {
        orderRepository.deleteExpiredOrders();
    }
}
```

**原理**：`self` 通过 `@Autowired` 注入的是容器中的 Bean，即代理对象。通过 `self.cleanExpiredOrders()` 调用就会经过代理。

**注意**：需要加 `@Lazy` 避免循环依赖。

### 7.5 方式五：使用 `AopContext.currentProxy()`

```java
@Component
public class OrderCleanupTask {
    
    @Scheduled(cron = "0 0 2 * * ?")
    public void scheduledCleanup() {
        // ✅ 获取当前代理对象，通过代理调用
        OrderCleanupTask proxy = (OrderCleanupTask) AopContext.currentProxy();
        proxy.cleanExpiredOrders();
    }
    
    @Transactional
    public void cleanExpiredOrders() {
        orderRepository.deleteExpiredOrders();
    }
}
```

**前提条件**：需要开启 `exposeProxy`：

```java
@EnableAspectJAutoProxy(exposeProxy = true)
// 或
@EnableTransactionManagement  // 默认不开启 exposeProxy
```

**原理**：`AopContext.currentProxy()` 从 `ThreadLocal` 中获取当前的代理对象。但前提是当前方法是通过代理调用的（`exposeProxy = true` 时代理会把自己放入 ThreadLocal）。

**⚠️ 注意**：在 `@Scheduled` 场景下，如果 `scheduledCleanup()` 方法本身没有被 AOP 增强（没有 `@Transactional`），那么即使 `method.invoke(proxy)` 是在代理上调用的，对于 CGLIB 代理来说，由于这个方法没有匹配到任何 Advisor，可能会走 "fast path"（直接调用 `invokeSuper`），此时 `AopContext.currentProxy()` 可能为 null。

**建议**：这种方式不太可靠，不推荐在 `@Scheduled` 场景下使用。

---

## 八、各方式对比总结

| 方式 | 可靠性 | 侵入性 | 复杂度 | 推荐度 |
|------|--------|--------|--------|--------|
| ① 同一方法加 @Scheduled + @Transactional | ✅ 高 | 低 | 低 | ⭐⭐⭐⭐ |
| ② 分离调度层和业务层 | ✅ 最高 | 低 | 中 | ⭐⭐⭐⭐⭐ |
| ③ TransactionTemplate 编程式事务 | ✅ 最高 | 高 | 中 | ⭐⭐⭐⭐ |
| ④ 自注入 (self-injection) | ✅ 高 | 中 | 中 | ⭐⭐⭐ |
| ⑤ AopContext.currentProxy() | ⚠️ 有条件 | 高 | 高 | ⭐⭐ |

**生产环境推荐**：方式 ② > 方式 ① ≈ 方式 ③ > 方式 ④ > 方式 ⑤

---

## 九、源码级证据：为什么 method.invoke(proxy) 能触发 CGLIB 拦截

### 9.1 CGLIB 代理的方法调用路径

```java
// CglibAopProxy.java - DynamicAdvisedInterceptor

public Object intercept(Object proxy, Method method, Object[] args, 
                         MethodProxy methodProxy) throws Throwable {
    Object oldProxy = null;
    boolean setProxyContext = false;
    Object target = null;
    TargetSource targetSource = this.advised.getTargetSource();
    
    try {
        if (this.advised.exposeProxy) {
            oldProxy = AopContext.setCurrentProxy(proxy);
            setProxyContext = true;
        }
        
        target = targetSource.getTarget();           // 获取原始对象
        Class<?> targetClass = (target != null ? target.getClass() : null);
        
        // ⭐ 获取当前方法的拦截器链
        List<Object> chain = this.advised
            .getInterceptorsAndDynamicInterceptionAdvice(method, targetClass);
        
        Object retVal;
        if (chain.isEmpty() && CglibMethodInvocation.isMethodProxyCompatible(method)) {
            // ⭐ 没有拦截器 → 直接调用目标方法（跳过代理）
            Object[] argsToUse = AopProxyUtils.adaptArgumentsIfNecessary(method, args);
            retVal = invokeMethod(target, method, argsToUse, methodProxy);
        }
        else {
            // ⭐ 有拦截器（如 TransactionInterceptor）→ 构建拦截器链执行
            retVal = new CglibMethodInvocation(
                proxy, target, method, args, targetClass, chain, methodProxy
            ).proceed();
        }
        
        retVal = processReturnType(proxy, target, method, retVal);
        return retVal;
    }
    finally {
        if (target != null && !targetSource.isStatic()) {
            targetSource.releaseTarget(target);
        }
        if (setProxyContext) {
            AopContext.setCurrentProxy(oldProxy);
        }
    }
}
```

**关键逻辑**：

1. **获取拦截器链**：`getInterceptorsAndDynamicInterceptionAdvice(method, targetClass)`
   - 如果当前方法匹配到了 `BeanFactoryTransactionAttributeSourceAdvisor`（即方法上有 `@Transactional`），则链中包含 `TransactionInterceptor`
   - 如果没有匹配到任何 Advisor，链为空

2. **有拦截器**：构建 `CglibMethodInvocation`，通过责任链模式依次执行
3. **没有拦截器**：直接调用原始方法，跳过代理增强

### 9.2 TransactionInterceptor 在拦截器链中的执行

```java
// TransactionInterceptor.java

@Override
@Nullable
public Object invoke(MethodInvocation invocation) throws Throwable {
    // 获取目标类
    Class<?> targetClass = (invocation.getThis() != null ? 
        AopUtils.getTargetClass(invocation.getThis()) : null);
    
    // 调用父类的事务管理逻辑
    return invokeWithinTransaction(
        invocation.getMethod(), 
        targetClass, 
        invocation::proceed  // 回调：执行目标方法
    );
}
```

```java
// TransactionAspectSupport.java

protected Object invokeWithinTransaction(Method method, @Nullable Class<?> targetClass,
        InvocationCallback invocation) throws Throwable {
    
    // 1. 获取事务属性（解析 @Transactional 注解）
    TransactionAttributeSource tas = getTransactionAttributeSource();
    TransactionAttribute txAttr = (tas != null ? 
        tas.getTransactionAttribute(method, targetClass) : null);
    
    // 2. 获取事务管理器
    PlatformTransactionManager tm = determineTransactionManager(txAttr);
    
    // 3. 创建事务
    TransactionInfo txInfo = createTransactionIfNecessary(tm, txAttr, joinpointIdentification);
    
    Object retVal = null;
    try {
        // 4. ⭐ 执行目标方法
        retVal = invocation.proceedWithInvocation();
    }
    catch (Throwable ex) {
        // 5. 异常回滚
        completeTransactionAfterThrowing(txInfo, ex);
        throw ex;
    }
    finally {
        // 6. 清理事务信息
        cleanupTransactionInfo(txInfo);
    }
    
    // 7. 提交事务
    commitTransactionAfterReturning(txInfo);
    return retVal;
}
```

---

## 十、调度线程与事务的线程问题

### 10.1 事务是绑定在线程上的

Spring 事务使用 `ThreadLocal` 来存储事务信息：

```java
// TransactionSynchronizationManager.java
private static final ThreadLocal<Map<Object, Object>> resources = 
    new NamedThreadLocal<>("Transactional resources");

private static final ThreadLocal<Set<TransactionSynchronization>> synchronizations = 
    new NamedThreadLocal<>("Transaction synchronizations");
```

**关键问题**：`@Scheduled` 方法是在 **调度线程池** 中执行的，不是在主线程中。

```
主线程                          调度线程 (pool-1-thread-1)
  │                                │
  │  Spring 容器启动                │
  │  注册定时任务                   │
  │                                │
  │                                │  ← 定时任务触发
  │                                │  ScheduledMethodRunnable.run()
  │                                │  method.invoke(proxy)
  │                                │  → TransactionInterceptor.invoke()
  │                                │  → 在调度线程上开启事务
  │                                │  → Connection 从连接池获取
  │                                │  → 绑定到调度线程的 ThreadLocal
  │                                │  → 执行业务逻辑
  │                                │  → 提交/回滚事务
  │                                │  → Connection 归还连接池
```

这意味着：
1. 每次定时任务触发都会从连接池获取一个新的 Connection
2. 事务的生命周期与调度线程的任务执行周期一致
3. 调度线程中不会继承主线程的事务
4. 如果调度线程池 `poolSize = 1`，所有定时任务的事务都在同一个线程上串行执行

### 10.2 长事务风险

```java
@Scheduled(cron = "0 0 2 * * ?")
@Transactional
public void cleanExpiredOrders() {
    // ⚠️ 如果这个方法执行 10 分钟，事务就会持续 10 分钟！
    List<Order> orders = orderRepository.findExpiredOrders();  // 可能查出百万条
    for (Order order : orders) {
        orderRepository.delete(order);  // 逐条删除
    }
}
```

**问题**：
- 数据库连接被长时间占用
- 长事务可能导致锁竞争、死锁
- 如果任务失败，大量操作需要回滚

**改进方案**：分批处理 + 小事务

```java
@Component
public class OrderCleanupScheduler {
    
    @Autowired
    private OrderCleanupService cleanupService;
    
    @Scheduled(cron = "0 0 2 * * ?")
    public void trigger() {
        int batchSize = 100;
        while (true) {
            // 每批次一个独立事务
            int deleted = cleanupService.deleteExpiredBatch(batchSize);
            if (deleted < batchSize) {
                break;  // 没有更多数据了
            }
        }
    }
}

@Service
public class OrderCleanupService {
    
    @Transactional
    public int deleteExpiredBatch(int batchSize) {
        // 每个批次独立事务，删除 batchSize 条
        List<Order> orders = orderRepository.findExpiredOrders(batchSize);
        orderRepository.deleteAll(orders);
        return orders.size();
    }
}
```

---

## 十一、面试高频问题

### Q1：`@Scheduled` 方法上加 `@Transactional`，事务会生效吗？

**答**：在标准 Spring 配置下，**会生效**。因为：
1. `InfrastructureAdvisorAutoProxyCreator` 的 BeanDefinition 注册早于 `ScheduledAnnotationBPP`，在 order 值相同时先执行
2. 传入 `createRunnable` 的 `bean` 已经是代理对象
3. `method.invoke(proxy)` 会触发 CGLIB/JDK 代理的拦截器链
4. `TransactionInterceptor` 会正常拦截并管理事务

但如果是 `@Scheduled` 方法**内部调用**同类的 `@Transactional` 方法（自调用），事务不生效。

### Q2：为什么自调用不走代理？

**答**：Spring AOP 是基于代理的。当代理对象的方法执行完拦截器后，最终调用的是 `target.method()`（原始对象的方法）。在原始对象内部，`this` 指向的是原始对象，不是代理对象，所以 `this.otherMethod()` 不经过代理。

### Q3：`ScheduledMethodRunnable` 中的 target 是原始对象还是代理对象？

**答**：
- 如果 Bean 被 AOP 代理了（如有 `@Transactional`），target 是 **代理对象**
- 如果 Bean 没有被代理，target 是 **原始对象**
- 特殊情况：ScopedProxy 下，target 指向内部的目标 Bean

因为 `ScheduledAnnotationBPP.postProcessAfterInitialization(Object bean, ...)` 中的 `bean` 在 AOP 代理创建之后已经是代理对象，而 `createRunnable(bean, method)` 直接把 `bean` 存到了 `ScheduledMethodRunnable.target`。

### Q4：既然 method.invoke(proxy) 可以触发 AOP，那自调用问题怎么解决？

**答**：有以下几种方式：
1. **分离调度层和业务层**（推荐）：通过注入另一个 Bean 来调用
2. **编程式事务**：使用 `TransactionTemplate`
3. **自注入**：`@Autowired @Lazy private SelfType self;`
4. **AopContext**：`((SelfType) AopContext.currentProxy()).method()`（需要 `exposeProxy = true`）

### Q5：@Scheduled + @Transactional 在调度线程池中执行，对事务有什么影响？

**答**：
1. 事务在调度线程上开启，Connection 绑定到调度线程的 ThreadLocal
2. 不会继承主线程或其他线程的事务
3. 默认 poolSize=1，所有定时任务的事务串行执行
4. 注意避免长事务，建议分批处理

### Q6：如果定时任务执行中抛出异常，事务会怎样？

**答**：
1. `TransactionInterceptor` 会捕获异常
2. 根据 `@Transactional` 的 rollbackFor 配置决定回滚还是提交
3. 默认：RuntimeException 和 Error 回滚，checked Exception 提交
4. 事务回滚/提交后，异常继续向上传播
5. 被 `DelegatingErrorHandlingRunnable` 的 `ErrorHandler` 捕获（默认记录日志、吞掉异常）
6. 下次调度正常继续

---

## 十二、最佳实践总结

### 12.1 推荐的代码结构

```
┌─────────────────────────────────────────────────────────────┐
│  @Component                                                  │
│  XxxScheduler（调度层）                                       │
│  ├── @Scheduled 触发任务                                     │
│  └── 调用 XxxService 的事务方法                              │
│                                                              │
│  @Service                                                    │
│  XxxService（业务层）                                         │
│  ├── @Transactional 管理事务                                  │
│  └── 操作 Repository / Mapper                                │
│                                                              │
│  @Repository                                                 │
│  XxxRepository（数据层）                                      │
│  └── 数据库操作                                              │
└─────────────────────────────────────────────────────────────┘
```

### 12.2 配置建议

```java
@Configuration
@EnableScheduling
public class ScheduledConfig {
    
    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);  // 根据任务数量调整
        scheduler.setThreadNamePrefix("scheduled-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(60);
        scheduler.setErrorHandler(t -> {
            log.error("定时任务执行异常", t);
            // 可以发送告警通知
        });
        return scheduler;
    }
}
```

### 12.3 注意事项清单

| # | 事项 | 说明 |
|---|------|------|
| 1 | 避免自调用 | `@Scheduled` 方法内不要用 `this.xxx()` 调用 `@Transactional` 方法 |
| 2 | 分离职责 | 调度层和业务层分开，通过注入调用 |
| 3 | 控制事务粒度 | 避免长事务，分批处理大量数据 |
| 4 | 设置事务超时 | `@Transactional(timeout = 30)` 防止事务无限等待 |
| 5 | 异常处理 | 自定义 ErrorHandler，记录日志和告警 |
| 6 | 只读优化 | 查询操作使用 `@Transactional(readOnly = true)` |
| 7 | 线程池配置 | 根据任务数量和特性配置合适的 poolSize |

---

## 十三、实验验证代码建议

### 13.1 验证 target 是否是代理对象

```java
@Component
public class ProxyCheckTask {
    
    @Scheduled(fixedRate = 5000)
    @Transactional
    public void checkProxy() {
        System.out.println("=== 代理检查 ===");
        System.out.println("this.getClass() = " + this.getClass().getName());
        System.out.println("是否是 CGLIB 代理: " + AopUtils.isCglibProxy(this));
        // 如果 target 是代理对象，this.getClass() 应该包含 $$EnhancerBySpringCGLIB$$
    }
}
```

### 13.2 验证自调用事务不生效

```java
@Component
public class SelfInvocationTask {
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Scheduled(fixedRate = 10000)
    public void trigger() {
        System.out.println("=== 触发自调用测试 ===");
        try {
            this.doWithTransaction();
        } catch (Exception e) {
            System.out.println("异常: " + e.getMessage());
        }
    }
    
    @Transactional
    public void doWithTransaction() {
        jdbcTemplate.update("INSERT INTO test_log(msg) VALUES(?)", "test");
        System.out.println("插入成功，准备抛异常...");
        throw new RuntimeException("故意抛异常");
        // 如果事务生效，插入应该被回滚
        // 如果事务不生效，插入不会回滚（可以在数据库中查到）
    }
}
```

### 13.3 验证分离调用事务生效

```java
@Component
public class SeparateCallScheduler {
    
    @Autowired
    private SeparateCallService service;
    
    @Scheduled(fixedRate = 10000)
    public void trigger() {
        System.out.println("=== 触发分离调用测试 ===");
        try {
            service.doWithTransaction();
        } catch (Exception e) {
            System.out.println("异常: " + e.getMessage());
        }
    }
}

@Service
public class SeparateCallService {
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Transactional
    public void doWithTransaction() {
        jdbcTemplate.update("INSERT INTO test_log(msg) VALUES(?)", "test");
        System.out.println("插入成功，准备抛异常...");
        throw new RuntimeException("故意抛异常");
        // 这次事务一定生效，插入会被回滚
    }
}
```

---

> **总结**：`@Scheduled + @Transactional` 的核心问题在于理解 Spring AOP 的代理机制。掌握了 `ScheduledMethodRunnable` 中 `target` 的来源（代理对象 vs 原始对象）、`method.invoke(target)` 的行为（是否经过代理拦截），以及自调用问题的本质，就能在实际项目中正确使用这两个注解的组合。
