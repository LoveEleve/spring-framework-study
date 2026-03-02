# Spring 循环依赖 - 自依赖详解

> 自依赖是循环依赖的一个特殊场景：Bean 注入自己。本文详细分析 Spring 如何处理自依赖。

---

## 一、什么是自依赖？

### 1.1 定义

自依赖是指一个 Bean 通过 `@Autowired` 注入自己：

```java
@Service
public class ServiceA {
    @Autowired
    private ServiceA self;  // 自己注入自己
}
```

### 1.2 自依赖的三种类型

```mermaid
flowchart TB
    subgraph 自依赖类型["自依赖的三种类型"]
        direction TB
        
        subgraph Type1["类型一：字段注入"]
            A1["@Autowired<br/>private ServiceA self;"]
            A2["✅ 可以解决"]
        end
        
        subgraph Type2["类型二：Setter注入"]
            B1["@Autowired<br/>public void setSelf(ServiceA self)"]
            B2["✅ 可以解决"]
        end
        
        subgraph Type3["类型三：构造器注入"]
            C1["public ServiceA(ServiceA self)"]
            C2["❌ 无法解决"]
        end
    end
    
    style A2 fill:#e8f5e9
    style B2 fill:#e8f5e9
    style C2 fill:#ffcccc
```

### 1.3 自依赖的实际应用场景

```java
@Service
public class OrderService {
    
    @Autowired
    private OrderService self;  // 注入自己
    
    @Transactional
    public void createOrder(Order order) {
        // 保存订单
        orderRepository.save(order);
        
        // ⭐ 通过 self 调用，触发新事务
        // 如果直接调用 processPayment()，不会走代理，事务不生效
        self.processPayment(order);
    }
    
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processPayment(Order order) {
        // REQUIRES_NEW：开启新事务
        // 即使外层事务回滚，这个方法的提交不受影响
        paymentService.charge(order);
    }
}
```

```mermaid
flowchart TB
    subgraph 自依赖的实际用途["自依赖的实际用途：解决同类方法调用事务失效"]
        direction TB
        
        A["问题：同类方法调用，事务不生效"] --> B["原因：this.method()<br/>绕过了代理对象"]
        B --> C["解决：self.method()<br/>通过代理对象调用"]
        C --> D["效果：事务传播正常工作"]
    end
    
    style B fill:#ffcccc
    style D fill:#e8f5e9
```

**为什么需要自依赖？**

```mermaid
flowchart LR
    subgraph 直接调用["直接调用 this.method()"]
        A1["调用方"] --> A2["OrderService代理"]
        A2 --> A3["TransactionInterceptor"]
        A3 --> A4["OrderService原始对象<br/>this.processPayment()"]
        A4 --> A5["❌ 绕过代理<br/>事务不生效!"]
    end
    
    subgraph 自依赖调用["自依赖调用 self.method()"]
        B1["调用方"] --> B2["OrderService代理"]
        B2 --> B3["TransactionInterceptor"]
        B3 --> B4["OrderService原始对象"]
        B4 --> B5["self.processPayment()"]
        B5 --> B6["OrderService代理"]
        B6 --> B7["TransactionInterceptor"]
        B7 --> B8["✅ 走代理<br/>事务生效!"]
    end
    
    style A5 fill:#ffcccc
    style B8 fill:#e8f5e9
```

---

## 二、自依赖能否被Spring解决？

### 2.1 答案

**✅ 字段注入和Setter注入的自依赖可以被Spring解决！**

**❌ 构造器注入的自依赖无法解决！**

### 2.2 对比表

| 注入方式 | 能否解决 | 原因 |
|---------|---------|------|
| `@Autowired` 字段注入 | ✅ 能 | 在 `populateBean()` 阶段注入，此时三级缓存已生效 |
| `@Autowired` Setter注入 | ✅ 能 | 同上 |
| 构造器注入 | ❌ 不能 | 在 `createBeanInstance()` 阶段就需要，三级缓存还未生效 |
| 构造器注入 + `@Lazy` | ✅ 能 | 注入代理对象，延迟获取 |

---

## 三、自依赖解决流程详解

### 3.1 完整执行流程

