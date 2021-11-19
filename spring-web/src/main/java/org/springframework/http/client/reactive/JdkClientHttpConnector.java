/*
 * Copyright 2002-2021 the original author or authors.
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

package org.springframework.http.client.reactive;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.function.Function;

import reactor.core.publisher.Mono;

import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpMethod;

/**
 * {@link ClientHttpConnector} for the Java {@link HttpClient}.
 *
 * @author Julien Eyraud
 * @author Rossen Stoyanchev
 * @since 6.0
 * @see <a href="https://docs.oracle.com/en/java/javase/11/docs/api/java.net.http/java/net/http/HttpClient.html">HttpClient</a>
 */
public class JdkClientHttpConnector implements ClientHttpConnector {

	private final HttpClient httpClient;

	private final DataBufferFactory bufferFactory;


	/**
	 * Default constructor that uses {@link HttpClient#newHttpClient()}.
	 */
	public JdkClientHttpConnector() {
		this(HttpClient.newHttpClient(), new DefaultDataBufferFactory());
	}

	/**
	 * Constructor with an initialized {@link HttpClient} and a {@link DataBufferFactory}.
	 */
	public JdkClientHttpConnector(HttpClient httpClient, DataBufferFactory bufferFactory) {
		this.httpClient = httpClient;
		this.bufferFactory = bufferFactory;
	}


	@Override
	public Mono<ClientHttpResponse> connect(
			HttpMethod method, URI uri, Function<? super ClientHttpRequest, Mono<Void>> requestCallback) {

		JdkClientHttpRequest jdkClientHttpRequest = new JdkClientHttpRequest(method, uri, this.bufferFactory);

		return requestCallback.apply(jdkClientHttpRequest).then(Mono.defer(() -> {
			HttpRequest httpRequest = jdkClientHttpRequest.getNativeRequest();

			CompletableFuture<HttpResponse<Flow.Publisher<List<ByteBuffer>>>> future =
					this.httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofPublisher());

			return Mono.fromCompletionStage(future)
					.map(response -> new JdkClientHttpResponse(response, this.bufferFactory));
		}));
	}

}
