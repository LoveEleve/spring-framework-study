# Tomcat 源码核心流程全景图

> **📍 文档导航** | [📚 返回目录](./README.md) | [⬅️ 上一篇：线程池实现](./Tomcat源码_线程池实现深度分析.md) | [➡️ 下一篇：面试问题清单](./Tomcat源码_面试问题清单.md)
>
> **📊 元信息** | 难度：⭐⭐⭐⭐ | 预估时间：0.5天 | 前置阅读：建议完成 [①②③⑤⑧](./README.md#21--完整学习路径推荐约-7-12-天)
>
> **🎯 学习目标** | 一张图串联 8 篇文档的核心内容，形成完整的知识网络

---

> 📁 **本地源码路径**：`/data/workspace/tomcat/`
>
> 📖 8 篇文档 + 9,500+ 行源码分析总结

---

## 一、请求处理完整链路

### 1.1 宏观架构图

> 📍 源码入口：`CoyoteAdapter.service()` → `/data/workspace/tomcat/java/org/apache/catalina/connector/CoyoteAdapter.java:313`

```mermaid
flowchart TB
    subgraph Client["客户端"]
        HTTP["HTTP 请求"]
    end

    subgraph Server["Tomcat Server"]
        subgraph ProtocolHandler["协议层（Coyote）"]
            NIO["NioEndpoint<br/>连接管理"]
            HP["Http11Processor<br/>协议解析"]
        end

        subgraph Container["容器层（Catalina）"]
            Adapter["CoyoteAdapter<br/>请求转换"]
            Mapper["Mapper<br/>路由匹配"]
            Engine["Engine<br/>引擎"]
            Host["Host<br/>虚拟主机"]
            Context["Context<br/>Web应用"]
            Wrapper["Wrapper<br/>Servlet"]
        end

        subgraph Lifecycle["生命周期层"]
            LB["LifecycleBase<br/>状态机"]
        end

        subgraph ClassLoader["类加载层"]
            WL["WebappClassLoader<br/>打破双亲委派"]
        end
    end

    HTTP -->|"TCP Socket"| NIO
    NIO -->|"SocketProcessor"| HP
    HP -->|"Request/Response"| Adapter
    Adapter -->|"映射 Context/Wrapper"| Mapper
    Mapper -->|"调用 Valve 链"| Engine
    Engine --> Host --> Context --> Wrapper
    Wrapper -->|"创建 FilterChain"| Servlet["Servlet<br/>业务处理"]

    LB -.->|"管理各组件启动/停止"| NIO & HP & Engine & Context
    WL -.->|"加载 Webapp 类"| Context
```

### 1.2 线程模型：Acceptor → Poller → Worker

> 📍 源码入口：
> - Acceptor: `Acceptor.run()` → `/data/workspace/tomcat/java/org/apache/tomcat/util/net/Acceptor.java:69`
> - Poller: `NioEndpoint.Poller.run()` → `/data/workspace/tomcat/java/org/apache/tomcat/util/net/NioEndpoint.java:819`

```mermaid
flowchart LR
    subgraph Acceptor["Acceptor 线程（1个）"]
        A1["阻塞 accept()"]
        A2["获取 SocketChannel"]
        A3["注册到 Poller"]
    end

    subgraph Poller["Poller 线程（1-N个）"]
        P1["select() 事件就绪"]
        P2["处理 OP_READ/OP_WRITE"]
        P3["包装为 NioSocketWrapper"]
        P4["提交到 Executor"]
    end

    subgraph Worker["Worker 线程池（N=200）"]
        W1["从 TaskQueue 获取任务"]
        W2["执行 SocketProcessor"]
        W3["Http11Processor 解析"]
        W4["返回响应"]
    end

    A1 --> A2 --> A3
    A3 -->|"事件就绪"| P1 --> P2 --> P3 --> W1
    W1 --> W2 --> W3 --> W4
```

---

## 二、NIO 处理流程

### 2.1 NioEndpoint 启动流程

> 📍 源码入口：`NioEndpoint.startInternal()` → `/data/workspace/tomcat/java/org/apache/tomcat/util/net/NioEndpoint.java:263`

```mermaid
flowchart TB
    start["startInternal()"] --> bind["bind() - 绑定端口"]
    bind --> limit["initLimitLatch() - 初始化连接限流"]
    limit --> acceptor["startAcceptorThread() - 启动 Acceptor"]
    acceptor --> poller["startPollerThread() - 启动 Poller"]
    poller --> executor["startExecutorThread() - 启动 Worker 线程池"]
```

### 2.2 Acceptor 处理流程

```mermaid
flowchart TD
    A["循环：accept() 阻塞等待"] --> B{"超过连接上限？"}
    B -->|是| A
    B -->|否| C["Socket socket = serverSock.accept()"]
    C --> D["获取 NioChannel"]
    D --> E["注册到 Poller（OP_READ）"]
    E --> A
```

### 2.3 Poller 处理流程

```mermaid
flowchart TD
    P["while(running)"] --> S["selector.select()"]
    S --> K["遍历 selectionKey"]
    K --> PK{"key.isReadable()?"}
    PK -->|是| R["NioSocketWrapper.configure"]
    R --> Q["创建 SocketProcessor"]
    Q --> Ex["executor.execute()"]
    Ex --> NK["nextKey()"]
    NK --> K
    PK -->|否| W{"key.isWritable()?"}
    W -->|是| WR["处理写事件"]
    WR --> NK
    W -->|否| NK
```

---

## 三、HTTP 协议解析流程

### 3.1 解析状态机

> 📍 源码入口：
> - `Http11InputBuffer.parseRequestLine()` → `/data/workspace/tomcat/java/org/apache/coyote/http11/Http11InputBuffer.java:345`
> - `Http11InputBuffer.parseHeaders()` → `/data/workspace/tomcat/java/org/apache/coyote/http11/Http11InputBuffer.java:598`

```mermaid
stateDiagram-v2
    [*] --> NEW
    NEW --> REQUEST_LINE: parseRequestLine()
    REQUEST_LINE --> HEADERS: parseHeader()
    HEADERS --> BODY: readBodyData()
    BODY --> READY: 解析完成
    READY --> NEW: 下一个请求
```

### 3.2 请求行解析 7 阶段

```
┌─────────────────────────────────────────────────────────────┐
│ Phase 0: 跳过空白字符                                       │
│ Phase 1: 解析 Method（GET/POST 等）                        │
│ Phase 2: 跳过空白字符                                       │
│ Phase 3: 解析 URI                                          │
│ Phase 4: 跳过空白字符                                       │
│ Phase 5: 解析 Protocol（HTTP/1.1）                         │
│ Phase 6: 跳过回车换行                                       │
│ Phase 7: 状态机结束，ready = true                          │
└─────────────────────────────────────────────────────────────┘
```

---

## 四、内存管理流程

### 4.1 四大对象池

> 📍 源码入口：
> - `NioEndpoint.nioChannels` → `/data/workspace/tomcat/java/org/apache/tomcat/util/net/NioEndpoint.java:96`
> - `NioEndpoint.eventCache` → `/data/workspace/tomcat/java/org/apache/tomcat/util/net/NioEndpoint.java:91`
> - `AbstractEndpoint.processorCache` → `/data/workspace/tomcat/java/org/apache/tomcat/util/net/AbstractEndpoint.java:208`
> - `AbstractProtocol.recycledProcessors` → `/data/workspace/tomcat/java/org/apache/coyote/AbstractProtocol.java:823`
>
> ⚠️ **NioSocketWrapper 没有对象池**，每次新连接都 `new`（NioEndpoint.java:464）

```mermaid
flowchart TB
    subgraph NioChannel池["NioChannel 池（nioChannels）"]
        NC["SynchronizedStack&lt;NioChannel&gt;<br/>初始128，上限500（bufferPool）"]
    end

    subgraph PollerEvent池["PollerEvent 池（eventCache）"]
        PE["SynchronizedStack&lt;PollerEvent&gt;<br/>初始128，上限500（eventCache）"]
    end

    subgraph SocketProcessor池["SocketProcessor 池（processorCache）"]
        SP["SynchronizedStack&lt;SocketProcessorBase&gt;<br/>初始128，上限500（processorCache）"]
    end

    subgraph Http11Processor池["Http11Processor 池（recycledProcessors）"]
        HP["RecycledProcessors extends SynchronizedStack&lt;Processor&gt;<br/>初始128，上限200（AbstractProtocol.processorCache）"]
    end

    NC -.->|"连接建立时 pop，关闭时 push"| PE
    PE -.->|"事件处理后 push 回池"| SP
    SP -.->|"SocketProcessor.doRun() 完成后 push"| HP
```

### 4.2 直接内存分配流程

```
用户请求 → NioChannel.read() 
         → ByteBuffer buf = socketBuffer.handler.buf
         → fill(buf) 从 Channel 读取数据到 DirectByteBuffer
         → 解析完成后 recycle 归还到池
```

---

## 五、Mapper 路由匹配流程

### 5.1 三级匹配

> 📍 源码入口：`Mapper.internalMap()` → `/data/workspace/tomcat/java/org/apache/catalina/mapper/Mapper.java:698`

```mermaid
flowchart TD
    R["Request URL"] --> H{"Host 匹配？"}
    H -->|exact| HM["精确匹配"]
    H -->|wildcard| HW["通配符匹配"]
    H -->|default| HD["默认 Host"]

    HM --> C{"Context 匹配？"}
    HW --> C
    HD --> C

    C -->|exact| CM["精确匹配 /app1"]
    C -->|wildcard| CW["通配符 /app1/*"]
    C -->|version| CV["版本号 /app1##version"]

    CM --> W{"Wrapper 匹配？"}
    CW --> W
    CV --> W

    W -->|exact| WM["精确 Servlet"]
    W -->|extension| WE["扩展名 *.do"]
    W -->|path| WP["路径 /user/*"]
    W -->|default| WD["默认 Servlet"]

    WD --> S["Servlet 实例"]
    WM --> S
    WE --> S
    WP --> S
```

---

## 六、Pipeline/Valve 责任链流程

### 6.1 四层 Valve

> 📍 源码入口：`StandardWrapperValve.invoke()` → `/data/workspace/tomcat/java/org/apache/catalina/core/StandardWrapperValve.java:87`

```mermaid
sequenceDiagram
    participant C as CoyoteAdapter
    participant E as Engine Valve
    participant H as Host Valve
    participant CT as Context Valve
    participant W as Wrapper Valve

    C->>E: invoke()
    E->>E: 前置处理
    E->>H: next.invoke()
    H->>H: 前置处理
    H->>CT: next.invoke()
    CT->>CT: 前置处理
    CT->>W: next.invoke()
    W->>W: 前置处理
    W->>W: 创建 FilterChain
    W->>W: 执行业务逻辑
    W-->>CT: 返回
    CT-->>H: 返回
    H-->>E: 返回
    E-->>C: 返回
```

---

## 七、FilterChain 执行流程

### 7.1 洋葱模型

> 📍 源码入口：`ApplicationFilterChain.internalDoFilter()` → `/data/workspace/tomcat/java/org/apache/catalina/core/ApplicationFilterChain.java:160`

```
┌────────────────────────────────────────────────────────────┐
│                     FilterChain 执行顺序                    │
├────────────────────────────────────────────────────────────┤
│                                                            │
│   第 1 轮：正向执行（从 Filter1 到 FilterN）               │
│   ┌─────┐    ┌─────┐    ┌─────┐    ┌─────┐               │
│   │ F1  │ -> │ F2  │ -> │ F3  │ -> │Servlet│              │
│   └─────┘    └─────┘    └─────┘    └─────┘               │
│                                                            │
│   第 2 轮：反向执行（从 FilterN 到 Filter1）               │
│   ┌─────┐    ┌─────┐    ┌─────┐    ┌─────┐               │
│   │Servlet│ <-│ F3  │ <-│ F2  │ <-│ F1  │               │
│   └─────┘    └─────┘    └─────┘    └─────┘               │
│                                                            │
└────────────────────────────────────────────────────────────┘
```

---

## 八、Lifecycle 状态机

### 8.1 12 状态转换

> 📍 源码入口：
> - `LifecycleBase.init()` → `/data/workspace/tomcat/java/org/apache/catalina/util/LifecycleBase.java:120`
> - `LifecycleBase.start()` → `/data/workspace/tomcat/java/org/apache/catalina/util/LifecycleBase.java:145`

```mermaid
stateDiagram-v2
    [*] --> NEW: new LifecycleBase()
    NEW --> INITIALIZING: init()
    INITIALIZING --> INITIALIZED: AFTER_INIT_EVENT
    INITIALIZED --> STARTING_PREP: start()
    STARTING_PREP --> STARTING: BEFORE_START_EVENT
    STARTING --> STARTED: START_EVENT
    STARTED --> STOPPING_PREP: stop()
    STOPPING_PREP --> STOPPING: BEFORE_STOP_EVENT
    STOPPING --> STOPPED: STOP_EVENT
    STOPPED --> DESTROYING: destroy()
    DESTROYING --> DESTROYED: AFTER_DESTROY_EVENT
    
    INITIALIZING --> FAILED: 初始化异常
    STARTING --> FAILED: 启动异常
    STOPPING --> FAILED: 停止异常
    FAILED --> DESTROYING: destroy()
```

---

## 九、类加载器层次

### 9.1 双亲委派 vs Tomcat 打破

> 📍 源码入口：`WebappClassLoaderBase.loadClass()` → `/data/workspace/tomcat/java/org/apache/catalina/loader/WebappClassLoaderBase.java:1174`

```mermaid
flowchart TB
    subgraph JDK["JDK 类加载器（双亲委派）"]
        Bootstrap["Bootstrap CL<br/>%JAVA_HOME%/jre/lib"]
        Ext["Extension CL<br/>%JAVA_HOME%/jre/lib/ext"]
        App["System CL<br/>-classpath"]
    end

    subgraph Tomcat["Tomcat 类加载器（打破委派）"]
        Common["Common CL<br/>$CATALINA_HOME/lib"]
        Webapp["Webapp CL<br/>WEB-INF/classes"]
        WebappLib["Webapp CL<br/>WEB-INF/lib"]
    end

    Bootstrap -->|"委派"| Ext
    Ext -->|"委派"| App
    
    Common -->|"优先本地"| Webapp
    Webapp -->|"未找到"| Common
    WebappLib -->|"优先本地"| Webapp
```

---

## 十、线程池执行流程

### 10.1 TaskQueue offer() 决策

> 📍 源码入口：`TaskQueue.offer()` → `/data/workspace/tomcat/java/org/apache/tomcat/util/threads/TaskQueue.java:98`

```mermaid
flowchart TD
    T["TaskQueue.offer()"] --> P{"parent == null?"}
    P -->|是| R1["return super.offer()"]
    P -->|否| P2{"poolSize == maxPoolSize?"}
    
    P2 -->|是| R2["return super.offer() 入队"]
    P2 -->|否| P3{"submittedCount <= poolSize?"}
    
    P3 -->|是| R3["return super.offer() 有空闲线程，入队"]
    P3 -->|否| P4{"poolSize < maxPoolSize?"}
    
    P4 -->|是| R4["return false 强制创建新线程"]
    P4 -->|否| R5["return super.offer() 入队"]
```

### 10.2 线程更新机制（热部署）

```mermaid
flowchart TD
    CTX["Context 停止"] --> LCS["设置 lastContextStoppedTime"]
    LCS --> WT["Worker 线程调用 take()"]
    WT --> CT{"creationTime < lastContextStoppedTime?"}
    CT -->|是| EX["抛出 StopPooledThreadException"]
    EX --> NT["ThreadPoolExecutor 创建新线程"]
    CT -->|否| CC["继续执行任务"]
```

---

## 十一、面试必问流程串联

### 完整请求处理链路

```
1. TCP 连接建立
   └─> Acceptor.accept() → NioChannel

2. Poller 事件就绪
   └─> selector.select() → 注册 OP_READ → executor.execute()

3. HTTP 协议解析
   └─> Http11InputBuffer.parseRequestLine() → 状态机 7 阶段
   └─> Http11InputBuffer.parseHeader() → MimeHeaders

4. 路由匹配
   └─> Mapper.map() → Host → Context → Wrapper

5. 责任链调用
   └─> Engine Valve → Host Valve → Context Valve → Wrapper Valve

6. Filter 处理
   └─> ApplicationFilterChain.doFilter() → 洋葱模型

7. Servlet 执行
   └─> service() → doGet/doPost

8. 响应返回
   └─> Http11OutputBuffer.flush() → ByteBuffer → SocketChannel
```

---

## 十二、设计模式汇总

| 设计模式 | 应用场景 | 文档位置 |
|---------|---------|---------|
| 状态机模式 | HTTP 解析（7阶段）、Lifecycle（12状态）、异步Servlet（13状态） | ③⑥ |
| 责任链模式 | Pipeline/Valve、FilterChain | ⑤ |
| 模板方法模式 | LifecycleBase 定义启动/停止流程 | ⑥ |
| 对象池模式 | SynchronizedStack（NioChannel/PollerEvent/SocketProcessor/Http11Processor） | ②④⑧ |
| 装饰器模式 | NioSocketWrapper 包装 Socket | ② |
| 工厂模式 | TaskThreadFactory 创建线程 | ⑧ |
| 策略模式 | 不同协议处理器（HTTP/AJP/WebSocket） | ③ |
