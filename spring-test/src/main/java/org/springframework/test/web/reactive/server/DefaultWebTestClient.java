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

package org.springframework.test.web.reactive.server;

import java.net.URI;
import java.nio.charset.Charset;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;

import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.http.client.reactive.ClientHttpRequest;
import org.springframework.http.client.reactive.ClientHttpResponse;
import org.springframework.lang.Nullable;
import org.springframework.test.util.JsonExpectationsHelper;
import org.springframework.util.Assert;
import org.springframework.util.MimeType;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyExtractor;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriBuilder;

import static java.nio.charset.StandardCharsets.*;
import static org.springframework.test.util.AssertionErrors.*;
import static org.springframework.web.reactive.function.BodyExtractors.*;

/**
 * Default implementation of {@link WebTestClient}.
 *
 * @author Rossen Stoyanchev
 * @since 5.0
 */
class DefaultWebTestClient implements WebTestClient {

	private final WebClient webClient;

	private final WiretapConnector wiretapConnector;

	private final Duration timeout;

	private final WebTestClient.Builder builder;

	private final AtomicLong requestIndex = new AtomicLong();


	DefaultWebTestClient(WebClient.Builder clientBuilder, ClientHttpConnector connector,
			@Nullable Duration timeout, WebTestClient.Builder webTestClientBuilder) {

		Assert.notNull(clientBuilder, "WebClient.Builder is required");
		this.wiretapConnector = new WiretapConnector(connector);
		this.webClient = clientBuilder.clientConnector(this.wiretapConnector).build();
		this.timeout = (timeout != null ? timeout : Duration.ofSeconds(5));
		this.builder = webTestClientBuilder;
	}


	private Duration getTimeout() {
		return this.timeout;
	}


	@Override
	public UriSpec<RequestHeadersSpec<?>> get() {
		return toUriSpec(wc -> wc.method(HttpMethod.GET));
	}

	@Override
	public UriSpec<RequestHeadersSpec<?>> head() {
		return toUriSpec(wc -> wc.method(HttpMethod.HEAD));
	}

	@Override
	public UriSpec<RequestBodySpec> post() {
		return toUriSpec(wc -> wc.method(HttpMethod.POST));
	}

	@Override
	public UriSpec<RequestBodySpec> put() {
		return toUriSpec(wc -> wc.method(HttpMethod.PUT));
	}

	@Override
	public UriSpec<RequestBodySpec> patch() {
		return toUriSpec(wc -> wc.method(HttpMethod.PATCH));
	}

	@Override
	public UriSpec<RequestHeadersSpec<?>> delete() {
		return toUriSpec(wc -> wc.method(HttpMethod.DELETE));
	}

	@Override
	public UriSpec<RequestHeadersSpec<?>> options() {
		return toUriSpec(wc -> wc.method(HttpMethod.OPTIONS));
	}

	@Override
	public Builder mutate() {
		return this.builder;
	}

	private <S extends RequestHeadersSpec<?>> UriSpec<S> toUriSpec(
			Function<WebClient, WebClient.UriSpec<WebClient.RequestBodySpec>> function) {

		return new DefaultUriSpec<>(function.apply(this.webClient));
	}


	@SuppressWarnings("unchecked")
	private class DefaultUriSpec<S extends RequestHeadersSpec<?>> implements UriSpec<S> {

		private final WebClient.UriSpec<WebClient.RequestBodySpec> uriSpec;


		DefaultUriSpec(WebClient.UriSpec<WebClient.RequestBodySpec> spec) {
			this.uriSpec = spec;
		}

		@Override
		public S uri(URI uri) {
			return (S) new DefaultRequestBodySpec(this.uriSpec.uri(uri), null);
		}

		@Override
		public S uri(String uriTemplate, Object... uriVariables) {
			return (S) new DefaultRequestBodySpec(this.uriSpec.uri(uriTemplate, uriVariables), uriTemplate);
		}

		@Override
		public S uri(String uriTemplate, Map<String, ?> uriVariables) {
			return (S) new DefaultRequestBodySpec(this.uriSpec.uri(uriTemplate, uriVariables), uriTemplate);
		}

		@Override
		public S uri(Function<UriBuilder, URI> uriBuilder) {
			return (S) new DefaultRequestBodySpec(this.uriSpec.uri(uriBuilder), null);
		}
	}

	private class DefaultRequestBodySpec implements RequestBodySpec {

		private final WebClient.RequestBodySpec bodySpec;

		@Nullable
		private final String uriTemplate;

		private final String requestId;

		DefaultRequestBodySpec(WebClient.RequestBodySpec spec, @Nullable String uriTemplate) {
			this.bodySpec = spec;
			this.uriTemplate = uriTemplate;
			this.requestId = String.valueOf(requestIndex.incrementAndGet());
			this.bodySpec.header(WebTestClient.WEBTESTCLIENT_REQUEST_ID, this.requestId);
		}

		@Override
		public RequestBodySpec header(String headerName, String... headerValues) {
			this.bodySpec.header(headerName, headerValues);
			return this;
		}

