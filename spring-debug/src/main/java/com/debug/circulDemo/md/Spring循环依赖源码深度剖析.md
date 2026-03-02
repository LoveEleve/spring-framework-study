# Spring 循环依赖源码深度剖析

> 从数据结构和方法调用链的角度，逐行剖析Spring循环依赖的解决机制。

---

## 一、核心数据结构详解

### 1.1 DefaultSingletonBeanRegistry 的字段定义

```java
// spring-beans/src/main/java/org/springframework/beans/factory/support/DefaultSingletonBeanRegistry.java

public class DefaultSingletonBeanRegistry extends SimpleAliasRegistry implements SingletonBeanRegistry {

    // ═══════════════════════════════════════════════════════════════════════
    // 核心缓存：三级缓存架构
    // ═══════════════════════════════════════════════════════════════════════
    
    /**
     * 一级缓存：单例池
     * - 存放完全初始化好的Bean（成品）
     * - key = beanName, value = bean实例
     * - 使用ConcurrentHashMap保证线程安全
     * - 初始容量256，避免频繁扩容
     */
    private final Map<String, Object> singletonObjects = new ConcurrentHashMap<>(256);
    
    /**
     * 三级缓存：单例工厂
     * - 存放ObjectFactory，延迟创建早期引用
     * - key = beanName, value = Lambda表达式
     * - 使用HashMap（非线程安全，因为操作都在synchronized块内）
     * - 初始容量16，通常只有少量Bean会触发
     */
    private final Map<String, ObjectFactory<?>> singletonFactories = new HashMap<>(16);
    
    /**
     * 二级缓存：早期单例对象
     * - 存放提前暴露的Bean（半成品，可能已被代理）
     * - key = beanName, value = 提前暴露的bean实例
     * - 使用ConcurrentHashMap保证读取的线程安全
     * - 初始容量16
     */
    private final Map<String, Object> earlySingletonObjects = new ConcurrentHashMap<>(16);
    
    /**
     * 已注册的单例名称集合
     * - 按注册顺序记录所有单例Bean的名称
     * - 使用LinkedHashSet保证顺序
     */
    private final Set<String> registeredSingletons = new LinkedHashSet<>(256);

    // ═══════════════════════════════════════════════════════════════════════
    // 循环依赖检测：正在创建中的Bean
    // ═══════════════════════════════════════════════════════════════════════
    
    /**
     * 正在创建中的单例名称集合
     * - 核心作用：检测循环依赖
     * - 使用Collections.newSetFromMap包装ConcurrentHashMap
     * - 当尝试重复添加时，add()返回false → 触发循环依赖检测
     */
    private final Set<String> singletonsCurrentlyInCreation =
            Collections.newSetFromMap(new ConcurrentHashMap<>(16));
    
    /**
     * 创建检查中已排除的单例名称集合
     * - 用于特殊场景，某些Bean不参与循环依赖检测
     */
    private final Set<String> inCreationCheckExclusions =
            Collections.newSetFromMap(new ConcurrentHashMap<>(16));

    // ═══════════════════════════════════════════════════════════════════════
    // 依赖关系管理
    // ═══════════════════════════════════════════════════════════════════════
    
    /**
     * 被依赖的Bean映射：bean name → 依赖它的Bean名称集合
     * - 用于确定Bean的销毁顺序
     * - 用于一致性检查
     */
    private final Map<String, Set<String>> dependentBeanMap = new ConcurrentHashMap<>(64);
    
    /**
     * 依赖的Bean映射：bean name → 它依赖的Bean名称集合
     * - 用于确定Bean的创建顺序
     */
    private final Map<String, Set<String>> dependenciesForBeanMap = new ConcurrentHashMap<>(64);
}
```