```mermaid
sequenceDiagram
    participant Client as 调用方
    participant Factory as BeanFactory
    participant Cache as 三级缓存
    participant A as ServiceA原始对象
    participant ProxyA as ServiceA代理对象
    
    Client->>Factory: 1. getBean("serviceA")
    Factory->>Factory: 2. beforeSingletonCreation("serviceA")<br/>singletonsCurrentlyInCreation = {A}
    
    Factory->>A: 3. 实例化ServiceA
    Factory->>Cache: 4. addSingletonFactory()<br/>三级缓存: {A: ObjectFactory}
    
    Factory->>Factory: 5. populateBean()<br/>发现需要注入 self (ServiceA)
    
    Factory->>Cache: 6. getSingleton("serviceA", true)
    Note over Cache: 一级缓存: null<br/>isSingletonCurrentlyInCreation(A): true<br/>二级缓存: null<br/>三级缓存: 命中!
    
    Cache->>Cache: 7. 调用 ObjectFactory.getObject()
    Cache->>Cache: 8. 执行 getEarlyBeanReference()
    Note over Cache: 如果有@Transactional等<br/>会创建代理对象
    
    Cache->>ProxyA: 9. 可能返回代理对象
    Cache->>Cache: 10. 三级→二级升级<br/>earlySingletonObjects.put("serviceA", proxy)<br/>singletonFactories.remove("serviceA")
    
    Cache-->>A: 11. 返回早期引用（可能是代理）
    Note over A: ⭐ self = 早期引用<br/>（自己注入自己！）
    
    A->>A: 12. initializeBean()<br/>初始化完成
    A->>Factory: 13. 一致性检查<br/>getSingleton("serviceA", false)
    
    Factory->>Factory: 14. afterSingletonCreation("serviceA")<br/>singletonsCurrentlyInCreation.remove(A)
    Factory->>Cache: 15. addSingleton()<br/>一级缓存: {A: finalBean}
    
    Cache-->>Client: 16. 返回ServiceA
```

### 3.2 关键步骤详解

#### 步骤1-3：创建开始

```
1. getBean("serviceA")
2. beforeSingletonCreation("serviceA")  → singletonsCurrentlyInCreation = {"serviceA"}
3. createBeanInstance() → 实例化 ServiceA
```

#### 步骤4：存入三级缓存

```java
// AbstractAutowireCapableBeanFactory.doCreateBean()
addSingletonFactory(beanName, () -> getEarlyBeanReference(beanName, mbd, bean));

// 此时状态：
// singletonFactories = {"serviceA": ObjectFactory}
// earlySingletonObjects = {}
// singletonObjects = {}
```

#### 步骤5-6：注入self时查找缓存

```
5. populateBean() → 发现字段 self 需要 ServiceA
6. getSingleton("serviceA", true)
   - singletonObjects.get("serviceA") → null（一级缓存没有）
   - isSingletonCurrentlyInCreation("serviceA") → true（正在创建中）
   - earlySingletonObjects.get("serviceA") → null（二级缓存没有）
   - singletonFactories.get("serviceA") → 命中！（三级缓存命中）
```

#### 步骤7-11：获取早期引用

```java
// 调用 ObjectFactory.getObject()
singletonObject = singletonFactory.getObject();
// 内部执行：getEarlyBeanReference()

// 如果有 @Transactional 等注解，会创建代理对象
// 返回的是代理对象或原始对象

// 三级→二级升级
earlySingletonObjects.put("serviceA", singletonObject);
singletonFactories.remove("serviceA");

// self 字段被赋值为早期引用
// 此时 self = 自己（原始对象或代理对象）
```

#### 步骤12-15：完成创建

```
12. initializeBean() → 初始化完成
13. 一致性检查 → getSingleton("serviceA", false)
14. afterSingletonCreation("serviceA") → 从创建集合移除
15. addSingleton() → 放入一级缓存
```

### 3.3 缓存状态变化

```mermaid
flowchart TB
    subgraph 自依赖缓存状态["自依赖缓存状态变化"]
        direction TB
        
        subgraph Step1["步骤1: 实例化后"]
            S1["一级缓存: 空"]
            S2["二级缓存: 空"]
            S3["三级缓存: ✅ ObjectFactory(A)"]
            S4["创建集合: {A}"]
        end
        
        subgraph Step2["步骤2: 注入self时"]
            T1["一级缓存: 空"]
            T2["二级缓存: ✅ 早期引用A"]
            T3["三级缓存: 空 (已移除)"]
            T4["创建集合: {A}"]
        end
        
        subgraph Step3["步骤3: 创建完成"]
            U1["一级缓存: ✅ 最终Bean A"]
            U2["二级缓存: 空 (已移除)"]
            U3["三级缓存: 空"]
            U4["创建集合: 空"]
        end
        
        Step1 --> Step2 --> Step3
    end
    
    style S3 fill:#e1f5ff
    style T2 fill:#fff4e6
    style U1 fill:#e8f5e9
```

