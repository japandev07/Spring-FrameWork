/*
 * Copyright 2002-2016 the original author or authors.
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

package org.springframework.messaging.handler.annotation.support;

import java.lang.reflect.Type;

import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.messaging.Message;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.converter.SmartMessageConverter;
import org.springframework.messaging.handler.invocation.HandlerMethodArgumentResolver;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

/**
 * {@code HandlerMethodArgumentResolver} for {@link Message} method arguments.
 * Validates that the generic type of the payload matches to the message value
 * or otherwise applies {@link MessageConverter} to convert to the expected
 * payload type.
 *
 * @author Rossen Stoyanchev
 * @author Stephane Nicoll
 * @since 4.0
 */
public class MessageMethodArgumentResolver implements HandlerMethodArgumentResolver {

	private final MessageConverter converter;


	/**
	 * Create a new instance with the given {@link MessageConverter}.
	 * @param converter the MessageConverter to use (required)
	 * @since 4.1
	 */
	public MessageMethodArgumentResolver(MessageConverter converter) {
		Assert.notNull(converter, "MessageConverter must not be null");
		this.converter = converter;
	}


	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return Message.class.isAssignableFrom(parameter.getParameterType());
	}

	@Override
	public Object resolveArgument(MethodParameter parameter, Message<?> message) throws Exception {

		Class<?> targetMessageType = parameter.getParameterType();
		Class<?> targetPayloadType = getPayloadType(parameter);

		if (!targetMessageType.isAssignableFrom(message.getClass())) {
			String actual = ClassUtils.getQualifiedName(message.getClass());
			String expected = ClassUtils.getQualifiedName(targetMessageType);
			throw new MethodArgumentTypeMismatchException(message, parameter, "The actual message type " +
					"[" + actual + "] does not match the expected type [" + expected + "]");
		}

		Object payload = message.getPayload();
		if (payload == null || targetPayloadType.isInstance(payload)) {
			return message;
		}

		if (isEmptyPayload(payload)) {
			String actual = ClassUtils.getQualifiedName(payload.getClass());
			String expected = ClassUtils.getQualifiedName(targetPayloadType);
			throw new MessageConversionException(message, "Cannot convert from the " +
					"expected payload type [" + expected + "] to the " +
					"actual payload type [" + actual + "] when the payload is empty.");
		}

		payload = convertPayload(message, parameter, targetPayloadType);
		return MessageBuilder.createMessage(payload, message.getHeaders());
	}

	private Class<?> getPayloadType(MethodParameter parameter) {
		Type genericParamType = parameter.getGenericParameterType();
		ResolvableType resolvableType = ResolvableType.forType(genericParamType).as(Message.class);
		return resolvableType.getGeneric(0).resolve(Object.class);
	}

	/**
	 * Check if the given {@code payload} is empty.
	 * @param payload the payload to check (can be {@code null})
	 */
	protected boolean isEmptyPayload(Object payload) {
		if (payload == null) {
			return true;
		}
		else if (payload instanceof byte[]) {
			return ((byte[]) payload).length == 0;
		}
		else if (payload instanceof String) {
			return !StringUtils.hasText((String) payload);
		}
		else {
			return false;
		}
	}

	private Object convertPayload(Message<?> message, MethodParameter parameter, Class<?> targetPayloadType) {
		Object result;
		if (this.converter instanceof SmartMessageConverter) {
			SmartMessageConverter smartConverter = (SmartMessageConverter) this.converter;
			result = smartConverter.fromMessage(message, targetPayloadType, parameter);
		}
		else {
			result = this.converter.fromMessage(message, targetPayloadType);
		}

		if (result == null) {
			String actual = ClassUtils.getQualifiedName(targetPayloadType);
			String expected = ClassUtils.getQualifiedName(message.getPayload().getClass());
			throw new MessageConversionException(message, "No converter found to convert payload " +
					"type [" + actual + "] to expected payload type [" + expected + "].");
		}
		return result;
	}

}
