/*
 * Copyright 2002-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.web.server.handler;


import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Before;
import org.junit.Test;
import reactor.core.publisher.Mono;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.mock.http.server.reactive.test.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.test.MockServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.server.WebHandler;
import org.springframework.web.server.adapter.WebHttpHandlerBuilder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * @author Rossen Stoyanchev
 */
public class FilteringWebHandlerTests {

	private static Log logger = LogFactory.getLog(FilteringWebHandlerTests.class);


	private MockServerHttpRequest request;

	private MockServerHttpResponse response;


	@Before
	public void setUp() throws Exception {
		this.request = new MockServerHttpRequest(HttpMethod.GET, "http://localhost");
		this.response = new MockServerHttpResponse();
	}

	@Test
	public void multipleFilters() throws Exception {
		StubWebHandler webHandler = new StubWebHandler();
		TestFilter filter1 = new TestFilter();
		TestFilter filter2 = new TestFilter();
		TestFilter filter3 = new TestFilter();
		HttpHandler httpHandler = createHttpHandler(webHandler, filter1, filter2, filter3);
		httpHandler.handle(this.request, this.response).block();

		assertTrue(filter1.invoked());
		assertTrue(filter2.invoked());
		assertTrue(filter3.invoked());
		assertTrue(webHandler.invoked());
	}

	@Test
	public void zeroFilters() throws Exception {
		StubWebHandler webHandler = new StubWebHandler();
		HttpHandler httpHandler = createHttpHandler(webHandler);
		httpHandler.handle(this.request, this.response).block();

		assertTrue(webHandler.invoked());
	}

	@Test
	public void shortcircuitFilter() throws Exception {
		StubWebHandler webHandler = new StubWebHandler();
		TestFilter filter1 = new TestFilter();
		ShortcircuitingFilter filter2 = new ShortcircuitingFilter();
		TestFilter filter3 = new TestFilter();
		HttpHandler httpHandler = createHttpHandler(webHandler, filter1, filter2, filter3);
		httpHandler.handle(this.request, this.response).block();

		assertTrue(filter1.invoked());
		assertTrue(filter2.invoked());
		assertFalse(filter3.invoked());
		assertFalse(webHandler.invoked());
	}

	@Test
	public void asyncFilter() throws Exception {
		StubWebHandler webHandler = new StubWebHandler();
		AsyncFilter filter = new AsyncFilter();
		HttpHandler httpHandler = createHttpHandler(webHandler, filter);
		httpHandler.handle(this.request, this.response).block();

		assertTrue(filter.invoked());
		assertTrue(webHandler.invoked());
	}

	@Test
	public void handleErrorFromFilter() throws Exception {
		TestExceptionHandler exceptionHandler = new TestExceptionHandler();
		HttpHandler handler = WebHttpHandlerBuilder.webHandler(new StubWebHandler())
				.filters(new ExceptionFilter()).exceptionHandlers(exceptionHandler).build();
		handler.handle(this.request, this.response).block();

		assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, this.response.getStatusCode());

		Throwable savedException = exceptionHandler.ex;
		assertNotNull(savedException);
		assertEquals("boo", savedException.getMessage());
	}

	private HttpHandler createHttpHandler(StubWebHandler webHandler, WebFilter... filters) {
		return WebHttpHandlerBuilder.webHandler(webHandler).filters(filters).build();
	}


	private static class TestFilter implements WebFilter {

		private volatile boolean invoked;

		public boolean invoked() {
			return this.invoked;
		}

		@Override
		public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
			this.invoked = true;
			return doFilter(exchange, chain);
		}

		public Mono<Void> doFilter(ServerWebExchange exchange, WebFilterChain chain) {
			return chain.filter(exchange);
		}
	}

	private static class ShortcircuitingFilter extends TestFilter {

		@Override
		public Mono<Void> doFilter(ServerWebExchange exchange, WebFilterChain chain) {
			return Mono.empty();
		}
	}

	private static class AsyncFilter extends TestFilter {

		@Override
		public Mono<Void> doFilter(ServerWebExchange exchange, WebFilterChain chain) {
			return doAsyncWork().then(asyncResult -> {
				logger.debug("Async result: " + asyncResult);
				return chain.filter(exchange);
			});
		}

		private Mono<String> doAsyncWork() {
			return Mono.just("123");
		}
	}

	private static class ExceptionFilter implements WebFilter {

		@Override
		public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
			return Mono.error(new IllegalStateException("boo"));
		}
	}

	private static class TestExceptionHandler implements WebExceptionHandler {

		private Throwable ex;

		@Override
		public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
			this.ex = ex;
			return Mono.error(ex);
		}
	}

	private static class StubWebHandler implements WebHandler {

		private volatile boolean invoked;

		public boolean invoked() {
			return this.invoked;
		}

		@Override
		public Mono<Void> handle(ServerWebExchange exchange) {
			logger.trace("StubHandler invoked.");
			this.invoked = true;
			return Mono.empty();
		}
	}

}
