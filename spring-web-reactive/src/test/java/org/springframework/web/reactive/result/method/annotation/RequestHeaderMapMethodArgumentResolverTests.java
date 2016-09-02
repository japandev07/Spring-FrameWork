/*
 * Copyright 2002-2016 the original author or authors.
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
import java.util.Collections;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import reactor.core.publisher.Mono;

import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.SynthesizingMethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.test.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.test.MockServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.adapter.DefaultServerWebExchange;
import org.springframework.web.server.session.MockWebSessionManager;
import org.springframework.web.server.session.WebSessionManager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for {@link RequestHeaderMapMethodArgumentResolver}.
 *
 * @author Rossen Stoyanchev
 */
public class RequestHeaderMapMethodArgumentResolverTests {

	private RequestHeaderMapMethodArgumentResolver resolver;

	private MethodParameter paramMap;
	private MethodParameter paramMultiValueMap;
	private MethodParameter paramHttpHeaders;
	private MethodParameter paramUnsupported;

	private ServerWebExchange exchange;


	@Before
	public void setUp() throws Exception {
		resolver = new RequestHeaderMapMethodArgumentResolver();

		Method method = getClass().getMethod("params", Map.class, MultiValueMap.class, HttpHeaders.class, Map.class);
		paramMap = new SynthesizingMethodParameter(method, 0);
		paramMultiValueMap = new SynthesizingMethodParameter(method, 1);
		paramHttpHeaders = new SynthesizingMethodParameter(method, 2);
		paramUnsupported = new SynthesizingMethodParameter(method, 3);

		ServerHttpRequest request = new MockServerHttpRequest(HttpMethod.GET, "/");
		WebSessionManager sessionManager = new MockWebSessionManager();
		this.exchange = new DefaultServerWebExchange(request, new MockServerHttpResponse(), sessionManager);
	}


	@Test
	public void supportsParameter() {
		assertTrue("Map parameter not supported", resolver.supportsParameter(paramMap));
		assertTrue("MultiValueMap parameter not supported", resolver.supportsParameter(paramMultiValueMap));
		assertTrue("HttpHeaders parameter not supported", resolver.supportsParameter(paramHttpHeaders));
		assertFalse("non-@RequestParam map supported", resolver.supportsParameter(paramUnsupported));
	}

	@Test
	public void resolveMapArgument() throws Exception {
		String name = "foo";
		String value = "bar";
		Map<String, String> expected = Collections.singletonMap(name, value);
		this.exchange.getRequest().getHeaders().add(name, value);

		Mono<Object> mono = this.resolver.resolveArgument(paramMap, null, this.exchange);
		Object result = mono.block();

		assertTrue(result instanceof Map);
		assertEquals("Invalid result", expected, result);
	}

	@Test
	public void resolveMultiValueMapArgument() throws Exception {
		String name = "foo";
		String value1 = "bar";
		String value2 = "baz";

		this.exchange.getRequest().getHeaders().add(name, value1);
		this.exchange.getRequest().getHeaders().add(name, value2);

		MultiValueMap<String, String> expected = new LinkedMultiValueMap<>(1);
		expected.add(name, value1);
		expected.add(name, value2);

		Mono<Object> mono = this.resolver.resolveArgument(paramMultiValueMap, null, this.exchange);
		Object result = mono.block();

		assertTrue(result instanceof MultiValueMap);
		assertEquals("Invalid result", expected, result);
	}

	@Test
	public void resolveHttpHeadersArgument() throws Exception {
		String name = "foo";
		String value1 = "bar";
		String value2 = "baz";

		this.exchange.getRequest().getHeaders().add(name, value1);
		this.exchange.getRequest().getHeaders().add(name, value2);

		HttpHeaders expected = new HttpHeaders();
		expected.add(name, value1);
		expected.add(name, value2);

		Mono<Object> mono = this.resolver.resolveArgument(paramHttpHeaders, null, this.exchange);
		Object result = mono.block();

		assertTrue(result instanceof HttpHeaders);
		assertEquals("Invalid result", expected, result);
	}


	@SuppressWarnings("unused")
	public void params(@RequestHeader Map<?, ?> param1,
					   @RequestHeader MultiValueMap<?, ?> param2,
					   @RequestHeader HttpHeaders param3,
					   Map<?,?> unsupported) {
	}

}
