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

package org.springframework.http.client.reactive;

import java.net.URI;
import java.util.function.Function;

import reactor.core.publisher.Mono;
import reactor.ipc.netty.config.ClientOptions;
import reactor.ipc.netty.http.HttpException;
import reactor.ipc.netty.http.HttpInbound;

import org.springframework.http.HttpMethod;

/**
 * Reactor-Netty implementation of {@link ClientHttpConnector}
 *
 * @author Brian Clozel
 * @since 5.0
 * @see reactor.ipc.netty.http.HttpClient
 */
public class ReactorClientHttpConnector implements ClientHttpConnector {

	private final ClientOptions clientOptions;


	/**
	 * Create a Reactor Netty {@link ClientHttpConnector} with default {@link ClientOptions}
	 * and SSL support enabled.
	 */
	public ReactorClientHttpConnector() {
		this(ClientOptions.create().sslSupport());
	}

	/**
	 * Create a Reactor Netty {@link ClientHttpConnector} with the given {@link ClientOptions}
	 */
	public ReactorClientHttpConnector(ClientOptions clientOptions) {
		this.clientOptions = clientOptions;
	}


	@Override
	public Mono<ClientHttpResponse> connect(HttpMethod method, URI uri,
			Function<? super ClientHttpRequest, Mono<Void>> requestCallback) {

		return reactor.ipc.netty.http.HttpClient.create(this.clientOptions)
				.request(io.netty.handler.codec.http.HttpMethod.valueOf(method.name()),
						uri.toString(),
						httpClientRequest -> requestCallback
								.apply(new ReactorClientHttpRequest(method, uri, httpClientRequest)))
				.cast(HttpInbound.class)
				.otherwise(HttpException.class, exc -> Mono.just(exc.getChannel()))
				.map(httpInbound -> new ReactorClientHttpResponse(httpInbound));
	}

}
