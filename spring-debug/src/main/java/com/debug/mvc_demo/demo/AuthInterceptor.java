package com.debug.mvc_demo.demo;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

/**
 * ========================================================================
 * Auth Interceptor — Interceptor Execution Order Demo
 * ========================================================================
 *
 * Registered AFTER LoggingInterceptor, so executes second.
 * Demonstrates what happens when preHandle() returns false.
 *
 * Special behavior:
 *   - If X-Intercept header is "reject", preHandle() returns false.
 *   - When preHandle returns false:
 *     → This interceptor's postHandle() is NOT called
 *     → This interceptor's afterCompletion() is NOT called
 *     → But LoggingInterceptor's afterCompletion() IS called
 *       (because its preHandle() already returned true)
 *
 * This is a key difference from Filter short-circuit:
 *   - Filter short-circuit: no cleanup is possible for preceding filters
 *   - Interceptor preHandle=false: preceding interceptors' afterCompletion() still runs
 *
 * Key Breakpoints:
 *   1. HandlerExecutionChain#applyPreHandle()
 *      → see interceptorIndex tracking, used for afterCompletion cleanup
 *   2. HandlerExecutionChain#triggerAfterCompletion()
 *      → only calls afterCompletion on interceptors[0..interceptorIndex]
 *
 * Corresponds to document ⑧: Filter vs Interceptor Complete Comparison
 * ========================================================================
 */
public class AuthInterceptor implements HandlerInterceptor {

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
			throws Exception {

		String interceptHeader = request.getHeader("X-Intercept");

		System.out.println("    [AuthInterceptor] preHandle() — X-Intercept=" + interceptHeader);

		if ("reject".equals(interceptHeader)) {
			System.out.println("    [AuthInterceptor] ⛔ preHandle() returns FALSE!");
			System.out.println("    [AuthInterceptor] → This interceptor's postHandle/afterCompletion will NOT run");
			System.out.println("    [AuthInterceptor] → But LoggingInterceptor's afterCompletion WILL run (cleanup)");

			response.setStatus(HttpServletResponse.SC_FORBIDDEN);
			response.setContentType("application/json;charset=UTF-8");
			response.getWriter().write("{\"error\":\"Forbidden\",\"message\":\"Blocked by AuthInterceptor\",\"debug_hint\":\"preHandle returned false. Check afterCompletion behavior in console.\"}");
			return false; // ★ Block the request — handler will NOT be invoked
		}

		System.out.println("    [AuthInterceptor] ✅ preHandle() returns true, proceeding");
		return true;
	}

	@Override
	public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
			ModelAndView modelAndView) throws Exception {
		System.out.println("    [AuthInterceptor] postHandle()");
	}

	@Override
	public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
			Exception ex) throws Exception {
		System.out.println("    [AuthInterceptor] afterCompletion() — Exception: " + (ex != null ? ex.getMessage() : "none"));
	}
}
