package com.debug.circulDemo.multi;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * ServiceB - 循环依赖 ServiceA
 * 场景：A → B, B → A
 * 
 * @author debug
 */
@Service
public class ServiceB {

    @Autowired
    private ServiceA serviceA;

    public void doSomething() {
        System.out.println("ServiceB.doSomething()");
    }

    public ServiceA getServiceA() {
        return serviceA;
    }
}
