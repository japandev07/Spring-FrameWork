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

package org.springframework.web.reactive.config;

import java.util.Collections;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import org.springframework.context.support.StaticApplicationContext;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.format.FormatterRegistry;
import org.springframework.format.support.FormattingConversionService;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.validation.Validator;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.support.ConfigurableWebBindingInitializer;
import org.springframework.web.reactive.accept.RequestedContentTypeResolverBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.verify;

/**
 * Test fixture for {@link DelegatingWebFluxConfiguration} tests.
 *
 * @author Brian Clozel
 */
public class DelegatingWebFluxConfigurationTests {

	private DelegatingWebFluxConfiguration delegatingConfig;

	@Mock
	private WebFluxConfigurer webFluxConfigurer;

	@Captor
	private ArgumentCaptor<ServerCodecConfigurer> codecsConfigurer;

	@Captor
	private ArgumentCaptor<List<HttpMessageWriter<?>>> writers;

	@Captor
	private ArgumentCaptor<FormatterRegistry> formatterRegistry;


	@Before
	public void setup() {
		MockitoAnnotations.initMocks(this);
		delegatingConfig = new DelegatingWebFluxConfiguration();
		delegatingConfig.setApplicationContext(new StaticApplicationContext());
		given(webFluxConfigurer.getValidator()).willReturn(null);
		given(webFluxConfigurer.getMessageCodesResolver()).willReturn(null);
	}


	@Test
	public void requestMappingHandlerMapping() throws Exception {
		delegatingConfig.setConfigurers(Collections.singletonList(webFluxConfigurer));
		delegatingConfig.requestMappingHandlerMapping(delegatingConfig.webFluxContentTypeResolver());

		verify(webFluxConfigurer).configureContentTypeResolver(any(RequestedContentTypeResolverBuilder.class));
		verify(webFluxConfigurer).addCorsMappings(any(CorsRegistry.class));
		verify(webFluxConfigurer).configurePathMatching(any(PathMatchConfigurer.class));
	}

	@Test
	public void requestMappingHandlerAdapter() throws Exception {
		delegatingConfig.setConfigurers(Collections.singletonList(webFluxConfigurer));
		ReactiveAdapterRegistry reactiveAdapterRegistry = delegatingConfig.webFluxAdapterRegistry();
		ServerCodecConfigurer serverCodecConfigurer = delegatingConfig.serverCodecConfigurer();
		FormattingConversionService formattingConversionService = delegatingConfig.webFluxConversionService();
		Validator validator = delegatingConfig.webFluxValidator();

		ConfigurableWebBindingInitializer initializer = (ConfigurableWebBindingInitializer)
				this.delegatingConfig.requestMappingHandlerAdapter(reactiveAdapterRegistry, serverCodecConfigurer,
						formattingConversionService, validator).getWebBindingInitializer();

		verify(webFluxConfigurer).configureHttpMessageCodecs(codecsConfigurer.capture());
		verify(webFluxConfigurer).getValidator();
		verify(webFluxConfigurer).getMessageCodesResolver();
		verify(webFluxConfigurer).addFormatters(formatterRegistry.capture());
		verify(webFluxConfigurer).configureArgumentResolvers(any());

		assertThat(initializer).isNotNull();
		boolean condition = initializer.getValidator() instanceof LocalValidatorFactoryBean;
		assertThat(condition).isTrue();
		assertThat(initializer.getConversionService()).isSameAs(formatterRegistry.getValue());
		assertThat(codecsConfigurer.getValue().getReaders().size()).isEqualTo(13);
	}

	@Test
	public void resourceHandlerMapping() throws Exception {
		delegatingConfig.setConfigurers(Collections.singletonList(webFluxConfigurer));
		willAnswer(invocation -> {
			ResourceHandlerRegistry registry = invocation.getArgument(0);
			registry.addResourceHandler("/static/**").addResourceLocations("classpath:/static");
			return null;
		}).given(webFluxConfigurer).addResourceHandlers(any(ResourceHandlerRegistry.class));

		delegatingConfig.resourceHandlerMapping(delegatingConfig.resourceUrlProvider());
		verify(webFluxConfigurer).addResourceHandlers(any(ResourceHandlerRegistry.class));
		verify(webFluxConfigurer).configurePathMatching(any(PathMatchConfigurer.class));
	}

	@Test
	public void responseBodyResultHandler() throws Exception {
		delegatingConfig.setConfigurers(Collections.singletonList(webFluxConfigurer));
		delegatingConfig.responseBodyResultHandler(
				delegatingConfig.webFluxAdapterRegistry(),
				delegatingConfig.serverCodecConfigurer(),
				delegatingConfig.webFluxContentTypeResolver());

		verify(webFluxConfigurer).configureHttpMessageCodecs(codecsConfigurer.capture());
		verify(webFluxConfigurer).configureContentTypeResolver(any(RequestedContentTypeResolverBuilder.class));
	}

	@Test
	public void viewResolutionResultHandler() throws Exception {
		delegatingConfig.setConfigurers(Collections.singletonList(webFluxConfigurer));
		delegatingConfig.viewResolutionResultHandler(delegatingConfig.webFluxAdapterRegistry(),
				delegatingConfig.webFluxContentTypeResolver());

		verify(webFluxConfigurer).configureViewResolvers(any(ViewResolverRegistry.class));
	}

}
