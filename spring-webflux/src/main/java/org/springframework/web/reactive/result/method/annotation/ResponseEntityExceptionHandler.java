/*
 * Copyright 2002-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.web.reactive.result.method.annotation;

import java.util.Locale;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Mono;

import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceAware;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.ErrorResponse;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.MethodNotAllowedException;
import org.springframework.web.server.MissingRequestValueException;
import org.springframework.web.server.NotAcceptableStatusException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerErrorException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;
import org.springframework.web.server.UnsatisfiedRequestParameterException;
import org.springframework.web.server.UnsupportedMediaTypeStatusException;

/**
 * A class with an {@code @ExceptionHandler} method that handles all Spring
 * WebFlux raised exceptions by returning a {@link ResponseEntity} with
 * RFC 7807 formatted error details in the body.
 *
 * <p>Convenient as a base class of an {@link ControllerAdvice @ControllerAdvice}
 * for global exception handling in an application. Subclasses can override
 * individual methods that handle a specific exception, override
 * {@link #handleExceptionInternal} to override common handling of all exceptions,
 * or {@link #createResponseEntity} to intercept the final step of creating the
 * {@link ResponseEntity} from the selected HTTP status code, headers, and body.
 *
 * @author Rossen Stoyanchev
 * @since 6.0
 */
public abstract class ResponseEntityExceptionHandler implements MessageSourceAware {

	/**
	 * Common logger for use in subclasses.
	 */
	protected final Log logger = LogFactory.getLog(getClass());

	@Nullable
	private MessageSource messageSource;


	@Override
	public void setMessageSource(MessageSource messageSource) {
		this.messageSource = messageSource;
	}


	/**
	 * Handle all exceptions raised within Spring MVC handling of the request .
	 * @param ex the exception to handle
	 * @param exchange the current request-response
	 */
	@ExceptionHandler({
			MethodNotAllowedException.class,
			NotAcceptableStatusException.class,
			UnsupportedMediaTypeStatusException.class,
			MissingRequestValueException.class,
			UnsatisfiedRequestParameterException.class,
			WebExchangeBindException.class,
			ServerWebInputException.class,
			ServerErrorException.class,
			ResponseStatusException.class,
			ErrorResponseException.class
	})
	public final Mono<ResponseEntity<Object>> handleException(Exception ex, ServerWebExchange exchange) {
		if (ex instanceof MethodNotAllowedException theEx) {
			return handleMethodNotAllowedException(theEx, theEx.getHeaders(), theEx.getStatusCode(), exchange);
		}
		else if (ex instanceof NotAcceptableStatusException theEx) {
			return handleNotAcceptableStatusException(theEx, theEx.getHeaders(), theEx.getStatusCode(), exchange);
		}
		else if (ex instanceof UnsupportedMediaTypeStatusException theEx) {
			return handleUnsupportedMediaTypeStatusException(theEx, theEx.getHeaders(), theEx.getStatusCode(), exchange);
		}
		else if (ex instanceof MissingRequestValueException theEx) {
			return handleMissingRequestValueException(theEx, theEx.getHeaders(), theEx.getStatusCode(), exchange);
		}
		else if (ex instanceof UnsatisfiedRequestParameterException theEx) {
			return handleUnsatisfiedRequestParameterException(theEx, theEx.getHeaders(), theEx.getStatusCode(), exchange);
		}
		else if (ex instanceof WebExchangeBindException theEx) {
			return handleWebExchangeBindException(theEx, theEx.getHeaders(), theEx.getStatusCode(), exchange);
		}
		else if (ex instanceof ServerWebInputException theEx) {
			return handleServerWebInputException(theEx, theEx.getHeaders(), theEx.getStatusCode(), exchange);
		}
		else if (ex instanceof ServerErrorException theEx) {
			return handleServerErrorException(theEx, theEx.getHeaders(), theEx.getStatusCode(), exchange);
		}
		else if (ex instanceof ResponseStatusException theEx) {
			return handleResponseStatusException(theEx, theEx.getHeaders(), theEx.getStatusCode(), exchange);
		}
		else if (ex instanceof ErrorResponseException theEx) {
			return handleErrorResponseException(theEx, theEx.getHeaders(), theEx.getStatusCode(), exchange);
		}
		else {
			if (logger.isWarnEnabled()) {
				logger.warn("Unexpected exception type: " + ex.getClass().getName());
			}
			return Mono.error(ex);
		}
	}

