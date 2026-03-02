package com.debug.circulDemo.multi;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * ServiceC - 循环依赖 ServiceA
 * 场景：A → C, C → A
 * 
 * @author debug
 */
@Service
public class ServiceC {

    @Autowired
    private ServiceA serviceA;

    public void doSomething() {
        System.out.println("ServiceC.doSomething()");
    }

    public ServiceA getServiceA() {
        return serviceA;
    }
}