---

## 四、关键问题：为什么自依赖不会触发重复创建检测？

### 4.1 两种getSingleton方法的区别

```mermaid
flowchart TB
    subgraph 两种getSingleton方法["两种getSingleton方法"]
        direction TB
        
        subgraph Method1["方法一：创建入口"]
            A["getSingleton(String beanName,<br/>ObjectFactory singletonFactory)"]
            A --> A1["⭐ 调用 beforeSingletonCreation()"]
            A1 --> A2["标记Bean正在创建中"]
            A2 --> A3["创建完成后调用 afterSingletonCreation()"]
        end
        
        subgraph Method2["方法二：缓存查找"]
            B["getSingleton(String beanName,<br/>boolean allowEarlyReference)"]
            B --> B1["❌ 不调用 beforeSingletonCreation()"]
            B1 --> B2["仅从三级缓存查找"]
            B2 --> B3["可能返回早期引用"]
        end
    end
    
    style A1 fill:#ffcccc
    style B1 fill:#e8f5e9
```

### 4.2 对比：普通循环依赖 vs 自依赖

```mermaid
flowchart TB
    subgraph 普通循环依赖["普通循环依赖 (A↔B)"]
        direction TB
        
        A1["getBean('A')"] --> A2["getSingleton(A, ObjectFactory)<br/>⭐ beforeSingletonCreation('A')<br/>创建集合 = {A}"]
        A2 --> A3["创建A实例"]
        A3 --> A4["populateBean需要B"]
        A4 --> A5["getBean('B')"]
        A5 --> A6["getSingleton(B, ObjectFactory)<br/>⭐ beforeSingletonCreation('B')<br/>创建集合 = {A, B}"]
        A6 --> A7["创建B实例"]
        A7 --> A8["populateBean需要A"]
        A8 --> A9["getSingleton('A', true)<br/>❌ 不调用beforeSingletonCreation<br/>直接查缓存"]
    end
    
    subgraph 自依赖["自依赖 (A→A)"]
        direction TB
        
        B1["getBean('A')"] --> B2["getSingleton(A, ObjectFactory)<br/>⭐ beforeSingletonCreation('A')<br/>创建集合 = {A}"]
        B2 --> B3["创建A实例"]
        B3 --> B4["populateBean需要A"]
        B4 --> B5["getSingleton('A', true)<br/>❌ 不调用beforeSingletonCreation<br/>直接查缓存"]
    end
    
    style A9 fill:#e8f5e9
    style B5 fill:#e8f5e9
```

### 4.3 关键结论

| 方法 | 是否调用beforeSingletonCreation | 用途 |
|------|-------------------------------|------|
| `getSingleton(String, ObjectFactory)` | ✅ 调用 | 创建Bean的入口 |
| `getSingleton(String, boolean)` | ❌ 不调用 | 仅从缓存获取 |

自依赖时，第二次获取A走的是 **`getSingleton(String, boolean)`**，只查缓存，不会重复调用 `beforeSingletonCreation`！

---

## 五、自依赖 + AOP代理

### 5.1 场景示例

```java
@Service
public class ServiceA {
    @Autowired
    private ServiceA self;  // 注入自己
    
    @Transactional
    public void doSomething() {
        // 通过 self 调用，事务生效
        self.internalMethod();
    }
    
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void internalMethod() {
        // REQUIRES_NEW：新事务
    }
}
```

### 5.2 代理对象创建流程

