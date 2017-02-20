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

package org.springframework.web.reactive.result.method;

import java.lang.reflect.Method;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import org.junit.Before;
import org.junit.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.test.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.test.MockServerHttpResponse;
import org.springframework.stereotype.Controller;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.reactive.BindingContext;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.HandlerResult;
import org.springframework.web.reactive.result.ResolvableMethod;
import org.springframework.web.reactive.result.method.RequestMappingInfo.*;
import org.springframework.web.server.MethodNotAllowedException;
import org.springframework.web.server.NotAcceptableStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;
import org.springframework.web.server.UnsupportedMediaTypeStatusException;
import org.springframework.web.server.adapter.DefaultServerWebExchange;
import org.springframework.web.server.support.HttpRequestPathHelper;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;
import static org.springframework.web.bind.annotation.RequestMethod.*;
import static org.springframework.web.reactive.result.method.RequestMappingInfo.*;

/**
 * Unit tests for {@link RequestMappingInfoHandlerMapping}.
 *
 * @author Rossen Stoyanchev
 */
public class RequestMappingInfoHandlerMappingTests {

	private TestRequestMappingInfoHandlerMapping handlerMapping;

	private ServerHttpRequest request;


	@Before
	public void setup() throws Exception {
		this.handlerMapping = new TestRequestMappingInfoHandlerMapping();
		this.handlerMapping.registerHandler(new TestController());
	}


	@Test
	public void getMappingPathPatterns() throws Exception {
		String[] patterns = {"/foo/*", "/foo", "/bar/*", "/bar"};
		RequestMappingInfo info = paths(patterns).build();
		Set<String> actual = this.handlerMapping.getMappingPathPatterns(info);

		assertEquals(new HashSet<>(Arrays.asList(patterns)), actual);
	}

	@Test
	public void getHandlerDirectMatch() throws Exception {
		String[] patterns = new String[] {"/foo"};
		String[] params = new String[] {};
		Method expected = resolveMethod(new TestController(), patterns, null, params);

		this.request = MockServerHttpRequest.get("/foo").build();
		HandlerMethod hm = (HandlerMethod) this.handlerMapping.getHandler(createExchange()).block();

		assertEquals(expected, hm.getMethod());
	}

	@Test
	public void getHandlerGlobMatch() throws Exception {
		String[] patterns = new String[] {"/ba*"};
		RequestMethod[] methods = new RequestMethod[] {GET, HEAD};
		Method expected = resolveMethod(new TestController(), patterns, methods, null);

		this.request = MockServerHttpRequest.get("/bar").build();
		HandlerMethod hm = (HandlerMethod) this.handlerMapping.getHandler(createExchange()).block();

		assertEquals(expected, hm.getMethod());
	}

	@Test
	public void getHandlerEmptyPathMatch() throws Exception {
		String[] patterns = new String[] {""};
		Method expected = resolveMethod(new TestController(), patterns, null, null);

		this.request = MockServerHttpRequest.get("").build();
		HandlerMethod hm = (HandlerMethod) this.handlerMapping.getHandler(createExchange()).block();
		assertEquals(expected, hm.getMethod());

		this.request = MockServerHttpRequest.get("/").build();
		hm = (HandlerMethod) this.handlerMapping.getHandler(createExchange()).block();
		assertEquals(expected, hm.getMethod());
	}

	@Test
	public void getHandlerBestMatch() throws Exception {
		String[] patterns = new String[] {"/foo"};
		String[] params = new String[] {"p"};
		Method expected = resolveMethod(new TestController(), patterns, null, params);

		this.request = MockServerHttpRequest.get("/foo?p=anything").build();
		HandlerMethod hm = (HandlerMethod) this.handlerMapping.getHandler(createExchange()).block();

		assertEquals(expected, hm.getMethod());
	}

	@Test
	public void getHandlerRequestMethodNotAllowed() throws Exception {
		this.request = MockServerHttpRequest.post("/bar").build();
		Mono<Object> mono = this.handlerMapping.getHandler(createExchange());

		assertError(mono, MethodNotAllowedException.class,
				ex -> assertEquals(new HashSet<>(Arrays.asList("GET", "HEAD")), ex.getSupportedMethods()));
	}

	@Test  // SPR-9603
	public void getHandlerRequestMethodMatchFalsePositive() throws Exception {
		this.request = MockServerHttpRequest.get("/users").accept(MediaType.APPLICATION_XML).build();
		this.handlerMapping.registerHandler(new UserController());
		Mono<Object> mono = this.handlerMapping.getHandler(createExchange());

		StepVerifier.create(mono)
				.expectError(NotAcceptableStatusException.class)
				.verify();
	}