### 1.2 三级缓存的数据流向

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          Bean 创建过程                                        │
│                                                                              │
│   实例化                初始化                     完成                      │
│   (createBeanInstance)  (populateBean + initializeBean)  (addSingleton)     │
│                                                                              │
│   ┌─────────┐         ┌─────────────┐            ┌─────────────┐           │
│   │ 三级缓存 │ ──────> │  二级缓存   │ ─────────> │  一级缓存   │           │
│   │         │         │             │            │             │           │
│   │ObjectFac│         │早期引用对象 │            │  成品Bean   │           │
│   │  tory   │         │  (可能代理) │            │             │           │
│   └─────────┘         └─────────────┘            └─────────────┘           │
│        │                    ↑                        │                      │
│        │                    │                        │                      │
│        └────────────────────┘                        │                      │
│              循环依赖时触发                            │                      │
│              singletonFactory.getObject()            │                      │
│                                                       │                      │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 1.3 AbstractAutowireCapableBeanFactory 的关键字段

```java
// spring-beans/src/main/java/org/springframework/beans/factory/support/AbstractAutowireCapableBeanFactory.java

public abstract class AbstractAutowireCapableBeanFactory extends AbstractBeanFactory
        implements AutowireCapableBeanFactory {

    /**
     * 是否允许循环依赖
     * - Spring 5.x 默认 true
     * - Spring 6.x 默认 false（Spring Boot 3.x）
     * - 可通过 spring.main.allow-circular-references 配置
     */
    private boolean allowCircularReferences = true;

    /**
     * 是否允许在循环依赖中注入原始Bean（即使最终被包装）
     * - 默认 false
     * - 设为 true 会跳过一致性检查（不推荐）
     */
    private boolean allowRawInjectionDespiteWrapping = false;

    /**
     * 忽略依赖的接口类型
     * - 这些接口的依赖不通过@Autowired注入
     * - 由专门的BPP在特定时机注入（如Aware接口）
     */
    private final Set<Class<?>> ignoredDependencyInterfaces = new HashSet<>();
    
    // 初始化时注册的忽略接口
    public AbstractAutowireCapableBeanFactory() {
        super();
        ignoreDependencyInterface(BeanNameAware.class);
        ignoreDependencyInterface(BeanFactoryAware.class);
        ignoreDependencyInterface(BeanClassLoaderAware.class);
    }
}
```

---

## 二、核心方法逐行剖析

### 2.1 缓存查找方法：getSingleton(String, boolean)

这是三级缓存的核心方法，决定了从哪一级缓存获取Bean。

```java
// DefaultSingletonBeanRegistry.java 第210-244行

@Nullable
protected Object getSingleton(String beanName, boolean allowEarlyReference) {
    
    // ═══════════════════════════════════════════════════════════════════════
    // Step 1: 查询一级缓存（单例池）
    // ═══════════════════════════════════════════════════════════════════════
    Object singletonObject = this.singletonObjects.get(beanName);
    
    // ═══════════════════════════════════════════════════════════════════════
    // Step 2: 一级缓存没有 + 正在创建中 → 可能存在循环依赖
    // ═══════════════════════════════════════════════════════════════════════
    if (singletonObject == null && isSingletonCurrentlyInCreation(beanName)) {
        
        // Step 3: 查询二级缓存（早期引用）
        singletonObject = this.earlySingletonObjects.get(beanName);
        
        // ═══════════════════════════════════════════════════════════════════
        // Step 4: 二级缓存没有 + 允许早期引用 → 需要从三级缓存获取
        // ═══════════════════════════════════════════════════════════════════
        if (singletonObject == null && allowEarlyReference) {
            
            // ⭐⭐⭐ 加锁：Double-Check Locking模式
            synchronized (this.singletonObjects) {
                
                // Double-check: 再次检查一级缓存
                // 防止在进入锁之前，其他线程已经完成了Bean创建
                singletonObject = this.singletonObjects.get(beanName);
                if (singletonObject == null) {
                    
                    // Double-check: 再次检查二级缓存
                    // 防止在进入锁之前，其他线程已经完成了三级→二级升级
                    singletonObject = this.earlySingletonObjects.get(beanName);
                    if (singletonObject == null) {
                        
                        // ═══════════════════════════════════════════════════
                        // Step 5: 查询三级缓存
                        // ═══════════════════════════════════════════════════
                        ObjectFactory<?> singletonFactory = this.singletonFactories.get(beanName);
                        if (singletonFactory != null) {
                            
                            // ⭐⭐⭐ 核心：调用ObjectFactory.getObject()
                            // 这里会触发 getEarlyBeanReference()
                            // 如果有AOP，会在这里创建代理对象！
                            singletonObject = singletonFactory.getObject();
                            
                            // ⭐⭐⭐ 三级→二级升级
                            // 放入二级缓存，移除三级缓存
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

**参数 `allowEarlyReference` 的含义**：

| 调用位置 | 参数值 | 说明 |
|---------|--------|------|
| `doGetBean()` 入口 | `true` | 允许从三级缓存获取早期引用，解决循环依赖 |
| 一致性检查 | `false` | 只查一级和二级缓存，不触发ObjectFactory |

### 2.2 Bean创建包装方法：getSingleton(String, ObjectFactory)

这个方法负责管理Bean创建的生命周期状态。

```java
// DefaultSingletonBeanRegistry.java 第254-322行

