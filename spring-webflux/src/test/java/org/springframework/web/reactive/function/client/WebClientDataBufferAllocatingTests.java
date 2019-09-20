/*
 * Copyright 2002-2019 the original author or authors.
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

package org.springframework.web.reactive.function.client;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.function.Function;

import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelOption;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.buffer.AbstractDataBufferAllocatingTests;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.NettyDataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.client.reactive.ReactorResourceFactory;
import org.springframework.web.reactive.function.UnsupportedMediaTypeException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WebClient integration tests focusing on data buffer management.
 *
 * @author Rossen Stoyanchev
 * @author Sam Brannen
 */
public class WebClientDataBufferAllocatingTests extends AbstractDataBufferAllocatingTests {

	private static final Duration DELAY = Duration.ofSeconds(5);


	private final ReactorResourceFactory factory = new ReactorResourceFactory();

	private final MockWebServer server = new MockWebServer();

	private WebClient webClient;


	@BeforeEach
	public void setUp() {
		this.factory.setUseGlobalResources(false);
		this.factory.afterPropertiesSet();

		this.webClient = WebClient
				.builder()
				.clientConnector(initConnector())
				.baseUrl(this.server.url("/").toString())
				.build();
	}

	private ReactorClientHttpConnector initConnector() {
		if (bufferFactory instanceof NettyDataBufferFactory) {
			ByteBufAllocator allocator = ((NettyDataBufferFactory) bufferFactory).getByteBufAllocator();
			return new ReactorClientHttpConnector(this.factory, httpClient ->
					httpClient.tcpConfiguration(tcpClient -> tcpClient.option(ChannelOption.ALLOCATOR, allocator)));
		}
		else {
			return new ReactorClientHttpConnector();
		}
	}

	@AfterEach
	public void shutDown() throws InterruptedException {
		waitForDataBufferRelease(Duration.ofSeconds(2));
		this.factory.destroy();
	}


	@ParameterizedDataBufferAllocatingTest
	public void bodyToMonoVoid(String displayName, DataBufferFactory bufferFactory) {
		super.bufferFactory = bufferFactory;

		this.server.enqueue(new MockResponse()
				.setResponseCode(201)
				.setHeader("Content-Type", "application/json")
				.setChunkedBody("{\"foo\" : {\"bar\" : \"123\", \"baz\" : \"456\"}}", 5));

		Mono<Void> mono = this.webClient.get()
				.uri("/json").accept(MediaType.APPLICATION_JSON)
				.retrieve()
				.bodyToMono(Void.class);

		StepVerifier.create(mono).expectComplete().verify(Duration.ofSeconds(3));
		assertThat(this.server.getRequestCount()).isEqualTo(1);
	}

	@ParameterizedDataBufferAllocatingTest // SPR-17482
	public void bodyToMonoVoidWithoutContentType(String displayName, DataBufferFactory bufferFactory) {
		super.bufferFactory = bufferFactory;

		this.server.enqueue(new MockResponse()
				.setResponseCode(HttpStatus.ACCEPTED.value())
				.setChunkedBody("{\"foo\" : \"123\",  \"baz\" : \"456\", \"baz\" : \"456\"}", 5));

		Mono<Map<String, String>> mono = this.webClient.get()
				.uri("/sample").accept(MediaType.APPLICATION_JSON)
				.retrieve()
				.bodyToMono(new ParameterizedTypeReference<Map<String, String>>() {});

		StepVerifier.create(mono).expectError(UnsupportedMediaTypeException.class).verify(Duration.ofSeconds(3));
		assertThat(this.server.getRequestCount()).isEqualTo(1);
	}

	@ParameterizedDataBufferAllocatingTest
	public void onStatusWithBodyNotConsumed(String displayName, DataBufferFactory bufferFactory) {
		super.bufferFactory = bufferFactory;

		RuntimeException ex = new RuntimeException("response error");
		testOnStatus(ex, response -> Mono.just(ex));
	}

