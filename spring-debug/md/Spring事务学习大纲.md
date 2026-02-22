# Spring 事务学习大纲（超详细版）

## 目录
1. [核心概念与架构](#一核心概念与架构)
2. [事务传播行为深度解析](#二事务传播行为深度解析)
3. [事务隔离级别与并发控制](#三事务隔离级别与并发控制)
4. [@Transactional注解全解析](#四transactional注解全解析)
5. [事务失效场景大全](#五事务失效场景大全)
6. [事务管理器架构详解](#六事务管理器架构详解)
7. [事务拦截器执行流程](#七事务拦截器执行流程)
8. [事务同步机制](#八事务同步机制)
9. [保存点与嵌套事务](#九保存点与嵌套事务)
10. [编程式事务详解](#十编程式事务详解)
11. [生产环境最佳实践](#十一生产环境最佳实践)
12. [分布式事务方案](#十二分布式事务方案)
13. [面试高频问题](#十三面试高频问题)
14. [源码学习路线图](#十四源码学习路线图)

---

## 一、核心概念与架构

### 1.1 事务 ACID 特性详解

| 特性 | 定义 | Spring 实现机制 | 数据库保障 | 常见问题 |
|------|------|----------------|-----------|----------|
| **原子性 (Atomicity)** | 事务内所有操作要么全成功，要么全失败 | `TransactionAspectSupport` 管理提交/回滚 | Undo Log | 部分提交、悬挂事务 |
| **一致性 (Consistency)** | 事务执行前后数据库完整性约束不被破坏 | 配合数据库约束、触发器 | 外键、CHECK约束 | 违反约束时的回滚策略 |
| **隔离性 (Isolation)** | 并发事务间互不干扰 | `TransactionDefinition` 定义隔离级别 | 锁机制、MVCC | 脏读、幻读、不可重复读 |
| **持久性 (Durability)** | 事务提交后数据永久保存 | `TransactionManager.commit()` | Redo Log | 数据库崩溃恢复 |

#### 原子性实现原理
```
业务方法执行
    ↓
发生异常？
    ├── 是 → TransactionManager.rollback() → 数据库回滚 → 释放连接
    ↓
    └── 否 → TransactionManager.commit() → 数据库提交 → 释放连接
```

### 1.2 Spring 事务架构全景图

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        Spring 事务管理架构                               │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                    声明式事务层 (@Transactional)                   │   │
│  │  ┌──────────────┐  ┌──────────────────┐  ┌──────────────────┐  │   │
│  │  │ @EnableTrans-│  │ TransactionProxy-│  │ TransactionIn-   │  │   │
│  │  │ actionMana-  │  │ FactoryBean      │  │ terceptor        │  │   │
│  │  │ gement       │  │ (代理创建)        │  │ (拦截器)          │  │   │
│  │  └──────────────┘  └──────────────────┘  └──────────────────┘  │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                              ↓                                          │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                    抽象事务管理层                                  │   │
│  │  ┌─────────────────────────────────────────────────────────┐   │   │
│  │  │           PlatformTransactionManager (核心接口)          │   │   │
│  │  │  ┌─────────────────┐ ┌─────────────────┐ ┌───────────┐ │   │   │
│  │  │  │ DataSourceTrans-│ │ JtaTransaction- │ │ 其他实现   │ │   │   │
│  │  │  │ actionManager   │ │ Manager         │ │           │ │   │   │
│  │  │  │ (单数据源)       │ │ (分布式XA)       │ │           │ │   │   │
│  │  │  └─────────────────┘ └─────────────────┘ └───────────┘ │   │   │
│  │  └─────────────────────────────────────────────────────────┘   │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                              ↓                                          │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                    底层资源管理层                                  │   │
│  │  ┌──────────────┐  ┌──────────────────┐  ┌──────────────────┐  │   │
│  │  │ DataSource   │  │ Connection       │  │ TransactionSyn-  │  │   │
│  │  │ (数据源)      │  │ (数据库连接)      │  │ chronization     │  │   │
│  │  │              │  │                  │  │ (事务同步器)      │  │   │
│  │  └──────────────┘  └──────────────────┘  └──────────────────┘  │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                              ↓                                          │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                    数据库事务层                                    │   │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐  │   │
│  │  │ BEGIN/START  │  │ COMMIT       │  │ ROLLBACK/ROLLBACK TO │  │   │
│  │  │ TRANSACTION  │  │              │  │ SAVEPOINT            │  │   │
│  │  └──────────────┘  └──────────────┘  └──────────────────────┘  │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

### 1.3 声明式事务 vs 编程式事务

| 对比维度 | 声明式事务 | 编程式事务 |
|---------|-----------|-----------|
| **实现方式** | `@Transactional` 注解 | `TransactionTemplate` / `PlatformTransactionManager` |
| **侵入性** | 低（AOP实现） | 高（代码侵入） |
| **控制粒度** | 方法级别 | 代码块级别 |
| **灵活性** | 低（固定配置） | 高（动态控制） |
| **性能** | 稍低（代理开销） | 稍高（无代理） |
| **适用场景** | 标准CRUD操作 | 复杂事务边界、动态事务 |
| **使用比例** | 90%场景 | 10%场景 |

#### 声明式事务代码示例
```java
@Service
public class OrderService {
    
    @Transactional(rollbackFor = Exception.class, timeout = 30)
    public Order createOrder(OrderDTO dto) {
        // 事务自动开启
        orderDao.insert(order);
        inventoryDao.deduct(order);
        // 无异常自动提交，有异常自动回滚
        return order;
    }
}
```

#### 编程式事务代码示例
```java
@Service
public class OrderService {
    
    @Autowired
    private TransactionTemplate transactionTemplate;
    
    public Order createOrder(OrderDTO dto) {
        // 精确控制事务边界
        validate(dto); // 无事务
        
        Order order = transactionTemplate.execute(status -> {
            // 事务开始
            try {
                orderDao.insert(order);
                inventoryDao.deduct(order);
                return order;
            } catch (Exception e) {
                status.setRollbackOnly();
                throw new OrderException("创建订单失败", e);
            }
            // 事务结束
        });
        
        sendNotification(order); // 无事务
        return order;
    }
}
```

---

## 二、事务传播行为深度解析

### 2.1 传播行为概述

传播行为定义：当一个事务方法调用另一个事务方法时，事务如何传播的规则。

```java
public enum Propagation {
    REQUIRED(0),          // 默认：有则加入，无则新建
    SUPPORTS(1),          // 有则用，无则无
    MANDATORY(2),         // 必须有，否则抛异常
    REQUIRES_NEW(3),      // 挂起当前，新建事务
    NOT_SUPPORTED(4),     // 挂起当前，非事务执行
    NEVER(5),             // 必须无，否则抛异常
    NESTED(6);            // 嵌套事务（savepoint）
}
```

### 2.2 七种传播行为详解

#### 2.2.1 REQUIRED（默认）⭐⭐⭐⭐⭐

**定义**：如果当前存在事务，则加入该事务；如果不存在，则新建一个事务。

**执行流程图**：
```
┌────────────────────────────────────────────────────────────┐
│  外部方法A() 调用 内部方法B()                                │
└────────────────────────────────────────────────────────────┘
                            │
           ┌────────────────┴────────────────┐
           ▼                                 ▼
    ┌──────────────┐                 ┌──────────────┐
    │ 外部无事务    │                 │ 外部有事务    │
    └──────────────┘                 └──────────────┘
           │                                 │
           ▼                                 ▼
    ┌──────────────┐                 ┌──────────────┐
    │ 新建事务T1   │                 │ 加入事务T1   │
    │ B()在T1中   │                 │ B()在T1中   │
    └──────────────┘                 └──────────────┘
```

**代码示例**：
```java
@Service
public class OrderService {
    
    @Autowired
    private InventoryService inventoryService;
    
    @Transactional  // REQUIRED 默认
    public void createOrder(Order order) {
        orderDao.save(order);                    // 在事务T1中
        inventoryService.deduct(order);          // 加入事务T1
    }
}

@Service
public class InventoryService {
    
    @Transactional  // REQUIRED 默认
    public void deduct(Order order) {
        inventoryDao.update(order);              // 加入外部事务T1
    }
}
```

**关键特性**：
- 内部方法抛出异常，会导致整个事务回滚
- 外部方法捕获内部异常，事务仍然可能回滚（取决于配置）

---

#### 2.2.2 REQUIRES_NEW ⭐⭐⭐⭐⭐

**定义**：挂起当前事务，创建一个新的事务执行。

**执行流程图**：
```
┌─────────────────────────────────────────────────────────────────┐
│  外部方法A() 调用 内部方法B() (REQUIRES_NEW)                      │
└─────────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│  外部事务T1(挂起) → 创建新事务T2 → 执行B() → 提交T2 → 恢复T1     │
│                                                                 │
│  时间点：                                                        │
│  ① A()开始 → T1开启                                             │
│  ② B()调用 → 挂起T1 → 开启T2                                     │
│  ③ B()结束 → 提交T2 → 恢复T1                                     │
│  ④ A()结束 → 提交T1                                             │
└─────────────────────────────────────────────────────────────────┘
```

**代码示例**：
```java
@Service
public class OrderService {
    
    @Autowired
    private LogService logService;
    
    @Transactional
    public void createOrder(Order order) {
        orderDao.save(order);
        
        try {
            // 独立事务，失败不影响主业务
            logService.record("订单创建: " + order.getId());
        } catch (Exception e) {
            // 日志失败不影响订单创建
            log.warn("日志记录失败", e);
        }
        
        // 模拟业务异常
        throw new RuntimeException("业务异常");
        // 订单会回滚，但日志已提交（独立事务）
    }
}

@Service
public class LogService {
    
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String message) {
        logDao.insert(message);
        // 此方法结束，事务立即提交
    }
}
```

**使用场景**：
| 场景 | 说明 |
|------|------|
| **日志记录** | 审计日志必须保存，不能因为业务回滚而丢失 |
| **消息发送** | MQ消息发送要成功，与业务事务解耦 |
| **通知发送** | 短信/邮件通知独立执行 |
| **监控上报** | 指标统计不能因为业务失败而丢失 |

**注意事项**：
- REQUIRES_NEW 会创建真正独立的事务
- 外部事务回滚不会影响内部事务（已提交）
- 如果内部事务回滚，外部事务可以继续执行（除非显式抛出异常）

---

#### 2.2.3 NESTED ⭐⭐⭐⭐

**定义**：在当前事务中创建一个保存点（Savepoint），如果嵌套事务回滚，只回滚到保存点。

**执行流程图**：
```
┌──────────────────────────────────────────────────────────────────────┐
│  外部方法A() 调用 内部方法B() (NESTED)                                 │
└──────────────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌──────────────────────────────────────────────────────────────────────┐
│  外部事务T1 → 创建Savepoint → 执行B() → 根据结果决定回滚到Savepoint  │
│                                                                 │
│  时间点：                                                        │
│  ① A()开始 → T1开启                                             │
│  ② B()调用 → 创建Savepoint S1                                    │
│  ③ B()成功 → 继续执行A()                                         │
│  ④ B()失败 → ROLLBACK TO S1 → 继续执行A()                        │
│  ⑤ A()结束 → COMMIT T1                                          │
└──────────────────────────────────────────────────────────────────────┘
```

**代码示例**：
```java
@Service
public class OrderService {
    
    @Autowired
    private ItemService itemService;
    
    @Transactional
    public void createOrder(Order order, List<Item> items) {
        orderDao.save(order);
        
        for (Item item : items) {
            try {
                // 每个商品保存失败只回滚当前商品，不影响其他
                itemService.saveItem(item);
            } catch (Exception e) {
                // 记录失败日志，继续处理下一个
                log.warn("商品保存失败: {}" , item.getId(), e);
            }
        }
        
        orderDao.updateStatus(order.getId(), "CREATED");
    }
}

@Service
public class ItemService {
    
    @Transactional(propagation = Propagation.NESTED)
    public void saveItem(Item item) {
        itemDao.save(item);
        // 如果这里抛出异常，只回滚到savepoint，外部事务继续
    }
}
```

**NESTED vs REQUIRES_NEW 深度对比**：

| 对比维度 | NESTED | REQUIRES_NEW |
|---------|--------|--------------|
| **事务关系** | 父子关系（逻辑嵌套） | 完全独立 |
| **实现机制** | JDBC Savepoint | 挂起/恢复事务 |
| **提交时机** | 随父事务一起提交 | 立即独立提交 |
| **回滚影响** | 只回滚到savepoint | 完全独立回滚 |
| **连接使用** | 共享连接 | 可能使用不同连接 |
| **使用场景** | 部分回滚 | 完全独立 |
| **异常传播** | 可选择捕获继续 | 完全独立 |
| **数据源要求** | 支持Savepoint | 无特殊要求 |

**注意事项**：
- NESTED 需要数据库支持 Savepoint（如 MySQL、Oracle）
- NESTED 本质是同一个物理事务，只是有逻辑回滚点
- 父事务回滚，子事务必定回滚（因为是同一个事务）

---

#### 2.2.4 其他传播行为

| 传播行为 | 行为描述 | 使用频率 | 典型场景 | 代码示例 |
|---------|---------|---------|---------|---------|
| **SUPPORTS** | 有事务则加入，无事务则非事务执行 | ⭐⭐ | 查询服务方法，事务可有可无 | `@Transactional(propagation = Propagation.SUPPORTS) public List<User> query() { }` |
| **NOT_SUPPORTED** | 挂起当前事务，非事务执行 | ⭐⭐ | 发送通知、上报指标，不需要事务 | `@Transactional(propagation = Propagation.NOT_SUPPORTED) public void sendMetric() { }` |
| **MANDATORY** | 必须有事务，否则抛异常 | ⭐ | 核心业务方法，强制在事务中执行 | `@Transactional(propagation = Propagation.MANDATORY) public void deductBalance() { }` |
| **NEVER** | 必须无事务，否则抛异常 | ⭐ | 非事务工具方法，确保无事务干扰 | `@Transactional(propagation = Propagation.NEVER) public void utilMethod() { }` |

### 2.3 传播行为组合场景实战

#### 场景1：订单创建 + 日志记录（REQUIRED + REQUIRES_NEW）
```java
@Service
public class OrderService {
    
    @Autowired
    private LogService logService;
    @Autowired
    private InventoryService inventoryService;
    
    @Transactional
    public void createOrder(OrderDTO dto) {
        // 1. 保存订单（主事务T1）
        Order order = orderDao.save(dto);
        
        // 2. 记录日志（REQUIRES_NEW，独立事务T2）
        logService.record("订单创建: " + order.getId());
        
        // 3. 扣减库存（REQUIRED，加入T1）
        inventoryService.deduct(order);
        
        // 4. 模拟异常
        throw new RuntimeException("业务异常");
        // 结果：订单回滚（T1），日志已提交（T2），库存回滚（T1）
    }
}
```

#### 场景2：批量处理 + 部分回滚（REQUIRED + NESTED）
```java
@Service
public class BatchService {
    
    @Autowired
    private DetailService detailService;
    
    @Transactional
    public void batchProcess(List<Data> dataList) {
        // 创建批次记录
        Batch batch = batchDao.save(new Batch());
        
        int successCount = 0;
        int failCount = 0;
        
        for (Data data : dataList) {
            try {
                // 每条记录独立处理，失败不影响其他
                detailService.processDetail(batch.getId(), data);
                successCount++;
            } catch (Exception e) {
                failCount++;
                log.warn("处理失败: {}", data.getId());
            }
        }
        
        // 更新批次统计
        batchDao.updateStatistics(batch.getId(), successCount, failCount);
        // 批次记录和成功的详情记录会被提交
    }
}

@Service
public class DetailService {
    
    @Transactional(propagation = Propagation.NESTED)
    public void processDetail(Long batchId, Data data) {
        Detail detail = convert(data);
        detail.setBatchId(batchId);
        detailDao.save(detail);
        
        // 处理业务逻辑
        processBusiness(detail);
        // 如果这里异常，只回滚当前detail，不影响批次和其他detail
    }
}
```

---

## 三、事务隔离级别与并发控制

### 3.1 四种隔离级别详解

```java
public enum Isolation {
    DEFAULT(-1),          // 使用数据库默认
    READ_UNCOMMITTED(1),  // 读未提交
    READ_COMMITTED(2),    // 读已提交
    REPEATABLE_READ(4),   // 可重复读
    SERIALIZABLE(8);      // 串行化
}
```

| 隔离级别 | 脏读 | 不可重复读 | 幻读 | 实现机制 | MySQL支持 | Oracle支持 |
|---------|------|-----------|------|---------|----------|-----------|
| **READ_UNCOMMITTED** | ✅ 允许 | ✅ 允许 | ✅ 允许 | 无锁 | ✅ | ❌ |
| **READ_COMMITTED** | ❌ 不允许 | ✅ 允许 | ✅ 允许 | MVCC | ✅ | ✅ 默认 |
| **REPEATABLE_READ** | ❌ 不允许 | ❌ 不允许 | ⚠️ 部分允许 | MVCC + 间隙锁 | ✅ 默认 | ❌ |
| **SERIALIZABLE** | ❌ 不允许 | ❌ 不允许 | ❌ 不允许 | 串行执行 | ✅ | ✅ |

### 3.2 并发问题详解

#### 3.2.1 脏读（Dirty Read）

**定义**：一个事务读到了另一个事务未提交的数据。

**时序图**：
```
事务A                          事务B (READ_UNCOMMITTED)
  │                                    │
  │ BEGIN;                             │
  │                                    │ BEGIN;
  │ UPDATE account SET balance=1000    │
  │ WHERE id=1; (未提交)                │
  │                                    │ SELECT balance FROM account
  │                                    │ WHERE id=1;  → 读到 1000 (脏数据！)
  │ ROLLBACK;                          │
  │                                    │ COMMIT;
```

**后果**：
- 读到不存在的数据
- 业务逻辑基于错误数据做决策
- 可能导致资金损失

**解决方案**：
- 使用 READ_COMMITTED 或更高隔离级别
- MVCC确保只读取已提交的数据版本

---

#### 3.2.2 不可重复读（Non-repeatable Read）

**定义**：同一事务内，两次读取同一数据，结果不同。

**时序图**：
```
事务A                          事务B
  │                                    │
  │ BEGIN;                             │
  │                                    │ BEGIN;
  │ SELECT balance FROM account        │
  │ WHERE id=1;  → 结果：500           │
  │                                    │ UPDATE account SET balance=1000
  │                                    │ WHERE id=1;
  │                                    │ COMMIT;
  │ SELECT balance FROM account        │
  │ WHERE id=1;  → 结果：1000 (不一致！)│
  │                                    │
  │ COMMIT;                            │
```

**后果**：
- 同一事务内数据不一致
- 基于第一次读取做的决策可能错误

**解决方案**：
- 使用 REPEATABLE_READ 或 SERIALIZABLE
- MVCC保证事务内看到的数据版本一致

---

#### 3.2.3 幻读（Phantom Read）

**定义**：同一事务内，两次相同条件的查询，返回的行数不同。

**时序图**：
```
事务A                          事务B
  │                                    │
  │ BEGIN;                             │
  │                                    │ BEGIN;
  │ SELECT * FROM account              │
  │ WHERE age > 18;  → 10条            │
  │                                    │ INSERT INTO account (name, age)
  │                                    │ VALUES ('new', 20);
  │                                    │ COMMIT;
  │ SELECT * FROM account              │
  │ WHERE age > 18;  → 11条 (幻行出现！)
  │                                    │
  │ COMMIT;                            │
```

**后果**：
- 两次查询结果集不一致
- 聚合统计结果不准确

**解决方案**：
- MVCC解决快照读的幻读
- 间隙锁（Gap Lock）解决当前读的幻读

### 3.3 MySQL 解决幻读的原理

#### 3.3.1 MVCC（多版本并发控制）

**核心概念**：
- **Read View**：事务快照，记录事务开始时已提交的事务ID
- **Undo Log**：记录数据的历史版本
- **数据行隐藏列**：
  - `DB_TRX_ID`：最后修改该记录的事务ID
  - `DB_ROLL_PTR`：回滚指针，指向Undo Log

**工作原理图**：
```
┌─────────────────────────────────────────────────────────────────┐
│                        数据行结构                               │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌──────────┬──────────┬──────────┬──────────┬──────────┐      │
│  │  id      │  name    │  age     │ DB_TRX_ID│DB_ROLL_  │      │
│  │          │          │          │ (6字节)  │ PTR(7字节)│      │
│  └──────────┴──────────┴──────────┴──────────┴──────────┘      │
│       │                                                  │      │
│       │         ┌────────────────────────────────────────┘      │
│       │         ▼                                               │
│       │    ┌─────────────────┐    ┌─────────────────┐          │
│       │    │ Undo Log 版本2  │    │ Undo Log 版本1  │          │
│       │    │ (name='B')      │───▶│ (name='A')      │          │
│       │    │ DB_TRX_ID=100   │    │ DB_TRX_ID=50    │          │
│       │    └─────────────────┘    └─────────────────┘          │
│       │                                                          │
│       └───────── 事务ID=100 修改，形成版本链                      │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

**可见性判断规则**：
```
事务A (Read View: m_ids=[90,100], min_trx_id=90, max_trx_id=101, creator_trx_id=90)

查询数据时：
1. DB_TRX_ID == creator_trx_id → 可见（自己改的）
2. DB_TRX_ID < min_trx_id → 可见（已提交）
3. DB_TRX_ID >= max_trx_id → 不可见（将来事务）
4. min_trx_id <= DB_TRX_ID < max_trx_id → 检查m_ids
   - 在m_ids中 → 不可见（未提交）
   - 不在m_ids中 → 可见（已提交）
```

**MVCC解决幻读的局限性**：
- 只解决**快照读**（普通SELECT）的幻读
- 不解决**当前读**（SELECT FOR UPDATE）的幻读

---

#### 3.3.2 间隙锁（Gap Lock）

**定义**：锁定索引记录之间的间隙，防止其他事务插入数据。

**锁类型**：
```
┌─────────────────────────────────────────────────────────────┐
│                      InnoDB 锁类型                           │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  Record Lock（记录锁）：锁定单条记录                          │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  id=1  │  id=2  │  id=3  │  id=5  │  id=8  │      │   │
│  │   🔒   │        │        │        │        │      │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
│  Gap Lock（间隙锁）：锁定记录间隙                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  id=1  │  id=2  │  id=3  │  id=5  │  id=8  │      │   │
│  │        │  ───🔒───  │        │        │        │      │   │
│  └─────────────────────────────────────────────────────┘   │
│                   锁定 2<id<3 的间隙                        │
│                                                             │
│  Next-Key Lock（临键锁）：记录锁 + 间隙锁                     │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  id=1  │  id=2  │  id=3  │  id=5  │  id=8  │      │   │
│  │        │  🔒────  │        │        │        │      │   │
│  └─────────────────────────────────────────────────────┘   │
│               锁定 id=2 及 2<id<3 的区间                     │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

**解决幻读示例**：
```sql
-- 事务A
BEGIN;
SELECT * FROM user WHERE age > 18 FOR UPDATE;
-- 不仅锁定 age>18 的已有记录，还锁定 age>18 的间隙

-- 事务B（被阻塞）
INSERT INTO user (name, age) VALUES ('new', 20); -- 阻塞等待！
```

**锁的范围分析**：
```
假设索引列age的值分布：10, 18, 20, 25, 30

SELECT * FROM user WHERE age > 18 FOR UPDATE;

锁定区间：
(18, 20] - Next-Key Lock
(20, 25] - Next-Key Lock
(25, 30] - Next-Key Lock
(30, +∞) - Gap Lock

这意味着 age=19,21,22... 的插入都会被阻塞
```

---

## 四、@Transactional注解全解析

### 4.1 注解属性完整列表

```java
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface Transactional {
    
    // ========== 事务管理器选择 ==========
    
    /**
     * 事务管理器名称（多数据源时使用）
     * 类型：String
     * 默认值：""
     */
    @AliasFor("transactionManager")
    String value() default "";
    
    /**
     * 事务管理器名称（与value互为别名）
     * 类型：String
     * 默认值：""
     */
    @AliasFor("value")
    String transactionManager() default "";
    
    
    // ========== 事务行为定义 ==========
    
    /**
     * 传播行为
     * 类型：Propagation（枚举）
     * 默认值：Propagation.REQUIRED
     * 可选值：REQUIRED, SUPPORTS, MANDATORY, REQUIRES_NEW, 
     *        NOT_SUPPORTED, NEVER, NESTED
     */
    Propagation propagation() default Propagation.REQUIRED;
    
    /**
     * 隔离级别
     * 类型：Isolation（枚举）
     * 默认值：Isolation.DEFAULT（使用数据库默认）
     * 可选值：DEFAULT, READ_UNCOMMITTED, READ_COMMITTED, 
     *        REPEATABLE_READ, SERIALIZABLE
     */
    Isolation isolation() default Isolation.DEFAULT;
    
    /**
     * 超时时间（秒）
     * 类型：int
     * 默认值：TransactionDefinition.TIMEOUT_DEFAULT (-1)
     * 说明：-1表示使用数据库默认超时，>0表示具体秒数
     */
    int timeout() default TransactionDefinition.TIMEOUT_DEFAULT;
    
    /**
     * 是否只读事务
     * 类型：boolean
     * 默认值：false
     * 作用：
     *   1. MySQL：不加写锁，提高并发性能
     *   2. ORM框架：不刷新持久化上下文
     *   3. 某些场景可路由到从库
     */
    boolean readOnly() default false;
    
    
    // ========== 回滚规则定义 ==========
    
    /**
     * 哪些异常触发回滚
     * 类型：Class<? extends Throwable>[]
     * 默认值：{}（空数组）
     * 默认行为：只回滚 RuntimeException 和 Error
     */
    Class<? extends Throwable>[] rollbackFor() default {};
    
    /**
     * 哪些异常触发回滚（类名形式）
     * 类型：String[]
     * 默认值：{}
     * 用途：用于无法直接引用异常类的情况
     */
    String[] rollbackForClassName() default {};
    
    /**
     * 哪些异常不触发回滚
     * 类型：Class<? extends Throwable>[]
     * 默认值：{}
     */
    Class<? extends Throwable>[] noRollbackFor() default {};
    
    /**
     * 哪些异常不触发回滚（类名形式）
     * 类型：String[]
     * 默认值：{}
     */
    String[] noRollbackForClassName() default {};
}
```

### 4.2 属性详解与使用建议

#### 4.2.1 propagation（传播行为）

| 场景 | 推荐值 | 说明 |
|------|--------|------|
| 标准业务方法 | REQUIRED | 默认即可，有事务加入，无则新建 |
| 日志/审计记录 | REQUIRES_NEW | 必须独立事务，不能影响主业务 |
| 批量处理中的单条 | NESTED | 失败可部分回滚 |
| 纯查询方法 | SUPPORTS 或 不加 | 事务可有可无 |
| 发送通知 | NOT_SUPPORTED | 不需要事务 |
| 核心业务校验 | MANDATORY | 强制必须在事务中调用 |

#### 4.2.2 isolation（隔离级别）

| 场景 | 推荐值 | 说明 |
|------|--------|------|
| 大多数业务 | DEFAULT | 使用数据库默认（MySQL RR） |
| 高并发读多写少 | READ_COMMITTED | 减少锁竞争 |
| 金融类强一致 | SERIALIZABLE | 串行执行，性能低 |
| 报表查询 | REPEATABLE_READ | 保证数据一致性 |

#### 4.2.3 timeout（超时时间）

```java
@Service
public class BatchService {
    
    /**
     * 大批量数据处理，设置30秒超时
     * 防止因数据量过大导致长时间占用连接
     */
    @Transactional(timeout = 30)
    public void processLargeData(List<Data> dataList) {
        for (Data data : dataList) {
            process(data);
        }
    }
    
    /**
     * 外部接口调用，设置10秒超时
     * 快速失败，释放资源
     */
    @Transactional(timeout = 10)
    public void callExternalAPI() {
        // 调用外部API
    }
}
```

**超时计算范围**：
- 包含：数据库操作时间
- 不包含：方法准备时间（如参数校验）
- 单位：秒

#### 4.2.4 readOnly（只读事务）

```java
@Service
public class ReportService {
    
    /**
     * 报表查询，标记为只读
     * 好处：
     * 1. MySQL不加写锁，提高并发性能
     * 2. Spring不刷新持久化上下文（JPA/Hibernate）
     * 3. 某些场景可路由到从库
     */
    @Transactional(readOnly = true)
    public Report generateReport(Date start, Date end) {
        List<Order> orders = orderDao.queryByDate(start, end);
        return calculateReport(orders);
    }
    
    /**
     * 复杂统计查询，配合RC隔离级别
     */
    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public Statistics getStatistics() {
        // 统计逻辑
    }
}
```

**注意事项**：
- `readOnly = true` 只是提示，不强制只读
- 在只读事务中执行写操作不会报错（但不应该这么做）

#### 4.2.5 rollbackFor（回滚异常）

```java
@Service
public class OrderService {
    
    /**
     * 默认配置：只回滚 RuntimeException 和 Error
     * Checked Exception 不会触发回滚
     */
    @Transactional
    public void method1() throws IOException {
        orderDao.save(order);
        throw new IOException("IO错误");  // ❌ 不会回滚！
    }
    
    /**
     * 指定回滚所有 Exception
     * 适用于需要回滚 Checked Exception 的场景
     */
    @Transactional(rollbackFor = Exception.class)
    public void method2() throws IOException {
        orderDao.save(order);
        throw new IOException("IO错误");  // ✅ 会回滚
    }
    
    /**
     * 指定多个异常类型
     */
    @Transactional(rollbackFor = {SQLException.class, IOException.class})
    public void method3() throws Exception {
        orderDao.save(order);
        throw new IOException("IO错误");  // ✅ 会回滚
    }
    
    /**
     * 使用 noRollbackFor 排除特定异常
     */
    @Transactional(
        rollbackFor = Exception.class,
        noRollbackFor = BusinessException.class
    )
    public void method4() throws Exception {
        orderDao.save(order);
        throw new BusinessException("业务异常");  // ❌ 不会回滚
    }
}
```

**回滚规则优先级**：
1. 首先检查是否在 `noRollbackFor` 中 → 是则不回滚
2. 然后检查是否在 `rollbackFor` 中 → 是则回滚
3. 最后检查默认规则 → RuntimeException/Error 回滚，其他不回滚

### 4.3 多数据源事务管理器配置

```java
@Configuration
public class DataSourceConfig {
    
    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.primary")
    public DataSource primaryDataSource() {
        return DataSourceBuilder.create().build();
    }
    
    @Bean
    @ConfigurationProperties("spring.datasource.secondary")
    public DataSource secondaryDataSource() {
        return DataSourceBuilder.create().build();
    }
    
    // ========== 主数据源事务管理器 ==========
    @Bean
    @Primary
    public PlatformTransactionManager primaryTransactionManager(
            @Qualifier("primaryDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
    
    // ========== 从数据源事务管理器 ==========
    @Bean
    public PlatformTransactionManager secondaryTransactionManager(
            @Qualifier("secondaryDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}

@Service
public class MultiDataSourceService {
    
    @Autowired
    @Qualifier("primaryJdbcTemplate")
    private JdbcTemplate primaryJdbcTemplate;
    
    @Autowired
    @Qualifier("secondaryJdbcTemplate")
    private JdbcTemplate secondaryJdbcTemplate;
    
    /**
     * 使用主库事务管理器
     */
    @Transactional(transactionManager = "primaryTransactionManager")
    public void usePrimary() {
        primaryJdbcTemplate.update("INSERT INTO ...");
    }
    
    /**
     * 使用从库事务管理器
     */
    @Transactional(transactionManager = "secondaryTransactionManager")
    public void useSecondary() {
        secondaryJdbcTemplate.update("INSERT INTO ...");
    }
}
```

---

## 五、事务失效场景大全

### 5.1 同类方法调用（this 调用）⭐⭐⭐⭐⭐

#### 问题代码
```java
@Service
public class OrderService {
    
    @Transactional
    public void createOrder() {
        orderDao.save(order);
        this.updateInventory();  // ❌ this 调用，事务失效
    }
    
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateInventory() {
        inventoryDao.deduct();
    }
}
```

#### 失效原理图
```
┌──────────────────────────────────────────────────────────────────┐
│                        调用栈分析                                 │
├──────────────────────────────────────────────────────────────────┤
│                                                                  │
│  main()                                                          │
│   │                                                              │
│   └── OrderService$$SpringCGLIB$$0.createOrder()                │
│        │  ↑ 代理对象，有事务拦截                                  │
│        │                                                         │
│        └── TransactionInterceptor.invoke()                       │
│             │                                                    │
│             └── OrderServiceImpl.createOrder()                   │
│                  │  ↑ 目标对象                                    │
│                  │                                               │
│                  └── this.updateInventory()                      │
│                       │  ↑ 直接调用，不经过代理                   │
│                       │                                          │
│                       └── OrderServiceImpl.updateInventory()     │
│                            ↑ 目标对象，无事务                     │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

#### 解决方案

**方案 1：注入自身代理（推荐）**
```java
@Service
public class OrderService {
    
    @Autowired
    private OrderService self;  // 注入自身代理
    
    @Transactional
    public void createOrder() {
        orderDao.save(order);
        self.updateInventory();  // ✅ 通过代理调用
    }
    
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateInventory() {
        inventoryDao.deduct();
    }
}
```

**方案 2：使用 AopContext**
```java
@EnableAspectJAutoProxy(exposeProxy = true)  // 必须开启

@Service
public class OrderService {
    
    @Transactional
    public void createOrder() {
        orderDao.save(order);
        ((OrderService) AopContext.currentProxy()).updateInventory();  // ✅
    }
}
```

**方案 3：拆分到不同 Service（最推荐）**
```java
@Service
public class OrderService {
    
    @Autowired
    private InventoryService inventoryService;
    
    @Transactional
    public void createOrder() {
        orderDao.save(order);
        inventoryService.updateInventory();  // ✅ 不同 Service，走代理
    }
}

@Service
public class InventoryService {
    
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateInventory() {
        inventoryDao.deduct();
    }
}
```

### 5.2 方法访问权限非 public ⭐⭐⭐⭐

```java
@Service
public class OrderService {
    
    @Transactional
    private void privateMethod() { }      // ❌ private，必定失效
    
    @Transactional
    protected void protectedMethod() { }  // ❌ protected，必定失效
    
    @Transactional
    void packageMethod() { }              // ❌ 默认包可见，必定失效
    
    @Transactional
    public void publicMethod() { }        // ✅ public，有效
}
```

**原因**：Spring AOP 基于代理实现，只能拦截 public 方法

### 5.3 异常被捕获未抛出 ⭐⭐⭐⭐⭐

#### 问题代码
```java
@Service
public class OrderService {
    
    @Transactional
    public void createOrder() {
        try {
            orderDao.save(order);
            int i = 1 / 0;  // 抛出 ArithmeticException
        } catch (Exception e) {
            log.error("出错了", e);
            // ❌ 捕获异常但未抛出，事务不会回滚
        }
    }
}
```

#### 解决方案

**方案 1：抛出异常**
```java
@Transactional
public void createOrder() throws Exception {
    orderDao.save(order);
    int i = 1 / 0;  // 抛出异常，事务回滚
}
```

**方案 2：手动回滚**
```java
@Transactional
public void createOrder() {
    try {
        orderDao.save(order);
    } catch (Exception e) {
        log.error("出错了", e);
        TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
    }
}
```

### 5.4 异常类型不匹配 ⭐⭐⭐⭐

```java
@Service
public class OrderService {
    
    /**
     * 默认只回滚 RuntimeException
     */
    @Transactional
    public void method1() throws IOException {
        orderDao.save(order);
        throw new IOException("IO错误");  // ❌ Checked 异常，不回滚
    }
    
    /**
     * 指定回滚所有 Exception
     */
    @Transactional(rollbackFor = Exception.class)
    public void method2() throws IOException {
        orderDao.save(order);
        throw new IOException("IO错误");  // ✅ 会回滚
    }
}
```

### 5.5 多线程环境下事务失效 ⭐⭐⭐

```java
@Service
public class OrderService {
    
    @Transactional
    public void createOrder() {
        orderDao.save(order);
        
        new Thread(() -> {
            itemDao.saveItems(items);  // ❌ 新线程，无事务上下文
        }).start();
    }
}
```

**原因**：事务上下文存储在 ThreadLocal 中，新线程获取不到

**解决方案**：
```java
// 方案 1：使用 @Async + @Transactional
@Async
@Transactional
public void asyncSaveItems() {
    itemDao.saveItems(items);
}

// 方案 2：使用编程式事务传递上下文
@Transactional
public void createOrder() {
    orderDao.save(order);
    
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                // 事务提交后执行
                asyncExecutor.execute(() -> saveItems());
            }
        }
    );
}
```

### 5.6 数据库引擎不支持事务 ⭐⭐

```sql
-- MyISAM 引擎不支持事务
CREATE TABLE t_order (
    id BIGINT PRIMARY KEY,
    name VARCHAR(100)
) ENGINE=MyISAM;  // ❌ 不支持事务

-- InnoDB 引擎支持事务
CREATE TABLE t_order (
    id BIGINT PRIMARY KEY,
    name VARCHAR(100)
) ENGINE=InnoDB;  // ✅ 支持事务
```

### 5.7 事务管理器配置错误 ⭐⭐⭐

```java
@Service
public class OrderService {
    
    @Autowired
    private JdbcTemplate secondaryJdbcTemplate;
    
    // ❌ 使用默认事务管理器（primary的），数据源不匹配
    @Transactional
    public void createOrder() {
        secondaryJdbcTemplate.update("INSERT INTO t_order ...");
    }
    
    // ✅ 指定正确的事务管理器
    @Transactional(transactionManager = "secondaryTransactionManager")
    public void createOrder2() {
        secondaryJdbcTemplate.update("INSERT INTO t_order ...");
    }
}
```

### 5.8 类未被 Spring 管理 ⭐⭐⭐

```java
// ❌ 没有被 Spring 管理，@Transactional 无效
public class OrderService {
    @Transactional
    public void createOrder() { }
}

// ✅ 被 Spring 管理
@Service
public class OrderService {
    @Transactional
    public void createOrder() { }
}
```

### 5.9 方法用 final/static 修饰 ⭐⭐

```java
@Service
public class OrderService {
    
    @Transactional
    public final void finalMethod() { }  // ❌ final 方法，无法代理
    
    @Transactional
    public static void staticMethod() { }  // ❌ static 方法，无法代理
}
```

### 5.10 事务失效检测清单

| 检查项 | 失效概率 | 检测方法 | 修复方案 |
|--------|---------|---------|---------|
| 同类方法调用（this） | ⭐⭐⭐⭐⭐ 极高 | 检查是否通过代理调用 | 注入自身 / AopContext / 拆分Service |
| 非 public 方法 | ⭐⭐⭐⭐⭐ 必定 | 检查方法修饰符 | 改为 public |
| 异常被捕获未抛出 | ⭐⭐⭐⭐⭐ 极高 | 检查异常处理逻辑 | 抛出异常或手动回滚 |
| checked 异常未配置 | ⭐⭐⭐⭐ 高 | 检查 rollbackFor | 添加 rollbackFor=Exception.class |
| 多线程环境 | ⭐⭐⭐⭐ 高 | 检查是否在新线程中操作 | 使用 @Async 或事务同步 |
| final/static 方法 | ⭐⭐⭐⭐ 必定 | 检查方法修饰符 | 移除 final/static |
| 类未被 Spring 管理 | ⭐⭐⭐⭐ 必定 | 检查 @Service 等注解 | 添加组件注解 |
| 数据库引擎不支持 | ⭐⭐⭐ 中 | 检查表引擎 | 改为 InnoDB |
| 事务管理器配置错误 | ⭐⭐⭐ 中 | 检查数据源和事务管理器匹配 | 指定正确的 transactionManager |

---

## 六、事务管理器架构详解

### 6.1 PlatformTransactionManager 接口

```java
/**
 * Spring 事务管理的核心接口
 * 定义了事务的基本操作：获取事务、提交事务、回滚事务
 */
public interface PlatformTransactionManager {
    
    /**
     * 获取事务状态
     * @param definition 事务定义（传播行为、隔离级别等）
     * @return 事务状态对象
     */
    TransactionStatus getTransaction(@Nullable TransactionDefinition definition);
    
    /**
     * 提交事务
     * @param status 事务状态对象
     */
    void commit(TransactionStatus status);
    
    /**
     * 回滚事务
     * @param status 事务状态对象
     */
    void rollback(TransactionStatus status);
}
```

### 6.2 事务管理器继承体系

```
PlatformTransactionManager (接口)
    │
    ├── AbstractPlatformTransactionManager (抽象类)
    │       │
    │       ├── DataSourceTransactionManager (JDBC单数据源)
    │       │       └── 管理 JDBC Connection 的事务
    │       │
    │       ├── JpaTransactionManager (JPA)
    │       │       └── 管理 JPA EntityManager 的事务
    │       │
    │       ├── HibernateTransactionManager (Hibernate)
    │       │       └── 管理 Hibernate Session 的事务
    │       │
    │       └── JtaTransactionManager (分布式XA)
    │               └── 管理 JTA 分布式事务
    │
    └── 其他实现...
        ├── WebLogicJtaTransactionManager
        ├── WebSphereUowTransactionManager
        └──...
```

### 6.3 DataSourceTransactionManager 详解

#### 核心属性
```java
public class DataSourceTransactionManager extends AbstractPlatformTransactionManager {
    
    /**
     * 数据源
     * 类型：javax.sql.DataSource
     * 说明：从中获取数据库连接
     */
    private DataSource dataSource;
    
    /**
     * 是否允许嵌套事务（使用JDBC 3.0+的Savepoint）
     * 类型：boolean
     * 默认值：true
     */
    private boolean nestedTransactionAllowed = true;
    
    /**
     * 是否在事务失败时提前释放连接
     * 类型：boolean
     * 默认值：false
     */
    private boolean failEarlyOnGlobalRollbackOnly = false;
}
```

#### 获取事务流程
```
getTransaction(definition)
    │
    ├── 1. 检查当前线程是否已有事务
    │       └── TransactionSynchronizationManager.getResource(dataSource)
    │           │
    │           ├── 有事务 → 根据传播行为处理
    │           │   ├── REQUIRED → 加入现有事务
    │           │   ├── REQUIRES_NEW → 挂起现有，创建新事务
    │           │   ├── NESTED → 创建Savepoint
    │           │   └──...
    │           │
    │           └── 无事务 → 根据传播行为处理
    │               ├── REQUIRED → 创建新事务
    │               ├── REQUIRES_NEW → 创建新事务
    │               ├── MANDATORY → 抛出异常
    │               └──...
    │
    ├── 2. 从DataSource获取Connection
    │       └── dataSource.getConnection()
    │
    ├── 3. 设置隔离级别
    │       └── con.setTransactionIsolation(isolationLevel)
    │
    ├── 4. 设置只读模式
    │       └── con.setReadOnly(true)
    │
    ├── 5. 关闭自动提交
    │       └── con.setAutoCommit(false)
    │
    └── 6. 绑定连接到线程
            └── TransactionSynchronizationManager.bindResource(dataSource, connHolder)
```

#### 提交事务流程
```
commit(status)
    │
    ├── 1. 检查是否已完成
    │       └── status.isCompleted() → 是则抛异常
    │
    ├── 2. 检查是否标记为回滚
    │       └── status.isRollbackOnly() → 是则执行回滚
    │
    ├── 3. 触发 beforeCommit 回调
    │       └── TransactionSynchronization.beforeCommit()
    │
    ├── 4. 执行数据库提交
    │       └── Connection.commit()
    │
    ├── 5. 触发 afterCommit 回调
    │       └── TransactionSynchronization.afterCommit()
    │
    └── 6. 清理资源
            ├── 解绑线程资源
            ├── 关闭连接
            └── 触发 afterCompletion 回调
```

#### 回滚事务流程
```
rollback(status)
    │
    ├── 1. 检查是否已完成
    │       └── status.isCompleted() → 是则抛异常
    │
    ├── 2. 判断回滚类型
    │       │
    │       ├── 是Savepoint（NESTED）
    │       │   └── Connection.rollback(savepoint)
    │       │
    │       └── 是普通事务
    │           ├── 触发 beforeCompletion 回调
    │           ├── Connection.rollback()
    │           └── 触发 afterCompletion 回调
    │
    └── 3. 清理资源
            ├── 解绑线程资源
            └── 关闭连接
```

### 6.4 TransactionStatus 接口

```java
/**
 * 事务状态接口，提供事务的运行时状态和控制能力
 */
public interface TransactionStatus extends TransactionExecution, SavepointManager {
    
    /**
     * 是否是新事务
     * 用于判断是否需要在 commit/rollback 后执行清理
     */
    boolean isNewTransaction();
    
    /**
     * 是否有Savepoint（NESTED事务）
     */
    boolean hasSavepoint();
    
    /**
     * 是否设置为只回滚
     */
    void setRollbackOnly();
    
    /**
     * 是否已标记为只回滚
     */
    boolean isRollbackOnly();
    
    /**
     * 事务是否已完成（已提交或已回滚）
     */
    boolean isCompleted();
}

/**
 * Savepoint 管理接口
 */
public interface SavepointManager {
    
    /**
     * 创建Savepoint
     */
    Object createSavepoint() throws TransactionException;
    
    /**
     * 回滚到指定Savepoint
     */
    void rollbackToSavepoint(Object savepoint) throws TransactionException;
    
    /**
     * 释放Savepoint
     */
    void releaseSavepoint(Object savepoint) throws TransactionException;
}
```

---

## 七、事务拦截器执行流程

### 7.1 TransactionInterceptor 结构

```java
/**
 * 事务拦截器，AOP 代理的核心
 * 拦截被 @Transactional 标记的方法，实现事务管理
 */
public class TransactionInterceptor extends TransactionAspectSupport implements MethodInterceptor {
    
    /**
     * AOP 方法拦截入口
     */
    @Override
    @Nullable
    public Object invoke(MethodInvocation invocation) throws Throwable {
        Class<?> targetClass = (invocation.getThis() != null ? 
            AopUtils.getTargetClass(invocation.getThis()) : null);
        
        // 调用父类的通用事务处理方法
        return invokeWithinTransaction(
            invocation.getMethod(), 
            targetClass, 
            invocation::proceed  // 业务方法执行回调
        );
    }
}
```

### 7.2 完整执行流程图

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                      TransactionInterceptor 执行流程                          │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ① 方法调用                                                                  │
│     │                                                                       │
│     ▼                                                                       │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │ Proxy.invoke()                                                      │   │
│  │  └── DynamicAdvisedInterceptor.intercept()                          │   │
│  │       └── TransactionInterceptor.invoke()  ← 事务拦截器入口          │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│     │                                                                       │
│     ▼                                                                       │
│  ② 获取事务属性                                                              │
│     │                                                                       │
│     └── AnnotationTransactionAttributeSource.getTransactionAttribute()      │
│         └── 解析 @Transactional 注解                                         │
│             ├── 传播行为 (Propagation)                                       │
│             ├── 隔离级别 (Isolation)                                         │
│             ├── 超时时间 (Timeout)                                           │
│             └── 回滚规则 (RollbackFor)                                       │
│                                                                             │
│     ▼                                                                       │
│  ③ 获取事务管理器                                                            │
│     │                                                                       │
│     └── determineTransactionManager(txAttr)                                 │
│         └── 从 @Transactional 中解析或使用默认事务管理器                      │
│                                                                             │
│     ▼                                                                       │
│  ④ 创建/加入事务                                                             │
│     │                                                                       │
│     └── createTransactionIfNecessary(tm, txAttr, joinpointIdentification)   │
│         └── PlatformTransactionManager.getTransaction(txAttr)               │
│             ├── 检查现有事务（ThreadLocal）                                   │
│             ├── 根据传播行为决定                                              │
│             │   ├── 加入现有事务                                              │
│             │   ├── 挂起现有，创建新事务                                       │
│             │   └── 创建Savepoint（NESTED）                                  │
│             └── 绑定新事务到线程（TransactionSynchronizationManager）        │
│                                                                             │
│     ▼                                                                       │
│  ⑤ 执行业务方法                                                              │
│     │                                                                       │
│     └── invocation.proceedWithInvocation()                                  │
│         └── 实际业务逻辑执行                                                  │
│             └── 可能发生异常                                                  │
│                                                                             │
│     ▼                                                                       │
│  ⑥ 异常处理（发生异常时）                                                     │
│     │                                                                       │
│     └── completeTransactionAfterThrowing(txInfo, ex)                        │
│         ├── 判断异常是否需要回滚                                              │
│         │   └── txAttr.rollbackOn(ex)                                        │
│         │       ├── 检查是否在 noRollbackFor 中                               │
│         │       └── 检查是否在 rollbackFor 中                                 │
│         │       └── 默认：RuntimeException/Error 回滚                         │
│         │                                                                   │
│         ├── 需要回滚 → txInfo.getTransactionManager().rollback()             │
│         │   └── Connection.rollback()                                        │
│         │                                                                   │
│         └── 不需要回滚 → txInfo.getTransactionManager().commit()             │
│                                                                             │
│     ▼                                                                       │
│  ⑦ 提交事务（正常结束时）                                                     │
│     │                                                                       │
│     └── commitTransactionAfterReturning(txInfo)                             │
│         ├── 检查是否标记为 rollback-only                                     │
│         ├── txInfo.getTransactionManager().commit()                         │
│         │   └── Connection.commit()                                          │
│         └── 清理资源（解绑ThreadLocal，关闭连接）                             │
│                                                                             │
│     ▼                                                                       │
│  ⑧ 清理资源                                                                 │
│     │                                                                       │
│     └── cleanupTransactionInfo(txInfo)                                      │
│         ├── 恢复挂起的事务（如果有）                                          │
│         └── 触发 afterCompletion 回调                                         │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 7.3 核心方法源码解析

#### invokeWithinTransaction（事务执行核心）
```java
protected Object invokeWithinTransaction(Method method, @Nullable Class<?> targetClass,
        final InvocationCallback invocation) throws Throwable {
    
    // ========== 1. 获取事务属性 ==========
    TransactionAttributeSource tas = getTransactionAttributeSource();
    final TransactionAttribute txAttr = (tas != null ? 
        tas.getTransactionAttribute(method, targetClass) : null);
    
    // ========== 2. 确定事务管理器 ==========
    final PlatformTransactionManager tm = determineTransactionManager(txAttr);
    
    // ========== 3. 构造方法标识（用于日志） ==========
    final String joinpointIdentification = methodIdentification(method, targetClass, txAttr);
    
    // ========== 4. 标准声明式事务处理 ==========
    if (txAttr == null || !(tm instanceof CallbackPreferringPlatformTransactionManager)) {
        
        // 4.1 创建/加入事务（如果必要）
        TransactionInfo txInfo = createTransactionIfNecessary(tm, txAttr, joinpointIdentification);
        
        Object retVal;
        try {
            // 4.2 执行业务方法
            retVal = invocation.proceedWithInvocation();
        }
        catch (Throwable ex) {
            // 4.3 异常处理：完成事务（提交或回滚）
            completeTransactionAfterThrowing(txInfo, ex);
            throw ex;
        }
        finally {
            // 4.4 清理事务信息
            cleanupTransactionInfo(txInfo);
        }
        
        // 4.5 正常完成：提交事务
        commitTransactionAfterReturning(txInfo);
        return retVal;
    }
    
    // ========== 5. 编程式事务处理（Callback方式） ==========
    else {
        // 使用回调方式执行...
    }
}
```

#### 回滚规则判断
```java
/**
 * 判断异常是否触发回滚
 */
public boolean rollbackOn(Throwable ex) {
    // 1. 检查是否在 noRollbackFor 中
    if (this.noRollbackFor != null) {
        for (Class<? extends Throwable> noRollbackClass : this.noRollbackFor) {
            if (noRollbackClass.isInstance(ex)) {
                return false;  // 不回滚
            }
        }
    }
    
    // 2. 检查是否在 rollbackFor 中
    if (this.rollbackFor != null) {
        for (Class<? extends Throwable> rollbackClass : this.rollbackFor) {
            if (rollbackClass.isInstance(ex)) {
                return true;  // 回滚
            }
        }
    }
    
    // 3. 默认规则：RuntimeException 和 Error 回滚
    return (ex instanceof RuntimeException || ex instanceof Error);
}
```

---

## 八、事务同步机制

### 8.1 TransactionSynchronizationManager

```java
/**
 * 事务同步管理器
 * 管理事务资源和事务同步回调
 * 所有数据存储在 ThreadLocal 中，保证线程安全
 */
public abstract class TransactionSynchronizationManager {
    
    // ========== ThreadLocal 存储 ==========
    
    /**
     * 存储事务资源（Connection、Session等）
     * Map<Object, Object>：key=资源标识（如DataSource），value=资源包装器
     */
    private static final ThreadLocal<Map<Object, Object>> resources = 
        new NamedThreadLocal<>("Transactional resources");
    
    /**
     * 存储事务同步回调
     */
    private static final ThreadLocal<Set<TransactionSynchronization>> synchronizations = 
        new NamedThreadLocal<>("Transaction synchronizations");
    
    /**
     * 当前事务名称
     */
    private static final ThreadLocal<String> currentTransactionName = 
        new NamedThreadLocal<>("Current transaction name");
    
    /**
     * 是否只读事务
     */
    private static final ThreadLocal<Boolean> currentTransactionReadOnly = 
        new NamedThreadLocal<>("Current transaction read-only status");
    
    /**
     * 隔离级别
     */
    private static final ThreadLocal<Integer> currentTransactionIsolationLevel = 
        new NamedThreadLocal<>("Current transaction isolation level");
    
    /**
     * 是否实际活跃
     */
    private static final ThreadLocal<Boolean> actualTransactionActive = 
        new NamedThreadLocal<>("Actual transaction active");
}
```

### 8.2 事务同步回调接口

```java
/**
 * 事务同步回调接口
 * 允许在事务生命周期关键点执行自定义逻辑
 */
public interface TransactionSynchronization extends Flushable {
    
    // ========== 状态常量 ==========
    int STATUS_COMMITTED = 0;      // 已提交
    int STATUS_ROLLED_BACK = 1;    // 已回滚
    int STATUS_UNKNOWN = 2;        // 状态未知
    
    // ========== 事务暂停/恢复 ==========
    
    /**
     * 事务挂起时调用
     * 用于保存当前线程的状态（如Hibernate的Session）
     */
    default void suspend() {}
    
    /**
     * 事务恢复时调用
     * 用于恢复之前保存的状态
     */
    default void resume() {}
    
    // ========== 刷新相关 ==========
    
    /**
     * 事务刷盘时调用
     */
    @Override
    default void flush() {}
    
    // ========== 提交阶段 ==========
    
    /**
     * 提交前调用（事务还未提交，可以回滚）
     * 用于执行必须在事务提交前完成的操作
     */
    default void beforeCommit(boolean readOnly) {}
    
    /**
     * 提交完成后调用（事务已提交）
     * 用于执行依赖事务结果的操作（如发送消息）
     */
    default void afterCommit() {}
    
    // ========== 完成阶段（无论提交或回滚都会调用）==========
    
    /**
     * 事务完成前调用
     */
    default void beforeCompletion() {}
    
    /**
     * 事务完成后调用
     * @param status 事务最终状态（COMMITTED/ROLLED_BACK/UNKNOWN）
     */
    default void afterCompletion(int status) {}
}
```

### 8.3 回调执行时机图

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                      事务同步回调执行时机                                     │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  事务开始                                                                    │
│     │                                                                       │
│     ▼                                                                       │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                        业务方法执行中                                │   │
│  │  TransactionSynchronizationManager.registerSynchronization(callback) │   │
│  │  // 注册同步回调                                                      │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│     │                                                                       │
│     │  方法正常结束                                                         │
│     ▼                                                                       │
│  beforeCommit(readOnly)  ← 事务提交前，还可以回滚                            │
│     │                                                                       │
│     ▼                                                                       │
│  Connection.commit()  ← 数据库提交                                           │
│     │                                                                       │
│     ▼                                                                       │
│  afterCommit()  ← 事务已提交，可发消息等                                     │
│     │                                                                       │
│     ▼                                                                       │
│  beforeCompletion()  ← 完成前清理                                            │
│     │                                                                       │
│     ▼                                                                       │
│  afterCompletion(STATUS_COMMITTED)  ← 最终清理                               │
│                                                                             │
│  ─────────────────────────────────────────────────────────────────────────  │
│                                                                             │
│  方法异常结束                                                                │
│     │                                                                       │
│     ▼                                                                       │
│  Connection.rollback()  ← 数据库回滚                                         │
│     │                                                                       │
│     ▼                                                                       │
│  beforeCompletion()  ← 完成前清理                                            │
│     │                                                                       │
│     ▼                                                                       │
│  afterCompletion(STATUS_ROLLED_BACK)  ← 最终清理                             │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 8.4 实际应用场景

#### 场景1：事务提交后发送MQ消息
```java
@Service
public class OrderService {
    
    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;
    
    @Transactional
    public void createOrder(Order order) {
        // 1. 保存订单
        orderDao.save(order);
        
        // 2. 注册事务同步回调
        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    // 事务提交成功后发送消息
                    kafkaTemplate.send("order-topic", 
                        JsonUtils.toJson(order));
                }
            }
        );
        
        // 如果这里发生异常，事务回滚，消息不会发送
    }
}
```

#### 场景2：清理线程本地数据
```java
@Service
public class ContextService {
    
    private static final ThreadLocal<UserContext> contextHolder = 
        new ThreadLocal<>();
    
    @Transactional
    public void businessMethod() {
        // 设置上下文
        contextHolder.set(new UserContext(userId));
        
        // 注册清理回调
        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    // 无论事务结果如何，清理上下文
                    contextHolder.remove();
                }
            }
        );
        
        // 业务逻辑...
    }
}
```

---

## 九、保存点与嵌套事务

### 9.1 Savepoint 原理

```java
/**
 * JDBC 3.0 引入的保存点机制
 * 允许在事务内设置回滚点，实现部分回滚
 */
public interface SavepointManager {
    
    /**
     * 创建保存点
     * @return 保存点对象
     */
    Object createSavepoint() throws TransactionException;
    
    /**
     * 回滚到指定保存点
     * 保存点之后的操作被回滚，之前的保留
     */
    void rollbackToSavepoint(Object savepoint) throws TransactionException;
    
    /**
     * 释放保存点
     * 释放后不能再回滚到该点
     */
    void releaseSavepoint(Object savepoint) throws TransactionException;
}
```

### 9.2 NESTED 传播行为实现

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                      NESTED 传播行为执行流程                                  │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  外部方法（REQUIRED）                                                         │
│     │                                                                       │
│     └── BEGIN TRANSACTION;  ← 开启事务T1                                     │
│         INSERT INTO order ...;                                               │
│         │                                                                   │
│         └── 调用内部方法（NESTED）                                            │
│             │                                                               │
│             └── SAVEPOINT savepoint_1;  ← 创建保存点                          │
│                 INSERT INTO item ...;                                        │
│                 │                                                           │
│                 ├── 成功 → RELEASE SAVEPOINT savepoint_1;                    │
│                 │           返回继续执行                                      │
│                 │                                                           │
│                 └── 失败 → ROLLBACK TO SAVEPOINT savepoint_1;                │
│                             回滚到保存点，外部事务继续                         │
│         │                                                                   │
│         └── 继续执行外部方法                                                  │
│             UPDATE order ...;                                                │
│             COMMIT;  ← 提交整个事务                                           │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 9.3 JDBC Savepoint 代码示例

```java
@Service
public class SavepointDemoService {
    
    @Autowired
    private DataSource dataSource;
    
    public void demoSavepoint() throws SQLException {
        Connection conn = dataSource.getConnection();
        conn.setAutoCommit(false);
        
        try {
            // 插入订单（外层操作）
            PreparedStatement ps1 = conn.prepareStatement(
                "INSERT INTO orders (id, amount) VALUES (?, ?)");
            ps1.setLong(1, 1);
            ps1.setBigDecimal(2, new BigDecimal("100.00"));
            ps1.executeUpdate();
            
            // 创建保存点
            Savepoint savepoint = conn.setSavepoint("after_order");
            
            try {
                // 插入订单项（内层操作）
                PreparedStatement ps2 = conn.prepareStatement(
                    "INSERT INTO order_items (order_id, item_name) VALUES (?, ?)");
                ps2.setLong(1, 1);
                ps2.setString(2, "Item1");
                ps2.executeUpdate();
                
                // 模拟内层异常
                throw new RuntimeException("Item处理异常");
                
            } catch (Exception e) {
                // 回滚到保存点（只回滚订单项，保留订单）
                conn.rollback(savepoint);
                System.out.println("订单项回滚，订单保留");
            }
            
            // 继续执行其他操作
            PreparedStatement ps3 = conn.prepareStatement(
                "UPDATE orders SET status = ? WHERE id = ?");
            ps3.setString(1, "PARTIAL");
            ps3.setLong(2, 1);
            ps3.executeUpdate();
            
            // 提交整个事务
            conn.commit();
            System.out.println("事务提交成功");
            
        } catch (Exception e) {
            conn.rollback();
            System.out.println("整个事务回滚");
        } finally {
            conn.close();
        }
    }
}
```

### 9.4 Spring NESTED 使用示例

```java
@Service
public class OrderService {
    
    @Autowired
    private ItemService itemService;
    
    @Transactional
    public void createOrder(Order order, List<Item> items) {
        // 保存订单（外层事务）
        orderDao.save(order);
        
        int successCount = 0;
        for (Item item : items) {
            try {
                // 每个商品使用NESTED事务
                itemService.saveItem(order.getId(), item);
                successCount++;
            } catch (Exception e) {
                // 记录失败，继续处理下一个
                log.warn("商品保存失败: {}", item.getId());
            }
        }
        
        if (successCount == 0) {
            throw new RuntimeException("所有商品保存失败");
        }
        
        // 更新订单状态
        orderDao.updateStatus(order.getId(), "CREATED");
    }
}

@Service
public class ItemService {
    
    @Transactional(propagation = Propagation.NESTED)
    public void saveItem(Long orderId, Item item) {
        item.setOrderId(orderId);
        itemDao.save(item);
        
        // 检查库存
        if (inventoryDao.getStock(item.getSkuId()) < item.getQuantity()) {
            throw new InsufficientStockException("库存不足");
            // 这里抛出异常，只回滚当前item，不影响其他item和order
        }
        
        inventoryDao.deduct(item.getSkuId(), item.getQuantity());
    }
}
```

---

## 十、编程式事务详解

### 10.1 TransactionTemplate 详解

```java
/**
 * 编程式事务模板类
 * 简化了 PlatformTransactionManager 的使用
 */
public class TransactionTemplate extends DefaultTransactionDefinition {
    
    private PlatformTransactionManager transactionManager;
    
    /**
     * 执行事务（无返回值）
     */
    public void executeWithoutResult(Consumer<TransactionStatus> action) {
        execute(status -> {
            action.accept(status);
            return null;
        });
    }
    
    /**
     * 执行事务（有返回值）
     */
    @Nullable
    public <T> T execute(TransactionCallback<T> action) {
        // 1. 获取事务
        TransactionStatus status = transactionManager.getTransaction(this);
        
        T result;
        try {
            // 2. 执行业务逻辑
            result = action.doInTransaction(status);
        }
        catch (RuntimeException | Error ex) {
            // 3. 运行时异常，回滚事务
            rollbackOnException(status, ex);
            throw ex;
        }
        catch (Exception ex) {
            // 4. Checked 异常，回滚事务
            rollbackOnException(status, ex);
            throw new UndeclaredThrowableException(ex);
        }
        
        // 5. 提交事务
        transactionManager.commit(status);
        return result;
    }
}
```

### 10.2 TransactionTemplate 使用示例

#### 基本使用
```java
@Service
public class OrderService {
    
    @Autowired
    private TransactionTemplate transactionTemplate;
    
    public Order createOrder(OrderDTO dto) {
        // 非事务操作：参数校验
        validate(dto);
        
        // 事务操作：保存订单
        Order order = transactionTemplate.execute(status -> {
            Order o = convert(dto);
            orderDao.save(o);
            inventoryDao.deduct(o.getItems());
            return o;
        });
        
        // 非事务操作：发送通知
        notificationService.send(order);
        
        return order;
    }
}
```

#### 手动回滚
```java
@Service
public class ComplexService {
    
    @Autowired
    private TransactionTemplate transactionTemplate;
    
    public boolean processWithFallback(Data data) {
        Boolean result = transactionTemplate.execute(status -> {
            try {
                // 尝试执行
                doProcess(data);
                return true;
            } catch (BusinessException e) {
                // 业务异常，手动回滚
                status.setRollbackOnly();
                return false;
            }
        });
        
        if (!result) {
            // 执行回退逻辑
            fallback(data);
        }
        
        return result;
    }
}
```

#### 设置事务属性
```java
@Service
public class BatchService {
    
    @Autowired
    private PlatformTransactionManager transactionManager;
    
    private TransactionTemplate transactionTemplate;
    
    @PostConstruct
    public void init() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        // 设置传播行为
        transactionTemplate.setPropagationBehavior(
            TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        // 设置隔离级别
        transactionTemplate.setIsolationLevel(
            TransactionDefinition.ISOLATION_READ_COMMITTED);
        // 设置超时时间
        transactionTemplate.setTimeout(30);
        // 设置只读
        transactionTemplate.setReadOnly(false);
    }
    
    public void batchProcess(List<Data> dataList) {
        for (Data data : dataList) {
            transactionTemplate.execute(status -> {
                processSingle(data);
                return null;
            });
        }
    }
}
```

### 10.3 PlatformTransactionManager 直接使用

```java
@Service
public class LowLevelTxService {
    
    @Autowired
    private PlatformTransactionManager transactionManager;
    
    public void manualTransaction() {
        // 1. 定义事务属性
        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        def.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        def.setTimeout(30);
        
        // 2. 获取事务状态
        TransactionStatus status = transactionManager.getTransaction(def);
        
        try {
            // 3. 执行业务逻辑
            businessDao.operation1();
            businessDao.operation2();
            
            // 4. 提交事务
            transactionManager.commit(status);
        } catch (Exception e) {
            // 5. 回滚事务
            transactionManager.rollback(status);
            throw new BusinessException("业务失败", e);
        }
    }
}
```

### 10.4 声明式 vs 编程式选择指南

| 场景 | 推荐方式 | 原因 |
|------|---------|------|
| 标准CRUD操作 | 声明式 | 简洁，无侵入 |
| 大事务拆分 | 编程式 | 精确控制事务边界 |
| 动态决定是否开启事务 | 编程式 | 运行时决策 |
| 事务内需要细粒度控制 | 编程式 | 可手动回滚部分操作 |
| 事务内需要捕获异常继续 | 编程式 | 灵活处理异常 |
| 需要访问TransactionStatus | 编程式 | 获取事务状态 |
| 事务内调用外部HTTP/RPC | 编程式 | 缩小事务范围 |
| 批量处理 | 编程式 | 每批独立事务 |

---

## 十一、生产环境最佳实践

### 11.1 事务边界设计原则

#### ❌ Bad：事务范围过大
```java
@Service
public class OrderService {
    
    @Transactional
    public void createOrder(OrderDTO dto) {
        // 1. 参数校验（不需要事务）
        validate(dto);  // 应该在事务外
        
        // 2. 查询用户信息（不需要事务）
        User user = userService.getById(dto.getUserId());  // 应该在事务外
        
        // 3. 调用外部接口（网络IO，耗时2秒）
        inventoryResult = inventoryClient.check(dto.getItems());  // ❌ 大忌！
        
        // 4. 保存订单（需要事务）
        orderDao.save(order);
        
        // 5. 发送消息（不需要事务）
        kafkaTemplate.send("order-topic", order);  // 应该在事务外
    }
}
```

**问题**：
- 事务持续时间过长（2秒+）
- 占用数据库连接池资源
- 增加锁持有时间，降低并发性能
- 外部接口失败导致事务回滚，但可能只是网络抖动

#### ✅ Good：缩小事务范围
```java
@Service
public class OrderService {
    
    @Autowired
    private TransactionTemplate transactionTemplate;
    
    public void createOrder(OrderDTO dto) {
        // 1. 参数校验（无事务）
        validate(dto);
        
        // 2. 查询用户信息（无事务）
        User user = userService.getById(dto.getUserId());
        
        // 3. 调用外部接口（无事务）
        inventoryResult = inventoryClient.check(dto.getItems());
        
        // 4. 核心业务开启事务（范围最小化）
        Order order = transactionTemplate.execute(status -> {
            Order o = buildOrder(dto, user);
            orderDao.save(o);
            orderItemDao.saveBatch(o.getItems());
            inventoryDao.deduct(o.getItems());
            return o;
        });
        
        // 5. 发送消息（无事务，使用事务同步）
        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    kafkaTemplate.send("order-topic", order);
                }
            }
        );
    }
}
```

### 11.2 只读事务优化

```java
@Service
public class UserService {
    
    /**
     * 查询方法标记为只读
     * 好处：
     * 1. MySQL不加写锁，提高并发性能
     * 2. Spring不刷新持久化上下文（JPA/Hibernate）
     * 3. 某些数据库可以路由到从库
     */
    @Transactional(readOnly = true)
    public List<User> queryUsers(UserQuery query) {
        return userDao.query(query);
    }
    
    /**
     * 复杂报表查询，配合RC隔离级别
     * 减少锁竞争，提高并发
     */
    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public Report generateReport(Date start, Date end) {
        // 复杂查询逻辑
    }
}
```

### 11.3 事务超时设置

```java
@Service
public class BatchService {
    
    /**
     * 大批量操作，设置超时防止长时间占用连接
     */
    @Transactional(timeout = 30)  // 30秒超时
    public void batchProcess(List<Data> dataList) {
        for (Data data : dataList) {
            process(data);
        }
    }
    
    /**
     * 分页处理，每页一个事务（推荐）
     */
    public void batchProcessSafe(List<Data> dataList) {
        int pageSize = 100;
        List<List<Data>> partitions = Lists.partition(dataList, pageSize);
        
        for (List<Data> page : partitions) {
            transactionTemplate.execute(status -> {
                for (Data data : page) {
                    process(data);
                }
                return null;
            });
        }
    }
}
```

### 11.4 异常处理规范

#### ❌ Bad：捕获所有异常
```java
@Transactional
public void createOrder() {
    try {
        orderDao.save(order);
        paymentService.charge(order);
    } catch (Exception e) {
        log.error("创建订单失败", e);
        // ❌ 没有抛出异常，也没有手动回滚
    }
}
```

#### ✅ Good：明确异常处理
```java
@Transactional
public void createOrder() throws Exception {
    orderDao.save(order);
    
    try {
        paymentService.charge(order);
    } catch (PaymentException e) {
        log.error("支付失败", e);
        throw new OrderCreationException("支付失败", e);  // 抛出自定义异常
    }
}

/**
 * 特定异常不回滚
 */
@Transactional(noRollbackFor = BusinessException.class)
public void createOrderWithNotification() {
    orderDao.save(order);
    
    try {
        notificationService.send(order);
    } catch (NotificationException e) {
        // 通知失败不影响主业务
        log.warn("通知发送失败", e);
    }
}
```

### 11.5 大事务拆分策略

```java
@Service
public class BigDataService {
    
    @Autowired
    private TransactionTemplate transactionTemplate;
    
    /**
     * ❌ Bad：一个大事务处理所有数据
     */
    @Transactional
    public void processAllBad(List<Data> allData) {
        for (Data data : allData) {
            process(data);
        }
    }
    
    /**
     * ✅ Good：分批处理，每批一个事务
     */
    public void processAllGood(List<Data> allData) {
        int batchSize = 100;
        for (int i = 0; i < allData.size(); i += batchSize) {
            List<Data> batch = allData.subList(i, 
                Math.min(i + batchSize, allData.size()));
            
            transactionTemplate.execute(status -> {
                for (Data data : batch) {
                    process(data);
                }
                return null;
            });
        }
    }
    
    /**
     * ✅ Good：流式处理，避免内存溢出
     */
    public void processStream(Stream<Data> dataStream) {
        AtomicInteger counter = new AtomicInteger(0);
        List<Data> buffer = new ArrayList<>(100);
        
        dataStream.forEach(data -> {
            buffer.add(data);
            if (counter.incrementAndGet() % 100 == 0) {
                flushBuffer(buffer);
            }
        });
        
        // 处理剩余数据
        if (!buffer.isEmpty()) {
            flushBuffer(buffer);
        }
    }
    
    private void flushBuffer(List<Data> buffer) {
        transactionTemplate.execute(status -> {
            for (Data data : new ArrayList<>(buffer)) {
                process(data);
            }
            return null;
        });
        buffer.clear();
    }
}
```

### 11.6 事务与锁的配合

```java
@Service
public class AccountService {
    
    /**
     * ❌ Bad：先查后更新，可能并发问题
     */
    @Transactional
    public void transferBad(Long fromId, Long toId, BigDecimal amount) {
        Account from = accountDao.findById(fromId);
        Account to = accountDao.findById(toId);
        
        from.setBalance(from.getBalance().subtract(amount));
        to.setBalance(to.getBalance().add(amount));
        
        accountDao.update(from);
        accountDao.update(to);
        // 问题：两次查询之间，余额可能被其他事务修改
    }
    
    /**
     * ✅ Good：使用悲观锁
     */
    @Transactional
    public void transferPessimistic(Long fromId, Long toId, BigDecimal amount) {
        // 使用 SELECT FOR UPDATE 加锁（顺序加锁避免死锁）
        Account from = accountDao.findByIdForUpdate(fromId);
        Account to = accountDao.findByIdForUpdate(toId);
        
        // 校验余额
        if (from.getBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException("余额不足");
        }
        
        from.setBalance(from.getBalance().subtract(amount));
        to.setBalance(to.getBalance().add(amount));
        
        accountDao.update(from);
        accountDao.update(to);
    }
    
    /**
     * ✅ Good：使用乐观锁
     */
    @Transactional
    public void transferOptimistic(Long fromId, Long toId, BigDecimal amount) {
        int retries = 3;
        while (retries-- > 0) {
            Account from = accountDao.findById(fromId);
            Account to = accountDao.findById(toId);
            
            if (from.getBalance().compareTo(amount) < 0) {
                throw new InsufficientBalanceException("余额不足");
            }
            
            from.setBalance(from.getBalance().subtract(amount));
            to.setBalance(to.getBalance().add(amount));
            
            // 使用版本号控制并发
            int updated = accountDao.updateWithVersion(from);
            if (updated == 0) {
                // 版本冲突，重试
                continue;
            }
            
            updated = accountDao.updateWithVersion(to);
            if (updated == 0) {
                throw new ConcurrentModificationException("转账失败，请重试");
            }
            
            return;  // 成功
        }
        throw new ConcurrentModificationException("转账失败，请重试");
    }
}
```

---

## 十二、分布式事务方案

### 12.1 分布式事务场景

| 场景 | 说明 | 示例 |
|------|------|------|
| **跨库事务** | 操作多个数据库实例 | 订单库 + 库存库 |
| **微服务事务** | 多个服务间的数据一致性 | 订单服务 + 支付服务 + 物流服务 |
| **消息事务** | 本地事务 + 消息发送 | 下单成功 + 发送扣库存消息 |

### 12.2 常见解决方案对比

| 方案 | 原理 | 一致性 | 性能 | 适用场景 | 缺点 |
|-----|------|--------|------|---------|------|
| **2PC/XA** | 两阶段提交 | 强一致性 | 差 | 短事务、低并发 | 同步阻塞、单点故障 |
| **TCC** | Try-Confirm-Cancel | 最终一致性 | 好 | 高并发、短事务 | 业务侵入大、开发成本高 |
| **Saga** | 长事务拆分 + 补偿 | 最终一致性 | 好 | 业务流程长 | 补偿逻辑复杂 |
| **本地消息表** | 本地事务 + 异步发送 | 最终一致性 | 好 | 异步场景 | 实现复杂、需定时任务 |
| **Seata AT** | 自动补偿（SQL解析） | 最终一致性 | 较好 | 通用场景 | 性能损耗、依赖Seata |
| **最大努力通知** | 多次通知直到成功 | 最终一致性 | 好 | 对账类业务 | 不保证一定成功 |

### 12.3 Seata AT 模式详解

```java
/**
 * Seata AT 模式使用示例
 */
@Service
public class BusinessService {
    
    /**
     * @GlobalTransactional 开启全局事务
     */
    @GlobalTransactional(name = "purchase-tx", rollbackFor = Exception.class)
    public void purchase() {
        // 分支事务1：创建订单
        orderService.createOrder();
        
        // 分支事务2：扣减库存
        storageService.deduct();
        
        // 分支事务3：扣减账户余额
        accountService.debit();
        
        // 任意分支失败，全局回滚
    }
}

@Service
public class OrderService {
    
    /**
     * 分支事务自动注册到全局事务
     */
    public void createOrder() {
        // Seata代理数据源，自动注册分支事务
        orderDao.insert(order);
        // 执行前后生成Undo Log
    }
}
```

**Seata AT 执行流程**：
```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        Seata AT 模式执行流程                                  │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  BusinessService.purchase()                                                  │
│     │                                                                       │
│     ├── @GlobalTransactional 拦截                                            │
│     │   └── TC (Transaction Coordinator) 开启全局事务                          │
│     │       └── 生成 XID，全局事务ID                                          │
│     │                                                                       │
│     ├── orderService.createOrder()                                           │
│     │   └── RM (Resource Manager) 注册分支事务                                 │
│     │       ├── 解析SQL，生成前镜像（Undo Log）                                 │
│     │       ├── 执行业务SQL                                                   │
│     │       ├── 生成后镜像                                                    │
│     │       └── 向TC报告分支事务状态                                           │
│     │                                                                       │
│     ├── storageService.deduct()                                              │
│     │   └── RM 注册分支事务...                                                │
│     │                                                                       │
│     ├── accountService.debit()                                               │
│     │   └── RM 注册分支事务...                                                │
│     │                                                                       │
│     └── 业务成功/失败                                                          │
│         │                                                                   │
│         ├── 成功 → TC 发送 Commit 指令                                         │
│         │       └── RM 异步删除 Undo Log                                       │
│         │                                                                   │
│         └── 失败 → TC 发送 Rollback 指令                                       │
│                 └── RM 使用 Undo Log 回滚                                      │
│                     └── 用前镜像数据覆盖当前数据                                │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 12.4 最终一致性方案：本地消息表

```java
/**
 * 本地消息表方案实现
 */
@Service
public class OrderService {
    
    @Transactional
    public void createOrder(OrderDTO dto) {
        // 1. 保存订单
        Order order = new Order();
        BeanUtils.copyProperties(dto, order);
        orderDao.save(order);
        
        // 2. 保存消息到本地消息表（同一事务）
        Message message = new Message();
        message.setTopic("order_created");
        message.setBody(JsonUtils.toJson(order));
        message.setStatus("PENDING");
        message.setRetryCount(0);
        message.setCreateTime(LocalDateTime.now());
        messageDao.save(message);
        
        // 事务提交后，订单和消息都持久化
    }
}

@Component
public class MessageSender {
    
    @Autowired
    private MessageDao messageDao;
    
    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;
    
    /**
     * 定时扫描发送消息
     */
    @Scheduled(fixedRate = 5000)
    public void sendPendingMessages() {
        List<Message> pendingMessages = messageDao.findByStatus("PENDING");
        
        for (Message message : pendingMessages) {
            try {
                kafkaTemplate.send(message.getTopic(), message.getBody())
                    .addCallback(
                        success -> {
                            // 发送成功，更新状态
                            messageDao.updateStatus(message.getId(), "SENT");
                        },
                        failure -> {
                            // 发送失败，增加重试计数
                            messageDao.incrementRetry(message.getId());
                        }
                    );
            } catch (Exception e) {
                // 增加重试计数，超过阈值报警
                messageDao.incrementRetry(message.getId());
            }
        }
    }
}
```

---

## 十三、面试高频问题

### 13.1 基础题

#### Q1：Spring 事务的实现原理？
**答**：Spring 事务基于 AOP 实现。`@Transactional` 注解被解析为事务属性，Spring 为目标对象创建代理（JDK动态代理或CGLIB）。当代理方法被调用时，`TransactionInterceptor` 拦截器会根据事务属性决定是否开启事务、提交或回滚。事务上下文存储在 `ThreadLocal` 中。

#### Q2：@Transactional 的工作原理？
**答**：
1. **解析阶段**：`@EnableTransactionManagement` 导入事务管理配置，`TransactionAttributeSource` 解析 `@Transactional` 注解
2. **代理阶段**：为 `@Transactional` 标记的类创建代理对象
3. **拦截阶段**：方法调用时，`TransactionInterceptor` 拦截
4. **执行阶段**：获取事务 → 执行业务 → 提交/回滚 → 清理资源

#### Q3：什么是事务传播行为？
**答**：传播行为定义了当一个事务方法调用另一个事务方法时，事务如何传播的规则。Spring 提供了7种传播行为：REQUIRED（默认）、SUPPORTS、MANDATORY、REQUIRES_NEW、NOT_SUPPORTED、NEVER、NESTED。

### 13.2 进阶题

#### Q4：REQUIRED 和 REQUIRES_NEW 的区别？
**答**：
- **REQUIRED**：如果当前存在事务，则加入该事务；如果不存在，则新建一个事务。内部方法与外部方法共享同一个事务，任一方法回滚都会导致整个事务回滚。
- **REQUIRES_NEW**：挂起当前事务，创建一个新的事务执行。内部事务独立提交，外部事务回滚不影响内部事务。

#### Q5：事务失效的场景有哪些？（至少说5个）
**答**：
1. **同类方法调用（this调用）**：绕过代理，直接调用目标对象
2. **非 public 方法**：Spring AOP 只能拦截 public 方法
3. **异常被捕获未抛出**：事务无法感知异常
4. **异常类型不匹配**：默认只回滚 RuntimeException，checked 异常需要配置 rollbackFor
5. **多线程环境**：事务上下文存储在 ThreadLocal，新线程获取不到
6. **final/static 方法**：无法被代理
7. **类未被 Spring 管理**：缺少 @Service 等注解

#### Q6：什么是脏读、幻读、不可重复读？
**答**：
- **脏读**：读到其他事务未提交的数据
- **不可重复读**：同一事务内两次读取同一数据，结果不同（被其他事务修改）
- **幻读**：同一事务内两次相同条件查询，返回的行数不同（其他事务插入/删除）

#### Q7：MySQL 如何解决幻读？
**答**：MySQL InnoDB 通过两种方式解决幻读：
1. **快照读（普通SELECT）**：使用 MVCC（多版本并发控制），事务内使用一致的 Read View，读取历史版本数据
2. **当前读（SELECT FOR UPDATE）**：使用间隙锁（Gap Lock）锁定查询范围，阻止其他事务插入数据

### 13.3 原理题

#### Q8：TransactionInterceptor 的执行流程？
**答**：
1. `invoke()` 拦截方法调用
2. 获取事务属性（`@Transactional` 配置）
3. 确定事务管理器
4. `getTransaction()` 根据传播行为获取/创建事务
5. 执行业务方法
6. 异常时 `rollback()`，正常时 `commit()`
7. 清理资源

#### Q9：事务上下文如何存储？
**答**：事务上下文存储在 `ThreadLocal` 中，通过 `TransactionSynchronizationManager` 管理。包括：事务资源（Connection）、同步回调、事务名称、隔离级别、只读状态等。这保证了同一线程内的事务共享，同时线程间隔离。

#### Q10：为什么同类方法调用事务失效？
**答**：Spring 事务基于 AOP 代理实现。调用代理方法时，先进入代理对象，触发 `TransactionInterceptor` 进行事务管理。但 `this.method()` 是直接调用目标对象的方法，绕过代理，因此事务失效。

### 13.4 场景题

#### Q11：设计一个转账功能，如何保证数据一致性？
**答**：
1. 使用 `@Transactional` 包裹转账操作
2. 使用悲观锁（SELECT FOR UPDATE）或乐观锁（版本号）保证并发安全
3. 先扣减后增加（或统一按id排序后操作，避免死锁）
4. 记录转账流水，用于对账
5. 如果是跨服务转账，使用分布式事务（如Seata）或最终一致性方案

#### Q12：日志记录如何不影响主业务事务？
**答**：使用 `Propagation.REQUIRES_NEW` 传播行为：
```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void logOperation(String content) {
    logDao.save(content);
}
```
日志方法独立事务，即使主业务回滚，日志也已提交。

#### Q13：大事务如何优化？
**答**：
1. **缩小事务范围**：只将必要的数据库操作放入事务
2. **大事务拆分**：分批处理，每批一个事务
3. **异步处理**：将非核心逻辑异步化
4. **避免远程调用**：RPC/HTTP调用放在事务外
5. **优化SQL**：减少事务内的SQL执行时间

---

## 十四、源码学习路线图

### 阶段1：概念理解（1-2天）
- [ ] ACID 特性深入理解
- [ ] 7种传播行为对比实验
- [ ] 4种隔离级别与并发问题
- [ ] 事务失效场景复现（至少5个）

### 阶段2：源码精读（3-5天）
- [ ] `@EnableTransactionManagement` 启动流程
  - `TransactionManagementConfigurationSelector`
  - `ProxyTransactionManagementConfiguration`
- [ ] `TransactionInterceptor` 执行流程
  - `invokeWithinTransaction()`
  - `createTransactionIfNecessary()`
  - `completeTransactionAfterThrowing()`
- [ ] `DataSourceTransactionManager` 事务管理
  - `doGetTransaction()`
  - `doBegin()`
  - `doCommit()` / `doRollback()`
- [ ] 事务同步机制
  - `TransactionSynchronizationManager`
  - `TransactionSynchronization`

### 阶段3：实战练习（2-3天）
- [ ] REQUIRED vs REQUIRES_NEW 对比实验
- [ ] NESTED 部分回滚实验
- [ ] 事务失效场景全部复现
- [ ] 编程式事务实战
- [ ] 大事务优化案例

### 阶段4：面试准备（1-2天）
- [ ] 整理事务失效清单（10个场景）
- [ ] 传播行为对比表（7种对比）
- [ ] 隔离级别对比表（4种对比）
- [ ] 源码执行流程图
- [ ] 场景题演练（转账、日志、大事务优化）

---

## 附录：重点速查表

### 传播行为速查表

| 传播行为 | 无当前事务 | 有当前事务 | 使用场景 |
|---------|-----------|-----------|---------|
| REQUIRED | 新建事务 | 加入事务 | 标准业务方法 |
| REQUIRES_NEW | 新建事务 | 挂起当前，新建 | 日志、审计 |
| NESTED | 新建事务 | 创建Savepoint | 部分回滚 |
| SUPPORTS | 无事务 | 加入事务 | 查询方法 |
| NOT_SUPPORTED | 无事务 | 挂起当前 | 发送通知 |
| MANDATORY | 抛异常 | 加入事务 | 强制事务 |
| NEVER | 无事务 | 抛异常 | 非事务工具 |

### 隔离级别速查表

| 隔离级别 | 脏读 | 不可重复读 | 幻读 | MySQL默认 |
|---------|------|-----------|------|----------|
| READ_UNCOMMITTED | ✅ | ✅ | ✅ | ❌ |
| READ_COMMITTED | ❌ | ✅ | ✅ | ❌ |
| REPEATABLE_READ | ❌ | ❌ | ⚠️ | ✅ |
| SERIALIZABLE | ❌ | ❌ | ❌ | ❌ |

### 事务失效速查表

| # | 场景 | 解决方案 |
|---|------|---------|
| 1 | this调用 | 注入自身 / AopContext |
| 2 | 非public | 改为public |
| 3 | 异常被捕获 | 抛出或手动回滚 |
| 4 | checked异常 | 配置rollbackFor |
| 5 | 多线程 | @Async或事务同步 |
| 6 | final/static | 移除修饰符 |
| 7 | 类未被管理 | 添加@Service |
| 8 | 引擎不支持 | 改为InnoDB |
