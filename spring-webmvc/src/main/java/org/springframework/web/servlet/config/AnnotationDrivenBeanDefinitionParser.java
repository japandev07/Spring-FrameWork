/*
 * Copyright 2002-2012 the original author or authors.
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

package org.springframework.web.servlet.config;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.parsing.BeanComponentDefinition;
import org.springframework.beans.factory.parsing.CompositeComponentDefinition;
import org.springframework.beans.factory.support.ManagedList;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.beans.factory.xml.BeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.format.support.FormattingConversionServiceFactoryBean;
import org.springframework.http.MediaType;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.ResourceHttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.feed.AtomFeedHttpMessageConverter;
import org.springframework.http.converter.feed.RssChannelHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.converter.json.MappingJacksonHttpMessageConverter;
import org.springframework.http.converter.xml.Jaxb2RootElementHttpMessageConverter;
import org.springframework.http.converter.xml.SourceHttpMessageConverter;
import org.springframework.http.converter.xml.XmlAwareFormHttpMessageConverter;
import org.springframework.util.ClassUtils;
import org.springframework.util.xml.DomUtils;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.HttpRequestHandler;
import org.springframework.web.accept.ContentNegotiationManager;
import org.springframework.web.accept.HeaderContentNegotiationStrategy;
import org.springframework.web.accept.PathExtensionContentNegotiationStrategy;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.support.ConfigurableWebBindingInitializer;
import org.springframework.web.bind.support.WebArgumentResolver;
import org.springframework.web.servlet.HandlerAdapter;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.handler.BeanNameUrlHandlerMapping;
import org.springframework.web.servlet.handler.ConversionServiceExposingInterceptor;
import org.springframework.web.servlet.handler.MappedInterceptor;
import org.springframework.web.servlet.mvc.Controller;
import org.springframework.web.servlet.mvc.HttpRequestHandlerAdapter;
import org.springframework.web.servlet.mvc.SimpleControllerHandlerAdapter;
import org.springframework.web.servlet.mvc.annotation.ResponseStatusExceptionResolver;
import org.springframework.web.servlet.mvc.method.annotation.ExceptionHandlerExceptionResolver;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.ServletWebArgumentResolverAdapter;
import org.springframework.web.servlet.mvc.support.DefaultHandlerExceptionResolver;
import org.w3c.dom.Element;

/**
 * A {@link BeanDefinitionParser} that provides the configuration for the
 * {@code <annotation-driven/>} MVC namespace  element.
 *
 * <p>This class registers the following {@link HandlerMapping}s:</p>
 * <ul>
 * 	<li>{@link RequestMappingHandlerMapping}
 * 	ordered at 0 for mapping requests to annotated controller methods.
 * 	<li>{@link BeanNameUrlHandlerMapping}
 * 	ordered at 2 to map URL paths to controller bean names.
 * </ul>
 *
 * <p><strong>Note:</strong> Additional HandlerMappings may be registered
 * as a result of using the {@code <view-controller>} or the
 * {@code <resources>} MVC namespace elements.
 *
 * <p>This class registers the following {@link HandlerAdapter}s:
 * <ul>
 * 	<li>{@link RequestMappingHandlerAdapter}
 * 	for processing requests with annotated controller methods.
 * 	<li>{@link HttpRequestHandlerAdapter}
 * 	for processing requests with {@link HttpRequestHandler}s.
 * 	<li>{@link SimpleControllerHandlerAdapter}
 * 	for processing requests with interface-based {@link Controller}s.
 * </ul>
 *
 * <p>This class registers the following {@link HandlerExceptionResolver}s:
 * <ul>
 * 	<li>{@link ExceptionHandlerExceptionResolver} for handling exceptions
 * 	through @{@link ExceptionHandler} methods.
 * 	<li>{@link ResponseStatusExceptionResolver} for exceptions annotated
 * 	with @{@link ResponseStatus}.
 * 	<li>{@link DefaultHandlerExceptionResolver} for resolving known Spring
 * 	exception types
 * </ul>
 *
 * <p>Both the {@link RequestMappingHandlerAdapter} and the
 * {@link ExceptionHandlerExceptionResolver} are configured with instances of
 * the following by default:
 * <ul>
 * 	<li>A {@link ContentNegotiationManager}
 * 	<li>A {@link DefaultFormattingConversionService}
 * 	<li>A {@link LocalValidatorFactoryBean} if a JSR-303 implementation is
 * 	available on the classpath
 * 	<li>A range of {@link HttpMessageConverter}s depending on what 3rd party
 * 	libraries are available on the classpath.
 * </ul>
 *
 * @author Keith Donald
 * @author Juergen Hoeller
 * @author Arjen Poutsma
 * @author Rossen Stoyanchev
 * @since 3.0
 */
