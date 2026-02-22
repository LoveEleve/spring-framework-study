# Spring AOP 性能优化与对象创建详解

## 一、fixedInterceptorMap - 拦截器链缓存

### 1.1 基本概念

```java
private transient Map<Method, Integer> fixedInterceptorMap;
```

**作用**：缓存**方法 → callbacks数组索引**的映射，避免重复计算拦截器链。

### 1.2 为什么 value 是 Integer？

这个 Integer 表示**该方法在 callbacks 数组中使用的固定拦截器索引**。

#### callbacks 数组完整结构

```
索引  内容                                          说明
─────────────────────────────────────────────────────────────
0     DynamicAdvisedInterceptor                    动态拦截器（通用）
1     InvokeTargetInterceptor                      直接调用目标
2     NoOp.INSTANCE                                无操作
3     DispatcherTargetSource                       目标分派器
4     AdvisedDispatcher                            Advised接口处理
5     EqualsInterceptor                            equals方法处理
6     HashCodeInterceptor                          hashCode方法处理
7     固定拦截器1（如saveUser专属）                  ★ 固定拦截器起始
8     固定拦截器2（如updateUser专属）
9     固定拦截器3（如deleteUser专属）
...   更多固定拦截器
```

### 1.3 缓存示例

```java
// 假设 UserService 有3个方法，经过分析后：
fixedInterceptorMap = {
    UserService.saveUser()    -> 7,   // saveUser使用callbacks[7]
    UserService.updateUser()  -> 8,   // updateUser使用callbacks[8]
    UserService.deleteUser()  -> 9    // deleteUser使用callbacks[9]
};
```

### 1.4 性能对比

#### 无缓存时（每次调用都计算）

```java
public Object intercept(Object proxy, Method method, Object[] args, MethodProxy mp) {
    // 每次都要执行这个复杂计算！
    List<Object> chain = advisorChainFactory
        .getInterceptorsAndDynamicInterceptionAdvice(method, targetClass);
    
    // 遍历所有Advisor，检查MethodMatcher...
    // 时间复杂度：O(N)，N为Advisor数量
}
```

#### 有缓存时（O(1)直接获取）

```java
public Object intercept(Object proxy, Method method, Object[] args, MethodProxy mp) {
    // 先从缓存获取
    Integer cachedIndex = fixedInterceptorMap.get(method);
    
    if (cachedIndex != null) {
        // 直接使用缓存的拦截器！O(1)
        return callbacks[cachedIndex].intercept(proxy, method, args, mp);
    }
    
    // 缓存未命中，才进行计算
    List<Object> chain = advisorChainFactory
        .getInterceptorsAndDynamicInterceptionAdvice(method, targetClass);
    // ...
}
```

### 1.5 什么方法会被缓存？

**固定拦截器（Fixed Interceptor）适用的场景**：

1. **没有参数的方法** - 参数不影响拦截器链
2. **非桥接方法** - 泛型桥接方法需要动态处理
3. **匹配规则简单的方法** - 如完全匹配所有Advisor或完全不匹配

**不会被缓存的场景**：

```java
// 参数影响匹配结果的方法不会被缓存
@Before("args(param)")  // 参数值影响切点匹配
public void before(JoinPoint jp, Object param) { }

// 这种每次都要动态计算
```

---

## 二、fixedInterceptorOffset - 固定拦截器偏移量

### 2.1 基本概念

```java
private transient int fixedInterceptorOffset;
```

**作用**：标记**固定拦截器在 callbacks 数组中的起始位置**。

### 2.2 为什么要分动态和固定？

| 类型 | 范围 | 特点 | 用途 |
|------|------|------|------|
| 动态拦截器 | 0-6 | 所有方法共享，逻辑固定 | 通用处理（AOP链、equals等） |
| 固定拦截器 | 7+ | 每个方法专属，预计算好 | 特定方法的优化处理 |

### 2.3 初始化过程

```java
// 初始时，固定拦截器从索引7开始
private transient int fixedInterceptorOffset = 7;

// 当发现一个新方法可以缓存时
protected void addFixedInterceptor(Method method, Interceptor interceptor) {
    // 1. 将拦截器放入callbacks数组
    callbacks[fixedInterceptorOffset] = interceptor;
    
    // 2. 记录方法到索引的映射
    fixedInterceptorMap.put(method, fixedInterceptorOffset);
    
    // 3. 偏移量加1，为下一个方法准备
    fixedInterceptorOffset++;
}
```

### 2.4 实际例子

```java
// 分析 UserService 的5个方法
public class UserService {
    public void save() { }      // 可以缓存（无参数，匹配固定）
    public void update() { }    // 可以缓存
    public void delete() { }    // 可以缓存
    public void query(String s) { }  // 不能缓存（参数影响匹配）
    public void export() { }    // 可以缓存
}

// 最终状态
fixedInterceptorOffset = 10;  // 使用了7,8,9三个位置

fixedInterceptorMap = {
    save()   -> 7,
    update() -> 8,
    delete() -> 9
    // query() 不在缓存中
    // export() -> 如果后来加入，可能是10
};
```

---

## 三、objenesis - 无构造创建对象

### 3.1 问题背景

#### 传统创建方式的问题

```java
public class UserService {
    private DataSource dataSource;
    
    // 只有带参数的构造函数
    public UserService(DataSource ds) {
        this.dataSource = ds;
        // 复杂的初始化逻辑
        initializeConnectionPool();
        warmUpCache();
    }
}

// 尝试创建CGLIB代理
Enhancer enhancer = new Enhancer();
enhancer.setSuperclass(UserService.class);
enhancer.setCallbacks(callbacks);

// 报错！因为没有默认构造函数
Object proxy = enhancer.create();  
// throws IllegalArgumentException: Superclass has no null constructors
```

