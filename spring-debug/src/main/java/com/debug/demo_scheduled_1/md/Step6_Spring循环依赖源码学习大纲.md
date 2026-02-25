# Spring 循环依赖源码深度学习大纲

> 基于 Spring 5.x 源码，系统性学习 Spring IoC 容器如何解决循环依赖问题。
> 这是 Spring 源码中最经典、面试最高频的知识点之一，涉及 Bean 生命周期、三级缓存、AOP 代理等多个核心机制的交汇。

---

## 学习路线总览

```
Sub-Step 1: 什么是循环依赖 & 为什么需要解决
  │
  ▼
Sub-Step 2: 三级缓存的设计 —— 核心数据结构与整体流程（⭐ 最核心）
  │
  ▼
Sub-Step 3: 源码逐行剖析 —— getSingleton / doCreateBean / populateBean
  │
  ▼
Sub-Step 4: AOP 代理 + 循环依赖 —— getEarlyBeanReference 与提前代理
  │
  ▼
Sub-Step 5: 循环依赖的边界 —— 哪些场景解决不了 & 一致性检查
  │
  ▼
Sub-Step 6: 面试通关 & 总结 —— 高频面试题 + 最佳实践 + Spring Boot 3.x 变化
```

---

## Sub-Step 1：什么是循环依赖 & 为什么需要解决

### 1.1 循环依赖的定义

- A 依赖 B，B 依赖 A（直接循环）
- A 依赖 B，B 依赖 C，C 依赖 A（间接循环）
- A 依赖 A（自依赖）

```
直接循环：          间接循环：             自依赖：
A ──→ B            A ──→ B              A ──→ A
↑     │            ↑     │
└─────┘            C ←───┘
```

### 1.2 为什么会产生循环依赖

- 实际项目中的常见场景举例
  - Service 层互相调用（UserService ↔ OrderService）
  - 事件监听与发布（EventPublisher ↔ EventListener）
  - 自注入场景（Service 注入自身的代理对象）
- 循环依赖不一定是设计问题，但过多循环依赖是代码坏味道的信号

### 1.3 没有三级缓存会怎样？—— 死循环问题

```
1. 创建 A → 需要注入 B → getBean("B")
2. 创建 B → 需要注入 A → getBean("A")
3. 创建 A → 需要注入 B → getBean("B")
4. ...无限递归，最终 StackOverflow！
```

- **关键问题**：在创建 A 的过程中（还没创建完），又触发了 A 的创建
- **核心思路**：能不能在 A 还没完全创建完时，先把一个"半成品 A"暴露出去，让 B 先拿到？

### 1.4 注入方式的分类与循环依赖的关系

| 注入方式 | 能否解决循环依赖 | 原因 |
|---------|---------------|------|
| Setter / @Autowired 字段注入 | ✅ 能 | 实例化和属性注入是分开的两步 |
| 构造器注入 | ❌ 不能 | 实例化时就需要依赖，无法暴露半成品 |
| @Lazy 构造器注入 | ✅ 能 | 注入的是代理对象，延迟真正获取 |

### 1.5 已有 Demo 回顾

- 回顾 `circulDemo` 中的 Man ↔ WoMan 案例
- 打断点验证循环依赖解决流程

#### 🔑 关键源码类
- `DefaultSingletonBeanRegistry`（三级缓存定义）
- `AbstractBeanFactory.doGetBean()`（入口）

#### 💡 调试建议
- 在 `Man` 和 `WoMan` 的构造方法中打断点
- 在 `DefaultSingletonBeanRegistry.getSingleton()` 中打断点
- 观察三级缓存的变化过程

#### 📝 面试题
- 什么是 Spring 循环依赖？
- Spring 能解决所有循环依赖吗？

---

## Sub-Step 2：三级缓存的设计 —— 核心数据结构与整体流程（⭐ 最核心）

### 2.1 三级缓存是什么？—— 三个 Map

```java
// DefaultSingletonBeanRegistry.java

/** 一级缓存：存放完全初始化好的 Bean（成品） */
private final Map<String, Object> singletonObjects = new ConcurrentHashMap<>(256);

/** 二级缓存：存放提前暴露的 Bean（半成品，可能已被代理） */
private final Map<String, Object> earlySingletonObjects = new ConcurrentHashMap<>(16);

/** 三级缓存：存放 Bean 的工厂对象（ObjectFactory，用于生成早期引用） */
private final Map<String, ObjectFactory<?>> singletonFactories = new HashMap<>(16);
```

- 深入分析每一级缓存的**存入时机**和**读取时机**
- 为什么用三级而不是两级？三级的核心价值是什么？

### 2.2 三级缓存的状态变迁图

```
Bean 创建前          Bean 实例化后        被其他 Bean 引用时     Bean 初始化完成后
┌──────────┐       ┌──────────┐       ┌──────────┐         ┌──────────┐
│ 一级: 无  │       │ 一级: 无  │       │ 一级: 无  │         │ 一级: ✅  │
│ 二级: 无  │  ──→  │ 二级: 无  │  ──→  │ 二级: ✅  │  ──→    │ 二级: 清除│
│ 三级: 无  │       │ 三级: ✅  │       │ 三级: 清除│         │ 三级: 清除│
└──────────┘       └──────────┘       └──────────┘         └──────────┘
                   addSingleton-      getSingleton()        addSingleton()
                   Factory()          三级→二级升级          二级→一级升级
```

### 2.3 完整的循环依赖解决流程（以 A ↔ B 为例）

