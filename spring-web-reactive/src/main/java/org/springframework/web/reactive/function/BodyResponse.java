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

import reactor.core.publisher.Mono;

import org.springframework.core.ResolvableType;
import org.springframework.http.HttpHeaders;
import org.springframework.util.Assert;
import org.springframework.web.server.ServerWebExchange;

/**
 * @author Arjen Poutsma
 */
class BodyResponse<T> extends AbstractHttpMessageWriterResponse<T> {

	private final T body;

	public BodyResponse(int statusCode, HttpHeaders headers,
						T body) {
		super(statusCode, headers);
		Assert.notNull(body, "'body' must not be null");
		this.body = body;
	}

	@Override
	public T body() {
		return this.body;
	}

	@Override
	public Mono<Void> writeTo(ServerWebExchange exchange) {
		return writeToInternal(exchange, Mono.just(this.body), ResolvableType.forInstance(this.body));
	}

}
