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

package org.springframework.http.server.reactive;

import java.util.Map;
import java.util.function.Function;

import io.netty.handler.codec.http.HttpResponseStatus;
import reactor.core.publisher.Mono;
import reactor.ipc.netty.http.HttpChannel;

import org.springframework.core.io.buffer.NettyDataBufferFactory;

/**
 * Adapt {@link HttpHandler} to the Reactor Netty channel handling function.
 *
 * @author Stephane Maldini
 * @since 5.0
 */
public class ReactorHttpHandlerAdapter extends HttpHandlerAdapterSupport
		implements Function<HttpChannel, Mono<Void>> {


	public ReactorHttpHandlerAdapter(HttpHandler httpHandler) {
		super(httpHandler);
	}

	public ReactorHttpHandlerAdapter(Map<String, HttpHandler> handlerMap) {
		super(handlerMap);
	}


	@Override
	public Mono<Void> apply(HttpChannel channel) {

		NettyDataBufferFactory bufferFactory = new NettyDataBufferFactory(channel.delegate().alloc());
		ReactorServerHttpRequest request = new ReactorServerHttpRequest(channel, bufferFactory);
		ReactorServerHttpResponse response = new ReactorServerHttpResponse(channel, bufferFactory);

		return getHttpHandler().handle(request, response)
				.otherwise(ex -> {
					logger.error("Could not complete request", ex);
					channel.status(HttpResponseStatus.INTERNAL_SERVER_ERROR);
					return Mono.empty();
				})
				.doOnSuccess(aVoid -> logger.debug("Successfully completed request"));
	}

}
