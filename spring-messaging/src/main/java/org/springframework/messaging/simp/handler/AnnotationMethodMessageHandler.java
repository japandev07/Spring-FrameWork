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

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.ReplyTo;
import org.springframework.messaging.handler.annotation.support.ExceptionHandlerMethodResolver;
import org.springframework.messaging.handler.annotation.support.MessageBodyMethodArgumentResolver;
import org.springframework.messaging.handler.method.HandlerMethod;
import org.springframework.messaging.handler.method.HandlerMethodArgumentResolverComposite;
import org.springframework.messaging.handler.method.HandlerMethodReturnValueHandlerComposite;
import org.springframework.messaging.handler.method.HandlerMethodSelector;
import org.springframework.messaging.handler.method.InvocableHandlerMethod;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.annotation.SubscribeEvent;
import org.springframework.messaging.simp.annotation.UnsubscribeEvent;
import org.springframework.messaging.simp.annotation.support.PrincipalMethodArgumentResolver;
import org.springframework.messaging.simp.annotation.support.ReplyToMethodReturnValueHandler;
import org.springframework.messaging.simp.annotation.support.SubscriptionMethodReturnValueHandler;
import org.springframework.messaging.support.converter.MessageConverter;
import org.springframework.stereotype.Controller;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils.MethodFilter;


/**
 * @author Rossen Stoyanchev
 * @since 4.0
 */
public class AnnotationMethodMessageHandler implements MessageHandler, ApplicationContextAware, InitializingBean {

	private static final Log logger = LogFactory.getLog(AnnotationMethodMessageHandler.class);

	private final SimpMessageSendingOperations inboundMessagingTemplate;

	private final SimpMessageSendingOperations outboundMessagingTemplate;

	private MessageConverter<?> messageConverter;

	private ApplicationContext applicationContext;

	private Map<MappingInfo, HandlerMethod> messageMethods = new HashMap<MappingInfo, HandlerMethod>();

	private Map<MappingInfo, HandlerMethod> subscribeMethods = new HashMap<MappingInfo, HandlerMethod>();

	private Map<MappingInfo, HandlerMethod> unsubscribeMethods = new HashMap<MappingInfo, HandlerMethod>();

	private final Map<Class<?>, ExceptionHandlerMethodResolver> exceptionHandlerCache =
			new ConcurrentHashMap<Class<?>, ExceptionHandlerMethodResolver>(64);

	private HandlerMethodArgumentResolverComposite argumentResolvers = new HandlerMethodArgumentResolverComposite();

	private HandlerMethodReturnValueHandlerComposite returnValueHandlers = new HandlerMethodReturnValueHandlerComposite();


	/**
	 * @param inboundMessagingTemplate a template for sending messages on the channel
	 *        where incoming messages from clients are sent; essentially messages sent
	 *        through this template will be re-processed by the application. One example
	 *        is the use of {@link ReplyTo} annotation on a method to send a broadcast
	 *        message.
	 * @param outboundMessagingTemplate a template for sending messages on the client used
	 *        to send messages back out to connected clients; such messages must have all
	 *        necessary information to reach the client such as session and subscription
	 *        id's. One example is returning a value from an {@link SubscribeEvent}
	 *        method.
	 */
	public AnnotationMethodMessageHandler(SimpMessageSendingOperations inboundMessagingTemplate,
			SimpMessageSendingOperations outboundMessagingTemplate) {

		Assert.notNull(inboundMessagingTemplate, "inboundMessagingTemplate is required");
		Assert.notNull(outboundMessagingTemplate, "outboundMessagingTemplate is required");
		this.inboundMessagingTemplate = inboundMessagingTemplate;
		this.outboundMessagingTemplate = outboundMessagingTemplate;
	}

	/**
	 * TODO: multiple converters with 'content-type' header
	 */
	public void setMessageConverter(MessageConverter<?> converter) {
		this.messageConverter = converter;
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
	}

	@Override
	public void afterPropertiesSet() {

		initHandlerMethods();

		this.argumentResolvers.addResolver(new PrincipalMethodArgumentResolver());
		this.argumentResolvers.addResolver(new MessageBodyMethodArgumentResolver(this.messageConverter));

		this.returnValueHandlers.addHandler(new ReplyToMethodReturnValueHandler(this.inboundMessagingTemplate));
		this.returnValueHandlers.addHandler(new SubscriptionMethodReturnValueHandler(this.outboundMessagingTemplate));
	}

	protected void initHandlerMethods() {
		String[] beanNames = this.applicationContext.getBeanNamesForType(Object.class);
		for (String beanName : beanNames) {
			if (isHandler(this.applicationContext.getType(beanName))){
				detectHandlerMethods(beanName);
			}
		}
	}

	protected boolean isHandler(Class<?> beanType) {
		return ((AnnotationUtils.findAnnotation(beanType, Controller.class) != null) ||
				(AnnotationUtils.findAnnotation(beanType, MessageMapping.class) != null));
	}

	protected void detectHandlerMethods(Object handler) {

		Class<?> handlerType = (handler instanceof String) ?
				this.applicationContext.getType((String) handler) : handler.getClass();

		final Class<?> userType = ClassUtils.getUserClass(handlerType);

		initHandlerMethods(handler, userType, MessageMapping.class,
				new MessageMappingInfoCreator(), this.messageMethods);

		initHandlerMethods(handler, userType, SubscribeEvent.class,
				new SubscribeMappingInfoCreator(), this.subscribeMethods);

		initHandlerMethods(handler, userType, UnsubscribeEvent.class,
				new UnsubscribeMappingInfoCreator(), this.unsubscribeMethods);
	}