class AnnotationDrivenBeanDefinitionParser implements BeanDefinitionParser {

	private static final boolean jsr303Present = ClassUtils.isPresent(
			"javax.validation.Validator", AnnotationDrivenBeanDefinitionParser.class.getClassLoader());

	private static final boolean jaxb2Present =
			ClassUtils.isPresent("javax.xml.bind.Binder", AnnotationDrivenBeanDefinitionParser.class.getClassLoader());

	private static final boolean jackson2Present =
			ClassUtils.isPresent("com.fasterxml.jackson.databind.ObjectMapper", AnnotationDrivenBeanDefinitionParser.class.getClassLoader()) &&
					ClassUtils.isPresent("com.fasterxml.jackson.core.JsonGenerator", AnnotationDrivenBeanDefinitionParser.class.getClassLoader());

	private static final boolean jacksonPresent =
			ClassUtils.isPresent("org.codehaus.jackson.map.ObjectMapper", AnnotationDrivenBeanDefinitionParser.class.getClassLoader()) &&
					ClassUtils.isPresent("org.codehaus.jackson.JsonGenerator", AnnotationDrivenBeanDefinitionParser.class.getClassLoader());

	private static boolean romePresent =
			ClassUtils.isPresent("com.sun.syndication.feed.WireFeed", AnnotationDrivenBeanDefinitionParser.class.getClassLoader());

