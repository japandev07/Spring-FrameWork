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

package org.springframework.web.socket.adapter;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketError;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketFrame;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.eclipse.jetty.websocket.api.extensions.Frame;
import org.eclipse.jetty.websocket.common.OpCode;
import org.springframework.util.Assert;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PongMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.support.ExceptionWebSocketHandlerDecorator;

/**
 * Adapts {@link WebSocketHandler} to the Jetty 9 WebSocket API.
 *
 * @author Rossen Stoyanchev
 * @since 4.0
 */
@WebSocket
public class JettyWebSocketHandlerAdapter {

	private static final Log logger = LogFactory.getLog(JettyWebSocketHandlerAdapter.class);

	private final WebSocketHandler webSocketHandler;

	private final JettyWebSocketSession wsSession;


	public JettyWebSocketHandlerAdapter(WebSocketHandler webSocketHandler, JettyWebSocketSession wsSession) {

		Assert.notNull(webSocketHandler, "webSocketHandler must not be null");
		Assert.notNull(wsSession, "wsSession must not be null");

		this.webSocketHandler = webSocketHandler;
		this.wsSession = wsSession;
	}


	@OnWebSocketConnect
	public void onWebSocketConnect(Session session) {
		try {
			this.wsSession.initializeNativeSession(session);
			this.webSocketHandler.afterConnectionEstablished(this.wsSession);
		}
		catch (Throwable t) {
			ExceptionWebSocketHandlerDecorator.tryCloseWithError(this.wsSession, t, logger);
		}
	}

	@OnWebSocketMessage
	public void onWebSocketText(String payload) {
		TextMessage message = new TextMessage(payload);
		try {
			this.webSocketHandler.handleMessage(this.wsSession, message);
		}
		catch (Throwable t) {
			ExceptionWebSocketHandlerDecorator.tryCloseWithError(this.wsSession, t, logger);
		}
	}

	@OnWebSocketMessage
	public void onWebSocketBinary(byte[] payload, int offset, int length) {
		BinaryMessage message = new BinaryMessage(payload, offset, length, true);
		try {
			this.webSocketHandler.handleMessage(this.wsSession, message);
		}
		catch (Throwable t) {
			ExceptionWebSocketHandlerDecorator.tryCloseWithError(this.wsSession, t, logger);
		}
	}

	@OnWebSocketFrame
	public void onWebSocketFrame(Frame frame) {
		if (OpCode.PONG == frame.getOpCode()) {
			PongMessage message = new PongMessage(frame.getPayload());
			try {
				this.webSocketHandler.handleMessage(this.wsSession, message);
			}
			catch (Throwable t) {
				ExceptionWebSocketHandlerDecorator.tryCloseWithError(this.wsSession, t, logger);
			}
		}
	}

	@OnWebSocketClose
	public void onWebSocketClose(int statusCode, String reason) {
		CloseStatus closeStatus = new CloseStatus(statusCode, reason);
		try {
			this.webSocketHandler.afterConnectionClosed(this.wsSession, closeStatus);
		}
		catch (Throwable t) {
			logger.error("Unhandled error for " + this.wsSession, t);
		}
	}

	@OnWebSocketError
	public void onWebSocketError(Throwable cause) {
		try {
			this.webSocketHandler.handleTransportError(this.wsSession, cause);
		}
		catch (Throwable t) {
			ExceptionWebSocketHandlerDecorator.tryCloseWithError(this.wsSession, t, logger);
		}
	}

}
