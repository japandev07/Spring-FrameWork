/*
 * Copyright 2002-2014 the original author or authors.
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

package org.springframework.messaging.simp.broker;

import java.util.Collection;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderInitializer;
import org.springframework.util.Assert;
import org.springframework.util.MultiValueMap;

/**
 * A "simple" message broker that recognizes the message types defined in
 * {@link SimpMessageType}, keeps track of subscriptions with the help of a
 * {@link SubscriptionRegistry} and sends messages to subscribers.
 *
 * @author Rossen Stoyanchev
 * @since 4.0
 */
public class SimpleBrokerMessageHandler extends AbstractBrokerMessageHandler {

	private static final byte[] EMPTY_PAYLOAD = new byte[0];

	private final SubscribableChannel clientInboundChannel;

	private final MessageChannel clientOutboundChannel;

	private final SubscribableChannel brokerChannel;

	private SubscriptionRegistry subscriptionRegistry = new DefaultSubscriptionRegistry();

	private MessageHeaderInitializer headerInitializer;


	/**
	 * Create a SimpleBrokerMessageHandler instance with the given message channels
	 * and destination prefixes.
	 *
	 * @param clientInboundChannel the channel for receiving messages from clients (e.g. WebSocket clients)
	 * @param clientOutboundChannel the channel for sending messages to clients (e.g. WebSocket clients)
	 * @param brokerChannel the channel for the application to send messages to the broker
	 */
	public SimpleBrokerMessageHandler(SubscribableChannel clientInboundChannel, MessageChannel clientOutboundChannel,
			SubscribableChannel brokerChannel, Collection<String> destinationPrefixes) {

		super(destinationPrefixes);
		Assert.notNull(clientInboundChannel, "'clientInboundChannel' must not be null");
		Assert.notNull(clientOutboundChannel, "'clientOutboundChannel' must not be null");
		Assert.notNull(brokerChannel, "'brokerChannel' must not be null");
		this.clientInboundChannel = clientInboundChannel;
		this.clientOutboundChannel = clientOutboundChannel;
		this.brokerChannel = brokerChannel;
	}


	public SubscribableChannel getClientInboundChannel() {
		return this.clientInboundChannel;
	}

	public MessageChannel getClientOutboundChannel() {
		return this.clientOutboundChannel;
	}

	public SubscribableChannel getBrokerChannel() {
		return this.brokerChannel;
	}

	public void setSubscriptionRegistry(SubscriptionRegistry subscriptionRegistry) {
		Assert.notNull(subscriptionRegistry, "SubscriptionRegistry must not be null");
		this.subscriptionRegistry = subscriptionRegistry;
	}

	public SubscriptionRegistry getSubscriptionRegistry() {
		return this.subscriptionRegistry;
	}

	/**
	 * Configure a {@link MessageHeaderInitializer} to apply to the headers of all
	 * messages sent to the client outbound channel.
	 *
	 * <p>By default this property is not set.
	 */
	public void setHeaderInitializer(MessageHeaderInitializer headerInitializer) {
		this.headerInitializer = headerInitializer;
	}

	/**
	 * @return the configured header initializer.
	 */
	public MessageHeaderInitializer getHeaderInitializer() {
		return this.headerInitializer;
	}


	@Override
	public void startInternal() {
		publishBrokerAvailableEvent();
		this.clientInboundChannel.subscribe(this);
		this.brokerChannel.subscribe(this);
	}

	@Override
	public void stopInternal() {
		publishBrokerUnavailableEvent();
		this.clientInboundChannel.unsubscribe(this);
		this.brokerChannel.unsubscribe(this);
	}

	@Override
	protected void handleMessageInternal(Message<?> message) {

		MessageHeaders headers = message.getHeaders();
		SimpMessageType messageType = SimpMessageHeaderAccessor.getMessageType(headers);
		String destination = SimpMessageHeaderAccessor.getDestination(headers);
		String sessionId = SimpMessageHeaderAccessor.getSessionId(headers);

		if (!checkDestinationPrefix(destination)) {
			if (logger.isTraceEnabled()) {
				logger.trace("No match on destination in " + message);
			}
			return;
		}

		if (SimpMessageType.MESSAGE.equals(messageType)) {
			sendMessageToSubscribers(destination, message);
		}
		else if (SimpMessageType.CONNECT.equals(messageType)) {
			if (logger.isInfoEnabled()) {
				logger.info("Handling CONNECT: " + message);
			}
			SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create(SimpMessageType.CONNECT_ACK);
			initHeaders(accessor);
			accessor.setSessionId(sessionId);
			accessor.setHeader(SimpMessageHeaderAccessor.CONNECT_MESSAGE_HEADER, message);
			Message<byte[]> connectAck = MessageBuilder.createMessage(EMPTY_PAYLOAD, accessor.getMessageHeaders());
			this.clientOutboundChannel.send(connectAck);
		}
		else if (SimpMessageType.DISCONNECT.equals(messageType)) {
			if (logger.isInfoEnabled()) {
				logger.info("Handling DISCONNECT: " + message);
			}
			this.subscriptionRegistry.unregisterAllSubscriptions(sessionId);
		}
		else if (SimpMessageType.SUBSCRIBE.equals(messageType)) {
			if (logger.isDebugEnabled()) {
				logger.debug("Handling SUBSCRIBE: " + message);
			}
			this.subscriptionRegistry.registerSubscription(message);
		}
		else if (SimpMessageType.UNSUBSCRIBE.equals(messageType)) {
			if (logger.isDebugEnabled()) {
				logger.debug("Handling UNSUBSCRIBE: " + message);
			}
			this.subscriptionRegistry.unregisterSubscription(message);
		}
		else {
			if (logger.isTraceEnabled()) {
				logger.trace("Unsupported message type in " + message);
			}
		}
	}

	private void initHeaders(SimpMessageHeaderAccessor accessor) {
		if (getHeaderInitializer() != null) {
			getHeaderInitializer().initHeaders(accessor);
		}
	}

	protected void sendMessageToSubscribers(String destination, Message<?> message) {
		MultiValueMap<String,String> subscriptions = this.subscriptionRegistry.findSubscriptions(message);
		if ((subscriptions.size() > 0) && logger.isTraceEnabled()) {
			logger.trace("Sending to " + subscriptions.size() + " subscriber(s): " + message);
		}
		for (String sessionId : subscriptions.keySet()) {
			for (String subscriptionId : subscriptions.get(sessionId)) {
				SimpMessageHeaderAccessor headerAccessor = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
				initHeaders(headerAccessor);
				headerAccessor.setSessionId(sessionId);
				headerAccessor.setSubscriptionId(subscriptionId);
				headerAccessor.copyHeadersIfAbsent(message.getHeaders());
				Object payload = message.getPayload();
				Message<?> reply = MessageBuilder.createMessage(payload, headerAccessor.getMessageHeaders());
				try {
					this.clientOutboundChannel.send(reply);
				}
				catch (Throwable ex) {
					logger.error("Failed to send " + message, ex);
				}
			}
		}
	}

	@Override
	public String toString() {
		return "SimpleBroker[" + this.subscriptionRegistry + "]";
	}

}
