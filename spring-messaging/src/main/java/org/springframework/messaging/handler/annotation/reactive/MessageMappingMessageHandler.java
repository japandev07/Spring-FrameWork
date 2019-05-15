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

package org.springframework.messaging.handler.annotation.reactive;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import reactor.core.publisher.Mono;

import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.EmbeddedValueResolverAware;
import org.springframework.core.KotlinDetector;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.codec.Decoder;
import org.springframework.core.convert.ConversionService;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.CompositeMessageCondition;
import org.springframework.messaging.handler.DestinationPatternsMessageCondition;
import org.springframework.messaging.handler.HandlerMethod;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.support.AnnotationExceptionHandlerMethodResolver;
import org.springframework.messaging.handler.invocation.AbstractExceptionHandlerMethodResolver;
import org.springframework.messaging.handler.invocation.reactive.AbstractEncoderMethodReturnValueHandler;
import org.springframework.messaging.handler.invocation.reactive.AbstractMethodMessageHandler;
import org.springframework.messaging.handler.invocation.reactive.HandlerMethodArgumentResolver;
import org.springframework.messaging.handler.invocation.reactive.HandlerMethodReturnValueHandler;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Controller;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.RouteMatcher;
import org.springframework.util.SimpleRouteMatcher;
import org.springframework.util.StringValueResolver;
import org.springframework.validation.Validator;

/**
 * Extension of {@link AbstractMethodMessageHandler} for reactive, non-blocking
 * handling of messages via {@link MessageMapping @MessageMapping} methods.
 * By default such methods are detected in {@code @Controller} Spring beans but
 * that can be changed via {@link #setHandlerPredicate(Predicate)}.
 *
 * <p>Payloads for incoming messages are decoded through the configured
 * {@link #setDecoders(List)} decoders, with the help of
 * {@link PayloadMethodArgumentResolver}.
 *
 * <p>There is no default handling for return values but
 * {@link #setReturnValueHandlerConfigurer} can be used to configure custom
 * return value handlers. Sub-classes may also override
 * {@link #initReturnValueHandlers()} to set up default return value handlers.
 *
 * @author Rossen Stoyanchev
 * @since 5.2
 * @see AbstractEncoderMethodReturnValueHandler
 */