public Object getSingleton(String beanName, ObjectFactory<?> singletonFactory) {
    Assert.notNull(beanName, "Bean name must not be null");
    
    // ═══════════════════════════════════════════════════════════════════════
    // Step 1: 加锁，保证单例创建的原子性
    // ═══════════════════════════════════════════════════════════════════════
    synchronized (this.singletonObjects) {
        
        // Step 2: 再次检查一级缓存
        Object singletonObject = this.singletonObjects.get(beanName);
        if (singletonObject == null) {
            
            // Step 3: 检查容器是否正在销毁
            if (this.singletonsCurrentlyInDestruction) {
                throw new BeanCreationNotAllowedException(beanName,
                    "Singleton bean creation not allowed while singletons of this factory are in destruction");
            }
            
            // ═══════════════════════════════════════════════════════════════
            // Step 4: ⭐⭐⭐ 标记Bean正在创建中
            // ═══════════════════════════════════════════════════════════════
            // 这里会检查 singletonsCurrentlyInCreation.add(beanName)
            // 如果返回false，说明已经存在 → 构造器循环依赖 → 抛异常
            beforeSingletonCreation(beanName);
            
            boolean newSingleton = false;
            boolean recordSuppressedExceptions = (this.suppressedExceptions == null);
            if (recordSuppressedExceptions) {
                this.suppressedExceptions = new LinkedHashSet<>();
            }
            
            try {
                // ═══════════════════════════════════════════════════════════
                // Step 5: ⭐⭐⭐ 调用ObjectFactory.getObject()
                // ═══════════════════════════════════════════════════════════
                // 这里会执行 createBean() → doCreateBean()
                singletonObject = singletonFactory.getObject();
                newSingleton = true;
                
            } catch (IllegalStateException ex) {
                // 处理并发创建的情况
                singletonObject = this.singletonObjects.get(beanName);
                if (singletonObject == null) {
                    throw ex;
                }
            } catch (BeanCreationException ex) {
                if (recordSuppressedExceptions) {
                    for (Exception suppressedException : this.suppressedExceptions) {
                        ex.addRelatedCause(suppressedException);
                    }
                }
                throw ex;
            } finally {
                if (recordSuppressedExceptions) {
                    this.suppressedExceptions = null;
                }
                
                // ═══════════════════════════════════════════════════════════
                // Step 6: ⭐⭐⭐ 取消创建中标记
                // ═══════════════════════════════════════════════════════════
                afterSingletonCreation(beanName);
            }
            
            // ═══════════════════════════════════════════════════════════════
            // Step 7: ⭐⭐⭐ 放入一级缓存，清除二级和三级缓存
            // ═══════════════════════════════════════════════════════════════
            if (newSingleton) {
                addSingleton(beanName, singletonObject);
            }
        }
        return singletonObject;
    }
}
```

**beforeSingletonCreation 和 afterSingletonCreation**：

```java
// DefaultSingletonBeanRegistry.java

protected void beforeSingletonCreation(String beanName) {
    // ⭐ 核心：将beanName加入 singletonsCurrentlyInCreation
    // 如果已经存在，add()返回false → 抛出异常
    // 这是检测构造器循环依赖的关键！
    if (!this.inCreationCheckExclusions.contains(beanName) 
            && !this.singletonsCurrentlyInCreation.add(beanName)) {
        throw new BeanCurrentlyInCreationException(beanName);
    }
}

