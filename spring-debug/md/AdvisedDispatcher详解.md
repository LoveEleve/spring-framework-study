# AdvisedDispatcher 详解

## 1. 背景：Advised 接口

代理对象不仅保留了原始类的功能，还**额外实现了一个 `Advised` 接口**：

```java
// 原始类
public class UserService {
    public void saveUser() { }
}

// CGLIB生成的代理类（简化示意）
public class UserService$$EnhancerByCGLIB extends UserService implements Advised {
    // 这就是为什么代理对象可以强转为 Advised
}
```

### Advised 接口定义

```java
public interface Advised {
    TargetSource getTargetSource();           // 获取目标对象源
    void addAdvisor(Advisor advisor);         // 添加通知
    void removeAdvisor(int index);            // 移除通知
    Advisor[] getAdvisors();                  // 获取所有通知
    void setTargetSource(TargetSource targetSource);  // 更换目标源
    // ... 其他配置方法
}
```

## 2. 实际使用场景

拿到代理对象后，可以强转为 `Advised` 进行动态配置：

```java
// 从Spring容器获取（实际拿到的是代理对象）
UserService userService = applicationContext.getBean(UserService.class);

// 强转为 Advised
Advised advised = (Advised) userService;

// 查看当前代理配置
System.out.println("通知数量：" + advised.getAdvisors().length);

// 动态添加新的通知
advised.addAdvisor(new MyNewAdvisor());

// 更换目标对象
advised.setTargetSource(new SingletonTargetSource(newAnotherUserService));
```

## 3. 核心问题：这些方法如何被处理？

代理对象的方法分两类：

| 方法类型 | 示例 | 处理方式 |
|---------|------|---------|
| 普通业务方法 | `saveUser()` | 走 AOP 拦截链（检查@Before/@After等） |
| Advised接口方法 | `getTargetSource()` | **直接返回配置对象**（不走拦截链） |

**为什么 Advised 方法不走拦截链？**
- 性能考虑：不需要检查通知
- 逻辑清晰：直接操作配置对象
- 避免循环：防止拦截器链中的无限递归

## 4. AdvisedDispatcher 的作用

`AdvisedDispatcher` 是专门处理 **Advised 接口方法调用** 的回调器。

### 类定义

```java
private static class AdvisedDispatcher implements Dispatcher {
    
    private final AdvisedSupport advised;
    
    public AdvisedDispatcher(AdvisedSupport advised) {
        this.advised = advised;
    }
    
    @Override
    public Object loadObject() {
        // 直接返回配置对象
        return this.advised;
    }
}
```

### 与其他 Callback 的区别

```java
// CGLIB 回调数组（共7个）
Callback[] callbacks = new Callback[] {
    new DynamicAdvisedInterceptor(this.advised),   // 索引0：普通方法拦截
    new InvokeTargetInterceptor(),                  // 索引1：直接调用目标
    NoOp.INSTANCE,                                  // 索引2：无操作
    new DispatcherTargetSource(),                   // 索引3：目标分派
    new AdvisedDispatcher(this.advised),           // 索引4：★ Advised方法
    new EqualsInterceptor(),                        // 索引5：equals方法
    new HashCodeInterceptor()                       // 索引6：hashCode方法
};
```

| Callback | 类型 | 作用 |
|---------|------|------|
| `DynamicAdvisedInterceptor` | `MethodInterceptor` | 拦截普通方法，走AOP链 |
| `AdvisedDispatcher` | `Dispatcher` | **直接返回配置对象** |

**关键区别**：
- `MethodInterceptor`：拦截方法，可以控制是否执行原方法
- `Dispatcher`：直接返回一个对象，方法调用在返回的对象上执行

## 5. 调用流程对比

### 场景1：调用普通方法（如 saveUser）

```java
userService.saveUser();
```

**执行流程**：
```
1. 进入代理对象的 saveUser()
2. CGLIB CallbackFilter 判断：不是 Advised 接口方法
3. 使用回调索引 0：DynamicAdvisedInterceptor
4. DynamicAdvisedInterceptor 执行：
   ├─ 遍历所有 Advisor
   ├─ 检查是否匹配当前方法
   ├─ 执行前置通知（@Before）
   ├─ 调用目标对象的 saveUser()
   └─ 执行后置通知（@After）
```

### 场景2：调用 Advised 接口方法（如 getTargetSource）

```java
((Advised)userService).getTargetSource();
```

**执行流程**：
```
1. 进入代理对象的 getTargetSource()
2. CGLIB CallbackFilter 判断：是 Advised 接口方法
3. 使用回调索引 4：AdvisedDispatcher
4. AdvisedDispatcher.loadObject() 直接返回 this.advised
5. 在返回的 AdvisedSupport 对象上调用 getTargetSource()
```

**特点**：
- 不经过拦截器链
- 不检查通知
- 直接操作配置对象
- 性能更高

## 6. CallbackFilter 的判断逻辑

```java
private static class ProxyCallbackFilter implements CallbackFilter {
    
    public int accept(Method method) {
        // 1. 判断是否是 Advised 接口的方法
        if (method.getDeclaringClass() == Advised.class) {
            return DISPATCH_ADVISED;  // 返回 4，使用 AdvisedDispatcher
        }
        
        // 2. 判断是否是 equals 方法
        if (isEqualsMethod(method)) {
            return INVOKE_EQUALS;  // 返回 5
        }
        
        // 3. 判断是否是 hashCode 方法
        if (isHashCodeMethod(method)) {
            return INVOKE_HASHCODE;  // 返回 6
        }
        
        // 4. 普通业务方法
        return AOP_PROXY;  // 返回 0，使用 DynamicAdvisedInterceptor
    }
}
```

## 7. 总结

| 属性 | 说明 |
|------|------|
| **类型** | `AdvisedDispatcher implements Dispatcher` |
| **位置** | CGLIB 回调数组索引 4（`DISPATCH_ADVISED`） |
| **作用** | 处理 `Advised` 接口方法的调用 |
| **核心方法** | `loadObject()` 直接返回 `AdvisedSupport` 配置对象 |
| **使用场景** | 当调用 `getTargetSource()`、`addAdvisor()` 等配置方法时 |
| **设计目的** | 避免走AOP拦截链，提高性能，逻辑清晰 |

### 为什么需要 AdvisedDispatcher？

1. **性能优化**：Advised 方法不需要检查通知，直接返回配置对象更快
2. **职责分离**：普通方法走拦截链，配置方法直接操作配置
3. **避免递归**：如果 Advised 方法也走拦截链，可能引发循环调用
4. **功能支持**：让代理对象可以动态修改配置（添加/移除通知等）
