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

package org.springframework.http.codec;

import java.util.List;
import java.util.Map;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.core.ResolvableType;
import org.springframework.http.MediaType;
import org.springframework.http.ReactiveHttpInputMessage;

/**
 * Strategy interface that specifies a reader that can convert from the HTTP
 * request body from a stream of bytes to Objects.
 *
 * @author Rossen Stoyanchev
 * @author Arjen Poutsma
 * @author Sebastien Deleuze
 * @since 5.0
 */
public interface HttpMessageReader<T> {

	/**
	 * Indicates whether the given class can be read by this converter.
	 * @param elementType the stream element type to test for readability
	 * @param mediaType the media type to read, can be {@code null} if not specified.
	 * Typically the value of a {@code Content-Type} header.
	 * @return {@code true} if readable; {@code false} otherwise
	 */
	boolean canRead(ResolvableType elementType, MediaType mediaType);

	/**
	 * Read a {@link Flux} of the given type form the given input message, and returns it.
	 * @param elementType the stream element type to return. This type must have previously been
	 * passed to the {@link #canRead canRead} method of this interface, which must have
	 * returned {@code true}.
	 * @param inputMessage the HTTP input message to read from
	 * @param hints additional information about how to read the body
	 * @return the converted {@link Flux} of elements
	 */
	Flux<T> read(ResolvableType elementType, ReactiveHttpInputMessage inputMessage, Map<String, Object> hints);

	/**
	 * Read a {@link Mono} of the given type form the given input message, and returns it.
	 * @param elementType the stream element type to return. This type must have previously been
	 * passed to the {@link #canRead canRead} method of this interface, which must have
	 * returned {@code true}.
	 * @param inputMessage the HTTP input message to read from
	 * @param hints additional information about how to read the body
	 * @return the converted {@link Mono} of object
	 */
	Mono<T> readMono(ResolvableType elementType, ReactiveHttpInputMessage inputMessage, Map<String, Object> hints);

	/**
	 * Return the list of {@link MediaType} objects that can be read by this converter.
	 * @return the list of supported readable media types
	 */
	List<MediaType> getReadableMediaTypes();

}
