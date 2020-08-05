/*
 * Copyright 2002-2020 the original author or authors.
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

package org.springframework.web.servlet.mvc.method.annotation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.core.SingleEmitter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoProcessor;
import reactor.core.publisher.Sinks;

import org.springframework.core.MethodParameter;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.core.ResolvableType;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.accept.ContentNegotiationManager;
import org.springframework.web.accept.ContentNegotiationManagerFactoryBean;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.async.AsyncWebRequest;
import org.springframework.web.context.request.async.StandardServletAsyncWebRequest;
import org.springframework.web.context.request.async.WebAsyncUtils;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.testfixture.servlet.MockHttpServletRequest;
import org.springframework.web.testfixture.servlet.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.core.ResolvableType.forClass;
import static org.springframework.web.testfixture.method.ResolvableMethod.on;

/**
 * Unit tests for {@link ReactiveTypeHandler}.
 * @author Rossen Stoyanchev
 */
public class ReactiveTypeHandlerTests {

	private ReactiveTypeHandler handler;

	private MockHttpServletRequest servletRequest;

	private MockHttpServletResponse servletResponse;

	private NativeWebRequest webRequest;


	@BeforeEach
	public void setup() throws Exception {
		ContentNegotiationManagerFactoryBean factoryBean = new ContentNegotiationManagerFactoryBean();
		factoryBean.afterPropertiesSet();
		ContentNegotiationManager manager = factoryBean.getObject();
		ReactiveAdapterRegistry adapterRegistry = ReactiveAdapterRegistry.getSharedInstance();
		this.handler = new ReactiveTypeHandler(adapterRegistry, new SyncTaskExecutor(), manager);
		resetRequest();
	}

	private void resetRequest() {
		this.servletRequest = new MockHttpServletRequest();
		this.servletResponse = new MockHttpServletResponse();
		this.webRequest = new ServletWebRequest(this.servletRequest, this.servletResponse);

		AsyncWebRequest webRequest = new StandardServletAsyncWebRequest(this.servletRequest, this.servletResponse);
		WebAsyncUtils.getAsyncManager(this.webRequest).setAsyncWebRequest(webRequest);
		this.servletRequest.setAsyncSupported(true);
	}


	@Test
	public void supportsType() throws Exception {
		assertThat(this.handler.isReactiveType(Mono.class)).isTrue();
		assertThat(this.handler.isReactiveType(Single.class)).isTrue();
	}

	@Test
	public void doesNotSupportType() throws Exception {
		assertThat(this.handler.isReactiveType(String.class)).isFalse();
	}

	@Test
	public void deferredResultSubscriberWithOneValue() throws Exception {

		// Mono
		MonoProcessor<String> mono = MonoProcessor.create();
		testDeferredResultSubscriber(mono, Mono.class, forClass(String.class), () -> mono.onNext("foo"), "foo");

		// Mono empty
		MonoProcessor<String> monoEmpty = MonoProcessor.create();
		testDeferredResultSubscriber(monoEmpty, Mono.class, forClass(String.class), monoEmpty::onComplete, null);

		// RxJava Single
		AtomicReference<SingleEmitter<String>> ref2 = new AtomicReference<>();
		Single<String> single2 = Single.create(ref2::set);
		testDeferredResultSubscriber(single2, Single.class, forClass(String.class),
				() -> ref2.get().onSuccess("foo"), "foo");
	}

	@Test
	public void deferredResultSubscriberWithNoValues() throws Exception {
		MonoProcessor<String> monoEmpty = MonoProcessor.create();
		testDeferredResultSubscriber(monoEmpty, Mono.class, forClass(String.class), monoEmpty::onComplete, null);
	}

	@Test
	public void deferredResultSubscriberWithMultipleValues() throws Exception {

		// JSON must be preferred for Flux<String> -> List<String> or else we stream
		this.servletRequest.addHeader("Accept", "application/json");

		Bar bar1 = new Bar("foo");
		Bar bar2 = new Bar("bar");

		Sinks.Many<Bar> sink = Sinks.many().unicast().onBackpressureBuffer();
		testDeferredResultSubscriber(sink.asFlux(), Flux.class, forClass(Bar.class), () -> {
			sink.emitNext(bar1);
			sink.emitNext(bar2);
			sink.emitComplete();
		}, Arrays.asList(bar1, bar2));
	}