		@Override
		public RequestBodySpec headers(Consumer<HttpHeaders> headersConsumer) {
			this.bodySpec.headers(headersConsumer);
			return this;
		}

		@Override
		public RequestBodySpec accept(MediaType... acceptableMediaTypes) {
			this.bodySpec.accept(acceptableMediaTypes);
			return this;
		}

		@Override
		public RequestBodySpec acceptCharset(Charset... acceptableCharsets) {
			this.bodySpec.acceptCharset(acceptableCharsets);
			return this;
		}

		@Override
		public RequestBodySpec contentType(MediaType contentType) {
			this.bodySpec.contentType(contentType);
			return this;
		}

		@Override
		public RequestBodySpec contentLength(long contentLength) {
			this.bodySpec.contentLength(contentLength);
			return this;
		}

		@Override
		public RequestBodySpec cookie(String name, String value) {
			this.bodySpec.cookie(name, value);
			return this;
		}

		@Override
		public RequestBodySpec cookies(
				Consumer<MultiValueMap<String, String>> cookiesConsumer) {
			this.bodySpec.cookies(cookiesConsumer);
			return this;
		}

		@Override
		public RequestBodySpec ifModifiedSince(ZonedDateTime ifModifiedSince) {
			this.bodySpec.ifModifiedSince(ifModifiedSince);
			return this;
		}

		@Override
		public RequestBodySpec ifNoneMatch(String... ifNoneMatches) {
			this.bodySpec.ifNoneMatch(ifNoneMatches);
			return this;
		}

		@Override
		public ResponseSpec exchange() {
			return toResponseSpec(this.bodySpec.exchange());
		}

		@Override
		public RequestHeadersSpec<?> body(BodyInserter<?, ? super ClientHttpRequest> inserter) {
			this.bodySpec.body(inserter);
			return this;
		}

		@Override
		public <T, S extends Publisher<T>> RequestHeadersSpec<?> body(S publisher, Class<T> elementClass) {
			this.bodySpec.body(publisher, elementClass);
			return this;
		}

		@Override
		public RequestHeadersSpec<?> syncBody(Object body) {
			this.bodySpec.syncBody(body);
			return this;
		}

		private DefaultResponseSpec toResponseSpec(Mono<ClientResponse> mono) {
			ClientResponse clientResponse = mono.block(getTimeout());
			Assert.state(clientResponse != null, "No ClientResponse");
			ExchangeResult exchangeResult = wiretapConnector.claimRequest(this.requestId);
			return new DefaultResponseSpec(exchangeResult, clientResponse, this.uriTemplate, getTimeout());
		}
	}


	private static class UndecodedExchangeResult extends ExchangeResult {

		private final ClientResponse response;

		private final Duration timeout;

		UndecodedExchangeResult(ExchangeResult result, ClientResponse response,
				@Nullable String uriTemplate, Duration timeout) {

			super(result, uriTemplate);
			this.response = response;
			this.timeout = timeout;
		}

		public <T> EntityExchangeResult<T> decode(BodyExtractor<Mono<T>, ? super ClientHttpResponse> extractor) {
			T body = this.response.body(extractor).block(this.timeout);
			return new EntityExchangeResult<>(this, body);
		}

		public <T> EntityExchangeResult<List<T>> decodeToList(BodyExtractor<Flux<T>, ? super ClientHttpResponse> extractor) {
			Flux<T> flux = this.response.body(extractor);
			List<T> body = flux.collectList().block(this.timeout);
			return new EntityExchangeResult<>(this, body);
		}

		public <T> FluxExchangeResult<T> decodeToFlux(BodyExtractor<Flux<T>, ? super ClientHttpResponse> extractor) {
			Flux<T> body = this.response.body(extractor);
			return new FluxExchangeResult<>(this, body, this.timeout);
		}

		public EntityExchangeResult<byte[]> decodeToByteArray() {
			ByteArrayResource resource = this.response.body(toMono(ByteArrayResource.class)).block(this.timeout);
			byte[] body = (resource != null ? resource.getByteArray() : null);
			return new EntityExchangeResult<>(this, body);
		}
	}


	private static class DefaultResponseSpec implements ResponseSpec {

		private final UndecodedExchangeResult result;

		DefaultResponseSpec(
				ExchangeResult result, ClientResponse response, @Nullable String uriTemplate, Duration timeout) {

			this.result = new UndecodedExchangeResult(result, response, uriTemplate, timeout);
		}

		@Override
		public StatusAssertions expectStatus() {
			return new StatusAssertions(this.result, this);
		}

		@Override
		public HeaderAssertions expectHeader() {
			return new HeaderAssertions(this.result, this);
		}

		@Override
		public <B> BodySpec<B, ?> expectBody(Class<B> bodyType) {
			return new DefaultBodySpec<>(this.result.decode(toMono(bodyType)));
		}

		@Override
		public <B> BodySpec<B, ?> expectBody(ParameterizedTypeReference<B> bodyType) {
			return new DefaultBodySpec<>(this.result.decode(toMono(bodyType)));
		}

