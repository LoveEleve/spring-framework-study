
# Step 4：任务重叠与并发问题 —— 深度源码分析

> 面试高频 + 生产踩坑的实战知识

---

## 一、问题的本质

在理解重叠问题之前，我们需要先建立一个核心认知：

> **"同一个 `@Scheduled` 方法，在不同轮次之间，是否可能同时有多个实例在并发执行？"**

答案取决于 **三个因素**：
1. 调度模式（fixedRate / fixedDelay / cron）
2. 线程池大小（poolSize = 1 还是 > 1）
3. 任务执行时间是否超过了调度间隔

---

## 二、三种模式的根本性差异

### 2.1 底层实现路径对比

| 调度模式 | 底层实现 | 循环驱动者 |
|----------|---------|-----------|
| **cron** | `ReschedulingRunnable` → `executor.schedule(this, delay)` | **Spring 自己驱动**循环 |
| **fixedRate** | `executor.scheduleAtFixedRate(task, 0, period, ms)` | **JDK 驱动**循环 |
| **fixedDelay** | `executor.scheduleWithFixedDelay(task, 0, delay, ms)` | **JDK 驱动**循环 |

**这个差异直接决定了重叠行为的不同！**

### 2.2 Spring 源码入口对比

三种模式在 `ThreadPoolTaskScheduler` 中的入口完全不同：

#### fixedRate 入口
```java
// ThreadPoolTaskScheduler.scheduleAtFixedRate()
public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, long period) {
    ScheduledExecutorService executor = getScheduledExecutor();
    try {
        // ★ 直接委托给 JDK 的 scheduleAtFixedRate
        // ★ errorHandlingTask(task, true) 包装了异常处理
        return executor.scheduleAtFixedRate(errorHandlingTask(task, true), 0, period, TimeUnit.MILLISECONDS);
    }
    catch (RejectedExecutionException ex) {
        throw new TaskRejectedException("Executor [" + executor + "] did not accept task: " + task, ex);
    }
}
```

#### fixedDelay 入口
```java
// ThreadPoolTaskScheduler.scheduleWithFixedDelay()
public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, long delay) {
    ScheduledExecutorService executor = getScheduledExecutor();
    try {
        // ★ 直接委托给 JDK 的 scheduleWithFixedDelay
        return executor.scheduleWithFixedDelay(errorHandlingTask(task, true), 0, delay, TimeUnit.MILLISECONDS);
    }
    catch (RejectedExecutionException ex) {
        throw new TaskRejectedException("Executor [" + executor + "] did not accept task: " + task, ex);
    }
}
```

#### cron 入口
```java
// ThreadPoolTaskScheduler.schedule(Runnable, Trigger)
public ScheduledFuture<?> schedule(Runnable task, Trigger trigger) {
    ScheduledExecutorService executor = getScheduledExecutor();
    try {
        ErrorHandler errorHandler = this.errorHandler;
        if (errorHandler == null) {
            errorHandler = TaskUtils.getDefaultErrorHandler(true);
        }
        // ★ 不是直接委托 JDK！而是创建 ReschedulingRunnable，由 Spring 自己驱动循环
        return new ReschedulingRunnable(task, trigger, this.clock, executor, errorHandler).schedule();
    }
    catch (RejectedExecutionException ex) {
        throw new TaskRejectedException("Executor [" + executor + "] did not accept task: " + task, ex);
    }
}
```

#### 关键区别

```
fixedRate / fixedDelay:
    Spring 只做了一件事 → errorHandlingTask() 包装异常处理
    然后直接交给 JDK 管理后续的所有循环调度

cron:
    Spring 创建了 ReschedulingRunnable
    每次执行完毕后，由 Spring 自己计算下次时间并重新提交
    JDK 只负责执行单次延迟任务（executor.schedule(this, delay)）
```

### 2.3 errorHandlingTask 的作用

```java
// ThreadPoolTaskScheduler
private Runnable errorHandlingTask(Runnable task, boolean isRepeatingTask) {
    return TaskUtils.decorateTaskWithErrorHandler(task, this.errorHandler, isRepeatingTask);
}
```

```java
// TaskUtils.decorateTaskWithErrorHandler()
public static DelegatingErrorHandlingRunnable decorateTaskWithErrorHandler(
        Runnable task, @Nullable ErrorHandler errorHandler, boolean isRepeatingTask) {
    if (task instanceof DelegatingErrorHandlingRunnable) {
        return (DelegatingErrorHandlingRunnable) task;     // 避免重复包装
    }
    ErrorHandler eh = (errorHandler != null ? errorHandler : getDefaultErrorHandler(isRepeatingTask));
    return new DelegatingErrorHandlingRunnable(task, eh);  // 包装异常处理
}
```

这个包装的意义在下文异常与重叠的关系中会详细说明。

---

## 三、Cron 任务 —— 天然不可能重叠 ⭐

### 3.1 核心源码