	public BeanDefinition parse(Element element, ParserContext parserContext) {
		Object source = parserContext.extractSource(element);

		CompositeComponentDefinition compDefinition = new CompositeComponentDefinition(element.getTagName(), source);
		parserContext.pushContainingComponent(compDefinition);

		RuntimeBeanReference contentNegotiationManager = getContentNegotiationManager(element, source, parserContext);

		RootBeanDefinition handlerMappingDef = new RootBeanDefinition(RequestMappingHandlerMapping.class);
		handlerMappingDef.setSource(source);
		handlerMappingDef.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
		handlerMappingDef.getPropertyValues().add("order", 0);
		handlerMappingDef.getPropertyValues().add("contentNegotiationManager", contentNegotiationManager);
		String methodMappingName = parserContext.getReaderContext().registerWithGeneratedName(handlerMappingDef);

		RuntimeBeanReference conversionService = getConversionService(element, source, parserContext);
		RuntimeBeanReference validator = getValidator(element, source, parserContext);
		RuntimeBeanReference messageCodesResolver = getMessageCodesResolver(element, source, parserContext);

		RootBeanDefinition bindingDef = new RootBeanDefinition(ConfigurableWebBindingInitializer.class);
		bindingDef.setSource(source);
		bindingDef.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
		bindingDef.getPropertyValues().add("conversionService", conversionService);
		bindingDef.getPropertyValues().add("validator", validator);
		bindingDef.getPropertyValues().add("messageCodesResolver", messageCodesResolver);

		ManagedList<?> messageConverters = getMessageConverters(element, source, parserContext);
		ManagedList<?> argumentResolvers = getArgumentResolvers(element, source, parserContext);
		ManagedList<?> returnValueHandlers = getReturnValueHandlers(element, source, parserContext);
		String asyncTimeout = getAsyncTimeout(element, source, parserContext);
		RuntimeBeanReference asyncExecutor = getAsyncExecutor(element, source, parserContext);

		RootBeanDefinition handlerAdapterDef = new RootBeanDefinition(RequestMappingHandlerAdapter.class);
		handlerAdapterDef.setSource(source);
		handlerAdapterDef.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
		handlerAdapterDef.getPropertyValues().add("contentNegotiationManager", contentNegotiationManager);
		handlerAdapterDef.getPropertyValues().add("webBindingInitializer", bindingDef);
		handlerAdapterDef.getPropertyValues().add("messageConverters", messageConverters);
		if (element.hasAttribute("ignoreDefaultModelOnRedirect")) {
			Boolean ignoreDefaultModel = Boolean.valueOf(element.getAttribute("ignoreDefaultModelOnRedirect"));
			handlerAdapterDef.getPropertyValues().add("ignoreDefaultModelOnRedirect", ignoreDefaultModel);
		}
		if (argumentResolvers != null) {
			handlerAdapterDef.getPropertyValues().add("customArgumentResolvers", argumentResolvers);
		}
		if (returnValueHandlers != null) {
			handlerAdapterDef.getPropertyValues().add("customReturnValueHandlers", returnValueHandlers);
		}
		if (asyncTimeout != null) {
			handlerAdapterDef.getPropertyValues().add("asyncRequestTimeout", asyncTimeout);
		}
		if (asyncExecutor != null) {
			handlerAdapterDef.getPropertyValues().add("taskExecutor", asyncExecutor);
		}
		String handlerAdapterName = parserContext.getReaderContext().registerWithGeneratedName(handlerAdapterDef);

		RootBeanDefinition csInterceptorDef = new RootBeanDefinition(ConversionServiceExposingInterceptor.class);
		csInterceptorDef.setSource(source);
		csInterceptorDef.getConstructorArgumentValues().addIndexedArgumentValue(0, conversionService);
		RootBeanDefinition mappedCsInterceptorDef = new RootBeanDefinition(MappedInterceptor.class);
		mappedCsInterceptorDef.setSource(source);
		mappedCsInterceptorDef.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
		mappedCsInterceptorDef.getConstructorArgumentValues().addIndexedArgumentValue(0, (Object) null);
		mappedCsInterceptorDef.getConstructorArgumentValues().addIndexedArgumentValue(1, csInterceptorDef);
		String mappedInterceptorName = parserContext.getReaderContext().registerWithGeneratedName(mappedCsInterceptorDef);

		RootBeanDefinition exceptionHandlerExceptionResolver = new RootBeanDefinition(ExceptionHandlerExceptionResolver.class);
		exceptionHandlerExceptionResolver.setSource(source);
		exceptionHandlerExceptionResolver.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
		exceptionHandlerExceptionResolver.getPropertyValues().add("contentNegotiationManager", contentNegotiationManager);
		exceptionHandlerExceptionResolver.getPropertyValues().add("messageConverters", messageConverters);
		exceptionHandlerExceptionResolver.getPropertyValues().add("order", 0);
		String methodExceptionResolverName =
				parserContext.getReaderContext().registerWithGeneratedName(exceptionHandlerExceptionResolver);

		RootBeanDefinition responseStatusExceptionResolver = new RootBeanDefinition(ResponseStatusExceptionResolver.class);
		responseStatusExceptionResolver.setSource(source);
		responseStatusExceptionResolver.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
		responseStatusExceptionResolver.getPropertyValues().add("order", 1);
		String responseStatusExceptionResolverName =
				parserContext.getReaderContext().registerWithGeneratedName(responseStatusExceptionResolver);

		RootBeanDefinition defaultExceptionResolver = new RootBeanDefinition(DefaultHandlerExceptionResolver.class);
		defaultExceptionResolver.setSource(source);
		defaultExceptionResolver.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
		defaultExceptionResolver.getPropertyValues().add("order", 2);
		String defaultExceptionResolverName =
				parserContext.getReaderContext().registerWithGeneratedName(defaultExceptionResolver);

		parserContext.registerComponent(new BeanComponentDefinition(handlerMappingDef, methodMappingName));
		parserContext.registerComponent(new BeanComponentDefinition(handlerAdapterDef, handlerAdapterName));
		parserContext.registerComponent(new BeanComponentDefinition(exceptionHandlerExceptionResolver, methodExceptionResolverName));
		parserContext.registerComponent(new BeanComponentDefinition(responseStatusExceptionResolver, responseStatusExceptionResolverName));
		parserContext.registerComponent(new BeanComponentDefinition(defaultExceptionResolver, defaultExceptionResolverName));
		parserContext.registerComponent(new BeanComponentDefinition(mappedCsInterceptorDef, mappedInterceptorName));

		// Ensure BeanNameUrlHandlerMapping (SPR-8289) and default HandlerAdapters are not "turned off"
		MvcNamespaceUtils.registerDefaultComponents(parserContext, source);

		parserContext.popAndRegisterContainingComponent();

		return null;
	}