	/**
	 * Customize the handling of {@link MethodNotAllowedException}.
	 * <p>This method delegates to {@link #handleExceptionInternal}.
	 * @param ex the exception to handle
	 * @param headers the headers to use for the response
	 * @param status the status code to use for the response
	 * @param exchange the current request and response
	 * @return a {@code Mono} with the {@code ResponseEntity} for the response
	 */
	protected Mono<ResponseEntity<Object>> handleMethodNotAllowedException(
			MethodNotAllowedException ex, HttpHeaders headers, HttpStatusCode status,
			ServerWebExchange exchange) {

		return handleExceptionInternal(ex, null, headers, status, exchange);
	}

	/**
	 * Customize the handling of {@link NotAcceptableStatusException}.
	 * <p>This method delegates to {@link #handleExceptionInternal}.
	 * @param ex the exception to handle
	 * @param headers the headers to use for the response
	 * @param status the status code to use for the response
	 * @param exchange the current request and response
	 * @return a {@code Mono} with the {@code ResponseEntity} for the response
	 */
	protected Mono<ResponseEntity<Object>> handleNotAcceptableStatusException(
			NotAcceptableStatusException ex, HttpHeaders headers, HttpStatusCode status,
			ServerWebExchange exchange) {

		return handleExceptionInternal(ex, null, headers, status, exchange);
	}

	/**
	 * Customize the handling of {@link UnsupportedMediaTypeStatusException}.
	 * <p>This method delegates to {@link #handleExceptionInternal}.
	 * @param ex the exception to handle
	 * @param headers the headers to use for the response
	 * @param status the status code to use for the response
	 * @param exchange the current request and response
	 * @return a {@code Mono} with the {@code ResponseEntity} for the response
	 */
	protected Mono<ResponseEntity<Object>> handleUnsupportedMediaTypeStatusException(
			UnsupportedMediaTypeStatusException ex, HttpHeaders headers, HttpStatusCode status,
			ServerWebExchange exchange) {

		return handleExceptionInternal(ex, null, headers, status, exchange);
	}

	/**
	 * Customize the handling of {@link MissingRequestValueException}.
	 * <p>This method delegates to {@link #handleExceptionInternal}.
	 * @param ex the exception to handle
	 * @param headers the headers to use for the response
	 * @param status the status code to use for the response
	 * @param exchange the current request and response
	 * @return a {@code Mono} with the {@code ResponseEntity} for the response
	 */
	protected Mono<ResponseEntity<Object>> handleMissingRequestValueException(
			MissingRequestValueException ex, HttpHeaders headers, HttpStatusCode status,
			ServerWebExchange exchange) {

		return handleExceptionInternal(ex, null, headers, status, exchange);
	}

	/**
	 * Customize the handling of {@link UnsatisfiedRequestParameterException}.
	 * <p>This method delegates to {@link #handleExceptionInternal}.
	 * @param ex the exception to handle
	 * @param headers the headers to use for the response
	 * @param status the status code to use for the response
	 * @param exchange the current request and response
	 * @return a {@code Mono} with the {@code ResponseEntity} for the response
	 */
	protected Mono<ResponseEntity<Object>> handleUnsatisfiedRequestParameterException(
			UnsatisfiedRequestParameterException ex, HttpHeaders headers, HttpStatusCode status,
			ServerWebExchange exchange) {

		return handleExceptionInternal(ex, null, headers, status, exchange);
	}

	/**
	 * Customize the handling of {@link WebExchangeBindException}.
	 * <p>This method delegates to {@link #handleExceptionInternal}.
	 * @param ex the exception to handle
	 * @param headers the headers to use for the response
	 * @param status the status code to use for the response
	 * @param exchange the current request and response
	 * @return a {@code Mono} with the {@code ResponseEntity} for the response
	 */
	protected Mono<ResponseEntity<Object>> handleWebExchangeBindException(
			WebExchangeBindException ex, HttpHeaders headers, HttpStatusCode status,
			ServerWebExchange exchange) {

		return handleExceptionInternal(ex, null, headers, status, exchange);
	}

	/**
	 * Customize the handling of {@link ServerWebInputException}.
	 * <p>This method delegates to {@link #handleExceptionInternal}.
	 * @param ex the exception to handle
	 * @param headers the headers to use for the response
	 * @param status the status code to use for the response
	 * @param exchange the current request and response
	 * @return a {@code Mono} with the {@code ResponseEntity} for the response
	 */
	protected Mono<ResponseEntity<Object>> handleServerWebInputException(
			ServerWebInputException ex, HttpHeaders headers, HttpStatusCode status,
			ServerWebExchange exchange) {

		return handleExceptionInternal(ex, null, headers, status, exchange);
	}

