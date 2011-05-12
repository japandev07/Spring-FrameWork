/*
 * Copyright 2002-2011 the original author or authors.
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

package org.springframework.web.servlet.config.annotation;

import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.format.Formatter;
import org.springframework.format.FormatterRegistry;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.validation.Validator;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.context.request.WebRequestInterceptor;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.HandlerInterceptor;

import com.sun.corba.se.impl.presentation.rmi.ExceptionHandler;

/**
 * Defines options for customizing or adding to the default Spring MVC configuration enabled through the use
 * of @{@link EnableWebMvc}. The @{@link Configuration} class annotated with @{@link EnableWebMvc}
 * is the most obvious place to implement this interface. However all @{@link Configuration} classes and more generally
 * all Spring beans that implement this interface will be detected at startup and given a chance to customize Spring
 * MVC configuration provided it is enabled through @{@link EnableWebMvc}.
 *
 * <p>Implementations of this interface will find it convenient to extend {@link WebMvcConfigurerAdapter} that
 * provides default method implementations and allows overriding only methods of interest.
 *
 * @author Rossen Stoyanchev
 * @author Keith Donald
 * @author David Syer
 * @since 3.1
 */
public interface WebMvcConfigurer {

	/**
	 * Add {@link Converter}s and {@link Formatter}s in addition to the ones registered by default.
	 */
	void addFormatters(FormatterRegistry registry);

	/**
	 * Configure the list of {@link HttpMessageConverter}s to use when resolving method arguments or handling
	 * return  values in @{@link RequestMapping} and @{@link ExceptionHandler} methods. 
	 * Specifying custom converters overrides the converters registered by default.
	 * @param converters a list to add message converters to
	 */
	void configureMessageConverters(List<HttpMessageConverter<?>> converters);

	/**
	 * Provide a custom {@link Validator} type replacing the one that would be created by default otherwise. If this
	 * method returns {@code null}, and assuming a JSR-303 implementation is available on the classpath, a validator
	 * of type {@link org.springframework.validation.beanvalidation.LocalValidatorFactoryBean} is created by default.
	 */
	Validator getValidator();

	/**
	 * Add custom {@link HandlerMethodArgumentResolver}s to use in addition to the ones registered by default.
	 * <p>Generally custom argument resolvers are invoked first. However this excludes default argument resolvers that
	 * rely on the presence of annotations (e.g. {@code @RequestParameter}, {@code @PathVariable}, etc.). Those 
	 * argument resolvers are not customizable without configuring RequestMappingHandlerAdapter directly. 
	 * @param argumentResolvers the list of custom converters, initially empty
	 */
	void addArgumentResolvers(List<HandlerMethodArgumentResolver> argumentResolvers);

	/**
	 * Add custom {@link HandlerMethodReturnValueHandler}s to in addition to the ones registered by default.
	 * <p>Generally custom return value handlers are invoked first. However this excludes default return value handlers 
	 * that rely on the presence of annotations (e.g. {@code @ResponseBody}, {@code @ModelAttribute}, etc.). Those 
	 * handlers are not customizable without configuring RequestMappingHandlerAdapter directly.
	 * @param returnValueHandlers the list of custom handlers, initially empty
	 */
	void addReturnValueHandlers(List<HandlerMethodReturnValueHandler> returnValueHandlers);

	/**
	 * Configure the list of {@link HandlerExceptionResolver}s to use for handling unresolved controller exceptions.
	 * Specifying exception resolvers overrides the ones registered by default.
	 * @param exceptionResolvers a list to add exception resolvers to
	 */
	void configureHandlerExceptionResolvers(List<HandlerExceptionResolver> exceptionResolvers);

	/**
	 * Configure the Spring MVC interceptors to use. Interceptors can be of type {@link HandlerInterceptor} or
	 * {@link WebRequestInterceptor}.  They allow requests to be pre/post processed before/after controller
	 * invocation. Interceptors can be registered to apply to all requests or limited to a set of path patterns.
	 * @see InterceptorConfigurer
	 */
	void configureInterceptors(InterceptorConfigurer configurer);

	/**
	 * Configure the view controllers to use. A view controller is used to map a URL path directly to a view name.
	 * This is convenient when a request does not require controller logic.
	 */
	void configureViewControllers(ViewControllerConfigurer configurer);

	/**
	 * Configure a handler for serving static resources such as images, js, and, css files through Spring MVC
	 * including setting cache headers optimized for efficient loading in a web browser. Resources can be served
	 * out of locations under web application root, from the classpath, and others.
	 */
	void configureResourceHandling(ResourceConfigurer configurer);

	/**
	 * Configure a handler for delegating unhandled requests by forwarding to the Servlet container's default
	 * servlet. This is commonly used when the {@link DispatcherServlet} is mapped to "/", which results in
	 * cleaner URLs (without a servlet prefix) but may need to still allow some requests (e.g. static resources)
	 * to be handled by the Servlet container's default servlet.
	 */
	void configureDefaultServletHandling(DefaultServletHandlerConfigurer configurer);

}