```java
// ReschedulingRunnable.run()
@Override
public void run() {
    Date actualExecutionTime = new Date(this.triggerContext.getClock().millis());
    super.run();           // ← 第 1 步：执行业务方法（同步阻塞，必须等它执行完！）
    Date completionTime = new Date(this.triggerContext.getClock().millis());
    synchronized (this.triggerContextMonitor) {
        Assert.state(this.scheduledExecutionTime != null, "No scheduled execution");
        // 第 2 步：更新 TriggerContext（记录本次调度时间、实际执行时间、完成时间）
        this.triggerContext.update(this.scheduledExecutionTime, actualExecutionTime, completionTime);
        if (!obtainCurrentFuture().isCancelled()) {
            schedule();    // ← 第 3 步：执行完了，才调度下一轮
        }
    }
}
```

```java
// ReschedulingRunnable.schedule()
@Nullable
public ScheduledFuture<?> schedule() {
    synchronized (this.triggerContextMonitor) {
        // ★ 通过 Trigger 计算下次执行时间
        this.scheduledExecutionTime = this.trigger.nextExecutionTime(this.triggerContext);
        if (this.scheduledExecutionTime == null) {
            return null;
        }
        long delay = this.scheduledExecutionTime.getTime() - this.triggerContext.getClock().millis();
        // ★ 提交给 JDK 的是一次性延迟任务，不是周期任务！
        this.currentFuture = this.executor.schedule(this, delay, TimeUnit.MILLISECONDS);
        return this;
    }
}
```

### 3.2 CronTrigger.nextExecutionTime() —— 为什么会"跳过"

```java
// CronTrigger.nextExecutionTime()
public Date nextExecutionTime(TriggerContext triggerContext) {
    // ★ 关键：使用 lastCompletionTime（上次完成时间）作为基准！
    Date timestamp = triggerContext.lastCompletionTime();
    if (timestamp != null) {
        Date scheduled = triggerContext.lastScheduledExecutionTime();
        if (scheduled != null && timestamp.before(scheduled)) {
            timestamp = scheduled;  // 防止在同一秒内重复触发
        }
    }
    else {
        timestamp = new Date(triggerContext.getClock().millis());
    }
    ZoneId zone = (this.zoneId != null ? this.zoneId : triggerContext.getClock().getZone());
    ZonedDateTime zonedTimestamp = ZonedDateTime.ofInstant(timestamp.toInstant(), zone);
    // ★ 基于完成时间计算 cron 表达式的下一个匹配时间
    ZonedDateTime nextTimestamp = this.expression.next(zonedTimestamp);
    return (nextTimestamp != null ? Date.from(nextTimestamp.toInstant()) : null);
}
```

**核心逻辑**：
- 使用 `lastCompletionTime`（完成时间）而不是 `lastScheduledExecutionTime`（计划时间）
- 如果任务执行了 5 秒才完成，那么 `next()` 会基于完成时间来找下一个 cron 匹配点
- 中间那些已经错过的 cron 时间点就被自然跳过了

### 3.3 时间线分析（cron = "每2秒执行一次"，任务执行需5秒）

```
时间轴:  0s     2s     4s     5s     6s     7s    10s    11s    12s
         │      │      │      │      │      │      │      │      │
         ├──────────────────────┤                           
         │  第1轮任务执行(5s)   │                           
         │                     │                           
         │                     ├─ run()完成                 
         │                     ├─ triggerContext.update(completionTime=5s)
         │                     ├─ schedule()                
         │                     │  → nextExecutionTime(lastCompletionTime=5s)
         │                     │  → cron.next(5s) = 6s      
         │                     │  → executor.schedule(this, 1s)
         │                                  │               
         │                                  ├──────────────────────┤
         │                                  │  第2轮任务执行(5s)   │
         │                                  │                      │
         │                                  │                      ├─ completionTime=11s
         │                                  │                      ├─ cron.next(11s) = 12s
         │                                                         │
         │                                                         ├───...
         
Cron触发点:  ↑0s  ↑2s  ↑4s       ↑6s       ↑8s     ↑10s      ↑12s
             执行  跳过  跳过      执行       跳过    跳过       执行
```

### 3.4 为什么不可能重叠？

```
关键链路：
  run()                              
    → super.run()         // 同步阻塞！在当前线程执行业务代码
    → schedule()          // 业务代码执行完毕后才调用
        → executor.schedule(this, delay)  // 提交下一轮（一次性延迟任务）

因为 schedule() 在 super.run() 之后才调用，
所以在当前轮次执行期间，根本没有"下一轮"被提交到线程池。
即使 poolSize = 100，也不可能有另一个线程来执行同一个 cron 任务。
```

> 🔑 **核心结论**：
> - Cron 任务**无论在单线程还是多线程池下，都不可能重叠执行**
> - 因为 `schedule()` 是在 `super.run()` 之后才调用的
> - 如果任务执行时间 > cron 间隔，**中间的触发点会被跳过**（不是排队，是直接跳过！）
> - 这是由 `CronTrigger.nextExecutionTime()` 内部用 `lastCompletionTime` 计算下次时间决定的

