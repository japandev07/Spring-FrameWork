/*
 * Copyright 2002-2017 the original author or authors.
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

package org.springframework.web.reactive.result.method.annotation;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Mono;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.codec.ByteArrayDecoder;
import org.springframework.core.codec.ByteBufferDecoder;
import org.springframework.core.codec.DataBufferDecoder;
import org.springframework.core.codec.StringDecoder;
import org.springframework.http.codec.DecoderHttpMessageReader;
import org.springframework.http.codec.HttpMessageReader;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.support.WebBindingInitializer;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.annotation.ExceptionHandlerMethodResolver;
import org.springframework.web.reactive.BindingContext;
import org.springframework.web.reactive.HandlerAdapter;
import org.springframework.web.reactive.HandlerResult;
import org.springframework.web.reactive.result.method.HandlerMethodArgumentResolver;
import org.springframework.web.reactive.result.method.InvocableHandlerMethod;
import org.springframework.web.reactive.result.method.SyncHandlerMethodArgumentResolver;
import org.springframework.web.reactive.result.method.SyncInvocableHandlerMethod;
import org.springframework.web.server.ServerWebExchange;

/**
 * Supports the invocation of {@code @RequestMapping} methods.
 *
 * @author Rossen Stoyanchev
 * @since 5.0
 */
public class RequestMappingHandlerAdapter implements HandlerAdapter, BeanFactoryAware, InitializingBean {

	private static final Log logger = LogFactory.getLog(RequestMappingHandlerAdapter.class);


	private final List<HttpMessageReader<?>> messageReaders = new ArrayList<>(10);

	private WebBindingInitializer webBindingInitializer;

	private ReactiveAdapterRegistry reactiveAdapterRegistry = new ReactiveAdapterRegistry();

	private List<HandlerMethodArgumentResolver> customArgumentResolvers;

	private List<HandlerMethodArgumentResolver> argumentResolvers;

	private List<SyncHandlerMethodArgumentResolver> customInitBinderArgumentResolvers;

	private List<SyncHandlerMethodArgumentResolver> initBinderArgumentResolvers;

	private ConfigurableBeanFactory beanFactory;


	private ModelInitializer modelInitializer;

	private final Map<Class<?>, Set<Method>> binderMethodCache = new ConcurrentHashMap<>(64);

	private final Map<Class<?>, Set<Method>> attributeMethodCache = new ConcurrentHashMap<>(64);

	private final Map<Class<?>, ExceptionHandlerMethodResolver> exceptionHandlerCache =
			new ConcurrentHashMap<>(64);


	public RequestMappingHandlerAdapter() {
		this.messageReaders.add(new DecoderHttpMessageReader<>(new ByteArrayDecoder()));
		this.messageReaders.add(new DecoderHttpMessageReader<>(new ByteBufferDecoder()));
		this.messageReaders.add(new DecoderHttpMessageReader<>(new DataBufferDecoder()));
		this.messageReaders.add(new DecoderHttpMessageReader<>(new StringDecoder()));
	}


	/**
	 * Configure message readers to de-serialize the request body with.
	 */
	public void setMessageReaders(List<HttpMessageReader<?>> messageReaders) {
		this.messageReaders.clear();
		this.messageReaders.addAll(messageReaders);
	}

	/**
	 * Return the configured message readers.
	 */
	public List<HttpMessageReader<?>> getMessageReaders() {
		return this.messageReaders;
	}

	/**
	 * Provide a WebBindingInitializer with "global" initialization to apply
	 * to every DataBinder instance.
	 */
	public void setWebBindingInitializer(WebBindingInitializer webBindingInitializer) {
		this.webBindingInitializer = webBindingInitializer;
	}

	/**
	 * Return the configured WebBindingInitializer, or {@code null} if none.
	 */
	public WebBindingInitializer getWebBindingInitializer() {
		return this.webBindingInitializer;
	}

	public void setReactiveAdapterRegistry(ReactiveAdapterRegistry registry) {
		this.reactiveAdapterRegistry = registry;
	}

	public ReactiveAdapterRegistry getReactiveAdapterRegistry() {
		return this.reactiveAdapterRegistry;
	}