	@Test  // SPR-8462
	public void getHandlerMediaTypeNotSupported() throws Exception {
		testHttpMediaTypeNotSupportedException("/person/1");
		testHttpMediaTypeNotSupportedException("/person/1/");
		testHttpMediaTypeNotSupportedException("/person/1.json");
	}

	@Test
	public void getHandlerTestInvalidContentType() throws Exception {
		this.request = MockServerHttpRequest.put("/person/1").header("content-type", "bogus").build();
		Mono<Object> mono = this.handlerMapping.getHandler(createExchange());

		assertError(mono, UnsupportedMediaTypeStatusException.class,
				ex -> assertEquals("Request failure [status: 415, " +
						"reason: \"Invalid mime type \"bogus\": does not contain '/'\"]",
						ex.getMessage()));
	}

	@Test  // SPR-8462
	public void getHandlerTestMediaTypeNotAcceptable() throws Exception {
		testMediaTypeNotAcceptable("/persons");
		testMediaTypeNotAcceptable("/persons/");
		testMediaTypeNotAcceptable("/persons.json");
	}

	@Test  // SPR-12854
	public void getHandlerTestRequestParamMismatch() throws Exception {
		this.request = MockServerHttpRequest.get("/params").build();
		Mono<Object> mono = this.handlerMapping.getHandler(createExchange());
		assertError(mono, ServerWebInputException.class, ex -> {
			assertThat(ex.getReason(), containsString("[foo=bar]"));
			assertThat(ex.getReason(), containsString("[bar=baz]"));
		});
	}

	@Test
	public void getHandlerHttpOptions() throws Exception {
		testHttpOptions("/foo", "GET,HEAD");
		testHttpOptions("/person/1", "PUT");
		testHttpOptions("/persons", "GET,HEAD,POST,PUT,PATCH,DELETE,OPTIONS");
		testHttpOptions("/something", "PUT,POST");
	}

