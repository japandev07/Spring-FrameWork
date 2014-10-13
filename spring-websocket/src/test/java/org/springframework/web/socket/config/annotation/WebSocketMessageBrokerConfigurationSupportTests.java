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
import java.util.concurrent.ScheduledThreadPoolExecutor;

import org.junit.Test;

import org.springframework.context.ApplicationContext;
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
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Controller;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.handler.SimpleUrlHandlerMapping;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.WebSocketMessageBrokerStats;
import org.springframework.web.socket.handler.TestWebSocketSession;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import org.springframework.web.socket.handler.WebSocketHandlerDecoratorFactory;
import org.springframework.web.socket.messaging.StompSubProtocolHandler;
import org.springframework.web.socket.messaging.StompTextMessageBuilder;
import org.springframework.web.socket.messaging.SubProtocolHandler;
import org.springframework.web.socket.messaging.SubProtocolWebSocketHandler;
import org.springframework.web.socket.server.support.WebSocketHttpRequestHandler;

import static org.junit.Assert.*;

/**
 * Test fixture for
 * {@link org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurationSupport}.
 *
 * @author Rossen Stoyanchev
 */
public class WebSocketMessageBrokerConfigurationSupportTests {


	@Test
	public void handlerMapping() {
		ApplicationContext config = createConfig(TestChannelConfig.class, TestConfigurer.class);
		SimpleUrlHandlerMapping hm = (SimpleUrlHandlerMapping) config.getBean(HandlerMapping.class);
		assertEquals(1, hm.getOrder());

		Map<String, Object> handlerMap = hm.getHandlerMap();
		assertEquals(1, handlerMap.size());
		assertNotNull(handlerMap.get("/simpleBroker"));
	}

	@Test
	public void clientInboundChannelSendMessage() throws Exception {
		ApplicationContext config = createConfig(TestChannelConfig.class, TestConfigurer.class);
		TestChannel channel = config.getBean("clientInboundChannel", TestChannel.class);
		SubProtocolWebSocketHandler webSocketHandler = config.getBean(SubProtocolWebSocketHandler.class);

		WebSocketSession session = new TestWebSocketSession("s1");
		webSocketHandler.afterConnectionEstablished(session);

		TextMessage textMessage = StompTextMessageBuilder.create(StompCommand.SEND).headers("destination:/foo").build();
		webSocketHandler.handleMessage(session, textMessage);

		Message<?> message = channel.messages.get(0);
		StompHeaderAccessor headers = StompHeaderAccessor.wrap(message);

		assertEquals(SimpMessageType.MESSAGE, headers.getMessageType());
		assertEquals("/foo", headers.getDestination());
	}

	@Test
	public void clientOutboundChannelChannel() {
		ApplicationContext config = createConfig(TestChannelConfig.class, TestConfigurer.class);
		TestChannel channel = config.getBean("clientOutboundChannel", TestChannel.class);
		Set<MessageHandler> handlers = channel.getSubscribers();

		assertEquals(1, handlers.size());
		assertTrue(handlers.iterator().next() instanceof SubProtocolWebSocketHandler);
	}

	@Test
	public void webSocketTransportOptions() {
		ApplicationContext config = createConfig(TestChannelConfig.class, TestConfigurer.class);
		SubProtocolWebSocketHandler subProtocolWebSocketHandler =
				config.getBean("subProtocolWebSocketHandler", SubProtocolWebSocketHandler.class);

		assertEquals(1024 * 1024, subProtocolWebSocketHandler.getSendBufferSizeLimit());
		assertEquals(25 * 1000, subProtocolWebSocketHandler.getSendTimeLimit());

		List<SubProtocolHandler> protocolHandlers = subProtocolWebSocketHandler.getProtocolHandlers();
		for(SubProtocolHandler protocolHandler : protocolHandlers) {
			assertTrue(protocolHandler instanceof StompSubProtocolHandler);
			assertEquals(128 * 1024, ((StompSubProtocolHandler) protocolHandler).getMessageSizeLimit());
		}
	}

