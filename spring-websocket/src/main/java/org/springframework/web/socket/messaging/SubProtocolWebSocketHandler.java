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

package org.springframework.web.socket.messaging;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.context.SmartLifecycle;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.SubProtocolCapable;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * An implementation of {@link WebSocketHandler} that delegates incoming WebSocket
 * messages to a {@link SubProtocolHandler} along with a {@link MessageChannel} to
 * which the sub-protocol handler can send messages from WebSocket clients to
 * the application.
 * <p>
 * Also an implementation of {@link MessageHandler} that finds the WebSocket
 * session associated with the {@link Message} and passes it, along with the message,
 * to the sub-protocol handler to send messages from the application back to the
 * client.
 *
 * @author Rossen Stoyanchev
 * @author Andy Wilkinson
 * @since 4.0
 */
public class SubProtocolWebSocketHandler
		implements WebSocketHandler, SubProtocolCapable, MessageHandler, SmartLifecycle {

	private final Log logger = LogFactory.getLog(SubProtocolWebSocketHandler.class);

	private final MessageChannel clientInboundChannel;

	private final SubscribableChannel clientOutboundChannel;

	private final Map<String, SubProtocolHandler> protocolHandlers =
			new TreeMap<String, SubProtocolHandler>(String.CASE_INSENSITIVE_ORDER);

	private SubProtocolHandler defaultProtocolHandler;

	private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<String, WebSocketSession>();

	private Object lifecycleMonitor = new Object();

	private volatile boolean running = false;


	public SubProtocolWebSocketHandler(MessageChannel clientInboundChannel, SubscribableChannel clientOutboundChannel) {
		Assert.notNull(clientInboundChannel, "ClientInboundChannel must not be null");
		Assert.notNull(clientOutboundChannel, "ClientOutboundChannel must not be null");
		this.clientInboundChannel = clientInboundChannel;
		this.clientOutboundChannel = clientOutboundChannel;
	}


	/**
	 * Configure one or more handlers to use depending on the sub-protocol requested by
	 * the client in the WebSocket handshake request.
	 * @param protocolHandlers the sub-protocol handlers to use
	 */
	public void setProtocolHandlers(List<SubProtocolHandler> protocolHandlers) {
		this.protocolHandlers.clear();
		for (SubProtocolHandler handler: protocolHandlers) {
			addProtocolHandler(handler);
		}
	}

	public List<SubProtocolHandler> getProtocolHandlers() {
		return new ArrayList<SubProtocolHandler>(protocolHandlers.values());
	}


	/**
	 * Register a sub-protocol handler.
	 */
	public void addProtocolHandler(SubProtocolHandler handler) {
		List<String> protocols = handler.getSupportedProtocols();
		if (CollectionUtils.isEmpty(protocols)) {
			logger.warn("No sub-protocols, ignoring handler " + handler);
			return;
		}
		for (String protocol: protocols) {
			SubProtocolHandler replaced = this.protocolHandlers.put(protocol, handler);
			if ((replaced != null) && (replaced != handler) ) {
				throw new IllegalStateException("Failed to map handler " + handler
						+ " to protocol '" + protocol + "', it is already mapped to handler " + replaced);
			}
		}
	}

	/**
	 * Return the sub-protocols keyed by protocol name.
	 */
	public Map<String, SubProtocolHandler> getProtocolHandlerMap() {
		return this.protocolHandlers;
	}

	/**
	 * Set the {@link SubProtocolHandler} to use when the client did not request a
	 * sub-protocol.
	 * @param defaultProtocolHandler the default handler
	 */
	public void setDefaultProtocolHandler(SubProtocolHandler defaultProtocolHandler) {
		this.defaultProtocolHandler = defaultProtocolHandler;
		if (this.protocolHandlers.isEmpty()) {
			setProtocolHandlers(Arrays.asList(defaultProtocolHandler));
		}
	}

	/**
	 * @return the default sub-protocol handler to use
	 */
	public SubProtocolHandler getDefaultProtocolHandler() {
		return this.defaultProtocolHandler;
	}

	/**
	 * Return all supported protocols.
	 */
	public List<String> getSubProtocols() {
		return new ArrayList<String>(this.protocolHandlers.keySet());
	}

	@Override
	public boolean isAutoStartup() {
		return true;
	}

	@Override
	public int getPhase() {
		return Integer.MAX_VALUE;
	}

	@Override
	public final boolean isRunning() {
		synchronized (this.lifecycleMonitor) {
			return this.running;
		}
	}

	@Override
	public final void start() {
		synchronized (this.lifecycleMonitor) {
			this.clientOutboundChannel.subscribe(this);
			this.running = true;
		}
	}

	@Override
	public final void stop() {
		synchronized (this.lifecycleMonitor) {
			this.running = false;
			this.clientOutboundChannel.unsubscribe(this);
		}
	}

	@Override
	public final void stop(Runnable callback) {
		synchronized (this.lifecycleMonitor) {
			stop();
			callback.run();
		}
	}

	@Override
	public void afterConnectionEstablished(WebSocketSession session) throws Exception {
		this.sessions.put(session.getId(), session);
		findProtocolHandler(session).afterSessionStarted(session, this.clientInboundChannel);
	}

	protected final SubProtocolHandler findProtocolHandler(WebSocketSession session) {
		SubProtocolHandler handler;
		String protocol = session.getAcceptedProtocol();
		if (!StringUtils.isEmpty(protocol)) {
			handler = this.protocolHandlers.get(protocol);
			Assert.state(handler != null,
					"No handler for sub-protocol '" + protocol + "', handlers=" + this.protocolHandlers);
		}
		else {
			if (this.defaultProtocolHandler != null) {
				handler = this.defaultProtocolHandler;
			}
			else {
				Set<SubProtocolHandler> handlers = new HashSet<SubProtocolHandler>(this.protocolHandlers.values());
				if (handlers.size() == 1) {
					handler = handlers.iterator().next();
				}
				else {
					throw new IllegalStateException(
							"No sub-protocol was requested and a default sub-protocol handler was not configured");
				}
			}
		}
		return handler;
	}

	@Override
	public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
		findProtocolHandler(session).handleMessageFromClient(session, message, this.clientInboundChannel);
	}

	@Override
	public void handleMessage(Message<?> message) throws MessagingException {

		String sessionId = resolveSessionId(message);
		if (sessionId == null) {
			logger.error("sessionId not found in message " + message);
			return;
		}

		WebSocketSession session = this.sessions.get(sessionId);
		if (session == null) {
			logger.error("Session not found for session with id " + sessionId);
			return;
		}

		try {
			findProtocolHandler(session).handleMessageToClient(session, message);
		}
		catch (Exception e) {
			logger.error("Failed to send message to client " + message, e);
		}
	}

	private String resolveSessionId(Message<?> message) {
		for (SubProtocolHandler handler : this.protocolHandlers.values()) {
			String sessionId = handler.resolveSessionId(message);
			if (sessionId != null) {
				return sessionId;
			}
		}
		if (this.defaultProtocolHandler != null) {
			String sessionId = this.defaultProtocolHandler.resolveSessionId(message);
			if (sessionId != null) {
				return sessionId;
			}
		}
		return null;
	}

	@Override
	public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
	}

	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
		this.sessions.remove(session.getId());
		findProtocolHandler(session).afterSessionEnded(session, closeStatus, this.clientInboundChannel);
	}

	@Override
	public boolean supportsPartialMessages() {
		return false;
	}

}