---

## 四、FixedDelay 任务 —— 天然不可能重叠 ⭐

### 4.1 JDK 层面的语义

`fixedDelay` 走的是 JDK 的 `scheduleWithFixedDelay()`。JDK 的语义是：

> **上一次执行完成后，等待 delay 毫秒，再执行下一次。**

### 4.2 JDK 内部原理

JDK 的 `ScheduledFutureTask.run()` 关键逻辑（伪代码）：

```java
// JDK ScheduledFutureTask.run() 核心逻辑
void run() {
    if (isPeriodic()) {
        boolean ran = super.runAndReset();  // ← 执行任务（同步阻塞）
        if (ran) {
            // fixedDelay: period < 0，下次时间 = now() + |period|
            setNextRunTime();               // ← 执行完了才计算下次时间
            reExecutePeriodic(outerTask);   // ← 重新放入延迟队列
        }
    }
}

// setNextRunTime() 中对 fixedDelay 的处理
void setNextRunTime() {
    long p = period;
    if (p > 0) {
        // fixedRate: 下次时间 = 本次计划时间 + period
        time += p;
    } else {
        // fixedDelay: 下次时间 = 当前时间 + |period|（基于完成时间！）
        time = triggerTime(-p);  // triggerTime = now() + delay
    }
}
```

### 4.3 时间线分析（fixedDelay = 1000ms，任务执行需3000ms）

```
时间轴:  0s      1s      2s      3s      4s      5s      6s      7s
         │       │       │       │       │       │       │       │
         ├───────────────────────┤                               
         │  第1轮执行(3s)        │                               
         │                      ├──等1s──┤                       
         │                               ├───────────────────────┤
         │                               │  第2轮执行(3s)        │
         │                                                       ├──等1s──→...
         
实际间隔:  ←────── 4秒 ──────→  ←────── 4秒 ──────→
           (3秒执行 + 1秒等待)    (3秒执行 + 1秒等待)
```

### 4.4 为什么不可能重叠？

```
JDK 内部保证：
  ScheduledFutureTask.run()
    → runAndReset()            // 同步阻塞执行
    → 返回 true
    → setNextRunTime()         // 基于 now() + delay 计算
    → reExecutePeriodic()      // 把自己重新放回队列

在 runAndReset() 执行期间：
  - 这个 ScheduledFutureTask 对象不在延迟队列中
  - 没有线程能从队列中取到它
  - 所以不可能被另一个线程执行

执行完毕后：
  - 计算 nextTime = now() + delay
  - 重新放回队列，等待 delay 毫秒后再被取出
```

> 🔑 **关键结论**：
> - fixedDelay **天然不可能重叠**，无论单线程还是多线程
> - 因为 JDK 在 `runAndReset()` **完成后**才计算下次执行时间
> - 实际间隔 = 任务执行时间 + delay
> - **这和 Cron 的机制本质相同**——都是"先完成，再调度"

---

## 五、FixedRate 任务 —— 重叠问题的核心战场 ⭐⭐⭐⭐⭐

`fixedRate` 走的是 JDK 的 `scheduleAtFixedRate()`。JDK 的语义是：

> **每隔固定时间就应该执行一次，不管上一次有没有执行完。**

### 5.1 JDK 内部的核心区别

```java
// ScheduledFutureTask.setNextRunTime() 中的关键区别
void setNextRunTime() {
    long p = period;
    if (p > 0) {
        // ★ fixedRate: 下次时间 = 本次【计划时间】 + period
        time += p;
    } else {
        // fixedDelay: 下次时间 = 当前时间 + |period|
        time = triggerTime(-p);
    }
}
```

**核心差异**：
- `fixedRate`：`nextTime = scheduledTime + period`（基于**计划时间**，不是完成时间！）
- `fixedDelay`：`nextTime = now() + delay`（基于**完成时间**）

这意味着即使任务还没执行完，下次的计划时间已经"过期"了！

### 5.2 JDK ScheduledFutureTask.run() 的完整逻辑

```java
// JDK ScheduledFutureTask.run() 伪代码
public void run() {
    boolean periodic = isPeriodic();
    // 1. 检查是否应该取消
    if (!canRunInCurrentRunState(periodic))
        cancel(false);
    // 2. 如果不是周期任务，直接执行一次
    else if (!periodic)
        super.run();
    // 3. 如果是周期任务（fixedRate 和 fixedDelay 都走这里）
    else if (super.runAndReset()) {     // ← 执行任务并重置状态（同步阻塞！）
        setNextRunTime();               // ← 计算下次执行时间
        reExecutePeriodic(outerTask);   // ← 把自己重新放回延迟队列
    }
}
```

