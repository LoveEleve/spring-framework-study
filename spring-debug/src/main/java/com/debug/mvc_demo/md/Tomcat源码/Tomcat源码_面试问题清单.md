# Tomcat 源码面试问题清单

> **📍 文档导航** | [📚 返回目录](./README.md) | [⬅️ 上一篇：核心流程全景图](./Tomcat源码_核心流程全景图.md) | [➡️ 下一篇：方法论与学习心得](./Tomcat源码_方法论与学习心得.md)
>
> **📊 元信息** | 难度：⭐⭐⭐⭐ | 预估时间：0.5天 | 前置阅读：建议完成全部 8 篇文档
>
> **🎯 学习目标** | 42 道面试高频问题，涵盖初级→中级→高级，答案速查表便于快速复习

---

> 📌 基于 8 篇 Tomcat 源码分析文档
> 📖 共 42 道高频面试问题

---

## 一、NIO 与网络通信（10 题）

### 1.1 基础概念

**Q1: Tomcat 的 NIO 模型是怎样的？有哪几个核心组件？**
> 参考答案：Tomcat 采用 NIO 模型，核心三线程：
> - **Acceptor**：负责accept()接收连接，1个线程
> - **Poller**：负责select()事件轮询，N个线程
> - **Worker**：线程池，负责处理请求，默认200线程
> 
> 文档位置：`Tomcat源码_NIO深度剖析.md`

**Q2: 为什么要区分 Acceptor 和 Poller？用一个线程不行吗？**
> 参考答案：分离设计和性能有关键原因：
> 1. **accept() 是阻塞的**，如果和 select() 在同一线程，accept() 阻塞时无法处理已建立的连接
> 2. **职责单一**：Acceptor 只处理连接建立，Poller 只处理 IO 事件
> 3. **扩展性**：可以独立调整 Acceptor 和 Poller 的数量

**Q3: NioChannel 和 NioSocketWrapper 的区别是什么？**
> - **NioChannel**：对 SocketChannel 的封装，包含读/写两个 ByteBuffer
> - **NioSocketWrapper**：对 NioChannel 的装饰，包含更丰富的功能：
>   - interestOps 管理
>   - 读写锁
>   - 超时追踪
>   - 异步 write 机制
> 
> 文档位置：`Tomcat源码_NIO深度剖析.md` 第五节

---

### 1.2 连接管理

**Q4: LimitLatch 是如何实现连接限流的？**
> 参考答案：基于 JDK AQS 的共享锁实现
> - `acquire()` 获取一个许可，许可数 = maxConnections
> - 超过上限时，线程阻塞在 `L.countDown()` 上
> - 连接关闭时 `countDown()` 释放许可
> 
> 源码位置：`LimitLatch.java`

**Q5: 当并发请求非常多时，线程池被打满了会发生什么？**
> 分三个阶段：
> 1. **线程数 < maxPoolSize**：TaskQueue.offer() 返回 false，强制创建新线程
> 2. **线程数 = maxPoolSize，队列未满**：TaskQueue.offer() 返回 true，任务入队等待
> 3. **线程数 = maxPoolSize，队列也满了**：抛出 RejectedExecutionException，但 Tomcat 有 **force() 回退机制**——在 `execute()` 的 catch 块中调用 `TaskQueue.force()` 强制入队（绕过 offer() 的判断逻辑，直接调用 `super.offer()`），只有 force() 也失败才真正拒绝
> 
> 源码位置：`ThreadPoolExecutor.java:1399-1424`（execute + force 回退）

---

### 1.3 进阶问题

**Q6: SynchronizedStack 对象池是如何实现的？有什么优势？**
> - **数据结构**：基于 `Object[]` 数组 + `index` 头指针的 LIFO 栈
> - **线程安全**：`push()` 和 `pop()` 均使用 `synchronized` 关键字同步（**不是无锁设计**）
> - **优势**：
>   - O(1) 的获取和归还复杂度
>   - LIFO 策略对 GC 友好（热点对象优先复用）
>   - 对象回收时只清空引用、不缩容，避免生成数组垃圾
>   - 设计极简（~100行代码），适合高频短生命周期对象池
> 
> 源码位置：`SynchronizedStack.java`（`push()` 第59行、`pop()` 第74行均有 `synchronized`）