	@Test
	public void deferredResultSubscriberWithError() throws Exception {

		IllegalStateException ex = new IllegalStateException();

		// Mono
		MonoProcessor<String> mono = MonoProcessor.create();
		testDeferredResultSubscriber(mono, Mono.class, forClass(String.class), () -> mono.onError(ex), ex);

		// RxJava Single
		AtomicReference<SingleEmitter<String>> ref2 = new AtomicReference<>();
		Single<String> single2 = Single.create(ref2::set);
		testDeferredResultSubscriber(single2, Single.class, forClass(String.class),
				() -> ref2.get().onError(ex), ex);
	}

	@Test
	public void mediaTypes() throws Exception {

		// Media type from request
		this.servletRequest.addHeader("Accept", "text/event-stream");
		testSseResponse(true);

		// Media type from "produces" attribute
		Set<MediaType> types = Collections.singleton(MediaType.TEXT_EVENT_STREAM);
		this.servletRequest.setAttribute(HandlerMapping.PRODUCIBLE_MEDIA_TYPES_ATTRIBUTE, types);
		testSseResponse(true);

		// No media type preferences
		testSseResponse(false);
	}

	private void testSseResponse(boolean expectSseEmitter) throws Exception {
		ResponseBodyEmitter emitter = handleValue(Flux.empty(), Flux.class, forClass(String.class));
		Object actual = emitter instanceof SseEmitter;
		assertThat(actual).isEqualTo(expectSseEmitter);
		resetRequest();
	}

	@Test
	public void writeServerSentEvents() throws Exception {

		this.servletRequest.addHeader("Accept", "text/event-stream");
		Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();
		SseEmitter sseEmitter = (SseEmitter) handleValue(sink.asFlux(), Flux.class, forClass(String.class));

		EmitterHandler emitterHandler = new EmitterHandler();
		sseEmitter.initialize(emitterHandler);

		sink.emitNext("foo");
		sink.emitNext("bar");
		sink.emitNext("baz");
		sink.emitComplete();

		assertThat(emitterHandler.getValuesAsText()).isEqualTo("data:foo\n\ndata:bar\n\ndata:baz\n\n");
	}

	@Test
	public void writeServerSentEventsWithBuilder() throws Exception {

		ResolvableType type = ResolvableType.forClassWithGenerics(ServerSentEvent.class, String.class);

		Sinks.Many<ServerSentEvent<?>> sink = Sinks.many().unicast().onBackpressureBuffer();
		SseEmitter sseEmitter = (SseEmitter) handleValue(sink.asFlux(), Flux.class, type);

		EmitterHandler emitterHandler = new EmitterHandler();
		sseEmitter.initialize(emitterHandler);

		sink.emitNext(ServerSentEvent.builder("foo").id("1").build());
		sink.emitNext(ServerSentEvent.builder("bar").id("2").build());
		sink.emitNext(ServerSentEvent.builder("baz").id("3").build());
		sink.emitComplete();

		assertThat(emitterHandler.getValuesAsText()).isEqualTo("id:1\ndata:foo\n\nid:2\ndata:bar\n\nid:3\ndata:baz\n\n");
	}

	@Test
	public void writeStreamJson() throws Exception {

		this.servletRequest.addHeader("Accept", "application/x-ndjson");

		Sinks.Many<Bar> sink = Sinks.many().unicast().onBackpressureBuffer();
		ResponseBodyEmitter emitter = handleValue(sink.asFlux(), Flux.class, forClass(Bar.class));

		EmitterHandler emitterHandler = new EmitterHandler();
		emitter.initialize(emitterHandler);

		ServletServerHttpResponse message = new ServletServerHttpResponse(this.servletResponse);
		emitter.extendResponse(message);

		Bar bar1 = new Bar("foo");
		Bar bar2 = new Bar("bar");

		sink.emitNext(bar1);
		sink.emitNext(bar2);
		sink.emitComplete();

		assertThat(message.getHeaders().getContentType().toString()).isEqualTo("application/x-ndjson");
		assertThat(emitterHandler.getValues()).isEqualTo(Arrays.asList(bar1, "\n", bar2, "\n"));
	}

