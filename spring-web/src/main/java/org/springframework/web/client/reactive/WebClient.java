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

package org.springframework.web.client.reactive;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;

import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import org.springframework.core.ResolvableType;
import org.springframework.core.codec.ByteBufferDecoder;
import org.springframework.core.codec.ByteBufferEncoder;
import org.springframework.core.codec.CharSequenceEncoder;
import org.springframework.core.codec.ResourceDecoder;
import org.springframework.core.codec.StringDecoder;
import org.springframework.http.HttpMessage;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.http.client.reactive.ClientHttpRequest;
import org.springframework.http.client.reactive.ClientHttpResponse;
import org.springframework.http.codec.DecoderHttpMessageReader;
import org.springframework.http.codec.EncoderHttpMessageWriter;
import org.springframework.http.codec.HttpMessageReader;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.http.codec.ResourceHttpMessageWriter;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.http.codec.xml.Jaxb2XmlDecoder;
import org.springframework.http.codec.xml.Jaxb2XmlEncoder;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

/**
 * Reactive Web client supporting the HTTP/1.1 protocol
 *
 * <p>Here is a simple example of a GET request:
 *
 * <pre class="code">
 * static imports: ClientWebRequestBuilder.*, ResponseExtractors.*
 *
 * // should be shared between HTTP calls
 * WebClient client = new WebClient(new ReactorClientHttpConnector());
 *
 * Mono&lt;String&gt; result = client
 * 		.perform(get("http://example.org/resource").accept(MediaType.TEXT_PLAIN))
 * 		.extract(body(String.class));
 * </pre>
 *
 * <p>This Web client relies on the following:
 * <ul>
 * <li>{@link ClientHttpConnector} implementation to drive the underlying
 * library (e.g. Reactor-Netty)</li>
 * <li>{@link ClientWebRequestBuilder} to create a Web request with a builder
 * API (see {@link ClientWebRequestBuilders})</li>
 * <li>{@link ResponseExtractor} to extract the relevant part of the server
 * response with the composition API of choice (see {@link ResponseExtractors}</li>
 * </ul>
 *
 * @author Brian Clozel
 * @since 5.0
 * @see ClientWebRequestBuilders
 * @see ResponseExtractors
 */
public final class WebClient {

	private static final boolean jackson2Present =
			ClassUtils.isPresent("com.fasterxml.jackson.databind.ObjectMapper", WebClient.class.getClassLoader()) &&
			ClassUtils.isPresent("com.fasterxml.jackson.core.JsonGenerator", WebClient.class.getClassLoader());

	private static final boolean jaxb2Present =
			ClassUtils.isPresent("javax.xml.bind.Binder", WebClient.class.getClassLoader());


	private ClientHttpConnector clientHttpConnector;

	private List<ClientHttpRequestInterceptor> interceptors;

	private final DefaultWebClientConfig webClientConfig;


	/**
	 * Create a {@code WebClient} instance, using the {@link ClientHttpConnector}
	 * implementation given as an argument to drive the underlying
	 * implementation.
	 * Register by default the following Encoders and Decoders:
	 * <ul>
	 * <li>{@link ByteBufferEncoder} / {@link ByteBufferDecoder}</li>
	 * <li>{@link CharSequenceEncoder} / {@link StringDecoder}</li>
	 * <li>{@link Jaxb2XmlEncoder} / {@link Jaxb2XmlDecoder}</li>
	 * <li>{@link Jackson2JsonEncoder} / {@link Jackson2JsonDecoder}</li>
	 * </ul>
	 * @param clientHttpConnector the {@code ClientHttpRequestFactory} to use
	 */
	public WebClient(ClientHttpConnector clientHttpConnector) {
		this.clientHttpConnector = clientHttpConnector;
		this.webClientConfig = new DefaultWebClientConfig();
		this.webClientConfig.setResponseErrorHandler(new DefaultResponseErrorHandler());
	}


	/**
	 * Add default HTTP message readers.
	 */
	protected final void addDefaultHttpMessageReaders(List<HttpMessageReader<?>> messageReaders) {
		messageReaders.add(new DecoderHttpMessageReader<>(new ByteBufferDecoder()));
		messageReaders.add(new DecoderHttpMessageReader<>(new StringDecoder(false)));
		messageReaders.add(new DecoderHttpMessageReader<>(new ResourceDecoder()));
		if (jaxb2Present) {
			messageReaders.add(new DecoderHttpMessageReader<>(new Jaxb2XmlDecoder()));
		}
		if (jackson2Present) {
			messageReaders.add(new DecoderHttpMessageReader<>(new Jackson2JsonDecoder()));
		}
	}

