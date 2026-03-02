package com.debug.mvc_demo.demo;

/**
 * Custom business exception for exception handling demo.
 *
 * This exception is handled by GlobalExceptionHandler#handleBusinessException().
 * It carries an error code and message for structured error responses.
 *
 * Corresponds to document ⑦: ExceptionHandler Exception Handling Mechanism
 */
public class BusinessException extends RuntimeException {

	private final String errorCode;

	public BusinessException(String errorCode, String message) {
		super(message);
		this.errorCode = errorCode;
	}

	public String getErrorCode() {
		return errorCode;
	}
}
