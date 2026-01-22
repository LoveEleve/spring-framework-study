package com.debug.simpleDemo_1.service;

import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

/**
 * @Classname TestListener
 * @Date 1/22/26
 * @Created by ywj
 */
@Component
public class TestListener implements ApplicationListener {
	@Override
	public void onApplicationEvent(ApplicationEvent event) {
		System.out.println("hello");
	}
}