	@Test
	public void getHandlerProducibleMediaTypesAttribute() throws Exception {
		this.request = MockServerHttpRequest.get("/content").accept(MediaType.APPLICATION_XML).build();
		ServerWebExchange exchange = createExchange();
		this.handlerMapping.getHandler(exchange).block();

		String name = HandlerMapping.PRODUCIBLE_MEDIA_TYPES_ATTRIBUTE;
		assertEquals(Collections.singleton(MediaType.APPLICATION_XML), exchange.getAttributes().get(name));

		this.request = MockServerHttpRequest.get("/content").accept(MediaType.APPLICATION_JSON).build();
		exchange = createExchange();
		this.handlerMapping.getHandler(exchange).block();

		assertNull("Negated expression shouldn't be listed as producible type",
				exchange.getAttributes().get(name));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void handleMatchUriTemplateVariables() throws Exception {
		String lookupPath = "/1/2";
		this.request = MockServerHttpRequest.get(lookupPath).build();
		ServerWebExchange exchange = createExchange();

		RequestMappingInfo key = paths("/{path1}/{path2}").build();
		this.handlerMapping.handleMatch(key, lookupPath, exchange);

		String name = HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE;
		Map<String, String> uriVariables = (Map<String, String>) exchange.getAttributes().get(name);

		assertNotNull(uriVariables);
		assertEquals("1", uriVariables.get("path1"));
		assertEquals("2", uriVariables.get("path2"));
	}

	@Test  // SPR-9098
	public void handleMatchUriTemplateVariablesDecode() throws Exception {
		RequestMappingInfo key = paths("/{group}/{identifier}").build();
		this.request = MockServerHttpRequest.method(HttpMethod.GET, URI.create("/group/a%2Fb")).build();
		ServerWebExchange exchange = createExchange();

		HttpRequestPathHelper pathHelper = new HttpRequestPathHelper();
		pathHelper.setUrlDecode(false);
		String lookupPath = pathHelper.getLookupPathForRequest(exchange);

		this.handlerMapping.setPathHelper(pathHelper);
		this.handlerMapping.handleMatch(key, lookupPath, exchange);

		String name = HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE;
		@SuppressWarnings("unchecked")
		Map<String, String> uriVariables = (Map<String, String>) exchange.getAttributes().get(name);

		assertNotNull(uriVariables);
		assertEquals("group", uriVariables.get("group"));
		assertEquals("a/b", uriVariables.get("identifier"));
	}

	@Test
	public void handleMatchBestMatchingPatternAttribute() throws Exception {
		RequestMappingInfo key = paths("/{path1}/2", "/**").build();
		this.request = MockServerHttpRequest.get("/1/2").build();
		ServerWebExchange exchange = createExchange();
		this.handlerMapping.handleMatch(key, "/1/2", exchange);

		assertEquals("/{path1}/2", exchange.getAttributes().get(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE));
	}

	@Test
	public void handleMatchBestMatchingPatternAttributeNoPatternsDefined() throws Exception {
		RequestMappingInfo key = paths().build();
		this.request = MockServerHttpRequest.get("/1/2").build();
		ServerWebExchange exchange = createExchange();

		this.handlerMapping.handleMatch(key, "/1/2", exchange);

		assertEquals("/1/2", exchange.getAttributes().get(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE));
	}

	@Test
	public void handleMatchMatrixVariables() throws Exception {
		ServerWebExchange exchange;
		MultiValueMap<String, String> matrixVariables;
		Map<String, String> uriVariables;

		this.request = MockServerHttpRequest.get("/").build();
		exchange = createExchange();
		handleMatch(exchange, "/{cars}", "/cars;colors=red,blue,green;year=2012");

		matrixVariables = getMatrixVariables(exchange, "cars");
		uriVariables = getUriTemplateVariables(exchange);

		assertNotNull(matrixVariables);
		assertEquals(Arrays.asList("red", "blue", "green"), matrixVariables.get("colors"));
		assertEquals("2012", matrixVariables.getFirst("year"));
		assertEquals("cars", uriVariables.get("cars"));

		this.request = MockServerHttpRequest.get("/").build();
		exchange = createExchange();
		handleMatch(exchange, "/{cars:[^;]+}{params}", "/cars;colors=red,blue,green;year=2012");

		matrixVariables = getMatrixVariables(exchange, "params");
		uriVariables = getUriTemplateVariables(exchange);

		assertNotNull(matrixVariables);
		assertEquals(Arrays.asList("red", "blue", "green"), matrixVariables.get("colors"));
		assertEquals("2012", matrixVariables.getFirst("year"));
		assertEquals("cars", uriVariables.get("cars"));
		assertEquals(";colors=red,blue,green;year=2012", uriVariables.get("params"));

		this.request = MockServerHttpRequest.get("/").build();
		exchange = createExchange();
		handleMatch(exchange, "/{cars:[^;]+}{params}", "/cars");

		matrixVariables = getMatrixVariables(exchange, "params");
		uriVariables = getUriTemplateVariables(exchange);

		assertNull(matrixVariables);
		assertEquals("cars", uriVariables.get("cars"));
		assertEquals("", uriVariables.get("params"));
	}

	@Test
	public void handleMatchMatrixVariablesDecoding() throws Exception {
		HttpRequestPathHelper urlPathHelper = new HttpRequestPathHelper();
		urlPathHelper.setUrlDecode(false);
		this.handlerMapping.setPathHelper(urlPathHelper);

		this.request = MockServerHttpRequest.get("/").build();
		ServerWebExchange exchange = createExchange();
		handleMatch(exchange, "/path{filter}", "/path;mvar=a%2fb");

		MultiValueMap<String, String> matrixVariables = getMatrixVariables(exchange, "filter");
		Map<String, String> uriVariables = getUriTemplateVariables(exchange);

		assertNotNull(matrixVariables);
		assertEquals(Collections.singletonList("a/b"), matrixVariables.get("mvar"));
		assertEquals(";mvar=a/b", uriVariables.get("filter"));
	}


	private ServerWebExchange createExchange() {
		return new DefaultServerWebExchange(this.request, new MockServerHttpResponse());
	}

	@SuppressWarnings("unchecked")
	private <T> void assertError(Mono<Object> mono, final Class<T> exceptionClass, final Consumer<T> consumer)  {
		StepVerifier.create(mono)
				.consumeErrorWith(error -> {
					assertEquals(exceptionClass, error.getClass());
					consumer.accept((T) error);

				})
				.verify();
	}

	private void testHttpMediaTypeNotSupportedException(String url) throws Exception {
		this.request = MockServerHttpRequest.put(url).contentType(MediaType.APPLICATION_JSON).build();
		Mono<Object> mono = this.handlerMapping.getHandler(createExchange());

		assertError(mono, UnsupportedMediaTypeStatusException.class, ex ->
				assertEquals("Invalid supported consumable media types",
						Collections.singletonList(new MediaType("application", "xml")),
						ex.getSupportedMediaTypes()));
	}

	private void testHttpOptions(String requestURI, String allowHeader) throws Exception {
		this.request = MockServerHttpRequest.options(requestURI).build();
		ServerWebExchange exchange = createExchange();
		HandlerMethod handlerMethod = (HandlerMethod) this.handlerMapping.getHandler(createExchange()).block();

		BindingContext bindingContext = new BindingContext();
		InvocableHandlerMethod invocable = new InvocableHandlerMethod(handlerMethod);
		Mono<HandlerResult> mono = invocable.invoke(exchange, bindingContext);

		HandlerResult result = mono.block();
		assertNotNull(result);

		Optional<Object> value = result.getReturnValue();
		assertTrue(value.isPresent());
		assertEquals(HttpHeaders.class, value.get().getClass());
		assertEquals(allowHeader, ((HttpHeaders) value.get()).getFirst("Allow"));
	}

	private void testMediaTypeNotAcceptable(String url) throws Exception {
		this.request = MockServerHttpRequest.get(url).accept(MediaType.APPLICATION_JSON).build();
		Mono<Object> mono = this.handlerMapping.getHandler(createExchange());

		assertError(mono, NotAcceptableStatusException.class, ex ->
				assertEquals("Invalid supported producible media types",
						Collections.singletonList(new MediaType("application", "xml")),
						ex.getSupportedMediaTypes()));
	}

	private void handleMatch(ServerWebExchange exchange, String pattern, String lookupPath) {
		RequestMappingInfo info = paths(pattern).build();
		this.handlerMapping.handleMatch(info, lookupPath, exchange);
	}

	@SuppressWarnings("unchecked")
	private MultiValueMap<String, String> getMatrixVariables(ServerWebExchange exchange, String uriVarName) {
		String attrName = HandlerMapping.MATRIX_VARIABLES_ATTRIBUTE;
		return ((Map<String, MultiValueMap<String, String>>) exchange.getAttributes().get(attrName)).get(uriVarName);
	}

	@SuppressWarnings("unchecked")
	private Map<String, String> getUriTemplateVariables(ServerWebExchange exchange) {
		String attrName = HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE;
		return (Map<String, String>) exchange.getAttributes().get(attrName);
	}

	private Method resolveMethod(Object controller, String[] patterns,
			RequestMethod[] methods, String[] params) {

		return ResolvableMethod.on(controller)
				.matching(method -> {
					RequestMapping annot = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
					if (annot == null) {
						return false;
					}
					else if (patterns != null && !Arrays.equals(annot.path(), patterns)) {
						return false;
					}
					else if (methods != null && !Arrays.equals(annot.method(), methods)) {
						return false;
					}
					else if (params != null && (!Arrays.equals(annot.params(), params))) {
						return false;
					}
					return true;
				})
				.resolve();
	}


	@SuppressWarnings("unused")
	@Controller
	private static class TestController {

		@GetMapping("/foo")
		public void foo() {
		}

		@GetMapping(path = "/foo", params="p")
		public void fooParam() {
		}

		@RequestMapping(path = "/ba*", method = { GET, HEAD })
		public void bar() {
		}

		@RequestMapping(path = "")
		public void empty() {
		}

		@PutMapping(path = "/person/{id}", consumes="application/xml")
		public void consumes(@RequestBody String text) {
		}

		@RequestMapping(path = "/persons", produces="application/xml")
		public String produces() {
			return "";
		}

		@RequestMapping(path = "/params", params="foo=bar")
		public String param() {
			return "";
		}

		@RequestMapping(path = "/params", params="bar=baz")
		public String param2() {
			return "";
		}

		@RequestMapping(path = "/content", produces="application/xml")
		public String xmlContent() {
			return "";
		}

		@RequestMapping(path = "/content", produces="!application/xml")
		public String nonXmlContent() {
			return "";
		}

		@RequestMapping(path = "/something", method = OPTIONS)
		public HttpHeaders fooOptions() {
			HttpHeaders headers = new HttpHeaders();
			headers.add("Allow", "PUT,POST");
			return headers;
		}
	}


	@SuppressWarnings("unused")
	@Controller
	private static class UserController {

		@GetMapping(path = "/users", produces = "application/json")
		public void getUser() {
		}

		@PutMapping(path = "/users")
		public void saveUser() {
		}
	}


	private static class TestRequestMappingInfoHandlerMapping extends RequestMappingInfoHandlerMapping {

		void registerHandler(Object handler) {
			super.detectHandlerMethods(handler);
		}

		@Override
		protected boolean isHandler(Class<?> beanType) {
			return AnnotationUtils.findAnnotation(beanType, RequestMapping.class) != null;
		}

		@Override
		protected RequestMappingInfo getMappingForMethod(Method method, Class<?> handlerType) {
			RequestMapping annot = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
			if (annot != null) {
				BuilderConfiguration options = new BuilderConfiguration();
				options.setPathHelper(getPathHelper());
				options.setPathMatcher(getPathMatcher());
				options.setSuffixPatternMatch(true);
				options.setTrailingSlashMatch(true);
				return paths(annot.value()).methods(annot.method())
						.params(annot.params()).headers(annot.headers())
						.consumes(annot.consumes()).produces(annot.produces())
						.options(options).build();
			}
			else {
				return null;
			}
		}
	}

}