```mermaid
flowchart TB
    subgraph 自依赖AOP代理["自依赖 + AOP代理"]
        direction TB
        
        subgraph 创建过程["创建过程"]
            A1["创建ServiceA实例"] --> A2["三级缓存: ObjectFactory<br/>() -> getEarlyBeanReference()"]
            A2 --> A3["populateBean注入self"]
            A3 --> A4["getSingleton触发"]
            A4 --> A5["getEarlyBeanReference()"]
            A5 --> A6["AbstractAutoProxyCreator<br/>wrapIfNecessary()"]
            A6 --> A7["创建代理对象"]
            A7 --> A8["self = 代理对象"]
        end
        
        subgraph 最终结构["最终对象结构"]
            B1["ServiceA代理对象"] --> B2["advisors: [TransactionInterceptor]"]
            B1 --> B3["target: ServiceA原始对象"]
            B3 --> B4["self: ServiceA代理对象<br/>⭐ 指向自己(代理)"]
        end
    end
    
    style A6 fill:#fff4e6
    style B4 fill:#e8f5e9
```

### 5.3 对象内存结构

```
┌─────────────────────────────────────────────────────────────────┐
│                    ServiceA$$EnhancerBySpringCGLIB              │
│                         （代理对象）                              │
├─────────────────────────────────────────────────────────────────┤
│  advisors: [TransactionInterceptor, ...]                        │
│  target: ──────────────────────────────────────┐                │
│                                                  │               │
└──────────────────────────────────────────────────│───────────────┘
                                                   │
                                                   ▼
┌─────────────────────────────────────────────────────────────────┐
│                      ServiceA（原始对象）                         │
├─────────────────────────────────────────────────────────────────┤
│  self: ────────────────────────────────────────┐               │
│                                                  │               │
│  @Transactional                                  │               │
│  public void doSomething() { ... }               │               │
│                                                  │               │
│  @Transactional(propagation = REQUIRES_NEW)      │               │
│  public void internalMethod() { ... }            │               │
│                                                  │               │
└──────────────────────────────────────────────────│───────────────┘
                                                   │
                                                   │
                                                   └──────► 指向代理对象
```

### 5.4 earlyProxyReferences的作用

```mermaid
flowchart TB
    subgraph earlyProxyReferences机制["earlyProxyReferences 确保代理只创建一次"]
        direction TB
        
        subgraph 第一次["第一次：注入self时"]
            A1["getEarlyBeanReference()"] --> A2["earlyProxyReferences.put(A, 原始对象)"]
            A2 --> A3["wrapIfNecessary() 创建代理"]
            A3 --> A4["返回代理对象"]
        end
        
        subgraph 第二次["第二次：initializeBean时"]
            B1["postProcessAfterInitialization()"] --> B2["earlyProxyReferences.remove(A)"]
            B2 --> B3{"返回值 == 原始对象?"}
            B3 -->|是| B4["已提前代理<br/>跳过创建"]
            B3 -->|否| B5["创建代理"]
        end
        
        A4 --> B1
    end
    
    style A2 fill:#e1f5ff
    style B4 fill:#e8f5e9
```

---

## 六、自依赖无法解决的场景

### 6.1 构造器自依赖

```java
@Service
public class ServiceA {
    private final ServiceA self;
    
    public ServiceA(ServiceA self) {  // ❌ 构造器自依赖
        this.self = self;
    }
}
```

```mermaid
sequenceDiagram
    participant Factory as BeanFactory
    participant A as ServiceA
    participant CreationSet as singletonsCurrentlyInCreation
    
    Factory->>CreationSet: 1. add("serviceA") ✅
    Factory->>A: 2. createBeanInstance()
    A->>Factory: 3. 构造器需要 ServiceA
    Factory->>Factory: 4. getBean("serviceA")
    Factory->>CreationSet: 5. add("serviceA") ❌ 已存在!
    CreationSet-->>Factory: 返回false
    Factory->>Factory: 6. 抛出 BeanCurrentlyInCreationException
    
    style CreationSet fill:#ffcccc
```

**原因**：构造器自依赖在实例化阶段就需要自己，但此时三级缓存还未生效。

### 6.2 自依赖 + @Async

```java
@Service
public class ServiceA {
    @Autowired
    private ServiceA self;
    
    @Async  // ⚠️ 不支持提前代理
    public void asyncMethod() {}
}
```

```mermaid
flowchart TB
    subgraph Async失败原因["@Async自依赖失败原因"]
        direction TB
        
        A["@Async使用AsyncAnnotationBeanPostProcessor"] --> B["未实现getEarlyBeanReference()"]
        B --> C["注入self时返回原始对象"]
        C --> D["initializeBean时创建新代理"]
        D --> E["exposedObject != bean"]
        E --> F["一致性检查失败"]
        F --> G["抛出异常!"]
    end
    
    style B fill:#ffcccc
    style G fill:#ffcccc
```