public class MessageMappingMessageHandler extends AbstractMethodMessageHandler<CompositeMessageCondition>
		implements EmbeddedValueResolverAware {

	@Nullable
	private Predicate<Class<?>> handlerPredicate =
			beanType -> AnnotatedElementUtils.hasAnnotation(beanType, Controller.class);

	private final List<Decoder<?>> decoders = new ArrayList<>();

	@Nullable
	private Validator validator;

	private RouteMatcher routeMatcher;

	private ConversionService conversionService = new DefaultFormattingConversionService();

	@Nullable
	private StringValueResolver valueResolver;


	public MessageMappingMessageHandler() {
		AntPathMatcher pathMatcher = new AntPathMatcher();
		pathMatcher.setPathSeparator(".");
		this.routeMatcher = new SimpleRouteMatcher(pathMatcher);
	}


	/**
	 * Manually configure handlers to check for {@code @MessageMapping} methods.
	 * <p><strong>Note:</strong> the given handlers are not required to be
	 * annotated with {@code @Controller}. Consider also using
	 * {@link #setAutoDetectDisabled()} if the intent is to use these handlers
	 * instead of, and not in addition to {@code @Controller} classes. Or
	 * alternatively use {@link #setHandlerPredicate(Predicate)} to select a
	 * different set of beans based on a different criteria.
	 * @param handlers the handlers to register
	 * @see #setAutoDetectDisabled()
	 * @see #setHandlerPredicate(Predicate)
	 */
	public void setHandlers(List<Object> handlers) {
		for (Object handler : handlers) {
			detectHandlerMethods(handler);
		}
		// Disable auto-detection..
		this.handlerPredicate = null;
	}

	/**
	 * Configure the predicate to use for selecting which Spring beans to check
	 * for {@code @MessageMapping} methods. When set to {@code null},
	 * auto-detection is turned off which is what
	 * {@link #setAutoDetectDisabled()} does internally.
	 * <p>The predicate used by default selects {@code @Controller} classes.
	 * @see #setHandlers(List)
	 * @see #setAutoDetectDisabled()
	 */
	public void setHandlerPredicate(@Nullable Predicate<Class<?>> handlerPredicate) {
		this.handlerPredicate = handlerPredicate;
	}

	/**
	 * Return the {@link #setHandlerPredicate configured} handler predicate.
	 */
	@Nullable
	public Predicate<Class<?>> getHandlerPredicate() {
		return this.handlerPredicate;
	}

	/**
	 * Disable auto-detection of {@code @MessageMapping} methods, e.g. in
	 * {@code @Controller}s, by setting {@link #setHandlerPredicate(Predicate)
	 * setHandlerPredicate(null)}.
	 */
	public void setAutoDetectDisabled() {
		this.handlerPredicate = null;
	}

	/**
	 * Configure the decoders to use for incoming payloads.
	 */
	public void setDecoders(List<? extends Decoder<?>> decoders) {
		this.decoders.addAll(decoders);
	}

	/**
	 * Return the configured decoders.
	 */
	public List<? extends Decoder<?>> getDecoders() {
		return this.decoders;
	}

	/**
	 * Set the Validator instance used for validating {@code @Payload} arguments.
	 * @see org.springframework.validation.annotation.Validated
	 * @see PayloadMethodArgumentResolver
	 */
	public void setValidator(@Nullable Validator validator) {
		this.validator = validator;
	}

	/**
	 * Return the configured Validator instance.
	 */
	@Nullable
	public Validator getValidator() {
		return this.validator;
	}

	/**
	 * Set the {@code RouteMatcher} to use for mapping messages to handlers
	 * based on the route patterns they're configured with.
	 * <p>By default, {@link SimpleRouteMatcher} is used, backed by
	 * {@link AntPathMatcher} with "." as separator. For greater
	 * efficiency consider using the {@code PathPatternRouteMatcher} from
	 * {@code spring-web} instead.
	 */
	public void setRouteMatcher(RouteMatcher routeMatcher) {
		Assert.notNull(routeMatcher, "RouteMatcher must not be null");
		this.routeMatcher = routeMatcher;
	}

	/**
	 * Return the {@code RouteMatcher} used to map messages to handlers.
	 */
	public RouteMatcher getRouteMatcher() {
		return this.routeMatcher;
	}

	/**
	 * Configure a {@link ConversionService} to use for type conversion of
	 * String based values, e.g. in destination variables or headers.
	 * <p>By default {@link DefaultFormattingConversionService} is used.
	 * @param conversionService the conversion service to use
	 */
	public void setConversionService(ConversionService conversionService) {
		this.conversionService = conversionService;
	}

	/**
	 * Return the configured ConversionService.
	 */
	public ConversionService getConversionService() {
		return this.conversionService;
	}

	@Override
	public void setEmbeddedValueResolver(StringValueResolver resolver) {
		this.valueResolver = resolver;
	}


	@Override
	protected List<? extends HandlerMethodArgumentResolver> initArgumentResolvers() {
		List<HandlerMethodArgumentResolver> resolvers = new ArrayList<>();

		ApplicationContext context = getApplicationContext();
		ConfigurableBeanFactory beanFactory = (context instanceof ConfigurableApplicationContext ?
				((ConfigurableApplicationContext) context).getBeanFactory() : null);

		// Annotation-based resolvers
		resolvers.add(new HeaderMethodArgumentResolver(this.conversionService, beanFactory));
		resolvers.add(new HeadersMethodArgumentResolver());
		resolvers.add(new DestinationVariableMethodArgumentResolver(this.conversionService));

		// Type-based...
		if (KotlinDetector.isKotlinPresent()) {
			resolvers.add(new ContinuationHandlerMethodArgumentResolver());
		}

		// Custom resolvers
		resolvers.addAll(getArgumentResolverConfigurer().getCustomResolvers());

		// Catch-all
		resolvers.add(new PayloadMethodArgumentResolver(
				this.decoders, this.validator, getReactiveAdapterRegistry(), true));

		return resolvers;
	}

	@Override
	protected List<? extends HandlerMethodReturnValueHandler> initReturnValueHandlers() {
		return Collections.emptyList();
	}

	@Override
	protected Predicate<Class<?>> initHandlerPredicate() {
		return this.handlerPredicate;
	}


	@Override
	protected CompositeMessageCondition getMappingForMethod(Method method, Class<?> handlerType) {
		CompositeMessageCondition methodCondition = getCondition(method);
		if (methodCondition != null) {
			CompositeMessageCondition typeCondition = getCondition(handlerType);
			if (typeCondition != null) {
				return typeCondition.combine(methodCondition);
			}
		}
		return methodCondition;
	}

	@Nullable
	private CompositeMessageCondition getCondition(AnnotatedElement element) {
		MessageMapping annot = AnnotatedElementUtils.findMergedAnnotation(element, MessageMapping.class);
		if (annot == null || annot.value().length == 0) {
			return null;
		}
		String[] destinations = annot.value();
		if (this.valueResolver != null) {
			destinations = Arrays.stream(annot.value())
					.map(s -> this.valueResolver.resolveStringValue(s))
					.toArray(String[]::new);
		}
		return new CompositeMessageCondition(
				new DestinationPatternsMessageCondition(destinations, this.routeMatcher));
	}

	@Override
	protected Set<String> getDirectLookupMappings(CompositeMessageCondition mapping) {
		Set<String> result = new LinkedHashSet<>();
		for (String pattern : mapping.getCondition(DestinationPatternsMessageCondition.class).getPatterns()) {
			if (!this.routeMatcher.isPattern(pattern)) {
				result.add(pattern);
			}
		}
		return result;
	}

	@Override
	protected RouteMatcher.Route getDestination(Message<?> message) {
		return (RouteMatcher.Route) message.getHeaders()
				.get(DestinationPatternsMessageCondition.LOOKUP_DESTINATION_HEADER);
	}

	@Override
	protected CompositeMessageCondition getMatchingMapping(CompositeMessageCondition mapping, Message<?> message) {
		return mapping.getMatchingCondition(message);
	}

	@Override
	protected Comparator<CompositeMessageCondition> getMappingComparator(Message<?> message) {
		return (info1, info2) -> info1.compareTo(info2, message);
	}

	@Override
	protected AbstractExceptionHandlerMethodResolver createExceptionMethodResolverFor(Class<?> beanType) {
		return new AnnotationExceptionHandlerMethodResolver(beanType);
	}

	@Override
	protected Mono<Void> handleMatch(
			CompositeMessageCondition mapping, HandlerMethod handlerMethod, Message<?> message) {

		Set<String> patterns = mapping.getCondition(DestinationPatternsMessageCondition.class).getPatterns();
		if (!CollectionUtils.isEmpty(patterns)) {
			String pattern = patterns.iterator().next();
			RouteMatcher.Route destination = getDestination(message);
			Assert.state(destination != null, "Missing destination header");
			Map<String, String> vars = getRouteMatcher().matchAndExtract(pattern, destination);
			if (!CollectionUtils.isEmpty(vars)) {
				MessageHeaderAccessor mha = MessageHeaderAccessor.getAccessor(message, MessageHeaderAccessor.class);
				Assert.state(mha != null && mha.isMutable(), "Mutable MessageHeaderAccessor required");
				mha.setHeader(DestinationVariableMethodArgumentResolver.DESTINATION_TEMPLATE_VARIABLES_HEADER, vars);
			}
		}
		return super.handleMatch(mapping, handlerMethod, message);
	}

}