**关键点**：
1. `runAndReset()` 是同步阻塞的——在当前线程执行完业务代码
2. 执行完后才调用 `setNextRunTime()` 和 `reExecutePeriodic()`
3. `reExecutePeriodic()` 是把**同一个 ScheduledFutureTask 对象**重新放回队列
4. 一个对象在执行期间不在队列中 → 不可能被另一个线程取到

### 5.3 单线程池下的 fixedRate（poolSize = 1）—— "追赶执行"现象

这是 **Spring 默认场景**（`poolSize = 1`）。

当任务执行时间（3s）> 调度间隔（1s）时：

```
时间轴:   0s      1s      2s      3s      4s      5s      6s      7s      8s      9s
          │       │       │       │       │       │       │       │       │       │
          ├───────────────────────┤                                               
          │  第1轮执行(3s)        │                                               
          │                      │                                               
          │   计划触发点:         ↑1s     ↑2s                                     
          │   (已过期,排队中)     │       │                                       
          │                      ├───────────────────────┤                       
          │                      │  第2轮立即执行(3s)     │  ← 不等待！立即追赶！
          │                                              ├───────────────────────┤
          │                                              │  第3轮立即执行(3s)     │
```

**详细过程**：

```
时间 0s:
  第1轮开始执行
  setNextRunTime() 会在第1轮完成后被调用

时间 3s（第1轮执行完毕）:
  runAndReset() 返回 true
  setNextRunTime(): nextTime = 0s + 1s = 1s（已经过期了！）
  reExecutePeriodic(): 把 task 放回延迟队列
  
  因为 nextTime = 1s < now = 3s，delay = 1s - 3s = -2s（负数）
  DelayedWorkQueue 会立即将其排在队列头部
  
  → 第2轮立即开始（不等待！）

时间 6s（第2轮执行完毕）:
  setNextRunTime(): nextTime = 1s + 1s = 2s（还是过期的！）
  又立即开始第3轮

时间 9s（第3轮执行完毕）:
  setNextRunTime(): nextTime = 2s + 1s = 3s（还是过期的！）
  又立即开始第4轮

... 以此类推，延迟不断积累，但每轮都不会被跳过
```

> 🔑 **关键行为 —— "追赶执行"（Catch-up）**：
> - 虽然只有 1 个线程，**不会并发执行同一个任务**
> - 但 JDK 会"记住"所有错过的触发点，**一个接一个排队执行**
> - 任务之间**没有间隔**——上一轮结束后立即开始下一轮
> - 延迟会**不断积累**，但不会跳过任何一轮

**为什么不会并发？** 因为只有 1 个线程，JDK 的 `ScheduledThreadPoolExecutor` 内部使用的是 `DelayedWorkQueue`（一个基于堆的优先队列），过期的任务会在队列头部等待执行，但只有 1 个工作线程来消费。

### 5.4 多线程池下的 fixedRate（poolSize > 1）—— ⚠️ 同一个任务依然不会并发！

**这是一个极其常见的误解！** 很多人以为 `poolSize > 1` 时 fixedRate 会让同一个任务并发执行。**实际上不会！**

```
场景: poolSize = 5, fixedRate = 1000ms, 任务执行时间 = 3000ms

时间轴:   0s      1s      2s      3s      4s      5s      6s
          │       │       │       │       │       │       │
Thread-1: ├───────────────────────┤
          │  第1轮执行(3s)        │
          │                      ├───────────────────────┤
          │                      │  第2轮追赶执行(3s)     │
          │                                              ├──...

Thread-2: (空闲)
Thread-3: (空闲)
Thread-4: (空闲)
Thread-5: (空闲)
```

**为什么即使 5 个线程也不并发？**

```java
// JDK ScheduledFutureTask.run() 伪代码
void run() {
    if (isPeriodic()) {
        // 1. 先执行任务
        boolean ran = super.runAndReset();  // ← 在当前线程上同步执行
        if (ran) {
            // 2. 执行完了，计算下次时间
            setNextRunTime();
            // 3. 把自己（同一个 FutureTask 对象）重新放回队列
            reExecutePeriodic(outerTask);   // ← 是同一个 Task 对象！
        }
    }
}
```

**根本原因**：

```
reExecutePeriodic(outerTask) 把【同一个 ScheduledFutureTask 对象】重新放回延迟队列。

在 runAndReset() 执行期间（即业务代码运行时）：
  ① 这个 Task 对象已经从队列中取出了
  ② 它正在被一个线程执行
  ③ 还没有调用 reExecutePeriodic() 重新放回队列
  ④ 所以其他线程从队列中根本取不到这个 Task

只有等 runAndReset() 完成 → setNextRunTime() → reExecutePeriodic() 之后，
这个 Task 才会重新出现在队列中，才可能被某个线程取到并执行。

结论：JDK 保证了对于同一个 scheduleAtFixedRate 调用返回的任务，
      同一时刻最多只有一个线程在执行它。
```

