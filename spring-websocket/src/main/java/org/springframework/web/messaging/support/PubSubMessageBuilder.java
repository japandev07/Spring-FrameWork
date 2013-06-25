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

package org.springframework.web.messaging.support;

import org.springframework.http.MediaType;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import reactor.util.Assert;


/**
 * @author Rossen Stoyanchev
 * @since 4.0
 */
public class PubSubMessageBuilder<T> {

	private final WebMessageHeaderAccesssor headers = WebMessageHeaderAccesssor.create();

	private final T payload;


	private PubSubMessageBuilder(T payload) {
		Assert.notNull(payload, "<T> is required");
		this.payload = payload;
	}


	public static <T> PubSubMessageBuilder<T> withPayload(T payload) {
		return new PubSubMessageBuilder<T>(payload);
	}


	public PubSubMessageBuilder<T> destination(String destination) {
		Assert.notNull(destination, "destination is required");
		this.headers.setDestination(destination);
		return this;
	}

	public PubSubMessageBuilder<T> contentType(MediaType contentType) {
		Assert.notNull(contentType, "contentType is required");
		this.headers.setContentType(contentType);
		return this;
	}

	public PubSubMessageBuilder<T> contentType(String contentType) {
		Assert.notNull(contentType, "contentType is required");
		this.headers.setContentType(MediaType.parseMediaType(contentType));
		return this;
	}

	public Message<T> build() {

		Message<?> message = MessageHolder.getMessage();
		if (message != null) {
			String sessionId = WebMessageHeaderAccesssor.wrap(message).getSessionId();
			this.headers.setSessionId(sessionId);
		}

		return MessageBuilder.withPayload(this.payload).copyHeaders(this.headers.toMap()).build();
	}

}
