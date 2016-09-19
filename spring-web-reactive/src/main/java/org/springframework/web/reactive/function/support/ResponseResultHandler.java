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

package org.springframework.web.reactive.function.support;

import reactor.core.publisher.Mono;

import org.springframework.util.Assert;
import org.springframework.web.reactive.HandlerResult;
import org.springframework.web.reactive.HandlerResultHandler;
import org.springframework.web.reactive.function.Response;
import org.springframework.web.reactive.function.StrategiesSupplier;
import org.springframework.web.server.ServerWebExchange;

/**
 * {@code HandlerResultHandler} implementation that supports {@link Response}s.
 *
 * @author Arjen Poutsma
 * @since 5.0
 */
public class ResponseResultHandler implements HandlerResultHandler {

	private final StrategiesSupplier strategies;

	/**
	 * Create a {@code ResponseResultHandler} with default strategies.
	 */
	public ResponseResultHandler() {
		this(StrategiesSupplier.builder().build());
	}

	/**
	 * Create a {@code ResponseResultHandler} with the given strategies.
	 */
	public ResponseResultHandler(StrategiesSupplier strategies) {
		Assert.notNull(strategies, "'strategies' must not be null");
		this.strategies = strategies;
	}

	@Override
	public boolean supports(HandlerResult result) {
		return result.getReturnValue()
				.filter(o -> o instanceof Response)
				.isPresent();
	}

	@Override
	public Mono<Void> handleResult(ServerWebExchange exchange, HandlerResult result) {
		Response<?> response = (Response<?>) result.getReturnValue().orElseThrow(
				IllegalStateException::new);
		return response.writeTo(exchange, this.strategies);
	}
}
