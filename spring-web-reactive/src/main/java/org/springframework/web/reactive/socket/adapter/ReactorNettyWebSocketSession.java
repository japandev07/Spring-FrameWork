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
package org.springframework.web.reactive.socket.adapter;

import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.ipc.netty.NettyInbound;
import reactor.ipc.netty.NettyOutbound;
import reactor.ipc.netty.NettyPipeline;

import org.springframework.core.io.buffer.NettyDataBufferFactory;
import org.springframework.web.reactive.socket.CloseStatus;
import org.springframework.web.reactive.socket.HandshakeInfo;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;


/**
 * Spring {@link WebSocketSession} implementation that adapts to Reactor Netty's
 * WebSocket {@link NettyInbound} and {@link NettyOutbound}.
 *
 * @author Rossen Stoyanchev
 * @since 5.0
 */
public class ReactorNettyWebSocketSession
		extends NettyWebSocketSessionSupport<ReactorNettyWebSocketSession.WebSocketConnection> {


	public ReactorNettyWebSocketSession(NettyInbound inbound, NettyOutbound outbound,
			HandshakeInfo info, NettyDataBufferFactory bufferFactory) {

		super(new WebSocketConnection(inbound, outbound), info, bufferFactory);
	}


	@Override
	public Flux<WebSocketMessage> receive() {
		NettyInbound inbound = getDelegate().getInbound();
		return toMessageFlux(inbound.receiveObject().cast(WebSocketFrame.class));
	}

	@Override
	public Mono<Void> send(Publisher<WebSocketMessage> messages) {
		Flux<WebSocketFrame> frameFlux = Flux.from(messages).map(this::toFrame);
		NettyOutbound outbound = getDelegate().getOutbound();
		return outbound.options(NettyPipeline.SendOptions::flushOnEach)
		               .sendObject(frameFlux)
		               .then();
	}

	@Override
	public Mono<Void> close(CloseStatus status) {
		return Mono.error(new UnsupportedOperationException(
				"Currently in Reactor Netty applications are expected to use the " +
						"Cancellation returned from subscribing to the \"receive\"-side Flux " +
						"in order to close the WebSocket session."));
	}


	/**
	 * Simple container for {@link NettyInbound} and {@link NettyOutbound}.
	 */
	public static class WebSocketConnection {

		private final NettyInbound inbound;

		private final NettyOutbound outbound;


		public WebSocketConnection(NettyInbound inbound, NettyOutbound outbound) {
			this.inbound = inbound;
			this.outbound = outbound;
		}

		public NettyInbound getInbound() {
			return this.inbound;
		}

		public NettyOutbound getOutbound() {
			return this.outbound;
		}
	}

}
