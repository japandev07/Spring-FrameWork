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

package org.springframework.messaging.simp.handler;

import java.lang.reflect.Method;
import java.util.*;

import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.convert.ConversionService;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.core.AbstractMessageSendingTemplate;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.support.*;
import org.springframework.messaging.handler.condition.DestinationPatternsMessageCondition;
import org.springframework.messaging.handler.method.*;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SubscribeEvent;
import org.springframework.messaging.simp.annotation.support.PrincipalMethodArgumentResolver;
import org.springframework.messaging.simp.annotation.support.SendToMethodReturnValueHandler;
import org.springframework.messaging.simp.annotation.support.SubscriptionMethodReturnValueHandler;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.converter.ByteArrayMessageConverter;
import org.springframework.messaging.support.converter.CompositeMessageConverter;
import org.springframework.messaging.support.converter.MessageConverter;
import org.springframework.messaging.support.converter.StringMessageConverter;
import org.springframework.stereotype.Controller;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.PathMatcher;


/**
 * A handler for messages delegating to {@link SubscribeEvent @SubscribeEvent} and
 * {@link MessageMapping @MessageMapping} annotated methods.
 * <p>
 * Supports Ant-style path patterns as well as URI template variables in destinations.
 *
 * @author Rossen Stoyanchev
 * @author Brian Clozel
 * @since 4.0
 */
public class SimpAnnotationMethodMessageHandler extends AbstractMethodMessageHandler<SimpMessageMappingInfo> {

	private final SimpMessageSendingOperations brokerTemplate;

	private final SimpMessageSendingOperations webSocketResponseTemplate;

	private MessageConverter messageConverter;

	private ConversionService conversionService = new DefaultFormattingConversionService();

	private PathMatcher pathMatcher = new AntPathMatcher();


	/**
	 * @param brokerTemplate a messaging template to send application messages to the broker
	 * @param webSocketResponseChannel the channel for messages to WebSocket clients
	 */
	public SimpAnnotationMethodMessageHandler(SimpMessageSendingOperations brokerTemplate,
			MessageChannel webSocketResponseChannel) {

		Assert.notNull(brokerTemplate, "brokerTemplate is required");
		Assert.notNull(webSocketResponseChannel, "webSocketReplyChannel is required");
		this.brokerTemplate = brokerTemplate;
		this.webSocketResponseTemplate = new SimpMessagingTemplate(webSocketResponseChannel);

		Collection<MessageConverter> converters = new ArrayList<MessageConverter>();
		converters.add(new StringMessageConverter());
		converters.add(new ByteArrayMessageConverter());
		this.messageConverter = new CompositeMessageConverter(converters);
	}

	/**
	 * Configure a {@link MessageConverter} to use to convert the payload of a message
	 * from serialize form with a specific MIME type to an Object matching the target
	 * method parameter. The converter is also used when sending message to the message
	 * broker.
	 *
	 * @see CompositeMessageConverter
	 */
	public void setMessageConverter(MessageConverter converter) {
		this.messageConverter = converter;
		if (converter != null) {
			((AbstractMessageSendingTemplate<?>) this.webSocketResponseTemplate).setMessageConverter(converter);
		}
	}

	/**
	 * Return the configured {@link MessageConverter}.
	 */
	public MessageConverter getMessageConverter() {
		return this.messageConverter;
	}

	/**
	 * Configure a {@link ConversionService} to use when resolving method arguments, for
	 * example message header values.
	 * <p>
	 * By default an instance of {@link DefaultFormattingConversionService} is used.
	 */
	public void setConversionService(ConversionService conversionService) {
		this.conversionService = conversionService;
	}

	/**
	 * The configured {@link ConversionService}.
	 */
	public ConversionService getConversionService() {
		return this.conversionService;
	}

	/**
	 * Set the PathMatcher implementation to use for matching destinations
	 * against configured destination patterns.
	 * <p>
	 * By default AntPathMatcher is used
	 */
	public void setPathMatcher(PathMatcher pathMatcher) {
		Assert.notNull(pathMatcher, "PathMatcher must not be null");
		this.pathMatcher = pathMatcher;
	}

	/**
	 * Return the PathMatcher implementation to use for matching destinations
	 */
	public PathMatcher getPathMatcher() {
		return this.pathMatcher;
	}


	protected List<HandlerMethodArgumentResolver> initArgumentResolvers() {

		ConfigurableBeanFactory beanFactory =
				(ClassUtils.isAssignableValue(ConfigurableApplicationContext.class, getApplicationContext())) ?
						((ConfigurableApplicationContext) getApplicationContext()).getBeanFactory() : null;

		List<HandlerMethodArgumentResolver> resolvers = new ArrayList<HandlerMethodArgumentResolver>();

		// Annotation-based argument resolution
		resolvers.add(new HeaderMethodArgumentResolver(this.conversionService, beanFactory));
		resolvers.add(new HeadersMethodArgumentResolver());
		resolvers.add(new PathVariableMethodArgumentResolver(this.conversionService));

		// Type-based argument resolution
		resolvers.add(new PrincipalMethodArgumentResolver());
		resolvers.add(new MessageMethodArgumentResolver());

		resolvers.addAll(getCustomArgumentResolvers());
		resolvers.add(new PayloadArgumentResolver(this.messageConverter));

		return resolvers;
	}

