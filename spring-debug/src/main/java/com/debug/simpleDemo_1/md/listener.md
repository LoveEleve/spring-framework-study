```java
@Component
public class OrderService {
    
    @EventListener
    public void handleOrderCreated(OrderCreatedEvent event) {
        System.out.println("处理订单创建事件: " + event.getOrderId());
    }
    
    @EventListener(condition = "#event.amount > 1000")
    public void handleLargeOrder(OrderCreatedEvent event) {
        System.out.println("处理大额订单: " + event.getOrderId());
    }
}

```


```text
1. afterSingletonsInstantiated() 被调用
        │
        ▼
2. 扫描 OrderService 类
        │
        ▼
3. 发现 2 个 @EventListener 方法
        │
        ▼
4. 为每个方法创建 ApplicationListenerMethodAdapter
        │
   ┌────┴────┐
   ▼         ▼
 方法1     方法2
   │         │
   ▼         ▼
5. 注册到 ApplicationContext
   context.addApplicationListener(adapter1)
   context.addApplicationListener(adapter2)

=====================================

后续发布事件时：

context.publishEvent(new OrderCreatedEvent(orderId, 2000))
        │
        ▼
ApplicationEventMulticaster 广播事件
        │
        ├──► adapter1.onApplicationEvent(event)
        │         │
        │         └──► 反射调用 orderService.handleOrderCreated(event)
        │
        └──► adapter2.onApplicationEvent(event)
                  │
                  ├──► shouldHandle() 检查条件: amount > 1000 ✓
                  └──► 反射调用 orderService.handleLargeOrder(event)

```