/*
 * Copyright 2002-2013 the original author or authors.
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
package org.springframework.web.messaging.stomp.socket;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.util.Assert;
import org.springframework.web.messaging.stomp.StompCommand;
import org.springframework.web.messaging.stomp.StompHeaders;
import org.springframework.web.messaging.stomp.StompMessage;
import org.springframework.web.messaging.stomp.StompSession;
import org.springframework.web.messaging.stomp.support.StompMessageConverter;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.adapter.TextWebSocketHandlerAdapter;


/**
 * @author Rossen Stoyanchev
 * @since 4.0
 */
public abstract class AbstractStompWebSocketHandler extends TextWebSocketHandlerAdapter {

	private final StompMessageConverter messageConverter = new StompMessageConverter();

	private final Map<String, WebSocketStompSession> sessions = new ConcurrentHashMap<String, WebSocketStompSession>();


	@Override
	public void afterConnectionEstablished(WebSocketSession session) throws Exception {
		WebSocketStompSession stompSession = new WebSocketStompSession(session, this.messageConverter);
		this.sessions.put(session.getId(), stompSession);
	}

	@Override
	protected void handleTextMessage(WebSocketSession session, TextMessage message) {

		StompSession stompSession = this.sessions.get(session.getId());
		Assert.notNull(stompSession, "No STOMP session for WebSocket session id=" + session.getId());

		try {
			StompMessage stompMessage = this.messageConverter.toStompMessage(message.getPayload());
			stompMessage.setSessionId(stompSession.getId());

			// TODO: validate size limits
			// http://stomp.github.io/stomp-specification-1.2.html#Size_Limits

			handleStompMessage(stompSession, stompMessage);

			// TODO: send RECEIPT message if incoming message has "receipt" header
			// http://stomp.github.io/stomp-specification-1.2.html#Header_receipt

		}
		catch (Throwable error) {
			StompHeaders headers = new StompHeaders();
			headers.setMessage(error.getMessage());
			StompMessage errorMessage = new StompMessage(StompCommand.ERROR, headers);
			try {
				stompSession.sendMessage(errorMessage);
			}
			catch (Throwable t) {
				// ignore
			}
		}
	}

	protected abstract void handleStompMessage(StompSession stompSession, StompMessage stompMessage);

	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
		WebSocketStompSession stompSession = this.sessions.remove(session.getId());
		if (stompSession != null) {
			stompSession.handleConnectionClosed();
		}
	}

}