```
步骤详解：
1. getBean("A") 
   → 三级缓存都找不到 A
   → 标记 A 正在创建中（singletonsCurrentlyInCreation.add("A")）
   → 实例化 A（构造方法）
   → 将 A 的 ObjectFactory 放入三级缓存
   → populateBean("A") → 发现需要 B → getBean("B")

2. getBean("B")
   → 三级缓存都找不到 B
   → 标记 B 正在创建中
   → 实例化 B（构造方法）
   → 将 B 的 ObjectFactory 放入三级缓存
   → populateBean("B") → 发现需要 A → getBean("A")

3. getBean("A")（第二次，此时 A 正在创建中）
   → getSingleton("A", true) 
   → 一级缓存没有
   → 二级缓存没有  
   → 三级缓存有！→ 调用 ObjectFactory.getObject() 获取早期引用
   → 将结果放入二级缓存，从三级缓存移除
   → 返回 A 的早期引用给 B

4. B 的 populateBean 完成（A 已注入）
   → initializeBean("B") → BPP 处理
   → B 创建完成，放入一级缓存

5. 回到 A 的 populateBean（B 已注入）
   → initializeBean("A") → BPP 处理
   → A 创建完成，放入一级缓存
```

### 2.4 为什么需要三级缓存？两级行不行？

- **核心答案**：为了延迟 AOP 代理的创建
- 如果没有 AOP，两级缓存就够了
- 三级缓存的 `ObjectFactory` 是一个 Lambda：`() -> getEarlyBeanReference(beanName, mbd, bean)`
- 只有在真正被循环引用时才调用，避免不必要的提前代理
- 如果直接在实例化后就创建代理（二级缓存方案），会破坏 Spring 的设计原则：**代理应该在 BPP 阶段创建**

### 2.5 用一张大图串联整体流程

- 画出 `doGetBean → createBean → doCreateBean` 的完整流程
- 标注三级缓存的每个操作点
- 标注 `populateBean`、`initializeBean` 的位置

#### 🔑 关键源码类
- `DefaultSingletonBeanRegistry`：三级缓存的定义与操作
  - `getSingleton(String beanName, boolean allowEarlyReference)`
  - `addSingletonFactory(String beanName, ObjectFactory<?> singletonFactory)`
  - `addSingleton(String beanName, Object singletonObject)`
- `AbstractAutowireCapableBeanFactory.doCreateBean()`：Bean 创建主流程

#### 💡 调试建议
- 在 `DefaultSingletonBeanRegistry.getSingleton()` 第一个重载（带 `allowEarlyReference` 参数）打断点
- 观察 `isSingletonCurrentlyInCreation(beanName)` 的判断
- 在 `addSingletonFactory()` 打断点，观察三级缓存的存入
- 在 `addSingleton()` 打断点，观察一级缓存的最终存入

#### 📝 面试题
- Spring 的三级缓存分别是什么？各自存什么？
- 为什么需要三级缓存？两级不行吗？
- 三级缓存中的 ObjectFactory 是什么时候调用的？

---

## Sub-Step 3：源码逐行剖析 —— getSingleton / doCreateBean / populateBean

### 3.1 入口：`AbstractBeanFactory.doGetBean()`

- `transformedBeanName(name)` 处理 FactoryBean 的 `&` 前缀和别名
- `getSingleton(beanName)` —— 先尝试从缓存获取
- `isPrototypeCurrentlyInCreation(beanName)` —— 原型 Bean 的循环依赖检测
- `getSingleton(beanName, () -> createBean(...))` —— 核心创建逻辑

### 3.2 核心方法一：`getSingleton(String beanName, boolean allowEarlyReference)`

```java
// DefaultSingletonBeanRegistry.java
// 这是缓存查找的核心方法，理解它就理解了一半

@Nullable
protected Object getSingleton(String beanName, boolean allowEarlyReference) {
    // 1. 先查一级缓存
    Object singletonObject = this.singletonObjects.get(beanName);
    
    if (singletonObject == null && isSingletonCurrentlyInCreation(beanName)) {
        // 2. 一级没有 + 正在创建中 → 查二级缓存
        singletonObject = this.earlySingletonObjects.get(beanName);
        
        if (singletonObject == null && allowEarlyReference) {
            // 3. 二级也没有 + 允许早期引用 → 查三级缓存
            synchronized (this.singletonObjects) {
                // Double-check（加锁后再查一次）
                singletonObject = this.singletonObjects.get(beanName);
                if (singletonObject == null) {
                    singletonObject = this.earlySingletonObjects.get(beanName);
                    if (singletonObject == null) {
                        ObjectFactory<?> singletonFactory = this.singletonFactories.get(beanName);
                        if (singletonFactory != null) {
                            // ⭐ 调用 ObjectFactory，获取早期引用
                            singletonObject = singletonFactory.getObject();
                            // 三级 → 二级 升级
                            this.earlySingletonObjects.put(beanName, singletonObject);
                            this.singletonFactories.remove(beanName);
                        }
                    }
                }
            }
        }
    }
    return singletonObject;
}
```

需要逐行分析：
- 为什么要 `isSingletonCurrentlyInCreation` 判断？
- 为什么要 Double-check locking？
- `allowEarlyReference` 什么时候为 true / false？
- 三级 → 二级升级的时机和意义

### 3.3 核心方法二：`getSingleton(String beanName, ObjectFactory<?> singletonFactory)`

