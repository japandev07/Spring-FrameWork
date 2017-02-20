/*
 * Copyright 2002-2017 the original author or authors.
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

package org.springframework.web.reactive.result.method.annotation;

import java.lang.reflect.Method;

import org.junit.Before;
import org.junit.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.SynthesizingMethodParameter;
import org.springframework.http.HttpCookie;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.test.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.test.MockServerHttpResponse;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.reactive.BindingContext;
import org.springframework.web.server.ServerWebInputException;
import org.springframework.web.server.adapter.DefaultServerWebExchange;

import static org.junit.Assert.*;

/**
 * Test fixture with {@link CookieValueMethodArgumentResolver}.
 *
 * @author Rossen Stoyanchev
 */
public class CookieValueMethodArgumentResolverTests {

	private CookieValueMethodArgumentResolver resolver;

	private ServerHttpRequest request;

	private BindingContext bindingContext;

	private MethodParameter cookieParameter;
	private MethodParameter cookieStringParameter;
	private MethodParameter stringParameter;


	@Before
	public void setup() throws Exception {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
		context.refresh();

		this.resolver = new CookieValueMethodArgumentResolver(context.getBeanFactory());
		this.request = MockServerHttpRequest.get("/").build();
		this.bindingContext = new BindingContext();

		Method method = getClass().getMethod("params", HttpCookie.class, String.class, String.class);
		this.cookieParameter = new SynthesizingMethodParameter(method, 0);
		this.cookieStringParameter = new SynthesizingMethodParameter(method, 1);
		this.stringParameter = new SynthesizingMethodParameter(method, 2);
	}


	@Test
	public void supportsParameter() {
		assertTrue(this.resolver.supportsParameter(this.cookieParameter));
		assertTrue(this.resolver.supportsParameter(this.cookieStringParameter));
		assertFalse(this.resolver.supportsParameter(this.stringParameter));
	}

	@Test
	public void resolveCookieArgument() {
		HttpCookie expected = new HttpCookie("name", "foo");
		this.request = MockServerHttpRequest.get("/").cookie(expected.getName(), expected).build();

		Mono<Object> mono = this.resolver.resolveArgument(
				this.cookieParameter, this.bindingContext, createExchange());

		assertEquals(expected, mono.block());
	}

	@Test
	public void resolveCookieStringArgument() {
		HttpCookie cookie = new HttpCookie("name", "foo");
		this.request = MockServerHttpRequest.get("/").cookie(cookie.getName(), cookie).build();

		Mono<Object> mono = this.resolver.resolveArgument(
				this.cookieStringParameter, this.bindingContext, createExchange());

		assertEquals("Invalid result", cookie.getValue(), mono.block());
	}

	@Test
	public void resolveCookieDefaultValue() {
		Object result = this.resolver.resolveArgument(
				this.cookieStringParameter, this.bindingContext, createExchange()).block();

		assertTrue(result instanceof String);
		assertEquals("bar", result);
	}

	@Test
	public void notFound() {
		Mono<Object> mono = resolver.resolveArgument(this.cookieParameter, this.bindingContext, createExchange());
		StepVerifier.create(mono)
				.expectNextCount(0)
				.expectError(ServerWebInputException.class)
				.verify();
	}


	private DefaultServerWebExchange createExchange() {
		return new DefaultServerWebExchange(this.request, new MockServerHttpResponse());
	}


	@SuppressWarnings("unused")
	public void params(
			@CookieValue("name") HttpCookie cookie,
			@CookieValue(name = "name", defaultValue = "bar") String cookieString,
			String stringParam) {
	}

}