**Q7: Selector 的 select() 操作为什么会阻塞？如何实现超时？**
> - 默认情况下 `select()` 阻塞，直到有 IO 事件就绪
> - Tomcat 使用 `selector.select(timeout)` 带超时参数
> - 超时时间 = `selectTimeout`（默认 1 秒）
> - 超时后会检查关闭超时的连接

---

## 二、HTTP 协议解析（8 题）

### 2.1 解析原理

**Q8: Tomcat 是如何解析 HTTP 请求行的？**
> 使用状态机模式，分 7 个阶段：
> - Phase 0: 跳过空白字符
> - Phase 1: 解析 Method
> - Phase 2: 跳过空白
> - Phase 3: 解析 URI
> - Phase 4: 跳过空白
> - Phase 5: 解析 Protocol
> - Phase 6: 跳过回车换行
> 
> 文档位置：`Tomcat源码_HTTP协议解析深度剖析.md`

**Q9: HTTP 请求头是如何解析的？MimeHeaders 使用了什么数据结构？**
> - 使用 **纯数组**（`MimeHeaderField[]`）结构，**不是哈希表，没有链表**：
>   - 初始大小 `DEFAULT_HEADER_SIZE = 8`，不够时倍增扩容（`count * 2`）
>   - 每个 `MimeHeaderField` 包含两个 `MessageBytes`（name + value）
>   - 查找时**线性遍历**（`findHeader()` 从 index 0 到 count），注释说明："headers 数量很少（4-5），哈希表的额外开销不值得"
> - 解析时逐字节读取，找到冒号后分割 name/value
> 
> 源码位置：`MimeHeaders.java` 第83行（`MimeHeaderField[] headers`）、第197行（`findHeader()` 线性查找）

**Q10: 为什么 Tomcat 不使用正则表达式解析 HTTP？**
> 1. **性能**：正则匹配有额外开销，HTTP 解析是高频操作
> 2. **确定性**：状态机逻辑简单确定，出错易定位
> 3. **安全性**：正则可能有 ReDoS 风险

---

### 2.2 Keep-Alive 与性能

**Q11: HTTP Keep-Alive 是如何实现的？**
> 1. 请求头中检测 `Connection: keep-alive`
> 2. 解析完成后不关闭 Socket
> 3. 清空缓冲区，进入下一轮循环
> 4. 超过 `keepAliveTimeout` 或达到最大请求数后关闭
> 
> 关键代码：`Http11Processor.java` 中的 `keepAlive` 循环

**Q12: Tomcat 如何防止 HTTP 拆包/粘包问题？**
> - HTTP 是文本协议，有明确的边界（空行分隔）
> - `fill()` 方法从 Channel 读取数据到缓冲区
> - 每次解析前检查缓冲区数据是否足够
> - 状态机设计确保完整解析一行/一个头后再处理

---

## 三、内存管理（6 题）

### 3.1 直接内存

**Q13: Tomcat 为什么使用直接内存（DirectByteBuffer）？**
> - **减少 GC**：直接在堆外分配，不受 GC 管理
> - **IO 效率**：与 Socket Channel 交互时无需拷贝
> - **突破堆内存限制**：可使用更大的缓冲区
> - **缺点**：分配/释放成本高 → 使用对象池复用

**Q14: DirectByteBuffer 的内存是如何分配的？**
> 调用 `ByteBuffer.allocateDirect(capacity)`：
> 1. `Bits.reserveMemory()` 尝试预约内存
> 2. `unsafe.allocateMemory()` 调用 native 方法分配
> 3. 创建 `DirectByteBuffer` 对象封装地址
> 4. 内存不归 JVM 管理，需要主动 `cleaner.clean()` 释放
> 
> 文档位置：`Tomcat内存管理深度解析.md`

**Q15: 如果直接内存泄漏了会怎样？如何防护？**
> - **泄漏后果**：堆外内存持续增长，最终 OOM
> - **防护机制**：
>   1. Poller 超时检查：定期检查并关闭超时连接，释放缓冲区
>   2. NioChannel 回收：处理完成后 recycle 归还到对象池
>   3. 心跳检测：检测空闲连接

---

## 四、Mapper 路由匹配（5 题）

