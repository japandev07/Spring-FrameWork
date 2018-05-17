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

package org.springframework.http.client.reactive;

import java.net.URI;
import java.util.function.Function;

import reactor.core.publisher.Mono;
import reactor.netty.NettyInbound;
import reactor.netty.NettyOutbound;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.HttpClientRequest;
import reactor.netty.http.client.HttpClientResponse;

import org.springframework.http.HttpMethod;

import io.netty.buffer.ByteBufAllocator;

/**
 * Reactor-Netty implementation of {@link ClientHttpConnector}.
 *
 * @author Brian Clozel
 * @since 5.0
 * @see reactor.netty.http.client.HttpClient
 */
public class ReactorClientHttpConnector implements ClientHttpConnector {

	private final HttpClient httpClient;


	/**
	 * Create a Reactor Netty {@link ClientHttpConnector}
	 * with a default configuration and HTTP compression support enabled.
	 */
	public ReactorClientHttpConnector() {
		this.httpClient = HttpClient.create()
									.compress();
	}

	/**
	 * Create a Reactor Netty {@link ClientHttpConnector} with the given
	 * {@link HttpClient}
	 */
	public ReactorClientHttpConnector(HttpClient httpClient) {
		this.httpClient = httpClient;
	}


	@Override
	public Mono<ClientHttpResponse> connect(HttpMethod method, URI uri,
			Function<? super ClientHttpRequest, Mono<Void>> requestCallback) {

		if (!uri.isAbsolute()) {
			return Mono.error(new IllegalArgumentException("URI is not absolute: " + uri));
		}

		return this.httpClient
				.request(adaptHttpMethod(method))
				.uri(uri.toString())
				.send((req, out) -> requestCallback.apply(adaptRequest(method, uri, req, out)))
				.responseConnection((res, con) -> Mono.just(adaptResponse(res, con.inbound(), con.outbound().alloc())))
				.next();
	}

	private io.netty.handler.codec.http.HttpMethod adaptHttpMethod(HttpMethod method) {
		return io.netty.handler.codec.http.HttpMethod.valueOf(method.name());
	}

	private ReactorClientHttpRequest adaptRequest(HttpMethod method, URI uri, HttpClientRequest request, NettyOutbound out) {
		return new ReactorClientHttpRequest(method, uri, request, out);
	}

	private ClientHttpResponse adaptResponse(HttpClientResponse response, NettyInbound nettyInbound,
			ByteBufAllocator alloc) {
		return new ReactorClientHttpResponse(response, nettyInbound, alloc);
	}

}
