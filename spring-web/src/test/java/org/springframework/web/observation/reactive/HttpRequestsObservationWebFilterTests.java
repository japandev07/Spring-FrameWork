/*
 * Copyright 2002-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.web.observation.reactive;


import java.util.Optional;

import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import org.assertj.core.api.ThrowingConsumer;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.testfixture.http.server.reactive.MockServerHttpRequest;
import org.springframework.web.testfixture.server.MockServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link HttpRequestsObservationWebFilter}.
 *
 * @author Brian Clozel
 */
class HttpRequestsObservationWebFilterTests {

	private final TestObservationRegistry observationRegistry = TestObservationRegistry.create();

	private final HttpRequestsObservationWebFilter filter = new HttpRequestsObservationWebFilter(this.observationRegistry);

	@Test
	void filterShouldFillObservationContext() {
		ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/test/resource"));
		exchange.getResponse().setRawStatusCode(200);
		WebFilterChain filterChain = createFilterChain(filterExchange -> {
			Optional<HttpRequestsObservationContext> observationContext = HttpRequestsObservationWebFilter.findObservationContext(filterExchange);
			assertThat(observationContext).isPresent();
			assertThat(observationContext.get().getCarrier()).isEqualTo(exchange.getRequest());
			assertThat(observationContext.get().getResponse()).isEqualTo(exchange.getResponse());
		});
		this.filter.filter(exchange, filterChain).block();
		assertThatHttpObservation().hasLowCardinalityKeyValue("outcome", "SUCCESS");
	}

	@Test
	void filterShouldUseThrownException() {
		ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/test/resource"));
		exchange.getResponse().setRawStatusCode(500);
		WebFilterChain filterChain = createFilterChain(filterExchange -> {
			throw new IllegalArgumentException("server error");
		});
		StepVerifier.create(this.filter.filter(exchange, filterChain))
				.expectError(IllegalArgumentException.class)
				.verify();
		Optional<HttpRequestsObservationContext> observationContext = HttpRequestsObservationWebFilter.findObservationContext(exchange);
		assertThat(observationContext.get().getError()).get().isInstanceOf(IllegalArgumentException.class);
		assertThatHttpObservation().hasLowCardinalityKeyValue("outcome", "SERVER_ERROR");
	}

	@Test
	void filterShouldRecordObservationWhenCancelled() {
		ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/test/resource"));
		exchange.getResponse().setRawStatusCode(200);
		WebFilterChain filterChain = createFilterChain(filterExchange -> {
		});
		StepVerifier.create(this.filter.filter(exchange, filterChain))
				.thenCancel()
				.verify();
		assertThatHttpObservation().hasLowCardinalityKeyValue("outcome", "UNKNOWN");
	}

	WebFilterChain createFilterChain(ThrowingConsumer<ServerWebExchange> exchangeConsumer) {
		return filterExchange -> {
			try {
				exchangeConsumer.accept(filterExchange);
			}
			catch (Throwable ex) {
				return Mono.error(ex);
			}
			return Mono.empty();
		};
	}

	private TestObservationRegistryAssert.TestObservationRegistryAssertReturningObservationContextAssert assertThatHttpObservation() {
		return TestObservationRegistryAssert.assertThat(this.observationRegistry)
				.hasObservationWithNameEqualTo("http.server.requests").that();
	}
}
