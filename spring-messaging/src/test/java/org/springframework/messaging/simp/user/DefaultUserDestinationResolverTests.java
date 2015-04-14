/*
 * Copyright 2002-2015 the original author or authors.
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

package org.springframework.messaging.simp.user;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.TestPrincipal;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.StringUtils;

/**
 * Unit tests for
 * {@link org.springframework.messaging.simp.user.DefaultUserDestinationResolver}.
 *
 * @author Rossen Stoyanchev
 */
public class DefaultUserDestinationResolverTests {

	public static final String SESSION_ID = "123";

	private DefaultUserDestinationResolver resolver;

	private UserSessionRegistry registry;

	private TestPrincipal user;


	@Before
	public void setup() {
		this.user = new TestPrincipal("joe");
		this.registry = new DefaultUserSessionRegistry();
		this.registry.registerSessionId(this.user.getName(), SESSION_ID);
		this.resolver = new DefaultUserDestinationResolver(this.registry);
	}


	@Test
	public void handleSubscribe() {
		String sourceDestination = "/user/queue/foo";
		Message<?> message = createWith(SimpMessageType.SUBSCRIBE, this.user, SESSION_ID, sourceDestination);
		UserDestinationResult actual = this.resolver.resolveDestination(message);

		assertEquals(sourceDestination, actual.getSourceDestination());
		assertEquals(1, actual.getTargetDestinations().size());
		assertEquals("/queue/foo-user123", actual.getTargetDestinations().iterator().next());
		assertEquals(sourceDestination, actual.getSubscribeDestination());
		assertEquals(this.user.getName(), actual.getUser());
	}

	// SPR-11325

	@Test
	public void handleSubscribeOneUserMultipleSessions() {

		this.registry.registerSessionId("joe", "456");
		this.registry.registerSessionId("joe", "789");

		Message<?> message = createWith(SimpMessageType.SUBSCRIBE, this.user, SESSION_ID, "/user/queue/foo");
		UserDestinationResult actual = this.resolver.resolveDestination(message);

		assertEquals(1, actual.getTargetDestinations().size());
		assertEquals("/queue/foo-user123", actual.getTargetDestinations().iterator().next());
	}

	@Test
	public void handleSubscribeNoUser() {
		String sourceDestination = "/user/queue/foo";
		Message<?> message = createWith(SimpMessageType.SUBSCRIBE, null, SESSION_ID, sourceDestination);
		UserDestinationResult actual = this.resolver.resolveDestination(message);

		assertEquals(sourceDestination, actual.getSourceDestination());
		assertEquals(1, actual.getTargetDestinations().size());
		assertEquals("/queue/foo-user" + SESSION_ID, actual.getTargetDestinations().iterator().next());
		assertEquals(sourceDestination, actual.getSubscribeDestination());
		assertNull(actual.getUser());
	}

	@Test
	public void handleUnsubscribe() {
		Message<?> message = createWith(SimpMessageType.UNSUBSCRIBE, this.user, SESSION_ID, "/user/queue/foo");
		UserDestinationResult actual = this.resolver.resolveDestination(message);

		assertEquals(1, actual.getTargetDestinations().size());
		assertEquals("/queue/foo-user123", actual.getTargetDestinations().iterator().next());
	}

	@Test
	public void handleMessage() {
		String sourceDestination = "/user/joe/queue/foo";
		Message<?> message = createWith(SimpMessageType.MESSAGE, this.user, SESSION_ID, sourceDestination);
		UserDestinationResult actual = this.resolver.resolveDestination(message);

		assertEquals(sourceDestination, actual.getSourceDestination());
		assertEquals(1, actual.getTargetDestinations().size());
		assertEquals("/queue/foo-user123", actual.getTargetDestinations().iterator().next());
		assertEquals("/user/queue/foo", actual.getSubscribeDestination());
		assertEquals(this.user.getName(), actual.getUser());
	}

	// SPR-12444
	@Test
	public void handleMessageToOtherUser() {
		final String OTHER_SESSION_ID = "456";
		final String OTHER_USER_NAME = "anna";

		String sourceDestination = "/user/"+OTHER_USER_NAME+"/queue/foo";
		TestPrincipal otherUser = new TestPrincipal(OTHER_USER_NAME);
		this.registry.registerSessionId(otherUser.getName(), OTHER_SESSION_ID);
		Message<?> message = createWith(SimpMessageType.MESSAGE, this.user, SESSION_ID, sourceDestination);
		UserDestinationResult actual = this.resolver.resolveDestination(message);

		assertEquals(sourceDestination, actual.getSourceDestination());
		assertEquals(1, actual.getTargetDestinations().size());
		assertEquals("/queue/foo-user" + OTHER_SESSION_ID, actual.getTargetDestinations().iterator().next());
		assertEquals("/user/queue/foo", actual.getSubscribeDestination());
		assertEquals(otherUser.getName(), actual.getUser());
	}

	@Test
	public void handleMessageEncodedUserName() {

		String userName = "http://joe.openid.example.org/";
		this.registry.registerSessionId(userName, "openid123");
		String destination = "/user/" + StringUtils.replace(userName, "/", "%2F") + "/queue/foo";
		Message<?> message = createWith(SimpMessageType.MESSAGE, this.user, null, destination);
		UserDestinationResult actual = this.resolver.resolveDestination(message);

		assertEquals(1, actual.getTargetDestinations().size());
		assertEquals("/queue/foo-useropenid123", actual.getTargetDestinations().iterator().next());
	}

	@Test
	public void handleMessageWithNoUser() {
		String sourceDestination = "/user/" + SESSION_ID + "/queue/foo";
		Message<?> message = createWith(SimpMessageType.MESSAGE, null, SESSION_ID, sourceDestination);
		UserDestinationResult actual = this.resolver.resolveDestination(message);

		assertEquals(sourceDestination, actual.getSourceDestination());
		assertEquals(1, actual.getTargetDestinations().size());
		assertEquals("/queue/foo-user123", actual.getTargetDestinations().iterator().next());
		assertEquals("/user/queue/foo", actual.getSubscribeDestination());
		assertNull(actual.getUser());
	}

	@Test
	public void ignoreMessage() {

		// no destination
		Message<?> message = createWith(SimpMessageType.MESSAGE, this.user, SESSION_ID, null);
		UserDestinationResult actual = this.resolver.resolveDestination(message);
		assertNull(actual);

		// not a user destination
		message = createWith(SimpMessageType.MESSAGE, this.user, SESSION_ID, "/queue/foo");
		actual = this.resolver.resolveDestination(message);
		assertNull(actual);

		// subscribe + not a user destination
		message = createWith(SimpMessageType.SUBSCRIBE, this.user, SESSION_ID, "/queue/foo");
		actual = this.resolver.resolveDestination(message);
		assertNull(actual);

		// no match on message type
		message = createWith(SimpMessageType.CONNECT, this.user, SESSION_ID, "user/joe/queue/foo");
		actual = this.resolver.resolveDestination(message);
		assertNull(actual);
	}


	private Message<?> createWith(SimpMessageType type, TestPrincipal user, String sessionId, String destination) {
		SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.create(type);
		if (destination != null) {
			headers.setDestination(destination);
		}
		if (user != null) {
			headers.setUser(user);
		}
		if (sessionId != null) {
			headers.setSessionId(sessionId);
		}
		return MessageBuilder.withPayload(new byte[0]).setHeaders(headers).build();
	}

}
