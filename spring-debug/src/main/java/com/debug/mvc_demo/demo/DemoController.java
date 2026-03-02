package com.debug.mvc_demo.demo;

import java.beans.PropertyEditorSupport;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.propertyeditors.CustomDateEditor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * ========================================================================
 * Spring MVC Source Code Debug Demo Controller
 * ========================================================================
 *
 * This controller provides rich endpoints for debugging different parts of
 * Spring MVC source code. Each endpoint maps to a specific document topic.
 *
 * How to use:
 *   1. Start MvcApplication (SCI mode recommended)
 *   2. Set breakpoints at the suggested locations
 *   3. Send requests using curl or browser
 *   4. Step through the source code
 *
 * ========================================================================
 * Endpoint Overview:
 *
 * [Parameter Resolution] — corresponds to document ⑤
 *   GET  /demo/param?name=test&age=18     → @RequestParam resolution
 *   GET  /demo/path/42                     → @PathVariable resolution
 *   GET  /demo/header                      → @RequestHeader resolution
 *   POST /demo/body                        → @RequestBody (JSON → Object)
 *   GET  /demo/model?name=test&age=18     → @ModelAttribute data binding
 *
 * [Data Binding & Type Conversion] — corresponds to document ⑥
 *   GET  /demo/date?date=2024-01-15       → String → Date conversion
 *   GET  /demo/enum?status=ACTIVE         → String → Enum conversion
 *   POST /demo/bindSafe?name=test&role=admin → @InitBinder security demo
 *
 * [Exception Handling] — corresponds to document ⑦
 *   GET  /demo/ex/nullpointer              → NullPointerException
 *   GET  /demo/ex/illegalarg               → IllegalArgumentException
 *   GET  /demo/ex/business                 → Custom BusinessException
 *   GET  /demo/ex/accessDenied             → AccessDeniedException (403)
 *
 * [Return Value Types] — corresponds to document ⑤
 *   GET  /demo/return/string               → plain String
 *   GET  /demo/return/map                  → Map → JSON
 *   GET  /demo/return/entity               → ResponseEntity with headers
 * ========================================================================
 */
@RestController
@RequestMapping("/demo")
public class DemoController {

	// ==================================================================
	// Part 1: Parameter Resolution (corresponds to document ⑤)
	// ==================================================================

	/**
	 * @RequestParam resolution demo
	 *
	 * curl: http://localhost:8080/demo/param?name=Spring&age=18
	 *
	 * Breakpoints:
	 *   1. RequestParamMethodArgumentResolver#resolveName()
	 *   2. AbstractNamedValueMethodArgumentResolver#resolveArgument()
	 *   3. TypeConverterDelegate#convertIfNecessary() — see "age" String→Integer
	 */
	@GetMapping("/param")
	public Map<String, Object> paramDemo(
			@RequestParam String name,
			@RequestParam(defaultValue = "0") Integer age) {
		Map<String, Object> result = new HashMap<>();
		result.put("name", name);
		result.put("age", age);
		result.put("debug_hint", "Set breakpoint at RequestParamMethodArgumentResolver#resolveName()");
		return result;
	}

	/**
	 * @PathVariable resolution demo
	 *
	 * curl: http://localhost:8080/demo/path/42
	 *
	 * Breakpoints:
	 *   1. PathVariableMethodArgumentResolver#resolveName()
	 *   2. RequestMappingInfoHandlerMapping#handleMatch() — see URI template variable extraction
	 */
	@GetMapping("/path/{id}")
	public Map<String, Object> pathDemo(@PathVariable Long id) {
		Map<String, Object> result = new HashMap<>();
		result.put("id", id);
		result.put("debug_hint", "Set breakpoint at PathVariableMethodArgumentResolver#resolveName()");
		return result;
	}

	/**
	 * @RequestHeader resolution demo
	 *
	 * curl -H "X-Token: abc123" http://localhost:8080/demo/header
	 *
	 * Breakpoints:
	 *   1. RequestHeaderMethodArgumentResolver#resolveName()
	 */
	@GetMapping("/header")
	public Map<String, Object> headerDemo(
			@RequestHeader(value = "X-Token", defaultValue = "none") String token,
			@RequestHeader("User-Agent") String userAgent) {
		Map<String, Object> result = new HashMap<>();
		result.put("X-Token", token);
		result.put("User-Agent", userAgent);
		result.put("debug_hint", "Set breakpoint at RequestHeaderMethodArgumentResolver#resolveName()");
		return result;
	}

	/**
	 * @RequestBody resolution demo (JSON → Java Object)
	 *
	 * curl -X POST http://localhost:8080/demo/body \
	 *   -H "Content-Type: application/json" \
	 *   -d '{"name":"Spring","age":18}'
	 *
	 * Breakpoints:
	 *   1. RequestResponseBodyMethodProcessor#resolveArgument()
	 *   2. AbstractMessageConverterMethodArgumentResolver#readWithMessageConverters()
	 *   3. MappingJackson2HttpMessageConverter#readInternal()
	 */
	@PostMapping("/body")
	public Map<String, Object> bodyDemo(@RequestBody UserDTO user) {
		Map<String, Object> result = new HashMap<>();
		result.put("received", user);
		result.put("debug_hint", "Set breakpoint at RequestResponseBodyMethodProcessor#resolveArgument()");
		return result;
	}