### 5.5 那 poolSize > 1 到底有什么用？

答案是：**用于并行执行不同的 `@Scheduled` 方法**。

```java
@Scheduled(fixedRate = 1000)
public void taskA() { Thread.sleep(3000); }  // 慢任务 A

@Scheduled(fixedRate = 1000)
public void taskB() { Thread.sleep(3000); }  // 慢任务 B

@Scheduled(fixedRate = 500)
public void taskC() { /* 快任务 C */ }
```

**poolSize = 1（默认）的行为**：

```
Thread-1: ├──A(3s)──├──B(3s)──├──C──├──A(3s)──├──B(3s)──├──C──...
                                               
所有任务串行！A 阻塞 3 秒 → B 被迫等待 → C 也被迫等待
任务之间互相影响！
```

**poolSize = 3 的行为**：

```
Thread-1: ├──A(3s)──├──A(3s)──├──A(3s)──├──...
Thread-2: ├──B(3s)──├──B(3s)──├──B(3s)──├──...
Thread-3: ├─C─├─C─├─C─├─C─├─C─├─C─├─...

不同任务可以并行！A 不会影响 B 和 C
```

> 🎯 **核心结论**：
> - `poolSize > 1` 解决的是**不同任务之间的互相阻塞**
> - **不是**解决同一个任务的并发执行
> - 同一个 `scheduleAtFixedRate` 任务，**永远不会并发执行**（JDK 保证）

---

## 六、为什么 Spring 默认 poolSize = 1 反而"安全"？

从 `ThreadPoolTaskScheduler` 源码可以看到：

```java
private volatile int poolSize = 1;  // 默认值
```

Spring 选择默认 `poolSize = 1`，原因有三个：

### 6.1 安全性 —— 所有任务串行，无并发问题

```
poolSize = 1 时:
┌──────────────────────────────────────────────┐
│ Thread-1: ─A─B─C─A─B─C─A─B─C─               │
│                                              │
│ ✅ 任务 A、B、C 之间绝对不会并发              │
│ ✅ 如果 A 和 B 操作同一个共享资源，不需要锁   │
│ ✅ 不需要考虑线程安全问题                     │
└──────────────────────────────────────────────┘

poolSize = 3 时:
┌──────────────────────────────────────────────┐
│ Thread-1: ─A─────A─────A─────                │
│ Thread-2: ─B─────B─────B─────                │
│ Thread-3: ─C─C─C─C─C─C─C────                │
│                                              │
│ ⚠️ A 和 B 可能同时执行！                      │
│ ⚠️ 如果它们操作同一个共享资源，需要加锁        │
│ ⚠️ 需要考虑线程安全问题                       │
└──────────────────────────────────────────────┘
```

### 6.2 简单性 —— 开箱即用

大多数简单应用的定时任务不多，单线程足够。避免用户在不了解并发的情况下遇到线程安全问题。

### 6.3 一致性 —— 与兜底方案保持一致

回忆 `ScheduledTaskRegistrar.scheduleTasks()` 中的兜底代码：

```java
if (this.taskScheduler == null) {
    this.localExecutor = Executors.newSingleThreadScheduledExecutor();
    this.taskScheduler = new ConcurrentTaskScheduler(this.localExecutor);
}
```

兜底方案也是单线程，保持行为一致。

---

## 七、"追赶执行" vs "跳过执行" vs "拉长间隔" —— 三种模式的终极对比

### 场景设定：任务执行需要 5 秒，间隔设为 2 秒

### 7.1 fixedRate —— 追赶执行

```
期望触发点:     0s   2s   4s   6s   8s   10s  12s  14s
                ↓    ↓    ↓    ↓    ↓     ↓    ↓    ↓
实际执行:       ├─────────┤├─────────┤├─────────┤├─────────┤
                │ 第1轮5s ││ 第2轮5s ││ 第3轮5s ││ 第4轮5s │
                0s       5s        10s       15s       20s

- 第1轮: 0s开始, 5s结束 (计划0s)
- 第2轮: 5s开始, 10s结束 (计划2s, 延迟3s, 立即追赶)  
- 第3轮: 10s开始, 15s结束 (计划4s, 延迟6s, 立即追赶)
- 第4轮: 15s开始, 20s结束 (计划6s, 延迟9s, 立即追赶)

结论: 不跳过！每轮都会执行！延迟不断积累！
      任务之间没有间隔，紧密连续执行！
```

### 7.2 cron —— 跳过执行

```
Cron触发点:     0s   2s   4s   6s   8s   10s  12s  14s
                ↓    ↓    ↓    ↓    ↓     ↓    ↓    ↓
实际执行:       ├─────────┤              ├─────────┤
                │ 第1轮5s │              │ 第2轮5s │
                0s       5s    ↑6s      6s        11s   ↑12s
                               schedule()                schedule()
                               计算下次=6s                计算下次=12s

- 第1轮: 0s开始, 5s结束
- 5s时schedule(): 下次应该什么时候? → CronTrigger基于5s计算 → 6s
- 第2轮: 6s开始, 11s结束
- 11s时schedule(): 下次应该什么时候? → CronTrigger基于11s计算 → 12s
- 第3轮: 12s开始...

结论: 2s和4s的触发点被跳过了！
      实际执行频率比预期低，但不会积压
```