**原因**：与普通循环依赖相同，`@Async` 的 BPP 没有实现 `getEarlyBeanReference()`。

### 6.3 Prototype自依赖

```java
@Scope("prototype")
@Service
public class ServiceA {
    @Autowired
    private ServiceA self;  // ❌ Prototype不支持
}
```

**原因**：Prototype Bean不使用三级缓存，每次获取都创建新实例。

---

## 七、自依赖解决方案

### 7.1 方案一：使用@Lazy（推荐）

```java
@Service
public class ServiceA {
    @Autowired
    @Lazy
    private ServiceA self;  // 注入代理，延迟获取
}
```

### 7.2 方案二：使用ApplicationContext

```java
@Service
public class ServiceA implements ApplicationContextAware {
    
    private ApplicationContext applicationContext;
    
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }
    
    public void doSomething() {
        // 手动获取代理对象
        ServiceA self = applicationContext.getBean(ServiceA.class);
        self.internalMethod();
    }
}
```

### 7.3 方案三：使用AopContext（不推荐）

```java
@Service
public class ServiceA {
    
    public void doSomething() {
        // 通过AopContext获取当前代理对象
        // 需要配置：@EnableAspectJAutoProxy(exposeProxy = true)
        ServiceA self = (ServiceA) AopContext.currentProxy();
        self.internalMethod();
    }
}
```

### 7.4 方案对比

```mermaid
flowchart TB
    subgraph 自依赖解决方案对比["自依赖解决方案对比"]
        direction TB
        
        subgraph 方案一["@Autowired @Lazy（推荐）"]
            A1["简单方便"] --> A2["Spring原生支持"]
            A2 --> A3["延迟加载"]
        end
        
        subgraph 方案二["ApplicationContext.getBean()"]
            B1["灵活可控"] --> B2["需要实现Aware接口"]
            B2 --> B3["与Spring耦合"]
        end
        
        subgraph 方案三["AopContext.currentProxy()"]
            C1["直接获取代理"] --> C2["需要额外配置"]
            C2 --> C3["侵入性较强"]
        end
    end
    
    style A1 fill:#e8f5e9
    style C3 fill:#ffcccc
```

| 方案 | 优点 | 缺点 | 推荐度 |
|------|------|------|--------|
| `@Autowired @Lazy` | 简单、原生支持 | 无 | ⭐⭐⭐⭐⭐ |
| `ApplicationContext.getBean()` | 灵活 | 与Spring耦合 | ⭐⭐⭐ |
| `AopContext.currentProxy()` | 直接获取代理 | 需要配置、侵入性强 | ⭐⭐ |

---

## 八、总结

### 8.1 核心要点

```mermaid
mindmap
  root((自依赖))
    能否解决
      字段注入 ✅
      Setter注入 ✅
      构造器注入 ❌
    解决原理
      三级缓存
      提前暴露
      早期引用
    关键区别
      getSingleton两种方法
      beforeSingletonCreation
      创建集合检测
    实际应用
      同类方法调用
      事务传播
      代理对象获取
```

### 8.2 对比总结

| 场景 | 能否解决 | 关键原因 |
|------|---------|---------|
| 字段注入自依赖 | ✅ 能 | populateBean阶段，三级缓存已生效 |
| Setter注入自依赖 | ✅ 能 | 同上 |
| 构造器注入自依赖 | ❌ 不能 | createBeanInstance阶段，三级缓存未生效 |
| 自依赖 + @Transactional | ✅ 能 | AbstractAutoProxyCreator支持提前代理 |
| 自依赖 + @Async | ❌ 不能 | AsyncAnnotationBPP不支持提前代理 |
| Prototype自依赖 | ❌ 不能 | 不使用三级缓存 |

### 8.3 面试高频问题

**Q1：自依赖能否被Spring解决？**

**Q2：自依赖和普通循环依赖的解决流程有什么区别？**

**Q3：为什么自依赖不会触发BeanCurrentlyInCreationException？**

**Q4：自依赖有什么实际应用场景？**

**Q5：自依赖 + @Async为什么会失败？如何解决？**
