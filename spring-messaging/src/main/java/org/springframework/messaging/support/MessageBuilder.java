/*
 * Copyright 2002-2013 the original author or authors.
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

package org.springframework.messaging.support;

import java.util.Map;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHeaders;
import org.springframework.util.Assert;

/**
 * A builder for creating a {@link GenericMessage} (or {@link ErrorMessage} if
 * the payload is of type {@link Throwable}).
 *
 * @author Arjen Poutsma
 * @author Mark Fisher
 * @author Rossen Stoyanchev
 * @since 4.0
 *
 * @see GenericMessage
 * @see ErrorMessage
 */
public final class MessageBuilder<T> {

	private final Message<T> originalMessage;

	private final T payload;

	private MessageHeaderAccessor headerAccessor;


	private MessageBuilder(Message<T> originalMessage) {
		Assert.notNull(originalMessage, "'originalMessage' must not be null");
		this.payload = originalMessage.getPayload();
		this.headerAccessor = new MessageHeaderAccessor(originalMessage);
		this.originalMessage = originalMessage;
	}

	private MessageBuilder(T payload, MessageHeaderAccessor accessor) {
		Assert.notNull(payload, "'payload' must not be null");
		Assert.notNull(accessor, "'messageHeaderAccessor' must not be null");
		this.payload = payload;
		this.headerAccessor = accessor;
		this.originalMessage = null;
	}

	/**
	 * Create a builder for a new {@link Message} instance pre-populated with all of the
	 * headers copied from the provided message. The payload of the provided Message will
	 * also be used as the payload for the new message.
	 *
	 * @param message the Message from which the payload and all headers will be copied
	 */
	public static <T> MessageBuilder<T> fromMessage(Message<T> message) {
		return new MessageBuilder<T>(message);
	}

	/**
	 * Create a new builder for a message with the given payload.
	 * @param payload the payload
	 */
	public static <T> MessageBuilder<T> withPayload(T payload) {
		return new MessageBuilder<T>(payload, new MessageHeaderAccessor());
	}

	/**
	 * A shortcut factory method for creating a message with the given payload
	 * and {@code MessageHeaders}.
	 *
	 * <p><strong>Note:</strong> the given {@code MessageHeaders} instance is used
	 * directly in the new message, i.e. it is not copied.
	 *
	 * @param payload the payload to use, never {@code null}
	 * @param messageHeaders the headers to use, never {@code null}
	 * @return the created message
	 * @since 4.1
	 */
	@SuppressWarnings("unchecked")
	public static <T> Message<T> createMessage(T payload, MessageHeaders messageHeaders) {
		Assert.notNull(payload, "'payload' must not be null");
		Assert.notNull(messageHeaders, "'messageHeaders' must not be null");
		if (payload instanceof Throwable) {
			return (Message<T>) new ErrorMessage((Throwable) payload, messageHeaders);
		}
		else {
			return new GenericMessage<T>(payload, messageHeaders);
		}
	}

	/**
	 * Set the message headers to use by providing a {@code MessageHeaderAccessor}.
	 *
	 * @param accessor the headers to use
	 */
	public MessageBuilder<T> setHeaders(MessageHeaderAccessor accessor) {
		Assert.notNull(accessor, "HeaderAccessor must not be null");
		this.headerAccessor = accessor;
		return this;
	}

	/**
	 * Set the value for the given header name. If the provided value is {@code null},
	 * the header will be removed.
	 */
	public MessageBuilder<T> setHeader(String headerName, Object headerValue) {
		this.headerAccessor.setHeader(headerName, headerValue);
		return this;
	}

	/**
	 * Set the value for the given header name only if the header name is not already
	 * associated with a value.
	 */
	public MessageBuilder<T> setHeaderIfAbsent(String headerName, Object headerValue) {
		this.headerAccessor.setHeaderIfAbsent(headerName, headerValue);
		return this;
	}

	/**
	 * Removes all headers provided via array of 'headerPatterns'. As the name suggests
	 * the array may contain simple matching patterns for header names. Supported pattern
	 * styles are: "xxx*", "*xxx", "*xxx*" and "xxx*yyy".
	 */
	public MessageBuilder<T> removeHeaders(String... headerPatterns) {
		this.headerAccessor.removeHeaders(headerPatterns);
		return this;
	}
	/**
	 * Remove the value for the given header name.
	 */
	public MessageBuilder<T> removeHeader(String headerName) {
		this.headerAccessor.removeHeader(headerName);
		return this;
	}

	/**
	 * Copy the name-value pairs from the provided Map. This operation will overwrite any
	 * existing values. Use { {@link #copyHeadersIfAbsent(Map)} to avoid overwriting
	 * values. Note that the 'id' and 'timestamp' header values will never be overwritten.
	 */
	public MessageBuilder<T> copyHeaders(Map<String, ?> headersToCopy) {
		this.headerAccessor.copyHeaders(headersToCopy);
		return this;
	}

	/**
	 * Copy the name-value pairs from the provided Map. This operation will <em>not</em>
	 * overwrite any existing values.
	 */
	public MessageBuilder<T> copyHeadersIfAbsent(Map<String, ?> headersToCopy) {
		this.headerAccessor.copyHeadersIfAbsent(headersToCopy);
		return this;
	}

	public MessageBuilder<T> setReplyChannel(MessageChannel replyChannel) {
		this.headerAccessor.setReplyChannel(replyChannel);
		return this;
	}

	public MessageBuilder<T> setReplyChannelName(String replyChannelName) {
		this.headerAccessor.setReplyChannelName(replyChannelName);
		return this;
	}

	public MessageBuilder<T> setErrorChannel(MessageChannel errorChannel) {
		this.headerAccessor.setErrorChannel(errorChannel);
		return this;
	}

	public MessageBuilder<T> setErrorChannelName(String errorChannelName) {
		this.headerAccessor.setErrorChannelName(errorChannelName);
		return this;
	}

	@SuppressWarnings("unchecked")
	public Message<T> build() {

		if (this.originalMessage != null && !this.headerAccessor.isModified()) {
			return this.originalMessage;
		}

		if (this.payload instanceof Throwable) {
			return (Message<T>) new ErrorMessage((Throwable) this.payload, this.headerAccessor.toMap());
		}
		else {
			return new GenericMessage<T>(this.payload, this.headerAccessor.toMap());
		}
	}

}