### 7.3 fixedDelay —— 拉长间隔

```
期望触发点:     0s   2s   4s   6s   8s   10s  12s  14s
                ↓    ↓    ↓    ↓    ↓     ↓    ↓    ↓
实际执行:       ├─────────┤   ├─────────┤   ├─────────┤
                │ 第1轮5s │   │ 第2轮5s │   │ 第3轮5s │
                0s       5s  7s        12s 14s       19s
                          ├2s┤          ├2s┤
                          等待           等待

- 第1轮: 0s开始, 5s结束
- 等待2s(fixedDelay)
- 第2轮: 7s开始, 12s结束
- 等待2s
- 第3轮: 14s开始, 19s结束

结论: 每轮之间都有完整的2s等待
      实际间隔 = 5s(执行) + 2s(等待) = 7s
```

### 7.4 三种模式对比总结表

```
┌──────────────┬──────────────────┬──────────────────┬──────────────────┐
│              │    fixedRate     │    fixedDelay    │      cron        │
├──────────────┼──────────────────┼──────────────────┼──────────────────┤
│ 底层实现     │ JDK              │ JDK              │ Spring           │
│              │ scheduleAtFixed  │ scheduleWithFixed│ Rescheduling     │
│              │ Rate             │ Delay            │ Runnable         │
├──────────────┼──────────────────┼──────────────────┼──────────────────┤
│ 循环驱动者   │ JDK 内部         │ JDK 内部         │ Spring 自己      │
├──────────────┼──────────────────┼──────────────────┼──────────────────┤
│ 下次时间     │ scheduledTime    │ completionTime   │ completionTime   │
│ 计算基准     │ + period         │ + delay          │ → Trigger计算    │
├──────────────┼──────────────────┼──────────────────┼──────────────────┤
│ 同一任务     │ ❌ 不会并发       │ ❌ 不会并发       │ ❌ 不会并发      │
│ 是否并发     │ (JDK保证)        │ (天然不可能)      │ (Spring保证)     │
├──────────────┼──────────────────┼──────────────────┼──────────────────┤
│ 任务耗时     │ "追赶执行"       │ 实际间隔拉长      │ 跳过中间触发点   │
│ > 间隔时     │ 不跳过、不等待   │ = 执行时间+delay │ 基于完成时间     │
│              │ 紧密连续执行     │                  │ 计算下次时间     │
├──────────────┼──────────────────┼──────────────────┼──────────────────┤
│ 单线程池     │ 追赶执行 +       │ 正常             │ 正常             │
│ (poolSize=1) │ 可能阻塞其他任务 │ (天然串行)        │ (天然串行)       │
├──────────────┼──────────────────┼──────────────────┼──────────────────┤
│ 多线程池     │ 同一任务仍不并发 │ 同一任务仍不并发  │ 同一任务仍不并发 │
│ (poolSize>1) │ 不同任务可并行   │ 不同任务可并行    │ 不同任务可并行   │
├──────────────┼──────────────────┼──────────────────┼──────────────────┤
│ 异常后       │ 继续调度         │ 继续调度          │ 继续调度         │
│ 是否继续     │ (ErrorHandler)   │ (ErrorHandler)    │ (ErrorHandler)   │
├──────────────┼──────────────────┼──────────────────┼──────────────────┤
│ 生产风险     │ ⚠️ 追赶执行可能  │ ✅ 安全           │ ✅ 安全          │
│              │ 导致CPU持续高负载│                   │                  │
└──────────────┴──────────────────┴──────────────────┴──────────────────┘
```

---

## 八、异常处理与重叠的关系

### 8.1 Spring 的 DelegatingErrorHandlingRunnable

```java
// DelegatingErrorHandlingRunnable.run()
@Override
public void run() {
    try {
        this.delegate.run();       // ← 执行业务代码
    }
    catch (UndeclaredThrowableException ex) {
        this.errorHandler.handleError(ex.getUndeclaredThrowable());
    }
    catch (Throwable ex) {
        this.errorHandler.handleError(ex);  // ← 捕获异常，交给 ErrorHandler
    }
    // ← 注意！方法正常返回了！没有异常向上传播！
}
```

### 8.2 为什么异常处理和重叠有关？

如果没有 `DelegatingErrorHandlingRunnable` 的保护，业务代码抛异常后：

