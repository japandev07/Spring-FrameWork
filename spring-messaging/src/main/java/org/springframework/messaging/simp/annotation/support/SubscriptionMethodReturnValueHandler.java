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

package org.springframework.messaging.simp.annotation.support;

import org.springframework.core.MethodParameter;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.core.MessageSendingOperations;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.handler.invocation.HandlerMethodReturnValueHandler;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
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
 * @since 4.0
 */
public class SubscriptionMethodReturnValueHandler implements HandlerMethodReturnValueHandler {

	private final MessageSendingOperations<String> messagingTemplate;


	/**
	 * Class constructor.
	 *
	 * @param messagingTemplate a messaging template to send messages to, most
	 * likely the "clientOutboundChannel", must not be {@link null}.
	 */
	public SubscriptionMethodReturnValueHandler(MessageSendingOperations<String> messagingTemplate) {
		Assert.notNull(messagingTemplate, "messagingTemplate must not be null");
		this.messagingTemplate = messagingTemplate;
	}


	@Override
	public boolean supportsReturnType(MethodParameter returnType) {
		return ((returnType.getMethodAnnotation(SubscribeMapping.class) != null)
				&& (returnType.getMethodAnnotation(SendTo.class) == null)
				&& (returnType.getMethodAnnotation(SendToUser.class) == null));
	}

	@Override
	public void handleReturnValue(Object returnValue, MethodParameter returnType, Message<?> message)
			throws Exception {

		if (returnValue == null) {
			return;
		}

		MessageHeaders headers = message.getHeaders();
		String destination = SimpMessageHeaderAccessor.getDestination(headers);
		String sessionId = SimpMessageHeaderAccessor.getSessionId(headers);
		String subscriptionId = SimpMessageHeaderAccessor.getSubscriptionId(headers);

		Assert.state(subscriptionId != null,
				"No subscriptionId in message=" + message + ", method=" + returnType.getMethod());

		this.messagingTemplate.convertAndSend(destination, returnValue, createHeaders(sessionId, subscriptionId));
	}

	private MessageHeaders createHeaders(String sessionId, String subscriptionId) {
		SimpMessageHeaderAccessor headerAccessor = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
		headerAccessor.setSessionId(sessionId);
		headerAccessor.setSubscriptionId(subscriptionId);
		headerAccessor.setLeaveMutable(true);
		return headerAccessor.getMessageHeaders();
	}

}