	/**
	 * Add default HTTP message writers.
	 */
	protected final void addDefaultHttpMessageWriters(List<HttpMessageWriter<?>> messageWriters) {
		messageWriters.add(new EncoderHttpMessageWriter<>(new ByteBufferEncoder()));
		messageWriters.add(new EncoderHttpMessageWriter<>(new CharSequenceEncoder()));
		messageWriters.add(new ResourceHttpMessageWriter());
		if (jaxb2Present) {
			messageWriters.add(new EncoderHttpMessageWriter<>(new Jaxb2XmlEncoder()));
		}
		if (jackson2Present) {
			messageWriters.add(new EncoderHttpMessageWriter<>(new Jackson2JsonEncoder()));
		}
	}


	/**
	 * Set the list of {@link HttpMessageReader}s to use for decoding the HTTP
	 * response body.
	 */
	public void setMessageReaders(List<HttpMessageReader<?>> messageReaders) {
		this.webClientConfig.setMessageReaders(messageReaders);
	}

	/**
	 * Set the list of {@link HttpMessageWriter}s to use for encoding the HTTP
	 * request body.
	 */
	public void setMessageWriters(List<HttpMessageWriter<?>> messageWrters) {
		this.webClientConfig.setMessageWriters(messageWrters);
	}

	/**
	 * Set the {@link ResponseErrorHandler} to use for handling HTTP response errors
	 */
	public void setResponseErrorHandler(ResponseErrorHandler responseErrorHandler) {
		this.webClientConfig.setResponseErrorHandler(responseErrorHandler);
	}

	/**
	 * Set the list of {@link ClientHttpRequestInterceptor} to use
	 * for intercepting client HTTP requests
	 */
	public void setInterceptors(List<ClientHttpRequestInterceptor> interceptors) {
		this.interceptors = (interceptors != null ?
				Collections.unmodifiableList(interceptors) : Collections.emptyList());
	}


	/**
	 * Perform the actual HTTP request/response exchange
	 * <p>Requesting from the exposed {@code Flux} will result in:
	 * <ul>
	 * <li>building the actual HTTP request using the provided {@code ClientWebRequestBuilder}</li>
	 * <li>encoding the HTTP request body with the configured {@code HttpMessageWriter}s</li>
	 * <li>returning the response with a publisher of the body</li>
	 * </ul>
	 */
	public WebResponseActions perform(ClientWebRequestBuilder builder) {
		ClientWebRequest clientWebRequest = builder.build();
		DefaultClientHttpRequestInterceptionChain interception =
				new DefaultClientHttpRequestInterceptionChain(this.clientHttpConnector,
						this.interceptors, clientWebRequest);

		final Mono<ClientHttpResponse> clientResponse = interception
				.intercept(clientWebRequest.getMethod(), clientWebRequest.getUrl(), null)
				.log("org.springframework.web.client.reactive", Level.FINE);

		return new WebResponseActions() {
			@Override
			public void doWithStatus(Consumer<HttpStatus> consumer) {
				clientResponse.doOnNext(clientHttpResponse -> consumer.accept(clientHttpResponse.getStatusCode()));
			}
			@Override
			public <T> T extract(ResponseExtractor<T> extractor) {
				return extractor.extract(clientResponse, webClientConfig);
			}
		};
	}


	protected class DefaultWebClientConfig implements WebClientConfig {

		private List<HttpMessageReader<?>> messageReaders;

		private List<HttpMessageWriter<?>> messageWriters;

		private ResponseErrorHandler responseErrorHandler;

		public DefaultWebClientConfig() {
			this.messageReaders = new ArrayList<>();
			addDefaultHttpMessageReaders(this.messageReaders);
			this.messageWriters = new ArrayList<>();
			addDefaultHttpMessageWriters(this.messageWriters);
		}

		@Override
		public List<HttpMessageReader<?>> getMessageReaders() {
			return this.messageReaders;
		}

		public void setMessageReaders(List<HttpMessageReader<?>> messageReaders) {
			this.messageReaders = messageReaders;
		}

		@Override
		public List<HttpMessageWriter<?>> getMessageWriters() {
			return this.messageWriters;
		}