### 3.2 Objenesis 解决方案

**核心原理**：使用底层机制（如`sun.misc.Unsafe`）直接分配内存，**绕过构造函数**。

```java
// 使用 Objenesis 创建实例
SpringObjenesis objenesis = new SpringObjenesis();

// 不调用任何构造函数！
UserService proxy = objenesis.newInstance(UserService.class);

// 创建成功，但字段都是默认值
dataSource = null;  // 没有执行构造函数，所以是null
```

### 3.3 为什么可以这样做？

**代理对象的特点**：
1. **不需要初始化字段** - 代理对象只是一个"壳"，真正的逻辑通过`TargetSource`委托给目标对象
2. **方法被拦截重写** - 所有业务方法都被CGLIB重写了，不依赖父类字段
3. **CGLIB回调机制** - 通过`MethodInterceptor`拦截调用

### 3.4 实际使用代码

```java
class ObjenesisCglibAopProxy extends CglibAopProxy {
    
    private static final SpringObjenesis objenesis = new SpringObjenesis();
    
    @Override
    protected Object createProxyClassAndInstance(Enhancer enhancer, Callback[] callbacks) {
        // 1. 生成代理类的Class对象
        Class<?> proxyClass = enhancer.createClass();
        
        Object proxyInstance = null;
        
        // 2. 尝试使用 Objenesis 创建实例（不调用构造函数）
        if (objenesis.isWorthTrying()) {
            try {
                proxyInstance = objenesis.newInstance(proxyClass, enhancer.getUseCache());
            } catch (Throwable ex) {
                // 失败则回退到传统方式
                logger.debug("Objenesis失败，使用传统方式", ex);
            }
        }
        
        // 3. 如果 Objenesis 失败，回退到反射调用构造函数
        if (proxyInstance == null) {
            Constructor<?> ctor = proxyClass.getDeclaredConstructor();
            ReflectionUtils.makeAccessible(ctor);
            proxyInstance = ctor.newInstance();
        }
        
        // 4. 设置CGLIB回调
        ((Factory) proxyInstance).setCallbacks(callbacks);
        
        return proxyInstance;
    }
}
```

### 3.5 底层实现原理

Objenesis 根据JVM类型选择不同策略：

```java
public class SpringObjenesis {
    
    private final ObjenesisStd objenesis;
    
    public SpringObjenesis() {
        // 自动检测JVM类型，选择最佳策略
        this.objenesis = new ObjenesisStd();
    }
    
    public <T> T newInstance(Class<T> clazz, boolean useCache) {
        // 底层可能使用：
        // 1. sun.misc.Unsafe.allocateInstance() - JDK通用
        // 2. ReflectionFactory.newConstructorForSerialization() - 特定JDK
        // 3. 其他JVM特定机制
        
        return objenesis.newInstantiatorOf(clazz).newInstance();
    }
}
```

### 3.6 内存布局对比

#### 传统方式创建的对象

```
UserService实例（传统new创建）
├── dataSource = 有效的DataSource对象  ← 构造函数中初始化
├── connectionPool = 已初始化的连接池
├── cache = 已预热的缓存
└── ... 其他字段都已初始化
```

#### Objenesis创建的对象

```
UserService$$EnhancerByCGLIB实例（Objenesis创建）
├── dataSource = null                   ← 未执行构造函数
├── connectionPool = null               ← 所有字段都是默认值
├── cache = null
├── CGLIB$CALLBACK_0 = DynamicAdvisedInterceptor  ← 只有CGLIB回调被设置
└── ... 其他CGLIB内部字段
```

**关键点**：
- 代理对象的字段值不重要（因为方法都被重写了）
- 真正的业务逻辑通过`TargetSource.getTarget()`获取真实目标对象执行

---

## 四、三者协作关系

```
创建代理时：
1. 分析目标类的所有方法
   ├─ 可缓存的方法 → 创建固定拦截器 → 放入callbacks[7+]
   │                 记录到 fixedInterceptorMap
   │                 fixedInterceptorOffset++
   │
   └─ 不可缓存的方法 → 使用动态拦截器 callbacks[0]

2. 创建代理实例
   └─ 使用 objenesis.newInstance() 绕过构造函数

调用方法时：
1. 检查 fixedInterceptorMap
   ├─ 有缓存 → 直接使用 callbacks[cachedIndex]  O(1)
   │
   └─ 无缓存 → 使用 callbacks[0] 动态计算拦截器链  O(N)
```

---

## 五、总结

| 属性 | 类型 | 作用 | 关键值说明 |
|------|------|------|-----------|
| `fixedInterceptorMap` | `Map<Method, Integer>` | 缓存方法到拦截器的映射 | Integer是callbacks数组索引 |
| `fixedInterceptorOffset` | `int` | 固定拦截器的起始位置 | 默认为7，动态增长 |
| `objenesis` | `SpringObjenesis` (static) | 绕过构造函数创建实例 | 使用Unsafe等底层机制 |

### 设计亮点

1. **空间换时间**：用额外的内存（cached拦截器）换取执行速度
2. **延迟初始化**：固定拦截器在首次分析时创建，不是所有方法都缓存
3. **兼容性**：Objenesis保证了对无默认构造函数类的支持
4. **回退机制**：Objenesis失败时自动回退到反射调用构造函数