**Q16: Tomcat 的 Mapper 是如何实现 URL 路由匹配的？**
> 三级匹配：
> 1. **Host 匹配**：精确匹配 → 通配符匹配 → 默认 Host
> 2. **Context 匹配**：精确匹配 → 版本匹配 → 通配符匹配 → 默认 Context
> 3. **Wrapper 匹配**：精确匹配 → 扩展名匹配 → 路径匹配 → 默认 Servlet
> 
> 文档位置：`Tomcat源码深度专题分析.md` 第一章

**Q17: Mapper 中的二分查找是如何优化的？**
> - Context 和 Wrapper 列表按匹配优先级排序
> - 使用二分查找提高搜索效率
> - 对于前缀匹配场景，优先检查通配符

**Q18: 一个 URL 请求进来，Tomcat 是如何找到对应的 Servlet 的？**
> 1. 根据 Host 找到对应的虚拟主机
> 2. 根据 Context Path 找到对应的 Web 应用
> 3. 根据 Servlet 路径找到对应的 Wrapper（Servlet 包装）
> 4. 获取对应的 StandardWrapper 实例
> 5. 从实例池获取或创建 Servlet 实例

---

## 五、Pipeline/Valve 责任链（5 题）

**Q19: Pipeline 和 Valve 是什么关系？为什么要设计这一层？**
> - **关系**：Pipeline 包含多个 Valve，形成责任链
> - **为什么**：
>   1. **解耦**：每个 Valve 专注特定功能（日志、权限、压缩等）
>   2. **可扩展**：通过添加 Valve 实现新功能，无需修改核心代码
>   3. **复用**：同一 Valve 可被多个容器复用

**Q20: 四层容器的 Valve 是一样的吗？**
> 不一样，但调用方式一致：
> - **Engine Valve**：日志、异常处理、虚拟主机路由
> - **Host Valve**：日志、基于 Host 的处理
> - **Context Valve**：Web 应用级别处理（如 Spring 容器初始化）
> - **Wrapper Valve**：Servlet 级别处理（Session、过滤器链调用）

**Q21: StandardWrapperValve 的核心工作流程是什么？**
> 1. 分配 Servlet 实例
> 2. 创建 ApplicationFilterChain
> 3. 调用 filterChain.doFilter()
> 4. 释放 Servlet 实例
> 
> 文档位置：`Tomcat源码深度专题分析.md` 第二章

---

## 六、FilterChain 与 Servlet（6 题）

**Q22: FilterChain 是如何实现的？什么是洋葱模型？**
> - **实现**：`ApplicationFilterChain` 内部维护 `filters[]` 数组
> - **执行**：
>   1. 每调用一次 `doFilter()`，index++
>   2. 调用下一个 Filter
>   3. Filter 执行完后，递归返回
> - **洋葱模型**：Filter 嵌套调用，形成类似洋葱的层次结构
> 
> 文档位置：`Tomcat源码深度专题分析.md` 第三章

**Q23: Filter 的执行顺序是怎么确定的？**
> 1. 根据 `FilterMap` 的 `urlPattern` 匹配规则排序
>   - 精确匹配 > 路径匹配 > 扩展名匹配 > 默认
>   - 同一优先级按 `filter-mapping` 声明顺序
> 2. 两轮匹配：第一轮按 URL，第二轮按 Servlet 名称

**Q24: 为什么要设计两轮 Filter 匹配？**
> - 第一轮：URL 模式匹配，快速筛选
>   - 减少第二轮匹配数量
> - 第二轮：Servlet 名称精确匹配
>   - 确保配置了 `<servlet-name>` 的 Filter 也能生效

---

## 七、Lifecycle 状态机（5 题）

**Q25: Tomcat 有哪些 Lifecycle 状态？状态转换规则是什么？**
> 12 个状态：
> ```
> NEW → INITIALIZING → INITIALIZED → STARTING_PREP → STARTING → STARTED
>                                        ↓                    ↓
>                              STOPPING_PREP → STOPPING → STOPPED
>                                        ↓                    ↓
>                                        ↓                 DESTROYING → DESTROYED
>                                        ↓
>                                     FAILED
> ```
> 转换规则：
> - 只能按顺序单向转换
> - 可以跳到 FAILED，然后到 DESTROYING
> 
> 文档位置：`Tomcat源码_Lifecycle生命周期状态机深度分析.md`

**Q26: LifecycleBase 用到了什么设计模式？**
> **模板方法模式**：
> - `initInternal()`、`startInternal()` 等方法定义了骨架
> - 子类实现 `initInternal()` 等方法完成具体逻辑
> - 状态转换、事件发布由父类统一处理

