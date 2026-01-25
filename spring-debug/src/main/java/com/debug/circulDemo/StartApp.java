package com.debug.circulDemo;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * @Classname StartApp
 * @Date 1/23/26
 * @Created by ywj
 */
public class StartApp {
	public static void main(String[] args) {
		AnnotationConfigApplicationContext annotationConfigApplicationContext
				= new AnnotationConfigApplicationContext(CirculConfig.class);
	}
}