```
JDK 原生行为（没有 Spring 包装）:
  ScheduledFutureTask.run()
    → runAndReset()
        → callable.call()         // 业务代码抛异常！
        → catch(Throwable) { }    // JDK 捕获异常
        → ran = false             // 没有正常完成
    ← runAndReset() 返回 false
  → if (false) {                  // 不进入 if 块！
        setNextRunTime();          // ❌ 不执行
        reExecutePeriodic();       // ❌ 不执行
    }
  → 任务永远消失！不再调度！

有了 Spring 包装:
  ScheduledFutureTask.run()
    → runAndReset()
        → DelegatingErrorHandlingRunnable.run()
            → delegate.run()       // 业务代码抛异常！
            → catch → errorHandler.handleError()  // Spring 捕获并处理
            → 方法正常返回（没有异常传播到 JDK 层）
        ← callable.call() 正常返回
        ← ran = true              // ✅ 正常完成
    ← runAndReset() 返回 true
  → if (true) {                   // ✅ 进入 if 块
        setNextRunTime();          // ✅ 计算下次时间
        reExecutePeriodic();       // ✅ 重新放回队列
    }
  → 任务继续调度！
```

> 🔑 Spring 的异常处理包装不仅仅是"打日志"，更重要的是**保护任务的持续调度不被异常中断**！

---

## 九、防止任务重叠的方案

虽然同一个 `@Scheduled` 方法不会并发，但在分布式环境下（多实例部署），同一个任务会在多个 JVM 上同时执行。以下是常见的防重叠方案：

### 9.1 方案对比

| 方案 | 适用场景 | 复杂度 | 说明 |
|------|---------|--------|------|
| 使用 `fixedDelay` | 单实例 | ⭐ | 天然不重叠 |
| 自定义状态标记 | 单实例 | ⭐⭐ | `AtomicBoolean` 控制 |
| 数据库锁 | 多实例 | ⭐⭐⭐ | `UPDATE ... WHERE status='IDLE'` |
| Redis 分布式锁 | 多实例 | ⭐⭐⭐ | `SETNX + 过期时间` |
| ShedLock | 多实例 | ⭐⭐ | 开源库，注解驱动 |
| XXL-Job / ElasticJob | 多实例 | ⭐⭐⭐⭐ | 分布式任务调度平台 |

### 9.2 单实例防重叠示例

```java
// 方案 1: 优先使用 fixedDelay（推荐）
@Scheduled(fixedDelay = 5000)
public void safeTask() {
    // 天然不重叠，不追赶
}

// 方案 2: 自定义状态标记
private final AtomicBoolean running = new AtomicBoolean(false);

@Scheduled(fixedRate = 1000)
public void guardedTask() {
    if (!running.compareAndSet(false, true)) {
        return;  // 上一轮还没执行完，跳过本轮
    }
    try {
        // 业务逻辑
    } finally {
        running.set(false);
    }
}
```

---

## 十、面试常见陷阱问题

### Q1: "fixedRate 在多线程池下会并发执行同一个任务吗？"

**A: 不会。** JDK 的 `ScheduledThreadPoolExecutor` 通过"同一个 `ScheduledFutureTask` 对象在执行期间不在队列中"的机制，保证了同一个周期任务不会被多个线程同时执行。多线程池的作用是**让不同的 `@Scheduled` 方法可以并行**。

### Q2: "为什么 Spring 默认 poolSize = 1？"

**A:** 三个原因：①安全——所有任务串行，无并发问题；②简单——开箱即用；③一致——与兜底方案（`Executors.newSingleThreadScheduledExecutor()`）行为一致。

### Q3: "fixedRate 任务执行时间超过间隔会怎样？"

**A:** 单线程池下会"追赶执行"——上一轮结束后立即开始下一轮，不等待，不跳过，延迟不断积累。**这可能导致 CPU 持续高负载**。

### Q4: "cron 和 fixedDelay 在任务超时场景下有什么不同？"

**A:** cron 会**跳过**中间错过的触发点（基于 `lastCompletionTime` 计算下次时间）；fixedDelay 则是**拉长间隔**（完成后固定等 delay 毫秒）。两者都不会积压。

### Q5: "怎么防止 fixedRate 的追赶执行？"

**A:** 三种方案：①换用 `fixedDelay`；②换用 `cron`；③自定义逻辑在任务内部检查是否应该跳过（如 `AtomicBoolean`）。

### Q6: "同一个 @Scheduled 任务在任何场景下都不会并发吗？"

**A:** 在**单个 JVM 内**，是的——无论什么模式、无论多少线程，都不会并发。但在**分布式多实例部署**下，同一个任务会在多个 JVM 上同时执行，需要使用分布式锁等方案来防止。

---

## 十一、生产环境最佳实践

