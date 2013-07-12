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
package org.springframework.messaging.simp.stomp;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.adapter.TextWebSocketHandlerAdapter;

import reactor.util.Assert;


/**
 * @author Rossen Stoyanchev
 * @since 4.0
 */
public class StompWebSocketHandler extends TextWebSocketHandlerAdapter implements MessageHandler {

	private static final byte[] EMPTY_PAYLOAD = new byte[0];

	private static Log logger = LogFactory.getLog(StompWebSocketHandler.class);

	private MessageChannel outputChannel;

	private final StompMessageConverter stompMessageConverter = new StompMessageConverter();

	private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<String, WebSocketSession>();


	/**
	 * @param outputChannel the channel to which incoming STOMP/WebSocket messages should
	 *        be sent to
	 */
	public StompWebSocketHandler(MessageChannel outputChannel) {
		Assert.notNull(outputChannel, "clientInputChannel is required");
		this.outputChannel = outputChannel;
	}


	public StompMessageConverter getStompMessageConverter() {
		return this.stompMessageConverter;
	}

	@Override
	public void afterConnectionEstablished(WebSocketSession session) throws Exception {
		Assert.notNull(this.outputChannel, "No output channel for STOMP messages.");
		this.sessions.put(session.getId(), session);
	}

	/**
	 * Handle incoming WebSocket messages from clients.
	 */
	@Override
	protected void handleTextMessage(WebSocketSession session, TextMessage textMessage) {
		try {
			String payload = textMessage.getPayload();
			Message<?> message = this.stompMessageConverter.toMessage(payload);

			StompHeaderAccessor headers = StompHeaderAccessor.wrap(message);
			headers.setSessionId(session.getId());
			headers.setUser(session.getPrincipal());

			// TODO: validate size limits
			// http://stomp.github.io/stomp-specification-1.2.html#Size_Limits

			if (logger.isTraceEnabled()) {
				logger.trace("Processing STOMP message: " + message);
			}

			try {
				StompHeaderAccessor stompHeaders = StompHeaderAccessor.wrap(message);
				SimpMessageType messageType = stompHeaders.getMessageType();
				if (SimpMessageType.CONNECT.equals(messageType)) {
					handleConnect(session, message);
				}

				message = MessageBuilder.fromMessage(message).copyHeaders(headers.toMap()).build();
				this.outputChannel.send(message);
			}
			catch (Throwable t) {
				logger.error("Terminating STOMP session due to failure to send message: ", t);
				sendErrorMessage(session, t);
			}

			// TODO: send RECEIPT message if incoming message has "receipt" header
			// http://stomp.github.io/stomp-specification-1.2.html#Header_receipt

		}
		catch (Throwable error) {
			sendErrorMessage(session, error);
		}
	}

	protected void handleConnect(WebSocketSession session, Message<?> message) throws IOException {

		StompHeaderAccessor connectHeaders = StompHeaderAccessor.wrap(message);
		StompHeaderAccessor connectedHeaders = StompHeaderAccessor.create(StompCommand.CONNECTED);

		Set<String> acceptVersions = connectHeaders.getAcceptVersion();
		if (acceptVersions.contains("1.2")) {
			connectedHeaders.setAcceptVersion("1.2");
		}
		else if (acceptVersions.contains("1.1")) {
			connectedHeaders.setAcceptVersion("1.1");
		}
		else if (acceptVersions.isEmpty()) {
			// 1.0
		}
		else {
			throw new StompConversionException("Unsupported version '" + acceptVersions + "'");
		}
		connectedHeaders.setHeartbeat(0,0); // TODO

		// TODO: security

		Message<?> connectedMessage = MessageBuilder.withPayload(EMPTY_PAYLOAD).copyHeaders(
				connectedHeaders.toMap()).build();
		byte[] bytes = this.stompMessageConverter.fromMessage(connectedMessage);
		session.sendMessage(new TextMessage(new String(bytes, Charset.forName("UTF-8"))));
	}

	protected void sendErrorMessage(WebSocketSession session, Throwable error) {

		StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.ERROR);
		headers.setMessage(error.getMessage());

		Message<?> message = MessageBuilder.withPayload(EMPTY_PAYLOAD).copyHeaders(headers.toMap()).build();
		byte[] bytes = this.stompMessageConverter.fromMessage(message);

		try {
			session.sendMessage(new TextMessage(new String(bytes, Charset.forName("UTF-8"))));
		}
		catch (Throwable t) {
			// ignore
		}
	}

	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {

		this.sessions.remove(session.getId());

		SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.create(SimpMessageType.DISCONNECT);
		headers.setSessionId(session.getId());
		Message<?> message = MessageBuilder.withPayload(new byte[0]).copyHeaders(headers.toMap()).build();
		this.outputChannel.send(message);
	}

	/**
	 * Handle STOMP messages going back out to WebSocket clients.
	 */
	@Override
	public void handleMessage(Message<?> message) {

		StompHeaderAccessor headers = StompHeaderAccessor.wrap(message);
		headers.setStompCommandIfNotSet(StompCommand.MESSAGE);

		if (StompCommand.CONNECTED.equals(headers.getStompCommand())) {
			// Ignore for now since we already sent it
			return;
		}

		String sessionId = headers.getSessionId();
		if (sessionId == null) {
			// TODO: failed message delivery mechanism
			logger.error("Ignoring message, no sessionId header: " + message);
			return;
		}

		WebSocketSession session = this.sessions.get(sessionId);
		if (session == null) {
			// TODO: failed message delivery mechanism
			logger.error("Ignoring message, session not found: " + sessionId);
			return;
		}

		if (headers.getSubscriptionId() == null) {
			// TODO: failed message delivery mechanism
			logger.error("Ignoring message, no subscriptionId header: " + message);
			return;
		}

		if (!(message.getPayload() instanceof byte[])) {
			// TODO: failed message delivery mechanism
			logger.error("Ignoring message, expected byte[] content: " + message);
			return;
		}

		try {
			message = MessageBuilder.fromMessage(message).copyHeaders(headers.toMap()).build();
			byte[] bytes = this.stompMessageConverter.fromMessage(message);
			session.sendMessage(new TextMessage(new String(bytes, Charset.forName("UTF-8"))));
		}
		catch (Throwable t) {
			sendErrorMessage(session, t);
		}
		finally {
			if (StompCommand.ERROR.equals(headers.getStompCommand())) {
				try {
					session.close(CloseStatus.PROTOCOL_ERROR);
				}
				catch (IOException e) {
				}
			}
		}
	}

}