```java
// 这是创建 Bean 的外层包装方法
// 核心职责：标记创建中状态、调用创建逻辑、放入一级缓存

public Object getSingleton(String beanName, ObjectFactory<?> singletonFactory) {
    synchronized (this.singletonObjects) {
        Object singletonObject = this.singletonObjects.get(beanName);
        if (singletonObject == null) {
            // 标记为正在创建中
            beforeSingletonCreation(beanName);
            try {
                // ⭐ 调用 createBean()，这才是真正创建 Bean 的地方
                singletonObject = singletonFactory.getObject();
                newSingleton = true;
            } finally {
                // 取消创建中标记
                afterSingletonCreation(beanName);
            }
            if (newSingleton) {
                // ⭐ 放入一级缓存，同时清除二级和三级缓存
                addSingleton(beanName, singletonObject);
            }
        }
        return singletonObject;
    }
}
```

需要重点分析：
- `beforeSingletonCreation` / `afterSingletonCreation` 做了什么？
- `singletonsCurrentlyInCreation` Set 的作用
- `addSingleton` 的缓存清理逻辑

### 3.4 核心方法三：`AbstractAutowireCapableBeanFactory.doCreateBean()`

```java
// Bean 创建的主流程
protected Object doCreateBean(String beanName, RootBeanDefinition mbd, Object[] args) {
    // 1. 实例化（构造方法）
    BeanWrapper instanceWrapper = createBeanInstance(beanName, mbd, args);
    Object bean = instanceWrapper.getWrappedInstance();
    
    // 2. ⭐ 判断是否需要提前暴露（解决循环依赖的关键！）
    boolean earlySingletonExposure = (mbd.isSingleton() 
            && this.allowCircularReferences 
            && isSingletonCurrentlyInCreation(beanName));
    
    if (earlySingletonExposure) {
        // ⭐ 放入三级缓存！
        addSingletonFactory(beanName, () -> getEarlyBeanReference(beanName, mbd, bean));
    }
    
    Object exposedObject = bean;
    
    // 3. 属性注入
    populateBean(beanName, mbd, instanceWrapper);
    
    // 4. 初始化（BPP 前置/后置处理、InitializingBean、init-method）
    exposedObject = initializeBean(beanName, exposedObject, mbd);
    
    // 5. ⭐ 循环依赖的一致性检查！
    if (earlySingletonExposure) {
        Object earlySingletonReference = getSingleton(beanName, false);
        if (earlySingletonReference != null) {
            if (exposedObject == bean) {
                exposedObject = earlySingletonReference;
            } else {
                // ⭐ 如果 exposedObject 被 BPP 替换了（如创建了新代理）
                // 且有其他 Bean 已经注入了旧的早期引用 → 不一致！
                // ... 检查是否有依赖的 Bean → 可能抛异常
            }
        }
    }
    
    return exposedObject;
}
```

需要重点分析：
- `earlySingletonExposure` 的三个条件各自含义
- `allowCircularReferences` 配置项（Spring Boot 3.x 默认改为了 false！）
- 三级缓存存入的 Lambda 表达式 `() -> getEarlyBeanReference(...)`
- 一致性检查的详细逻辑（后面 Sub-Step 5 深入）

### 3.5 `populateBean()` 中触发循环依赖的位置

- `@Autowired` 字段注入：`AutowiredAnnotationBeanPostProcessor`
  - `inject()` → `beanFactory.resolveDependency()` → `doResolveDependency()` → `beanFactory.getBean()`
- `@Resource` 字段注入：`CommonAnnotationBeanPostProcessor`
- Setter 注入：`applyPropertyValues()`

需要跟踪：
- 从 `@Autowired` 字段注入到最终调用 `getBean()` 的完整调用链
- 这个 `getBean()` 又回到了 3.1 的入口，形成了递归

### 3.6 `initializeBean()` 阶段

- `applyBeanPostProcessorsBeforeInitialization()` → BPP 前置处理
- `invokeInitMethods()` → `InitializingBean.afterPropertiesSet()` + `init-method`
- `applyBeanPostProcessorsAfterInitialization()` → BPP 后置处理
  - ⭐ **AOP 代理就是在这里创建的**（正常情况下）
  - `AbstractAutoProxyCreator.postProcessAfterInitialization()`

### 3.7 隐藏细节：BPP 的 order 值 —— `AopConfigUtils` 的覆盖机制

> 💡 这是之前学习中发现的一个**非常容易踩的认知陷阱**，在循环依赖中尤其重要。

`ProxyProcessorSupport`（`AbstractAutoProxyCreator` 的祖先类）中默认 order = `LOWEST_PRECEDENCE`：

```java
// ProxyProcessorSupport.java
private int order = Ordered.LOWEST_PRECEDENCE;  // 默认 Integer.MAX_VALUE
```

**但是！** 在 `AopConfigUtils.registerOrEscalateApcAsRequired()` 中注册 `InfrastructureAdvisorAutoProxyCreator` 的 BeanDefinition 时：

```java
// AopConfigUtils.java
RootBeanDefinition beanDefinition = new RootBeanDefinition(cls);
beanDefinition.getPropertyValues().add("order", Ordered.HIGHEST_PRECEDENCE);  // ⭐ 覆盖！
```

**通过 BeanDefinition 的 PropertyValues 显式设置了 `order = HIGHEST_PRECEDENCE`（`Integer.MIN_VALUE`）！**

这就保证了 `InfrastructureAdvisorAutoProxyCreator`（事务代理创建器）在 BPP 排序时一定排在最前面：

```
排序后的 BPP 执行顺序：
[0] InfrastructureAdvisorAutoProxyCreator  → order = MIN_VALUE  → 最先执行
[1] AsyncAnnotationBeanPostProcessor       → order = MAX_VALUE  → 后执行
[2] ScheduledAnnotationBeanPostProcessor   → order = MAX_VALUE  → 后执行
```

