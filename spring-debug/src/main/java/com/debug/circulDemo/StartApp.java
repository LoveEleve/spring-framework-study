package com.debug.circulDemo;

import com.debug.circulDemo.service.Man;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * @Classname StartApp
 * @Date 1/23/26
 * @Created by ywj
 */
public class StartApp {
	public static void main(String[] args) {
		AnnotationConfigApplicationContext context
				= new AnnotationConfigApplicationContext(CirculConfig.class);
		Man man = context.getBean(Man.class);
		System.out.println("hello world");
	}
}
