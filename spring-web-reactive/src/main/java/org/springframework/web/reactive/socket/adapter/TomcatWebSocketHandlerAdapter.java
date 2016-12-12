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

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import javax.websocket.CloseReason;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.PongMessage;
import javax.websocket.Session;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.util.Assert;
import org.springframework.web.reactive.socket.CloseStatus;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketMessage.Type;

/**
 * Tomcat {@code WebSocketHandler} implementation adapting and
 * delegating to a Spring {@link WebSocketHandler}.
 * 
 * @author Violeta Georgieva
 * @since 5.0
 */
public class TomcatWebSocketHandlerAdapter extends Endpoint {

	private final DataBufferFactory bufferFactory = new DefaultDataBufferFactory(false);

	private final WebSocketHandler handler;

	private TomcatWebSocketSession wsSession;

	public TomcatWebSocketHandlerAdapter(WebSocketHandler handler) {
		Assert.notNull("'handler' is required");
		this.handler = handler;
	}

	@Override
	public void onOpen(Session session, EndpointConfig config) {
		this.wsSession = new TomcatWebSocketSession(session);

		session.addMessageHandler(new MessageHandler.Whole<String>() {

			@Override
			public void onMessage(String message) {
				WebSocketMessage wsMessage = toMessage(message);
				wsSession.handleMessage(wsMessage.getType(), wsMessage);
			}

		});
		session.addMessageHandler(new MessageHandler.Whole<ByteBuffer>() {

			@Override
			public void onMessage(ByteBuffer message) {
				WebSocketMessage wsMessage = toMessage(message);
				wsSession.handleMessage(wsMessage.getType(), wsMessage);
			}

		});
		session.addMessageHandler(new MessageHandler.Whole<PongMessage>() {

			@Override
			public void onMessage(PongMessage message) {
				WebSocketMessage wsMessage = toMessage(message);
				wsSession.handleMessage(wsMessage.getType(), wsMessage);
			}

		});

		HandlerResultSubscriber resultSubscriber = new HandlerResultSubscriber();
		this.handler.handle(this.wsSession).subscribe(resultSubscriber);
	}

	@Override
	public void onClose(Session session, CloseReason reason) {
		if (this.wsSession != null) {
			this.wsSession.handleClose(
					new CloseStatus(reason.getCloseCode().getCode(), reason.getReasonPhrase()));
		}
	}

	@Override
	public void onError(Session session, Throwable exception) {
		if (this.wsSession != null) {
			this.wsSession.handleError(exception);
		}
	}

	private <T> WebSocketMessage toMessage(T message) {
		if (message instanceof String) {
			return WebSocketMessage.create(Type.TEXT,
					bufferFactory.wrap(((String) message).getBytes(StandardCharsets.UTF_8)));
		}
		else if (message instanceof ByteBuffer) {
			return WebSocketMessage.create(Type.BINARY,
					bufferFactory.wrap((ByteBuffer) message));
		}
		else if (message instanceof PongMessage) {
			return WebSocketMessage.create(Type.PONG,
					bufferFactory.wrap(((PongMessage) message).getApplicationData()));
		}
		else {
			throw new IllegalArgumentException("Unexpected message type: " + message);
		}
	}

	private final class HandlerResultSubscriber implements Subscriber<Void> {

		@Override
		public void onSubscribe(Subscription subscription) {
			subscription.request(Long.MAX_VALUE);
		}

		@Override
		public void onNext(Void aVoid) {
			// no op
		}

		@Override
		public void onError(Throwable ex) {
			if (wsSession != null) {
				wsSession.close(new CloseStatus(CloseStatus.SERVER_ERROR.getCode(), ex.getMessage()));
			}
		}

		@Override
		public void onComplete() {
			if (wsSession != null) {
				wsSession.close();
			}
		}
	}

}
