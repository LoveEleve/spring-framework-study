package com.debug.circulDemo.multi;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * ServiceCWithProxy - 循环依赖需要代理的ServiceA
 * 
 * @author debug
 */
@Service
public class ServiceCWithProxy {

    @Autowired
    private ServiceAWithProxy serviceA;

    public void doSomething() {
        System.out.println("ServiceCWithProxy.doSomething()");
    }

    public ServiceAWithProxy getServiceA() {
        return serviceA;
    }
}
