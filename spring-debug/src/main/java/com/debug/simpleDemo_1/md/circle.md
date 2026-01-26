# 循环依赖中的一致性检查相关

说实话,这里一开始没太看懂到底在干什么,AI举了一个例子，如下：
```java
@Service
public class ServiceA {
    @Autowired
    private ServiceB serviceB;

    @Async  // 需要 AOP 代理
    public void asyncMethod() {}
}

@Service
public class ServiceB {
    @Autowired
    private ServiceA serviceA;
}
```

```text
1. 创建 ServiceA
   ├── 实例化 ServiceA（原始对象 beanA）
   ├── 放入三级缓存：singletonFactories.put("serviceA", () -> getEarlyBeanReference(...))
   └── populateBean() → 需要注入 ServiceB
   
2. 创建 ServiceB
   ├── 实例化 ServiceB
   ├── populateBean() → 需要注入 ServiceA
   │   └── 从三级缓存获取 ServiceA 的早期引用
   │       └── getEarlyBeanReference() 返回原始对象 beanA（此时还没代理）
   │       └── 放入二级缓存：earlySingletonObjects.put("serviceA", beanA)
   └── initializeBean() → ServiceB 创建完成
   
3. 回到 ServiceA
   ├── populateBean() 完成
   └── initializeBean() 
       └── @Async 处理 → 创建代理对象 proxyA
       └── exposedObject = proxyA（新对象！）

4. 一致性检查
   ├── earlySingletonReference = beanA（从二级缓存获取）
   ├── exposedObject = proxyA
   ├── exposedObject != bean（proxyA != beanA）
   └── ServiceB 持有的是 beanA，但最终对象是 proxyA
       └── 抛出异常！
```

通过这个案例,我大概理解了是怎么回事了，后续需要写一个demo来具体跟踪一下

补充：在代码中有关于依赖关系的代码逻辑，是在哪里处理的呢？还是以上面的代码为例子
```text
======== 阶段1：创建 ServiceA ========

1. 实例化 ServiceA（原始对象 beanA）
2. 放入三级缓存
3. populateBean() → 需要注入 ServiceB
   └── 触发 getBean("serviceB")

======== 阶段2：创建 ServiceB ========

4. 实例化 ServiceB
5. 放入三级缓存
6. populateBean() → 需要注入 ServiceA
   └── 触发 getBean("serviceA")
   └── 从三级缓存获取 ServiceA 的早期引用 (beanA)
   └── ⭐ 此时调用 registerDependentBean("serviceA", "serviceB")
       ├── dependentBeanMap.put("serviceA", ["serviceB"])  
       │   含义：serviceB 依赖 serviceA
       └── dependenciesForBeanMap.put("serviceB", ["serviceA"])
           含义：serviceB 的依赖列表包含 serviceA
           
7. initializeBean() → ServiceB 创建完成
8. ServiceB 放入一级缓存

======== 阶段3：回到 ServiceA ========

9. populateBean() 完成（ServiceB 已注入）
10. initializeBean() 
    └── @Async 后置处理器创建代理 → proxyA
    └── exposedObject = proxyA

======== 阶段4：一致性检查 ========

11. getSingleton("serviceA", false) 
    └── 从二级缓存获取 earlySingletonReference = beanA

12. 判断：exposedObject == bean ?
    └── proxyA != beanA → 进入 else 分支

13. hasDependentBean("serviceA") ?
    └── dependentBeanMap.get("serviceA") = ["serviceB"]
    └── 返回 true

14. getDependentBeans("serviceA")
    └── 返回 ["serviceB"]

15. 遍历检查每个 dependentBean
    └── removeSingletonIfCreatedForTypeCheckOnly("serviceB") = false
        （ServiceB 不是仅用于类型检查的临时Bean）
    └── actualDependentBeans.add("serviceB")

16. actualDependentBeans 不为空
    └── 抛出 BeanCurrentlyInCreationException！

步骤6执行后，两个 Map 的状态：
dependentBeanMap:
┌─────────────┬──────────────────┐
│  beanName   │  dependentBeans  │
├─────────────┼──────────────────┤
│  serviceA   │  [serviceB]      │  ← 谁依赖 serviceA？答：serviceB
└─────────────┴──────────────────┘

dependenciesForBeanMap:
┌─────────────┬──────────────────┐
│  beanName   │  dependencies    │
├─────────────┼──────────────────┤
│  serviceB   │  [serviceA]      │  ← serviceB 依赖谁？答：serviceA
└─────────────┴──────────────────┘

问题的本质：

ServiceB 在步骤6注入的是：beanA（原始对象）
ServiceA 最终暴露的是：proxyA（代理对象）

结果：
┌──────────────────────────────────────────────┐
│  ServiceB.serviceA  →  beanA (原始对象)      │
│  容器中的 ServiceA  →  proxyA (代理对象)     │
└──────────────────────────────────────────────┘

这意味着：
- 通过 ServiceB 调用 serviceA.asyncMethod() → 不会异步执行！
- 通过容器获取 ServiceA 调用 asyncMethod() → 会异步执行！

这是严重的不一致问题，所以 Spring 选择抛出异常！

```


