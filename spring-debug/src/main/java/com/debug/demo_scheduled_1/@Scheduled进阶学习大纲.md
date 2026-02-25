# @Scheduled 进阶学习大纲

> 基于 Spring 源码的深度学习路线

---

## 一、已掌握的基础

### 1.1 入口原理
- [x] `@EnableScheduling` → `@Import(SchedulingConfiguration.class)` → 注册 `ScheduledAnnotationBeanPostProcessor`

### 1.2 BPP 生命周期
- [x] 10 个接口及其回调时机
- [x] Aware 注入阶段、扫描阶段、单例完成阶段、事件阶段、销毁阶段

### 1.3 数据结构体系
- [x] Task 体系：`Task` → `TriggerTask` → `CronTask`
- [x] Task 体系：`Task` → `IntervalTask` → `FixedRateTask` / `FixedDelayTask`
- [x] `ScheduledTask`：运行时包装，持有 `ScheduledFuture`
- [x] `ScheduledMethodRunnable`：Runnable 适配器，反射调用目标方法
- [x] `Trigger` 体系：`Trigger` 接口 → `CronTrigger` → `CronExpression`

### 1.4 核心设计
- [x] 两阶段调度：阶段 1 收集，阶段 4 真正提交
- [x] 双写存储：4 个任务容器（遍历用） + `unresolvedTasks`（复用引用）
- [x] 引用传递：BPP 和 Registrar 持有同一个 `ScheduledTask`，保证 `cancel()` 生效
- [x] 4 级调度器查找：显式设置 → SchedulingConfigurer → 容器查找 → 兜底单线程池

---

## 二、待深入学习的内容

### 2.1 三种调度模式的底层差异 ⭐⭐⭐⭐⭐

#### 核心问题
- `fixedRate` 和 `fixedDelay` 在 JDK `ScheduledExecutorService` 层面有什么区别？
- 如果任务执行时间 > 间隔时间，会发生什么？
- cron 任务为什么天然不会重叠执行？

#### 学习要点
```
1. JDK ScheduledExecutorService 源码
   - scheduleAtFixedRate() 的实现原理
   - scheduleWithFixedDelay() 的实现原理
   - 两者的核心区别：计算下次执行时间的时机不同

2. fixedRate 特性
   - 固定频率，不管任务执行多久
   - 任务执行时间 > 间隔时间 → 任务重叠/并发？
   - 单线程池下会发生什么？

3. fixedDelay 特性
   - 上次完成后等 interval 再执行
   - 任务执行时间不影响下次调度时间
   - 天然避免重叠

4. cron 特性
   - 基于 Trigger.nextExecutionTime() 计算
   - 使用 lastCompletionTime 作为基准
   - 为什么天然不会重叠？

5. 源码追踪
   - ConcurrentTaskScheduler.scheduleAtFixedRate()
   - ConcurrentTaskScheduler.scheduleWithFixedDelay()
   - ConcurrentTaskScheduler.schedule(Runnable, Trigger)
   - JDK ScheduledThreadPoolExecutor 内部实现
```

#### 实验验证
```java
// 实验 1：fixedRate 任务执行时间 > 间隔
@Scheduled(fixedRate = 1000)
public void slowTask() throws InterruptedException {
    System.out.println("开始: " + LocalTime.now());
    Thread.sleep(3000);  // 执行 3 秒
    System.out.println("结束: " + LocalTime.now());
}
// 观察输出：是并发？排队？跳过？

// 实验 2：fixedDelay 任务执行时间 > 间隔
@Scheduled(fixedDelay = 1000)
public void slowTask() throws InterruptedException {
    Thread.sleep(3000);
}
// 观察输出：与 fixedRate 的区别？

// 实验 3：cron 任务执行时间 > 间隔
@Scheduled(cron = "0/2 * * * * ?")
public void slowTask() throws InterruptedException {
    Thread.sleep(5000);
}
// 观察输出：下次执行时间如何计算？
```

---

### 2.2 异常处理机制 ⭐⭐⭐⭐⭐

#### 核心问题
- `@Scheduled` 方法抛异常后，任务还会继续执行吗？
- 异常被谁捕获？怎么处理的？
- 如何自定义异常处理？

