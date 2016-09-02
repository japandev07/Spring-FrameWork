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
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import reactor.core.publisher.Mono;

import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.SynthesizingMethodParameter;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.test.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.test.MockServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.tests.TestSubscriber;
import org.springframework.ui.ModelMap;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.server.ServerErrorException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.adapter.DefaultServerWebExchange;
import org.springframework.web.server.session.MockWebSessionManager;
import org.springframework.web.server.session.WebSessionManager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for {@link PathVariableMethodArgumentResolver}.
 *
 * @author Rossen Stoyanchev
 * @author Juergen Hoeller
 */
public class PathVariableMethodArgumentResolverTests {

	private PathVariableMethodArgumentResolver resolver;

	private ServerWebExchange exchange;

	private MethodParameter paramNamedString;

	private MethodParameter paramString;

	private MethodParameter paramNotRequired;

	private MethodParameter paramOptional;


	@Before
	public void setUp() throws Exception {
		ConversionService conversionService = new DefaultConversionService();
		this.resolver = new PathVariableMethodArgumentResolver(conversionService, null);

		ServerHttpRequest request = new MockServerHttpRequest(HttpMethod.GET, "/");
		WebSessionManager sessionManager = new MockWebSessionManager();
		this.exchange = new DefaultServerWebExchange(request, new MockServerHttpResponse(), sessionManager);

		Method method = ReflectionUtils.findMethod(getClass(), "handle", (Class<?>[]) null);
		paramNamedString = new SynthesizingMethodParameter(method, 0);
		paramString = new SynthesizingMethodParameter(method, 1);
		paramNotRequired = new SynthesizingMethodParameter(method, 2);
		paramOptional = new SynthesizingMethodParameter(method, 3);
	}


	@Test
	public void supportsParameter() {
		assertTrue(this.resolver.supportsParameter(this.paramNamedString));
		assertFalse(this.resolver.supportsParameter(this.paramString));
	}

	@Test
	public void resolveArgument() throws Exception {
		Map<String, String> uriTemplateVars = new HashMap<>();
		uriTemplateVars.put("name", "value");
		this.exchange.getAttributes().put(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, uriTemplateVars);

		Mono<Object> mono = this.resolver.resolveArgument(this.paramNamedString, new ModelMap(), this.exchange);
		Object result = mono.block();
		assertEquals("value", result);
	}

	@Test
	public void resolveArgumentNotRequired() throws Exception {
		Map<String, String> uriTemplateVars = new HashMap<>();
		uriTemplateVars.put("name", "value");
		this.exchange.getAttributes().put(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, uriTemplateVars);

		Mono<Object> mono = this.resolver.resolveArgument(this.paramNotRequired, new ModelMap(), this.exchange);
		Object result = mono.block();
		assertEquals("value", result);
	}

	@Test
	public void resolveArgumentWrappedAsOptional() throws Exception {
		Map<String, String> uriTemplateVars = new HashMap<>();
		uriTemplateVars.put("name", "value");
		this.exchange.getAttributes().put(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, uriTemplateVars);

		Mono<Object> mono = this.resolver.resolveArgument(this.paramOptional, new ModelMap(), this.exchange);
		Object result = mono.block();
		assertEquals(Optional.of("value"), result);
	}

	@Test
	public void handleMissingValue() throws Exception {
		Mono<Object> mono = this.resolver.resolveArgument(this.paramNamedString, new ModelMap(), this.exchange);
		TestSubscriber
				.subscribe(mono)
				.assertError(ServerErrorException.class);
	}

	@Test
	public void nullIfNotRequired() throws Exception {
		Mono<Object> mono = this.resolver.resolveArgument(this.paramNotRequired, new ModelMap(), this.exchange);
		TestSubscriber
				.subscribe(mono)
				.assertComplete()
				.assertNoValues();
	}

	@Test
	public void wrapEmptyWithOptional() throws Exception {
		Mono<Object> mono = this.resolver.resolveArgument(this.paramOptional, new ModelMap(), this.exchange);
		Object result = mono.block();
		TestSubscriber
				.subscribe(mono)
				.assertValues(Optional.empty());
	}


	@SuppressWarnings("unused")
	public void handle(@PathVariable(value = "name") String param1, String param2,
			@PathVariable(name="name", required = false) String param3,
			@PathVariable("name") Optional<String> param4) {
	}

}
