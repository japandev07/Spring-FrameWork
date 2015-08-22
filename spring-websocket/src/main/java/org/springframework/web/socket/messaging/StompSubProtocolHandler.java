/*
 * Copyright 2002-2015 the original author or authors.
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

package org.springframework.web.socket.messaging;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.Principal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpAttributes;
import org.springframework.messaging.simp.SimpAttributesContextHolder;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.BufferingStompDecoder;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompDecoder;
import org.springframework.messaging.simp.stomp.StompEncoder;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.simp.user.DestinationUserNameProvider;
import org.springframework.messaging.support.AbstractMessageChannel;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.ImmutableMessageChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.messaging.support.MessageHeaderInitializer;
import org.springframework.util.Assert;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.SessionLimitExceededException;
import org.springframework.web.socket.handler.WebSocketSessionDecorator;
import org.springframework.web.socket.sockjs.transport.SockJsSession;

/**
 * A {@link SubProtocolHandler} for STOMP that supports versions 1.0, 1.1, and 1.2
 * of the STOMP specification.
 *
 * @author Rossen Stoyanchev
 * @author Andy Wilkinson
 * @since 4.0
 */
public class StompSubProtocolHandler implements SubProtocolHandler, ApplicationEventPublisherAware {

	/**
	 * This handler supports assembling large STOMP messages split into multiple
	 * WebSocket messages and STOMP clients (like stomp.js) indeed split large STOMP
	 * messages at 16K boundaries. Therefore the WebSocket server input message
	 * buffer size must allow 16K at least plus a little extra for SockJS framing.
	 */
	public static final int MINIMUM_WEBSOCKET_MESSAGE_SIZE = 16 * 1024 + 256;

	/**
	 * The name of the header set on the CONNECTED frame indicating the name
	 * of the user authenticated on the WebSocket session.
	 */
	public static final String CONNECTED_USER_HEADER = "user-name";

	private static final Log logger = LogFactory.getLog(StompSubProtocolHandler.class);

	private static final byte[] EMPTY_PAYLOAD = new byte[0];


	private StompSubProtocolErrorHandler errorHandler;

	private int messageSizeLimit = 64 * 1024;

	@SuppressWarnings("deprecation")
	private org.springframework.messaging.simp.user.UserSessionRegistry userSessionRegistry;

	private final StompEncoder stompEncoder = new StompEncoder();

	private final StompDecoder stompDecoder = new StompDecoder();

	private final Map<String, BufferingStompDecoder> decoders = new ConcurrentHashMap<String, BufferingStompDecoder>();

	private MessageHeaderInitializer headerInitializer;

	private Boolean immutableMessageInterceptorPresent;

	private ApplicationEventPublisher eventPublisher;

	private final Stats stats = new Stats();


	/**
	 * Configure a handler for error messages sent to clients which allows
	 * customizing the error messages or preventing them from being sent.
	 * <p>By default this isn't configured in which case an ERROR frame is sent
	 * with a message header reflecting the error.
	 * @param errorHandler the error handler
	 */
	public void setErrorHandler(StompSubProtocolErrorHandler errorHandler) {
		this.errorHandler = errorHandler;
	}

	/**
	 * Return the configured error handler.
	 */
	public StompSubProtocolErrorHandler getErrorHandler() {
		return this.errorHandler;
	}

	/**
	 * Configure the maximum size allowed for an incoming STOMP message.
	 * Since a STOMP message can be received in multiple WebSocket messages,
	 * buffering may be required and therefore it is necessary to know the maximum
	 * allowed message size.
	 * <p>By default this property is set to 64K.
	 * @since 4.0.3
	 */
	public void setMessageSizeLimit(int messageSizeLimit) {
		this.messageSizeLimit = messageSizeLimit;
	}

	/**
	 * Get the configured message buffer size limit in bytes.
	 * @since 4.0.3
	 */
	public int getMessageSizeLimit() {
		return this.messageSizeLimit;
	}

	/**
	 * Provide a registry with which to register active user session ids.
	 * @see org.springframework.messaging.simp.user.UserDestinationMessageHandler
	 * @deprecated as of 4.2 in favor of {@link DefaultSimpUserRegistry} which relies
	 * on the ApplicationContext events published by this class and is created via
	 * {@link org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurationSupport#createLocalUserRegistry
	 * WebSocketMessageBrokerConfigurationSupport.createLocalUserRegistry}
	 */
	@Deprecated
	public void setUserSessionRegistry(org.springframework.messaging.simp.user.UserSessionRegistry registry) {
		this.userSessionRegistry = registry;
	}