	private <A extends Annotation> void initHandlerMethods(Object handler, Class<?> handlerType,
			final Class<A> annotationType, MappingInfoCreator<A> mappingInfoCreator,
			Map<MappingInfo, HandlerMethod> handlerMethods) {

		Set<Method> messageMethods = HandlerMethodSelector.selectMethods(handlerType, new MethodFilter() {
			@Override
			public boolean matches(Method method) {
				return AnnotationUtils.findAnnotation(method, annotationType) != null;
			}
		});

		for (Method method : messageMethods) {
			A annotation = AnnotationUtils.findAnnotation(method, annotationType);
			HandlerMethod hm = createHandlerMethod(handler, method);
			handlerMethods.put(mappingInfoCreator.create(annotation), hm);
		}
	}

	protected HandlerMethod createHandlerMethod(Object handler, Method method) {
		HandlerMethod handlerMethod;
		if (handler instanceof String) {
			String beanName = (String) handler;
			handlerMethod = new HandlerMethod(beanName, this.applicationContext, method);
		}
		else {
			handlerMethod = new HandlerMethod(handler, method);
		}
		return handlerMethod;
	}

	@Override
	public void handleMessage(Message<?> message) throws MessagingException {

		SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.wrap(message);
		SimpMessageType messageType = headers.getMessageType();

		if (SimpMessageType.MESSAGE.equals(messageType)) {
			handleMessageInternal(message, this.messageMethods);
		}
		else if (SimpMessageType.SUBSCRIBE.equals(messageType)) {
			handleMessageInternal(message, this.subscribeMethods);
		}
		else if (SimpMessageType.UNSUBSCRIBE.equals(messageType)) {
			handleMessageInternal(message, this.unsubscribeMethods);
		}
	}

	private void handleMessageInternal(final Message<?> message, Map<MappingInfo, HandlerMethod> handlerMethods) {

		SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.wrap(message);
		String destination = headers.getDestination();

		HandlerMethod match = getHandlerMethod(destination, handlerMethods);
		if (match == null) {
			return;
		}

		HandlerMethod handlerMethod = match.createWithResolvedBean();

		InvocableHandlerMethod invocableHandlerMethod = new InvocableHandlerMethod(handlerMethod);
		invocableHandlerMethod.setMessageMethodArgumentResolvers(this.argumentResolvers);

		try {
			Object returnValue = invocableHandlerMethod.invoke(message);

			MethodParameter returnType = handlerMethod.getReturnType();
			if (void.class.equals(returnType.getParameterType())) {
				return;
			}
			this.returnValueHandlers.handleReturnValue(returnValue, returnType, message);
		}
		catch (Exception ex) {
			invokeExceptionHandler(message, handlerMethod, ex);
		}
		catch (Throwable ex) {
			// TODO
			ex.printStackTrace();
		}
	}

	private void invokeExceptionHandler(Message<?> message, HandlerMethod handlerMethod, Exception ex) {

		InvocableHandlerMethod exceptionHandlerMethod;
		Class<?> beanType = handlerMethod.getBeanType();
		ExceptionHandlerMethodResolver resolver = this.exceptionHandlerCache.get(beanType);
		if (resolver == null) {
			resolver = new ExceptionHandlerMethodResolver(beanType);
			this.exceptionHandlerCache.put(beanType, resolver);
		}

		Method method = resolver.resolveMethod(ex);
		if (method == null) {
			logger.error("Unhandled exception", ex);
			return;
		}

		exceptionHandlerMethod = new InvocableHandlerMethod(handlerMethod.getBean(), method);
		exceptionHandlerMethod.setMessageMethodArgumentResolvers(this.argumentResolvers);

		try {
			Object returnValue = exceptionHandlerMethod.invoke(message, ex);

			MethodParameter returnType = exceptionHandlerMethod.getReturnType();
			if (void.class.equals(returnType.getParameterType())) {
				return;
			}
			this.returnValueHandlers.handleReturnValue(returnValue, returnType, message);
		}
		catch (Throwable t) {
			logger.error("Error while handling exception", t);
			return;
		}
	}

	protected HandlerMethod getHandlerMethod(String destination, Map<MappingInfo, HandlerMethod> handlerMethods) {
		for (MappingInfo key : handlerMethods.keySet()) {
			for (String mappingDestination : key.getDestinations()) {
				if (destination.equals(mappingDestination)) {
					return handlerMethods.get(key);
				}
			}
		}
		return null;
	}


	private static class MappingInfo {

		private final List<String> destinations;


		public MappingInfo(List<String> destinations) {
			this.destinations = destinations;
		}

		public List<String> getDestinations() {
			return this.destinations;
		}

		@Override
		public String toString() {
			return "MappingInfo [destinations=" + this.destinations + "]";
		}
	}

	private interface MappingInfoCreator<A extends Annotation> {

		MappingInfo create(A annotation);
	}

	private static class MessageMappingInfoCreator implements MappingInfoCreator<MessageMapping> {

		@Override
		public MappingInfo create(MessageMapping annotation) {
			return new MappingInfo(Arrays.asList(annotation.value()));
		}
	}

	private static class SubscribeMappingInfoCreator implements MappingInfoCreator<SubscribeEvent> {

		@Override
		public MappingInfo create(SubscribeEvent annotation) {
			return new MappingInfo(Arrays.asList(annotation.value()));
		}
	}

	private static class UnsubscribeMappingInfoCreator implements MappingInfoCreator<UnsubscribeEvent> {

		@Override
		public MappingInfo create(UnsubscribeEvent annotation) {
			return new MappingInfo(Arrays.asList(annotation.value()));
		}
	}

}