这个细节在循环依赖中至关重要——它确保了 AOP 代理一定在 `ScheduledAnnotationBPP` 之前创建，也确保了 `getEarlyBeanReference()` 中 `AbstractAutoProxyCreator` 能正确参与提前代理的创建。

#### 🔑 关键源码类
- `DefaultSingletonBeanRegistry`
  - `getSingleton(String, boolean)` —— 缓存查找
  - `getSingleton(String, ObjectFactory)` —— 创建包装
  - `addSingletonFactory()` —— 存入三级缓存
  - `addSingleton()` —— 存入一级缓存
- `AbstractAutowireCapableBeanFactory`
  - `doCreateBean()` —— 创建主流程
  - `populateBean()` —— 属性注入
  - `initializeBean()` —— 初始化
- `AutowiredAnnotationBeanPostProcessor`
  - `postProcessProperties()` → `inject()` —— 触发循环依赖的位置

#### 💡 调试建议
- 在 `doCreateBean()` 打断点，重点观察 `earlySingletonExposure` 的判断
- 在 `addSingletonFactory()` 打断点，观察 Lambda 表达式
- 在 `populateBean()` 打断点，观察属性注入触发 `getBean()` 的递归
- 在 `getSingleton(beanName, true)` 打断点，观察第二次获取 A 时命中三级缓存
- **建议用 Man ↔ WoMan 的 Demo 来跟踪**

#### 📝 面试题
- 请描述 Spring 解决循环依赖的完整流程
- `getSingleton` 方法中的 Double-check locking 是为了什么？
- Bean 的创建过程中，三级缓存的状态是如何变化的？

---

## Sub-Step 4：AOP 代理 + 循环依赖 —— getEarlyBeanReference 与提前代理

> 这是三级缓存存在的**根本原因**！没有 AOP，两级缓存就够了。

### 4.1 `getEarlyBeanReference()` 方法剖析

```java
// AbstractAutowireCapableBeanFactory.java
protected Object getEarlyBeanReference(String beanName, RootBeanDefinition mbd, Object bean) {
    Object exposedObject = bean;
    if (!mbd.isSynthetic() && hasInstantiationAwareBeanPostProcessors()) {
        for (SmartInstantiationAwareBeanPostProcessor bp : getBeanPostProcessorCache().smartInstantiationAware) {
            exposedObject = bp.getEarlyBeanReference(exposedObject, beanName);
        }
    }
    return exposedObject;
}
```

- 遍历所有 `SmartInstantiationAwareBeanPostProcessor`
- 调用每个 BPP 的 `getEarlyBeanReference()` 方法
- 如果有 AOP，`AbstractAutoProxyCreator` 会在这里**提前创建代理**

### 4.2 `AbstractAutoProxyCreator.getEarlyBeanReference()`

```java
// AbstractAutoProxyCreator.java
@Override
public Object getEarlyBeanReference(Object bean, String beanName) {
    Object cacheKey = getCacheKey(bean.getClass(), beanName);
    this.earlyProxyReferences.put(cacheKey, bean);  // ⭐ 记录已经提前代理过
    return wrapIfNecessary(bean, beanName, cacheKey);  // ⭐ 创建代理
}
```

关键分析：
- `earlyProxyReferences` 的作用：记录哪些 Bean 已经通过 `getEarlyBeanReference` 提前创建了代理
- `wrapIfNecessary()` 和正常 AOP 流程是**同一个方法**

### 4.3 提前代理 vs 正常代理的冲突处理

```java
// AbstractAutoProxyCreator.postProcessAfterInitialization()
// 正常 AOP 代理创建（initializeBean 阶段）
@Override
public Object postProcessAfterInitialization(@Nullable Object bean, String beanName) {
    if (bean != null) {
        Object cacheKey = getCacheKey(bean.getClass(), beanName);
        // ⭐ 检查是否已经在 getEarlyBeanReference 中提前创建了代理
        if (this.earlyProxyReferences.remove(cacheKey) != bean) {
            return wrapIfNecessary(bean, beanName, cacheKey);
        }
    }
    return bean;
}
```

关键逻辑：
- 如果 `earlyProxyReferences` 中有记录，说明已经提前创建了代理 → **跳过**，不重复创建
- 如果没有记录，说明不涉及循环依赖 → **正常创建代理**
- 这保证了代理对象只被创建一次

### 4.4 有 AOP 的循环依赖完整流程

```
场景：A 有 @Transactional（需要代理），B 依赖 A，A 依赖 B

1. 创建 A
   ├── 实例化 A（原始对象 beanA）
   ├── 三级缓存存入：singletonFactories.put("A", () -> getEarlyBeanReference("A", mbd, beanA))
   └── populateBean("A") → 需要 B → getBean("B")

2. 创建 B
   ├── 实例化 B
   ├── populateBean("B") → 需要 A → getBean("A")
   │   └── getSingleton("A", true)
   │       └── 三级缓存命中！
   │       └── 调用 ObjectFactory.getObject()
   │           └── getEarlyBeanReference("A", mbd, beanA)
   │               └── AbstractAutoProxyCreator.getEarlyBeanReference()
   │                   └── wrapIfNecessary() → 创建代理 proxyA
   │                   └── earlyProxyReferences.put("A", beanA)  // 记录
   │       └── 二级缓存存入：earlySingletonObjects.put("A", proxyA)
   │       └── 三级缓存移除：singletonFactories.remove("A")
   │       └── 返回 proxyA 给 B
   └── initializeBean("B") → B 完成

3. 回到 A
   ├── populateBean("A") 完成（B 已注入）
   └── initializeBean("A")
       └── postProcessAfterInitialization()
           └── AbstractAutoProxyCreator
               └── earlyProxyReferences.remove("A") == beanA → 已提前代理 → 跳过！

4. 一致性检查
   ├── getSingleton("A", false) → 从二级缓存拿到 proxyA
   ├── exposedObject == bean? → beanA == beanA → true!
   └── exposedObject = proxyA（用二级缓存中的代理对象替换）

5. addSingleton("A", proxyA) → 最终放入一级缓存
```