	/**
	 * @deprecated as of 4.2
	 */
	@Deprecated
	public org.springframework.messaging.simp.user.UserSessionRegistry getUserSessionRegistry() {
		return this.userSessionRegistry;
	}

	/**
	 * Configure a {@link MessageHeaderInitializer} to apply to the headers of all
	 * messages created from decoded STOMP frames and other messages sent to the
	 * client inbound channel.
	 * <p>By default this property is not set.
	 */
	public void setHeaderInitializer(MessageHeaderInitializer headerInitializer) {
		this.headerInitializer = headerInitializer;
		this.stompDecoder.setHeaderInitializer(headerInitializer);
	}

	/**
	 * Return the configured header initializer.
	 */
	public MessageHeaderInitializer getHeaderInitializer() {
		return this.headerInitializer;
	}

	@Override
	public List<String> getSupportedProtocols() {
		return Arrays.asList("v10.stomp", "v11.stomp", "v12.stomp");
	}

	@Override
	public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
		this.eventPublisher = applicationEventPublisher;
	}

	/**
	 * Return a String describing internal state and counters.
	 */
	public String getStatsInfo() {
		return this.stats.toString();
	}


	/**
	 * Handle incoming WebSocket messages from clients.
	 */
	public void handleMessageFromClient(WebSocketSession session,
			WebSocketMessage<?> webSocketMessage, MessageChannel outputChannel) {

		List<Message<byte[]>> messages;
		try {
			ByteBuffer byteBuffer;
			if (webSocketMessage instanceof TextMessage) {
				byteBuffer = ByteBuffer.wrap(((TextMessage) webSocketMessage).asBytes());
			}
			else if (webSocketMessage instanceof BinaryMessage) {
				byteBuffer = ((BinaryMessage) webSocketMessage).getPayload();
			}
			else {
				return;
			}

			BufferingStompDecoder decoder = this.decoders.get(session.getId());
			if (decoder == null) {
				throw new IllegalStateException("No decoder for session id '" + session.getId() + "'");
			}

			messages = decoder.decode(byteBuffer);
			if (messages.isEmpty()) {
				if (logger.isTraceEnabled()) {
					logger.trace("Incomplete STOMP frame content received in session " +
							session + ", bufferSize=" + decoder.getBufferSize() +
							", bufferSizeLimit=" + decoder.getBufferSizeLimit() + ".");
				}
				return;
			}
		}
		catch (Throwable ex) {
			if (logger.isErrorEnabled()) {
				logger.error("Failed to parse " + webSocketMessage +
						" in session " + session.getId() + ". Sending STOMP ERROR to client.", ex);
			}
			handleError(session, ex, null);
			return;
		}

		for (Message<byte[]> message : messages) {
			try {
				StompHeaderAccessor headerAccessor =
						MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
				Principal user = session.getPrincipal();

				headerAccessor.setSessionId(session.getId());
				headerAccessor.setSessionAttributes(session.getAttributes());
				headerAccessor.setUser(user);
				headerAccessor.setHeader(SimpMessageHeaderAccessor.HEART_BEAT_HEADER, headerAccessor.getHeartbeat());
				if (!detectImmutableMessageInterceptor(outputChannel)) {
					headerAccessor.setImmutable();
				}

				if (logger.isTraceEnabled()) {
					logger.trace("From client: " + headerAccessor.getShortLogMessage(message.getPayload()));
				}

				if (StompCommand.CONNECT.equals(headerAccessor.getCommand())) {
					this.stats.incrementConnectCount();
				}
				else if (StompCommand.DISCONNECT.equals(headerAccessor.getCommand())) {
					this.stats.incrementDisconnectCount();
				}

				try {
					SimpAttributesContextHolder.setAttributesFromMessage(message);
					boolean sent = outputChannel.send(message);

					if (sent && this.eventPublisher != null) {
						if (StompCommand.CONNECT.equals(headerAccessor.getCommand())) {
							publishEvent(new SessionConnectEvent(this, message, user));
						}
						else if (StompCommand.SUBSCRIBE.equals(headerAccessor.getCommand())) {
							publishEvent(new SessionSubscribeEvent(this, message, user));
						}
						else if (StompCommand.UNSUBSCRIBE.equals(headerAccessor.getCommand())) {
							publishEvent(new SessionUnsubscribeEvent(this, message, user));
						}
					}
				}
				finally {
					SimpAttributesContextHolder.resetAttributes();
				}
			}
			catch (Throwable ex) {
				logger.error("Failed to send client message to application via MessageChannel" +
						" in session " + session.getId() + ". Sending STOMP ERROR to client.", ex);
				handleError(session, ex, message);
			}
		}
	}

	@SuppressWarnings("deprecation")
	private void handleError(WebSocketSession session, Throwable ex, Message<byte[]> clientMessage) {
		if (getErrorHandler() == null) {
			sendErrorMessage(session, ex);
			return;
		}

		Message<byte[]> message = getErrorHandler().handleClientMessageProcessingError(clientMessage, ex);
		if (message == null) {
			return;
		}

		StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
		Assert.notNull(accessor, "Expected STOMP headers");
		sendToClient(session, accessor, message.getPayload());
	}

	/**
	 * Invoked when no
	 * {@link #setErrorHandler(StompSubProtocolErrorHandler) errorHandler}
	 * is configured to send an ERROR frame to the client.
	 * @deprecated as of Spring 4.2, in favor of
	 * {@link #setErrorHandler(StompSubProtocolErrorHandler) configuring}
	 * a {@code StompSubProtocolErrorHandler}
	 */
	@Deprecated
	protected void sendErrorMessage(WebSocketSession session, Throwable error) {
		StompHeaderAccessor headerAccessor = StompHeaderAccessor.create(StompCommand.ERROR);
		headerAccessor.setMessage(error.getMessage());

		byte[] bytes = this.stompEncoder.encode(headerAccessor.getMessageHeaders(), EMPTY_PAYLOAD);
		try {
			session.sendMessage(new TextMessage(bytes));
		}
		catch (Throwable ex) {
			// Could be part of normal workflow (e.g. browser tab closed)
			logger.debug("Failed to send STOMP ERROR to client", ex);
		}
	}

	private boolean detectImmutableMessageInterceptor(MessageChannel channel) {
		if (this.immutableMessageInterceptorPresent != null) {
			return this.immutableMessageInterceptorPresent;
		}

		if (channel instanceof AbstractMessageChannel) {
			for (ChannelInterceptor interceptor : ((AbstractMessageChannel) channel).getInterceptors()) {
				if (interceptor instanceof ImmutableMessageChannelInterceptor) {
					this.immutableMessageInterceptorPresent = true;
					return true;
				}
			}
		}
		this.immutableMessageInterceptorPresent = false;
		return false;
	}

	private void publishEvent(ApplicationEvent event) {
		try {
			this.eventPublisher.publishEvent(event);
		}
		catch (Throwable ex) {
			logger.error("Error publishing " + event, ex);
		}
	}

	/**
	 * Handle STOMP messages going back out to WebSocket clients.
	 */
	@Override
	@SuppressWarnings("unchecked")
	public void handleMessageToClient(WebSocketSession session, Message<?> message) {
		if (!(message.getPayload() instanceof byte[])) {
			logger.error("Expected byte[] payload. Ignoring " + message + ".");
			return;
		}

		StompHeaderAccessor stompAccessor = getStompHeaderAccessor(message);
		StompCommand command = stompAccessor.getCommand();

		if (StompCommand.MESSAGE.equals(command)) {
			if (stompAccessor.getSubscriptionId() == null) {
				logger.warn("No STOMP \"subscription\" header in " + message);
			}
			String origDestination = stompAccessor.getFirstNativeHeader(SimpMessageHeaderAccessor.ORIGINAL_DESTINATION);
			if (origDestination != null) {
				stompAccessor = toMutableAccessor(stompAccessor, message);
				stompAccessor.removeNativeHeader(SimpMessageHeaderAccessor.ORIGINAL_DESTINATION);
				stompAccessor.setDestination(origDestination);
			}
		}
		else if (StompCommand.CONNECTED.equals(command)) {
			this.stats.incrementConnectedCount();
			stompAccessor = afterStompSessionConnected(message, stompAccessor, session);
			if (this.eventPublisher != null && StompCommand.CONNECTED.equals(command)) {
				try {
					SimpAttributes simpAttributes = new SimpAttributes(session.getId(), session.getAttributes());
					SimpAttributesContextHolder.setAttributes(simpAttributes);
					Principal user = session.getPrincipal();
					publishEvent(new SessionConnectedEvent(this, (Message<byte[]>) message, user));
				}
				finally {
					SimpAttributesContextHolder.resetAttributes();
				}
			}
		}

		byte[] payload = (byte[]) message.getPayload();

		if (StompCommand.ERROR.equals(command) && getErrorHandler() != null) {
			Message<byte[]> errorMessage = getErrorHandler().handleErrorMessageToClient((Message<byte[]>) message);
			stompAccessor = MessageHeaderAccessor.getAccessor(errorMessage, StompHeaderAccessor.class);
			Assert.notNull(stompAccessor, "Expected STOMP headers");
			payload = errorMessage.getPayload();
		}

		sendToClient(session, stompAccessor, payload);
	}

	private void sendToClient(WebSocketSession session, StompHeaderAccessor stompAccessor, byte[] payload) {
		StompCommand command = stompAccessor.getCommand();
		try {
			byte[] bytes = this.stompEncoder.encode(stompAccessor.getMessageHeaders(), payload);

			boolean useBinary = (payload.length > 0 && !(session instanceof SockJsSession) &&
					MimeTypeUtils.APPLICATION_OCTET_STREAM.isCompatibleWith(stompAccessor.getContentType()));

			if (useBinary) {
				session.sendMessage(new BinaryMessage(bytes));
			}
			else {
				session.sendMessage(new TextMessage(bytes));
			}
		}
		catch (SessionLimitExceededException ex) {
			// Bad session, just get out
			throw ex;
		}
		catch (Throwable ex) {
			// Could be part of normal workflow (e.g. browser tab closed)
			logger.debug("Failed to send WebSocket message to client in session " + session.getId(), ex);
			command = StompCommand.ERROR;
		}
		finally {
			if (StompCommand.ERROR.equals(command)) {
				try {
					session.close(CloseStatus.PROTOCOL_ERROR);
				}
				catch (IOException ex) {
					// Ignore
				}
			}
		}
	}

	private  StompHeaderAccessor getStompHeaderAccessor(Message<?> message) {
		MessageHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, MessageHeaderAccessor.class);
		if (accessor == null) {
			// Shouldn't happen (only broker broadcasts directly to clients)
			throw new IllegalStateException("No header accessor in " + message);
		}
		StompHeaderAccessor stompAccessor;
		if (accessor instanceof StompHeaderAccessor) {
			stompAccessor = (StompHeaderAccessor) accessor;
		}
		else if (accessor instanceof SimpMessageHeaderAccessor) {
			stompAccessor = StompHeaderAccessor.wrap(message);
			SimpMessageType messageType = SimpMessageHeaderAccessor.getMessageType(message.getHeaders());
			if (SimpMessageType.CONNECT_ACK.equals(messageType)) {
				stompAccessor = convertConnectAcktoStompConnected(stompAccessor);
			}
			else if (SimpMessageType.DISCONNECT_ACK.equals(messageType)) {
				stompAccessor = StompHeaderAccessor.create(StompCommand.ERROR);
				stompAccessor.setMessage("Session closed.");
			}
			else if (SimpMessageType.HEARTBEAT.equals(messageType)) {
				stompAccessor = StompHeaderAccessor.createForHeartbeat();
			}
			else if (stompAccessor.getCommand() == null || StompCommand.SEND.equals(stompAccessor.getCommand())) {
				stompAccessor.updateStompCommandAsServerMessage();
			}
		}
		else {
			// Shouldn't happen (only broker broadcasts directly to clients)
			throw new IllegalStateException(
					"Unexpected header accessor type: " + accessor.getClass() + " in " + message);
		}
		return stompAccessor;
	}

	/**
	 * The simple broker produces {@code SimpMessageType.CONNECT_ACK} that's not STOMP
	 * specific and needs to be turned into a STOMP CONNECTED frame.
	 */
	private StompHeaderAccessor convertConnectAcktoStompConnected(StompHeaderAccessor connectAckHeaders) {
		String name = StompHeaderAccessor.CONNECT_MESSAGE_HEADER;
		Message<?> message = (Message<?>) connectAckHeaders.getHeader(name);
		Assert.notNull(message, "Original STOMP CONNECT not found in " + connectAckHeaders);
		StompHeaderAccessor connectHeaders = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
		StompHeaderAccessor connectedHeaders = StompHeaderAccessor.create(StompCommand.CONNECTED);
		Set<String> acceptVersions = connectHeaders.getAcceptVersion();
		if (acceptVersions.contains("1.2")) {
			connectedHeaders.setVersion("1.2");
		}
		else if (acceptVersions.contains("1.1")) {
			connectedHeaders.setVersion("1.1");
		}
		else if (!acceptVersions.isEmpty()) {
			throw new IllegalArgumentException("Unsupported STOMP version '" + acceptVersions + "'");
		}
		long[] heartbeat = (long[]) connectAckHeaders.getHeader(SimpMessageHeaderAccessor.HEART_BEAT_HEADER);
		if (heartbeat != null) {
			connectedHeaders.setHeartbeat(heartbeat[0], heartbeat[1]);
		}
		else {
			connectedHeaders.setHeartbeat(0, 0);
		}
		return connectedHeaders;
	}

	protected StompHeaderAccessor toMutableAccessor(StompHeaderAccessor headerAccessor, Message<?> message) {
		return (headerAccessor.isMutable() ? headerAccessor : StompHeaderAccessor.wrap(message));
	}

	@SuppressWarnings("deprecation")
	private StompHeaderAccessor afterStompSessionConnected(Message<?> message, StompHeaderAccessor accessor,
			WebSocketSession session) {

		Principal principal = session.getPrincipal();
		if (principal != null) {
			accessor = toMutableAccessor(accessor, message);
			accessor.setNativeHeader(CONNECTED_USER_HEADER, principal.getName());
			if (this.userSessionRegistry != null) {
				String userName = getSessionRegistryUserName(principal);
				this.userSessionRegistry.registerSessionId(userName, session.getId());
			}
		}

		long[] heartbeat = accessor.getHeartbeat();
		if (heartbeat[1] > 0) {
			session = WebSocketSessionDecorator.unwrap(session);
			if (session instanceof SockJsSession) {
				((SockJsSession) session).disableHeartbeat();
			}
		}

		return accessor;
	}

	private String getSessionRegistryUserName(Principal principal) {
		String userName = principal.getName();
		if (principal instanceof DestinationUserNameProvider) {
			userName = ((DestinationUserNameProvider) principal).getDestinationUserName();
		}
		return userName;
	}

	@Override
	public String resolveSessionId(Message<?> message) {
		return SimpMessageHeaderAccessor.getSessionId(message.getHeaders());
	}

	@Override
	public void afterSessionStarted(WebSocketSession session, MessageChannel outputChannel) {
		if (session.getTextMessageSizeLimit() < MINIMUM_WEBSOCKET_MESSAGE_SIZE) {
			session.setTextMessageSizeLimit(MINIMUM_WEBSOCKET_MESSAGE_SIZE);
		}
		this.decoders.put(session.getId(), new BufferingStompDecoder(this.stompDecoder, getMessageSizeLimit()));
	}

	@Override
	@SuppressWarnings("deprecation")
	public void afterSessionEnded(WebSocketSession session, CloseStatus closeStatus, MessageChannel outputChannel) {
		this.decoders.remove(session.getId());

		Principal principal = session.getPrincipal();
		if (principal != null && this.userSessionRegistry != null) {
			String userName = getSessionRegistryUserName(principal);
			this.userSessionRegistry.unregisterSessionId(userName, session.getId());
		}

		Message<byte[]> message = createDisconnectMessage(session);
		SimpAttributes simpAttributes = SimpAttributes.fromMessage(message);
		try {
			SimpAttributesContextHolder.setAttributes(simpAttributes);
			if (this.eventPublisher != null) {
				Principal user = session.getPrincipal();
				publishEvent(new SessionDisconnectEvent(this, message, session.getId(), closeStatus, user));
			}
			outputChannel.send(message);
		}
		finally {
			SimpAttributesContextHolder.resetAttributes();
			simpAttributes.sessionCompleted();
		}
	}

	private Message<byte[]> createDisconnectMessage(WebSocketSession session) {
		StompHeaderAccessor headerAccessor = StompHeaderAccessor.create(StompCommand.DISCONNECT);
		if (getHeaderInitializer() != null) {
			getHeaderInitializer().initHeaders(headerAccessor);
		}
		headerAccessor.setSessionId(session.getId());
		headerAccessor.setSessionAttributes(session.getAttributes());
		headerAccessor.setUser(session.getPrincipal());
		return MessageBuilder.createMessage(EMPTY_PAYLOAD, headerAccessor.getMessageHeaders());
	}

	@Override
	public String toString() {
		return "StompSubProtocolHandler" + getSupportedProtocols();
	}


	private static class Stats {

		private final AtomicInteger connect = new AtomicInteger();

		private final AtomicInteger connected = new AtomicInteger();

		private final AtomicInteger disconnect = new AtomicInteger();

		public void incrementConnectCount() {
			this.connect.incrementAndGet();
		}

		public void incrementConnectedCount() {
			this.connected.incrementAndGet();
		}

		public void incrementDisconnectCount() {
			this.disconnect.incrementAndGet();
		}

		public String toString() {
			return "processed CONNECT(" + this.connect.get() + ")-CONNECTED(" +
					this.connected.get() + ")-DISCONNECT(" + this.disconnect.get() + ")";
		}
	}

}
