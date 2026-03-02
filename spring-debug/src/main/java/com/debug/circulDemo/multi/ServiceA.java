package com.debug.circulDemo.multi;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

/**
 * ServiceA - 被多个Bean循环依赖的核心Bean
 * 场景：A → B, A → C, B → A, C → A
 * 
 * @author debug
 */
@Service
public class ServiceA {

    @Autowired
    private ServiceB serviceB;

    @Autowired
    private ServiceC serviceC;

    public void doSomething() {
        System.out.println("ServiceA.doSomething()");
    }

    public ServiceB getServiceB() {
        return serviceB;
    }

    public ServiceC getServiceC() {
        return serviceC;
    }
}