### 4.5 三级缓存的核心价值证明

思考题：为什么不在实例化后直接创建代理放入二级缓存？

```
方案 A（三级缓存，Spring 的实际方案）：
  → 只有真正被循环引用时，才通过 ObjectFactory 创建代理
  → 如果没有循环依赖，代理在 initializeBean 阶段正常创建
  → 保持了 Spring Bean 生命周期的设计原则

方案 B（二级缓存，实例化后立即创建代理）：
  → 每个有 AOP 的 Bean 都要在实例化后立即创建代理
  → 破坏了 Spring 的设计原则：BPP 应该在初始化阶段工作
  → 属性注入的是代理对象，但代理内部的 target 可能还未完成属性注入
  → 增加了复杂度
```

### 4.6 @Async 与 @Transactional 在循环依赖中的区别

- `@Transactional`：由 `AbstractAutoProxyCreator`（子类）处理，支持 `getEarlyBeanReference` → ✅ 循环依赖可以正常工作
- `@Async`：由 `AsyncAnnotationBeanPostProcessor` 处理，它在 `postProcessAfterInitialization` 中创建新代理，**不走** `getEarlyBeanReference` → ❌ 循环依赖会导致一致性检查失败

### 4.7 易混淆概念：BPP 的 order vs Advisor 的 order —— 两个完全不同的维度

> 💡 这是之前学习 `@Async + @Transactional` 时反复讨论的一个核心区别。

```
┌──────────────────────┬───────────────────────────────────────────┐
│  BPP 的 order         │  Advisor 的 order                         │
│  (循环依赖中关注的)    │  (@Async+@Transactional 时关注的)          │
├──────────────────────┼───────────────────────────────────────────┤
│  决定哪个 BPP 先执行   │  决定代理对象内部拦截器链的排列顺序         │
│  postProcess...()    │                                           │
├──────────────────────┼───────────────────────────────────────────┤
│  影响：bean 是原始对象  │  影响：谁在外层先拦截                     │
│  还是代理对象？         │  谁在内层后拦截？                         │
├──────────────────────┼───────────────────────────────────────────┤
│  InfrastructureAdvisor│  AsyncAnnotationAdvisor                   │
│  AutoProxyCreator     │  → order = MIN_VALUE（最外层）             │
│  → BPP order = MIN    │                                           │
│                      │  BeanFactoryTransactionAdvisor             │
│  ScheduledAnnotation  │  → order = MAX_VALUE（内层）               │
│  BPP → order = MAX   │                                           │
└──────────────────────┴───────────────────────────────────────────┘
```

在循环依赖场景中，关注的是 **BPP 的 order**——它决定了 `getEarlyBeanReference()` 中哪些 BPP 参与提前代理创建。

在 `@Async + @Transactional` 同一方法场景中，关注的是 **Advisor 的 order**——它决定了异步拦截在外层（先提交线程池），事务拦截在内层（在异步线程中开启事务）。

### 4.8 调试时的一个常见困惑：Method 对象的 declaringClass

> 💡 这是之前在调试 `@Scheduled + @Transactional` 时发现的现象，与循环依赖中的 `getEarlyBeanReference` 是同一套 AOP 代理机制。

在 `ScheduledMethodRunnable` 中调试时会发现：
- `target` = 代理对象（`MyService$$EnhancerBySpringCGLIB$$xxx`）✅
- `method` 的 `declaringClass` = **原始类**（`MyService.class`），不是代理类 ❓

这是因为 `AopUtils.selectInvocableMethod()` 的设计：

```java
// MethodIntrospector.selectInvocableMethod()
if (method.getDeclaringClass().isAssignableFrom(targetType)) {
    return method;  // ← 原始类.isAssignableFrom(CGLIB子类) = true，直接返回原始类的 Method
}
```

**不影响功能**：`method.invoke(proxy)` 时 Java 反射会走**多态分派**，实际调用代理类重写的方法 → AOP 拦截正常触发。

这个机制在循环依赖中同样适用——三级缓存的 `ObjectFactory` 调用 `getEarlyBeanReference()` 创建的代理对象，内部使用的也是相同的 CGLIB 多态机制。

#### 🔑 关键源码类
- `AbstractAutowireCapableBeanFactory.getEarlyBeanReference()`
- `AbstractAutoProxyCreator`
  - `getEarlyBeanReference()` —— 提前创建代理
  - `postProcessAfterInitialization()` —— 正常创建代理（带去重检查）
  - `earlyProxyReferences` Map —— 提前代理的记录
  - `wrapIfNecessary()` —— 代理创建的核心方法

#### 💡 调试建议
- 给 Man 或 WoMan 添加 `@Transactional` 方法，制造 AOP 代理场景
- 在 `getEarlyBeanReference()` 打断点，观察是否被调用
- 在 `AbstractAutoProxyCreator.postProcessAfterInitialization()` 打断点，观察 `earlyProxyReferences` 的去重判断
- 在 `wrapIfNecessary()` 打断点，确认代理只创建了一次