#### 学习要点
```
1. 异常传播路径
   - ScheduledMethodRunnable.run() 中的 try-catch
   - ReflectionUtils.rethrowRuntimeException() 的作用
   - 异常最终抛给谁？

2. JDK 层面的处理
   - ScheduledThreadPoolExecutor.afterExecute()
   - 任务抛异常后，ScheduledFuture 的状态
   - 后续调度是否继续？

3. Spring 的 ErrorHandler 机制
   - TaskScheduler 的 setErrorHandler()
   - 自定义 ErrorHandler

4. 三种调度方式的异常表现差异
   - fixedRate：抛异常后还会继续调度吗？
   - fixedDelay：抛异常后还会继续调度吗？
   - cron：抛异常后还会继续调度吗？
```

#### 源码追踪
```
ScheduledMethodRunnable.run()
    → ReflectionUtils.rethrowRuntimeException(ex.getTargetException())

ThreadPoolTaskScheduler.scheduleAtFixedRate()
    → DelegatingErrorHandlingCallable
    → ErrorHandler.handleError(t)
```

#### 实验验证
```java
// 实验 1：fixedRate 任务抛异常
@Scheduled(fixedRate = 1000)
public void throwException() {
    System.out.println("执行: " + LocalTime.now());
    throw new RuntimeException("故意抛异常");
}
// 观察输出：还会继续执行吗？

// 实验 2：自定义 ErrorHandler
@Bean
public ThreadPoolTaskScheduler taskScheduler() {
    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.setErrorHandler(t -> {
        System.err.println("捕获到异常: " + t.getMessage());
    });
    return scheduler;
}
```

---

### 2.3 任务重叠与并发问题 ⭐⭐⭐⭐

#### 核心问题
- fixedRate 任务执行时间 > 间隔，会并发执行吗？
- 单线程池 vs 多线程池的行为差异？
- 如何防止任务重叠？

#### 学习要点
```
1. 单线程池下的行为
   - 任务排队执行
   - 不存在真正的并发
   - 但会"追赶"执行

2. 多线程池下的行为
   - 任务可能并发执行
   - 需要考虑线程安全

3. 防重叠方案
   - 使用 fixedDelay 代替 fixedRate
   - 使用分布式锁
   - 使用 @SchedulerLock（ShedLock）
   - 自定义状态标记
```

---

### 2.4 分布式调度问题 ⭐⭐⭐⭐⭐

#### 核心问题
- 多实例部署时，定时任务会重复执行吗？
- 有哪些解决方案？

#### 学习要点
```
1. 问题场景
   - 实例 A、B 同时部署
   - cron 表达式触发时，两个实例都执行

2. 解决方案对比
   ┌─────────────────┬──────────────────────────────────┐
   │ 方案            │ 说明                              │
   ├─────────────────┼──────────────────────────────────┤
   │ 单实例部署      │ 简单但不可靠                      │
   │ 数据库锁        │ UPDATE ... WHERE status='IDLE'   │
   │ Redis 分布式锁  │ SETNX + 过期时间                  │
   │ ShedLock        │ 开源库，基于数据库/Redis/Zookeeper│
   │ XXL-Job         │ 分布式任务调度平台                │
   │ ElasticJob      │ 分布式任务调度平台                │
   └─────────────────┴──────────────────────────────────┘

3. ShedLock 使用
   - @SchedulerLock 注解
   - 锁提供者配置（JdbcLockProvider / RedisLockProvider）
   - 锁超时时间配置
```

---

### 2.5 @Scheduled + @Transactional ⭐⭐⭐⭐

#### 核心问题
- `@Scheduled` 方法上加 `@Transactional`，事务生效吗？
- 事务在哪个线程开启？
- 如果方法调用其他带 `@Transactional` 的方法？

