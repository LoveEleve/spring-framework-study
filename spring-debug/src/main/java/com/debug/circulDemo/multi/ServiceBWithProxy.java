package com.debug.circulDemo.multi;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * ServiceBWithProxy - 循环依赖需要代理的ServiceA
 * 
 * @author debug
 */
@Service
public class ServiceBWithProxy {

    @Autowired
    private ServiceAWithProxy serviceA;

    public void doSomething() {
        System.out.println("ServiceBWithProxy.doSomething()");
    }

    public ServiceAWithProxy getServiceA() {
        return serviceA;
    }
}
