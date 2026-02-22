# Spring事务核心类与数据结构深度解析

> 本文从原始JDBC事务开始，一步步演进，详细讲解Spring事务的完整体系（不含编程式事务）。
> 
> **核心思想**：程序 = 数据结构 + 算法。理解Spring事务，本质上是理解它设计了哪些数据结构，以及基于这些数据结构实现了什么算法。

---

## 目录

1. [阶段一：最原始的JDBC事务](#阶段一最原始的jdbc事务)
2. [阶段二：抽象事务操作 - PlatformTransactionManager](#阶段二抽象事务操作---platformtransactionmanager)
3. [阶段三：事务配置 - TransactionDefinition](#阶段三事务配置---transactiondefinition)
4. [阶段四：事务状态 - TransactionStatus](#阶段四事务状态---transactionstatus)
5. [阶段五：事务属性扩展 - TransactionAttribute](#阶段五事务属性扩展---transactionattribute)
6. [阶段六：回滚规则 - RuleBasedTransactionAttribute](#阶段六回滚规则---rulebasedtransactionattribute)
7. [阶段七：线程上下文存储 - TransactionSynchronizationManager](#阶段七线程上下文存储---transactionsynchronizationmanager)
8. [阶段八：资源持有 - ConnectionHolder](#阶段八资源持有---connectionholder)
9. [阶段九：连接工具 - DataSourceUtils](#阶段九连接工具---datasourceutils)
10. [阶段十：保存点管理 - SavepointManager](#阶段十保存点管理---savepointmanager)
11. [阶段十一：JDBC事务管理器 - DataSourceTransactionManager](#阶段十一jdbc事务管理器---datasourcetransactionmanager)
12. [阶段十二：传播行为实现 - AbstractPlatformTransactionManager](#阶段十二传播行为实现---abstractplatformtransactionmanager)
13. [阶段十三：AOP拦截 - TransactionInterceptor](#阶段十三aop拦截---transactioninterceptor)
14. [阶段十四：注解解析 - TransactionAttributeSource](#阶段十四注解解析---transactionattributesource)
15. [阶段十五：事务回调 - TransactionSynchronization](#阶段十五事务回调---transactionsynchronization)
16. [完整执行流程总结](#完整执行流程总结)
17. [核心类关系总览](#核心类关系总览)

---

## 阶段一：最原始的JDBC事务

### 1.1 原始代码

假设我们要实现一个转账功能：

```java
public void transfer(Long fromId, Long toId, BigDecimal amount) {
    Connection conn = null;
    try {
        // 1. 获取连接
        conn = dataSource.getConnection();
        
        // 2. 开启事务
        conn.setAutoCommit(false);
        
        // 3. 设置隔离级别
        conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        
        // 4. 执行业务
        deduct(conn, fromId, amount);  // 扣款
        add(conn, toId, amount);       // 加款
        
        // 5. 提交事务
        conn.commit();
        
    } catch (Exception e) {
        // 6. 回滚事务
        if (conn != null) {
            conn.rollback();
        }
        throw e;
    } finally {
        // 7. 关闭连接
        if (conn != null) {
            conn.close();
        }
    }
}

private void deduct(Connection conn, Long id, BigDecimal amount) throws SQLException {
    PreparedStatement ps = conn.prepareStatement("UPDATE account SET balance = balance - ? WHERE id = ?");
    ps.setBigDecimal(1, amount);
    ps.setLong(2, id);
    ps.executeUpdate();
    ps.close();
}

private void add(Connection conn, Long id, BigDecimal amount) throws SQLException {
    PreparedStatement ps = conn.prepareStatement("UPDATE account SET balance = balance + ? WHERE id = ?");
    ps.setBigDecimal(1, amount);
    ps.setLong(2, id);
    ps.executeUpdate();
    ps.close();
}
```

### 1.2 存在的问题

| 问题 | 描述 |
|------|------|
| **代码重复** | 每个事务方法都要写相同的try-catch-finally结构 |
| **方法间共享Connection** | `deduct`和`add`方法需要传递Connection参数，污染方法签名 |
| **事务属性硬编码** | 隔离级别、超时等硬编码在代码中 |
| **无法处理传播行为** | 如果`deduct`调用另一个方法，如何共享事务？ |
| **无法判断异常回滚** | 哪些异常回滚？哪些不回滚？ |
| **多数据源支持差** | 如果涉及多个数据库，事务管理更复杂 |

### 1.3 核心问题提炼

**问题1：如何让业务方法不感知Connection？**

如果`deduct`和`add`方法不需要传递Connection参数，那该多好：

```java
// 理想状态
public void transfer(Long fromId, Long toId, BigDecimal amount) {
    deduct(fromId, amount);  // 不需要传Connection
    add(toId, amount);       // 不需要传Connection
}

private void deduct(Long id, BigDecimal amount) {
    // 方法内部自己获取Connection
    // 但如何保证和transfer用同一个Connection？
}
```

**问题2：不同的事务实现方式差异巨大**

```java
// JDBC事务
Connection conn = dataSource.getConnection();
conn.setAutoCommit(false);
conn.commit();

// JTA分布式事务
UserTransaction ut = getUserTransaction();
ut.begin();
ut.commit();

// Hibernate事务
Session session = sessionFactory.openSession();
Transaction tx = session.beginTransaction();
tx.commit();

// JPA事务
EntityManager em = entityManagerFactory.createEntityManager();
EntityTransaction tx = em.getTransaction();
tx.begin();
tx.commit();
```

**能不能统一抽象？**

---

## 阶段二：抽象事务操作 - PlatformTransactionManager

### 2.1 问题：不同事务实现方式差异大

上面展示了四种不同的事务实现方式，它们的API完全不同：
- JDBC用`Connection`
- JTA用`UserTransaction`
- Hibernate用`Session`和`Transaction`
- JPA用`EntityManager`和`EntityTransaction`

**如何统一？**

### 2.2 思考：事务的本质是什么？

无论哪种实现，事务都只有三个核心操作：

| 操作 | 说明 |
|------|------|
| **获取/开始事务** | 开始一个新事务或加入已有事务 |
| **提交事务** | 持久化所有更改 |
| **回滚事务** | 撤销所有更改 |

### 2.3 接口设计

Spring设计了`PlatformTransactionManager`接口：

```java
public interface PlatformTransactionManager extends TransactionManager {
    
    /**
     * 根据事务定义，获取一个事务状态对象
     * 可能创建新事务，也可能加入已有事务
     */
    TransactionStatus getTransaction(@Nullable TransactionDefinition definition) 
        throws TransactionException;
    
    /**
     * 提交事务
     */
    void commit(TransactionStatus status) throws TransactionException;
    
    /**
     * 回滚事务
     */
    void rollback(TransactionStatus status) throws TransactionException;
}
```

### 2.4 数据结构分析

**问题：PlatformTransactionManager接口有数据结构吗？**

**答案：没有！这是一个纯行为定义的接口。**

它只定义了三个方法，没有任何字段。这是**策略模式**的体现：
- 接口定义行为契约
- 不同实现类提供不同的实现策略

### 2.5 继承体系

```
PlatformTransactionManager (接口)
        ↑
        ├── AbstractPlatformTransactionManager (抽象类，实现通用逻辑)
        │       ↑
        │       ├── DataSourceTransactionManager (JDBC)
        │       ├── HibernateTransactionManager (Hibernate)
        │       ├── JpaTransactionManager (JPA)
        │       └── JtaTransactionManager (JTA分布式事务)
        │
        └── 其他实现...
```

### 2.6 使用示例

```java
// 使用Spring事务管理器后
public void transfer(Long fromId, Long toId, BigDecimal amount) {
    // 获取事务管理器（通常注入）
    PlatformTransactionManager tm = getTransactionManager();
    
    // 定义事务属性
    DefaultTransactionDefinition def = new DefaultTransactionDefinition();
    def.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    
    // 获取事务状态
    TransactionStatus status = tm.getTransaction(def);
    
    try {
        // 执行业务
        deduct(fromId, amount);
        add(toId, amount);
        
        // 提交
        tm.commit(status);
    } catch (Exception e) {
        // 回滚
        tm.rollback(status);
        throw e;
    }
}
```

**问题：这样写还是很繁琐啊？别急，后面会讲到AOP自动处理。**

---

## 阶段三：事务配置 - TransactionDefinition

### 3.1 问题：如何配置事务属性？

事务有很多属性需要配置：

```java
@Transactional(
    propagation = Propagation.REQUIRED,      // 传播行为
    isolation = Isolation.READ_COMMITTED,    // 隔离级别
    timeout = 30,                            // 超时时间
    readOnly = false,                        // 是否只读
    rollbackFor = Exception.class,           // 回滚异常
    noRollbackFor = BusinessException.class  // 不回滚异常
)
```

这些配置如何传递给事务管理器？

### 3.2 接口设计

Spring设计了`TransactionDefinition`接口：

```java
public interface TransactionDefinition {
    
    // ========== 传播行为常量 ==========
    int PROPAGATION_REQUIRED = 0;          // 有则加入，无则新建（默认）
    int PROPAGATION_SUPPORTS = 1;          // 有则用，无则无
    int PROPAGATION_MANDATORY = 2;         // 必须有，否则抛异常
    int PROPAGATION_REQUIRES_NEW = 3;      // 挂起当前，新建事务
    int PROPAGATION_NOT_SUPPORTED = 4;     // 挂起当前，非事务执行
    int PROPAGATION_NEVER = 5;             // 必须无，否则抛异常
    int PROPAGATION_NESTED = 6;            // 嵌套事务（Savepoint）
    
    // ========== 隔离级别常量 ==========
    int ISOLATION_DEFAULT = -1;            // 使用数据库默认
    int ISOLATION_READ_UNCOMMITTED = 1;    // 读未提交
    int ISOLATION_READ_COMMITTED = 2;      // 读已提交
    int ISOLATION_REPEATABLE_READ = 4;     // 可重复读
    int ISOLATION_SERIALIZABLE = 8;        // 串行化
    
    // ========== 超时常量 ==========
    int TIMEOUT_DEFAULT = -1;              // 使用默认超时
    
    // ========== 获取方法 ==========
    default int getPropagationBehavior() {
        return PROPAGATION_REQUIRED;
    }
    
    default int getIsolationLevel() {
        return ISOLATION_DEFAULT;
    }
    
    default int getTimeout() {
        return TIMEOUT_DEFAULT;
    }
    
    default boolean isReadOnly() {
        return false;
    }
    
    @Nullable
    default String getName() {
        return null;
    }
}
```

### 3.3 数据结构分析

**问题：TransactionDefinition的数据结构是什么？**

**答案：常量定义 + 行为方法**

它没有实例字段，但定义了一组常量和默认方法。这是**配置模式**的体现：
- 常量定义了可能的取值范围
- 方法定义了获取配置的方式

### 3.4 传播行为详解

| 传播行为 | 当前无事务 | 当前有事务 | 使用场景 |
|---------|-----------|-----------|---------|
| **REQUIRED** | 新建事务 | 加入当前事务 | 默认，最常用 |
| **SUPPORTS** | 非事务执行 | 加入当前事务 | 查询方法 |
| **MANDATORY** | 抛异常 | 加入当前事务 | 必须在事务中调用 |
| **REQUIRES_NEW** | 新建事务 | 挂起当前，新建事务 | 独立日志记录 |
| **NOT_SUPPORTED** | 非事务执行 | 挂起当前，非事务执行 | 耗时操作 |
| **NEVER** | 非事务执行 | 抛异常 | 必须在非事务中调用 |
| **NESTED** | 新建事务 | 创建Savepoint | 部分回滚 |

### 3.5 隔离级别详解

| 隔离级别 | 脏读 | 不可重复读 | 幻读 | 说明 |
|---------|-----|-----------|-----|------|
| **READ_UNCOMMITTED** | ✓ | ✓ | ✓ | 性能最高，数据一致性最差 |
| **READ_COMMITTED** | ✗ | ✓ | ✓ | Oracle默认，避免脏读 |
| **REPEATABLE_READ** | ✗ | ✗ | ✓ | MySQL默认，避免脏读和不可重复读 |
| **SERIALIZABLE** | ✗ | ✗ | ✗ | 性能最低，数据一致性最好 |

### 3.6 默认实现 - DefaultTransactionDefinition

```java
public class DefaultTransactionDefinition implements TransactionDefinition, Serializable {
    
    // ========== 实例字段 ==========
    private int propagationBehavior = PROPAGATION_REQUIRED;
    private int isolationLevel = ISOLATION_DEFAULT;
    private int timeout = TIMEOUT_DEFAULT;
    private boolean readOnly = false;
    @Nullable
    private String name;
    
    // ========== setter方法 ==========
    public void setPropagationBehavior(int propagationBehavior) {
        this.propagationBehavior = propagationBehavior;
    }
    
    public void setIsolationLevel(int isolationLevel) {
        this.isolationLevel = isolationLevel;
    }
    
    // ... 其他setter
}
```

**内存布局**：

```
DefaultTransactionDefinition对象
┌────────────────────────────────────────────┐
│ propagationBehavior: int = 0 (REQUIRED)    │  4字节
├────────────────────────────────────────────┤
│ isolationLevel: int = -1 (DEFAULT)         │  4字节
├────────────────────────────────────────────┤
│ timeout: int = -1 (DEFAULT)                │  4字节
├────────────────────────────────────────────┤
│ readOnly: boolean = false                  │  1字节
├────────────────────────────────────────────┤
│ name: String = null                        │  引用（4或8字节）
└────────────────────────────────────────────┘

总计约 20-30 字节（不含对象头）
```

---

## 阶段四：事务状态 - TransactionStatus

### 4.1 问题：如何表示事务的运行状态？

调用`getTransaction()`后，需要知道：
- 这是一个新事务，还是加入了已有事务？
- 事务是否已被标记为回滚？
- 是否有Savepoint？
- 事务是否已完成？

### 4.2 接口设计

```java
public interface TransactionStatus extends SavepointManager {
    
    /**
     * 是否是新事务
     * 如果是false，表示加入了已有事务
     */
    boolean isNewTransaction();
    
    /**
     * 是否有保存点（NESTED传播行为）
     */
    boolean hasSavepoint();
    
    /**
     * 标记事务为只回滚
     * 调用后，事务最终会回滚，即使后续没有异常
     */
    void setRollbackOnly();
    
    /**
     * 事务是否已被标记为回滚
     */
    boolean isRollbackOnly();
    
    /**
     * 刷新底层资源（用于Hibernate/JPA等）
     */
    void flush();
    
    /**
     * 事务是否已完成（提交或回滚）
     */
    boolean isCompleted();
}
```

### 4.3 为什么继承SavepointManager？

```java
public interface SavepointManager {
    
    /**
     * 创建保存点
     */
    Object createSavepoint() throws TransactionException;
    
    /**
     * 回滚到保存点
     */
    void rollbackToSavepoint(Object savepoint) throws TransactionException;
    
    /**
     * 释放保存点
     */
    void releaseSavepoint(Object savepoint) throws TransactionException;
}
```

**原因**：NESTED传播行为需要Savepoint支持。通过继承，TransactionStatus自然获得了Savepoint操作能力。

### 4.4 默认实现 - DefaultTransactionStatus

```java
public class DefaultTransactionStatus implements TransactionStatus {
    
    // ========== 核心字段 ==========
    @Nullable
    private final Object transaction;  // 事务对象（如DataSourceTransactionObject）
    
    private final boolean newTransaction;      // 是否新事务
    private final boolean newSynchronization;  // 是否新同步
    private final boolean readOnly;            // 是否只读
    private final boolean debug;               // 是否调试模式
    
    @Nullable
    private SuspendedResourcesHolder suspendedResources;  // 挂起的资源（REQUIRES_NEW时）
    
    // ========== 状态字段 ==========
    private boolean rollbackOnly = false;  // 回滚标记
    private boolean completed = false;     // 完成标记
    
    @Nullable
    private Object savepoint;  // 保存点
    
    // ========== 关键方法 ==========
    public void setRollbackOnly() {
        this.rollbackOnly = true;
    }
    
    public boolean isRollbackOnly() {
        return this.rollbackOnly;
    }
    
    public void setCompleted() {
        this.completed = true;
    }
    
    public boolean isCompleted() {
        return this.completed;
    }
}
```

### 4.5 数据结构分析

**内存布局**：

```
DefaultTransactionStatus对象
┌─────────────────────────────────────────────────────┐
│ transaction: Object                                  │  引用（事务对象）
│   └── DataSourceTransactionObject                   │
│       └── ConnectionHolder                          │
│           └── Connection (JDBC连接)                 │
├─────────────────────────────────────────────────────┤
│ newTransaction: boolean                             │  1字节
├─────────────────────────────────────────────────────┤
│ newSynchronization: boolean                         │  1字节
├─────────────────────────────────────────────────────┤
│ readOnly: boolean                                   │  1字节
├─────────────────────────────────────────────────────┤
│ debug: boolean                                      │  1字节
├─────────────────────────────────────────────────────┤
│ suspendedResources: SuspendedResourcesHolder        │  引用（可能为null）
│   ├── suspendedResources: Object                    │
│   ├── suspendedSynchronizations: List               │
│   └── ...                                           │
├─────────────────────────────────────────────────────┤
│ rollbackOnly: boolean                               │  1字节
├─────────────────────────────────────────────────────┤
│ completed: boolean                                  │  1字节
├─────────────────────────────────────────────────────┤
│ savepoint: Object                                   │  引用（Savepoint对象）
└─────────────────────────────────────────────────────┘
```

---

## 阶段五：事务属性扩展 - TransactionAttribute

### 5.1 问题：TransactionDefinition还不够

`TransactionDefinition`只定义了事务的基本属性，但还需要：
- 哪些异常需要回滚？
- 哪些异常不需要回滚？
- 使用哪个事务管理器？

### 5.2 接口设计

```java
public interface TransactionAttribute extends TransactionDefinition {
    
    /**
     * 返回事务管理器的名称
     * 用于多事务管理器场景
     */
    @Nullable
    String getQualifier();
    
    /**
     * 判断给定异常是否需要回滚
     */
    boolean rollbackOn(Throwable ex);
}
```

### 5.3 默认实现 - DefaultTransactionAttribute

```java
public class DefaultTransactionAttribute extends DefaultTransactionDefinition 
        implements TransactionAttribute {
    
    // ========== 扩展字段 ==========
    @Nullable
    private String qualifier;  // 事务管理器名称
    
    @Nullable
    private String descriptor;  // 方法描述（用于日志）
    
    // ========== 回滚判断 ==========
    @Override
    public boolean rollbackOn(Throwable ex) {
        // 默认：RuntimeException和Error回滚
        return (ex instanceof RuntimeException || ex instanceof Error);
    }
}
```

**问题：为什么默认只回滚RuntimeException和Error？**

**答案**：
- `RuntimeException`：未检查异常，通常表示程序错误
- `Error`：严重错误，如OutOfMemoryError
- `Checked Exception`：通常表示业务异常，可能是预期内的，所以默认不回滚

### 5.4 继承体系

```
TransactionDefinition (接口)
        ↑
DefaultTransactionDefinition (实现类)
        ↑
TransactionAttribute (接口，扩展TransactionDefinition)
        ↑
DefaultTransactionAttribute (实现类)
        ↑
RuleBasedTransactionAttribute (实现类，支持回滚规则)
```

---

## 阶段六：回滚规则 - RuleBasedTransactionAttribute

### 6.1 问题：如何指定哪些异常回滚？

```java
@Transactional(
    rollbackFor = {SQLException.class, IOException.class},      // 这些异常回滚
    noRollbackFor = {BusinessException.class, UserException.class}  // 这些异常不回滚
)
```

这些规则如何表示和处理？

### 6.2 回滚规则的数据结构

Spring设计了`RollbackRuleAttribute`类：

```java
public class RollbackRuleAttribute implements Serializable {
    
    // 异常类名（注意是String，不是Class）
    private final String exceptionName;
    
    // 异常深度（用于匹配）
    private int depth = 0;
    
    public RollbackRuleAttribute(Class<?> clazz) {
        this.exceptionName = clazz.getName();
    }
    
    public RollbackRuleAttribute(String exceptionName) {
        this.exceptionName = exceptionName;
    }
    
    /**
     * 计算异常深度
     * 返回-1表示不匹配
     * 返回>=0表示匹配，数字越小表示越接近
     */
    public int getDepth(Throwable ex) {
        return getDepth(ex.getClass(), 0);
    }
    
    private int getDepth(Class<?> exceptionClass, int depth) {
        // 完全匹配
        if (exceptionClass.getName().equals(this.exceptionName)) {
            return depth;
        }
        // 找到Throwable了，还没匹配上
        if (exceptionClass == Throwable.class) {
            return -1;
        }
        // 递归查找父类
        return getDepth(exceptionClass.getSuperclass(), depth + 1);
    }
}
```

**问题：为什么用String而不是Class？**

**答案**：
1. 避免类加载问题：异常类可能不在当前ClassLoader中
2. 支持通配符：如`"javax.sql.*"`
3. 解耦：不需要依赖具体的异常类

### 6.3 RuleBasedTransactionAttribute

```java
public class RuleBasedTransactionAttribute extends DefaultTransactionAttribute {
    
    // 回滚规则列表
    @Nullable
    private List<RollbackRuleAttribute> rollbackRules;
    
    public void setRollbackRules(List<RollbackRuleAttribute> rollbackRules) {
        this.rollbackRules = rollbackRules;
    }
    
    /**
     * 根据规则判断是否回滚
     */
    @Override
    public boolean rollbackOn(Throwable ex) {
        if (this.rollbackRules == null) {
            // 没有规则，使用默认行为
            return super.rollbackOn(ex);
        }
        
        RollbackRuleAttribute winner = null;
        int deepest = Integer.MAX_VALUE;
        
        // 遍历所有规则，找到最匹配的
        for (RollbackRuleAttribute rule : this.rollbackRules) {
            int depth = rule.getDepth(ex);
            if (depth >= 0 && depth < deepest) {
                deepest = depth;
                winner = rule;
            }
        }
        
        // 没有匹配的规则，使用默认行为
        if (winner == null) {
            return super.rollbackOn(ex);
        }
        
        return !(winner instanceof NoRollbackRuleAttribute);
    }
}
```

### 6.4 匹配算法示例

假设配置：
```java
@Transactional(
    rollbackFor = Exception.class,
    noRollbackFor = BusinessException.class
)
```

规则列表：
```
rollbackRules = [
    RollbackRuleAttribute(Exception.class),      // 回滚
    NoRollbackRuleAttribute(BusinessException.class)  // 不回滚
]
```

异常继承体系：
```
Throwable
    └── Exception (depth=1)
            ├── BusinessException (depth=2, noRollback)
            │       └── UserNotFoundException (depth=3)
            └── SQLException (depth=2)
```

**测试**：

| 异常类型 | Exception规则深度 | BusinessException规则深度 | 结果 |
|---------|------------------|-------------------------|------|
| `SQLException` | 1 | -1（不匹配） | 回滚（winner=Exception规则） |
| `BusinessException` | 2 | 0 | 不回滚（winner=BusinessException规则，深度更小） |
| `UserNotFoundException` | 3 | 1 | 不回滚（winner=BusinessException规则，深度更小） |

**关键**：**深度越小越优先**，即更具体的异常规则优先级更高。

### 6.5 数据结构总结

```
@Transactional(rollbackFor = Exception.class, noRollbackFor = BusinessException.class)
                    ↓ 解析
RuleBasedTransactionAttribute
    ├── propagationBehavior = 0 (REQUIRED)
    ├── isolationLevel = -1 (DEFAULT)
    ├── timeout = -1
    ├── readOnly = false
    └── rollbackRules = [
            RollbackRuleAttribute("java.lang.Exception"),
            NoRollbackRuleAttribute("com.example.BusinessException")
        ]
```

---

## 阶段七：线程上下文存储 - TransactionSynchronizationManager

### 7.1 问题：Connection存在哪里？

回到最初的问题：如何让`deduct`和`add`方法不需要传递Connection参数？

**答案：存在ThreadLocal中！**

### 7.2 ThreadLocal回顾

```java
// 每个线程有独立的存储空间
ThreadLocal<String> threadLocal = new ThreadLocal<>();

// 线程A存储
threadLocal.set("A的数据");

// 线程B存储
threadLocal.set("B的数据");

// 线程A读取 → "A的数据"
// 线程B读取 → "B的数据"
```

**原理**：
```
Thread对象
└── threadLocals: ThreadLocalMap
        ├── key: ThreadLocal实例A → value: "A的数据"
        ├── key: ThreadLocal实例B → value: "B的数据"
        └── ...
```

### 7.3 TransactionSynchronizationManager设计

Spring设计了`TransactionSynchronizationManager`类，包含**6个ThreadLocal**：

```java
public abstract class TransactionSynchronizationManager {
    
    // ========== 六个ThreadLocal存储 ==========
    
    // ① 资源存储：Map<数据源, ConnectionHolder>
    private static final ThreadLocal<Map<Object, Object>> resources =
        new NamedThreadLocal<>("Transactional resources");
    
    // ② 同步回调：Set<TransactionSynchronization>
    private static final ThreadLocal<Set<TransactionSynchronization>> synchronizations =
        new NamedThreadLocal<>("Transaction synchronizations");
    
    // ③ 当前事务名称
    private static final ThreadLocal<String> currentTransactionName =
        new NamedThreadLocal<>("Current transaction name");
    
    // ④ 当前事务是否只读
    private static final ThreadLocal<Boolean> currentTransactionReadOnly =
        new NamedThreadLocal<>("Current transaction read-only status");
    
    // ⑤ 当前事务隔离级别
    private static final ThreadLocal<Integer> currentTransactionIsolationLevel =
        new NamedThreadLocal<>("Current transaction isolation level");
    
    // ⑥ 当前是否有实际事务活跃
    private static final ThreadLocal<Boolean> actualTransactionActive =
        new NamedThreadLocal<>("Actual transaction active");
}
```

### 7.4 为什么是Map<Object, Object>？

**问题：为什么`resources`是`ThreadLocal<Map<Object, Object>>`而不是`ThreadLocal<Connection>`？**

**答案：支持多数据源！**

```java
// 多数据源场景
@Bean
public DataSource dataSource1() { ... }

@Bean
public DataSource dataSource2() { ... }

@Transactional
public void method() {
    // 操作数据源1
    jdbcTemplate1.update(...);
    
    // 操作数据源2
    jdbcTemplate2.update(...);
}
```

**存储结构**：

```
ThreadLocal<Map<Object, Object>> resources
└── Thread[main]
        └── HashMap
                ├── key: dataSource1 → value: ConnectionHolder1
                └── key: dataSource2 → value: ConnectionHolder2
```

### 7.5 核心方法

```java
// 绑定资源
public static void bindResource(Object key, Object value) throws IllegalStateException {
    Object actualKey = TransactionSynchronizationUtils.unwrapResourceIfNecessary(key);
    Assert.notNull(value, "Value must not be null");
    
    Map<Object, Object> map = resources.get();
    if (map == null) {
        map = new HashMap<>();
        resources.set(map);
    }
    
    Object oldValue = map.put(actualKey, value);
    if (oldValue != null) {
        throw new IllegalStateException("Already value [" + oldValue + "] for key [" + actualKey + "]");
    }
}

// 获取资源
@Nullable
public static Object getResource(Object key) {
    Object actualKey = TransactionSynchronizationUtils.unwrapResourceIfNecessary(key);
    Map<Object, Object> map = resources.get();
    if (map == null) {
        return null;
    }
    return map.get(actualKey);
}

// 解绑资源
public static Object unbindResource(Object key) throws IllegalStateException {
    Object actualKey = TransactionSynchronizationUtils.unwrapResourceIfNecessary(key);
    Object value = doUnbindResource(actualKey);
    if (value == null) {
        throw new IllegalStateException("No value for key [" + actualKey + "] bound to thread");
    }
    return value;
}

private static Object doUnbindResource(Object actualKey) {
    Map<Object, Object> map = resources.get();
    if (map == null) {
        return null;
    }
    Object value = map.remove(actualKey);
    // 清空后移除ThreadLocal，防止内存泄漏
    if (map.isEmpty()) {
        resources.remove();
    }
    return value;
}
```

### 7.6 内存结构可视化

```
线程1的ThreadLocal存储
┌─────────────────────────────────────────────────────────┐
│ resources                                               │
│   └── HashMap                                           │
│       ├── DataSource@abc123 → ConnectionHolder{         │
│       │                       connection: JDBC Connection│
│       │                       transactionActive: true    │
│       │                       referenceCount: 1          │
│       │                     }                            │
│       └── DataSource@def456 → ConnectionHolder{...}     │
├─────────────────────────────────────────────────────────┤
│ synchronizations                                        │
│   └── LinkedHashSet                                     │
│       ├── DataSourceSynchronization@1                   │
│       ├── HibernateSynchronization@2                    │
│       └── UserCustomSynchronization@3                   │
├─────────────────────────────────────────────────────────┤
│ currentTransactionName → "com.example.service.transfer" │
├─────────────────────────────────────────────────────────┤
│ currentTransactionReadOnly → Boolean.FALSE              │
├─────────────────────────────────────────────────────────┤
│ currentTransactionIsolationLevel → 2 (READ_COMMITTED)   │
├─────────────────────────────────────────────────────────┤
│ actualTransactionActive → Boolean.TRUE                  │
└─────────────────────────────────────────────────────────┘
```

---

## 阶段八：资源持有 - ConnectionHolder

### 8.1 问题：为什么需要包装Connection？

直接把Connection存在ThreadLocal不行吗？

**答案：不够！还需要额外信息。**

### 8.2 ConnectionHolder设计

```java
public class ConnectionHolder extends ResourceHolderSupport {
    
    // ========== 核心字段 ==========
    @Nullable
    private Connection currentConnection;  // 当前Connection
    
    @Nullable
    private ConnectionHandle connectionHandle;  // Connection句柄（用于包装）
    
    private boolean transactionActive = false;  // 事务是否活跃
    
    @Nullable
    private Boolean savepointsSupported;  // 是否支持Savepoint
    
    private int referenceCount = 0;  // 引用计数
    
    private boolean isCloseSuppressed = false;  // 是否抑制关闭
}
```

### 8.3 引用计数的作用

**场景：REQUIRED传播行为，多个方法共享事务**

```java
@Transactional
public void methodA() {
    // 获取Connection，referenceCount = 1
    userDao.save(user);
    
    methodB();  // 调用另一个@Transactional方法
    
    // 继续使用同一个Connection
    logDao.save(log);
    
    // methodA结束，referenceCount = 0，提交事务
}

@Transactional
public void methodB() {
    // 加入已有事务，referenceCount = 2
    orderDao.save(order);
    
    // methodB结束，referenceCount = 1，不提交，等待外层
}
```

**关键**：
- `referenceCount`记录有多少个方法在使用这个Connection
- 只有`referenceCount = 0`时才真正提交/回滚

### 8.4 ResourceHolderSupport基类

```java
public abstract class ResourceHolderSupport implements ResourceHolder {
    
    // ========== 字段 ==========
    private boolean synchronizedWithTransaction = false;  // 是否与事务同步
    
    @Nullable
    private Date deadline;  // 超时截止时间
    
    private int referenceCount = 0;  // 引用计数
    
    private boolean isRollbackOnly = false;  // 是否标记回滚
    
    private volatile boolean isVoid = false;  // 是否无效
    
    // ========== 引用计数方法 ==========
    public void requested() {
        this.referenceCount++;
    }
    
    public void released() {
        this.referenceCount--;
    }
    
    public boolean isAvailable() {
        return this.referenceCount > 0;
    }
    
    // ========== 超时方法 ==========
    public void setTimeoutInSeconds(int seconds) {
        this.deadline = new Date(System.currentTimeMillis() + seconds * 1000L);
    }
    
    public int getTimeToLiveInSeconds() {
        if (this.deadline == null) {
            return -1;
        }
        long diff = this.deadline.getTime() - System.currentTimeMillis();
        return (int) (diff / 1000);
    }
    
    public boolean hasTimeout() {
        return this.deadline != null;
    }
}
```

### 8.5 数据结构关系

```
ResourceHolderSupport (抽象基类)
        ├── referenceCount: int          引用计数
        ├── deadline: Date               超时时间
        ├── isRollbackOnly: boolean      回滚标记
        └── synchronizedWithTransaction: boolean
        ↑
ConnectionHolder (JDBC实现)
        ├── currentConnection: Connection    JDBC连接
        ├── transactionActive: boolean       事务活跃标志
        ├── savepointsSupported: Boolean     是否支持Savepoint
        └── 继承自父类的字段...
```

---

## 阶段九：连接工具 - DataSourceUtils

### 9.1 问题：业务代码如何获取Connection？

业务代码使用JdbcTemplate或MyBatis时，它们如何获取Connection？

**答案：通过`DataSourceUtils`！**

### 9.2 DataSourceUtils设计

```java
public abstract class DataSourceUtils {
    
    /**
     * 获取Connection（事务感知）
     */
    public static Connection getConnection(DataSource dataSource) throws CannotGetJdbcConnectionException {
        try {
            return doGetConnection(dataSource);
        } catch (SQLException ex) {
            throw new CannotGetJdbcConnectionException("Failed to obtain JDBC Connection", ex);
        }
    }
    
    public static Connection doGetConnection(DataSource dataSource) throws SQLException {
        // 1. 先从ThreadLocal中查找
        ConnectionHolder conHolder = (ConnectionHolder) 
            TransactionSynchronizationManager.getResource(dataSource);
        
        // 2. 如果存在且事务活跃，复用Connection
        if (conHolder != null && 
                (conHolder.hasConnection() || conHolder.isSynchronizedWithTransaction())) {
            
            conHolder.requested();  // 引用计数+1
            
            if (!conHolder.hasConnection()) {
                conHolder.setConnection(dataSource.getConnection());
            }
            
            return conHolder.getConnection();
        }
        
        // 3. 不存在，从DataSource获取新Connection
        Connection con = dataSource.getConnection();
        
        // 4. 如果有事务，绑定到ThreadLocal
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            ConnectionHolder holderToUse = new ConnectionHolder(con);
            holderToUse.requested();
            TransactionSynchronizationManager.bindResource(dataSource, holderToUse);
            return con;
        }
        
        // 5. 无事务，返回新Connection
        return con;
    }
    
    /**
     * 释放Connection
     */
    public static void releaseConnection(@Nullable Connection con, @Nullable DataSource dataSource) {
        try {
            doReleaseConnection(con, dataSource);
        } catch (SQLException ex) {
            logger.debug("Could not close JDBC Connection", ex);
        }
    }
    
    public static void doReleaseConnection(@Nullable Connection con, @Nullable DataSource dataSource) throws SQLException {
        if (con == null) {
            return;
        }
        
        // 1. 检查是否是事务Connection
        ConnectionHolder conHolder = (ConnectionHolder) 
            TransactionSynchronizationManager.getResource(dataSource);
        
        if (conHolder != null && con == conHolder.getConnection()) {
            // 2. 是事务Connection，引用计数-1，不真正关闭
            conHolder.released();
            return;
        }
        
        // 3. 不是事务Connection，真正关闭
        con.close();
    }
}
```

### 9.3 执行流程示例

```
methodA()调用（有@Transactional）
    │
    ├── TransactionInterceptor开启事务
    │   ├── doGetConnection() → 新Connection
    │   ├── conn.setAutoCommit(false)
    │   └── bindResource(dataSource, ConnectionHolder)
    │
    ├── userDao.save(user)
    │   └── JdbcTemplate.execute()
    │       └── DataSourceUtils.getConnection(dataSource)
    │           └── 从ThreadLocal获取ConnectionHolder
    │           └── holder.requested() → referenceCount = 1
    │           └── 返回Connection
    │       └── 执行SQL
    │       └── DataSourceUtils.releaseConnection()
    │           └── holder.released() → referenceCount = 0
    │           └── 不关闭，因为还在事务中
    │
    └── TransactionInterceptor提交事务
        ├── conn.commit()
        ├── unbindResource()
        └── conn.close()
```

---

## 阶段十：保存点管理 - SavepointManager

### 10.1 问题：NESTED传播行为如何实现？

NESTED传播行为需要：
1. 在当前事务中创建Savepoint
2. 回滚时只回滚到Savepoint
3. 提交时释放Savepoint

### 10.2 SavepointManager接口

```java
public interface SavepointManager {
    
    /**
     * 创建保存点
     */
    Object createSavepoint() throws TransactionException;
    
    /**
     * 回滚到保存点
     */
    void rollbackToSavepoint(Object savepoint) throws TransactionException;
    
    /**
     * 释放保存点
     */
    void releaseSavepoint(Object savepoint) throws TransactionException;
}
```

### 10.3 JDBC实现 - ConnectionHolder

```java
public class ConnectionHolder extends ResourceHolderSupport {
    
    // ========== Savepoint支持 ==========
    
    public boolean isSavepointAllowed() {
        return this.savepointsSupported != null && this.savepointsSupported;
    }
    
    public void setSavepointAllowed(boolean savepointsSupported) {
        this.savepointsSupported = savepointsSupported;
    }
    
    /**
     * 创建Savepoint
     */
    public Object createSavepoint() throws SQLException {
        if (!isSavepointAllowed()) {
            throw new NestedTransactionNotSupportedException(
                "Cannot create a savepoint because the JDBC driver does not support savepoints");
        }
        
        Connection con = getConnection();
        
        // 测试是否支持Savepoint
        if (this.savepointsSupported == null) {
            try {
                con.setSavepoint("test");
                this.savepointsSupported = true;
            } catch (SQLException ex) {
                this.savepointsSupported = false;
                throw new NestedTransactionNotSupportedException(
                    "JDBC driver does not support savepoints", ex);
            }
        }
        
        // 创建Savepoint
        return con.setSavepoint();
    }
    
    /**
     * 回滚到Savepoint
     */
    public void rollbackToSavepoint(Object savepoint) throws SQLException {
        getConnection().rollback((Savepoint) savepoint);
    }
    
    /**
     * 释放Savepoint
     */
    public void releaseSavepoint(Object savepoint) throws SQLException {
        getConnection().releaseSavepoint((Savepoint) savepoint);
    }
}
```

### 10.4 使用示例

```java
// NESTED传播行为的实现
@Transactional(propagation = Propagation.NESTED)
public void nestedMethod() {
    // AbstractPlatformTransactionManager中的处理
    
    // 1. 创建Savepoint
    Object savepoint = connectionHolder.createSavepoint();
    
    // 2. 设置到TransactionStatus
    status.setSavepoint(savepoint);
    
    try {
        // 3. 执行业务方法
        invocation.proceed();
        
        // 4. 成功，释放Savepoint
        connectionHolder.releaseSavepoint(savepoint);
        
    } catch (Exception e) {
        // 5. 失败，回滚到Savepoint
        connectionHolder.rollbackToSavepoint(savepoint);
        throw e;
    }
}
```

---

## 阶段十一：JDBC事务管理器 - DataSourceTransactionManager

### 11.1 整体结构

```java
public class DataSourceTransactionManager extends AbstractPlatformTransactionManager
        implements ResourceTransactionManager, InitializingBean {
    
    // ========== 核心字段 ==========
    @Nullable
    private DataSource dataSource;  // 数据源
    
    private boolean enforceReadOnly = false;  // 是否强制只读
}
```

### 11.2 事务对象 - DataSourceTransactionObject

```java
// DataSourceTransactionManager内部类
private static class DataSourceTransactionObject extends JdbcTransactionObjectSupport {
    
    // ========== 字段 ==========
    @Nullable
    private ConnectionHolder connectionHolder;  // Connection包装器
    
    private boolean newConnectionHolder;  // 是否是新创建的Holder
    
    private boolean mustRestoreAutoCommit;  // 是否需要恢复自动提交
    
    // ========== 方法 ==========
    public void setConnectionHolder(@Nullable ConnectionHolder connectionHolder, boolean newConnectionHolder) {
        this.connectionHolder = connectionHolder;
        this.newConnectionHolder = newConnectionHolder;
    }
    
    public boolean hasConnectionHolder() {
        return this.connectionHolder != null;
    }
    
    public boolean isNewConnectionHolder() {
        return this.newConnectionHolder;
    }
    
    public boolean isMustRestoreAutoCommit() {
        return this.mustRestoreAutoCommit;
    }
}
```

### 11.3 核心方法实现

#### doGetTransaction - 获取事务对象

```java
@Override
protected Object doGetTransaction() {
    DataSourceTransactionObject txObject = new DataSourceTransactionObject();
    
    // 从ThreadLocal获取已有的ConnectionHolder
    txObject.setConnectionHolder(
        (ConnectionHolder) TransactionSynchronizationManager.getResource(obtainDataSource()),
        false  // false表示不是新创建的
    );
    
    return txObject;
}
```

#### isExistingTransaction - 检查是否已有事务

```java
@Override
protected boolean isExistingTransaction(Object transaction) {
    DataSourceTransactionObject txObject = (DataSourceTransactionObject) transaction;
    
    // 检查ConnectionHolder是否存在且事务活跃
    return (txObject.hasConnectionHolder() && 
            txObject.getConnectionHolder().isTransactionActive());
}
```

#### doBegin - 开始事务

```java
@Override
protected void doBegin(Object transaction, TransactionDefinition definition) {
    DataSourceTransactionObject txObject = (DataSourceTransactionObject) transaction;
    Connection con = null;
    
    try {
        // 1. 如果没有ConnectionHolder，创建新的
        if (!txObject.hasConnectionHolder() ||
                txObject.getConnectionHolder().isSynchronizedWithTransaction()) {
            Connection newCon = obtainDataSource().getConnection();
            txObject.setConnectionHolder(new ConnectionHolder(newCon), true);
        }
        
        txObject.getConnectionHolder().setSynchronizedWithTransaction(true);
        con = txObject.getConnectionHolder().getConnection();
        
        // 2. 设置隔离级别
        Integer previousIsolationLevel = DataSourceUtils.prepareConnectionForTransaction(con, definition);
        txObject.setPreviousIsolationLevel(previousIsolationLevel);
        
        // 3. 关闭自动提交（开启事务）
        if (con.getAutoCommit()) {
            txObject.setMustRestoreAutoCommit(true);
            con.setAutoCommit(false);
        }
        
        // 4. 准备事务性连接（如设置只读）
        prepareTransactionalConnection(con, definition);
        
        // 5. 标记事务活跃
        txObject.getConnectionHolder().setTransactionActive(true);
        
        // 6. 设置超时
        int timeout = determineTimeout(definition);
        if (timeout != TransactionDefinition.TIMEOUT_DEFAULT) {
            txObject.getConnectionHolder().setTimeoutInSeconds(timeout);
        }
        
        // 7. 绑定到ThreadLocal（如果是新创建的Holder）
        if (txObject.isNewConnectionHolder()) {
            TransactionSynchronizationManager.bindResource(
                obtainDataSource(), txObject.getConnectionHolder());
        }
    } catch (Throwable ex) {
        // 异常处理
        if (txObject.isNewConnectionHolder()) {
            DataSourceUtils.releaseConnection(con, obtainDataSource());
            txObject.setConnectionHolder(null, false);
        }
        throw new CannotCreateTransactionException("Could not open JDBC Connection for transaction", ex);
    }
}
```

#### doCommit - 提交事务

```java
@Override
protected void doCommit(DefaultTransactionStatus status) {
    DataSourceTransactionObject txObject = (DataSourceTransactionObject) status.getTransaction();
    Connection con = txObject.getConnectionHolder().getConnection();
    
    try {
        con.commit();  // 简单的JDBC提交
    } catch (SQLException ex) {
        throw new TransactionSystemException("Could not commit JDBC transaction", ex);
    }
}
```

#### doRollback - 回滚事务

```java
@Override
protected void doRollback(DefaultTransactionStatus status) {
    DataSourceTransactionObject txObject = (DataSourceTransactionObject) status.getTransaction();
    Connection con = txObject.getConnectionHolder().getConnection();
    
    try {
        con.rollback();  // 简单的JDBC回滚
    } catch (SQLException ex) {
        throw new TransactionSystemException("Could not roll back JDBC transaction", ex);
    }
}
```

#### doCleanupAfterCompletion - 清理资源

```java
@Override
protected void doCleanupAfterCompletion(Object transaction) {
    DataSourceTransactionObject txObject = (DataSourceTransactionObject) transaction;
    
    // 1. 从ThreadLocal解绑
    if (txObject.isNewConnectionHolder()) {
        TransactionSynchronizationManager.unbindResource(obtainDataSource());
    }
    
    // 2. 恢复连接属性
    Connection con = txObject.getConnectionHolder().getConnection();
    try {
        if (txObject.isMustRestoreAutoCommit()) {
            con.setAutoCommit(true);  // 恢复自动提交
        }
        if (txObject.getPreviousIsolationLevel() != null) {
            con.setTransactionIsolation(txObject.getPreviousIsolationLevel());  // 恢复隔离级别
        }
    } catch (Throwable ex) {
        logger.debug("Could not reset JDBC Connection after transaction", ex);
    }
    
    // 3. 归还连接到连接池
    if (txObject.isNewConnectionHolder()) {
        DataSourceUtils.releaseConnection(con, obtainDataSource());
    }
    
    // 4. 清理ConnectionHolder
    txObject.getConnectionHolder().clear();
}
```

---

## 阶段十二：传播行为实现 - AbstractPlatformTransactionManager

### 12.1 模板方法模式

`AbstractPlatformTransactionManager`使用模板方法模式：
- 父类定义算法骨架（传播行为处理）
- 子类实现具体步骤（JDBC/JTA/Hibernate）

### 12.2 getTransaction - 核心方法

```java
@Override
public final TransactionStatus getTransaction(@Nullable TransactionDefinition definition)
        throws TransactionException {
    
    // 1. 获取事务对象（子类实现）
    Object transaction = doGetTransaction();
    
    // 2. 处理超时
    if (definition != null && definition.getTimeout() < TransactionDefinition.TIMEOUT_DEFAULT) {
        throw new InvalidTimeoutException("Invalid transaction timeout", definition.getTimeout());
    }
    
    // 3. 检查是否已有事务
    if (isExistingTransaction(transaction)) {
        // ========== 已有事务，根据传播行为处理 ==========
        return handleExistingTransaction(definition, transaction, debugEnabled);
    }
    
    // ========== 没有事务，检查传播行为 ==========
    
    // 3.1 MANDATORY：必须有事务
    if (definition.getPropagationBehavior() == TransactionDefinition.PROPAGATION_MANDATORY) {
        throw new IllegalTransactionStateException(
            "No existing transaction found for transaction marked with propagation 'mandatory'");
    }
    
    // 3.2 REQUIRED/REQUIRES_NEW/NESTED：创建新事务
    if (definition.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRED ||
        definition.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRES_NEW ||
        definition.getPropagationBehavior() == TransactionDefinition.PROPAGATION_NESTED) {
        
        SuspendedResourcesHolder suspendedResources = suspend(null);
        try {
            return startTransaction(definition, transaction, debugEnabled, suspendedResources);
        } catch (RuntimeException | Error ex) {
            resume(null, suspendedResources);
            throw ex;
        }
    }
    
    // 3.3 其他：非事务执行
    boolean newSynchronization = (getTransactionSynchronization() == SYNCHRONIZATION_ALWAYS);
    return prepareTransactionStatus(definition, null, true, newSynchronization, debugEnabled, null);
}
```

### 12.3 handleExistingTransaction - 处理已有事务

```java
private TransactionStatus handleExistingTransaction(
        TransactionDefinition definition, Object transaction, boolean debugEnabled)
        throws TransactionException {
    
    // ========== NEVER：抛异常 ==========
    if (definition.getPropagationBehavior() == TransactionDefinition.PROPAGATION_NEVER) {
        throw new IllegalTransactionStateException(
            "Existing transaction found for transaction marked with propagation 'never'");
    }
    
    // ========== NOT_SUPPORTED：挂起当前，非事务执行 ==========
    if (definition.getPropagationBehavior() == TransactionDefinition.PROPAGATION_NOT_SUPPORTED) {
        Object suspendedResources = suspend(transaction);
        boolean newSynchronization = (getTransactionSynchronization() == SYNCHRONIZATION_ALWAYS);
        return prepareTransactionStatus(
            definition, null, false, newSynchronization, debugEnabled, suspendedResources);
    }
    
    // ========== REQUIRES_NEW：挂起当前，创建新事务 ==========
    if (definition.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRES_NEW) {
        SuspendedResourcesHolder suspendedResources = suspend(transaction);
        try {
            return startTransaction(definition, transaction, debugEnabled, suspendedResources);
        } catch (RuntimeException | Error beginEx) {
            resumeAfterBeginException(transaction, suspendedResources, beginEx);
            throw beginEx;
        }
    }
    
    // ========== NESTED：创建Savepoint ==========
    if (definition.getPropagationBehavior() == TransactionDefinition.PROPAGATION_NESTED) {
        if (!isNestedTransactionAllowed()) {
            throw new NestedTransactionNotSupportedException(
                "Transaction manager does not allow nested transactions");
        }
        
        if (useSavepointForNestedTransaction()) {
            DefaultTransactionStatus status = prepareTransactionStatus(
                definition, transaction, false, false, debugEnabled, null);
            status.createAndHoldSavepoint();
            return status;
        } else {
            return startTransaction(definition, transaction, debugEnabled, null);
        }
    }
    
    // ========== REQUIRED/SUPPORTS/MANDATORY：加入当前事务 ==========
    boolean newSynchronization = (getTransactionSynchronization() != SYNCHRONIZATION_NEVER);
    return prepareTransactionStatus(definition, transaction, false, newSynchronization, debugEnabled, null);
}
```

### 12.4 suspend - 挂起事务

```java
protected final SuspendedResourcesHolder suspend(@Nullable Object transaction) throws TransactionException {
    
    // 1. 挂起同步回调
    List<TransactionSynchronization> suspendedSynchronizations = null;
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
        suspendedSynchronizations = TransactionSynchronizationManager.getSynchronizations();
        for (TransactionSynchronization synchronization : suspendedSynchronizations) {
            synchronization.suspend();
        }
        TransactionSynchronizationManager.clearSynchronization();
    }
    
    // 2. 挂起资源（Connection）
    Object suspendedResources = null;
    if (transaction != null) {
        suspendedResources = doSuspend(transaction);  // 子类实现
    }
    
    // 3. 挂起事务属性
    String name = TransactionSynchronizationManager.getCurrentTransactionName();
    TransactionSynchronizationManager.setCurrentTransactionName(null);
    
    boolean readOnly = TransactionSynchronizationManager.isCurrentTransactionReadOnly();
    TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
    
    Integer isolationLevel = TransactionSynchronizationManager.getCurrentTransactionIsolationLevel();
    TransactionSynchronizationManager.setCurrentTransactionIsolationLevel(null);
    
    boolean wasActive = TransactionSynchronizationManager.isActualTransactionActive();
    TransactionSynchronizationManager.setActualTransactionActive(false);
    
    // 4. 包装返回
    return new SuspendedResourcesHolder(
        suspendedResources, suspendedSynchronizations, name, readOnly, isolationLevel, wasActive);
}
```

### 12.5 resume - 恢复事务

```java
protected final void resume(@Nullable Object transaction, 
        @Nullable SuspendedResourcesHolder suspendedResources) throws TransactionException {
    
    if (suspendedResources == null) {
        return;
    }
    
    // 1. 恢复资源
    if (suspendedResources.getSuspendedResources() != null) {
        doResume(transaction, suspendedResources.getSuspendedResources());  // 子类实现
    }
    
    // 2. 恢复同步回调
    List<TransactionSynchronization> suspendedSynchronizations = 
        suspendedResources.getSuspendedSynchronizations();
    if (suspendedSynchronizations != null) {
        TransactionSynchronizationManager.setActualTransactionActive(
            suspendedResources.wasTransactionActive());
        TransactionSynchronizationManager.setCurrentTransactionIsolationLevel(
            suspendedResources.getSuspendedIsolationLevel());
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(
            suspendedResources.isReadOnly());
        TransactionSynchronizationManager.setCurrentTransactionName(
            suspendedResources.getName());
        
        TransactionSynchronizationManager.initSynchronization();
        for (TransactionSynchronization synchronization : suspendedSynchronizations) {
            synchronization.resume();
            TransactionSynchronizationManager.registerSynchronization(synchronization);
        }
    }
}
```

### 12.6 commit - 提交事务

```java
@Override
public final void commit(TransactionStatus status) throws TransactionException {
    if (status.isCompleted()) {
        throw new IllegalTransactionStateException(
            "Transaction is already completed - do not call commit or rollback more than once per transaction");
    }
    
    DefaultTransactionStatus defStatus = (DefaultTransactionStatus) status;
    
    // 1. 检查是否被标记为回滚
    if (defStatus.isLocalRollbackOnly()) {
        processRollback(defStatus, false);
        return;
    }
    
    // 2. 全局回滚标记
    if (!shouldCommitOnGlobalRollbackOnly() && defStatus.isGlobalRollbackOnly()) {
        processRollback(defStatus, true);
        return;
    }
    
    // 3. 正常提交
    processCommit(defStatus);
}

private void processCommit(DefaultTransactionStatus status) throws TransactionException {
    try {
        // 提交前回调
        triggerBeforeCommit(status);
        triggerBeforeCompletion(status);
        
        // 是否有Savepoint
        if (status.hasSavepoint()) {
            // NESTED：释放Savepoint
            status.releaseHeldSavepoint();
        } else if (status.isNewTransaction()) {
            // 新事务：真正提交
            doCommit(status);
        } else if (status.hasTransaction()) {
            // 参与的事务：如果被标记回滚，设置全局回滚
            if (status.isLocalRollbackOnly()) {
                doSetRollbackOnly(status);
            }
        }
        
        // 提交后回调
        triggerAfterCommit(status);
        triggerAfterCompletion(status, TransactionSynchronization.STATUS_COMMITTED);
        
    } catch (RuntimeException | Error ex) {
        triggerAfterCompletion(status, TransactionSynchronization.STATUS_UNKNOWN);
        throw ex;
    } finally {
        cleanupAfterCompletion(status);
    }
}
```

---

## 阶段十三：AOP拦截 - TransactionInterceptor

### 13.1 问题：如何让@Transactional自动生效？

前面的代码都需要手动调用事务管理器，如何实现声明式事务？

**答案：AOP拦截！**

### 13.2 TransactionInterceptor类

```java
public class TransactionInterceptor extends TransactionAspectSupport 
        implements MethodInterceptor, Serializable {
    
    // 实现MethodInterceptor接口（来自AOP Alliance）
    @Override
    @Nullable
    public Object invoke(MethodInvocation invocation) throws Throwable {
        
        // 1. 获取目标类
        Class<?> targetClass = (invocation.getThis() != null ? 
            AopUtils.getTargetClass(invocation.getThis()) : null);
        
        // 2. 调用父类的模板方法
        return invokeWithinTransaction(invocation.getMethod(), targetClass, 
            new CoroutinesInvocationCallback() {
                @Override
                @Nullable
                public Object proceedWithInvocation() throws Throwable {
                    return invocation.proceed();  // 执行目标方法
                }
                
                @Override
                public Object getTarget() {
                    return invocation.getThis();
                }
                
                @Override
                public Object[] getArguments() {
                    return invocation.getArguments();
                }
            });
    }
}
```

### 13.3 TransactionAspectSupport - 核心逻辑

```java
public abstract class TransactionAspectSupport implements BeanFactoryAware, InitializingBean {
    
    // ========== 核心字段 ==========
    @Nullable
    private PlatformTransactionManager transactionManager;
    
    @Nullable
    private TransactionAttributeSource transactionAttributeSource;
    
    // ThreadLocal存储当前事务信息
    private static final ThreadLocal<TransactionInfo> transactionInfoHolder =
        new NamedThreadLocal<>("Current aspect-driven transaction");
    
    // ========== 核心方法 ==========
    @Nullable
    protected Object invokeWithinTransaction(Method method, @Nullable Class<?> targetClass,
            InvocationCallback invocation) throws Throwable {
        
        // 1. 获取事务属性
        TransactionAttributeSource tas = getTransactionAttributeSource();
        TransactionAttribute txAttr = (tas != null ? 
            tas.getTransactionAttribute(method, targetClass) : null);
        
        // 2. 获取事务管理器
        PlatformTransactionManager tm = determineTransactionManager(txAttr);
        
        // 3. 构造方法标识
        String joinpointIdentification = methodIdentification(method, targetClass, txAttr);
        
        // 4. 事务处理
        if (txAttr == null || !(tm instanceof CallbackPreferringPlatformTransactionManager)) {
            
            // 4.1 创建事务
            TransactionInfo txInfo = createTransactionIfNecessary(tm, txAttr, joinpointIdentification);
            
            Object retVal = null;
            try {
                // 4.2 执行目标方法
                retVal = invocation.proceedWithInvocation();
            } catch (Throwable ex) {
                // 4.3 异常处理
                completeTransactionAfterThrowing(txInfo, ex);
                throw ex;
            } finally {
                // 4.4 清理事务信息
                cleanupTransactionInfo(txInfo);
            }
            
            // 4.5 提交事务
            commitTransactionAfterReturning(txInfo);
            return retVal;
        } else {
            // 编程式事务处理...
        }
    }
    
    // ========== 创建事务 ==========
    protected TransactionInfo createTransactionIfNecessary(
            @Nullable PlatformTransactionManager tm,
            @Nullable TransactionAttribute txAttr,
            String joinpointIdentification) {
        
        if (txAttr != null && tm != null) {
            TransactionStatus status = tm.getTransaction(txAttr);
            return prepareTransactionInfo(tm, txAttr, joinpointIdentification, status);
        }
        return prepareTransactionInfo(tm, txAttr, joinpointIdentification, null);
    }
    
    // ========== 异常处理 ==========
    protected void completeTransactionAfterThrowing(@Nullable TransactionInfo txInfo, Throwable ex) {
        if (txInfo != null && txInfo.getTransactionStatus() != null) {
            if (txInfo.transactionAttribute != null && txInfo.transactionAttribute.rollbackOn(ex)) {
                txInfo.getTransactionManager().rollback(txInfo.getTransactionStatus());
            } else {
                txInfo.getTransactionManager().commit(txInfo.getTransactionStatus());
            }
        }
    }
    
    // ========== 提交事务 ==========
    protected void commitTransactionAfterReturning(@Nullable TransactionInfo txInfo) {
        if (txInfo != null && txInfo.getTransactionStatus() != null) {
            txInfo.getTransactionManager().commit(txInfo.getTransactionStatus());
        }
    }
}
```

### 13.4 TransactionInfo - 事务信息

```java
protected static final class TransactionInfo {
    
    @Nullable
    private final PlatformTransactionManager transactionManager;
    
    @Nullable
    private final TransactionAttribute transactionAttribute;
    
    private final String joinpointIdentification;
    
    @Nullable
    private TransactionStatus transactionStatus;
    
    // ========== 关键：指向之前的事务信息（链表） ==========
    @Nullable
    private TransactionInfo oldTransactionInfo;
    
    // 绑定到ThreadLocal
    public void bindToThread() {
        this.oldTransactionInfo = transactionInfoHolder.get();
        transactionInfoHolder.set(this);
    }
    
    // 恢复之前的事务信息
    public void restoreThreadLocalStatus() {
        transactionInfoHolder.set(this.oldTransactionInfo);
    }
}
```

### 13.5 链表结构支持事务嵌套

```
调用methodA (REQUIRED):
    创建TransactionInfo A
    A.oldTransactionInfo = null
    ThreadLocal = A
    
    调用methodB (REQUIRES_NEW):
        挂起事务A
        创建TransactionInfo B
        B.oldTransactionInfo = A
        ThreadLocal = B
        
        methodB完成:
            提交事务B
            B.restoreThreadLocalStatus() → ThreadLocal = A
            恢复事务A
    
    methodA完成:
        提交事务A
        A.restoreThreadLocalStatus() → ThreadLocal = null
```

---

## 阶段十四：注解解析 - TransactionAttributeSource

### 14.1 问题：@Transactional如何解析？

```java
@Transactional(
    propagation = Propagation.REQUIRED,
    isolation = Isolation.READ_COMMITTED,
    timeout = 30,
    rollbackFor = Exception.class
)
public void transfer() { ... }
```

### 14.2 TransactionAttributeSource接口

```java
public interface TransactionAttributeSource {
    
    @Nullable
    TransactionAttribute getTransactionAttribute(Method method, @Nullable Class<?> targetClass);
}
```

### 14.3 AnnotationTransactionAttributeSource

```java
public class AnnotationTransactionAttributeSource extends AbstractFallbackTransactionAttributeSource {
    
    private final boolean publicMethodsOnly;
    
    // 注解解析器列表
    private final Set<TransactionAnnotationParser> annotationParsers;
    
    public AnnotationTransactionAttributeSource(boolean publicMethodsOnly) {
        this.publicMethodsOnly = publicMethodsOnly;
        this.annotationParsers = new LinkedHashSet<>(4);
        
        // 支持Spring的@Transactional
        this.annotationParsers.add(new SpringTransactionAnnotationParser());
        
        // 支持JTA的@javax.transaction.Transactional
        this.annotationParsers.add(new JtaTransactionAnnotationParser());
        
        // 支持EJB3的@javax.ejb.TransactionAttribute
        this.annotationParsers.add(new Ejb3TransactionAnnotationParser());
    }
}
```

### 14.4 SpringTransactionAnnotationParser

```java
public class SpringTransactionAnnotationParser implements TransactionAnnotationParser, Serializable {
    
    @Override
    @Nullable
    public TransactionAttribute parseTransactionAnnotation(AnnotatedElement element) {
        AnnotationAttributes attributes = AnnotatedElementUtils.findMergedAnnotationAttributes(
            element, Transactional.class, false, false);
        
        if (attributes != null) {
            return parseTransactionAnnotation(attributes);
        }
        return null;
    }
    
    protected TransactionAttribute parseTransactionAnnotation(AnnotationAttributes attributes) {
        RuleBasedTransactionAttribute rbta = new RuleBasedTransactionAttribute();
        
        // 解析传播行为
        rbta.setPropagationBehavior(attributes.getEnum("propagation").value());
        
        // 解析隔离级别
        rbta.setIsolationLevel(attributes.getEnum("isolation").value());
        
        // 解析超时
        rbta.setTimeout(attributes.getNumber("timeout").intValue());
        
        // 解析只读
        rbta.setReadOnly(attributes.getBoolean("readOnly"));
        
        // 解析事务管理器
        rbta.setQualifier(attributes.getString("value"));
        
        // 解析回滚规则
        List<RollbackRuleAttribute> rollbackRules = new ArrayList<>();
        for (Class<?> rbRule : attributes.getClassArray("rollbackFor")) {
            rollbackRules.add(new RollbackRuleAttribute(rbRule));
        }
        for (String rbRule : attributes.getStringArray("rollbackForClassName")) {
            rollbackRules.add(new RollbackRuleAttribute(rbRule));
        }
        for (Class<?> rbRule : attributes.getClassArray("noRollbackFor")) {
            rollbackRules.add(new NoRollbackRuleAttribute(rbRule));
        }
        for (String rbRule : attributes.getStringArray("noRollbackForClassName")) {
            rollbackRules.add(new NoRollbackRuleAttribute(rbRule));
        }
        rbta.setRollbackRules(rollbackRules);
        
        return rbta;
    }
}
```

### 14.5 缓存机制

```java
public abstract class AbstractFallbackTransactionAttributeSource 
        implements TransactionAttributeSource {
    
    // 缓存：Method -> TransactionAttribute
    private final Map<Object, TransactionAttribute> attributeCache = 
        new ConcurrentHashMap<>(1024);
    
    @Override
    @Nullable
    public TransactionAttribute getTransactionAttribute(Method method, @Nullable Class<?> targetClass) {
        
        Object cacheKey = getCacheKey(method, targetClass);
        
        // 先从缓存查
        TransactionAttribute cached = this.attributeCache.get(cacheKey);
        if (cached != null) {
            return (cached != NULL_TRANSACTION_ATTRIBUTE ? cached : null);
        }
        
        // 缓存未命中，解析
        TransactionAttribute txAttr = computeTransactionAttribute(method, targetClass);
        
        // 放入缓存
        if (txAttr == null) {
            this.attributeCache.put(cacheKey, NULL_TRANSACTION_ATTRIBUTE);
        } else {
            String methodIdentification = ClassUtils.getQualifiedMethodName(method, targetClass);
            if (txAttr instanceof DefaultTransactionAttribute) {
                ((DefaultTransactionAttribute) txAttr).setDescriptor(methodIdentification);
            }
            this.attributeCache.put(cacheKey, txAttr);
        }
        
        return txAttr;
    }
}
```

---

## 阶段十五：事务回调 - TransactionSynchronization

### 15.1 问题：如何在事务提交后执行操作？

```java
@Transactional
public void createOrder(Order order) {
    orderDao.save(order);
    
    // 问题：如果这里发送MQ消息，事务还没提交！
    // 如果后面回滚了，消息就发错了
    kafkaTemplate.send("order-topic", order);
}
```

### 15.2 TransactionSynchronization接口

```java
public interface TransactionSynchronization extends Flushable {
    
    int STATUS_COMMITTED = 0;
    int STATUS_ROLLED_BACK = 1;
    int STATUS_UNKNOWN = 2;
    
    // 挂起时调用
    default void suspend() {}
    
    // 恢复时调用
    default void resume() {}
    
    // 刷新时调用
    @Override
    default void flush() {}
    
    // 提交前调用
    default void beforeCommit(boolean readOnly) {}
    
    // 完成前调用（提交或回滚）
    default void beforeCompletion() {}
    
    // 提交后调用
    default void afterCommit() {}
    
    // 完成后调用（提交或回滚）
    default void afterCompletion(int status) {}
}
```

### 15.3 使用示例

```java
@Transactional
public void createOrder(Order order) {
    orderDao.save(order);
    
    // 注册回调
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                // 确保事务提交后才发送消息
                kafkaTemplate.send("order-topic", order);
            }
            
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    // 事务回滚了
                    log.warn("Order creation rolled back: {}", order.getId());
                }
            }
        }
    );
}
```

### 15.4 执行时机

```
事务开始
    │
    ├── 业务方法执行
    │   └── 注册TransactionSynchronization
    │
    ├── 提交前
    │   └── TransactionSynchronization.beforeCommit(readOnly)
    │
    ├── 完成前
    │   └── TransactionSynchronization.beforeCompletion()
    │
    ├── 真正提交
    │   └── connection.commit()
    │
    ├── 提交后
    │   └── TransactionSynchronization.afterCommit()
    │
    └── 完成后
        └── TransactionSynchronization.afterCompletion(STATUS_COMMITTED)
```

---

## 完整执行流程总结

### 完整执行流程图

```
客户端调用: orderService.createOrder(order)
        │
        ▼
CGLIB代理对象拦截
        │
        ▼
TransactionInterceptor.invoke(invocation)
        │
        ├── 1. 解析@Transactional注解
        │   └── AnnotationTransactionAttributeSource.getTransactionAttribute()
        │       └── 从缓存或解析注解 → RuleBasedTransactionAttribute
        │
        ├── 2. 获取事务管理器
        │   └── DataSourceTransactionManager
        │
        ├── 3. 开启事务
        │   └── AbstractPlatformTransactionManager.getTransaction()
        │       ├── doGetTransaction() → DataSourceTransactionObject
        │       ├── isExistingTransaction() → false
        │       └── doBegin()
        │           ├── dataSource.getConnection()
        │           ├── conn.setAutoCommit(false)
        │           ├── conn.setTransactionIsolation(READ_COMMITTED)
        │           ├── connectionHolder.setTransactionActive(true)
        │           └── bindResource(dataSource, connectionHolder)
        │
        ├── 4. 创建TransactionInfo并绑定到ThreadLocal
        │   └── transactionInfoHolder.set(txInfo)
        │
        ├── 5. 执行业务方法
        │   └── OrderService.createOrder()
        │       └── orderDao.save(order)
        │           └── JdbcTemplate.update()
        │               └── DataSourceUtils.getConnection()
        │                   └── 从ThreadLocal获取ConnectionHolder
        │                   └── holder.requested() → referenceCount++
        │                   └── 返回Connection
        │               └── 执行SQL
        │               └── DataSourceUtils.releaseConnection()
        │                   └── holder.released() → referenceCount--
        │                   └── 不关闭，还在事务中
        │
        ├── 6. 提交事务
        │   └── AbstractPlatformTransactionManager.commit()
        │       ├── triggerBeforeCommit() → TransactionSynchronization.beforeCommit()
        │       ├── triggerBeforeCompletion() → TransactionSynchronization.beforeCompletion()
        │       ├── doCommit() → conn.commit()
        │       ├── triggerAfterCommit() → TransactionSynchronization.afterCommit()
        │       ├── triggerAfterCompletion() → TransactionSynchronization.afterCompletion()
        │       └── doCleanupAfterCompletion()
        │           ├── unbindResource()
        │           └── conn.close()
        │
        └── 7. 清理TransactionInfo
            └── txInfo.restoreThreadLocalStatus()
```

---

## 核心类关系总览

### 数据结构总结

| 类/接口 | 数据结构 | 作用 |
|---------|---------|------|
| `TransactionDefinition` | 常量定义 | 定义事务属性（传播行为、隔离级别等） |
| `TransactionStatus` | 布尔标志 | 表示事务运行状态 |
| `TransactionAttribute` | 回滚规则列表 | 扩展事务属性，支持回滚规则 |
| `RuleBasedTransactionAttribute` | `List<RollbackRuleAttribute>` | 基于规则的回滚判断 |
| `ConnectionHolder` | Connection + 引用计数 | 包装Connection，支持复用 |
| `TransactionSynchronizationManager` | 6个ThreadLocal | 线程上下文存储中心 |
| `DataSourceTransactionObject` | ConnectionHolder | JDBC事务对象 |
| `TransactionInfo` | 链表结构（oldTransactionInfo） | 支持事务嵌套 |
| `DefaultTransactionStatus` | 事务对象 + 状态标志 | 事务状态实现 |

### 类关系图

```
┌─────────────────────────────────────────────────────────────────┐
│                          接口层                                  │
├─────────────────────────────────────────────────────────────────┤
│ PlatformTransactionManager    TransactionDefinition             │
│         ↑                            ↑                          │
│         │                            │                          │
│ TransactionStatus ◄──────── TransactionAttribute                │
│         ↑                            ↑                          │
└─────────┼────────────────────────────┼──────────────────────────┘
          │                            │
┌─────────┼────────────────────────────┼──────────────────────────┐
│         │         实现层             │                          │
├─────────┼────────────────────────────┼──────────────────────────┤
│         │                            │                          │
│ AbstractPlatformTransactionManager   RuleBasedTransactionAttribute
│         ↑                            │                          │
│         │                            │                          │
│ DataSourceTransactionManager         │                          │
│         │                            │                          │
│         └── DataSourceTransactionObject                         │
│                 └── ConnectionHolder                            │
│                         └── Connection                         │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
          │
┌─────────┼─────────────────────────────────────────────────────┐
│         │              存储层                                  │
├─────────┼─────────────────────────────────────────────────────┤
│ TransactionSynchronizationManager                              │
│ ├── resources: ThreadLocal<Map<Object, Object>>                │
│ ├── synchronizations: ThreadLocal<Set<TransactionSynchronization>>
│ ├── currentTransactionName: ThreadLocal<String>                │
│ ├── currentTransactionReadOnly: ThreadLocal<Boolean>           │
│ ├── currentTransactionIsolationLevel: ThreadLocal<Integer>     │
│ └── actualTransactionActive: ThreadLocal<Boolean>              │
└─────────────────────────────────────────────────────────────────┘
          │
┌─────────┼─────────────────────────────────────────────────────┐
│         │              AOP层                                   │
├─────────┼─────────────────────────────────────────────────────┤
│ TransactionInterceptor (实现MethodInterceptor)                 │
│         ↑                                                      │
│ TransactionAspectSupport                                       │
│ ├── transactionInfoHolder: ThreadLocal<TransactionInfo>        │
│ └── invokeWithinTransaction()                                  │
│                                                                 │
│ TransactionAttributeSource                                     │
│ └── AnnotationTransactionAttributeSource                       │
│     └── SpringTransactionAnnotationParser                      │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 核心设计模式

| 模式 | 应用位置 | 说明 |
|------|---------|------|
| **策略模式** | PlatformTransactionManager | 不同数据源有不同实现 |
| **模板方法** | AbstractPlatformTransactionManager | 定义事务处理流程骨架 |
| **代理模式** | TransactionInterceptor | AOP代理实现声明式事务 |
| **责任链模式** | TransactionInfo链表 | 支持事务嵌套 |
| **观察者模式** | TransactionSynchronization | 事务生命周期回调 |

---

## 总结

Spring事务的核心是**数据结构设计**：

1. **ThreadLocal存储**：解决线程安全和隐式传递问题
2. **引用计数**：解决REQUIRED传播行为的Connection复用
3. **链表结构**：解决事务嵌套的上下文恢复
4. **回滚规则**：解决异常类型的回滚判断

基于这些数据结构，实现了**传播行为算法**、**事务同步算法**等核心逻辑。

理解了数据结构，就理解了Spring事务的本质。
