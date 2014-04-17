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

package org.springframework.jms.config;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

import javax.jms.MessageListener;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import org.springframework.jms.listener.DefaultMessageListenerContainer;
import org.springframework.jms.listener.MessageListenerContainer;
import org.springframework.jms.listener.adapter.MessageListenerAdapter;
import org.springframework.jms.listener.endpoint.JmsActivationSpecConfig;
import org.springframework.jms.listener.endpoint.JmsMessageEndpointManager;

/**
 *
 * @author Stephane Nicoll
 */
public class JmsListenerEndpointTests {

	@Rule
	public final ExpectedException thrown = ExpectedException.none();

	@Test
	public void setupJmsMessageContainerFullConfig() {
		DefaultMessageListenerContainer container = new DefaultMessageListenerContainer();
		MessageListener messageListener = new MessageListenerAdapter();
		SimpleJmsListenerEndpoint endpoint = new SimpleJmsListenerEndpoint();
		endpoint.setDestination("myQueue");
		endpoint.setQueue(true);
		endpoint.setSelector("foo = 'bar'");
		endpoint.setSubscription("mySubscription");
		endpoint.setMessageListener(messageListener);

		endpoint.setupMessageContainer(container);
		assertEquals("myQueue", container.getDestinationName());
		assertFalse(container.isPubSubDomain());
		assertEquals("foo = 'bar'", container.getMessageSelector());
		assertEquals("mySubscription", container.getDurableSubscriptionName());
		assertEquals(messageListener, container.getMessageListener());
	}

	@Test
	public void setupJcaMessageContainerFullConfig() {
		JmsMessageEndpointManager container = new JmsMessageEndpointManager();
		MessageListener messageListener = new MessageListenerAdapter();
		SimpleJmsListenerEndpoint endpoint = new SimpleJmsListenerEndpoint();
		endpoint.setDestination("myQueue");
		endpoint.setQueue(true);
		endpoint.setSelector("foo = 'bar'");
		endpoint.setSubscription("mySubscription");
		endpoint.setMessageListener(messageListener);

		endpoint.setupMessageContainer(container);
		JmsActivationSpecConfig config = container.getActivationSpecConfig();
		assertEquals("myQueue", config.getDestinationName());
		assertFalse(config.isPubSubDomain());
		assertEquals("foo = 'bar'", config.getMessageSelector());
		assertEquals("mySubscription", config.getDurableSubscriptionName());
		assertEquals(messageListener, container.getMessageListener());
	}


	@Test
	public void setupMessageContainerNoListener() {
		DefaultMessageListenerContainer container = new DefaultMessageListenerContainer();
		SimpleJmsListenerEndpoint endpoint = new SimpleJmsListenerEndpoint();

		thrown.expect(IllegalStateException.class);
		endpoint.setupMessageContainer(container);
	}

	@Test
	public void setupMessageContainerUnsupportedContainer() {
		MessageListenerContainer container = mock(MessageListenerContainer.class);
		SimpleJmsListenerEndpoint endpoint = new SimpleJmsListenerEndpoint();
		endpoint.setMessageListener(new MessageListenerAdapter());

		thrown.expect(IllegalArgumentException.class);
		endpoint.setupMessageContainer(container);
	}


}
