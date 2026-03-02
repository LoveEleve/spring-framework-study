package com.debug.mvc_demo.demo;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * ========================================================================
 * Auth Filter — Filter Chain Execution Order Demo
 * ========================================================================
 *
 * Demonstrates Filter-based authentication and the Filter chain order.
 * Registered AFTER LoggingFilter, so executes second in the chain.
 *
 * Execution Order:
 *   LoggingFilter (1st) → AuthFilter (2nd, this class) → DispatcherServlet
 *
 * Special behavior:
 *   - If X-Auth header is "reject", this filter SHORT-CIRCUITS the chain
 *     by NOT calling chain.doFilter(). The request never reaches DispatcherServlet.
 *   - This demonstrates that Filters can completely block requests before
 *     they reach Spring MVC, while Interceptors cannot.
 *
 * Key Breakpoints:
 *   1. ApplicationFilterChain#internalDoFilter() → see this filter invoked
 *   2. This class — doFilter() method, especially the short-circuit logic
 *
 * Corresponds to document ⑧: Filter vs Interceptor Complete Comparison
 * ========================================================================
 */
public class AuthFilter implements Filter {

	@Override
	public void init(FilterConfig filterConfig) throws ServletException {
		System.out.println("[AuthFilter] ✅ init() called — Filter initialized");
	}

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {

		HttpServletRequest httpRequest = (HttpServletRequest) request;
		HttpServletResponse httpResponse = (HttpServletResponse) response;
		String authHeader = httpRequest.getHeader("X-Auth");

		System.out.println("  [AuthFilter] ▶ PRE: Checking auth header, X-Auth=" + authHeader);

		// Short-circuit demo: if X-Auth is "reject", block the request
		if ("reject".equals(authHeader)) {
			System.out.println("  [AuthFilter] ⛔ BLOCKED! Request short-circuited by Filter.");
			System.out.println("  [AuthFilter] ⛔ chain.doFilter() is NOT called — DispatcherServlet never sees this request!");
			httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			httpResponse.setContentType("application/json;charset=UTF-8");
			httpResponse.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"Blocked by AuthFilter\",\"debug_hint\":\"Filter short-circuited! Interceptor cannot do this.\"}");
			return; // ★ NOT calling chain.doFilter() — this is the short-circuit
		}

		System.out.println("  [AuthFilter] ✅ Auth passed, proceeding to next filter/servlet");

		// Normal flow: pass to next filter or servlet
		chain.doFilter(request, response);

		System.out.println("  [AuthFilter] ◀ POST: Auth filter post-processing complete");
	}

	@Override
	public void destroy() {
		System.out.println("[AuthFilter] ❌ destroy() called — Filter destroyed");
	}
}