protected void afterSingletonCreation(String beanName) {
    // ⭐ 从 singletonsCurrentlyInCreation 移除
    if (!this.inCreationCheckExclusions.contains(beanName) 
            && !this.singletonsCurrentlyInCreation.remove(beanName)) {
        throw new IllegalStateException("Singleton '" + beanName + "' isn't currently in creation");
    }
}
```

### 2.3 Bean创建主流程：doCreateBean()

这是Bean创建的核心方法，循环依赖的处理逻辑主要在这里。

```java
// AbstractAutowireCapableBeanFactory.java

protected Object doCreateBean(String beanName, RootBeanDefinition mbd, Object[] args) {
    
    // ═══════════════════════════════════════════════════════════════════════
    // Step 1: 实例化（调用构造方法）
    // ═══════════════════════════════════════════════════════════════════════
    BeanWrapper instanceWrapper = null;
    if (mbd.isSingleton()) {
        instanceWrapper = this.factoryBeanInstanceCache.remove(beanName);
    }
    if (instanceWrapper == null) {
        // ⭐ 创建实例（调用构造方法）
        // 如果是构造器注入，会在这里触发依赖Bean的创建
        instanceWrapper = createBeanInstance(beanName, mbd, args);
    }
    Object bean = instanceWrapper.getWrappedInstance();
    Class<?> beanType = instanceWrapper.getWrappedClass();
    
    // ═══════════════════════════════════════════════════════════════════════
    // Step 2: ⭐⭐⭐ 提前暴露半成品（解决循环依赖的关键！）
    // ═══════════════════════════════════════════════════════════════════════
    // 三个条件：
    // 1. mbd.isSingleton() - 必须是单例
    // 2. this.allowCircularReferences - 允许循环依赖
    // 3. isSingletonCurrentlyInCreation(beanName) - 正在创建中
    boolean earlySingletonExposure = (
            mbd.isSingleton() 
            && this.allowCircularReferences
            && isSingletonCurrentlyInCreation(beanName)
    );
    
    if (earlySingletonExposure) {
        if (logger.isTraceEnabled()) {
            logger.trace("Eagerly caching bean '" + beanName + 
                        "' to allow for resolving potential circular references");
        }
        
        // ⭐⭐⭐ 存入三级缓存！
        // 注意：这里存的是一个Lambda表达式，延迟执行
        // 只有在循环依赖触发时才会执行
        addSingletonFactory(beanName, () -> getEarlyBeanReference(beanName, mbd, bean));
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // Step 3: 初始化（属性注入 + 初始化方法）
    // ═══════════════════════════════════════════════════════════════════════
    Object exposedObject = bean;
    
    try {
        // ⭐ 属性注入（这里可能触发循环依赖！）
        populateBean(beanName, mbd, instanceWrapper);
        
        // ⭐ 初始化（BPP处理、InitializingBean、init-method）
        // ⭐ AOP代理在这里创建（postProcessAfterInitialization）
        exposedObject = initializeBean(beanName, exposedObject, mbd);
        
    } catch (Throwable ex) {
        // ...
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // Step 4: ⭐⭐⭐ 一致性检查（循环依赖的最后一道防线）
    // ═══════════════════════════════════════════════════════════════════════
    if (earlySingletonExposure) {
        // ⭐ 注意：allowEarlyReference=false
        // 只查一级和二级缓存，不触发ObjectFactory
        Object earlySingletonReference = getSingleton(beanName, false);
        
        if (earlySingletonReference != null) {
            // 有其他Bean已经通过早期引用拿到了这个Bean
            
            if (exposedObject == bean) {
                // 情况1：exposedObject没有被BPP替换
                // 使用二级缓存中的对象（可能是代理）
                exposedObject = earlySingletonReference;
                
            } else if (!this.allowRawInjectionDespiteWrapping) {
                // 情况2：exposedObject被BPP替换了！
                // 说明initializeBean阶段创建了新的代理对象
                // 但其他Bean注入的是旧的早期引用
                // → 不一致！需要检查是否有依赖的Bean
                
                String[] dependentBeans = getDependentBeans(beanName);
                Set<String> actualDependentBeans = new LinkedHashSet<>(dependentBeans.length);
                
                for (String dependentBean : dependentBeans) {
                    if (!removeSingletonIfCreatedForTypeCheckOnly(dependentBean)) {
                        actualDependentBeans.add(dependentBean);
                    }
                }
                
                if (!actualDependentBeans.isEmpty()) {
                    // ⭐⭐⭐ 抛出异常！
                    throw new BeanCurrentlyInCreationException(beanName,
                        "Bean with name '" + beanName + "' has been injected into other beans [" +
                        StringUtils.collectionToCommaDelimitedString(actualDependentBeans) +
                        "] in its raw version as part of a circular reference, but has eventually been " +
                        "wrapped. This means that said other beans do not use the final version of the " +
                        "bean. This is often the result of over-eager type matching - consider using " +
                        "'getBeanNamesForType' with the 'allowEagerInit' flag turned off, for example.");
                }
            }
        }
    }
    
    // Step 5: 注册DisposableBean
    try {
        registerDisposableBeanIfNecessary(beanName, bean, mbd);
    } catch (BeanDefinitionValidationException ex) {
        // ...
    }
    
    return exposedObject;
}
```

### 2.4 提前暴露方法：addSingletonFactory()

```java
// DefaultSingletonBeanRegistry.java 第178-187行

protected void addSingletonFactory(String beanName, ObjectFactory<?> singletonFactory) {
    Assert.notNull(singletonFactory, "Singleton factory must not be null");
    
    synchronized (this.singletonObjects) {
        // 只有当一级缓存中不存在时才添加
        if (!this.singletonObjects.containsKey(beanName)) {
            // ⭐ 存入三级缓存
            this.singletonFactories.put(beanName, singletonFactory);
            
            // ⭐ 移除二级缓存（如果存在）
            this.earlySingletonObjects.remove(beanName);
            
            // ⭐ 记录已注册
            this.registeredSingletons.add(beanName);
        }
    }
}
```

### 2.5 完成注册方法：addSingleton()

```java
// DefaultSingletonBeanRegistry.java 第155-168行

protected void addSingleton(String beanName, Object singletonObject) {
    synchronized (this.singletonObjects) {
        // ⭐ 放入一级缓存（最终存储位置）
        this.singletonObjects.put(beanName, singletonObject);
        
        // ⭐ 移除三级缓存
        this.singletonFactories.remove(beanName);
        
        // ⭐ 移除二级缓存
        this.earlySingletonObjects.remove(beanName);
        
        // ⭐ 记录已注册
        this.registeredSingletons.add(beanName);
    }
}
```

---

## 三、关键调用链分析

### 3.1 正常创建流程（无循环依赖）

```
getBean("serviceA")
    └── doGetBean("serviceA", ...)
        ├── getSingleton("serviceA") → null（一级缓存没有）
        ├── markBeanAsCreated("serviceA")
        └── getSingleton("serviceA", ObjectFactory)
            ├── beforeSingletonCreation("serviceA") → 加入singletonsCurrentlyInCreation
            ├── singletonFactory.getObject()
            │   └── createBean("serviceA", mbd, args)
            │       └── doCreateBean("serviceA", mbd, args)
            │           ├── createBeanInstance() → 实例化
            │           ├── addSingletonFactory() → 存入三级缓存
            │           ├── populateBean() → 属性注入（无循环依赖）
            │           ├── initializeBean() → 初始化
            │           └── 一致性检查 → getSingleton("serviceA", false) → null
            ├── afterSingletonCreation("serviceA") → 移除singletonsCurrentlyInCreation
            └── addSingleton("serviceA", object) → 存入一级缓存
```

### 3.2 循环依赖解决流程（A依赖B，B依赖A）

```
getBean("serviceA")
    └── doGetBean("serviceA", ...)
        ├── getSingleton("serviceA") → null
        └── getSingleton("serviceA", ObjectFactory)
            ├── beforeSingletonCreation("serviceA") → singletonsCurrentlyInCreation = {A}
            └── doCreateBean("serviceA", ...)
                ├── createBeanInstance() → A实例化完成
                ├── addSingletonFactory("serviceA", () -> getEarlyBeanReference(...))
                │   └── 三级缓存：singletonFactories = {A: ObjectFactory}
                └── populateBean() → 发现需要注入B
                    └── getBean("serviceB")
                        └── doGetBean("serviceB", ...)
                            ├── getSingleton("serviceB") → null
                            └── getSingleton("serviceB", ObjectFactory)
                                ├── beforeSingletonCreation("serviceB") → singletonsCurrentlyInCreation = {A, B}
                                └── doCreateBean("serviceB", ...)
                                    ├── createBeanInstance() → B实例化完成
                                    ├── addSingletonFactory("serviceB", ...)
                                    └── populateBean() → 发现需要注入A
                                        └── getBean("serviceA")
                                            └── doGetBean("serviceA", ...)
                                                └── getSingleton("serviceA", true)
                                                    ├── 一级缓存：null
                                                    ├── isSingletonCurrentlyInCreation("serviceA") → true
                                                    ├── 二级缓存：null
                                                    └── 三级缓存：命中！
                                                        ├── singletonFactory.getObject()
                                                        │   └── getEarlyBeanReference()
                                                        │       └── 可能创建代理对象
                                                        ├── earlySingletonObjects.put("serviceA", proxy)
                                                        └── singletonFactories.remove("serviceA")
                                                        → 返回A的早期引用
                                            → B注入A的早期引用
                                    ├── initializeBean() → B初始化完成
                                    └── 一致性检查 → null（B没有提前暴露）
                                ├── afterSingletonCreation("serviceB")
                                └── addSingleton("serviceB", B) → 存入一级缓存
                        → A注入B
                ├── initializeBean() → A初始化完成
                └── 一致性检查
                    ├── getSingleton("serviceA", false)
                    │   └── 二级缓存命中 → 返回A的早期引用
                    ├── exposedObject == bean?
                    │   └── 如果是，exposedObject = earlySingletonReference
                    └── 最终返回exposedObject
            ├── afterSingletonCreation("serviceA")
            └── addSingleton("serviceA", A) → 存入一级缓存
```

---

## 四、getEarlyBeanReference() 源码分析

### 4.1 方法定义

```java
// AbstractAutowireCapableBeanFactory.java

protected Object getEarlyBeanReference(String beanName, RootBeanDefinition mbd, Object bean) {
    Object exposedObject = bean;
    
    // 只有非合成Bean且有InstantiationAwareBPP时才处理
    if (!mbd.isSynthetic() && hasInstantiationAwareBeanPostProcessors()) {
        // 遍历所有 SmartInstantiationAwareBeanPostProcessor
        for (SmartInstantiationAwareBeanPostProcessor bp : getBeanPostProcessorCache().smartInstantiationAware) {
            // ⭐ 核心：调用 getEarlyBeanReference()
            // 主要是 AbstractAutoProxyCreator 实现
            exposedObject = bp.getEarlyBeanReference(exposedObject, beanName);
        }
    }
    return exposedObject;
}
```

### 4.2 AbstractAutoProxyCreator 的实现

```java
// AbstractAutoProxyCreator.java

private final Map<Object, Object> earlyProxyReferences = new ConcurrentHashMap<>(16);

@Override
public Object getEarlyBeanReference(Object bean, String beanName) {
    Object cacheKey = getCacheKey(bean.getClass(), beanName);
    
    // ⭐ 记录：这个Bean已经提前创建代理
    this.earlyProxyReferences.put(cacheKey, bean);
    
    // ⭐ 创建代理（如果需要）
    return wrapIfNecessary(bean, beanName, cacheKey);
}

@Override
public Object postProcessAfterInitialization(Object bean, String beanName) {
    if (bean != null) {
        Object cacheKey = getCacheKey(bean.getClass(), beanName);
        
        // ⭐⭐⭐ 关键检查：是否已经提前创建代理？
        if (this.earlyProxyReferences.remove(cacheKey) != bean) {
            // 没有提前创建 → 现在创建代理
            return wrapIfNecessary(bean, beanName, cacheKey);
        }
        // 已经提前创建 → 返回原始bean
        // 后续会在一致性检查中使用二级缓存中的代理对象
    }
    return bean;
}
```

### 4.3 earlyProxyReferences 的作用

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                  earlyProxyReferences 的作用                                 │
│                                                                              │
│   场景：ServiceA 有 @Transactional，与 ServiceB 循环依赖                      │
│                                                                              │
│   1. 实例化ServiceA                                                          │
│      └── 三级缓存：ObjectFactory                                             │
│                                                                              │
│   2. populateBean → 需要ServiceB                                             │
│                                                                              │
│   3. 创建ServiceB → 需要ServiceA                                             │
│      └── getSingleton("serviceA", true)                                     │
│          └── 调用 ObjectFactory.getObject()                                  │
│              └── getEarlyBeanReference()                                     │
│                  ├── earlyProxyReferences.put(A, 原始对象)  ← 记录           │
│                  └── wrapIfNecessary() → 创建代理                            │
│                                                                              │
│   4. ServiceB注入代理A                                                       │
│                                                                              │
│   5. 回到ServiceA的initializeBean()                                          │
│      └── postProcessAfterInitialization()                                    │
│          └── earlyProxyReferences.remove(A)                                  │
│              ├── 返回原始对象 → 说明已提前代理                                 │
│              └── 不再创建代理                                                 │
│                                                                              │
│   6. 一致性检查                                                               │
│      ├── getSingleton("serviceA", false) → 返回二级缓存的代理                 │
│      └── exposedObject = earlySingletonReference                             │
│                                                                              │
│   结论：确保代理对象只创建一次，且所有引用都指向同一个代理                       │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 五、缓存状态变迁详解

### 5.1 正常创建（无循环依赖）

```
时间线 ──────────────────────────────────────────────────────────────────────>

          │ 实例化后              │ populateBean后        │ initializeBean后
          │ addSingletonFactory() │                       │ addSingleton()
          ▼                       ▼                       ▼
一级缓存   │                       │                       │ ✅ Bean
──────────────────────────────────────────────────────────────────────────────
二级缓存   │                       │                       │ 
──────────────────────────────────────────────────────────────────────────────
三级缓存   │ ✅ ObjectFactory      │                       │ 
──────────────────────────────────────────────────────────────────────────────
创建中集合 │ ✅ beanName           │ ✅ beanName           │ 
──────────────────────────────────────────────────────────────────────────────

说明：ObjectFactory没有被调用，直接在initializeBean后移除
```

### 5.2 循环依赖场景

```
时间线 ──────────────────────────────────────────────────────────────────────>

          │ A实例化后            │ B需要A时              │ B创建完成         │ A创建完成
          │                     │ getSingleton(A,true)  │                   │ addSingleton(A)
          ▼                     ▼                       ▼                   ▼
一级缓存   │                     │                       │                   │ ✅ A
──────────────────────────────────────────────────────────────────────────────
二级缓存   │                     │ ✅ A的代理             │ ✅ A的代理         │ 
          │                     │ (三级→二级升级)        │                   │ 
──────────────────────────────────────────────────────────────────────────────
三级缓存   │ ✅ A的ObjectFactory │                       │                   │ 
          │                     │ (被移除)              │                   │ 
──────────────────────────────────────────────────────────────────────────────
创建中集合 │ ✅ A                │ ✅ A, B               │ ✅ A              │ 
──────────────────────────────────────────────────────────────────────────────
```

---

## 六、边界条件详解

### 6.1 构造器循环依赖

```java
// 为什么构造器循环依赖无法解决？

@Service
public class ServiceA {
    public ServiceA(ServiceB serviceB) {  // 构造器需要B
        this.serviceB = serviceB;
    }
}

@Service
public class ServiceB {
    public ServiceB(ServiceA serviceA) {  // 构造器需要A
        this.serviceA = serviceA;
    }
}

// 执行流程：
// 1. getBean("serviceA")
// 2. beforeSingletonCreation("serviceA") → singletonsCurrentlyInCreation = {A}
// 3. createBeanInstance() → 发现构造器需要B
// 4. getBean("serviceB")
// 5. beforeSingletonCreation("serviceB") → singletonsCurrentlyInCreation = {A, B}
// 6. createBeanInstance() → 发现构造器需要A
// 7. getBean("serviceA")
// 8. beforeSingletonCreation("serviceA")
//    └── singletonsCurrentlyInCreation.add("serviceA") 返回false！
//    └── 抛出 BeanCurrentlyInCreationException

// 关键：此时A还没有实例化完成，三级缓存还没有存入ObjectFactory
```

### 6.2 @Async 与循环依赖的冲突

```java
// @Async 使用的是 AsyncAnnotationBeanPostProcessor
// 它在 postProcessAfterInitialization 阶段创建代理
// 但不会实现 getEarlyBeanReference 方法

@Service
public class ServiceA {
    @Autowired private ServiceB serviceB;
    
    @Async  // ⚠️ 这里会创建代理，但不会提前暴露
    public void asyncMethod() {}
}

@Service
public class ServiceB {
    @Autowired private ServiceA serviceA;
}

// 执行流程：
// 1. A实例化 → 三级缓存存入ObjectFactory
// 2. A的populateBean → 需要B
// 3. B创建 → 需要A
// 4. getSingleton("serviceA", true) → 调用ObjectFactory
//    └── getEarlyBeanReference() → 返回原始对象（@Async不处理）
//    └── 二级缓存：原始对象
// 5. B注入A的原始对象
// 6. B创建完成
// 7. 回到A的initializeBean()
//    └── postProcessAfterInitialization()
//        └── @Async创建代理 → exposedObject = 代理对象
// 8. 一致性检查
//    ├── getSingleton("serviceA", false) → 返回二级缓存的原始对象
//    ├── exposedObject != bean → 进入不一致检查
//    └── 发现有依赖的Bean → 抛出异常！

// 解决方案：使用 @Lazy
@Service
public class ServiceB {
    @Autowired @Lazy
    private ServiceA serviceA;  // 注入代理，延迟获取
}
```

### 6.3 prototype Bean 的循环依赖

```java
// prototype Bean 不使用三级缓存，无法解决循环依赖

@Scope("prototype")
@Service
public class PrototypeA {
    @Autowired private PrototypeB prototypeB;
}

@Scope("prototype")
@Service
public class PrototypeB {
    @Autowired private PrototypeA prototypeA;
}

// 源码位置：AbstractBeanFactory.doGetBean()

if (isPrototypeCurrentlyInCreation(beanName)) {
    // ⭐ prototype Bean 直接抛异常
    throw new BeanCurrentlyInCreationException(beanName);
}

// prototype 的检测机制：
// ThreadLocal<Object> prototypesCurrentlyInCreation
// 每个线程独立记录正在创建的prototype Bean
```

---

## 七、总结

### 7.1 核心数据结构

| 数据结构 | 类型 | 作用 | 初始容量 |
|---------|------|------|---------|
| `singletonObjects` | `ConcurrentHashMap` | 一级缓存，成品Bean | 256 |
| `earlySingletonObjects` | `ConcurrentHashMap` | 二级缓存，早期引用 | 16 |
| `singletonFactories` | `HashMap` | 三级缓存，ObjectFactory | 16 |
| `singletonsCurrentlyInCreation` | `Set<String>` | 正在创建的Bean | 16 |
| `earlyProxyReferences` | `ConcurrentHashMap` | 已提前代理的Bean | 16 |

### 7.2 核心方法

| 方法 | 位置 | 作用 |
|------|------|------|
| `getSingleton(String, boolean)` | `DefaultSingletonBeanRegistry` | 三级缓存查找 |
| `getSingleton(String, ObjectFactory)` | `DefaultSingletonBeanRegistry` | Bean创建包装 |
| `doCreateBean()` | `AbstractAutowireCapableBeanFactory` | Bean创建主流程 |
| `getEarlyBeanReference()` | `AbstractAutowireCapableBeanFactory` | 提前暴露引用 |
| `addSingletonFactory()` | `DefaultSingletonBeanRegistry` | 存入三级缓存 |
| `addSingleton()` | `DefaultSingletonBeanRegistry` | 存入一级缓存 |

### 7.3 关键设计

1. **三级缓存的本质**：ObjectFactory 延迟执行，按需创建代理
2. **Double-Check Locking**：保证并发安全，避免重复创建代理
3. **earlyProxyReferences**：确保代理只创建一次
4. **一致性检查**：防止注入的引用与最终对象不一致
