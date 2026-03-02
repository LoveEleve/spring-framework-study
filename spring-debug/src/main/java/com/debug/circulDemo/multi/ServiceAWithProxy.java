package com.debug.circulDemo.multi;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ServiceAWithProxy - 需要被AOP代理的Bean
 * 场景：A → B, A → C, B → A, C → A
 * 
 * 关键点：@Transactional会让A被代理
 * 
 * 如果没有三级缓存：
 * 1. B注入A时，拿到原始对象
 * 2. C注入A时，拿到原始对象  
 * 3. A初始化后被代理
 * 结果：B和C持有原始对象，但容器中是代理对象 ❌
 * 
 * 使用三级缓存：
 * 1. B注入A时，调用ObjectFactory.getObject()
 * 2. 触发getEarlyBeanReference()，立即创建代理
 * 3. 代理放入二级缓存
 * 4. C注入A时，从二级缓存获取代理
 * 结果：B和C持有的是同一个代理对象 ✅
 * 
 * @author debug
 */
@Service
public class ServiceAWithProxy {

    @Autowired
    private ServiceBWithProxy serviceB;

    @Autowired
    private ServiceCWithProxy serviceC;

    /**
     * @Transactional 会触发AOP代理创建
     */
    @Transactional
    public void doSomething() {
        System.out.println("ServiceAWithProxy.doSomething() - @Transactional方法");
    }

    public ServiceBWithProxy getServiceB() {
        return serviceB;
    }

    public ServiceCWithProxy getServiceC() {
        return serviceC;
    }
}
