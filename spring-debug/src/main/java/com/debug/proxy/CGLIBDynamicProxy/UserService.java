package com.debug.proxy.CGLIBDynamicProxy;

/**
 * @Classname UserService
 * @Date 2/1/26
 * @Created by ywj
 */
public class UserService {
    public void commit() {
        System.out.println("UserService commit");
    }

    public final void finalMethod() {
        System.out.println("UserService finalMethod");
    }

    public void test(){
        System.out.println("UserService test()");
    }

    public void internalMethod(){
        System.out.println("UserService internalMethod()");
    }

    public void noProxyMethod(){
        System.out.println("UserService noProxyMethod()");
    }
}
