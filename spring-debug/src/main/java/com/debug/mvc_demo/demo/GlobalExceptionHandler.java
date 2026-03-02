package com.debug.mvc_demo.demo;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * ========================================================================
 * Global Exception Handler — @ControllerAdvice Demo
 * ========================================================================
 *
 * Demonstrates how Spring MVC's exception handling mechanism works.
 * Corresponds to document ⑦: ExceptionHandler Exception Handling Mechanism.
 *
 * Exception Resolution Order (3 built-in resolvers):
 *   1. ExceptionHandlerExceptionResolver — handles @ExceptionHandler methods (this class)
 *   2. ResponseStatusExceptionResolver   — handles @ResponseStatus annotated exceptions
 *   3. DefaultHandlerExceptionResolver   — handles standard Spring MVC exceptions
 *
 * Key Breakpoints:
 *   1. DispatcherServlet#processHandlerException()
 *      → iterates through HandlerExceptionResolver chain
 *   2. ExceptionHandlerExceptionResolver#doResolveHandlerMethodException()
 *      → finds matching @ExceptionHandler method
 *   3. ExceptionHandlerExceptionResolver#getExceptionHandlerMethod()
 *      → search order: local controller first, then @ControllerAdvice
 *   4. ExceptionHandlerMethodResolver#resolveMethodByThrowable()
 *      → depth-first matching: NullPointerException → RuntimeException → Exception
 *
 * ========================================================================
 */
@ControllerAdvice
public class GlobalExceptionHandler {

	/**
	 * Handle NullPointerException
	 *
	 * Trigger: GET /demo/ex/nullpointer
	 *
	 * Breakpoints:
	 *   1. ExceptionHandlerMethodResolver#resolveMethodByThrowable()
	 *      → matches NullPointerException.class
	 *   2. This method is invoked via InvocableHandlerMethod#invokeForRequest()
	 */
	@ExceptionHandler(NullPointerException.class)
	@ResponseBody
	public ResponseEntity<Map<String, Object>> handleNullPointer(NullPointerException ex) {
		Map<String, Object> body = new HashMap<>();
		body.put("error", "NullPointerException");
		body.put("message", ex.getMessage() != null ? ex.getMessage() : "A null pointer occurred");
		body.put("status", 500);
		body.put("handler", "GlobalExceptionHandler#handleNullPointer");
		body.put("resolver", "ExceptionHandlerExceptionResolver");
		body.put("debug_hint", "Set breakpoint at ExceptionHandlerExceptionResolver#doResolveHandlerMethodException()");
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
	}

	/**
	 * Handle IllegalArgumentException
	 *
	 * Trigger: GET /demo/ex/illegalarg
	 *
	 * Breakpoints:
	 *   1. ExceptionHandlerMethodResolver#resolveMethodByThrowable()
	 *      → matches IllegalArgumentException.class
	 */
	@ExceptionHandler(IllegalArgumentException.class)
	@ResponseBody
	public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
		Map<String, Object> body = new HashMap<>();
		body.put("error", "IllegalArgumentException");
		body.put("message", ex.getMessage());
		body.put("status", 400);
		body.put("handler", "GlobalExceptionHandler#handleIllegalArgument");
		body.put("resolver", "ExceptionHandlerExceptionResolver");
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
	}

	/**
	 * Handle custom BusinessException
	 *
	 * Trigger: GET /demo/ex/business
	 *
	 * Breakpoints:
	 *   1. ExceptionHandlerMethodResolver#resolveMethodByThrowable()
	 *      → exact match: BusinessException.class
	 *   2. Note: if we didn't have this handler, it would fall through to
	 *      handleGenericException() because BusinessException extends RuntimeException
	 */
	@ExceptionHandler(BusinessException.class)
	@ResponseBody
	public ResponseEntity<Map<String, Object>> handleBusinessException(BusinessException ex) {
		Map<String, Object> body = new HashMap<>();
		body.put("error", "BusinessException");
		body.put("errorCode", ex.getErrorCode());
		body.put("message", ex.getMessage());
		body.put("status", 422);
		body.put("handler", "GlobalExceptionHandler#handleBusinessException");
		body.put("resolver", "ExceptionHandlerExceptionResolver");
		body.put("debug_hint", "Try commenting out this method, then BusinessException will be caught by handleGenericException()");
		return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body);
	}

	/**
	 * Fallback handler for all other exceptions
	 *
	 * This is the catch-all: any exception not matched by the above handlers
	 * will be caught here.
	 *
	 * Breakpoints:
	 *   1. ExceptionHandlerMethodResolver#resolveMethodByThrowable()
	 *      → walks up the exception hierarchy until it matches Exception.class
	 */
	@ExceptionHandler(Exception.class)
	@ResponseBody
	public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
		Map<String, Object> body = new HashMap<>();
		body.put("error", ex.getClass().getSimpleName());
		body.put("message", ex.getMessage());
		body.put("status", 500);
		body.put("handler", "GlobalExceptionHandler#handleGenericException (fallback)");
		body.put("resolver", "ExceptionHandlerExceptionResolver");
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
	}
}