		@Override
		public <E> ListBodySpec<E> expectBodyList(Class<E> elementType) {
			return new DefaultListBodySpec<>(this.result.decodeToList(toFlux(elementType)));
		}

		@Override
		public <E> ListBodySpec<E> expectBodyList(ParameterizedTypeReference<E> elementType) {
			return new DefaultListBodySpec<>(this.result.decodeToList(toFlux(elementType)));
		}

		@Override
		public BodyContentSpec expectBody() {
			return new DefaultBodyContentSpec(this.result.decodeToByteArray());
		}

		@Override
		public <T> FluxExchangeResult<T> returnResult(Class<T> elementType) {
			return this.result.decodeToFlux(toFlux(elementType));
		}

		@Override
		public <T> FluxExchangeResult<T> returnResult(ParameterizedTypeReference<T> elementType) {
			return this.result.decodeToFlux(toFlux(elementType));
		}
	}


	private static class DefaultBodySpec<B, S extends BodySpec<B, S>> implements BodySpec<B, S> {

		private final EntityExchangeResult<B> result;

		DefaultBodySpec(EntityExchangeResult<B> result) {
			this.result = result;
		}

		protected EntityExchangeResult<B> getResult() {
			return this.result;
		}

		@Override
		public <T extends S> T isEqualTo(B expected) {
			this.result.assertWithDiagnostics(() ->
					assertEquals("Response body", expected, this.result.getResponseBody()));
			return self();
		}

		@Override
		public <T extends S> T consumeWith(Consumer<EntityExchangeResult<B>> consumer) {
			this.result.assertWithDiagnostics(() -> consumer.accept(this.result));
			return self();
		}

		@SuppressWarnings("unchecked")
		private <T extends S> T self() {
			return (T) this;
		}

		@Override
		public EntityExchangeResult<B> returnResult() {
			return this.result;
		}
	}


	private static class DefaultListBodySpec<E> extends DefaultBodySpec<List<E>, ListBodySpec<E>>
			implements ListBodySpec<E> {

		DefaultListBodySpec(EntityExchangeResult<List<E>> result) {
			super(result);
		}

		@Override
		public ListBodySpec<E> hasSize(int size) {
			List<E> actual = getResult().getResponseBody();
			String message = "Response body does not contain " + size + " elements";
			getResult().assertWithDiagnostics(() -> assertEquals(message, size, (actual != null ? actual.size() : 0)));
			return this;
		}

		@Override
		@SuppressWarnings("unchecked")
		public ListBodySpec<E> contains(E... elements) {
			List<E> expected = Arrays.asList(elements);
			List<E> actual = getResult().getResponseBody();
			String message = "Response body does not contain " + expected;
			getResult().assertWithDiagnostics(() -> assertTrue(message, (actual != null && actual.containsAll(expected))));
			return this;
		}

		@Override
		@SuppressWarnings("unchecked")
		public ListBodySpec<E> doesNotContain(E... elements) {
			List<E> expected = Arrays.asList(elements);
			List<E> actual = getResult().getResponseBody();
			String message = "Response body should not have contained " + expected;
			getResult().assertWithDiagnostics(() -> assertTrue(message, (actual == null || !actual.containsAll(expected))));
			return this;
		}

		@Override
		public EntityExchangeResult<List<E>> returnResult() {
			return getResult();
		}
	}


	private static class DefaultBodyContentSpec implements BodyContentSpec {

		private final EntityExchangeResult<byte[]> result;

		private final boolean isEmpty;

		DefaultBodyContentSpec(EntityExchangeResult<byte[]> result) {
			this.result = result;
			this.isEmpty = (result.getResponseBody() == null);
		}

		@Override
		public EntityExchangeResult<Void> isEmpty() {
			this.result.assertWithDiagnostics(() -> assertTrue("Expected empty body", this.isEmpty));
			return new EntityExchangeResult<>(this.result, null);
		}

		@Override
		public BodyContentSpec json(String json) {
			this.result.assertWithDiagnostics(() -> {
				try {
					new JsonExpectationsHelper().assertJsonEqual(json, getBodyAsString());
				}
				catch (Exception ex) {
					throw new AssertionError("JSON parsing error", ex);
				}
			});
			return this;
		}

		@Override
		public JsonPathAssertions jsonPath(String expression, Object... args) {
			return new JsonPathAssertions(this, getBodyAsString(), expression, args);
		}

		private String getBodyAsString() {
			byte[] body = this.result.getResponseBody();
			if (body == null || body.length == 0) {
				return "";
			}
			MediaType mediaType = this.result.getResponseHeaders().getContentType();
			Charset charset = Optional.ofNullable(mediaType).map(MimeType::getCharset).orElse(UTF_8);
			return new String(body, charset);
		}

		@Override
		public BodyContentSpec consumeWith(Consumer<EntityExchangeResult<byte[]>> consumer) {
			this.result.assertWithDiagnostics(() -> consumer.accept(this.result));
			return this;
		}

		@Override
		public EntityExchangeResult<byte[]> returnResult() {
			return this.result;
		}
	}

}
