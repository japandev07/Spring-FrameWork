/*
 * Copyright 2002-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.websocket.adapter;

import java.nio.ByteBuffer;

import javax.websocket.CloseReason;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.util.Assert;
import org.springframework.websocket.BinaryMessage;
import org.springframework.websocket.CloseStatus;
import org.springframework.websocket.HandlerProvider;
import org.springframework.websocket.PartialMessageHandler;
import org.springframework.websocket.TextMessage;
import org.springframework.websocket.WebSocketHandler;
import org.springframework.websocket.WebSocketSession;


/**
 * An {@link Endpoint} that delegates to a {@link WebSocketHandler}.
 *
 * @author Rossen Stoyanchev
 * @since 4.0
 */
public class StandardEndpointAdapter extends Endpoint {

	private static Log logger = LogFactory.getLog(StandardEndpointAdapter.class);

	private final WebSocketHandler handler;

	private final Class<?> handlerClass;

	private WebSocketSession wsSession;



	public StandardEndpointAdapter(HandlerProvider<WebSocketHandler> provider) {
		Assert.notNull(provider, "provider is required");
		this.handler = new WebSocketHandlerInvoker(provider).setLogger(logger);
		this.handlerClass= provider.getHandlerType();
	}


	@Override
	public void onOpen(final javax.websocket.Session session, EndpointConfig config) {

		session.addMessageHandler(new MessageHandler.Whole<String>() {
			@Override
			public void onMessage(String message) {
				handleTextMessage(session, message);
			}
		});
		if (PartialMessageHandler.class.isAssignableFrom(this.handlerClass)) {
			session.addMessageHandler(new MessageHandler.Partial<ByteBuffer>() {
				@Override
				public void onMessage(ByteBuffer messagePart, boolean isLast) {
					handleBinaryMessage(session, messagePart, isLast);
				}
			});
		}
		else {
			session.addMessageHandler(new MessageHandler.Whole<ByteBuffer>() {
				@Override
				public void onMessage(ByteBuffer message) {
					handleBinaryMessage(session, message, true);
				}
			});
		}

		this.wsSession = new StandardWebSocketSessionAdapter(session);
		this.handler.afterConnectionEstablished(this.wsSession);
	}

	private void handleTextMessage(javax.websocket.Session session, String payload) {
		TextMessage message = new TextMessage(payload);
		this.handler.handleTextMessage(message, this.wsSession);
	}

	private void handleBinaryMessage(javax.websocket.Session session, ByteBuffer payload, boolean isLast) {
		BinaryMessage message = new BinaryMessage(payload, isLast);
		this.handler.handleBinaryMessage(message, this.wsSession);
	}

	@Override
	public void onClose(javax.websocket.Session session, CloseReason reason) {
		CloseStatus closeStatus = new CloseStatus(reason.getCloseCode().getCode(), reason.getReasonPhrase());
		this.handler.afterConnectionClosed(closeStatus, this.wsSession);
	}

	@Override
	public void onError(javax.websocket.Session session, Throwable exception) {
		this.handler.handleTransportError(exception, this.wsSession);
	}

}
