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

package org.springframework.jms.support;

import java.util.List;
import java.util.Map;

import javax.jms.Destination;

import org.springframework.jms.support.converter.JmsHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.NativeMessageHeaderAccessor;

/**
 * A {@link org.springframework.messaging.support.MessageHeaderAccessor}
 * implementation giving access to JMS-specific headers.
 *
 * @author Stephane Nicoll
 * @since 4.1
 */
public class JmsMessageHeaderAccessor extends NativeMessageHeaderAccessor {

	protected JmsMessageHeaderAccessor(Map<String, List<String>> nativeHeaders) {
		super(nativeHeaders);
	}

	protected JmsMessageHeaderAccessor(Message<?> message) {
		super(message);
	}


	/**
	 * Create {@link JmsMessageHeaderAccessor} from the headers of an existing message.
	 */
	public static JmsMessageHeaderAccessor wrap(Message<?> message) {
		return new JmsMessageHeaderAccessor(message);
	}


	@Override
	public Object getReplyChannel() {
		return getReplyTo();
	}

	public String getCorrelationId() {
		return (String) getHeader(JmsHeaders.CORRELATION_ID);
	}

	public Destination getDestination() {
		return (Destination) getHeader(JmsHeaders.DESTINATION);
	}

	public Integer getDeliveryMode() {
		return (Integer) getHeader(JmsHeaders.DELIVERY_MODE);
	}

	public Long getExpiration() {
		return (Long) getHeader(JmsHeaders.EXPIRATION);
	}

	public String getMessageId() {
		return (String) getHeader(JmsHeaders.MESSAGE_ID);
	}

	public Integer getPriority() {
		return (Integer) getHeader(JmsHeaders.PRIORITY);
	}

	public Destination getReplyTo() {
		return (Destination) getHeader(JmsHeaders.REPLY_TO);
	}

	public Boolean getRedelivered() {
		return (Boolean) getHeader(JmsHeaders.REDELIVERED);
	}

	public String getType() {
		return (String) getHeader(JmsHeaders.TYPE);
	}

	public Long getTimestamp() {
		return (Long) getHeader(JmsHeaders.TIMESTAMP);
	}

}
