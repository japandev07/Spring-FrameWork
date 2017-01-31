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

package org.springframework.http.server.reactive;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Map;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.reactivex.netty.protocol.http.server.HttpServerRequest;
import io.reactivex.netty.protocol.http.server.HttpServerResponse;
import io.reactivex.netty.protocol.http.server.RequestHandler;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;
import rx.Observable;
import rx.RxReactiveStreams;

import org.springframework.core.io.buffer.NettyDataBufferFactory;

/**
 * Adapt {@link HttpHandler} to the RxNetty {@link RequestHandler}.
 *
 * @author Rossen Stoyanchev
 * @since 5.0
 */
public class RxNettyHttpHandlerAdapter extends HttpHandlerAdapterSupport
		implements RequestHandler<ByteBuf, ByteBuf> {


	public RxNettyHttpHandlerAdapter(HttpHandler httpHandler) {
		super(httpHandler);
	}

	public RxNettyHttpHandlerAdapter(Map<String, HttpHandler> handlerMap) {
		super(handlerMap);
	}


	@Override
	public Observable<Void> handle(HttpServerRequest<ByteBuf> nativeRequest,
			HttpServerResponse<ByteBuf> nativeResponse) {

		Channel channel = nativeResponse.unsafeNettyChannel();
		NettyDataBufferFactory bufferFactory = new NettyDataBufferFactory(channel.alloc());
		InetSocketAddress remoteAddress = (InetSocketAddress) channel.remoteAddress();

		RxNettyServerHttpRequest request = new RxNettyServerHttpRequest(nativeRequest, bufferFactory, remoteAddress);
		RxNettyServerHttpResponse response = new RxNettyServerHttpResponse(nativeResponse, bufferFactory);

		Publisher<Void> result = getHttpHandler().handle(request, response)
				.otherwise(ex -> {
					logger.error("Could not complete request", ex);
					nativeResponse.setStatus(HttpResponseStatus.INTERNAL_SERVER_ERROR);
					return Mono.empty();
				})
				.doOnSuccess(aVoid -> logger.debug("Successfully completed request"));

		return RxReactiveStreams.toObservable(result);
	}

}
