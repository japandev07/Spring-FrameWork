/*
 * Copyright 2002-2018 the original author or authors.
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

package org.springframework.web.reactive.function.client;

import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import reactor.core.publisher.Flux;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.client.reactive.ClientHttpResponse;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * Default implementation of {@link ClientResponse.Builder}.
 *
 * @author Arjen Poutsma
 * @since 5.0.5
 */
class DefaultClientResponseBuilder implements ClientResponse.Builder {

	private final HttpHeaders headers = new HttpHeaders();

	private final MultiValueMap<String, ResponseCookie> cookies = new LinkedMultiValueMap<>();

	private HttpStatus statusCode = HttpStatus.OK;

	private Flux<DataBuffer> body = Flux.empty();

	private ExchangeStrategies strategies;


	public DefaultClientResponseBuilder(ExchangeStrategies strategies) {
		Assert.notNull(strategies, "'strategies' must not be null");
		this.strategies = strategies;
	}

	public DefaultClientResponseBuilder(ClientResponse other) {
		this(other.strategies());
		statusCode(other.statusCode());
		headers(headers -> headers.addAll(other.headers().asHttpHeaders()));
		cookies(cookies -> cookies.addAll(other.cookies()));
	}

	@Override
	public DefaultClientResponseBuilder statusCode(HttpStatus statusCode) {
		Assert.notNull(statusCode, "'statusCode' must not be null");
		this.statusCode = statusCode;
		return this;
	}

	@Override
	public ClientResponse.Builder header(String headerName, String... headerValues) {
		for (String headerValue : headerValues) {
			this.headers.add(headerName, headerValue);
		}
		return this;
	}

	@Override
	public ClientResponse.Builder headers(Consumer<HttpHeaders> headersConsumer) {
		Assert.notNull(headersConsumer, "'headersConsumer' must not be null");
		headersConsumer.accept(this.headers);
		return this;
	}

	@Override
	public DefaultClientResponseBuilder cookie(String name, String... values) {
		for (String value : values) {
			this.cookies.add(name, ResponseCookie.from(name, value).build());
		}
		return this;
	}

	@Override
	public ClientResponse.Builder cookies(
			Consumer<MultiValueMap<String, ResponseCookie>> cookiesConsumer) {
		Assert.notNull(cookiesConsumer, "'cookiesConsumer' must not be null");
		cookiesConsumer.accept(this.cookies);
		return this;
	}

	@Override
	public ClientResponse.Builder body(Flux<DataBuffer> body) {
		Assert.notNull(body, "'body' must not be null");
		releaseBody();
		this.body = body;
		return this;
	}

	@Override
	public ClientResponse.Builder body(String body) {
		Assert.notNull(body, "'body' must not be null");
		releaseBody();
		DataBufferFactory dataBufferFactory = new DefaultDataBufferFactory();
		this.body = Flux.just(body).
				map(s -> {
					byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
					return dataBufferFactory.wrap(bytes);
				});
		return this;
	}

	private void releaseBody() {
		this.body.subscribe(DataBufferUtils.releaseConsumer());
	}

	@Override
	public ClientResponse build() {
		ClientHttpResponse clientHttpResponse = new BuiltClientHttpResponse(this.statusCode,
				this.headers, this.cookies, this.body);
		return new DefaultClientResponse(clientHttpResponse, this.strategies);
	}

	private static class BuiltClientHttpResponse implements ClientHttpResponse {

		private final HttpStatus statusCode;

		private final HttpHeaders headers;

		private final MultiValueMap<String, ResponseCookie> cookies;

		private final Flux<DataBuffer> body;

		public BuiltClientHttpResponse(HttpStatus statusCode, HttpHeaders headers,
				MultiValueMap<String, ResponseCookie> cookies,
				Flux<DataBuffer> body) {

			this.statusCode = statusCode;
			this.headers = HttpHeaders.readOnlyHttpHeaders(headers);
			this.cookies = unmodifiableCopy(cookies);
			this.body = body;
		}

		private static @Nullable <K, V> MultiValueMap<K, V> unmodifiableCopy(@Nullable MultiValueMap<K, V> original) {
			if (original != null) {
				return CollectionUtils.unmodifiableMultiValueMap(new LinkedMultiValueMap<>(original));
			}
			else {
				return null;
			}
		}

		@Override
		public HttpStatus getStatusCode() {
			return this.statusCode;
		}

		@Override
		public HttpHeaders getHeaders() {
			return this.headers;
		}

		@Override
		public MultiValueMap<String, ResponseCookie> getCookies() {
			return this.cookies;
		}

		@Override
		public Flux<DataBuffer> getBody() {
			return this.body;
		}
	}

}
