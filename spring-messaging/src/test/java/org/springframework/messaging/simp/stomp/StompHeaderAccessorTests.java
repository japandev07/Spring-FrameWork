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

package org.springframework.messaging.simp.stomp;

import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import static org.junit.Assert.*;


/**
 * Test fixture for {@link StompHeaderAccessor}.
 *
 * @author Rossen Stoyanchev
 * @since 4.0
 */
public class StompHeaderAccessorTests {


	@Test
	public void createWithCommand() {

		StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECTED);
		assertEquals(StompCommand.CONNECTED, accessor.getCommand());

		accessor = StompHeaderAccessor.create(StompCommand.CONNECTED, new LinkedMultiValueMap<String, String>());
		assertEquals(StompCommand.CONNECTED, accessor.getCommand());
	}

	@Test
	public void createWithSubscribeNativeHeaders() {

		MultiValueMap<String, String> extHeaders = new LinkedMultiValueMap<>();
		extHeaders.add(StompHeaderAccessor.STOMP_ID_HEADER, "s1");
		extHeaders.add(StompHeaderAccessor.STOMP_DESTINATION_HEADER, "/d");

		StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.SUBSCRIBE, extHeaders);

		assertEquals(StompCommand.SUBSCRIBE, headers.getCommand());
		assertEquals(SimpMessageType.SUBSCRIBE, headers.getMessageType());
		assertEquals("/d", headers.getDestination());
		assertEquals("s1", headers.getSubscriptionId());
	}

	@Test
	public void createWithUnubscribeNativeHeaders() {

		MultiValueMap<String, String> extHeaders = new LinkedMultiValueMap<>();
		extHeaders.add(StompHeaderAccessor.STOMP_ID_HEADER, "s1");

		StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.UNSUBSCRIBE, extHeaders);

		assertEquals(StompCommand.UNSUBSCRIBE, headers.getCommand());
		assertEquals(SimpMessageType.UNSUBSCRIBE, headers.getMessageType());
		assertEquals("s1", headers.getSubscriptionId());
	}

	@Test
	public void createWithMessageFrameNativeHeaders() {

		MultiValueMap<String, String> extHeaders = new LinkedMultiValueMap<>();
		extHeaders.add(StompHeaderAccessor.DESTINATION_HEADER, "/d");
		extHeaders.add(StompHeaderAccessor.STOMP_SUBSCRIPTION_HEADER, "s1");
		extHeaders.add(StompHeaderAccessor.STOMP_CONTENT_TYPE_HEADER, "application/json");

		StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.MESSAGE, extHeaders);

		assertEquals(StompCommand.MESSAGE, headers.getCommand());
		assertEquals(SimpMessageType.MESSAGE, headers.getMessageType());
		assertEquals("s1", headers.getSubscriptionId());
	}

	@Test
	public void toNativeHeadersSubscribe() {

		StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
		headers.setSubscriptionId("s1");
		headers.setDestination("/d");

		Map<String, List<String>> actual = headers.toNativeHeaderMap();

		assertEquals(2, actual.size());
		assertEquals("s1", actual.get(StompHeaderAccessor.STOMP_ID_HEADER).get(0));
		assertEquals("/d", actual.get(StompHeaderAccessor.STOMP_DESTINATION_HEADER).get(0));
	}

	@Test
	public void toNativeHeadersUnsubscribe() {

		StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.UNSUBSCRIBE);
		headers.setSubscriptionId("s1");

		Map<String, List<String>> actual = headers.toNativeHeaderMap();

		assertEquals(1, actual.size());
		assertEquals("s1", actual.get(StompHeaderAccessor.STOMP_ID_HEADER).get(0));
	}

	@Test
	public void toNativeHeadersMessageFrame() {

		StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.MESSAGE);
		headers.setSubscriptionId("s1");
		headers.setDestination("/d");
		headers.setContentType(MediaType.APPLICATION_JSON);

		Map<String, List<String>> actual = headers.toNativeHeaderMap();

		assertEquals(4, actual.size());
		assertEquals("s1", actual.get(StompHeaderAccessor.STOMP_SUBSCRIPTION_HEADER).get(0));
		assertEquals("/d", actual.get(StompHeaderAccessor.STOMP_DESTINATION_HEADER).get(0));
		assertEquals("application/json", actual.get(StompHeaderAccessor.STOMP_CONTENT_TYPE_HEADER).get(0));
		assertNotNull("message-id was not created", actual.get(StompHeaderAccessor.STOMP_MESSAGE_ID_HEADER).get(0));
	}

	@Test
	public void modifyCustomNativeHeader() {

		MultiValueMap<String, String> extHeaders = new LinkedMultiValueMap<>();
		extHeaders.add(StompHeaderAccessor.STOMP_ID_HEADER, "s1");
		extHeaders.add(StompHeaderAccessor.STOMP_DESTINATION_HEADER, "/d");
		extHeaders.add("accountId", "ABC123");

		StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.SUBSCRIBE, extHeaders);
		String accountId = headers.getFirstNativeHeader("accountId");
		headers.setNativeHeader("accountId", accountId.toLowerCase());

		Map<String, List<String>> actual = headers.toNativeHeaderMap();
		assertEquals(3, actual.size());

		assertEquals("s1", actual.get(StompHeaderAccessor.STOMP_ID_HEADER).get(0));
		assertEquals("/d", actual.get(StompHeaderAccessor.STOMP_DESTINATION_HEADER).get(0));
		assertNotNull("abc123", actual.get("accountId").get(0));
	}


}