	/**
	 * Configure custom argument resolvers without overriding the built-in ones.
	 */
	public void setCustomArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
		this.customArgumentResolvers = resolvers;
	}

	/**
	 * Return the custom argument resolvers.
	 */
	public List<HandlerMethodArgumentResolver> getCustomArgumentResolvers() {
		return this.customArgumentResolvers;
	}

	/**
	 * Configure the complete list of supported argument types thus overriding
	 * the resolvers that would otherwise be configured by default.
	 */
	public void setArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
		this.argumentResolvers = new ArrayList<>(resolvers);
	}

	/**
	 * Return the configured argument resolvers.
	 */
	public List<HandlerMethodArgumentResolver> getArgumentResolvers() {
		return this.argumentResolvers;
	}

	/**
	 * Configure custom argument resolvers for {@code @InitBinder} methods.
	 */
	public void setCustomInitBinderArgumentResolvers(List<SyncHandlerMethodArgumentResolver> resolvers) {
		this.customInitBinderArgumentResolvers = resolvers;
	}

	/**
	 * Return the custom {@code @InitBinder} argument resolvers.
	 */
	public List<SyncHandlerMethodArgumentResolver> getCustomInitBinderArgumentResolvers() {
		return this.customInitBinderArgumentResolvers;
	}

	/**
	 * Configure the supported argument types in {@code @InitBinder} methods.
	 */
	public void setInitBinderArgumentResolvers(List<SyncHandlerMethodArgumentResolver> resolvers) {
		this.initBinderArgumentResolvers = null;
		if (resolvers != null) {
			this.initBinderArgumentResolvers = new ArrayList<>();
			this.initBinderArgumentResolvers.addAll(resolvers);
		}
	}

	/**
	 * Return the configured argument resolvers for {@code @InitBinder} methods.
	 */
	public List<SyncHandlerMethodArgumentResolver> getInitBinderArgumentResolvers() {
		return this.initBinderArgumentResolvers;
	}


	/**
	 * A {@link ConfigurableBeanFactory} is expected for resolving expressions
	 * in method argument default values.
	 */
	@Override
	public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
		if (beanFactory instanceof ConfigurableBeanFactory) {
			this.beanFactory = (ConfigurableBeanFactory) beanFactory;
		}
	}

	public ConfigurableBeanFactory getBeanFactory() {
		return this.beanFactory;
	}


	@Override
	public void afterPropertiesSet() throws Exception {
		if (this.argumentResolvers == null) {
			this.argumentResolvers = getDefaultArgumentResolvers();
		}
		if (this.initBinderArgumentResolvers == null) {
			this.initBinderArgumentResolvers = getDefaultInitBinderArgumentResolvers();
		}
		this.modelInitializer = new ModelInitializer(getReactiveAdapterRegistry());
	}

	protected List<HandlerMethodArgumentResolver> getDefaultArgumentResolvers() {
		List<HandlerMethodArgumentResolver> resolvers = new ArrayList<>();

		// Annotation-based argument resolution
		resolvers.add(new RequestParamMethodArgumentResolver(getBeanFactory()));
		resolvers.add(new RequestParamMapMethodArgumentResolver());
		resolvers.add(new PathVariableMethodArgumentResolver(getBeanFactory()));
		resolvers.add(new PathVariableMapMethodArgumentResolver());
		resolvers.add(new RequestBodyArgumentResolver(getMessageReaders(), getReactiveAdapterRegistry()));
		resolvers.add(new ModelAttributeMethodArgumentResolver(getReactiveAdapterRegistry()));
		resolvers.add(new RequestHeaderMethodArgumentResolver(getBeanFactory()));
		resolvers.add(new RequestHeaderMapMethodArgumentResolver());
		resolvers.add(new CookieValueMethodArgumentResolver(getBeanFactory()));
		resolvers.add(new ExpressionValueMethodArgumentResolver(getBeanFactory()));
		resolvers.add(new SessionAttributeMethodArgumentResolver(getBeanFactory()));
		resolvers.add(new RequestAttributeMethodArgumentResolver(getBeanFactory()));

		// Type-based argument resolution
		resolvers.add(new HttpEntityArgumentResolver(getMessageReaders(), getReactiveAdapterRegistry()));
		resolvers.add(new ModelArgumentResolver());
		resolvers.add(new ErrorsMethodArgumentResolver(getReactiveAdapterRegistry()));
		resolvers.add(new ServerWebExchangeArgumentResolver());
		resolvers.add(new PrincipalArgumentResolver());
		resolvers.add(new WebSessionArgumentResolver());

		// Custom resolvers
		if (getCustomArgumentResolvers() != null) {
			resolvers.addAll(getCustomArgumentResolvers());
		}

		// Catch-all
		resolvers.add(new RequestParamMethodArgumentResolver(getBeanFactory(), true));
		resolvers.add(new ModelAttributeMethodArgumentResolver(getReactiveAdapterRegistry(), true));
		return resolvers;
	}

	protected List<SyncHandlerMethodArgumentResolver> getDefaultInitBinderArgumentResolvers() {
		List<SyncHandlerMethodArgumentResolver> resolvers = new ArrayList<>();

		// Annotation-based argument resolution
		resolvers.add(new RequestParamMethodArgumentResolver(getBeanFactory(), false));
		resolvers.add(new RequestParamMapMethodArgumentResolver());
		resolvers.add(new PathVariableMethodArgumentResolver(getBeanFactory()));
		resolvers.add(new PathVariableMapMethodArgumentResolver());
		resolvers.add(new RequestHeaderMethodArgumentResolver(getBeanFactory()));
		resolvers.add(new RequestHeaderMapMethodArgumentResolver());
		resolvers.add(new CookieValueMethodArgumentResolver(getBeanFactory()));
		resolvers.add(new ExpressionValueMethodArgumentResolver(getBeanFactory()));
		resolvers.add(new RequestAttributeMethodArgumentResolver(getBeanFactory()));

		// Type-based argument resolution
		resolvers.add(new ModelArgumentResolver());
		resolvers.add(new ServerWebExchangeArgumentResolver());

		// Custom resolvers
		if (getCustomInitBinderArgumentResolvers() != null) {
			resolvers.addAll(getCustomInitBinderArgumentResolvers());
		}

		// Catch-all
		resolvers.add(new RequestParamMethodArgumentResolver(getBeanFactory(), true));
		return resolvers;
	}


	@Override
	public boolean supports(Object handler) {
		return HandlerMethod.class.equals(handler.getClass());
	}

	@Override
	public Mono<HandlerResult> handle(ServerWebExchange exchange, Object handler) {

		HandlerMethod handlerMethod = (HandlerMethod) handler;

		BindingContext bindingContext = new InitBinderBindingContext(
				getWebBindingInitializer(), getBinderMethods(handlerMethod));

		Mono<Void> modelCompletion = this.modelInitializer.initModel(
				bindingContext, getAttributeMethods(handlerMethod), exchange);

		Function<Throwable, Mono<HandlerResult>> exceptionHandler =
				ex -> handleException(ex, handlerMethod, bindingContext, exchange);

		return modelCompletion.then(() -> {

			InvocableHandlerMethod invocable = new InvocableHandlerMethod(handlerMethod);
			invocable.setArgumentResolvers(getArgumentResolvers());

			return invocable.invoke(exchange, bindingContext)
					.doOnNext(result -> result.setExceptionHandler(exceptionHandler))
					.otherwise(exceptionHandler);
		});
	}

	private List<SyncInvocableHandlerMethod> getBinderMethods(HandlerMethod handlerMethod) {

		Class<?> handlerType = handlerMethod.getBeanType();

		Set<Method> methods = this.binderMethodCache.computeIfAbsent(handlerType, aClass ->
				MethodIntrospector.selectMethods(handlerType, BINDER_METHODS));

		return methods.stream()
				.map(method -> {
					Object bean = handlerMethod.getBean();
					SyncInvocableHandlerMethod invocable = new SyncInvocableHandlerMethod(bean, method);
					invocable.setSyncArgumentResolvers(getInitBinderArgumentResolvers());
					return invocable;
				})
				.collect(Collectors.toList());
	}

	private List<InvocableHandlerMethod> getAttributeMethods(HandlerMethod handlerMethod) {

		Class<?> handlerType = handlerMethod.getBeanType();

		Set<Method> methods = this.attributeMethodCache.computeIfAbsent(handlerType, aClass ->
				MethodIntrospector.selectMethods(handlerType, ATTRIBUTE_METHODS));

		return methods.stream()
				.map(method -> {
					Object bean = handlerMethod.getBean();
					InvocableHandlerMethod invocable = new InvocableHandlerMethod(bean, method);
					invocable.setArgumentResolvers(getArgumentResolvers());
					return invocable;
				})
				.collect(Collectors.toList());
	}

	private Mono<HandlerResult> handleException(Throwable ex, HandlerMethod handlerMethod,
			BindingContext bindingContext, ServerWebExchange exchange) {

		ExceptionHandlerMethodResolver resolver = this.exceptionHandlerCache
				.computeIfAbsent(handlerMethod.getBeanType(), ExceptionHandlerMethodResolver::new);

		Method method = resolver.resolveMethodByExceptionType(ex.getClass());

		if (method != null) {
			Object bean = handlerMethod.getBean();
			InvocableHandlerMethod invocable = new InvocableHandlerMethod(bean, method);
			invocable.setArgumentResolvers(getArgumentResolvers());
			try {
				if (logger.isDebugEnabled()) {
					logger.debug("Invoking @ExceptionHandler method: " + invocable.getMethod());
				}
				bindingContext.getModel().asMap().clear();
				return invocable.invoke(exchange, bindingContext, ex);
			}
			catch (Throwable invocationEx) {
				if (logger.isWarnEnabled()) {
					logger.warn("Failed to invoke: " + invocable.getMethod(), invocationEx);
				}
			}
		}

		return Mono.error(ex);
	}


	/**
	 * MethodFilter that matches {@link InitBinder @InitBinder} methods.
	 */
	public static final ReflectionUtils.MethodFilter BINDER_METHODS = method ->
			AnnotationUtils.findAnnotation(method, InitBinder.class) != null;

	/**
	 * MethodFilter that matches {@link ModelAttribute @ModelAttribute} methods.
	 */
	public static final ReflectionUtils.MethodFilter ATTRIBUTE_METHODS = method ->
			(AnnotationUtils.findAnnotation(method, RequestMapping.class) == null) &&
					(AnnotationUtils.findAnnotation(method, ModelAttribute.class) != null);

}
