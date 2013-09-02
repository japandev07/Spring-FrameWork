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

package org.springframework.messaging.simp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.messaging.Message;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.messaging.handler.websocket.SubProtocolWebSocketHandler;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.handler.AbstractBrokerMessageHandler;
import org.springframework.messaging.simp.handler.AnnotationMethodMessageHandler;
import org.springframework.messaging.simp.handler.MutableUserQueueSuffixResolver;
import org.springframework.messaging.simp.handler.SimpleUserQueueSuffixResolver;
import org.springframework.messaging.simp.handler.UserDestinationMessageHandler;
import org.springframework.messaging.support.channel.ExecutorSubscribableChannel;
import org.springframework.messaging.support.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.support.converter.MessageConverter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.handler.AbstractHandlerMapping;
import org.springframework.web.socket.server.config.SockJsServiceRegistration;


/**
 * Configuration support for broker-backed messaging over WebSocket using a higher-level
 * messaging sub-protocol such as STOMP. This class can either be extended directly
 * or its configuration can also be customized in a callback style via
 * {@link EnableWebSocketMessageBroker @EnableWebSocketMessageBroker} and
 * {@link WebSocketMessageBrokerConfigurer}.
 *
 * @author Rossen Stoyanchev
 * @since 4.0
 */
public abstract class WebSocketMessageBrokerConfigurationSupport {

	private MessageBrokerConfigurer messageBrokerConfigurer;


	// WebSocket configuration including message channels to/from the application

	@Bean
	public HandlerMapping brokerWebSocketHandlerMapping() {
		ServletStompEndpointRegistry registry = new ServletStompEndpointRegistry(
				subProtocolWebSocketHandler(), userQueueSuffixResolver(), brokerDefaultSockJsTaskScheduler());
		registerStompEndpoints(registry);
		AbstractHandlerMapping hm = registry.getHandlerMapping();
		hm.setOrder(1);
		return hm;
	}

	@Bean
	public SubProtocolWebSocketHandler subProtocolWebSocketHandler() {
		SubProtocolWebSocketHandler wsHandler = new SubProtocolWebSocketHandler(webSocketRequestChannel());
		webSocketReplyChannel().subscribe(wsHandler);
		return wsHandler;
	}

	@Bean
	public MutableUserQueueSuffixResolver userQueueSuffixResolver() {
		return new SimpleUserQueueSuffixResolver();
	}

	/**
	 * The default TaskScheduler to use if none is configured via
	 * {@link SockJsServiceRegistration#setTaskScheduler()}, i.e.
	 * <pre class="code">
	 * &#064;Configuration
	 * &#064;EnableWebSocketMessageBroker
	 * public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
	 *
	 *   public void registerStompEndpoints(StompEndpointRegistry registry) {
	 *     registry.addEndpoint("/stomp").withSockJS().setTaskScheduler(myScheduler());
	 *   }
	 *
	 *   // ...
	 *
	 * }
	 * </pre>
	 */
	@Bean
	public ThreadPoolTaskScheduler brokerDefaultSockJsTaskScheduler() {
		ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
		scheduler.setThreadNamePrefix("BrokerSockJS-");
		return scheduler;
	}

	protected void registerStompEndpoints(StompEndpointRegistry registry) {
	}

	@Bean
	public SubscribableChannel webSocketRequestChannel() {
		return new ExecutorSubscribableChannel(webSocketChannelExecutor());
	}

	@Bean
	public SubscribableChannel webSocketReplyChannel() {
		return new ExecutorSubscribableChannel(webSocketChannelExecutor());
	}

	@Bean
	public ThreadPoolTaskExecutor webSocketChannelExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setThreadNamePrefix("BrokerWebSocketChannel-");
		return executor;
	}

	// Handling of messages by the application

	@Bean
	public AnnotationMethodMessageHandler annotationMethodMessageHandler() {
		AnnotationMethodMessageHandler handler =
				new AnnotationMethodMessageHandler(brokerMessagingTemplate(), webSocketReplyChannel());
		handler.setDestinationPrefixes(getMessageBrokerConfigurer().getAnnotationMethodDestinationPrefixes());
		handler.setMessageConverter(brokerMessageConverter());
		webSocketRequestChannel().subscribe(handler);
		return handler;
	}

	@Bean
	public AbstractBrokerMessageHandler simpleBrokerMessageHandler() {
		AbstractBrokerMessageHandler handler = getMessageBrokerConfigurer().getSimpleBroker();
		if (handler == null) {
			return noopBroker;
		}
		else {
			webSocketRequestChannel().subscribe(handler);
			brokerMessageChannel().subscribe(handler);
			return handler;
		}
	}

	@Bean
	public AbstractBrokerMessageHandler stompBrokerRelayMessageHandler() {
		AbstractBrokerMessageHandler handler = getMessageBrokerConfigurer().getStompBrokerRelay();
		if (handler == null) {
			return noopBroker;
		}
		else {
			webSocketRequestChannel().subscribe(handler);
			brokerMessageChannel().subscribe(handler);
			return handler;
		}
	}

	protected final MessageBrokerConfigurer getMessageBrokerConfigurer() {
		if (this.messageBrokerConfigurer == null) {
			MessageBrokerConfigurer configurer = new MessageBrokerConfigurer(webSocketReplyChannel());
			configureMessageBroker(configurer);
			this.messageBrokerConfigurer = configurer;
		}
		return this.messageBrokerConfigurer;
	}

	protected void configureMessageBroker(MessageBrokerConfigurer configurer) {
	}

	@Bean
	public UserDestinationMessageHandler userDestinationMessageHandler() {
		UserDestinationMessageHandler handler = new UserDestinationMessageHandler(
				brokerMessagingTemplate(), userQueueSuffixResolver());
		webSocketRequestChannel().subscribe(handler);
		brokerMessageChannel().subscribe(handler);
		return handler;
	}

	@Bean
	public SimpMessageSendingOperations brokerMessagingTemplate() {
		SimpMessagingTemplate template = new SimpMessagingTemplate(webSocketRequestChannel());
		template.setMessageConverter(brokerMessageConverter());
		return template;
	}

	@Bean
	public SubscribableChannel brokerMessageChannel() {
		return new ExecutorSubscribableChannel(); // synchronous
	}

	@Bean
	public MessageConverter<?> brokerMessageConverter() {
		return new MappingJackson2MessageConverter();
	}


	private static final AbstractBrokerMessageHandler noopBroker = new AbstractBrokerMessageHandler(null) {

		@Override
		protected void startInternal() {
		}
		@Override
		protected void stopInternal() {
		}
		@Override
		protected void handleMessageInternal(Message<?> message) {
		}
	};

}
