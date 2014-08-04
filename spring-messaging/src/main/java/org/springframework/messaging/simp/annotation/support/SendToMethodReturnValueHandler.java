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
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.handler.DestinationPatternsMessageCondition;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.handler.invocation.HandlerMethodReturnValueHandler;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.messaging.simp.user.DestinationUserNameProvider;
import org.springframework.messaging.support.MessageHeaderInitializer;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;

import java.lang.annotation.Annotation;
import java.security.Principal;

/**
 * A {@link HandlerMethodReturnValueHandler} for sending to destinations specified in a
 * {@link SendTo} or {@link SendToUser} method-level annotations.
 *
 * <p>The value returned from the method is converted, and turned to a {@link Message} and
 * sent through the provided {@link MessageChannel}. The
 * message is then enriched with the sessionId of the input message as well as the
 * destination from the annotation(s). If multiple destinations are specified, a copy of
 * the message is sent to each destination.
 *
 * @author Rossen Stoyanchev
 * @since 4.0
 */
public class SendToMethodReturnValueHandler implements HandlerMethodReturnValueHandler {

	private final SimpMessageSendingOperations messagingTemplate;

	private final boolean annotationRequired;

	private String defaultDestinationPrefix = "/topic";

	private String defaultUserDestinationPrefix = "/queue";

	private MessageHeaderInitializer headerInitializer;


	public SendToMethodReturnValueHandler(SimpMessageSendingOperations messagingTemplate, boolean annotationRequired) {
		Assert.notNull(messagingTemplate, "messagingTemplate must not be null");
		this.messagingTemplate = messagingTemplate;
		this.annotationRequired = annotationRequired;
	}


	/**
	 * Configure a default prefix to add to message destinations in cases where a method
	 * is not annotated with {@link SendTo @SendTo} or does not specify any destinations
	 * through the annotation's value attribute.
	 * <p>By default, the prefix is set to "/topic".
	 */
	public void setDefaultDestinationPrefix(String defaultDestinationPrefix) {
		this.defaultDestinationPrefix = defaultDestinationPrefix;
	}

	/**
	 * Return the configured default destination prefix.
	 * @see #setDefaultDestinationPrefix(String)
	 */
	public String getDefaultDestinationPrefix() {
		return this.defaultDestinationPrefix;
	}

	/**
	 * Configure a default prefix to add to message destinations in cases where a
	 * method is annotated with {@link SendToUser @SendToUser} but does not specify
	 * any destinations through the annotation's value attribute.
	 * <p>By default, the prefix is set to "/queue".
	 */
	public void setDefaultUserDestinationPrefix(String prefix) {
		this.defaultUserDestinationPrefix = prefix;
	}

	/**
	 * Return the configured default user destination prefix.
	 * @see #setDefaultUserDestinationPrefix(String)
	 */
	public String getDefaultUserDestinationPrefix() {
		return this.defaultUserDestinationPrefix;
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
	public boolean supportsReturnType(MethodParameter returnType) {
		if ((returnType.getMethodAnnotation(SendTo.class) != null) ||
				(returnType.getMethodAnnotation(SendToUser.class) != null)) {
			return true;
		}
		return (!this.annotationRequired);
	}

	@Override
	public void handleReturnValue(Object returnValue, MethodParameter returnType, Message<?> message) throws Exception {
		if (returnValue == null) {
			return;
		}
		MessageHeaders headers = message.getHeaders();
		String sessionId = SimpMessageHeaderAccessor.getSessionId(headers);

		SendToUser sendToUser = returnType.getMethodAnnotation(SendToUser.class);
		if (sendToUser != null) {
			boolean broadcast = sendToUser.broadcast();
			String user = getUserName(message, headers);
			if (user == null) {
				if (sessionId == null) {
					throw new MissingSessionUserException(message);
				}
				user = sessionId;
				broadcast = false;
			}
			String[] destinations = getTargetDestinations(sendToUser, message, this.defaultUserDestinationPrefix);
			for (String destination : destinations) {
				if (broadcast) {
					this.messagingTemplate.convertAndSendToUser(user, destination, returnValue);
				}
				else {
					this.messagingTemplate.convertAndSendToUser(user, destination, returnValue, createHeaders(sessionId));
				}
			}
		}
		else {
			SendTo sendTo = returnType.getMethodAnnotation(SendTo.class);
			String[] destinations = getTargetDestinations(sendTo, message, this.defaultDestinationPrefix);
			for (String destination : destinations) {
				this.messagingTemplate.convertAndSend(destination, returnValue, createHeaders(sessionId));
			}
		}
	}

	protected String getUserName(Message<?> message, MessageHeaders headers) {
		Principal principal = SimpMessageHeaderAccessor.getUser(headers);
		if (principal != null) {
			return (principal instanceof DestinationUserNameProvider ?
					((DestinationUserNameProvider) principal).getDestinationUserName() : principal.getName());
		}
		return null;
	}

	protected String[] getTargetDestinations(Annotation annotation, Message<?> message, String defaultPrefix) {
		if (annotation != null) {
			String[] value = (String[]) AnnotationUtils.getValue(annotation);
			if (!ObjectUtils.isEmpty(value)) {
				return value;
			}
		}
		String name = DestinationPatternsMessageCondition.LOOKUP_DESTINATION_HEADER;
		String destination = (String) message.getHeaders().get(name);
		Assert.hasText(destination, "No lookup destination header in " + message);

		return (destination.startsWith("/") ?
				new String[] {defaultPrefix + destination} : new String[] {defaultPrefix + "/" + destination});
	}

	private MessageHeaders createHeaders(String sessionId) {
		SimpMessageHeaderAccessor headerAccessor = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
		if (getHeaderInitializer() != null) {
			getHeaderInitializer().initHeaders(headerAccessor);
		}
		headerAccessor.setSessionId(sessionId);
		headerAccessor.setLeaveMutable(true);
		return headerAccessor.getMessageHeaders();
	}


	@Override
	public String toString() {
		return "SendToMethodReturnValueHandler [annotationRequired=" + annotationRequired + "]";
	}

}