	/**
	 * Customize the handling of any {@link ResponseStatusException}.
	 * <p>This method delegates to {@link #handleExceptionInternal}.
	 * @param ex the exception to handle
	 * @param headers the headers to use for the response
	 * @param status the status code to use for the response
	 * @param exchange the current request and response
	 * @return a {@code Mono} with the {@code ResponseEntity} for the response
	 */
	protected Mono<ResponseEntity<Object>> handleResponseStatusException(
			ResponseStatusException ex, HttpHeaders headers, HttpStatusCode status,
			ServerWebExchange exchange) {

		return handleExceptionInternal(ex, null, headers, status, exchange);
	}

	/**
	 * Customize the handling of {@link ServerErrorException}.
	 * <p>This method delegates to {@link #handleExceptionInternal}.
	 * @param ex the exception to handle
	 * @param headers the headers to use for the response
	 * @param status the status code to use for the response
	 * @param exchange the current request and response
	 * @return a {@code Mono} with the {@code ResponseEntity} for the response
	 */
	protected Mono<ResponseEntity<Object>> handleServerErrorException(
			ServerErrorException ex, HttpHeaders headers, HttpStatusCode status,
			ServerWebExchange exchange) {

		return handleExceptionInternal(ex, null, headers, status, exchange);
	}

	/**
	 * Customize the handling of any {@link ErrorResponseException}.
	 * <p>This method delegates to {@link #handleExceptionInternal}.
	 * @param ex the exception to handle
	 * @param headers the headers to use for the response
	 * @param status the status code to use for the response
	 * @param exchange the current request and response
	 * @return a {@code Mono} with the {@code ResponseEntity} for the response
	 */
	protected Mono<ResponseEntity<Object>> handleErrorResponseException(
			ErrorResponseException ex, HttpHeaders headers, HttpStatusCode status,
			ServerWebExchange exchange) {

		return handleExceptionInternal(ex, null, headers, status, exchange);
	}

	/**
	 * Internal handler method that all others in this class delegate to, for
	 * common handling, and for the creation of a {@link ResponseEntity}.
	 * <p>The default implementation does the following:
	 * <ul>
	 * <li>return {@code null} if response is already committed
	 * <li>set the {@code "jakarta.servlet.error.exception"} request attribute
	 * if the response status is 500 (INTERNAL_SERVER_ERROR).
	 * <li>extract the {@link ErrorResponse#getBody() body} from
	 * {@link ErrorResponse} exceptions, if the {@code body} is {@code null}.
	 * </ul>
	 * @param ex the exception to handle
	 * @param body the body to use for the response
	 * @param headers the headers to use for the response
	 * @param status the status code to use for the response
	 * @param exchange the current request and response
	 * @return a {@code Mono} with the {@code ResponseEntity} for the response
	 */
	protected Mono<ResponseEntity<Object>> handleExceptionInternal(
			Exception ex, @Nullable Object body, HttpHeaders headers, HttpStatusCode status,
			ServerWebExchange exchange) {

		if (exchange.getResponse().isCommitted()) {
			return Mono.error(ex);
		}

		if (body == null && ex instanceof ErrorResponse errorResponse) {
			body = resolveDetailViaMessageSource(errorResponse, exchange.getLocaleContext().getLocale());
		}

		return createResponseEntity(body, headers, status, exchange);
	}

	private ProblemDetail resolveDetailViaMessageSource(ErrorResponse response, @Nullable Locale locale) {
		ProblemDetail body = response.getBody();
		if (this.messageSource != null) {
			locale = (locale != null ? locale : Locale.getDefault());
			Object[] arguments = response.getDetailMessageArguments(this.messageSource, locale);
			String detail = this.messageSource.getMessage(response.getDetailMessageCode(), arguments, null, locale);
			if (detail != null) {
				body.setDetail(detail);
			}
		}
		return body;
	}

	/**
	 * Create the {@link ResponseEntity} to use from the given body, headers,
	 * and statusCode. Subclasses can override this method to inspect and possibly
	 * modify the body, headers, or statusCode, e.g. to re-create an instance of
	 * {@link ProblemDetail} as an extension of {@link ProblemDetail}.
	 * @param body the body to use for the response
	 * @param headers the headers to use for the response
	 * @param status the status code to use for the response
	 * @param exchange the current request and response
	 * @return a {@code Mono} with the created {@code ResponseEntity}
	 * @since 6.0
	 */
	protected Mono<ResponseEntity<Object>> createResponseEntity(
			@Nullable Object body, HttpHeaders headers, HttpStatusCode status, ServerWebExchange exchange) {

		return Mono.just(new ResponseEntity<>(body, headers, status));
	}

}