```java
@Configuration
@EnableScheduling
public class ScheduleConfig implements SchedulingConfigurer {

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        // ✅ 最佳实践 1: 自定义线程池，poolSize 根据任务数量设置
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(Runtime.getRuntime().availableProcessors());
        scheduler.setThreadNamePrefix("scheduled-");
        
        // ✅ 最佳实践 2: 设置 removeOnCancelPolicy
        //    任务取消时立即从队列移除，避免内存泄漏
        scheduler.setRemoveOnCancelPolicy(true);
        
        // ✅ 最佳实践 3: 自定义 ErrorHandler
        scheduler.setErrorHandler(t -> {
            log.error("定时任务异常", t);
            // 可以发告警通知
        });
        
        scheduler.initialize();
        taskRegistrar.setTaskScheduler(scheduler);
    }
}

// ✅ 最佳实践 4: 大多数场景优先使用 fixedDelay 而非 fixedRate
@Scheduled(fixedDelay = 5000)
public void safeTask() {
    // 天然不重叠，不追赶
}

// ✅ 最佳实践 5: 如果必须用 fixedRate，确保任务执行时间 < 间隔时间
@Scheduled(fixedRate = 10000)
public void fastTask() {
    // 确保执行时间 < 10秒
}
```

---

## 十二、源码路径汇总

```
Spring 源码:
├── ThreadPoolTaskScheduler.java
│   ├── scheduleAtFixedRate()      → 委托 JDK，传入 errorHandlingTask
│   ├── scheduleWithFixedDelay()   → 委托 JDK，传入 errorHandlingTask
│   ├── schedule(Runnable, Trigger)→ 创建 ReschedulingRunnable
│   └── errorHandlingTask()        → TaskUtils.decorateTaskWithErrorHandler()
│
├── ReschedulingRunnable.java
│   ├── run()                      → super.run() + schedule()
│   └── schedule()                 → trigger.nextExecutionTime() + executor.schedule()
│
├── DelegatingErrorHandlingRunnable.java
│   └── run()                      → try { delegate.run() } catch { errorHandler.handleError() }
│
├── TaskUtils.java
│   ├── decorateTaskWithErrorHandler()
│   ├── LoggingErrorHandler        → 只打日志，吞掉异常
│   └── PropagatingErrorHandler    → 打日志 + 重新抛出
│
├── CronTrigger.java
│   └── nextExecutionTime()        → 基于 lastCompletionTime 计算
│
└── SimpleTriggerContext.java
    └── update()                   → 记录 scheduledTime / actualTime / completionTime

JDK 源码:
├── ScheduledThreadPoolExecutor
│   └── ScheduledFutureTask.run()
│       ├── runAndReset()          → 同步执行任务
│       ├── setNextRunTime()       → fixedRate vs fixedDelay 的核心差异
│       └── reExecutePeriodic()    → 同一个对象重新入队
└── DelayedWorkQueue               → 基于堆的优先队列
```

---

## 十三、实验验证建议

```java
// 实验 1：fixedRate 追赶执行
@Scheduled(fixedRate = 1000)
public void fixedRateSlowTask() throws InterruptedException {
    System.out.println("[fixedRate] 开始: " + LocalTime.now() + " 线程: " + Thread.currentThread().getName());
    Thread.sleep(3000);  // 执行 3 秒
    System.out.println("[fixedRate] 结束: " + LocalTime.now());
}
// 观察: 每次结束后立即开始下一次，没有间隔

// 实验 2：fixedDelay 拉长间隔
@Scheduled(fixedDelay = 1000)
public void fixedDelaySlowTask() throws InterruptedException {
    System.out.println("[fixedDelay] 开始: " + LocalTime.now() + " 线程: " + Thread.currentThread().getName());
    Thread.sleep(3000);
    System.out.println("[fixedDelay] 结束: " + LocalTime.now());
}
// 观察: 每次结束后等 1 秒才开始下一次

// 实验 3：cron 跳过执行
@Scheduled(cron = "0/2 * * * * ?")  // 每 2 秒
public void cronSlowTask() throws InterruptedException {
    System.out.println("[cron] 开始: " + LocalTime.now() + " 线程: " + Thread.currentThread().getName());
    Thread.sleep(5000);  // 执行 5 秒
    System.out.println("[cron] 结束: " + LocalTime.now());
}
// 观察: 中间的触发点被跳过，基于完成时间找下一个 cron 匹配点

// 实验 4：多线程池下 fixedRate 是否并发
// 配置: poolSize = 5
@Scheduled(fixedRate = 1000)
public void multiThreadTest() throws InterruptedException {
    System.out.println("[多线程] 开始: " + LocalTime.now() + " 线程: " + Thread.currentThread().getName());
    Thread.sleep(3000);
    System.out.println("[多线程] 结束: " + LocalTime.now());
}
// 观察: 即使 5 个线程，同一个任务仍然不会并发（但线程名可能不同）
```

---

> 🎯 **一句话总结**：在 Spring 调度框架下，同一个 `@Scheduled` 任务在单个 JVM 内永远不会并发执行——fixedRate 会"追赶"、cron 会"跳过"、fixedDelay 会"拉长"——三种模式的差异在于如何处理"任务执行超时"的情况。