	/**
	 * @ModelAttribute data binding demo (form params → Object)
	 *
	 * curl "http://localhost:8080/demo/model?name=Spring&age=18"
	 *
	 * Breakpoints:
	 *   1. ModelAttributeMethodProcessor#resolveArgument()
	 *   2. WebDataBinder#bind() — see how request params bind to object fields
	 *   3. DataBinder#doBind() → BeanWrapperImpl#setPropertyValue()
	 */
	@GetMapping("/model")
	public Map<String, Object> modelDemo(@ModelAttribute UserDTO user) {
		Map<String, Object> result = new HashMap<>();
		result.put("bound_object", user);
		result.put("debug_hint", "Set breakpoint at ModelAttributeMethodProcessor#resolveArgument()");
		return result;
	}

	// ==================================================================
	// Part 2: Data Binding & Type Conversion (corresponds to document ⑥)
	// ==================================================================

	/**
	 * @InitBinder — register custom Date editor
	 *
	 * This method is called before parameter resolution for every request
	 * to this controller. It registers a custom PropertyEditor for Date type.
	 *
	 * Breakpoints:
	 *   1. InitBinderDataBinderFactory#initBinder()
	 *   2. InvocableHandlerMethod#invokeForRequest() — for @InitBinder method
	 *
	 * Security demo: disallowedFields("role") prevents "role" from being
	 * bound via @ModelAttribute, even if the client sends it.
	 *
	 * Breakpoints for security:
	 *   1. WebDataBinder#checkFieldMarkers() → check disallowedFields
	 */
	@InitBinder
	public void initBinder(WebDataBinder binder) {
		// Register custom Date format
		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
		dateFormat.setLenient(false);
		binder.registerCustomEditor(Date.class, new CustomDateEditor(dateFormat, true));

		// Register custom enum editor for Status
		binder.registerCustomEditor(Status.class, new PropertyEditorSupport() {
			@Override
			public void setAsText(String text) {
				setValue(Status.valueOf(text.toUpperCase()));
			}
		});

		// Security: prevent "role" field from being bound
		// Even if client sends ?role=admin, it will be ignored
		binder.setDisallowedFields("role");
	}

	/**
	 * String → Date type conversion demo
	 *
	 * curl "http://localhost:8080/demo/date?date=2024-01-15"
	 *
	 * Breakpoints:
	 *   1. TypeConverterDelegate#convertIfNecessary()
	 *   2. CustomDateEditor#setAsText()
	 */
	@GetMapping("/date")
	public Map<String, Object> dateDemo(@RequestParam Date date) {
		Map<String, Object> result = new HashMap<>();
		result.put("parsed_date", date.toString());
		result.put("debug_hint", "Set breakpoint at TypeConverterDelegate#convertIfNecessary()");
		return result;
	}

	/**
	 * String → Enum type conversion demo
	 *
	 * curl "http://localhost:8080/demo/enum?status=ACTIVE"
	 *
	 * Breakpoints:
	 *   1. TypeConverterDelegate#convertIfNecessary()
	 *   2. PropertyEditorSupport#setAsText() — the custom editor above
	 */
	@GetMapping("/enum")
	public Map<String, Object> enumDemo(@RequestParam Status status) {
		Map<String, Object> result = new HashMap<>();
		result.put("status", status);
		result.put("ordinal", status.ordinal());
		result.put("debug_hint", "Set breakpoint at TypeConverterDelegate#convertIfNecessary()");
		return result;
	}

	/**
	 * @InitBinder security demo — disallowedFields
	 *
	 * curl -X POST "http://localhost:8080/demo/bindSafe?name=test&age=18&role=admin"
	 *
	 * Result: "role" field will be null because it's in disallowedFields.
	 * This prevents mass assignment attacks (Spring's equivalent of Rails' strong_params).
	 *
	 * Breakpoints:
	 *   1. WebDataBinder#checkFieldMarkers()
	 *   2. DataBinder#isAllowed() — check if field is allowed
	 */
	@PostMapping("/bindSafe")
	public Map<String, Object> bindSafeDemo(@ModelAttribute UserDTO user) {
		Map<String, Object> result = new HashMap<>();
		result.put("name", user.getName());
		result.put("age", user.getAge());
		result.put("role", user.getRole());
		result.put("role_is_null", user.getRole() == null);
		result.put("explanation", "'role' is in disallowedFields, so it's not bound even if provided");
		result.put("debug_hint", "Set breakpoint at DataBinder#isAllowed()");
		return result;
	}

	// ==================================================================
	// Part 3: Exception Handling (corresponds to document ⑦)
	// ==================================================================