#### 📝 面试题
- AOP 代理和循环依赖是怎么配合的？
- `getEarlyBeanReference` 的作用是什么？
- 为什么 `@Async` 的循环依赖会失败？（引出 Sub-Step 5）

---

## Sub-Step 5：循环依赖的边界 —— 哪些场景解决不了 & 一致性检查

> 你之前在 circle.md 中已经记录了一部分，这里系统性展开。

### 5.1 不能解决的场景一：构造器注入循环依赖

```java
@Component
public class A {
    public A(B b) { this.b = b; }  // 构造器注入
}

@Component
public class B {
    public B(A a) { this.a = a; }  // 构造器注入
}
```

- 为什么不能解决？
  - 构造器注入时，实例化就需要依赖对象
  - 还没实例化完成，无法将半成品放入三级缓存
  - 因为三级缓存是在 `createBeanInstance()` **之后**放入的
- 源码中在哪里检测和抛异常？
  - `beforeSingletonCreation()` → `singletonsCurrentlyInCreation.add(beanName)` 返回 false → 抛异常
- 异常信息：`BeanCurrentlyInCreationException`
- **解决方案**：`@Lazy` 注解

```java
@Component
public class A {
    public A(@Lazy B b) { this.b = b; }  // 注入的是 B 的代理，不会立即触发 B 的创建
}
```

#### @Lazy 解决构造器循环依赖的原理
- `@Lazy` 会让 Spring 注入一个 **CGLIB 代理对象**
- 这个代理对象不会立即触发 `getBean("B")`
- 只有在真正调用代理的方法时，才会通过 `getBean()` 获取真正的 B
- 此时 A 已经创建完成，不存在循环依赖问题

### 5.2 不能解决的场景二：原型（Prototype）Bean 的循环依赖

```java
@Component
@Scope("prototype")
public class A {
    @Autowired private B b;
}

@Component
@Scope("prototype")
public class B {
    @Autowired private A a;
}
```

- 为什么不能解决？
  - 原型 Bean 每次 `getBean()` 都创建新实例
  - 三级缓存只给 Singleton Bean 用
  - `isPrototypeCurrentlyInCreation(beanName)` 检测到循环 → 直接抛异常
- 源码位置：`AbstractBeanFactory.doGetBean()` 开头的检查

### 5.3 不能解决的场景三：@Async + 循环依赖（一致性检查失败）

> 这是你之前在 circle.md 中记录的内容，现在从源码角度系统分析。

```java
@Service
public class ServiceA {
    @Autowired private ServiceB serviceB;
    
    @Async
    public void asyncMethod() {}
}

@Service
public class ServiceB {
    @Autowired private ServiceA serviceA;
}
```

### 5.4 一致性检查的源码逐行分析

```java
// doCreateBean() 中的一致性检查
if (earlySingletonExposure) {
    // ⭐ 注意：这里 allowEarlyReference = false
    //    只查一级和二级缓存，不查三级
    Object earlySingletonReference = getSingleton(beanName, false);
    
    if (earlySingletonReference != null) {
        // 有其他 Bean 已经通过早期引用拿到了这个 Bean
        
        if (exposedObject == bean) {
            // ⭐ exposedObject 没有被 BPP 替换（没有创建新代理）
            // 用早期引用替换（如果有提前代理，这里就是代理对象）
            exposedObject = earlySingletonReference;
        } else {
            // ⭐⭐⭐ 关键分支：exposedObject 被 BPP 替换了！
            // 说明 initializeBean 阶段创建了新的代理对象
            // 但其他 Bean 注入的是旧的早期引用
            // → 不一致！
            
            if (!this.allowRawInjectionDespiteWrapping) {
                // 检查有哪些 Bean 已经依赖了旧的早期引用
                String[] dependentBeans = getDependentBeans(beanName);
                Set<String> actualDependentBeans = new LinkedHashSet<>(dependentBeans.length);
                
                for (String dependentBean : dependentBeans) {
                    if (!removeSingletonIfCreatedForTypeCheckOnly(dependentBean)) {
                        actualDependentBeans.add(dependentBean);
                    }
                }
                
                if (!actualDependentBeans.isEmpty()) {
                    // ⭐ 抛出异常！
                    throw new BeanCurrentlyInCreationException(beanName, "...");
                }
            }
        }
    }
}
```

详细分析：
- `earlySingletonReference != null` 的含义：有 Bean 在创建过程中从二级缓存拿到了这个 Bean 的早期引用
- `exposedObject == bean` 的含义：`initializeBean()` 没有创建新的代理对象
- `exposedObject != bean` 的含义：`initializeBean()` 创建了新的代理对象（如 `@Async`）
- 为什么 `@Transactional` 不会走到 else 分支？因为 `AbstractAutoProxyCreator` 的 `getEarlyBeanReference` 和 `postProcessAfterInitialization` 有去重逻辑
- 为什么 `@Async` 会走到 else 分支？因为 `AsyncAnnotationBeanPostProcessor` 没有实现 `getEarlyBeanReference`，它总是在 `postProcessAfterInitialization` 创建新代理

### 5.5 `dependentBeanMap` 和 `dependenciesForBeanMap` 的关系

```java
// DefaultSingletonBeanRegistry.java

/** 记录：谁依赖了我 —— beanName → 依赖它的所有 Bean */
private final Map<String, Set<String>> dependentBeanMap = new ConcurrentHashMap<>(64);

/** 记录：我依赖了谁 —— beanName → 它依赖的所有 Bean */
private final Map<String, Set<String>> dependenciesForBeanMap = new ConcurrentHashMap<>(64);
```

