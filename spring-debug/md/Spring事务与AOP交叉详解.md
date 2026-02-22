# Spring事务与AOP交叉详解

> 本文从问题出发，深入解析Spring事务模块如何"借用"AOP模块的能力实现声明式事务管理。理解这个交叉点是掌握Spring框架设计的精髓。

---

## 目录

1. [问题引入：@Transactional是如何生效的？](#一问题引入transactional是如何生效的)
2. [AOP模块提供了什么？](#二aop模块提供了什么)
3. [事务模块需要什么？](#三事务模块需要什么)
4. [交叉点一：MethodInterceptor接口](#四交叉点一methodinterceptor接口)
5. [交叉点二：TransactionInterceptor](#五交叉点二transactioninterceptor)
6. [交叉点三：TransactionAspectSupport](#六交叉点三transactionaspectsupport)
7. [交叉点四：Advisor与Pointcut](#七交叉点四advisor与pointcut)
8. [交叉点五：InfrastructureAdvisorAutoProxyCreator](#八交叉点五infrastructureadvisorautoproxycreator)
9. [完整注册流程](#九完整注册流程)
10. [类关系总结](#十类关系总结)

---

## 一、问题引入：@Transactional是如何生效的？

### 1.1 一个简单的使用示例

```java
@Service
public class UserService {
    
    @Transactional
    public void createUser(User user) {
        userDao.insert(user);
        logDao.insertLog(user);
    }
}
```

**问题1**：我们在方法上加了`@Transactional`注解，Spring是如何让事务自动生效的？

**问题2**：事务管理代码（开启事务、提交、回滚）是在哪里执行的？

**问题3**：Spring事务和Spring AOP是什么关系？

---

### 1.2 直觉上的猜测

你可能会想：
- 是不是有个切面（Aspect）拦截了这个方法？
- 是不是在方法执行前后做了事务管理？
- 是不是AOP技术？

**答案是肯定的！但具体是怎么实现的？**

---

## 二、AOP模块提供了什么？

### 2.1 AOP的核心能力

Spring AOP模块提供了**方法拦截**的能力，其核心接口是：

```java
package org.aopalliance.intercept;

/**
 * AOP Alliance定义的拦截器接口
 * 这是一个通用的方法拦截接口，不依赖Spring
 */
public interface MethodInterceptor extends Interceptor {
    
    /**
     * 拦截方法调用
     * @param invocation 方法调用信息，包含目标方法、参数、目标对象等
     * @return 方法执行结果
     * @throws Throwable 方法执行异常
     */
    Object invoke(MethodInvocation invocation) throws Throwable;
}
```

**问题4**：`MethodInvocation`是什么？它包含什么信息？

```java
public interface MethodInvocation extends Invocation {
    
    // 获取目标方法
    Method getMethod();
    
    // 获取方法参数
    Object[] getArguments();
    
    // 执行下一个拦截器或目标方法
    Object proceed() throws Throwable;
    
    // 获取目标对象
    Object getThis();
}
```

### 2.2 AOP的执行流程

当调用一个被AOP代理的方法时：

```
客户端调用 proxy.method()
        ↓
MethodInterceptor.invoke(invocation)
        ↓
    前置逻辑
        ↓
invocation.proceed() → 执行下一个拦截器或目标方法
        ↓
    后置逻辑
        ↓
返回结果
```

**问题5**：如果我要用AOP实现事务管理，我应该怎么做？

**答案**：实现`MethodInterceptor`接口，在`invoke`方法中：
1. 方法执行前 → 开启事务
2. `invocation.proceed()` → 执行目标方法
3. 正常返回 → 提交事务
4. 抛出异常 → 回滚事务

---

## 三、事务模块需要什么？

### 3.1 事务管理的本质需求

事务管理本质上是一个**横切关注点**（Cross-cutting Concern），它需要：

| 需求 | 说明 |
|------|------|
| **拦截方法** | 在方法执行前后插入事务逻辑 |
| **识别目标方法** | 判断方法是否需要事务 |
| **获取事务配置** | 读取`@Transactional`的配置 |
| **管理事务状态** | 开启、提交、回滚事务 |

### 3.2 如果没有AOP

假设Spring没有AOP模块，事务模块需要自己实现：
- 动态代理生成
- 方法拦截
- 切面织入

这显然是重复造轮子，而且AOP模块已经做得很好了。

**问题6**：Spring事务模块是如何"借用"AOP能力的？

---

## 四、交叉点一：MethodInterceptor接口

### 4.1 接口的归属

```
┌─────────────────────────────────────────────────────────┐
│                    aopalliance.jar                      │
│  （AOP Alliance，第三方规范，不依赖Spring）               │
│                                                         │
│      interface MethodInterceptor {                      │
│          Object invoke(MethodInvocation invocation);    │
│      }                                                  │
│                          ↑                              │
│                          │ 实现                         │
│                          │                              │
├──────────────────────────┼──────────────────────────────┤
│      Spring AOP 模块     │     Spring Tx 模块           │
│                          │                              │
│   - AspectJAroundAdvice  │   - TransactionInterceptor   │
│   - AfterReturningAdvice │                              │
│   - MethodBeforeAdvice   │                              │
└──────────────────────────┴──────────────────────────────┘
```

**关键设计**：`MethodInterceptor`接口来自**AOP Alliance**，这是一个独立的规范。

**问题7**：为什么要用第三方规范？

**答案**：
1. 解耦：AOP模块和事务模块都依赖同一个接口，而不是相互依赖
2. 标准：AOP Alliance是一个通用规范，不是Spring私有API
3. 扩展：任何模块都可以实现这个接口，获得AOP能力

### 4.2 MethodInterceptor的核心方法

```java
public interface MethodInterceptor extends Interceptor {
    Object invoke(MethodInvocation invocation) throws Throwable;
}
```

**参数解析**：

```java
// MethodInvocation包含的信息
MethodInvocation invocation = ...;

// 1. 目标对象
Object target = invocation.getThis();  // 如：UserService实例

// 2. 目标方法
Method method = invocation.getMethod();  // 如：createUser方法

// 3. 方法参数
Object[] args = invocation.getArguments();  // 如：[User对象]

// 4. 执行目标方法
Object result = invocation.proceed();  // 执行createUser方法体
```

---

## 五、交叉点二：TransactionInterceptor

### 5.1 类定义

`TransactionInterceptor`是**事务模块**实现`MethodInterceptor`的核心类：

```java
package org.springframework.transaction.interceptor;

/**
 * AOP Alliance MethodInterceptor for declarative transaction management.
 * 
 * 实现了MethodInterceptor接口，这意味着它可以被AOP框架识别和执行。
 */
public class TransactionInterceptor extends TransactionAspectSupport 
        implements MethodInterceptor, Serializable {
    
    // ...
}
```

**问题8**：为什么继承`TransactionAspectSupport`？

**答案**：模板方法模式
- `TransactionAspectSupport`提供事务管理的核心逻辑
- `TransactionInterceptor`专注于AOP拦截的实现

### 5.2 invoke方法的实现

```java
@Override
@Nullable
public Object invoke(MethodInvocation invocation) throws Throwable {
    // 1. 获取目标类（可能为null，如静态方法）
    Class<?> targetClass = (invocation.getThis() != null ? 
            AopUtils.getTargetClass(invocation.getThis()) : null);
    
    // 2. 调用父类的事务管理逻辑
    //    withinTransaction是一个模板方法，定义了事务的标准流程
    return invokeWithinTransaction(invocation.getMethod(), targetClass, 
            new CoroutinesInvocationCallback() {
                @Override
                public Object proceedWithInvocation() throws Throwable {
                    // 这里面执行目标方法
                    return invocation.proceed();
                }
            });
}
```

**问题9**：为什么不直接在这里写事务逻辑？

**答案**：分离关注点
- `TransactionInterceptor`：处理AOP特定的逻辑（获取目标类、方法等）
- `TransactionAspectSupport`：处理通用的事务逻辑（开启、提交、回滚）

这样设计的好处：
1. 其他场景（如AspectJ LTW）可以复用`TransactionAspectSupport`
2. 代码职责清晰，易于维护

### 5.3 TransactionInterceptor的字段

```java
public class TransactionInterceptor extends TransactionAspectSupport 
        implements MethodInterceptor, Serializable {
    
    // 从父类继承的重要字段：
    
    // 1. 事务管理器（可以是单个，也可以是Map<String, PlatformTransactionManager>）
    //    private PlatformTransactionManager transactionManager;
    
    // 2. 事务属性源（解析@Transactional注解）
    //    private TransactionAttributeSource transactionAttributeSource;
    
    // 3. 事务信息持有者（ThreadLocal存储当前事务信息）
    //    private static final ThreadLocal<TransactionInfo> transactionInfoHolder;
}
```

---

## 六、交叉点三：TransactionAspectSupport

### 6.1 类定义

`TransactionAspectSupport`是事务切面的基类，提供了事务管理的核心逻辑：

```java
package org.springframework.transaction.interceptor;

/**
 * Base class for transactional aspects, such as the AOP Alliance 
 * TransactionInterceptor.
 * 
 * 提供了事务管理的模板方法，供子类调用。
 */
public abstract class TransactionAspectSupport implements BeanFactoryAware, InitializingBean {
    
    // ...
}
```

### 6.2 核心方法：invokeWithinTransaction

```java
@Nullable
protected Object invokeWithinTransaction(Method method, @Nullable Class<?> targetClass,
        InvocationCallback invocation) throws Throwable {
    
    // ========== 第一步：获取事务属性 ==========
    TransactionAttributeSource tas = getTransactionAttributeSource();
    TransactionAttribute txAttr = (tas != null ? 
            tas.getTransactionAttribute(method, targetClass) : null);
    
    // ========== 第二步：获取事务管理器 ==========
    PlatformTransactionManager tm = determineTransactionManager(txAttr);
    
    // ========== 第三步：构造方法标识（用于日志和监控） ==========
    String joinpointIdentification = methodIdentification(method, targetClass, txAttr);
    
    // ========== 第四步：根据事务属性决定如何处理 ==========
    
    // 情况1：响应式事务（WebFlux）
    if (txAttr == null || !(tm instanceof CallbackPreferringPlatformTransactionManager)) {
        
        // ========== 创建事务（如果需要） ==========
        TransactionInfo txInfo = createTransactionIfNecessary(
                tm, txAttr, joinpointIdentification);
        
        Object retVal = null;
        try {
            // ========== 执行目标方法 ==========
            retVal = invocation.proceedWithInvocation();
        }
        catch (Throwable ex) {
            // ========== 异常处理：回滚还是提交？ ==========
            completeTransactionAfterThrowing(txInfo, ex);
            throw ex;
        }
        finally {
            // ========== 清理事务信息（恢复之前的事务） ==========
            cleanupTransactionInfo(txInfo);
        }
        
        // ========== 提交事务 ==========
        commitTransactionAfterReturning(txInfo);
        return retVal;
    }
    
    // 情况2：回调式事务管理器（编程式事务，本文不展开）
    else {
        // ...
    }
}
```

### 6.3 关键方法详解

#### createTransactionIfNecessary

```java
protected TransactionInfo createTransactionIfNecessary(
        PlatformTransactionManager tm, 
        @Nullable TransactionAttribute txAttr, 
        String joinpointIdentification) {
    
    // 1. 如果没有事务属性，创建一个空的TransactionInfo
    if (txAttr == null || tm == null) {
        return new TransactionInfo(null, null, joinpointIdentification);
    }
    
    // 2. 获取事务（核心！根据传播行为决定是新建还是加入）
    TransactionStatus status = tm.getTransaction(txAttr);
    
    // 3. 创建TransactionInfo并绑定到当前线程
    TransactionInfo txInfo = new TransactionInfo(tm, txAttr, joinpointIdentification);
    txInfo.newTransactionStatus(status);
    
    // 4. 绑定到ThreadLocal
    txInfo.bindToThread();
    
    return txInfo;
}
```

#### completeTransactionAfterThrowing

```java
protected void completeTransactionAfterThrowing(
        @Nullable TransactionInfo txInfo, Throwable ex) {
    
    if (txInfo != null && txInfo.getTransactionStatus() != null) {
        
        // 关键判断：是否需要回滚？
        // 这里会调用rollbackOn方法，根据异常类型和事务属性决定
        if (txInfo.transactionAttribute != null && 
                txInfo.transactionAttribute.rollbackOn(ex)) {
            
            // 回滚事务
            txInfo.getTransactionManager().rollback(txInfo.getTransactionStatus());
        }
        else {
            // 异常但仍提交事务（非回滚异常）
            txInfo.getTransactionManager().commit(txInfo.getTransactionStatus());
        }
    }
}
```

#### cleanupTransactionInfo

```java
protected void cleanupTransactionInfo(@Nullable TransactionInfo txInfo) {
    if (txInfo != null) {
        // 恢复之前的事务信息（支持事务嵌套）
        txInfo.restoreThreadLocalStatus();
    }
}
```

### 6.4 TransactionInfo的数据结构

```java
/**
 * 事务信息对象，包含当前事务的所有上下文信息
 * 使用链表结构支持事务嵌套
 */
protected static final class TransactionInfo {
    
    // 当前事务管理器
    @Nullable
    private final PlatformTransactionManager transactionManager;
    
    // 当前事务属性
    @Nullable
    private final TransactionAttribute transactionAttribute;
    
    // 方法标识（用于日志）
    private final String joinpointIdentification;
    
    // 当前事务状态
    @Nullable
    private TransactionStatus transactionStatus;
    
    // ========== 关键：指向之前的事务信息（链表结构） ==========
    @Nullable
    private TransactionInfo oldTransactionInfo;
    
    // 绑定到ThreadLocal
    public void bindToThread() {
        // 保存之前的事务信息
        this.oldTransactionInfo = transactionInfoHolder.get();
        // 设置当前事务信息
        transactionInfoHolder.set(this);
    }
    
    // 恢复之前的事务信息
    public void restoreThreadLocalStatus() {
        transactionInfoHolder.set(this.oldTransactionInfo);
    }
}
```

**问题10**：为什么使用链表结构？

**答案**：支持事务嵌套
- 外层事务执行时，创建TransactionInfo A
- 调用内层事务方法时，创建TransactionInfo B，B.oldTransactionInfo = A
- 内层事务完成后，恢复A
- 这是典型的栈结构，用链表实现

---

## 七、交叉点四：Advisor与Pointcut

### 7.1 AOP的Advisor接口

在Spring AOP中，一个完整的切面由两部分组成：

```java
/**
 * Advisor：切面定义，包含Advice和Pointcut
 */
public interface Advisor {
    
    // 获取通知（要执行的逻辑）
    Advice getAdvice();
    
    // 是否是PerTarget（每次调用都创建新的通知实例）
    boolean isPerInstance();
}

/**
 * PointcutAdvisor：包含切入点的切面
 */
public interface PointcutAdvisor extends Advisor {
    
    // 获取切入点（定义哪些方法需要被拦截）
    Pointcut getPointcut();
}
```

### 7.2 事务模块的Advisor实现

事务模块提供了`BeanFactoryTransactionAttributeSourceAdvisor`：

```java
package org.springframework.transaction.interceptor;

/**
 * Advisor driven by a TransactionAttributeSource, 
 * used to include a transaction advice bean for methods 
 * that are transactional.
 */
public class BeanFactoryTransactionAttributeSourceAdvisor 
        extends AbstractPointcutAdvisor 
        implements BeanFactoryAware {
    
    // 事务属性源（用于解析@Transactional注解）
    private TransactionAttributeSource transactionAttributeSource;
    
    // 通知（TransactionInterceptor）
    private Advice advice;
    
    // 切入点
    private final Pointcut pointcut = new TransactionAttributeSourcePointcut() {
        @Override
        protected TransactionAttributeSource getTransactionAttributeSource() {
            return transactionAttributeSource;
        }
    };
    
    @Override
    public Pointcut getPointcut() {
        return this.pointcut;
    }
    
    @Override
    public Advice getAdvice() {
        return this.advice;
    }
}
```

### 7.3 TransactionAttributeSourcePointcut

```java
/**
 * 切入点：判断方法是否有@Transactional注解
 */
abstract class TransactionAttributeSourcePointcut extends StaticMethodMatcherPointcut {
    
    @Override
    public boolean matches(Method method, Class<?> targetClass) {
        // 1. 获取事务属性源
        TransactionAttributeSource tas = getTransactionAttributeSource();
        
        // 2. 如果没有配置事务属性源，不匹配
        if (tas == null) {
            return false;
        }
        
        // 3. 解析方法的事务属性
        //    如果有@Transactional注解，返回非null，表示匹配
        return (tas.getTransactionAttribute(method, targetClass) != null);
    }
    
    protected abstract TransactionAttributeSource getTransactionAttributeSource();
}
```

**问题11**：为什么不直接用AspectJ表达式？

**答案**：更灵活的匹配方式
- AspectJ表达式：基于方法名、包名等
- TransactionAttributeSource：基于注解内容
- TransactionAttributeSource可以解析注解属性（传播行为、隔离级别等），实现更精细的控制

### 7.4 类关系图

```
┌─────────────────────────────────────────────────────────┐
│                    Spring AOP 模块                       │
│                                                         │
│  interface Advisor                                      │
│         ↑                                               │
│         │                                               │
│  interface PointcutAdvisor                              │
│         ↑                                               │
│         │                                               │
│  abstract class AbstractPointcutAdvisor                 │
└─────────┼───────────────────────────────────────────────┘
          │
          │ extends
          │
┌─────────┼───────────────────────────────────────────────┐
│         ↓           Spring Tx 模块                      │
│                                                         │
│  BeanFactoryTransactionAttributeSourceAdvisor           │
│  ┌─────────────────────────────────────────────┐       │
│  │ - transactionAttributeSource                │       │
│  │ - advice = TransactionInterceptor           │       │
│  │ - pointcut = TransactionAttributeSourcePointcut    │
│  └─────────────────────────────────────────────┘       │
└─────────────────────────────────────────────────────────┘
```

---

## 八、交叉点五：InfrastructureAdvisorAutoProxyCreator

### 8.1 自动代理创建器

问题：`BeanFactoryTransactionAttributeSourceAdvisor`是如何被应用到目标Bean的？

答案：通过`InfrastructureAdvisorAutoProxyCreator`

```java
package org.springframework.aop.framework.autoproxy;

/**
 * Auto-proxy creator that considers infrastructure Advisor beans only,
 * ignoring any application-defined Advisors.
 * 
 * 基础设施Advisor自动代理创建器
 * 专门用于处理事务等基础设施相关的Advisor
 */
public class InfrastructureAdvisorAutoProxyCreator 
        extends AbstractAdvisorAutoProxyCreator {
    
    // ...
}
```

### 8.2 继承关系

```
ProxyConfig
    ↓
AbstractAutoProxyCreator
    ↓
AbstractAdvisorAutoProxyCreator
    ↓
InfrastructureAdvisorAutoProxyCreator
```

### 8.3 核心方法

`AbstractAutoProxyCreator`实现了`BeanPostProcessor`接口：

```java
public abstract class AbstractAutoProxyCreator extends ProxyConfig
        implements SmartInstantiationAwareBeanPostProcessor, BeanClassLoaderAware,
        BeanFactoryAware, Ordered, InitializingBean {
    
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean != null) {
            Object cacheKey = getCacheKey(bean.getClass(), beanName);
            
            // 1. 检查是否已经创建过代理
            if (this.earlyProxyReferences.remove(cacheKey) != bean) {
                // 2. 如果需要，包装成代理
                return wrapIfNecessary(bean, beanName, cacheKey);
            }
        }
        return bean;
    }
    
    protected Object wrapIfNecessary(Object bean, String beanName, Object cacheKey) {
        // 1. 获取适用的Advisor
        Object[] specificInterceptors = getAdvicesAndAdvisorsForBean(
                bean.getClass(), beanName, null);
        
        if (specificInterceptors != DO_NOT_PROXY) {
            // 2. 创建代理
            Object proxy = createProxy(bean.getClass(), beanName, 
                    specificInterceptors, new SingletonTargetSource(bean));
            return proxy;
        }
        return bean;
    }
}
```

### 8.4 Advisor的查找逻辑

```java
// AbstractAdvisorAutoProxyCreator
protected Object[] getAdvicesAndAdvisorsForBean(
        Class<?> beanClass, String beanName, @Nullable TargetSource targetSource) {
    
    // 1. 查找所有适用的Advisor
    List<Advisor> advisors = findEligibleAdvisors(beanClass, beanName);
    
    if (advisors.isEmpty()) {
        return DO_NOT_PROXY;
    }
    return advisors.toArray();
}

protected List<Advisor> findEligibleAdvisors(Class<?> beanClass, String beanName) {
    // 1. 查找所有候选Advisor
    List<Advisor> candidateAdvisors = findCandidateAdvisors();
    
    // 2. 过滤出适用的Advisor
    List<Advisor> eligibleAdvisors = findAdvisorsThatCanApply(
            candidateAdvisors, beanClass, beanName);
    
    return eligibleAdvisors;
}
```

### 8.5 InfrastructureAdvisorAutoProxyCreator的特殊之处

```java
public class InfrastructureAdvisorAutoProxyCreator 
        extends AbstractAdvisorAutoProxyCreator {
    
    @Override
    protected boolean isEligibleAdvisorBean(String beanName) {
        // 只处理基础设施角色（ROLE_INFRASTRUCTURE）的Advisor
        // 这是与普通AutoProxyCreator的区别
        return false;
    }
    
    // 实际上，它通过BeanFactoryAdvisorRetrievalHelper来查找
    // 该Helper只返回role为ROLE_INFRASTRUCTURE的Advisor
}
```

**问题12**：为什么要有`InfrastructureAdvisorAutoProxyCreator`？

**答案**：避免与用户自定义的Advisor冲突
- 用户可能定义自己的`@Aspect`切面
- 事务Advisor是框架级别的（ROLE_INFRASTRUCTURE）
- 分开处理，避免相互影响

---

## 九、完整注册流程

### 9.1 @EnableTransactionManagement

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(TransactionManagementConfigurationSelector.class)
public @interface EnableTransactionManagement {
    
    // 是否使用CGLIB代理（默认false，使用JDK动态代理）
    boolean proxyTargetClass() default false;
    
    // 代理模式：PROXY或ASPECTJ
    AdviceMode mode() default AdviceMode.PROXY;
    
    // 自动代理创建器的顺序
    int order() default Ordered.LOWEST_PRECEDENCE;
}
```

### 9.2 TransactionManagementConfigurationSelector

```java
public class TransactionManagementConfigurationSelector 
        extends AdviceModeImportSelector<EnableTransactionManagement> {
    
    @Override
    protected String[] selectImports(AdviceMode adviceMode) {
        switch (adviceMode) {
            case PROXY:
                return new String[] {
                    // 注册自动代理创建器
                    AutoProxyRegistrar.class.getName(),
                    // 注册事务配置
                    ProxyTransactionManagementConfiguration.class.getName()
                };
            case ASPECTJ:
                return new String[] {
                    // AspectJ LTW模式
                    TransactionManagementConfigUtils.TRANSACTION_ASPECT_CONFIGURATION_CLASS_NAME
                };
            default:
                return null;
        }
    }
}
```

### 9.3 AutoProxyRegistrar

```java
public class AutoProxyRegistrar implements ImportBeanDefinitionRegistrar {
    
    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, 
            BeanDefinitionRegistry registry) {
        
        boolean candidateFound = false;
        Set<String> annTypes = importingClassMetadata.getAnnotationTypes();
        
        for (String annType : annTypes) {
            AnnotationAttributes candidate = AnnotationConfigUtils.attributesFor(
                    importingClassMetadata, annType);
            
            if (candidate == null) {
                continue;
            }
            
            Object mode = candidate.get("mode");
            Object proxyTargetClass = candidate.get("proxyTargetClass");
            
            if (mode != null && proxyTargetClass != null && 
                    AdviceMode.class == mode.getClass() && 
                    Boolean.class == proxyTargetClass.getClass()) {
                
                candidateFound = true;
                
                if (mode == AdviceMode.PROXY) {
                    // 注册InfrastructureAdvisorAutoProxyCreator
                    AopConfigUtils.registerAutoProxyCreatorIfNecessary(registry);
                    
                    if ((Boolean) proxyTargetClass) {
                        AopConfigUtils.forceAutoProxyCreatorToUseClassProxying(registry);
                    }
                }
            }
        }
    }
}
```

### 9.4 ProxyTransactionManagementConfiguration

```java
@Configuration(proxyBeanMethods = false)
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
public class ProxyTransactionManagementConfiguration 
        extends AbstractTransactionManagementConfiguration {
    
    // 注册事务Advisor
    @Bean(name = TransactionManagementConfigUtils.TRANSACTION_ADVISOR_BEAN_NAME)
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public BeanFactoryTransactionAttributeSourceAdvisor transactionAdvisor(
            TransactionAttributeSource transactionAttributeSource,
            TransactionInterceptor transactionInterceptor) {
        
        BeanFactoryTransactionAttributeSourceAdvisor advisor = 
                new BeanFactoryTransactionAttributeSourceAdvisor();
        
        advisor.setTransactionAttributeSource(transactionAttributeSource);
        advisor.setAdvice(transactionInterceptor);
        
        if (this.enableTx != null) {
            advisor.setOrder(this.enableTx.<Integer>getNumber("order"));
        }
        
        return advisor;
    }
    
    // 注册事务属性源
    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public TransactionAttributeSource transactionAttributeSource() {
        return new AnnotationTransactionAttributeSource();
    }
    
    // 注册事务拦截器
    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public TransactionInterceptor transactionInterceptor(
            TransactionAttributeSource transactionAttributeSource) {
        
        TransactionInterceptor interceptor = new TransactionInterceptor();
        interceptor.setTransactionAttributeSource(transactionAttributeSource);
        
        if (this.txManager != null) {
            interceptor.setTransactionManager(this.txManager);
        }
        
        return interceptor;
    }
}
```

### 9.5 完整流程图

```
@EnableTransactionManagement
        ↓
@Import(TransactionManagementConfigurationSelector)
        ↓
┌───────┴───────┐
│               │
↓               ↓
AutoProxyRegistrar    ProxyTransactionManagementConfiguration
│               │
│               ├──→ @Bean TransactionAttributeSource
│               │         (解析@Transactional注解)
│               │
│               ├──→ @Bean TransactionInterceptor
│               │         (实现MethodInterceptor)
│               │
│               └──→ @Bean BeanFactoryTransactionAttributeSourceAdvisor
│                         (组合Pointcut + Advice)
│
└──→ 注册 InfrastructureAdvisorAutoProxyCreator
           (BeanPostProcessor，自动创建代理)
                    ↓
           扫描所有ROLE_INFRASTRUCTURE的Advisor
                    ↓
           应用到匹配的Bean
```

---

## 十、类关系总结

### 10.1 核心交叉类一览

| 类名 | 所属模块 | 继承/实现 | 作用 |
|------|---------|----------|------|
| `MethodInterceptor` | AOP Alliance | 接口 | 方法拦截器接口 |
| `TransactionInterceptor` | Tx | 实现MethodInterceptor | 事务拦截器，核心交叉点 |
| `TransactionAspectSupport` | Tx | 父类 | 事务管理核心逻辑 |
| `Advisor` | AOP | 接口 | 切面定义 |
| `PointcutAdvisor` | AOP | 接口 | 带切入点的切面 |
| `AbstractPointcutAdvisor` | AOP | 抽象类 | 切面基类 |
| `BeanFactoryTransactionAttributeSourceAdvisor` | Tx | 继承AbstractPointcutAdvisor | 事务切面 |
| `TransactionAttributeSourcePointcut` | Tx | 继承Pointcut | 事务切入点 |
| `InfrastructureAdvisorAutoProxyCreator` | AOP | BeanPostProcessor | 自动代理创建器 |

### 10.2 依赖关系图

```
┌─────────────────────────────────────────────────────────────────────┐
│                         AOP Alliance (第三方规范)                    │
│                                                                     │
│    MethodInterceptor ◄─────────────────────────────────────────────┤
│         ↑                                                           │
└─────────┼───────────────────────────────────────────────────────────┘
          │ 实现
┌─────────┼───────────────────────────────────────────────────────────┐
│         │                Spring AOP 模块                             │
│         │                                                           │
│    Advisor ◄─────────────────────────────────────────────────────┐  │
│         ↑                                                         │  │
│    PointcutAdvisor                                                │  │
│         ↑                                                         │  │
│    AbstractPointcutAdvisor                                        │  │
│         ↑                           extends                       │  │
│         │                           ┌─────────────────────────────┘  │
│    InfrastructureAdvisorAutoProxyCreator                          │  │
│         │                                                         │  │
└─────────┼─────────────────────────────────────────────────────────┼──┘
          │                                                         │
          │ 发现并应用                                               │
          ↓                                                         │
┌─────────────────────────────────────────────────────────────────────┼──┐
│                          Spring Tx 模块                             │  │
│                                                                     │  │
│    TransactionInterceptor ───────────────────────────────────────────┘
│         │                实现 MethodInterceptor
│         │
│         ↓ extends
│    TransactionAspectSupport
│         │
│         └──→ TransactionInfo (ThreadLocal存储)
│
│    BeanFactoryTransactionAttributeSourceAdvisor
│         │                extends AbstractPointcutAdvisor
│         │
│         ├──→ TransactionAttributeSourcePointcut (匹配@Transactional方法)
│         │
│         └──→ TransactionInterceptor (advice)
│
│    TransactionAttributeSource (解析@Transactional)
│         │
│         └──→ AnnotationTransactionAttributeSource
│                   │
│                   └──→ SpringTransactionAnnotationParser
└─────────────────────────────────────────────────────────────────────┘
```

### 10.3 执行流程

```
1. 启动阶段
   @EnableTransactionManagement
   → 注册 InfrastructureAdvisorAutoProxyCreator (AOP)
   → 注册 BeanFactoryTransactionAttributeSourceAdvisor (Tx)
   → 注册 TransactionInterceptor (Tx)

2. Bean创建阶段
   InfrastructureAdvisorAutoProxyCreator.postProcessAfterInitialization()
   → 查找所有ROLE_INFRASTRUCTURE的Advisor
   → 找到 BeanFactoryTransactionAttributeSourceAdvisor
   → 检查Pointcut是否匹配当前Bean的方法
   → 如果匹配，创建代理对象

3. 方法调用阶段
   proxy.method()
   → TransactionInterceptor.invoke()
   → TransactionAspectSupport.invokeWithinTransaction()
   → 创建事务
   → 执行目标方法
   → 提交/回滚事务
```

### 10.4 设计模式总结

| 模式 | 应用位置 | 说明 |
|------|---------|------|
| **模板方法** | TransactionAspectSupport | 定义事务处理的标准流程 |
| **策略模式** | PlatformTransactionManager | 不同数据源有不同实现 |
| **代理模式** | 代理对象创建 | JDK动态代理或CGLIB |
| **责任链模式** | MethodInterceptor链 | 多个拦截器依次执行 |
| **工厂模式** | ProxyFactory | 创建代理对象 |
| **观察者模式** | TransactionSynchronization | 事务生命周期回调 |

---

## 总结

Spring事务与AOP的交叉是通过**接口继承**和**组合**实现的：

1. **接口层面**：`TransactionInterceptor`实现AOP Alliance的`MethodInterceptor`
2. **切面层面**：`BeanFactoryTransactionAttributeSourceAdvisor`继承AOP的`AbstractPointcutAdvisor`
3. **代理层面**：`InfrastructureAdvisorAutoProxyCreator`自动发现并应用事务Advisor

这种设计的精妙之处：
- **解耦**：事务模块不依赖AOP模块的具体实现，只依赖接口
- **复用**：事务模块"借用"AOP的能力，无需自己实现代理
- **扩展**：其他模块（如缓存、安全）可以用同样的方式实现声明式功能