	@Override
	protected List<? extends HandlerMethodReturnValueHandler> initReturnValueHandlers() {

		List<HandlerMethodReturnValueHandler> handlers = new ArrayList<HandlerMethodReturnValueHandler>();

		// Annotation-based return value types
		handlers.add(new SendToMethodReturnValueHandler(this.brokerTemplate, true));
		handlers.add(new SubscriptionMethodReturnValueHandler(this.webSocketResponseTemplate));

		// custom return value types
		handlers.addAll(getCustomReturnValueHandlers());

		// catch-all
		handlers.add(new SendToMethodReturnValueHandler(this.brokerTemplate, false));

		return handlers;
	}


	@Override
	protected boolean isHandler(Class<?> beanType) {
		return (AnnotationUtils.findAnnotation(beanType, Controller.class) != null);
	}

	@Override
	protected SimpMessageMappingInfo getMappingForMethod(Method method, Class<?> handlerType) {

		MessageMapping messageMappingAnnot = AnnotationUtils.findAnnotation(method, MessageMapping.class);
		if (messageMappingAnnot != null) {
			SimpMessageMappingInfo result = createMessageMappingCondition(messageMappingAnnot);
			MessageMapping typeAnnot = AnnotationUtils.findAnnotation(handlerType, MessageMapping.class);
			if (typeAnnot != null) {
				result = createMessageMappingCondition(typeAnnot).combine(result);
			}
			return result;
		}

		SubscribeEvent subsribeAnnot = AnnotationUtils.findAnnotation(method, SubscribeEvent.class);
		if (subsribeAnnot != null) {
			SimpMessageMappingInfo result = createSubscribeCondition(subsribeAnnot);
			SubscribeEvent typeAnnot = AnnotationUtils.findAnnotation(handlerType, SubscribeEvent.class);
			if (typeAnnot != null) {
				result = createSubscribeCondition(typeAnnot).combine(result);
			}
			return result;
		}

		return null;
	}

	private SimpMessageMappingInfo createMessageMappingCondition(MessageMapping annotation) {
		return new SimpMessageMappingInfo(SimpMessageTypeMessageCondition.MESSAGE,
				new DestinationPatternsMessageCondition(annotation.value()));
	}

	private SimpMessageMappingInfo createSubscribeCondition(SubscribeEvent annotation) {
		return new SimpMessageMappingInfo(SimpMessageTypeMessageCondition.SUBSCRIBE,
				new DestinationPatternsMessageCondition(annotation.value()));
	}

	@Override
	protected Set<String> getDirectLookupDestinations(SimpMessageMappingInfo mapping) {
		Set<String> result = new LinkedHashSet<String>();
		for (String s : mapping.getDestinationConditions().getPatterns()) {
			if (!this.pathMatcher.isPattern(s)) {
				result.add(s);
			}
		}
		return result;
	}

	@Override
	protected String getDestination(Message<?> message) {
		return (String) message.getHeaders().get(SimpMessageHeaderAccessor.DESTINATION_HEADER);
	}

	@Override
	protected SimpMessageMappingInfo getMatchingMapping(SimpMessageMappingInfo mapping, Message<?> message) {
		return mapping.getMatchingCondition(message);

	}

	@Override
	protected Comparator getMappingComparator(final Message<?> message) {
		return new Comparator<SimpMessageMappingInfo>() {
			@Override
			public int compare(SimpMessageMappingInfo info1, SimpMessageMappingInfo info2) {
				return info1.compareTo(info2, message);
			}
		};
	}

	@Override
	protected void handleMatch(SimpMessageMappingInfo mapping, HandlerMethod handlerMethod,
			String lookupDestination, Message<?> message) {

		SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.wrap(message);

		String matchedPattern = mapping.getDestinationConditions().getPatterns().iterator().next();
		Map<String, String> vars = getPathMatcher().extractUriTemplateVariables(matchedPattern, lookupDestination);

		headers.setDestination(lookupDestination);
		headers.setHeader(PathVariableMethodArgumentResolver.PATH_TEMPLATE_VARIABLES_HEADER, vars);
		message = MessageBuilder.withPayload(message.getPayload()).setHeaders(headers).build();

		super.handleMatch(mapping, handlerMethod, lookupDestination, message);
	}

	@Override
	protected void handleNoMatch(Set<SimpMessageMappingInfo> set, String lookupDestination, Message<?> message) {
		if (logger.isTraceEnabled()) {
			logger.trace("No match for " + lookupDestination);
		}
	}

	@Override
	protected AbstractExceptionHandlerMethodResolver createExceptionHandlerMethodResolverFor(Class<?> beanType) {
		return new AnnotationExceptionHandlerMethodResolver(beanType);
	}

}