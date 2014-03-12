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

package org.springframework.jms.support.converter;

/**
 * Pre-defined names and prefixes to be used for setting and/or retrieving JMS
 * attributes from/to integration Message Headers.
 *
 * @author Mark Fisher
 * @author Stephane Nicoll
 * @since 4.1
 */
public interface JmsHeaders {

	/**
	 * Prefix used for JMS API related headers in order to distinguish from
	 * user-defined headers and other internal headers (e.g. correlationId).
	 * @see SimpleJmsHeaderMapper
	 */
	public static final String PREFIX = "jms_";

	/**
	 * Correlation ID for the message. This may be the {@link #MESSAGE_ID} of
	 * the message that this message replies to. It may also be an
	 * application-specific identifier.
	 * @see javax.jms.Message#getJMSCorrelationID()
	 */
	public static final String CORRELATION_ID = PREFIX + "correlationId";

	/**
	 * Name of the destination (topic or queue) of the message.
	 * <p>Read only value.
	 * @see javax.jms.Message#getJMSDestination()
	 * @see javax.jms.Destination
	 * @see javax.jms.Queue
	 * @see javax.jms.Topic
	 */
	public static final String DESTINATION = PREFIX + "destination";

	/**
	 * Distribution mode.
	 * <p>Read only value.
	 * @see javax.jms.Message#getJMSDeliveryMode()
	 * @see javax.jms.DeliveryMode
	 */
	public static final String DELIVERY_MODE = PREFIX + "deliveryMode";

	/**
	 * Message expiration date and time.
	 * <p>Read only value.
	 * @see javax.jms.Message#getJMSExpiration()
	 */
	public static final String EXPIRATION = PREFIX + "expiration";

	/**
	 * Unique Identifier for a message.
	 * <p>Read only value.
	 * @see javax.jms.Message#getJMSMessageID()
	 */
	public static final String MESSAGE_ID = PREFIX + "messageId";

	/**
	 * The message priority level.
	 * <p>Read only value.
	 * @see javax.jms.Message#getJMSPriority()
	 */
	public static final String PRIORITY = PREFIX + "priority";

	/**
	 * Name of the destination (topic or queue) the message replies should
	 * be sent to.
	 * @see javax.jms.Message#getJMSReplyTo()
	 */
	public static final String REPLY_TO = PREFIX + "replyTo";

	/**
	 * Specify if the message was resent. This occurs when a message
	 * consumer fails to acknowledge the message reception.
	 * <p>Read only value.
	 * @see javax.jms.Message#getJMSRedelivered()
	 */
	public static final String REDELIVERED = PREFIX + "redelivered";

	/**
	 * Message type label. This type is a string value describing the message
	 * in a functional manner.
	 * @see javax.jms.Message#getJMSType()
	 */
	public static final String TYPE = PREFIX + "type";

	/**
	 * Date and time of the message sending operation.
	 * <p>Read only value.
	 * @see javax.jms.Message#getJMSTimestamp()
	 */
	public static final String TIMESTAMP = PREFIX + "timestamp";

}