**Q27: 事件监听机制是如何实现的？**
> - 实现 `LifecycleListener` 接口
> - 父类维护 `listeners` 列表
> - 状态转换时调用 `fireLifecycleEvent()` 通知所有监听器

---

## 八、类加载器（6 题）

**Q28: 为什么 Tomcat 要打破双亲委派？**
> - **目的**：实现 Web 应用之间的类隔离
> - **问题**：如果用标准双亲委派，同一类名只能加载一次
> - **解决**：WebappClassLoader 优先加载本地类，未找到再委派父类
> 
> 文档位置：`Tomcat源码_类加载器与双亲委派打破深度分析.md`

**Q29: Tomcat 的类加载器层次是怎样的？**
> ```
> BootstrapClassLoader
>     ↓
> ExtensionClassLoader
>     ↓
> SystemClassLoader
>     ↓
> CommonClassLoader ($CATALINA_HOME/lib)
>     ↓
> WebappClassLoader (WEB-INF/classes, WEB-INF/lib)
> ```

**Q30: filter() 方法的作用是什么？**
> - 定义哪些类必须由父类加载，不能本地优先
> - 包括：`java.*`, `javax.*`, `org.xml.sax.*`, `org.w3c.dom.*` 等
> - 避免 Web 应用覆盖 JDK 类导致问题

**Q31: 类加载器泄漏是如何产生的？如何预防？**
> - **产生**：线程池中的线程持有旧 WebappClassLoader 引用
> - **预防**：Tomcat 的 `contextStopping()` 会更新线程，抛出 `StopPooledThreadException`

---

## 九、线程池（6 题）

**Q32: Tomcat 的线程池和 JDK 有什么区别？**
> | 特性 | JDK | Tomcat |
> |------|-----|--------|
> | 线程扩容 | 先入队，队满才扩容 | 优先扩容，最后入队 |
> | maxThreads | 队列无限时无效 | 真正生效 |
> | 线程更新 | 不支持 | 支持热部署时更新 |
> | 任务统计 | 无 | submittedCount |
> 
> 文档位置：`Tomcat源码_线程池实现深度分析.md`

**Q33: TaskQueue.offer() 返回 false 是什么意思？为什么要这样做？**
> - 返回 false 欺骗 ThreadPoolExecutor "队列已满"
> - 使其创建新线程处理任务（直到 maxPoolSize）
> - 解决 JDK 线程池在无界队列下 maxThreads 失效的问题

**Q34: submittedCount 是做什么用的？**
> - 统计已提交但未完成的任务数
> - 包括：队列中等待 + 已分配但未执行 + 正在执行
> - TaskQueue.offer() 用它判断是否有空闲线程

**Q35: 线程更新机制是如何实现的？**
> 1. Context 停止时设置 `lastContextStoppedTime`
> 2. Worker 线程从队列获取任务时检查 `creationTime`
> 3. 如果线程创建时间早于 `lastContextStoppedTime`
> 4. 抛出 `StopPooledThreadException`，线程池创建新线程替代

---

## 十、高级特性（8 题）

### 10.1 异步 Servlet

**Q36: 异步 Servlet 的生命周期有哪些状态？**
> 13 个状态（AsyncStateMachine）：
> - `START_INTERNAL`, `STARTING`, `STARTED`
> - `DISPATCHING`, `DISPATCHED`
> - `COMPLETING`, `COMPLETED`
> - `TIMING_OUT`, `TIMED_OUT`
> - `ERROR`
> 等
> 
> 文档位置：`Tomcat源码深度专题分析.md` 第四章

**Q37: complete() 和 dispatch() 的区别是什么？**
> - `complete()`：标记异步完成，开始写响应
> - `dispatch()`：分发到另一个 Servlet/Filter 继续处理

---

### 10.2 零拷贝

**Q38: sendFile 是如何实现零拷贝的？**
> - 传统 IO：`磁盘 → 内核缓冲区 → 用户缓冲区 → Socket 缓冲区 → 网卡`（4次拷贝）
> - sendFile：`磁盘 → 内核缓冲区 → 网卡`（3次拷贝）
> - 原理：使用 `FileChannel.transferTo()` 直接在内核空间完成
> 
> 文档位置：`Tomcat源码深度专题分析.md` 第五章

