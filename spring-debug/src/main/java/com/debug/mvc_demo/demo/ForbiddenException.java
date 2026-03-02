package com.debug.mvc_demo.demo;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception annotated with @ResponseStatus — triggers ResponseStatusExceptionResolver
 *
 * This is NOT handled by ExceptionHandlerExceptionResolver,
 * but by ResponseStatusExceptionResolver (a different resolver in the chain).
 *
 * Breakpoints:
 *   1. ResponseStatusExceptionResolver#doResolveException()
 *   2. ResponseStatusExceptionResolver#resolveResponseStatus()
 *   3. HttpServletResponse#sendError(403, ...)
 *
 * Corresponds to document ⑦: ExceptionHandler Exception Handling Mechanism
 */
@ResponseStatus(value = HttpStatus.FORBIDDEN, reason = "Access Denied")
public class ForbiddenException extends RuntimeException {

	public ForbiddenException(String message) {
		super(message);
	}
}
