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


import java.lang.reflect.Method;
import java.net.URI;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.ErrorResponseException;
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
import org.springframework.web.testfixture.http.server.reactive.MockServerHttpRequest;
import org.springframework.web.testfixture.server.MockServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ResponseEntityExceptionHandler}.
 *
 * @author Rossen Stoyanchev
 */
public class ResponseEntityExceptionHandlerTests {

	private final ResponseEntityExceptionHandler exceptionHandler = new GlobalExceptionHandler();

	private final MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/").build());


	@Test
	void handleMethodNotAllowedException() {
		ResponseEntity<ProblemDetail> entity = testException(
				new MethodNotAllowedException(HttpMethod.PATCH, List.of(HttpMethod.GET, HttpMethod.POST)));

		assertThat(entity.getHeaders().getFirst(HttpHeaders.ALLOW)).isEqualTo("GET,POST");
	}

	@Test
	void handleNotAcceptableStatusException() {
		ResponseEntity<ProblemDetail> entity = testException(
				new NotAcceptableStatusException(List.of(MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML)));

		assertThat(entity.getHeaders().getFirst(HttpHeaders.ACCEPT)).isEqualTo("application/json, application/xml");
	}

	@Test
	void handleUnsupportedMediaTypeStatusException() {
		ResponseEntity<ProblemDetail> entity = testException(
				new UnsupportedMediaTypeStatusException(MediaType.APPLICATION_JSON, List.of(MediaType.APPLICATION_XML)));

		assertThat(entity.getHeaders().getFirst(HttpHeaders.ACCEPT)).isEqualTo("application/xml");
	}

	@Test
	void handleMissingRequestValueException() {
		testException(new MissingRequestValueException("id", String.class, "cookie", null));
	}

	@Test
	void handleUnsatisfiedRequestParameterException() {
		testException(new UnsatisfiedRequestParameterException(Collections.emptyList(), new LinkedMultiValueMap<>()));
	}

	@Test
	void handleWebExchangeBindException() {
		testException(new WebExchangeBindException(null, null));
	}

	@Test
	void handleServerWebInputException() {
		testException(new ServerWebInputException(""));
	}

	@Test
	void handleServerErrorException() {
		testException(new ServerErrorException("", (Method) null, null));
	}

	@Test
	void handleResponseStatusException() {
		testException(new ResponseStatusException(HttpStatus.BANDWIDTH_LIMIT_EXCEEDED));
	}

	@Test
	void handleErrorResponseException() {
		testException(new ErrorResponseException(HttpStatus.CONFLICT));
	}


	@SuppressWarnings("unchecked")
	private ResponseEntity<ProblemDetail> testException(ErrorResponseException exception) {
		ResponseEntity<?> responseEntity =
				this.exceptionHandler.handleException(exception, this.exchange).block();

		assertThat(responseEntity).isNotNull();
		assertThat(responseEntity.getStatusCode()).isEqualTo(exception.getStatusCode());

		assertThat(responseEntity.getBody()).isNotNull().isInstanceOf(ProblemDetail.class);
		ProblemDetail body = (ProblemDetail) responseEntity.getBody();
		assertThat(body.getType()).isEqualTo(URI.create(exception.getClass().getName()));

		return (ResponseEntity<ProblemDetail>) responseEntity;
	}


	private static class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

		private Mono<ResponseEntity<Object>> handleAndSetTypeToExceptionName(
				ErrorResponseException ex, HttpHeaders headers, HttpStatusCode status, ServerWebExchange exchange) {

			ProblemDetail body = ex.getBody();
			body.setType(URI.create(ex.getClass().getName()));
			return handleExceptionInternal(ex, body, headers, status, exchange);
		}

		@Override
		protected Mono<ResponseEntity<Object>> handleMethodNotAllowedException(
				MethodNotAllowedException ex, HttpHeaders headers, HttpStatusCode status,
				ServerWebExchange exchange) {

			return handleAndSetTypeToExceptionName(ex, headers, status, exchange);
		}

		@Override
		protected Mono<ResponseEntity<Object>> handleNotAcceptableStatusException(
				NotAcceptableStatusException ex, HttpHeaders headers, HttpStatusCode status,
				ServerWebExchange exchange) {

			return handleAndSetTypeToExceptionName(ex, headers, status, exchange);
		}

		@Override
		protected Mono<ResponseEntity<Object>> handleUnsupportedMediaTypeStatusException(
				UnsupportedMediaTypeStatusException ex, HttpHeaders headers, HttpStatusCode status,
				ServerWebExchange exchange) {

			return handleAndSetTypeToExceptionName(ex, headers, status, exchange);
		}

		@Override
		protected Mono<ResponseEntity<Object>> handleMissingRequestValueException(
				MissingRequestValueException ex, HttpHeaders headers, HttpStatusCode status,
				ServerWebExchange exchange) {

			return handleAndSetTypeToExceptionName(ex, headers, status, exchange);
		}

		@Override
		protected Mono<ResponseEntity<Object>> handleUnsatisfiedRequestParameterException(
				UnsatisfiedRequestParameterException ex, HttpHeaders headers, HttpStatusCode status,
				ServerWebExchange exchange) {

			return handleAndSetTypeToExceptionName(ex, headers, status, exchange);
		}

		@Override
		protected Mono<ResponseEntity<Object>> handleWebExchangeBindException(
				WebExchangeBindException ex, HttpHeaders headers, HttpStatusCode status,
				ServerWebExchange exchange) {

			return handleAndSetTypeToExceptionName(ex, headers, status, exchange);
		}

		@Override
		protected Mono<ResponseEntity<Object>> handleServerWebInputException(
				ServerWebInputException ex, HttpHeaders headers, HttpStatusCode status,
				ServerWebExchange exchange) {

			return handleAndSetTypeToExceptionName(ex, headers, status, exchange);
		}

		@Override
		protected Mono<ResponseEntity<Object>> handleResponseStatusException(
				ResponseStatusException ex, HttpHeaders headers, HttpStatusCode status,
				ServerWebExchange exchange) {

			return handleAndSetTypeToExceptionName(ex, headers, status, exchange);
		}

		@Override
		protected Mono<ResponseEntity<Object>> handleServerErrorException(
				ServerErrorException ex, HttpHeaders headers, HttpStatusCode status,
				ServerWebExchange exchange) {

			return handleAndSetTypeToExceptionName(ex, headers, status, exchange);
		}

		@Override
		protected Mono<ResponseEntity<Object>> handleErrorResponseException(
				ErrorResponseException ex, HttpHeaders headers, HttpStatusCode status,
				ServerWebExchange exchange) {

			return handleAndSetTypeToExceptionName(ex, headers, status, exchange);
		}
	}

}