**Q39: sendFile 有什么限制条件？**
> 7 个必要条件：
> 1. FileChannel 可用
> 2. 原始缓冲区为堆缓冲区
> 3. 零拷贝启用
> 4. HTTP/1.1 Keep-Alive 关闭
> 5. 响应未压缩
> 6. 响应未提交
> 7. 包含文件内容

---

### 10.3 综合问题

**Q40: 从浏览器输入 URL 到收到响应，Tomcat 经历了哪些核心流程？**
> 完整调用链（每步附源码位置）：
> ```
> Acceptor.run()                            [Acceptor.java:149]
>   └─ endpoint.setSocketOptions(socket)    [Acceptor.java:173]
>     └─ poller.register(socketWrapper)     [NioEndpoint.java:483]
>
> Poller.run()                              [NioEndpoint.java:819]
>   └─ selector.select()                   [NioEndpoint.java:841]
>   └─ processKey() → processSocket()      [NioEndpoint.java:942]
>     └─ executor.execute(socketProcessor) [AbstractEndpoint.java:1314]
>
> SocketProcessor.doRun()                   [NioEndpoint.java:1854]
>   └─ handler.process(wrapper, event)     → ConnectionHandler
>
> ConnectionHandler.process()               [AbstractProtocol.java:849]
>   └─ processor.process(wrapper, status)  → AbstractProcessorLight
>     └─ service(socketWrapper)            [AbstractProcessorLight.java:63]
>
> Http11Processor.service()                 [Http11Processor.java:473]
>   └─ parseRequestLine() + parseHeaders() → HTTP 解析
>   └─ adapter.service(request, response)  [Http11Processor.java:643]
>
> CoyoteAdapter.service()                   [CoyoteAdapter.java:313]
>   └─ postParseRequest()                  → Mapper.map() 路由匹配
>   └─ connector.getService().getContainer()
>        .getPipeline().getFirst().invoke() [CoyoteAdapter.java:362]
>
> StandardEngineValve → StandardHostValve → StandardContextValve →
>
> StandardWrapperValve.invoke()             [StandardWrapperValve.java:87]
>   └─ wrapper.allocate()                  → 获取 Servlet 实例
>   └─ filterChain.doFilter(req, res)      [StandardWrapperValve.java:168]
>     └─ Servlet.service()                 → 你的业务代码
> ```

**Q41: Tomcat 的性能调优有哪些参数？**
> - **Connector 参数**：
>   - `maxThreads`：最大工作线程数
>   - `minSpareThreads`：最小空闲线程
>   - `acceptorThreadCount`：Acceptor 线程数
>   - `pollerThreadCount`：Poller 线程数
>   - `maxConnections`：最大连接数（LimitLatch）
> - **JVM 参数**：
>   - `-XX:MaxDirectMemorySize`：直接内存大小
>   - `-Xss`：线程栈大小

**Q42: Tomcat 和 Jetty/Undertow 有什么区别？**
> | 特性 | Tomcat | Jetty | Undertow |
> |------|--------|-------|----------|
> | 架构 | 同步为主 | 异步 | 异步 |
> | 线程模型 | 3线程 | 3线程 | XNIO |
> | 类加载 | 打破双亲 | 打破双亲 | 打破双亲 |
> | 轻量级 | 中 | 轻 | 最轻 |

---

## 答案速查表

| 题号 | 答案位置 | 关键点 |
|------|---------|--------|
| Q1-Q7 | NIO深度剖析 | 三线程模型、对象池、LimitLatch |
| Q8-Q12 | HTTP协议解析 | 状态机、MimeHeaders、Keep-Alive |
| Q13-Q15 | 内存管理 | DirectByteBuffer、对象池、泄漏防护 |
| Q16-Q18 | 深度专题-Mapper | 三级匹配、二分查找 |
| Q19-Q21 | 深度专题-Pipeline | 责任链、四层容器 |
| Q22-Q24 | 深度专题-Filter | 洋葱模型、两轮匹配 |
| Q25-Q27 | Lifecycle | 12状态、模板方法 |
| Q28-Q31 | 类加载器 | 打破双亲、filter机制 |
| Q32-Q35 | 线程池 | TaskQueue、线程更新 |
| Q36-Q42 | 深度专题 | 异步Servlet、零拷贝、综合 |
