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

package org.springframework.web.reactive.function.server;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.Charset;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Function;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.MediaType;
import org.springframework.http.codec.HttpMessageReader;
import org.springframework.http.server.PathContainer;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.util.Assert;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyExtractor;
import org.springframework.web.reactive.function.BodyExtractors;
import org.springframework.web.reactive.function.UnsupportedMediaTypeException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.UnsupportedMediaTypeStatusException;
import org.springframework.web.server.WebSession;

/**
 * {@code ServerRequest} implementation based on a {@link ServerWebExchange}.
 *
 * @author Arjen Poutsma
 * @since 5.0
 */
class DefaultServerRequest implements ServerRequest {

	private static final Function<UnsupportedMediaTypeException, UnsupportedMediaTypeStatusException> ERROR_MAPPER =
			ex -> (ex.getContentType() != null ?
					new UnsupportedMediaTypeStatusException(ex.getContentType(), ex.getSupportedMediaTypes()) :
					new UnsupportedMediaTypeStatusException(ex.getMessage()));


	private final ServerWebExchange exchange;

	private final Headers headers;

	private final List<HttpMessageReader<?>> messageReaders;


	DefaultServerRequest(ServerWebExchange exchange, List<HttpMessageReader<?>> messageReaders) {
		this.exchange = exchange;
		this.messageReaders = unmodifiableCopy(messageReaders);
		this.headers = new DefaultHeaders();
	}

	private static <T> List<T> unmodifiableCopy(List<? extends T> list) {
		return Collections.unmodifiableList(new ArrayList<>(list));
	}


	@Override
	public String methodName() {
		return request().getMethodValue();
	}

	@Override
	public URI uri() {
		return request().getURI();
	}

	@Override
	public PathContainer pathContainer() {
		return request().getPath();
	}

	@Override
	public Headers headers() {
		return this.headers;
	}

	@Override
	public MultiValueMap<String, HttpCookie> cookies() {
		return request().getCookies();
	}

	@Override
	public <T> T body(BodyExtractor<T, ? super ServerHttpRequest> extractor) {
		return body(extractor, Collections.emptyMap());
	}

	@Override
	public <T> T body(BodyExtractor<T, ? super ServerHttpRequest> extractor, Map<String, Object> hints) {
		Assert.notNull(extractor, "'extractor' must not be null");
		return extractor.extract(request(),
				new BodyExtractor.Context() {
					@Override
					public List<HttpMessageReader<?>> messageReaders() {
						return messageReaders;
					}
					@Override
					public Optional<ServerHttpResponse> serverResponse() {
						return Optional.of(exchange().getResponse());
					}
					@Override
					public Map<String, Object> hints() {
						return hints;
					}
				});
	}

	@Override
	public <T> Mono<T> bodyToMono(Class<? extends T> elementClass) {
		Mono<T> mono = body(BodyExtractors.toMono(elementClass));
		return mono.onErrorMap(UnsupportedMediaTypeException.class, ERROR_MAPPER);
	}

	@Override
	public <T> Mono<T> bodyToMono(ParameterizedTypeReference<T> typeReference) {
		Mono<T> mono = body(BodyExtractors.toMono(typeReference));
		return mono.onErrorMap(UnsupportedMediaTypeException.class, ERROR_MAPPER);
	}

	@Override
	public <T> Flux<T> bodyToFlux(Class<? extends T> elementClass) {
		Flux<T> flux = body(BodyExtractors.toFlux(elementClass));
		return flux.onErrorMap(UnsupportedMediaTypeException.class, ERROR_MAPPER);
	}

	@Override
	public <T> Flux<T> bodyToFlux(ParameterizedTypeReference<T> typeReference) {
		Flux<T> flux = body(BodyExtractors.toFlux(typeReference));
		return flux.onErrorMap(UnsupportedMediaTypeException.class, ERROR_MAPPER);
	}

	@Override
	public Map<String, Object> attributes() {
		return this.exchange.getAttributes();
	}

	@Override
	public MultiValueMap<String, String> queryParams() {
		return request().getQueryParams();
	}

	@Override
	public Map<String, String> pathVariables() {
		return this.exchange.getAttributeOrDefault(
				RouterFunctions.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Collections.emptyMap());
	}

	@Override
	public Mono<WebSession> session() {
		return this.exchange.getSession();
	}

	@Override
	public Mono<? extends Principal> principal() {
		return this.exchange.getPrincipal();
	}

	private ServerHttpRequest request() {
		return this.exchange.getRequest();
	}

	ServerWebExchange exchange() {
		return this.exchange;
	}

	@Override
	public String toString() {
		return String.format("%s %s", method(), path());
	}


	private class DefaultHeaders implements Headers {

		private HttpHeaders delegate() {
			return request().getHeaders();
		}

		@Override
		public List<MediaType> accept() {
			return delegate().getAccept();
		}

		@Override
		public List<Charset> acceptCharset() {
			return delegate().getAcceptCharset();
		}

		@Override
		public List<Locale.LanguageRange> acceptLanguage() {
			return delegate().getAcceptLanguage();
		}

		@Override
		public OptionalLong contentLength() {
			long value = delegate().getContentLength();
			return (value != -1 ? OptionalLong.of(value) : OptionalLong.empty());
		}

		@Override
		public Optional<MediaType> contentType() {
			return Optional.ofNullable(delegate().getContentType());
		}

		@Override
		public InetSocketAddress host() {
			return delegate().getHost();
		}

		@Override
		public List<HttpRange> range() {
			return delegate().getRange();
		}

		@Override
		public List<String> header(String headerName) {
			List<String> headerValues = delegate().get(headerName);
			return (headerValues != null ? headerValues : Collections.emptyList());
		}

		@Override
		public HttpHeaders asHttpHeaders() {
			return HttpHeaders.readOnlyHttpHeaders(delegate());
		}

		@Override
		public String toString() {
			return delegate().toString();
		}
	}

}
