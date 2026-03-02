package com.debug.circulDemo.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * @Classname Man
 * @Date 1/23/26
 * @Created by ywj
 */
@Component
public class Man {
	@Autowired
	WoMan woman;
}
