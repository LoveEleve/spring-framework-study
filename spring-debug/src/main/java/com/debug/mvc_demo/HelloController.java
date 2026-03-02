package com.debug.mvc_demo;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 测试用 Controller
 *
 * 调试建议 - 请求处理的核心断点位置：
 * 1. DispatcherServlet#doDispatch()           → 请求分发入口
 * 2. RequestMappingHandlerMapping#getHandler() → 根据URL找到对应的Handler
 * 3. RequestMappingHandlerAdapter#handleInternal() → 执行Handler方法
 * 4. HandlerMethodArgumentResolver#resolveArgument() → 参数解析
 * 5. HandlerMethodReturnValueHandler#handleReturnValue() → 返回值处理
 */
@RestController
public class HelloController {

	/**
	 * 最简单的接口 - 用于跟踪完整的请求处理流程
	 * 访问地址：http://localhost:8080/hello
	 */
	@GetMapping("/hello")
	public String hello() {
		return "Hello Spring MVC Source Code!";
	}

	/**
	 * 带参数的接口 - 用于调试参数解析过程
	 * 访问地址：http://localhost:8080/greet?name=Spring
	 *
	 * 调试重点：RequestParamMethodArgumentResolver 如何解析 @RequestParam
	 */
	@GetMapping("/greet")
	public String greet(@RequestParam(defaultValue = "World") String name) {
		return "Hello, " + name + "!";
	}

	/**
	 * 路径变量接口 - 用于调试 PathVariable 解析过程
	 * 访问地址：http://localhost:8080/user/1
	 *
	 * 调试重点：PathVariableMethodArgumentResolver 如何解析 @PathVariable
	 */
	@GetMapping("/user/{id}")
	public Map<String, Object> getUser(@PathVariable Long id) {
		Map<String, Object> user = new HashMap<>();
		user.put("id", id);
		user.put("name", "用户" + id);
		user.put("message", "这是用于调试 Spring MVC 源码的示例接口");
		return user;
	}
}
