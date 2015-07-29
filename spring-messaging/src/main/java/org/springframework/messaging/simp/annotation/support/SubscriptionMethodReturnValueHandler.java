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

package org.springframework.messaging.simp.annotation.support;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.core.MethodParameter;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.core.MessageSendingOperations;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.handler.invocation.HandlerMethodReturnValueHandler;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.messaging.support.MessageHeaderInitializer;
import org.springframework.util.Assert;

/**
 * A {@link HandlerMethodReturnValueHandler} for replying directly to a subscription.
 * It is supported on methods annotated with
 * {@link org.springframework.messaging.simp.annotation.SubscribeMapping}
 * unless they're also annotated with {@link SendTo} or {@link SendToUser} in
 * which case a message is sent to the broker instead.
 *
 * <p>The value returned from the method is converted, and turned to a {@link Message}
 * and then enriched with the sessionId, subscriptionId, and destination of the
 * input message. The message is then sent directly back to the connected client.
 *
 * @author Rossen Stoyanchev
 * @author Sebastien Deleuze
 * @since 4.0
 */
public class SubscriptionMethodReturnValueHandler implements HandlerMethodReturnValueHandler {

	private static Log logger = LogFactory.getLog(SubscriptionMethodReturnValueHandler.class);


	private final MessageSendingOperations<String> messagingTemplate;

	private MessageHeaderInitializer headerInitializer;


	/**
	 * Construct a new SubscriptionMethodReturnValueHandler.
	 * @param messagingTemplate a messaging template to send messages to,
	 * most likely the "clientOutboundChannel" (must not be {@code null})
	 */
	public SubscriptionMethodReturnValueHandler(MessageSendingOperations<String> messagingTemplate) {
		Assert.notNull(messagingTemplate, "messagingTemplate must not be null");
		this.messagingTemplate = messagingTemplate;
	}


	/**
	 * Configure a {@link MessageHeaderInitializer} to apply to the headers of all
	 * messages sent to the client outbound channel.
	 * <p>By default this property is not set.
	 */
	public void setHeaderInitializer(MessageHeaderInitializer headerInitializer) {
		this.headerInitializer = headerInitializer;
	}

	/**
	 * Return the configured header initializer.
	 */
	public MessageHeaderInitializer getHeaderInitializer() {
		return this.headerInitializer;
	}


	@Override
	public boolean supportsReturnType(MethodParameter returnType) {
		return (returnType.getMethodAnnotation(SubscribeMapping.class) != null &&
				returnType.getMethodAnnotation(SendTo.class) == null &&
				returnType.getMethodAnnotation(SendToUser.class) == null);
	}

	@Override
	public void handleReturnValue(Object returnValue, MethodParameter returnType, Message<?> message) throws Exception {
		if (returnValue == null) {
			return;
		}

		MessageHeaders headers = message.getHeaders();
		String destination = SimpMessageHeaderAccessor.getDestination(headers);
		String sessionId = SimpMessageHeaderAccessor.getSessionId(headers);
		String subscriptionId = SimpMessageHeaderAccessor.getSubscriptionId(headers);

		if (subscriptionId == null) {
			throw new IllegalStateException(
					"No subscriptionId in " + message + " returned by: " + returnType.getMethod());
		}

		if (logger.isDebugEnabled()) {
			logger.debug("Reply to @SubscribeMapping: " + returnValue);
		}
		this.messagingTemplate.convertAndSend(
				destination, returnValue, createHeaders(sessionId, subscriptionId, returnType));
	}

	private MessageHeaders createHeaders(String sessionId, String subscriptionId, MethodParameter returnType) {
		SimpMessageHeaderAccessor headerAccessor = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
		if (getHeaderInitializer() != null) {
			getHeaderInitializer().initHeaders(headerAccessor);
		}
		headerAccessor.setSessionId(sessionId);
		headerAccessor.setSubscriptionId(subscriptionId);
		headerAccessor.setHeader(SimpMessagingTemplate.CONVERSION_HINT_HEADER, returnType);
		headerAccessor.setLeaveMutable(true);
		return headerAccessor.getMessageHeaders();
	}

}