#### 学习要点
```
1. 事务生效条件
   - 必须通过代理调用
   - @Scheduled 方法是直接反射调用
   - 是否走代理？

2. 源码分析
   - ScheduledMethodRunnable.run()
   - method.invoke(target)
   - 是否经过 AOP 代理？

3. 正确使用方式
   - 方式 1：在 @Scheduled 方法内部调用其他带 @Transactional 的方法
   - 方式 2：使用 TransactionTemplate 编程式事务
   - 方式 3：自注入（self-injection）

4. 事务传播问题
   - @Scheduled 方法每次执行是独立事务？
   - 还是在调度器线程的事务中？
```

---

### 2.6 优雅停机 ⭐⭐⭐

#### 学习要点
```
1. ThreadPoolTaskScheduler 的停机配置
   - setWaitForTasksToCompleteOnShutdown(true)
   - setAwaitTerminationSeconds(60)

2. 源码分析
   - ExecutorConfigurationSupport.shutdown()
   - 与 @Async 的优雅停机对比

3. 注意事项
   - 任务被中断时的处理
   - 长时间运行任务的应对
```

---

### 2.7 动态调度 ⭐⭐⭐

#### 学习要点
```
1. 编程式注册任务
   - SchedulingConfigurer 接口
   - ScheduledTaskRegistrar.addCronTask()
   - ScheduledTaskRegistrar.addTriggerTask()

2. 运行时动态控制
   - 获取 ScheduledTask 对象
   - 调用 scheduledTask.cancel()
   - 动态修改 cron 表达式

3. 实现动态调度
   - 基于数据库存储 cron 表达式
   - 使用 RefreshTrigger 动态计算下次执行时间
```

---

### 2.8 监控与运维 ⭐⭐⭐

#### 学习要点
```
1. 任务状态监控
   - ThreadPoolTaskScheduler.getActiveCount()
   - ThreadPoolTaskScheduler.getPoolSize()
   - 自定义 TaskListener

2. 任务执行日志
   - AOP 拦截 @Scheduled 方法
   - 记录执行开始、结束、耗时、异常

3. 集成 Micrometer + Prometheus
   - 任务执行次数
   - 任务执行耗时
   - 任务失败次数
```

---

## 三、学习路线建议

### 按面试频率 + 生产重要性排序

```
Phase 1: 必须掌握（面试高频 + 生产必踩坑）
├── 2.2 异常处理机制
├── 2.1 三种调度模式底层差异
└── 2.4 分布式调度问题

Phase 2: 重要但非紧急
├── 2.3 任务重叠与并发问题
├── 2.5 @Scheduled + @Transactional
└── 2.6 优雅停机

Phase 3: 进阶内容
├── 2.7 动态调度
└── 2.8 监控与运维
```

---

## 四、参考资料

### 源码路径
```
spring-context/
├── scheduling/
│   ├── annotation/
│   │   ├── EnableScheduling.java
│   │   ├── SchedulingConfiguration.java
│   │   ├── ScheduledAnnotationBeanPostProcessor.java
│   │   └── SchedulingConfigurer.java
│   ├── config/
│   │   ├── Task.java
│   │   ├── TriggerTask.java
│   │   ├── CronTask.java
│   │   ├── IntervalTask.java
│   │   ├── FixedRateTask.java
│   │   ├── FixedDelayTask.java
│   │   ├── ScheduledTask.java
│   │   └── ScheduledTaskRegistrar.java
│   ├── support/
│   │   ├── ScheduledMethodRunnable.java
│   │   ├── CronTrigger.java
│   │   └── CronExpression.java
│   ├── concurrent/
│   │   ├── ThreadPoolTaskScheduler.java
│   │   └── ConcurrentTaskScheduler.java
│   ├── TaskScheduler.java
│   └── Trigger.java
```

### JDK 源码
```
java.util.concurrent/
├── ScheduledExecutorService.java
├── ScheduledThreadPoolExecutor.java
└── ScheduledFuture.java
```

---

## 五、实验代码位置

```
spring-debug/src/main/java/com/debug/demo_scheduled_1/
├── ScheduledConfig.java          # 配置类
├── MyScheduledTask.java          # 定时任务示例
├── ScheduledTest.java            # 启动类
└── @Scheduled进阶学习大纲.md      # 本文档
```

---

> 学习建议：每个主题都先写实验代码验证，再追踪源码确认，最后总结成笔记。
