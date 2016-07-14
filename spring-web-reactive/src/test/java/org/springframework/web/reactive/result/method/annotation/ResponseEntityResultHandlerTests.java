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

import java.net.URI;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.junit.Before;
import org.junit.Test;
import reactor.core.publisher.Mono;
import reactor.core.test.TestSubscriber;
import rx.Single;

import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.core.codec.ByteBufferEncoder;
import org.springframework.core.codec.StringEncoder;
import org.springframework.core.convert.support.MonoToCompletableFutureConverter;
import org.springframework.core.convert.support.ReactorToRxJava1Converter;
import org.springframework.core.io.buffer.support.DataBufferTestUtils;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.format.support.FormattingConversionService;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.json.JacksonJsonEncoder;
import org.springframework.http.codec.xml.Jaxb2Encoder;
import org.springframework.http.converter.reactive.CodecHttpMessageConverter;
import org.springframework.http.converter.reactive.HttpMessageConverter;
import org.springframework.http.converter.reactive.ResourceHttpMessageConverter;
import org.springframework.http.server.reactive.MockServerHttpRequest;
import org.springframework.http.server.reactive.MockServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.ObjectUtils;
import org.springframework.web.reactive.HandlerResult;
import org.springframework.web.reactive.accept.RequestedContentTypeResolver;
import org.springframework.web.reactive.accept.RequestedContentTypeResolverBuilder;
import org.springframework.web.reactive.result.ResolvableMethod;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.adapter.DefaultServerWebExchange;
import org.springframework.web.server.session.MockWebSessionManager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.core.ResolvableType.forClassWithGenerics;

/**
 * Unit tests for {@link ResponseEntityResultHandler}. When adding a test also
 * consider whether the logic under test is in a parent class, then see:
 * <ul>
 * 	<li>{@code MessageConverterResultHandlerTests},
 *  <li>{@code ContentNegotiatingResultHandlerSupportTests}
 * </ul>
 * @author Rossen Stoyanchev
 */
public class ResponseEntityResultHandlerTests {

	private ResponseEntityResultHandler resultHandler;

	private MockServerHttpResponse response = new MockServerHttpResponse();

	private ServerWebExchange exchange;


	@Before
	public void setUp() throws Exception {
		this.resultHandler = createHandler();
		ServerHttpRequest request = new MockServerHttpRequest(HttpMethod.GET, new URI("/path"));
		this.exchange = new DefaultServerWebExchange(request, this.response, new MockWebSessionManager());
	}

	private ResponseEntityResultHandler createHandler(HttpMessageConverter<?>... converters) {
		List<HttpMessageConverter<?>> converterList;
		if (ObjectUtils.isEmpty(converters)) {
			converterList = new ArrayList<>();
			converterList.add(new CodecHttpMessageConverter<>(new ByteBufferEncoder()));
			converterList.add(new CodecHttpMessageConverter<>(new StringEncoder()));
			converterList.add(new ResourceHttpMessageConverter());
			converterList.add(new CodecHttpMessageConverter<>(new Jaxb2Encoder()));
			converterList.add(new CodecHttpMessageConverter<>(new JacksonJsonEncoder()));
		}
		else {
			converterList = Arrays.asList(converters);
		}
		FormattingConversionService service = new DefaultFormattingConversionService();
		service.addConverter(new MonoToCompletableFutureConverter());
		service.addConverter(new ReactorToRxJava1Converter());


		RequestedContentTypeResolver resolver = new RequestedContentTypeResolverBuilder().build();

		return new ResponseEntityResultHandler(converterList, service, resolver);
	}


	@Test @SuppressWarnings("ConstantConditions")
	public void supports() throws NoSuchMethodException {

		Object value = null;
		ResolvableType type = responseEntity(String.class);
		assertTrue(this.resultHandler.supports(handlerResult(value, type)));

		type = forClassWithGenerics(Mono.class, responseEntity(String.class));
		assertTrue(this.resultHandler.supports(handlerResult(value, type)));

		type = forClassWithGenerics(Single.class, responseEntity(String.class));
		assertTrue(this.resultHandler.supports(handlerResult(value, type)));

		type = forClassWithGenerics(CompletableFuture.class, responseEntity(String.class));
		assertTrue(this.resultHandler.supports(handlerResult(value, type)));

		type = ResolvableType.forClass(String.class);
		assertFalse(this.resultHandler.supports(handlerResult(value, type)));
	}