	@Test
	public void messageBrokerSockJsTaskScheduler() {
		ApplicationContext config = createConfig(TestChannelConfig.class, TestConfigurer.class);
		ThreadPoolTaskScheduler taskScheduler =
				config.getBean("messageBrokerSockJsTaskScheduler", ThreadPoolTaskScheduler.class);

		ScheduledThreadPoolExecutor executor = taskScheduler.getScheduledThreadPoolExecutor();
		assertEquals(Runtime.getRuntime().availableProcessors(), executor.getCorePoolSize());
		assertTrue(executor.getRemoveOnCancelPolicy());
	}

	@Test
	public void webSocketMessageBrokerStats() {
		ApplicationContext config = createConfig(TestChannelConfig.class, TestConfigurer.class);
		String name = "webSocketMessageBrokerStats";
		WebSocketMessageBrokerStats stats = config.getBean(name, WebSocketMessageBrokerStats.class);
		String actual = stats.toString();
		String expected = "WebSocketSession\\[0 current WS\\(0\\)-HttpStream\\(0\\)-HttpPoll\\(0\\), " +
				"0 total, 0 closed abnormally \\(0 connect failure, 0 send limit, 0 transport error\\)\\], " +
				"stompSubProtocol\\[processed CONNECT\\(0\\)-CONNECTED\\(0\\)-DISCONNECT\\(0\\)\\], " +
				"stompBrokerRelay\\[null\\], " +
				"inboundChannel\\[pool size = \\d, active threads = \\d, queued tasks = \\d, completed tasks = \\d\\], " +
				"outboundChannelpool size = \\d, active threads = \\d, queued tasks = \\d, completed tasks = \\d\\], " +
				"sockJsScheduler\\[pool size = \\d, active threads = \\d, queued tasks = \\d, completed tasks = \\d\\]";

		assertTrue("\nExpected: " + expected.replace("\\", "") + "\n  Actual: " + actual, actual.matches(expected));
	}

	@Test
	public void webSocketHandlerDecorator() throws Exception {
		ApplicationContext config = createConfig(WebSocketHandlerDecoratorConfig.class);
		WebSocketHandler handler = config.getBean(SubProtocolWebSocketHandler.class);
		assertNotNull(handler);

		SimpleUrlHandlerMapping mapping = (SimpleUrlHandlerMapping) config.getBean("stompWebSocketHandlerMapping");
		WebSocketHttpRequestHandler httpHandler = (WebSocketHttpRequestHandler) mapping.getHandlerMap().get("/test");
		handler = httpHandler.getWebSocketHandler();

		WebSocketSession session = new TestWebSocketSession("id");
		handler.afterConnectionEstablished(session);
		assertEquals(true, session.getAttributes().get("decorated"));
	}


	private ApplicationContext createConfig(Class<?>... configClasses) {
		AnnotationConfigApplicationContext config = new AnnotationConfigApplicationContext();
		config.register(configClasses);
		config.refresh();
		return config;
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
	static class TestConfigurer extends AbstractWebSocketMessageBrokerConfigurer {

		@Bean
		public TestController subscriptionController() {
			return new TestController();
		}

		@Override
		public void registerStompEndpoints(StompEndpointRegistry registry) {
			registry.addEndpoint("/simpleBroker");
		}

		@Override
		public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
			registration.setMessageSizeLimit(128 * 1024);
			registration.setSendTimeLimit(25 * 1000);
			registration.setSendBufferSizeLimit(1024 * 1024);
		}
	}

	@Configuration
	static class TestChannelConfig extends DelegatingWebSocketMessageBrokerConfiguration {

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

	@Configuration
	static class WebSocketHandlerDecoratorConfig extends WebSocketMessageBrokerConfigurationSupport {

		@Override
		protected void registerStompEndpoints(StompEndpointRegistry registry) {
			registry.addEndpoint("/test");
		}

		@Override
		protected void configureWebSocketTransport(WebSocketTransportRegistration registry) {
			registry.addDecoratorFactory(new WebSocketHandlerDecoratorFactory() {
				@Override
				public WebSocketHandlerDecorator decorate(WebSocketHandler handler) {
					return new WebSocketHandlerDecorator(handler) {
						@Override
						public void afterConnectionEstablished(WebSocketSession session) throws Exception {
							session.getAttributes().put("decorated", true);
							super.afterConnectionEstablished(session);
						}
					};
				}
			});
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
