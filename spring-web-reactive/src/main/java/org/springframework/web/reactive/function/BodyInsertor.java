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

import java.util.function.BiFunction;
import java.util.function.Supplier;

import reactor.core.publisher.Mono;

import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.util.Assert;

/**
 * A component that can insert data into a {@link Response} body.
 *
 * @param <T> the type of data to insert
 * @author Arjen Poutsma
 * @since 5.0
 * @see Response#body()
 * @see Response.BodyBuilder#body(BodyInsertor)
 * @see BodyInsertors
 */
public interface BodyInsertor<T> {

	/**
	 * Insert into the given response.
	 * @param response the response to insert into
	 * @param configuration the configuration to use
	 * @return a {@code Mono} that indicates completion or error
	 */
	Mono<Void> insert(ServerHttpResponse response, Configuration configuration);

	/**
	 * Return the type contained in the body.
	 * @return the type contained in the body
	 */
	T t();


	/**
	 * Return a new {@code BodyInsertor} described by the given writer and supplier functions.
	 * @param writer  the writer function for the new insertor
	 * @param supplier the supplier function for the new insertor
	 * @param <T> the type supplied and written by the insertor
	 * @return the new {@code BodyInsertor}
	 */
	static <T> BodyInsertor<T> of(BiFunction<ServerHttpResponse, Configuration, Mono<Void>> writer,
			Supplier<T> supplier) {

		Assert.notNull(writer, "'writer' must not be null");
		Assert.notNull(supplier, "'supplier' must not be null");

		return new BodyInsertors.DefaultBodyInsertor<T>(writer, supplier);
	}

}
