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

package org.springframework.web.client.reactive;

import java.util.Optional;

import org.junit.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Arjen Poutsma
 */
public class ExchangeFilterFunctionsTests {

	@Test
	public void andThen() throws Exception {
		ClientRequest<Void> request = ClientRequest.GET("http://example.com").build();
		ClientResponse response = mock(ClientResponse.class);
		ExchangeFunction exchange = r -> Mono.just(response);

		boolean[] filtersInvoked = new boolean[2];
		ExchangeFilterFunction filter1 = (r, n) -> {
			assertFalse(filtersInvoked[0]);
			assertFalse(filtersInvoked[1]);
			filtersInvoked[0] = true;
			assertFalse(filtersInvoked[1]);
			return n.exchange(r);
		};
		ExchangeFilterFunction filter2 = (r, n) -> {
			assertTrue(filtersInvoked[0]);
			assertFalse(filtersInvoked[1]);
			filtersInvoked[1] = true;
			return n.exchange(r);
		};
		ExchangeFilterFunction filter = filter1.andThen(filter2);


		ClientResponse result = filter.filter(request, exchange).block();
		assertEquals(response, result);

		assertTrue(filtersInvoked[0]);
		assertTrue(filtersInvoked[1]);
	}

	@Test
	public void apply() throws Exception {
		ClientRequest<Void> request = ClientRequest.GET("http://example.com").build();
		ClientResponse response = mock(ClientResponse.class);
		ExchangeFunction exchange = r -> Mono.just(response);

		boolean[] filterInvoked = new boolean[1];
		ExchangeFilterFunction filter = (r, n) -> {
			assertFalse(filterInvoked[0]);
			filterInvoked[0] = true;
			return n.exchange(r);
		};

		ExchangeFunction filteredExchange = filter.apply(exchange);
		ClientResponse result = filteredExchange.exchange(request).block();
		assertEquals(response, result);
		assertTrue(filterInvoked[0]);
	}


	@Test
	public void clientNoError() throws Exception {
		ClientRequest<Void> request = ClientRequest.GET("http://example.com").build();
		ClientResponse response = mock(ClientResponse.class);
		when(response.statusCode()).thenReturn(HttpStatus.OK);
		ExchangeFunction exchange = r -> Mono.just(response);

		ExchangeFilterFunction standardErrors = ExchangeFilterFunctions.clientError();

		Mono<ClientResponse> result = standardErrors.filter(request, exchange);

		StepVerifier.create(result)
				.expectNext(response)
				.expectComplete()
				.verify();
	}

	@Test
	public void serverError() throws Exception {
		ClientRequest<Void> request = ClientRequest.GET("http://example.com").build();
		ClientResponse response = mock(ClientResponse.class);
		when(response.statusCode()).thenReturn(HttpStatus.INTERNAL_SERVER_ERROR);
		ExchangeFunction exchange = r -> Mono.just(response);

		ExchangeFilterFunction standardErrors = ExchangeFilterFunctions.serverError();

		Mono<ClientResponse> result = standardErrors.filter(request, exchange);

		StepVerifier.create(result)
				.expectError(WebClientException.class)
				.verify();
	}

	@Test
	public void errorPredicate() throws Exception {
		ClientRequest<Void> request = ClientRequest.GET("http://example.com").build();
		ClientResponse response = mock(ClientResponse.class);
		when(response.statusCode()).thenReturn(HttpStatus.NOT_FOUND);
		ExchangeFunction exchange = r -> Mono.just(response);

		ExchangeFilterFunction errorPredicate = ExchangeFilterFunctions
				.errorPredicate(clientResponse -> clientResponse.statusCode().is4xxClientError());

		Mono<ClientResponse> result = errorPredicate.filter(request, exchange);

		StepVerifier.create(result)
				.expectError(WebClientException.class)
				.verify();
	}


	@Test
	public void errorMapperFunction() throws Exception {
		ClientRequest<Void> request = ClientRequest.GET("http://example.com").build();
		ClientResponse response = mock(ClientResponse.class);
		ExchangeFunction exchange = r -> Mono.just(response);

		ExchangeFilterFunction errorMapper = ExchangeFilterFunctions
				.errorMapper(clientResponse -> Optional.of(new IllegalStateException()));

		Mono<ClientResponse> result = errorMapper.filter(request, exchange);

		StepVerifier.create(result)
				.expectError(IllegalStateException.class)
				.verify();
	}


	@Test
	public void basicAuthentication() throws Exception {
		ClientRequest<Void> request = ClientRequest.GET("http://example.com").build();
		ClientResponse response = mock(ClientResponse.class);

		ExchangeFunction exchange = r -> {
			assertTrue(r.headers().containsKey(HttpHeaders.AUTHORIZATION));
			assertTrue(r.headers().getFirst(HttpHeaders.AUTHORIZATION).startsWith("Basic "));
			return Mono.just(response);
		};

		ExchangeFilterFunction auth = ExchangeFilterFunctions.basicAuthentication("foo", "bar");
		assertFalse(request.headers().containsKey(HttpHeaders.AUTHORIZATION));
		ClientResponse result = auth.filter(request, exchange).block();
		assertEquals(response, result);
	}

}