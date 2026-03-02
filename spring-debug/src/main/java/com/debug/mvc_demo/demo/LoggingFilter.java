package com.debug.mvc_demo.demo;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

/**
 * ========================================================================
 * Logging Filter — Filter Chain Execution Order Demo
 * ========================================================================
 *
 * Demonstrates the Filter chain's "onion model" (recursive invocation).
 * Corresponds to document ⑧: Filter vs Interceptor Complete Comparison.
 *
 * Execution Order (when both LoggingFilter and AuthFilter are registered):
 *
 *   Request  →  LoggingFilter (pre)  →  AuthFilter (pre)  →  DispatcherServlet
 *   Response ←  LoggingFilter (post) ←  AuthFilter (post) ←  DispatcherServlet
 *
 * This is because Filters use a recursive call stack:
 *   LoggingFilter.doFilter() {
 *       // pre-processing
 *       chain.doFilter() → AuthFilter.doFilter() {
 *                              // pre-processing
 *                              chain.doFilter() → DispatcherServlet.service()
 *                              // post-processing
 *                          }
 *       // post-processing
 *   }
 *
 * Key Breakpoints:
 *   1. ApplicationFilterChain#internalDoFilter() → see filter[n] invocation
 *   2. ApplicationFilterChain#doFilter() → see pos++ (array index increment)
 *   3. This class — doFilter() method below
 *
 * ========================================================================
 */
public class LoggingFilter implements Filter {

	@Override
	public void init(FilterConfig filterConfig) throws ServletException {
		System.out.println("[LoggingFilter] ✅ init() called — Filter initialized");
	}

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {

		HttpServletRequest httpRequest = (HttpServletRequest) request;
		String uri = httpRequest.getRequestURI();
		String method = httpRequest.getMethod();
		long startTime = System.currentTimeMillis();

		System.out.println();
		System.out.println("╔══════════════════════════════════════════════════════════╗");
		System.out.println("║ [LoggingFilter] ▶ PRE-PROCESSING                        ║");
		System.out.println("║ Request: " + method + " " + uri);
		System.out.println("║ Thread: " + Thread.currentThread().getName());
		System.out.println("╚══════════════════════════════════════════════════════════╝");

		// ★ This is the key line — it passes control to the next filter in the chain.
		// Set breakpoint at ApplicationFilterChain#internalDoFilter() to see the recursion.
		chain.doFilter(request, response);

		long duration = System.currentTimeMillis() - startTime;
		System.out.println();
		System.out.println("╔══════════════════════════════════════════════════════════╗");
		System.out.println("║ [LoggingFilter] ◀ POST-PROCESSING                       ║");
		System.out.println("║ Request: " + method + " " + uri);
		System.out.println("║ Duration: " + duration + "ms");
		System.out.println("╚══════════════════════════════════════════════════════════╝");
	}

	@Override
	public void destroy() {
		System.out.println("[LoggingFilter] ❌ destroy() called — Filter destroyed");
	}
}