	@Test
	public void defaultOrder() throws Exception {
		assertEquals(0, this.resultHandler.getOrder());
	}

	@Test
	public void statusCode() throws Exception {
		ResponseEntity<Void> value = ResponseEntity.noContent().build();
		ResolvableType type = responseEntity(Void.class);
		HandlerResult result = handlerResult(value, type);
		this.resultHandler.handleResult(exchange, result).block(Duration.ofSeconds(5));

		assertEquals(HttpStatus.NO_CONTENT, this.response.getStatusCode());
		assertEquals(0, this.response.getHeaders().size());
		assertNull(this.response.getBody());
	}

	@Test
	public void headers() throws Exception {
		URI location = new URI("/path");
		ResolvableType type = responseEntity(Void.class);
		ResponseEntity<Void> value = ResponseEntity.created(location).build();
		HandlerResult result = handlerResult(value, type);
		this.resultHandler.handleResult(this.exchange, result).block(Duration.ofSeconds(5));

		assertEquals(HttpStatus.CREATED, this.response.getStatusCode());
		assertEquals(1, this.response.getHeaders().size());
		assertEquals(location, this.response.getHeaders().getLocation());
		assertNull(this.response.getBody());
	}

	@Test
	public void handleReturnTypes() throws Exception {
		Object returnValue = ResponseEntity.ok("abc");
		ResolvableType returnType = responseEntity(String.class);
		testHandle(returnValue, returnType);

		returnValue = Mono.just(ResponseEntity.ok("abc"));
		returnType = forClassWithGenerics(Mono.class, responseEntity(String.class));
		testHandle(returnValue, returnType);

		returnValue = Mono.just(ResponseEntity.ok("abc"));
		returnType = forClassWithGenerics(Single.class, responseEntity(String.class));
		testHandle(returnValue, returnType);

		returnValue = Mono.just(ResponseEntity.ok("abc"));
		returnType = forClassWithGenerics(CompletableFuture.class, responseEntity(String.class));
		testHandle(returnValue, returnType);
	}


	private void testHandle(Object returnValue, ResolvableType type) {
		HandlerResult result = handlerResult(returnValue, type);
		this.resultHandler.handleResult(this.exchange, result).block(Duration.ofSeconds(5));

		assertEquals(HttpStatus.OK, this.response.getStatusCode());
		assertEquals("text/plain;charset=UTF-8", this.response.getHeaders().getFirst("Content-Type"));
		assertResponseBody("abc");
	}


	private ResolvableType responseEntity(Class<?> bodyType) {
		return forClassWithGenerics(ResponseEntity.class, ResolvableType.forClass(bodyType));
	}

	private HandlerResult handlerResult(Object returnValue, ResolvableType type) {
		MethodParameter param = ResolvableMethod.onClass(TestController.class).returning(type).resolveReturnType();
		return new HandlerResult(new TestController(), returnValue, param);
	}

	private void assertResponseBody(String responseBody) {
		TestSubscriber.subscribe(this.response.getBody())
				.assertValuesWith(buf -> assertEquals(responseBody,
						DataBufferTestUtils.dumpString(buf, Charset.forName("UTF-8"))));
	}


	@SuppressWarnings("unused")
	private static class TestController {

		ResponseEntity<String> responseEntityString() { return null; }

		ResponseEntity<Void> responseEntityVoid() { return null; }

		Mono<ResponseEntity<String>> mono() { return null; }

		Single<ResponseEntity<String>> single() { return null; }

		CompletableFuture<ResponseEntity<String>> completableFuture() { return null; }

		String string() { return null; }
	}

}