	@Test
	public void writeText() throws Exception {

		Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();
		ResponseBodyEmitter emitter = handleValue(sink.asFlux(), Flux.class, forClass(String.class));

		EmitterHandler emitterHandler = new EmitterHandler();
		emitter.initialize(emitterHandler);

		sink.emitNext("The quick");
		sink.emitNext(" brown fox jumps over ");
		sink.emitNext("the lazy dog");
		sink.emitComplete();

		assertThat(emitterHandler.getValuesAsText()).isEqualTo("The quick brown fox jumps over the lazy dog");
	}

	@Test
	public void writeFluxOfString() throws Exception {

		// Default to "text/plain"
		testEmitterContentType("text/plain");

		// Same if no concrete media type
		this.servletRequest.addHeader("Accept", "text/*");
		testEmitterContentType("text/plain");

		// Otherwise pick concrete media type
		this.servletRequest.addHeader("Accept", "*/*, text/*, text/markdown");
		testEmitterContentType("text/markdown");

		// Any concrete media type
		this.servletRequest.addHeader("Accept", "*/*, text/*, foo/bar");
		testEmitterContentType("foo/bar");

		// Including json
		this.servletRequest.addHeader("Accept", "*/*, text/*, application/json");
		testEmitterContentType("application/json");
	}

	private void testEmitterContentType(String expected) throws Exception {
		ServletServerHttpResponse message = new ServletServerHttpResponse(this.servletResponse);
		ResponseBodyEmitter emitter = handleValue(Flux.empty(), Flux.class, forClass(String.class));
		emitter.extendResponse(message);
		assertThat(message.getHeaders().getContentType().toString()).isEqualTo(expected);
		resetRequest();
	}


	private void testDeferredResultSubscriber(Object returnValue, Class<?> asyncType,
			ResolvableType elementType, Runnable produceTask, Object expected) throws Exception {

		ResponseBodyEmitter emitter = handleValue(returnValue, asyncType, elementType);
		assertThat(emitter).isNull();

		assertThat(this.servletRequest.isAsyncStarted()).isTrue();
		assertThat(WebAsyncUtils.getAsyncManager(this.webRequest).hasConcurrentResult()).isFalse();

		produceTask.run();

		assertThat(WebAsyncUtils.getAsyncManager(this.webRequest).hasConcurrentResult()).isTrue();
		assertThat(WebAsyncUtils.getAsyncManager(this.webRequest).getConcurrentResult()).isEqualTo(expected);

		resetRequest();
	}

	private ResponseBodyEmitter handleValue(Object returnValue, Class<?> asyncType,
			ResolvableType genericType) throws Exception {

		ModelAndViewContainer mavContainer = new ModelAndViewContainer();
		MethodParameter returnType = on(TestController.class).resolveReturnType(asyncType, genericType);
		return this.handler.handleValue(returnValue, returnType, mavContainer, this.webRequest);
	}


	@SuppressWarnings("unused")
	static class TestController {

		String handleString() { return null; }

		Mono<String> handleMono() { return null; }

		Single<String> handleSingle() { return null; }

		Flux<Bar> handleFlux() { return null; }

		Flux<String> handleFluxString() { return null; }

		Flux<ServerSentEvent<String>> handleFluxSseEventBuilder() { return null; }
	}


	private static class EmitterHandler implements ResponseBodyEmitter.Handler {

		private final List<Object> values = new ArrayList<>();


		public List<?> getValues() {
			return this.values;
		}

		public String getValuesAsText() {
			return this.values.stream().map(Object::toString).collect(Collectors.joining());
		}

		@Override
		public void send(Object data, MediaType mediaType) throws IOException {
			this.values.add(data);
		}

		@Override
		public void complete() {
		}

		@Override
		public void completeWithError(Throwable failure) {
		}

		@Override
		public void onTimeout(Runnable callback) {
		}

		@Override
		public void onError(Consumer<Throwable> callback) {
		}

		@Override
		public void onCompletion(Runnable callback) {
		}
	}

	private static class Bar {

		private final String value;

		public Bar(String value) {
			this.value = value;
		}

		@SuppressWarnings("unused")
		public String getValue() {
			return this.value;
		}
	}

}
