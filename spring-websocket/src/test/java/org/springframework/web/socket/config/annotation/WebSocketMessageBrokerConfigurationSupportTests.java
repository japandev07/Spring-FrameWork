/*
 * Copyright 2002-2014 the original author or authors.
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

package org.springframework.web.socket.config.annotation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.support.AbstractSubscribableChannel;
import org.springframework.messaging.support.ExecutorSubscribableChannel;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.handler.SimpleUrlHandlerMapping;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.handler.TestWebSocketSession;
import org.springframework.web.socket.messaging.StompTextMessageBuilder;
import org.springframework.web.socket.messaging.SubProtocolWebSocketHandler;

import static org.junit.Assert.*;

/**
 * Test fixture for {@link org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurationSupport}.
 *
 * @author Rossen Stoyanchev
 */
public class WebSocketMessageBrokerConfigurationSupportTests {

	private AnnotationConfigApplicationContext config;


	@Before
	public void setupOnce() {
		this.config = new AnnotationConfigApplicationContext();
		this.config.register(TestWebSocketMessageBrokerConfiguration.class, TestSimpleMessageBrokerConfig.class);
		this.config.refresh();
	}

	@Test
	public void handlerMapping() {

		SimpleUrlHandlerMapping hm = (SimpleUrlHandlerMapping) this.config.getBean(HandlerMapping.class);
		assertEquals(1, hm.getOrder());

		Map<String, Object> handlerMap = hm.getHandlerMap();
		assertEquals(1, handlerMap.size());
		assertNotNull(handlerMap.get("/simpleBroker"));
	}

	@Test
	public void clientInboundChannelSendMessage() throws Exception {

		TestChannel channel = this.config.getBean("clientInboundChannel", TestChannel.class);
		SubProtocolWebSocketHandler webSocketHandler = this.config.getBean(SubProtocolWebSocketHandler.class);

		TextMessage textMessage = StompTextMessageBuilder.create(StompCommand.SEND).headers("destination:/foo").build();
		webSocketHandler.handleMessage(new TestWebSocketSession(), textMessage);

		Message<?> message = channel.messages.get(0);
		StompHeaderAccessor headers = StompHeaderAccessor.wrap(message);

		assertEquals(SimpMessageType.MESSAGE, headers.getMessageType());
		assertEquals("/foo", headers.getDestination());
	}

	@Test
	public void clientOutboundChannelChannel() {
		TestChannel channel = this.config.getBean("clientOutboundChannel", TestChannel.class);
		Set<MessageHandler> handlers = channel.getSubscribers();

		assertEquals(1, handlers.size());
		assertTrue(handlers.iterator().next() instanceof SubProtocolWebSocketHandler);
	}


	@Controller
	static class TestController {

		@SubscribeMapping("/foo")
		public String handleSubscribe() {
			return "bar";
		}

		@MessageMapping("/foo")
		@SendTo("/bar")
		public String handleMessage() {
			return "bar";
		}
	}

	@Configuration
	static class TestSimpleMessageBrokerConfig extends AbstractWebSocketMessageBrokerConfigurer {

		@Bean
		public TestController subscriptionController() {
			return new TestController();
		}

		@Override
		public void registerStompEndpoints(StompEndpointRegistry registry) {
			registry.addEndpoint("/simpleBroker");
		}

	}

	@Configuration
	static class TestWebSocketMessageBrokerConfiguration extends DelegatingWebSocketMessageBrokerConfiguration {

		@Override
		@Bean
		public AbstractSubscribableChannel clientInboundChannel() {
			return new TestChannel();
		}

		@Override
		@Bean
		public AbstractSubscribableChannel clientOutboundChannel() {
			return new TestChannel();
		}

		@Override
		public AbstractSubscribableChannel brokerChannel() {
			return new TestChannel();
		}
	}

	private static class TestChannel extends ExecutorSubscribableChannel {

		private final List<Message<?>> messages = new ArrayList<>();

		@Override
		public boolean sendInternal(Message<?> message, long timeout) {
			this.messages.add(message);
			return true;
		}
	}

}
