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

package org.springframework.web.reactive.function;

import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

import reactor.core.publisher.Mono;

import org.springframework.http.ReactiveHttpOutputMessage;
import org.springframework.http.codec.HttpMessageWriter;

/**
 * A combination of functions that can populate a {@link ReactiveHttpOutputMessage} body.
 *
 * @param <T> the type of data to insert
 * @param <M> the type of {@link ReactiveHttpOutputMessage} this inserter can be applied to
 * @author Arjen Poutsma
 * @since 5.0
 * @see BodyInserters
 */
@FunctionalInterface
public interface BodyInserter<T, M extends ReactiveHttpOutputMessage> {

	/**
	 * Insert into the given output message.
	 * @param outputMessage the response to insert into
	 * @param context the context to use
	 * @return a {@code Mono} that indicates completion or error
	 */
	Mono<Void> insert(M outputMessage, Context context);

	/**
	 * Defines the context used during the insertion.
	 */
	interface Context {

		/**
		 * Supply a {@linkplain Stream stream} of {@link HttpMessageWriter}s to be used for response
		 * body conversion.
		 * @return the stream of message writers
		 */
		Supplier<Stream<HttpMessageWriter<?>>> messageWriters();

		/**
		 * Return the map of hints to use for response body conversion.
		 */
		Map<String, Object> hints();

	}


}
