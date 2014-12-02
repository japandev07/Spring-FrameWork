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

package org.springframework.messaging.simp.broker;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Collections;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.TestPrincipal;
import org.springframework.messaging.support.MessageBuilder;

/**
 * Unit tests for SimpleBrokerMessageHandler.
 *
 * @author Rossen Stoyanchev
 * @since 4.0
 */
public class SimpleBrokerMessageHandlerTests {

	private SimpleBrokerMessageHandler messageHandler;

	@Mock
	private SubscribableChannel clientInboundChannel;

	@Mock
	private MessageChannel clientOutboundChannel;

	@Mock
	private SubscribableChannel brokerChannel;

	@Captor
	ArgumentCaptor<Message<?>> messageCaptor;


	@Before
	public void setup() {
		MockitoAnnotations.initMocks(this);
		this.messageHandler = new SimpleBrokerMessageHandler(this.clientInboundChannel,
				this.clientOutboundChannel, this.brokerChannel, Collections.<String>emptyList());
	}


	@Test
	public void subcribePublish() {

		this.messageHandler.start();

		this.messageHandler.handleMessage(createSubscriptionMessage("sess1", "sub1", "/foo"));
		this.messageHandler.handleMessage(createSubscriptionMessage("sess1", "sub2", "/foo"));
		this.messageHandler.handleMessage(createSubscriptionMessage("sess1", "sub3", "/bar"));

		this.messageHandler.handleMessage(createSubscriptionMessage("sess2", "sub1", "/foo"));
		this.messageHandler.handleMessage(createSubscriptionMessage("sess2", "sub2", "/foo"));
		this.messageHandler.handleMessage(createSubscriptionMessage("sess2", "sub3", "/bar"));

		this.messageHandler.handleMessage(createMessage("/foo", "message1"));
		this.messageHandler.handleMessage(createMessage("/bar", "message2"));

		verify(this.clientOutboundChannel, times(6)).send(this.messageCaptor.capture());
		assertCapturedMessage("sess1", "sub1", "/foo");
		assertCapturedMessage("sess1", "sub2", "/foo");
		assertCapturedMessage("sess2", "sub1", "/foo");
		assertCapturedMessage("sess2", "sub2", "/foo");
		assertCapturedMessage("sess1", "sub3", "/bar");
		assertCapturedMessage("sess2", "sub3", "/bar");
	}

	@Test
	public void subcribeDisconnectPublish() {

		String sess1 = "sess1";
		String sess2 = "sess2";

		this.messageHandler.start();

		this.messageHandler.handleMessage(createSubscriptionMessage(sess1, "sub1", "/foo"));
		this.messageHandler.handleMessage(createSubscriptionMessage(sess1, "sub2", "/foo"));
		this.messageHandler.handleMessage(createSubscriptionMessage(sess1, "sub3", "/bar"));

		this.messageHandler.handleMessage(createSubscriptionMessage(sess2, "sub1", "/foo"));
		this.messageHandler.handleMessage(createSubscriptionMessage(sess2, "sub2", "/foo"));
		this.messageHandler.handleMessage(createSubscriptionMessage(sess2, "sub3", "/bar"));

		SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.create(SimpMessageType.DISCONNECT);
		headers.setSessionId(sess1);
		headers.setUser(new TestPrincipal("joe"));
		Message<byte[]> message = MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
		this.messageHandler.handleMessage(message);

		this.messageHandler.handleMessage(createMessage("/foo", "message1"));
		this.messageHandler.handleMessage(createMessage("/bar", "message2"));

		verify(this.clientOutboundChannel, times(4)).send(this.messageCaptor.capture());

		Message<?> captured = this.messageCaptor.getAllValues().get(0);
		assertEquals(SimpMessageType.DISCONNECT_ACK, SimpMessageHeaderAccessor.getMessageType(captured.getHeaders()));
		assertEquals(sess1, SimpMessageHeaderAccessor.getSessionId(captured.getHeaders()));
		assertEquals("joe", SimpMessageHeaderAccessor.getUser(captured.getHeaders()).getName());

		assertCapturedMessage(sess2, "sub1", "/foo");
		assertCapturedMessage(sess2, "sub2", "/foo");
		assertCapturedMessage(sess2, "sub3", "/bar");
	}

	@Test
	public void connect() {

		String sess1 = "sess1";

		this.messageHandler.start();

		Message<String> connectMessage = createConnectMessage(sess1);
		this.messageHandler.handleMessage(connectMessage);

		verify(this.clientOutboundChannel, times(1)).send(this.messageCaptor.capture());
		Message<?> connectAckMessage = this.messageCaptor.getValue();

		SimpMessageHeaderAccessor connectAckHeaders = SimpMessageHeaderAccessor.wrap(connectAckMessage);
		assertEquals(connectMessage, connectAckHeaders.getHeader(SimpMessageHeaderAccessor.CONNECT_MESSAGE_HEADER));
		assertEquals(sess1, connectAckHeaders.getSessionId());
		assertEquals("joe", connectAckHeaders.getUser().getName());
	}


	protected Message<String> createSubscriptionMessage(String sessionId, String subcriptionId, String destination) {
		SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.create(SimpMessageType.SUBSCRIBE);
		headers.setSubscriptionId(subcriptionId);
		headers.setDestination(destination);
		headers.setSessionId(sessionId);
		return MessageBuilder.createMessage("", headers.getMessageHeaders());
	}

	protected Message<String> createConnectMessage(String sessionId) {
		SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.create(SimpMessageType.CONNECT);
		headers.setSessionId(sessionId);
		headers.setUser(new TestPrincipal("joe"));
		return MessageBuilder.createMessage("", headers.getMessageHeaders());
	}

	protected Message<String> createMessage(String destination, String payload) {
		SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
		headers.setDestination(destination);
		return MessageBuilder.createMessage("", headers.getMessageHeaders());
	}

	protected boolean assertCapturedMessage(String sessionId, String subcriptionId, String destination) {
		for (Message<?> message : this.messageCaptor.getAllValues()) {
			SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.wrap(message);
			if (sessionId.equals(headers.getSessionId())) {
				if (subcriptionId.equals(headers.getSubscriptionId())) {
					if (destination.equals(headers.getDestination())) {
						return true;
					}
				}
			}
		}
		return false;
	}

}