	@ParameterizedDataBufferAllocatingTest
	public void onStatusWithBodyConsumed(String displayName, DataBufferFactory bufferFactory) {
		super.bufferFactory = bufferFactory;

		RuntimeException ex = new RuntimeException("response error");
		testOnStatus(ex, response -> response.bodyToMono(Void.class).thenReturn(ex));
	}

	@ParameterizedDataBufferAllocatingTest // SPR-17473
	public void onStatusWithMonoErrorAndBodyNotConsumed(String displayName, DataBufferFactory bufferFactory) {
		super.bufferFactory = bufferFactory;

		RuntimeException ex = new RuntimeException("response error");
		testOnStatus(ex, response -> Mono.error(ex));
	}

	@ParameterizedDataBufferAllocatingTest
	public void onStatusWithMonoErrorAndBodyConsumed(String displayName, DataBufferFactory bufferFactory) {
		super.bufferFactory = bufferFactory;

		RuntimeException ex = new RuntimeException("response error");
		testOnStatus(ex, response -> response.bodyToMono(Void.class).then(Mono.error(ex)));
	}

	@ParameterizedDataBufferAllocatingTest // gh-23230
	public void onStatusWithImmediateErrorAndBodyNotConsumed(String displayName, DataBufferFactory bufferFactory) {
		super.bufferFactory = bufferFactory;

		RuntimeException ex = new RuntimeException("response error");
		testOnStatus(ex, response -> {
			throw ex;
		});
	}

	@ParameterizedDataBufferAllocatingTest
	public void releaseBody(String displayName, DataBufferFactory bufferFactory) {
		super.bufferFactory = bufferFactory;

		this.server.enqueue(new MockResponse()
				.setResponseCode(200)
				.setHeader("Content-Type", "text/plain")
				.setBody("foo bar"));

		Mono<Void> result  = this.webClient.get()
				.exchange()
				.flatMap(ClientResponse::releaseBody);


		StepVerifier.create(result)
				.expectComplete()
				.verify(Duration.ofSeconds(3));
	}

	@ParameterizedDataBufferAllocatingTest
	public void exchangeToBodilessEntity(String displayName, DataBufferFactory bufferFactory) {
		super.bufferFactory = bufferFactory;

		this.server.enqueue(new MockResponse()
				.setResponseCode(201)
				.setHeader("Foo", "bar")
				.setBody("foo bar"));

		Mono<ResponseEntity<Void>> result  = this.webClient.get()
				.exchange()
				.flatMap(ClientResponse::toBodilessEntity);

		StepVerifier.create(result)
				.assertNext(entity -> {
					assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.CREATED);
					assertThat(entity.getHeaders()).containsEntry("Foo", Collections.singletonList("bar"));
					assertThat(entity.getBody()).isNull();
				})
				.expectComplete()
				.verify(Duration.ofSeconds(3));
	}


	private void testOnStatus(Throwable expected,
			Function<ClientResponse, Mono<? extends Throwable>> exceptionFunction) {

		HttpStatus errorStatus = HttpStatus.BAD_GATEWAY;

		this.server.enqueue(new MockResponse()
				.setResponseCode(errorStatus.value())
				.setHeader("Content-Type", "application/json")
				.setChunkedBody("{\"error\" : {\"status\" : 502, \"message\" : \"Bad gateway.\"}}", 5));

		Mono<String> mono = this.webClient.get()
				.uri("/json").accept(MediaType.APPLICATION_JSON)
				.retrieve()
				.onStatus(status -> status.equals(errorStatus), exceptionFunction)
				.bodyToMono(String.class);

		StepVerifier.create(mono).expectErrorSatisfies(actual -> assertThat(actual).isSameAs(expected)).verify(DELAY);
		assertThat(this.server.getRequestCount()).isEqualTo(1);
	}

}
