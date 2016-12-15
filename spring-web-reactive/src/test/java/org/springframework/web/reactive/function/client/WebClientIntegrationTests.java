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

package org.springframework.web.reactive.function.client;

import java.time.Duration;

import okhttp3.HttpUrl;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.codec.Pojo;
import org.springframework.web.reactive.function.BodyExtractors;
import org.springframework.web.reactive.function.BodyInserters;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.springframework.web.reactive.function.BodyExtractors.toFlux;
import static org.springframework.web.reactive.function.BodyExtractors.toMono;

/**
 * {@link WebClient} integration tests with the {@code Flux} and {@code Mono} API.
 *
 * @author Brian Clozel
 */
public class WebClientIntegrationTests {

	private MockWebServer server;

	private WebClient webClient;

	@Before
	public void setup() {
		this.server = new MockWebServer();
		this.webClient = WebClient.create(new ReactorClientHttpConnector());
	}

	@Test
	public void headers() throws Exception {
		HttpUrl baseUrl = server.url("/greeting?name=Spring");
		this.server.enqueue(new MockResponse().setHeader("Content-Type", "text/plain").setBody("Hello Spring!"));

		ClientRequest<Void> request = ClientRequest.GET(baseUrl.toString()).build();
		Mono<HttpHeaders> result = this.webClient
				.exchange(request)
				.map(response -> response.headers().asHttpHeaders());

		StepVerifier.create(result)
				.consumeNextWith(
						httpHeaders -> {
							assertEquals(MediaType.TEXT_PLAIN, httpHeaders.getContentType());
							assertEquals(13L, httpHeaders.getContentLength());
						})
				.expectComplete()
				.verify(Duration.ofSeconds(3));

		RecordedRequest recordedRequest = server.takeRequest();
		Assert.assertEquals(1, server.getRequestCount());
		Assert.assertEquals("*/*", recordedRequest.getHeader(HttpHeaders.ACCEPT));
		Assert.assertEquals("/greeting?name=Spring", recordedRequest.getPath());
	}

	@Test
	public void plainText() throws Exception {
		HttpUrl baseUrl = server.url("/greeting?name=Spring");
		this.server.enqueue(new MockResponse().setBody("Hello Spring!"));

		ClientRequest<Void> request = ClientRequest.GET(baseUrl.toString())
				.header("X-Test-Header", "testvalue")
				.build();

		Mono<String> result = this.webClient
				.exchange(request)
				.then(response -> response.body(toMono(String.class)));

		StepVerifier.create(result)
				.expectNext("Hello Spring!")
				.expectComplete()
				.verify(Duration.ofSeconds(3));

		RecordedRequest recordedRequest = server.takeRequest();
		Assert.assertEquals(1, server.getRequestCount());
		Assert.assertEquals("testvalue", recordedRequest.getHeader("X-Test-Header"));
		Assert.assertEquals("*/*", recordedRequest.getHeader(HttpHeaders.ACCEPT));
		Assert.assertEquals("/greeting?name=Spring", recordedRequest.getPath());
	}

	@Test
	public void jsonString() throws Exception {
		HttpUrl baseUrl = server.url("/json");
		String content = "{\"bar\":\"barbar\",\"foo\":\"foofoo\"}";
		this.server.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
				.setBody(content));

		ClientRequest<Void> request = ClientRequest.GET(baseUrl.toString())
				.accept(MediaType.APPLICATION_JSON)
				.build();

		Mono<String> result = this.webClient
				.exchange(request)
				.then(response -> response.body(toMono(String.class)));

		StepVerifier.create(result)
				.expectNext(content)
				.expectComplete()
				.verify(Duration.ofSeconds(3));

