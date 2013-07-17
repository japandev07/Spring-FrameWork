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

package org.springframework.messaging.simp.annotation.support;

import org.springframework.core.MethodParameter;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.core.MessagePostProcessor;
import org.springframework.messaging.handler.annotation.ReplyTo;
import org.springframework.messaging.handler.method.HandlerMethodReturnValueHandler;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.messaging.simp.annotation.ReplyToUser;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.Assert;


/**
 * A {@link HandlerMethodReturnValueHandler} for replying to destinations specified in a
 * {@link ReplyTo} or {@link ReplyToUser} method-level annotations.
 * <p>
 * The value returned from the method is converted, and turned to a {@link Message} and
 * sent through the provided {@link MessageChannel}. The
 * message is then enriched with the sessionId of the input message as well as the
 * destination from the annotation(s). If multiple destinations are specified, a copy of
 * the message is sent to each destination.
 *
 * @author Rossen Stoyanchev
 * @since 4.0
 */
public class ReplyToMethodReturnValueHandler implements HandlerMethodReturnValueHandler {

	private final SimpMessageSendingOperations messagingTemplate;


	public ReplyToMethodReturnValueHandler(SimpMessageSendingOperations messagingTemplate) {
		Assert.notNull(messagingTemplate, "messagingTemplate is required");
		this.messagingTemplate = messagingTemplate;
	}


	@Override
	public boolean supportsReturnType(MethodParameter returnType) {
		return ((returnType.getMethodAnnotation(ReplyTo.class) != null)
				|| (returnType.getMethodAnnotation(ReplyToUser.class) != null));
	}

	@Override
	public void handleReturnValue(Object returnValue, MethodParameter returnType, Message<?> inputMessage)
			throws Exception {

		if (returnValue == null) {
			return;
		}

		SimpMessageHeaderAccessor inputHeaders = SimpMessageHeaderAccessor.wrap(inputMessage);

		String sessionId = inputHeaders.getSessionId();
		MessagePostProcessor postProcessor = new SessionHeaderPostProcessor(sessionId);

		ReplyTo replyTo = returnType.getMethodAnnotation(ReplyTo.class);
		if (replyTo != null) {
			for (String destination : replyTo.value()) {
				this.messagingTemplate.convertAndSend(destination, returnValue, postProcessor);
			}
		}

		ReplyToUser replyToUser = returnType.getMethodAnnotation(ReplyToUser.class);
		if (replyToUser != null) {
			if (inputHeaders.getUser() == null) {
				throw new MissingSessionUserException(inputMessage);
			}
			String user = inputHeaders.getUser().getName();
			for (String destination : replyToUser.value()) {
				this.messagingTemplate.convertAndSendToUser(user, destination, returnValue, postProcessor);
			}
		}
	}


	private final class SessionHeaderPostProcessor implements MessagePostProcessor {

		private final String sessionId;

		public SessionHeaderPostProcessor(String sessionId) {
			this.sessionId = sessionId;
		}

		@Override
		public Message<?> postProcessMessage(Message<?> message) {
			SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.wrap(message);
			headers.setSessionId(this.sessionId);
			return MessageBuilder.withPayloadAndHeaders(message.getPayload(), headers).build();
		}
	}
}
