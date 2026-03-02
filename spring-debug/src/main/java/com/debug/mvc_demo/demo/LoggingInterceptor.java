package com.debug.mvc_demo.demo;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

/**
 * ========================================================================
 * Logging Interceptor — Interceptor Execution Order Demo
 * ========================================================================
 *
 * Demonstrates the HandlerInterceptor lifecycle and execution order.
 * Registered BEFORE AuthInterceptor, so executes first (lower order number).
 *
 * Interceptor Lifecycle:
 *   preHandle  → executed BEFORE handler method (forward order: 0,1,2)
 *   postHandle → executed AFTER handler, BEFORE view rendering (reverse order: 2,1,0)
 *   afterCompletion → executed AFTER everything, always (reverse order: 2,1,0)
 *
 * Key Breakpoints:
 *   1. HandlerExecutionChain#applyPreHandle()
 *      → iterates interceptors[0..n] calling preHandle()
 *   2. HandlerExecutionChain#applyPostHandle()
 *      → iterates interceptors[n..0] calling postHandle() (reverse!)
 *   3. HandlerExecutionChain#triggerAfterCompletion()
 *      → iterates interceptors[interceptorIndex..0] calling afterCompletion()
 *      → only calls interceptors whose preHandle() returned true
 *
 * Corresponds to document ⑧: Filter vs Interceptor Complete Comparison
 * ========================================================================
 */
public class LoggingInterceptor implements HandlerInterceptor {

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
			throws Exception {

		String uri = request.getRequestURI();
		String method = request.getMethod();

		System.out.println("  ┌─────────────────────────────────────────────────┐");
		System.out.println("  │ [LoggingInterceptor] preHandle()                │");
		System.out.println("  │ Request: " + method + " " + uri);
		System.out.println("  │ Handler: " + handler);
		System.out.println("  │ This runs AFTER Filter, BEFORE Controller       │");
		System.out.println("  └─────────────────────────────────────────────────┘");

		request.setAttribute("interceptor_startTime", System.currentTimeMillis());
		return true; // return true to continue, false to block
	}

	@Override
	public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
			ModelAndView modelAndView) throws Exception {

		System.out.println("  ┌─────────────────────────────────────────────────┐");
		System.out.println("  │ [LoggingInterceptor] postHandle()               │");
		System.out.println("  │ This runs AFTER Controller, BEFORE view render  │");
		System.out.println("  │ Note: NOT called if Controller throws exception │");
		System.out.println("  │ ModelAndView: " + modelAndView);
		System.out.println("  └─────────────────────────────────────────────────┘");
	}

	@Override
	public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
			Exception ex) throws Exception {

		long startTime = (Long) request.getAttribute("interceptor_startTime");
		long duration = System.currentTimeMillis() - startTime;

		System.out.println("  ┌─────────────────────────────────────────────────┐");
		System.out.println("  │ [LoggingInterceptor] afterCompletion()          │");
		System.out.println("  │ Duration: " + duration + "ms");
		System.out.println("  │ Exception: " + (ex != null ? ex.getMessage() : "none"));
		System.out.println("  │ This ALWAYS runs (even if exception occurred)   │");
		System.out.println("  └─────────────────────────────────────────────────┘");
	}
}