		RecordedRequest recordedRequest = server.takeRequest();
		Assert.assertEquals(1, server.getRequestCount());
		Assert.assertEquals("/json", recordedRequest.getPath());
		Assert.assertEquals("application/json", recordedRequest.getHeader(HttpHeaders.ACCEPT));
	}

	@Test
	public void jsonPojoMono() throws Exception {
		HttpUrl baseUrl = server.url("/pojo");
		this.server.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
				.setBody("{\"bar\":\"barbar\",\"foo\":\"foofoo\"}"));

		ClientRequest<Void> request = ClientRequest.GET(baseUrl.toString())
				.accept(MediaType.APPLICATION_JSON)
				.build();

		Mono<Pojo> result = this.webClient
				.exchange(request)
				.then(response -> response.body(toMono(Pojo.class)));

		StepVerifier.create(result)
				.consumeNextWith(p -> assertEquals("barbar", p.getBar()))
				.expectComplete()
				.verify(Duration.ofSeconds(3));

		RecordedRequest recordedRequest = server.takeRequest();
		Assert.assertEquals(1, server.getRequestCount());
		Assert.assertEquals("/pojo", recordedRequest.getPath());
		Assert.assertEquals("application/json", recordedRequest.getHeader(HttpHeaders.ACCEPT));
	}

	@Test
	public void jsonPojoFlux() throws Exception {
		HttpUrl baseUrl = server.url("/pojos");
		this.server.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
				.setBody("[{\"bar\":\"bar1\",\"foo\":\"foo1\"},{\"bar\":\"bar2\",\"foo\":\"foo2\"}]"));

		ClientRequest<Void> request = ClientRequest.GET(baseUrl.toString())
				.accept(MediaType.APPLICATION_JSON)
				.build();

		Flux<Pojo> result = this.webClient
				.exchange(request)
				.flatMap(response -> response.body(toFlux(Pojo.class)));

		StepVerifier.create(result)
				.consumeNextWith(p -> assertThat(p.getBar(), Matchers.is("bar1")))
				.consumeNextWith(p -> assertThat(p.getBar(), Matchers.is("bar2")))
				.expectComplete()
				.verify(Duration.ofSeconds(3));

		RecordedRequest recordedRequest = server.takeRequest();
		Assert.assertEquals(1, server.getRequestCount());
		Assert.assertEquals("/pojos", recordedRequest.getPath());
		Assert.assertEquals("application/json", recordedRequest.getHeader(HttpHeaders.ACCEPT));
	}

	@Test
	public void postJsonPojo() throws Exception {
		HttpUrl baseUrl = server.url("/pojo/capitalize");
		this.server.enqueue(new MockResponse()
				.setHeader("Content-Type", "application/json")
				.setBody("{\"bar\":\"BARBAR\",\"foo\":\"FOOFOO\"}"));

		Pojo spring = new Pojo("foofoo", "barbar");
		ClientRequest<Pojo> request = ClientRequest.POST(baseUrl.toString())
				.accept(MediaType.APPLICATION_JSON)
				.contentType(MediaType.APPLICATION_JSON)
				.body(BodyInserters.fromObject(spring));

		Mono<Pojo> result = this.webClient
				.exchange(request)
				.then(response -> response.body(BodyExtractors.toMono(Pojo.class)));

		StepVerifier.create(result)
				.consumeNextWith(p -> assertEquals("BARBAR", p.getBar()))
				.expectComplete()
				.verify(Duration.ofSeconds(3));

		RecordedRequest recordedRequest = server.takeRequest();
		Assert.assertEquals(1, server.getRequestCount());
		Assert.assertEquals("/pojo/capitalize", recordedRequest.getPath());
		Assert.assertEquals("{\"foo\":\"foofoo\",\"bar\":\"barbar\"}", recordedRequest.getBody().readUtf8());
		Assert.assertEquals("chunked", recordedRequest.getHeader(HttpHeaders.TRANSFER_ENCODING));
		Assert.assertEquals("application/json", recordedRequest.getHeader(HttpHeaders.ACCEPT));
		Assert.assertEquals("application/json", recordedRequest.getHeader(HttpHeaders.CONTENT_TYPE));
	}

	@Test
	public void cookies() throws Exception {
		HttpUrl baseUrl = server.url("/test");
		this.server.enqueue(new MockResponse()
				.setHeader("Content-Type", "text/plain").setBody("test"));

		ClientRequest<Void> request = ClientRequest.GET(baseUrl.toString())
				.cookie("testkey", "testvalue")
				.build();

		Mono<String> result = this.webClient
				.exchange(request)
				.then(response -> response.body(toMono(String.class)));

		StepVerifier.create(result)
				.expectNext("test")
				.expectComplete()
				.verify(Duration.ofSeconds(3));

		RecordedRequest recordedRequest = server.takeRequest();
		Assert.assertEquals(1, server.getRequestCount());
		Assert.assertEquals("/test", recordedRequest.getPath());
		Assert.assertEquals("testkey=testvalue", recordedRequest.getHeader(HttpHeaders.COOKIE));
	}

	@Test
	public void notFound() throws Exception {
		HttpUrl baseUrl = server.url("/greeting?name=Spring");
		this.server.enqueue(new MockResponse().setResponseCode(404)
				.setHeader("Content-Type", "text/plain").setBody("Not Found"));

		ClientRequest<Void> request = ClientRequest.GET(baseUrl.toString()).build();

		Mono<ClientResponse> result = this.webClient
				.exchange(request);

		StepVerifier.create(result)
				.consumeNextWith(response -> {
					assertEquals(HttpStatus.NOT_FOUND, response.statusCode());
				})
				.expectComplete()
				.verify(Duration.ofSeconds(3));

		RecordedRequest recordedRequest = server.takeRequest();
		Assert.assertEquals(1, server.getRequestCount());
		Assert.assertEquals("*/*", recordedRequest.getHeader(HttpHeaders.ACCEPT));
		Assert.assertEquals("/greeting?name=Spring", recordedRequest.getPath());
	}

	@Test
	public void buildFilter() throws Exception {
		HttpUrl baseUrl = server.url("/greeting?name=Spring");
		this.server.enqueue(new MockResponse().setHeader("Content-Type", "text/plain").setBody("Hello Spring!"));

		ExchangeFilterFunction filter = (request, next) -> {
			ClientRequest<?> filteredRequest = ClientRequest.from(request)
					.header("foo", "bar").build();
			return next.exchange(filteredRequest);
		};
		WebClient filteredClient = WebClient.builder(new ReactorClientHttpConnector())
				.filter(filter).build();

		ClientRequest<Void> request = ClientRequest.GET(baseUrl.toString()).build();

		Mono<String> result = filteredClient.exchange(request)
				.then(response -> response.body(toMono(String.class)));

		StepVerifier.create(result)
				.expectNext("Hello Spring!")
				.expectComplete()
				.verify(Duration.ofSeconds(3));

		RecordedRequest recordedRequest = server.takeRequest();
		Assert.assertEquals(1, server.getRequestCount());
		Assert.assertEquals("bar", recordedRequest.getHeader("foo"));

	}

	@Test
	public void filter() throws Exception {
		HttpUrl baseUrl = server.url("/greeting?name=Spring");
		this.server.enqueue(new MockResponse().setHeader("Content-Type", "text/plain").setBody("Hello Spring!"));

		ExchangeFilterFunction filter = (request, next) -> {
			ClientRequest<?> filteredRequest = ClientRequest.from(request)
					.header("foo", "bar").build();
			return next.exchange(filteredRequest);
		};
		WebClient client = WebClient.create(new ReactorClientHttpConnector());
		WebClient filteredClient = client.filter(filter);

		ClientRequest<Void> request = ClientRequest.GET(baseUrl.toString()).build();

		Mono<String> result = filteredClient.exchange(request)
				.then(response -> response.body(toMono(String.class)));

		StepVerifier.create(result)
				.expectNext("Hello Spring!")
				.expectComplete()
				.verify(Duration.ofSeconds(3));

		RecordedRequest recordedRequest = server.takeRequest();
		Assert.assertEquals(1, server.getRequestCount());
		Assert.assertEquals("bar", recordedRequest.getHeader("foo"));

	}

	@After
	public void tearDown() throws Exception {
		this.server.shutdown();
	}
}