		public void setMessageWriters(List<HttpMessageWriter<?>> messageWriters) {
			this.messageWriters = messageWriters;
		}

		@Override
		public ResponseErrorHandler getResponseErrorHandler() {
			return responseErrorHandler;
		}

		public void setResponseErrorHandler(ResponseErrorHandler responseErrorHandler) {
			this.responseErrorHandler = responseErrorHandler;
		}
	}


	protected class DefaultRequestCallback implements Function<ClientHttpRequest, Mono<Void>> {

		private final ClientWebRequest clientWebRequest;

		private final List<Consumer<? super HttpMessage>> requestCustomizers;

		public DefaultRequestCallback(ClientWebRequest clientWebRequest,
				List<Consumer<? super HttpMessage>> requestCustomizers) {
			this.clientWebRequest = clientWebRequest;
			this.requestCustomizers = requestCustomizers;
		}

		@Override
		public Mono<Void> apply(ClientHttpRequest clientHttpRequest) {
			clientHttpRequest.getHeaders().putAll(this.clientWebRequest.getHttpHeaders());
			if (clientHttpRequest.getHeaders().getAccept().isEmpty()) {
				clientHttpRequest.getHeaders().setAccept(
						Collections.singletonList(MediaType.ALL));
			}
			this.clientWebRequest.getCookies().values()
					.stream().flatMap(cookies -> cookies.stream())
					.forEach(cookie -> clientHttpRequest.getCookies().add(cookie.getName(), cookie));

			this.requestCustomizers.forEach(customizer -> customizer.accept(clientHttpRequest));

			if (this.clientWebRequest.getBody() != null) {
				return writeRequestBody(this.clientWebRequest.getBody(),
						this.clientWebRequest.getElementType(),
						clientHttpRequest, WebClient.this.webClientConfig.getMessageWriters());
			}
			else {
				return clientHttpRequest.setComplete();
			}
		}

		@SuppressWarnings({"unchecked", "rawtypes"})
		protected Mono<Void> writeRequestBody(Publisher<?> content, ResolvableType requestType,
				ClientHttpRequest request, List<HttpMessageWriter<?>> messageWriters) {

			MediaType contentType = request.getHeaders().getContentType();
			Optional<HttpMessageWriter<?>> messageWriter = resolveWriter(messageWriters, requestType, contentType);
			if (!messageWriter.isPresent()) {
				return Mono.error(new IllegalStateException(
						"Could not encode request body of type '" + contentType
								+ "' with target type '" + requestType.toString() + "'"));
			}
			return messageWriter.get().write((Publisher) content, requestType, contentType, request, Collections.emptyMap());
		}

		protected Optional<HttpMessageWriter<?>> resolveWriter(List<HttpMessageWriter<?>> messageWriters,
				ResolvableType type, MediaType mediaType) {

			return messageWriters.stream().filter(e -> e.canWrite(type, mediaType)).findFirst();
		}
	}


	protected class DefaultClientHttpRequestInterceptionChain implements ClientHttpRequestInterceptionChain {

		private final ClientHttpConnector connector;

		private final List<ClientHttpRequestInterceptor> interceptors;

		private final ClientWebRequest clientWebRequest;

		private final List<Consumer<? super HttpMessage>> requestCustomizers;

		private int index;

		public DefaultClientHttpRequestInterceptionChain(ClientHttpConnector connector,
				List<ClientHttpRequestInterceptor> interceptors, ClientWebRequest clientWebRequest) {

			Assert.notNull(connector, "ClientHttpConnector should not be null");
			this.connector = connector;
			this.interceptors = interceptors;
			this.clientWebRequest = clientWebRequest;
			this.requestCustomizers = new ArrayList<>();
			this.index = 0;
		}

		@Override
		public Mono<ClientHttpResponse> intercept(HttpMethod method, URI uri,
				Consumer<? super HttpMessage> requestCustomizer) {

			if (requestCustomizer != null) {
				this.requestCustomizers.add(requestCustomizer);
			}
			if (this.interceptors != null && this.index < this.interceptors.size()) {
				ClientHttpRequestInterceptor interceptor = this.interceptors.get(this.index++);
				return interceptor.intercept(method, uri, this);
			}
			else {
				return this.connector.connect(method, uri,
						new DefaultRequestCallback(this.clientWebRequest, this.requestCustomizers));
			}
		}
	}

}