	private RuntimeBeanReference getConversionService(Element element, Object source, ParserContext parserContext) {
		RuntimeBeanReference conversionServiceRef;
		if (element.hasAttribute("conversion-service")) {
			conversionServiceRef = new RuntimeBeanReference(element.getAttribute("conversion-service"));
		}
		else {
			RootBeanDefinition conversionDef = new RootBeanDefinition(FormattingConversionServiceFactoryBean.class);
			conversionDef.setSource(source);
			conversionDef.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
			String conversionName = parserContext.getReaderContext().registerWithGeneratedName(conversionDef);
			parserContext.registerComponent(new BeanComponentDefinition(conversionDef, conversionName));
			conversionServiceRef = new RuntimeBeanReference(conversionName);
		}
		return conversionServiceRef;
	}

	private RuntimeBeanReference getValidator(Element element, Object source, ParserContext parserContext) {
		if (element.hasAttribute("validator")) {
			return new RuntimeBeanReference(element.getAttribute("validator"));
		}
		else if (jsr303Present) {
			RootBeanDefinition validatorDef = new RootBeanDefinition(LocalValidatorFactoryBean.class);
			validatorDef.setSource(source);
			validatorDef.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
			String validatorName = parserContext.getReaderContext().registerWithGeneratedName(validatorDef);
			parserContext.registerComponent(new BeanComponentDefinition(validatorDef, validatorName));
			return new RuntimeBeanReference(validatorName);
		}
		else {
			return null;
		}
	}

	private RuntimeBeanReference getContentNegotiationManager(Element element, Object source, ParserContext parserContext) {
		RuntimeBeanReference contentNegotiationManagerRef;
		if (element.hasAttribute("content-negotiation-manager")) {
			contentNegotiationManagerRef = new RuntimeBeanReference(element.getAttribute("content-negotiation-manager"));
		}
		else {
			RootBeanDefinition managerDef = new RootBeanDefinition(ContentNegotiationManager.class);
			managerDef.setSource(source);
			managerDef.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
			PathExtensionContentNegotiationStrategy strategy1 = new PathExtensionContentNegotiationStrategy(getDefaultMediaTypes());
			HeaderContentNegotiationStrategy strategy2 = new HeaderContentNegotiationStrategy();
			managerDef.getConstructorArgumentValues().addIndexedArgumentValue(0, Arrays.asList(strategy1,strategy2));

			String beanName = "mvcContentNegotiationManager";
			parserContext.getReaderContext().getRegistry().registerBeanDefinition(beanName , managerDef);
			parserContext.registerComponent(new BeanComponentDefinition(managerDef, beanName));
			contentNegotiationManagerRef = new RuntimeBeanReference(beanName);
		}
		return contentNegotiationManagerRef;
	}

	private Map<String, MediaType> getDefaultMediaTypes() {
		Map<String, MediaType> map = new HashMap<String, MediaType>();
		if (romePresent) {
			map.put("atom", MediaType.APPLICATION_ATOM_XML);
			map.put("rss", MediaType.valueOf("application/rss+xml"));
		}
		if (jackson2Present || jacksonPresent) {
			map.put("json", MediaType.APPLICATION_JSON);
		}
		if (jaxb2Present) {
			map.put("xml", MediaType.APPLICATION_XML);
		}
		return map;
	}

	private RuntimeBeanReference getMessageCodesResolver(Element element, Object source, ParserContext parserContext) {
		if (element.hasAttribute("message-codes-resolver")) {
			return new RuntimeBeanReference(element.getAttribute("message-codes-resolver"));
		} else {
			return null;
		}
	}

	private String getAsyncTimeout(Element element, Object source, ParserContext parserContext) {
		Element asyncElement = DomUtils.getChildElementByTagName(element, "async-support");
		return (asyncElement != null) ? asyncElement.getAttribute("default-timeout") : null;
	}

	private RuntimeBeanReference getAsyncExecutor(Element element, Object source, ParserContext parserContext) {
		Element asyncElement = DomUtils.getChildElementByTagName(element, "async-support");
		if (asyncElement != null) {
			if (asyncElement.hasAttribute("task-executor")) {
				return new RuntimeBeanReference(asyncElement.getAttribute("task-executor"));
			}
		}
		return null;
	}

	private ManagedList<?> getArgumentResolvers(Element element, Object source, ParserContext parserContext) {
		Element resolversElement = DomUtils.getChildElementByTagName(element, "argument-resolvers");
		if (resolversElement != null) {
			ManagedList<BeanDefinitionHolder> argumentResolvers = extractBeanSubElements(resolversElement, parserContext);
			return wrapWebArgumentResolverBeanDefs(argumentResolvers);
		}
		return null;
	}

