package com.debug.simpleDebug;

import org.springframework.context.annotation.*;

@Configuration
@ComponentScan("com.debug.simpleDebug")
@EnableAutoConfiguration
@Import(B.class)
public class DebugApplication {
	//
	public static void main(String[] args) {
		// 断点1: 容器创建入口 - 可以跟踪 refresh() 方法
		AnnotationConfigApplicationContext context =
				new AnnotationConfigApplicationContext(DebugApplication.class);

		// 断点2: 获取 Bean - 可以跟踪 getBean() 流程
		HelloService helloService = context.getBean(HelloService.class);
		helloService.hello();

		context.close();
	}
	//
	@Bean
	public HelloService helloService() {
		return new HelloService();
	}

	public static class HelloService {
		public void hello() {
			System.out.println("Debug Spring Source!");
		}
	}
}
