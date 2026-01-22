package com.debug.simpleDemo_1.service;

import org.springframework.beans.factory.FactoryBean;
import org.springframework.stereotype.Component;

/**
 * @Classname MyFactoryBean
 * @Date 1/22/26
 * @Created by ywj
 */
@Component
public class MyFactoryBean implements FactoryBean {
	@Override
	public Object getObject() throws Exception {
		return null;
	}

	@Override
	public Class<?> getObjectType() {
		return null;
	}

	@Override
	public boolean isSingleton() {
		return FactoryBean.super.isSingleton();
	}
}
