```java
@Component
public class KafkaListenerContainer implements SmartLifecycle {
    @Override
    public int getPhase() { return -10; }  // 最先启动
    
    @Override
    public void start() {
        System.out.println("启动 Kafka 消费者...");
    }
}

@Component  
public class WebSocketServer implements SmartLifecycle {
    @Override
    public int getPhase() { return 0; }
    
    @Override
    public void start() {
        System.out.println("启动 WebSocket 服务...");
    }
}

@Component
public class ScheduledTaskRunner implements SmartLifecycle {
    @Override
    public int getPhase() { return 10; }  // 最后启动
    
    @Override
    public void start() {
        System.out.println("启动定时任务...");
    }
}
```

```text
onRefresh() 执行：

1. 获取所有 SmartLifecycle Bean
   └── [KafkaListenerContainer, WebSocketServer, ScheduledTaskRunner]

2. 按 phase 分组
   ├── phase=-10: [KafkaListenerContainer]
   ├── phase=0:   [WebSocketServer]
   └── phase=10:  [ScheduledTaskRunner]

3. 按顺序启动
   ├── "启动 Kafka 消费者..."     (phase=-10)
   ├── "启动 WebSocket 服务..."   (phase=0)
   └── "启动定时任务..."          (phase=10)
```