	private ManagedList<?> getReturnValueHandlers(Element element, Object source, ParserContext parserContext) {
		Element handlersElement = DomUtils.getChildElementByTagName(element, "return-value-handlers");
		if (handlersElement != null) {
			return extractBeanSubElements(handlersElement, parserContext);
		}
		return null;
	}

	private ManagedList<?> getMessageConverters(Element element, Object source, ParserContext parserContext) {
		Element convertersElement = DomUtils.getChildElementByTagName(element, "message-converters");
		ManagedList<? super Object> messageConverters = new ManagedList<Object>();
		if (convertersElement != null) {
			messageConverters.setSource(source);
			for (Element converter : DomUtils.getChildElementsByTagName(convertersElement, "bean")) {
				BeanDefinitionHolder beanDef = parserContext.getDelegate().parseBeanDefinitionElement(converter);
				beanDef = parserContext.getDelegate().decorateBeanDefinitionIfRequired(converter, beanDef);
				messageConverters.add(beanDef);
			}
		}

		if (convertersElement == null || Boolean.valueOf(convertersElement.getAttribute("register-defaults"))) {
			messageConverters.setSource(source);
			messageConverters.add(createConverterBeanDefinition(ByteArrayHttpMessageConverter.class, source));

			RootBeanDefinition stringConverterDef = createConverterBeanDefinition(StringHttpMessageConverter.class, source);
			stringConverterDef.getPropertyValues().add("writeAcceptCharset", false);
			messageConverters.add(stringConverterDef);

			messageConverters.add(createConverterBeanDefinition(ResourceHttpMessageConverter.class, source));
			messageConverters.add(createConverterBeanDefinition(SourceHttpMessageConverter.class, source));
			messageConverters.add(createConverterBeanDefinition(XmlAwareFormHttpMessageConverter.class, source));
			if (romePresent) {
				messageConverters.add(createConverterBeanDefinition(AtomFeedHttpMessageConverter.class, source));
				messageConverters.add(createConverterBeanDefinition(RssChannelHttpMessageConverter.class, source));
			}
			if (jaxb2Present) {
				messageConverters
						.add(createConverterBeanDefinition(Jaxb2RootElementHttpMessageConverter.class, source));
			}
			if (jackson2Present) {
				messageConverters.add(createConverterBeanDefinition(MappingJackson2HttpMessageConverter.class, source));
			}
			else if (jacksonPresent) {
				messageConverters.add(createConverterBeanDefinition(MappingJacksonHttpMessageConverter.class, source));
			}
		}
		return messageConverters;
	}

	private RootBeanDefinition createConverterBeanDefinition(
			Class<? extends HttpMessageConverter> converterClass, Object source) {

		RootBeanDefinition beanDefinition = new RootBeanDefinition(converterClass);
		beanDefinition.setSource(source);
		beanDefinition.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);

		return beanDefinition;
	}

	private ManagedList<BeanDefinitionHolder> extractBeanSubElements(Element parentElement, ParserContext parserContext) {
		ManagedList<BeanDefinitionHolder> list = new ManagedList<BeanDefinitionHolder>();
		list.setSource(parserContext.extractSource(parentElement));
		for (Element beanElement : DomUtils.getChildElementsByTagName(parentElement, "bean")) {
			BeanDefinitionHolder beanDef = parserContext.getDelegate().parseBeanDefinitionElement(beanElement);
			beanDef = parserContext.getDelegate().decorateBeanDefinitionIfRequired(beanElement, beanDef);
			list.add(beanDef);
		}
		return list;
	}

	private ManagedList<BeanDefinitionHolder> wrapWebArgumentResolverBeanDefs(List<BeanDefinitionHolder> beanDefs) {
		ManagedList<BeanDefinitionHolder> result = new ManagedList<BeanDefinitionHolder>();

		for (BeanDefinitionHolder beanDef : beanDefs) {
			String className = beanDef.getBeanDefinition().getBeanClassName();
			Class<?> clazz = ClassUtils.resolveClassName(className, ClassUtils.getDefaultClassLoader());

			if (WebArgumentResolver.class.isAssignableFrom(clazz)) {
				RootBeanDefinition adapter = new RootBeanDefinition(ServletWebArgumentResolverAdapter.class);
				adapter.getConstructorArgumentValues().addIndexedArgumentValue(0, beanDef);
				result.add(new BeanDefinitionHolder(adapter, beanDef.getBeanName() + "Adapter"));
			} else {
				result.add(beanDef);
			}
		}

		return result;
	}

}
