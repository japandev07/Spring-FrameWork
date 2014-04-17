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

package org.springframework.messaging.core;

import java.util.Map;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.converter.MessageConversionException;

/**
 * An extension of {@link AbstractMessageSendingTemplate} that adds support for
 * receive as well as request-reply style operations as defined by
 * {@link MessageReceivingOperations} and {@link MessageRequestReplyOperations}.
 *
 * @author Mark Fisher
 * @author Rossen Stoyanchev
 * @since 4.0
 */
public abstract class AbstractMessagingTemplate<D> extends AbstractMessageSendingTemplate<D>
		implements MessageRequestReplyOperations<D>, MessageReceivingOperations<D> {


	@Override
	public Message<?> receive() {
		return receive(getRequiredDefaultDestination());
	}

	@Override
	public Message<?> receive(D destination) {
		return doReceive(destination);
	}

	protected abstract Message<?> doReceive(D destination);


	@Override
	public <T> T receiveAndConvert(Class<T> targetClass) {
		return receiveAndConvert(getRequiredDefaultDestination(), targetClass);
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> T receiveAndConvert(D destination, Class<T> targetClass) {
		Message<?> message = doReceive(destination);
		if (message != null) {
			return (T) getMessageConverter().fromMessage(message, targetClass);
		}
		else {
			return null;
		}
	}

	@Override
	public Message<?> sendAndReceive(Message<?> requestMessage) {
		return sendAndReceive(getRequiredDefaultDestination(), requestMessage);
	}

	@Override
	public Message<?> sendAndReceive(D destination, Message<?> requestMessage) {
		return doSendAndReceive(destination, requestMessage);
	}

	protected abstract Message<?> doSendAndReceive(D destination, Message<?> requestMessage);


	@Override
	public <T> T convertSendAndReceive(Object request, Class<T> targetClass) {
		return convertSendAndReceive(getRequiredDefaultDestination(), request, targetClass);
	}

	@Override
	public <T> T convertSendAndReceive(D destination, Object request, Class<T> targetClass) {
		return convertSendAndReceive(destination, request, null, targetClass);
	}

	@Override
	public <T> T convertSendAndReceive(D destination, Object request, Map<String, Object> headers, Class<T> targetClass) {
		return convertSendAndReceive(destination, request, headers, targetClass, null);
	}

	@Override
	public <T> T convertSendAndReceive(Object request, Class<T> targetClass, MessagePostProcessor postProcessor) {
		return convertSendAndReceive(getRequiredDefaultDestination(), request, targetClass, postProcessor);
	}

	@Override
	public <T> T convertSendAndReceive(D destination, Object request, Class<T> targetClass, MessagePostProcessor postProcessor) {
		return convertSendAndReceive(destination, request, null, targetClass, postProcessor);
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> T convertSendAndReceive(D destination, Object request, Map<String, Object> headers,
			Class<T> targetClass, MessagePostProcessor postProcessor) {

		MessageHeaders messageHeaders = (headers != null ? new MessageHeaders(headers) : null);
		Message<?> requestMessage = getMessageConverter().toMessage(request, messageHeaders);

		if (requestMessage == null) {
			String payloadType = (request != null ? request.getClass().getName() : null);
			Object contentType = (messageHeaders != null ? messageHeaders.get(MessageHeaders.CONTENT_TYPE) : null);
			throw new MessageConversionException("Unable to convert payload with type '" + payloadType +
					"', contentType='" + contentType + "', converter=[" + getMessageConverter() + "]");
		}

		if (postProcessor != null) {
			requestMessage = postProcessor.postProcessMessage(requestMessage);
		}

		Message<?> replyMessage = sendAndReceive(destination, requestMessage);
		return (replyMessage != null ? (T) getMessageConverter().fromMessage(replyMessage, targetClass) : null);
	}

}
