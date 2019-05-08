/*
 * Copyright 2002-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.messaging.simp;

import org.junit.Test;

import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Unit tests for SimpMessageTypeMessageCondition.
 *
 * @author Rossen Stoyanchev
 */
public class SimpMessageTypeMessageConditionTests {

	@Test
	public void combine() {
		SimpMessageType messageType = SimpMessageType.MESSAGE;
		SimpMessageType subscribeType = SimpMessageType.SUBSCRIBE;

		SimpMessageType actual = condition(messageType).combine(condition(subscribeType)).getMessageType();
		assertEquals(subscribeType, actual);

		actual = condition(messageType).combine(condition(messageType)).getMessageType();
		assertEquals(messageType, actual);

		actual = condition(subscribeType).combine(condition(subscribeType)).getMessageType();
		assertEquals(subscribeType, actual);
	}

	@Test
	public void getMatchingCondition() {
		Message<?> message = message(SimpMessageType.MESSAGE);
		SimpMessageTypeMessageCondition condition = condition(SimpMessageType.MESSAGE);
		SimpMessageTypeMessageCondition actual = condition.getMatchingCondition(message);

		assertNotNull(actual);
		assertEquals(SimpMessageType.MESSAGE, actual.getMessageType());
	}

	@Test
	public void getMatchingConditionNoMessageType() {
		Message<?> message = message(null);
		SimpMessageTypeMessageCondition condition = condition(SimpMessageType.MESSAGE);

		assertNull(condition.getMatchingCondition(message));
	}

	@Test
	public void compareTo() {
		Message<byte[]> message = message(null);
		assertEquals(0, condition(SimpMessageType.MESSAGE).compareTo(condition(SimpMessageType.MESSAGE), message));
		assertEquals(0, condition(SimpMessageType.MESSAGE).compareTo(condition(SimpMessageType.SUBSCRIBE), message));
	}

	private Message<byte[]> message(SimpMessageType messageType) {
		MessageBuilder<byte[]> builder = MessageBuilder.withPayload(new byte[0]);
		if (messageType != null) {
			builder.setHeader(SimpMessageHeaderAccessor.MESSAGE_TYPE_HEADER, messageType);
		}
		return builder.build();
	}

	private SimpMessageTypeMessageCondition condition(SimpMessageType messageType) {
		return new SimpMessageTypeMessageCondition(messageType);
	}

}
