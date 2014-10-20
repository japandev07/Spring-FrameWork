/*
 * Copyright 2002-2014 the original author or authors.
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

package org.springframework.jms.config;

import java.lang.reflect.Method;
import java.util.Arrays;

import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.jms.listener.MessageListenerContainer;
import org.springframework.jms.listener.adapter.MessagingMessageListenerAdapter;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.handler.annotation.support.MessageHandlerMethodFactory;
import org.springframework.messaging.handler.invocation.InvocableHandlerMethod;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * A {@link JmsListenerEndpoint} providing the method to invoke to process
 * an incoming message for this endpoint.
 *
 * @author Stephane Nicoll
 * @since 4.1
 */
public class MethodJmsListenerEndpoint extends AbstractJmsListenerEndpoint {

	private Object bean;

	private Method method;

	private MessageHandlerMethodFactory messageHandlerMethodFactory;


	/**
	 * Set the object instance that should manage this endpoint.
	 */
	public void setBean(Object bean) {
		this.bean = bean;
	}

	public Object getBean() {
		return this.bean;
	}

	/**
	 * Set the method to invoke to process a message managed by this endpoint.
	 */
	public void setMethod(Method method) {
		this.method = method;
	}

	public Method getMethod() {
		return this.method;
	}

	/**
	 * Set the {@link MessageHandlerMethodFactory} to use to build the
	 * {@link InvocableHandlerMethod} responsible to manage the invocation
	 * of this endpoint.
	 */
	public void setMessageHandlerMethodFactory(MessageHandlerMethodFactory messageHandlerMethodFactory) {
		this.messageHandlerMethodFactory = messageHandlerMethodFactory;
	}


	@Override
	protected MessagingMessageListenerAdapter createMessageListener(MessageListenerContainer container) {
		Assert.state(this.messageHandlerMethodFactory != null,
				"Could not create message listener - MessageHandlerMethodFactory not set");
		MessagingMessageListenerAdapter messageListener = createMessageListenerInstance();
		InvocableHandlerMethod invocableHandlerMethod =
				this.messageHandlerMethodFactory.createInvocableHandlerMethod(getBean(), getMethod());
		messageListener.setHandlerMethod(invocableHandlerMethod);
		String responseDestination = getDefaultResponseDestination();
		if (StringUtils.hasText(responseDestination)) {
			if (container.isPubSubDomain()) {
				messageListener.setDefaultResponseTopicName(responseDestination);
			}
			else {
				messageListener.setDefaultResponseQueueName(responseDestination);
			}
		}
		MessageConverter messageConverter = container.getMessageConverter();
		if (messageConverter != null) {
			messageListener.setMessageConverter(messageConverter);
		}
		return messageListener;
	}

	/**
	 * Create an empty {@link MessagingMessageListenerAdapter} instance.
	 */
	protected MessagingMessageListenerAdapter createMessageListenerInstance() {
		return new MessagingMessageListenerAdapter();
	}

	private String getDefaultResponseDestination() {
		SendTo ann = AnnotationUtils.getAnnotation(getMethod(), SendTo.class);
		if (ann != null) {
			Object[] destinations = ann.value();
			if (destinations.length != 1) {
				throw new IllegalStateException("Invalid @" + SendTo.class.getSimpleName() + " annotation on '"
						+ getMethod() + "' one destination must be set (got " + Arrays.toString(destinations) + ")");
			}
			return (String) destinations[0];
		}
		return null;
	}

	@Override
	protected StringBuilder getEndpointDescription() {
		return super.getEndpointDescription()
				.append(" | bean='").append(this.bean).append("'")
				.append(" | method='").append(this.method).append("'");
	}

}