	/**
	 * NullPointerException demo
	 *
	 * curl http://localhost:8080/demo/ex/nullpointer
	 *
	 * Breakpoints:
	 *   1. DispatcherServlet#processDispatchResult() → enters exception path
	 *   2. DispatcherServlet#processHandlerException() → iterates ExceptionResolvers
	 *   3. ExceptionHandlerExceptionResolver#doResolveHandlerMethodException()
	 *   4. GlobalExceptionHandler#handleNullPointer() — our @ExceptionHandler
	 */
	@GetMapping("/ex/nullpointer")
	public String nullPointerDemo() {
		String s = null;
		return s.length() + "";  // throws NullPointerException
	}

	/**
	 * IllegalArgumentException demo
	 *
	 * curl http://localhost:8080/demo/ex/illegalarg
	 *
	 * Breakpoints: same as nullpointer, but matches different @ExceptionHandler
	 */
	@GetMapping("/ex/illegalarg")
	public String illegalArgDemo() {
		throw new IllegalArgumentException("Invalid parameter: id must be positive");
	}

	/**
	 * Custom BusinessException demo
	 *
	 * curl http://localhost:8080/demo/ex/business
	 *
	 * This exception is handled by GlobalExceptionHandler#handleBusinessException()
	 *
	 * Breakpoints:
	 *   1. ExceptionHandlerExceptionResolver#getExceptionHandlerMethod()
	 *      → see how it searches: first local controller, then @ControllerAdvice
	 *   2. ExceptionHandlerMethodResolver#resolveMethodByThrowable()
	 *      → see depth-first exception type matching
	 */
	@GetMapping("/ex/business")
	public String businessExDemo() {
		throw new BusinessException("ORDER_NOT_FOUND", "Order #12345 does not exist");
	}

	/**
	 * AccessDeniedException demo (triggers ResponseStatusExceptionResolver)
	 *
	 * curl http://localhost:8080/demo/ex/accessDenied
	 *
	 * This exception is annotated with @ResponseStatus(403),
	 * handled by ResponseStatusExceptionResolver (not ExceptionHandlerExceptionResolver).
	 *
	 * Breakpoints:
	 *   1. ResponseStatusExceptionResolver#doResolveException()
	 *   2. response.sendError() — see how HTTP status code is set
	 */
	@GetMapping("/ex/accessDenied")
	public String accessDeniedDemo() {
		throw new ForbiddenException("You don't have permission to access this resource");
	}

	// ==================================================================
	// Part 4: Return Value Types (corresponds to document ⑤)
	// ==================================================================

	/**
	 * Return plain String
	 *
	 * curl http://localhost:8080/demo/return/string
	 *
	 * Breakpoints:
	 *   1. RequestResponseBodyMethodProcessor#handleReturnValue()
	 *   2. StringHttpMessageConverter#writeInternal()
	 */
	@GetMapping("/return/string")
	public String returnStringDemo() {
		return "Hello from DemoController!";
	}

	/**
	 * Return Map → serialized as JSON
	 *
	 * curl http://localhost:8080/demo/return/map
	 *
	 * Breakpoints:
	 *   1. RequestResponseBodyMethodProcessor#handleReturnValue()
	 *   2. AbstractGenericHttpMessageConverter#write()
	 *   3. MappingJackson2HttpMessageConverter#writeInternal()
	 */
	@GetMapping("/return/map")
	public Map<String, Object> returnMapDemo() {
		Map<String, Object> result = new HashMap<>();
		result.put("message", "This is a Map that will be serialized to JSON");
		result.put("timestamp", System.currentTimeMillis());
		result.put("debug_hint", "Set breakpoint at MappingJackson2HttpMessageConverter#writeInternal()");
		return result;
	}

	/**
	 * Return ResponseEntity (with custom headers and status code)
	 *
	 * curl -v http://localhost:8080/demo/return/entity
	 *
	 * Breakpoints:
	 *   1. HttpEntityMethodProcessor#handleReturnValue()
	 *      → this is a different handler than RequestResponseBodyMethodProcessor!
	 */
	@GetMapping("/return/entity")
	public ResponseEntity<Map<String, Object>> returnEntityDemo() {
		Map<String, Object> body = new HashMap<>();
		body.put("message", "ResponseEntity demo");
		body.put("debug_hint", "Set breakpoint at HttpEntityMethodProcessor#handleReturnValue()");

		return ResponseEntity
				.status(HttpStatus.CREATED)
				.header("X-Custom-Header", "debug-demo")
				.body(body);
	}

	// ==================================================================
	// Inner classes / DTOs / Enums
	// ==================================================================

	/**
	 * Simple DTO for data binding demos
	 */
	public static class UserDTO {
		private String name;
		private Integer age;
		private String role;  // This field is in disallowedFields

		public String getName() { return name; }
		public void setName(String name) { this.name = name; }
		public Integer getAge() { return age; }
		public void setAge(Integer age) { this.age = age; }
		public String getRole() { return role; }
		public void setRole(String role) { this.role = role; }

		@Override
		public String toString() {
			return "UserDTO{name='" + name + "', age=" + age + ", role='" + role + "'}";
		}
	}

	/**
	 * Enum for type conversion demo
	 */
	public enum Status {
		ACTIVE, INACTIVE, PENDING
	}
}