- `registerDependentBean(dep, beanName)` 在哪里调用？
- 在循环依赖场景下，这两个 Map 的状态变化
- 一致性检查中 `getDependentBeans()` 查的是哪个 Map

### 5.6 `@Async` 循环依赖的解决方案

```
方案一：@Lazy 延迟注入
@Service
public class ServiceA {
    @Autowired @Lazy private ServiceB serviceB;
}

方案二：分离职责，消除循环依赖

方案三：使用事件驱动替代直接依赖
```

### 5.8 延伸：`@Async + @Scheduled` 的反面案例

> 💡 这是之前讨论过的一个重要知识点，与 `@Async` 循环依赖的根本原因相同。

`@Async` 和 `@Scheduled` **不应该**加到同一个方法上：

```java
// ❌ 反面案例
@Async
@Scheduled(fixedDelay = 5000)
public void doWork() { /* 耗时操作 */ }
```

**问题**：
1. `ScheduledMethodRunnable.run()` → `method.invoke(proxy)` → `AsyncExecutionInterceptor` 将任务提交到异步线程池后**立即返回 null**
2. 调度框架认为任务"秒完成"，`fixedDelay` 语义被彻底破坏（退化为 fixedRate）
3. 异常处理从 Spring Scheduling 的 `ErrorHandler` 体系**脱离**，转到 `@Async` 的 `AsyncUncaughtExceptionHandler`
4. 可能导致多个 `doWork()` 在异步线程池中**不可控并发**

**根本原因**与循环依赖中 `@Async` 失败的原因一致——`AsyncAnnotationBeanPostProcessor` 的代理创建机制和 Spring 的其他机制（三级缓存 / 调度框架）不完全兼容。

**正确做法**：分离调度层和异步层，或配置多线程 `TaskScheduler`。

### 5.7 `allowCircularReferences` 配置

```java
// AbstractAutowireCapableBeanFactory.java
private boolean allowCircularReferences = true;  // Spring 5.x 默认 true
```

- Spring Boot 2.x：默认 `true`，允许循环依赖
- Spring Boot 3.x：默认改为 `false`，**禁止循环依赖**！
- 可以通过 `spring.main.allow-circular-references=true` 开启

#### 🔑 关键源码类
- `AbstractAutowireCapableBeanFactory.doCreateBean()` —— 一致性检查
- `DefaultSingletonBeanRegistry`
  - `dependentBeanMap` / `dependenciesForBeanMap`
  - `registerDependentBean()`
  - `getDependentBeans()`
- `AsyncAnnotationBeanPostProcessor.postProcessAfterInitialization()` —— @Async 创建新代理

#### 💡 调试建议

**实验 1：构造器注入循环依赖（观察报错）**
```java
@Component
public class ConstructorA {
    public ConstructorA(ConstructorB b) {}
}
@Component
public class ConstructorB {
    public ConstructorB(ConstructorA a) {}
}
// 观察 BeanCurrentlyInCreationException 从哪里抛出
```

**实验 2：@Async + 循环依赖（观察一致性检查报错）**
```java
@Service
public class AsyncServiceA {
    @Autowired private AsyncServiceB b;
    @Async public void doSomething() {}
}
@Service
public class AsyncServiceB {
    @Autowired private AsyncServiceA a;
}
// 需要 @EnableAsync，观察一致性检查的 else 分支
```

**实验 3：@Transactional + 循环依赖（观察正常通过）**
```java
@Service
public class TxServiceA {
    @Autowired private TxServiceB b;
    @Transactional public void doSomething() {}
}
@Service
public class TxServiceB {
    @Autowired private TxServiceA a;
}
// 观察 getEarlyBeanReference 被调用，一致性检查通过
```

#### 📝 面试题
- Spring 解决不了哪些类型的循环依赖？为什么？
- 构造器注入的循环依赖为什么解决不了？@Lazy 怎么解决？
- @Async + 循环依赖为什么会报错？和 @Transactional 有什么区别？
- `doCreateBean()` 中的一致性检查逻辑是怎样的？
- Spring Boot 3.x 对循环依赖的态度有什么变化？

---

## Sub-Step 6：面试通关 & 总结

### 6.1 面试高频问题汇总（约 18 道）

#### 基础题
1. 什么是循环依赖？Spring 怎么解决的？
2. Spring 的三级缓存分别是什么？
3. 为什么需要三级缓存？两级行不行？
4. Bean 的创建过程中，三级缓存的状态是怎么变化的？

#### 进阶题
5. 请描述完整的循环依赖解决流程（从 getBean 开始）
6. 三级缓存的 ObjectFactory 什么时候被调用？调用后做了什么？
7. 构造器注入的循环依赖为什么解决不了？
8. @Lazy 怎么解决构造器注入的循环依赖？原理是什么？
9. Prototype Bean 的循环依赖为什么解决不了？

#### 高级题
10. AOP 代理和循环依赖是怎么配合的？getEarlyBeanReference 做了什么？
11. @Async 和 @Transactional 在循环依赖场景下表现为什么不同？
12. doCreateBean 中的一致性检查逻辑是怎样的？什么时候会抛异常？
13. earlyProxyReferences 这个 Map 的作用是什么？
14. Spring Boot 3.x 为什么默认禁止循环依赖？你怎么看？
15. 在实际项目中，你是怎么处理循环依赖的？（最佳实践）

