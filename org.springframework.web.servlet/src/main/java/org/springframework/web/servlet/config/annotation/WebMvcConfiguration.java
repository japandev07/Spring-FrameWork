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

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.support.FormattingConversionService;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.validation.Validator;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.handler.HandlerExceptionResolverComposite;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

/**
 * Provides default configuration for Spring MVC applications by registering Spring MVC infrastructure components 
 * to be detected by the {@link DispatcherServlet}. This class is imported whenever @{@link EnableWebMvc} is 
 * added to an @{@link Configuration} class.
 * 
 * <p>See the base class {@link WebMvcConfigurationSupport} for a list of registered instances. This class is closed
 * for extension. However, the configuration it provides can be customized by having your @{@link Configuration}
 * class implement {@link WebMvcConfigurer} or more conveniently extend from {@link WebMvcConfigurerAdapter}.
 * 
 * <p>This class will detect your @{@link Configuration} class and any other @{@link Configuration} classes that 
 * implement {@link WebMvcConfigurer} via autowiring and will allow each of them to participate in the process 
 * of configuring Spring MVC through the configuration callbacks defined in {@link WebMvcConfigurer}.
 *
 * @see EnableWebMvc
 * @see WebMvcConfigurer
 *
 * @author Rossen Stoyanchev
 * @since 3.1
 */
@Configuration
class WebMvcConfiguration extends WebMvcConfigurationSupport {

	private final WebMvcConfigurerComposite configurers = new WebMvcConfigurerComposite();

	@Autowired(required = false)
	public void setConfigurers(List<WebMvcConfigurer> configurers) {
		if (configurers == null || configurers.isEmpty()) {
			return;
		}
		this.configurers.addWebMvcConfigurers(configurers);
	}

	@Override
	protected void configureInterceptors(InterceptorConfigurer configurer) {
		configurers.configureInterceptors(configurer);
	}

	@Override
	protected void configureViewControllers(ViewControllerConfigurer configurer) {
		configurers.configureViewControllers(configurer);
	}

	@Override
	protected void configureResourceHandling(ResourceConfigurer configurer) {
		configurers.configureResourceHandling(configurer);
	}

	@Override
	protected void configureDefaultServletHandling(DefaultServletHandlerConfigurer configurer) {
		configurers.configureDefaultServletHandling(configurer);
	}

	@Override
	@Bean
	public RequestMappingHandlerAdapter requestMappingHandlerAdapter() {
		RequestMappingHandlerAdapter adapter = super.requestMappingHandlerAdapter();

		List<HandlerMethodArgumentResolver> argumentResolvers = new ArrayList<HandlerMethodArgumentResolver>();
		configurers.addArgumentResolvers(argumentResolvers);
		adapter.setCustomArgumentResolvers(argumentResolvers);

		List<HandlerMethodReturnValueHandler> returnValueHandlers = new ArrayList<HandlerMethodReturnValueHandler>();
		configurers.addReturnValueHandlers(returnValueHandlers);
		adapter.setCustomReturnValueHandlers(returnValueHandlers);
		
		return adapter;
	}

	@Override
	protected void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
		configurers.configureMessageConverters(converters);
	}

	@Override
	@Bean
	public FormattingConversionService mvcConversionService() {
		FormattingConversionService conversionService = super.mvcConversionService();
		configurers.addFormatters(conversionService);
		return conversionService;
	}

	@Override
	@Bean
	public Validator mvcValidator() {
		Validator validator = configurers.getValidator();
		return validator != null ? validator : super.mvcValidator();
	}

	@Bean
	public HandlerExceptionResolverComposite handlerExceptionResolver() throws Exception {
		List<HandlerExceptionResolver> resolvers = new ArrayList<HandlerExceptionResolver>();
		configurers.configureHandlerExceptionResolvers(resolvers);

		HandlerExceptionResolverComposite composite = super.handlerExceptionResolver();
		if (resolvers.size() != 0) {
			composite.setExceptionResolvers(resolvers);
		}
		return composite;
	}
	
}
