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

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.http.MediaType;
import org.springframework.messaging.GenericMessage;
import org.springframework.messaging.Message;
import org.springframework.web.messaging.MessageType;
import org.springframework.web.messaging.converter.CompositeMessageConverter;
import org.springframework.web.messaging.converter.MessageConverter;
import org.springframework.web.messaging.event.EventBus;
import org.springframework.web.messaging.event.EventConsumer;
import org.springframework.web.messaging.service.AbstractMessageService;
import org.springframework.web.messaging.stomp.StompCommand;
import org.springframework.web.messaging.stomp.StompConversionException;
import org.springframework.web.messaging.stomp.StompHeaders;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * @author Gary Russell
 * @author Rossen Stoyanchev
 * @since 4.0
 */
public class DefaultStompWebSocketHandler extends AbstractStompWebSocketHandler {

	private static Log logger = LogFactory.getLog(DefaultStompWebSocketHandler.class);


	private final EventBus eventBus;

	private MessageConverter payloadConverter = new CompositeMessageConverter(null);


	public DefaultStompWebSocketHandler(EventBus eventBus) {

		this.eventBus = eventBus;

		this.eventBus.registerConsumer(AbstractMessageService.SERVER_TO_CLIENT_MESSAGE_KEY,
				new EventConsumer<Message<?>>() {
					@Override
					public void accept(Message<?> message) {

						StompHeaders stompHeaders = StompHeaders.fromMessageHeaders(message.getHeaders());
						stompHeaders.setStompCommandIfNotSet(StompCommand.MESSAGE);

						if (StompCommand.CONNECTED.equals(stompHeaders.getStompCommand())) {
							// Ignore for now since we already sent it
							return;
						}

						String sessionId = stompHeaders.getSessionId();
						if (sessionId == null) {
							logger.error("Cannot send message without a session id: " + message);
						}
						WebSocketSession session = getWebSocketSession(sessionId);

						byte[] payload;
						try {
							MediaType contentType = stompHeaders.getContentType();
							payload = payloadConverter.convertToPayload(message.getPayload(), contentType);
						}
						catch (Exception e) {
							logger.error("Failed to send " + message, e);
							return;
						}

						try {
							Map<String, Object> messageHeaders = stompHeaders.toMessageHeaders();
							Message<byte[]> byteMessage = new GenericMessage<byte[]>(payload, messageHeaders);
							byte[] bytes = getStompMessageConverter().fromMessage(byteMessage);
							session.sendMessage(new TextMessage(new String(bytes, Charset.forName("UTF-8"))));
						}
						catch (Throwable t) {
							sendErrorMessage(session, t);
						}
						finally {
							if (StompCommand.ERROR.equals(stompHeaders.getStompCommand())) {
								try {
									session.close(CloseStatus.PROTOCOL_ERROR);
								}
								catch (IOException e) {
								}
							}
						}
					}
				});
	}


	public void setMessageConverters(List<MessageConverter> converters) {
		this.payloadConverter = new CompositeMessageConverter(converters);
	}

	public void handleStompMessage(final WebSocketSession session, Message<byte[]> message) {

		if (logger.isTraceEnabled()) {
			logger.trace("Processing STOMP message: " + message);
		}

		try {
			StompHeaders stompHeaders = StompHeaders.fromMessageHeaders(message.getHeaders());
			MessageType messageType = stompHeaders.getMessageType();
			if (MessageType.CONNECT.equals(messageType)) {
				handleConnect(session, message);
			}
			else if (MessageType.MESSAGE.equals(messageType)) {
				handleMessage(message);
			}
			else if (MessageType.SUBSCRIBE.equals(messageType)) {
				handleSubscribe(message);
			}
			else if (MessageType.UNSUBSCRIBE.equals(messageType)) {
				handleUnsubscribe(message);
			}
			else if (MessageType.DISCONNECT.equals(messageType)) {
				handleDisconnect(message);
			}
			this.eventBus.send(AbstractMessageService.CLIENT_TO_SERVER_MESSAGE_KEY, message);
		}
		catch (Throwable t) {
			logger.error("Terminating STOMP session due to failure to send message: ", t);
			sendErrorMessage(session, t);
		}
	}

	protected void handleConnect(final WebSocketSession session, Message<byte[]> message) throws IOException {

		StompHeaders connectStompHeaders = StompHeaders.fromMessageHeaders(message.getHeaders());
		StompHeaders connectedStompHeaders = StompHeaders.create(StompCommand.CONNECTED);

		Set<String> acceptVersions = connectStompHeaders.getAcceptVersion();
		if (acceptVersions.contains("1.2")) {
			connectedStompHeaders.setAcceptVersion("1.2");
		}
		else if (acceptVersions.contains("1.1")) {
			connectedStompHeaders.setAcceptVersion("1.1");
		}
		else if (acceptVersions.isEmpty()) {
			// 1.0
		}
		else {
			throw new StompConversionException("Unsupported version '" + acceptVersions + "'");
		}
		connectedStompHeaders.setHeartbeat(0,0); // TODO

		// TODO: security

		Message<byte[]> connectedMessage = new GenericMessage<byte[]>(new byte[0], connectedStompHeaders.toMessageHeaders());
		byte[] bytes = getStompMessageConverter().fromMessage(connectedMessage);
		session.sendMessage(new TextMessage(new String(bytes, Charset.forName("UTF-8"))));
	}

	protected void handleSubscribe(Message<byte[]> message) {
		// TODO: need a way to communicate back if subscription was successfully created or
		// not in which case an ERROR should be sent back and close the connection
		// http://stomp.github.io/stomp-specification-1.2.html#SUBSCRIBE
	}

	protected void handleUnsubscribe(Message<byte[]> message) {
	}

	protected void handleMessage(Message<byte[]> stompMessage) {
	}

	protected void handleDisconnect(Message<byte[]> stompMessage) {
	}

	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
		eventBus.send(AbstractMessageService.CLIENT_CONNECTION_CLOSED_KEY, session.getId());
	}

}