#### 串联题（结合之前学习内容）
16. BPP 的 order 和 Advisor 的 order 有什么区别？各自影响什么？
17. `InfrastructureAdvisorAutoProxyCreator` 的 BPP order 默认值是什么？实际运行时是什么？为什么不同？（考察 `AopConfigUtils` 的 PropertyValues 覆盖机制）
18. `@Scheduled + @Transactional` 同一方法上事务能生效吗？和循环依赖有什么关系？（考察 `ScheduledMethodRunnable.target` = 代理对象 + `method.invoke(proxy)` 触发 AOP 拦截）

### 6.2 与之前学习内容的串联

```
┌─────────────────────────────────────────────────────────────────┐
│                    知识串联总览                                   │
│                                                                  │
│  Bean 生命周期                                                   │
│  ├── 实例化 → 三级缓存存入 ObjectFactory                         │
│  ├── 属性注入 → 触发循环依赖的位置                                │
│  ├── 初始化 → BPP 创建代理（正常路径）                            │
│  └── 一致性检查 → 验证代理对象一致性                              │
│                                                                  │
│  AOP 代理（之前学的 @Transactional / @Async）                    │
│  ├── AbstractAutoProxyCreator → 支持 getEarlyBeanReference      │
│  ├── AsyncAnnotationBPP → 不支持 → 循环依赖失败                  │
│  └── 代理创建时机：正常在 initializeBean，循环依赖时提前          │
│                                                                  │
│  BPP 执行顺序（Step 5 中讨论的）                                 │
│  ├── BPP 的 order → 决定谁先执行 postProcessAfterInitialization │
│  └── 影响 getEarlyBeanReference 中哪些 BPP 参与                  │
│                                                                  │
│  @Scheduled（之前学的）                                          │
│  ├── ScheduledAnnotationBPP 中传入的 bean 是代理对象             │
│  └── 因为 AOP BPP 先执行（BD 注册更早）                          │
│      和循环依赖中的"三级缓存提前代理"是同一个代理创建机制          │
└─────────────────────────────────────────────────────────────────┘
```

### 6.3 最佳实践

| # | 建议 | 原因 |
|---|------|------|
| 1 | 尽量避免循环依赖 | Spring Boot 3.x 默认禁止，循环依赖是设计问题的信号 |
| 2 | 优先使用构造器注入 | 强制依赖显式化，编译时就能发现循环依赖 |
| 3 | 用事件驱动解耦 | `ApplicationEventPublisher` 替代直接依赖 |
| 4 | 引入中间层打破循环 | A → C ← B，而不是 A ↔ B |
| 5 | 必要时用 @Lazy | 对构造器注入的循环依赖，@Lazy 是最简单的解决方案 |
| 6 | 不要依赖 allowCircularReferences | 这是兼容性开关，不是长期方案 |

### 6.4 一句话总结

> Spring 通过**三级缓存 + 提前暴露半成品对象**来解决 Setter/@Autowired 注入的单例 Bean 循环依赖。
> 三级缓存的核心价值是**延迟 AOP 代理的创建**，只在真正被循环引用时才提前创建代理。
> 构造器注入和原型 Bean 的循环依赖无法解决。

---

## 附录：关键源码文件索引

| 文件 | 路径 | 关键内容 |
|------|------|---------|
| DefaultSingletonBeanRegistry | spring-beans/.../factory/support/ | 三级缓存定义、getSingleton、addSingleton |
| AbstractBeanFactory | spring-beans/.../factory/support/ | doGetBean 入口 |
| AbstractAutowireCapableBeanFactory | spring-beans/.../factory/support/ | doCreateBean、populateBean、initializeBean、getEarlyBeanReference |
| AbstractAutoProxyCreator | spring-aop/.../framework/autoproxy/ | getEarlyBeanReference（提前代理）、earlyProxyReferences |
| AutowiredAnnotationBeanPostProcessor | spring-beans/.../factory/annotation/ | @Autowired 注入触发 getBean |
| AsyncAnnotationBeanPostProcessor | spring-context/.../scheduling/annotation/ | @Async 代理创建（不支持提前代理） |

---

## 附录：建议的调试步骤

### 第一轮：基础循环依赖（Man ↔ WoMan，无 AOP）
1. 在 `getSingleton(String, boolean)` 打断点
2. 在 `addSingletonFactory()` 打断点
3. 在 `populateBean()` 打断点
4. 在 `addSingleton()` 打断点
5. 跟踪整个流程，画出三级缓存的状态变迁图

### 第二轮：AOP + 循环依赖（给某个类加 @Transactional）
1. 在 `getEarlyBeanReference()` 打断点
2. 在 `AbstractAutoProxyCreator.getEarlyBeanReference()` 打断点
3. 在 `AbstractAutoProxyCreator.postProcessAfterInitialization()` 打断点
4. 观察 `earlyProxyReferences` 的去重逻辑
5. 观察一致性检查的 `exposedObject == bean` 分支

### 第三轮：@Async + 循环依赖（观察报错）
1. 创建 AsyncServiceA ↔ AsyncServiceB 的 Demo
2. 在 `doCreateBean()` 的一致性检查处打断点
3. 观察 `exposedObject != bean` 的分支
4. 观察 `getDependentBeans()` 返回的依赖列表
5. 观察异常抛出的过程

---

> 🎯 **学习目标**：学完这 6 个 Sub-Step 后，你应该能够：
> 1. 白板画出三级缓存的完整流程图
> 2. 说出三级缓存每一级存什么、什么时候存、什么时候取
> 3. 解释为什么需要三级而不是两级
> 4. 说出哪些场景循环依赖解决不了，以及为什么
> 5. 解释 @Transactional 和 @Async 在循环依赖中的不同表现
> 6. 在面试中流畅地回答 15 道高频问题
