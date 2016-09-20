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

package org.springframework.web.reactive.function;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.core.ResolvableType;
import org.springframework.http.MediaType;
import org.springframework.http.codec.HttpMessageReader;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.Assert;
import org.springframework.web.server.UnsupportedMediaTypeStatusException;

/**
 * Implementations of {@link BodyExtractor} that read various bodies, such a reactive streams.
 *
 * @author Arjen Poutsma
 * @since 5.0
 */
public abstract class BodyExtractors {

	/**
	 * Return a {@code BodyExtractor} that reads into a Reactor {@link Mono}.
	 * @param elementClass the class of element in the {@code Mono}
	 * @param <T> the element type
	 * @return a {@code BodyExtractor} that reads a mono
	 */
	public static <T> BodyExtractor<Mono<T>> toMono(Class<? extends T> elementClass) {
		Assert.notNull(elementClass, "'elementClass' must not be null");
		return toMono(ResolvableType.forClass(elementClass));
	}

	/**
	 * Return a {@code BodyExtractor} that reads into a Reactor {@link Mono}.
	 * @param elementType the type of element in the {@code Mono}
	 * @param <T> the element type
	 * @return a {@code BodyExtractor} that reads a mono
	 */
	public static <T> BodyExtractor<Mono<T>> toMono(ResolvableType elementType) {
		Assert.notNull(elementType, "'elementType' must not be null");
		return (request, strategies) -> readWithMessageReaders(request, strategies,
				elementType,
				reader -> reader.readMono(elementType, request, Collections.emptyMap()),
				Mono::error);
	}

	/**
	 * Return a {@code BodyExtractor} that reads into a Reactor {@link Flux}.
	 * @param elementClass the class of element in the {@code Flux}
	 * @param <T> the element type
	 * @return a {@code BodyExtractor} that reads a mono
	 */
	public static <T> BodyExtractor<Flux<T>> toFlux(Class<? extends T> elementClass) {
		Assert.notNull(elementClass, "'elementClass' must not be null");
		return toFlux(ResolvableType.forClass(elementClass));
	}

	/**
	 * Return a {@code BodyExtractor} that reads into a Reactor {@link Flux}.
	 * @param elementType the type of element in the {@code Flux}
	 * @param <T> the element type
	 * @return a {@code BodyExtractor} that reads a mono
	 */
	public static <T> BodyExtractor<Flux<T>> toFlux(ResolvableType elementType) {
		Assert.notNull(elementType, "'elementType' must not be null");
		return (request, strategies) -> readWithMessageReaders(request, strategies,
				elementType,
				reader -> reader.read(elementType, request, Collections.emptyMap()),
				Flux::error);
	}

	private static <T, S extends Publisher<T>> S readWithMessageReaders(
			ServerHttpRequest request,
			StrategiesSupplier strategies,
			ResolvableType elementType,
			Function<HttpMessageReader<T>, S> readerFunction,
			Function<Throwable, S> unsupportedError) {

		MediaType contentType = contentType(request);
		Supplier<Stream<HttpMessageReader<?>>> messageReaders = strategies.messageReaders();
		return messageReaders.get()
				.filter(r -> r.canRead(elementType, contentType))
				.findFirst()
				.map(BodyExtractors::<T>cast)
				.map(readerFunction)
				.orElseGet(() -> {
					List<MediaType> supportedMediaTypes = messageReaders.get()
							.flatMap(reader -> reader.getReadableMediaTypes().stream())
							.collect(Collectors.toList());
					UnsupportedMediaTypeStatusException error =
							new UnsupportedMediaTypeStatusException(contentType, supportedMediaTypes);
					return unsupportedError.apply(error);
				});
	}

	private static MediaType contentType(ServerHttpRequest request) {
		MediaType result = request.getHeaders().getContentType();
		return result != null ? result : MediaType.APPLICATION_OCTET_STREAM;
	}

	@SuppressWarnings("unchecked")
	private static <T> HttpMessageReader<T> cast(HttpMessageReader<?> messageReader) {
		return (HttpMessageReader<T>) messageReader;
	}

}
