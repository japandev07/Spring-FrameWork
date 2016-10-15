/*
 * Copyright 2002-2016 the original author or authors.
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

package org.springframework.web.reactive.config;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import org.springframework.context.support.StaticApplicationContext;
import org.springframework.core.convert.ConversionService;
import org.springframework.format.FormatterRegistry;
import org.springframework.http.codec.HttpMessageReader;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.support.ConfigurableWebBindingInitializer;
import org.springframework.web.reactive.accept.RequestedContentTypeResolverBuilder;
import org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerAdapter;
import org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerMapping;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test fixture for {@link DelegatingWebReactiveConfiguration} tests.
 *
 * @author Brian Clozel
 */
public class DelegatingWebReactiveConfigurationTests {

	private DelegatingWebReactiveConfiguration delegatingConfig;

	@Mock
	private WebReactiveConfigurer webReactiveConfigurer;

	@Captor
	private ArgumentCaptor<List<HttpMessageReader<?>>> readers;

	@Captor
	private ArgumentCaptor<List<HttpMessageWriter<?>>> writers;

	@Captor
	private ArgumentCaptor<FormatterRegistry> formatterRegistry;

	@Rule
	public ExpectedException thrown = ExpectedException.none();


	@Before
	public void setUp() {
		MockitoAnnotations.initMocks(this);
		delegatingConfig = new DelegatingWebReactiveConfiguration();
		delegatingConfig.setApplicationContext(new StaticApplicationContext());
		given(webReactiveConfigurer.createRequestMappingHandlerMapping()).willReturn(Optional.empty());
		given(webReactiveConfigurer.createRequestMappingHandlerAdapter()).willReturn(Optional.empty());
		given(webReactiveConfigurer.getValidator()).willReturn(Optional.empty());
		given(webReactiveConfigurer.getMessageCodesResolver()).willReturn(Optional.empty());
	}


	@Test
	public void requestMappingHandlerMapping() throws Exception {
		delegatingConfig.setConfigurers(Collections.singletonList(webReactiveConfigurer));
		delegatingConfig.requestMappingHandlerMapping();

		verify(webReactiveConfigurer).createRequestMappingHandlerMapping();
		verify(webReactiveConfigurer).configureRequestedContentTypeResolver(any(RequestedContentTypeResolverBuilder.class));
		verify(webReactiveConfigurer).addCorsMappings(any(CorsRegistry.class));
		verify(webReactiveConfigurer).configurePathMatching(any(PathMatchConfigurer.class));
	}

	@Test
	public void requestMappingHandlerMappingFactoryMethod() throws Exception {
		RequestMappingHandlerMapping mapping = new RequestMappingHandlerMapping();

		WebReactiveConfigurer configurer1 = mock(WebReactiveConfigurer.class);
		WebReactiveConfigurer configurer2 = mock(WebReactiveConfigurer.class);

		when(configurer1.createRequestMappingHandlerMapping()).thenReturn(Optional.of(mapping));
		when(configurer2.createRequestMappingHandlerMapping()).thenReturn(Optional.empty());

		delegatingConfig.setConfigurers(Arrays.asList(configurer1, configurer2));
		Object actual = delegatingConfig.createRequestMappingHandlerMapping();

		assertSame(mapping, actual);
	}

	@Test
	public void multipleRequestMappingHandlerMappingFactoryMethods() throws Exception {
		RequestMappingHandlerMapping mapping1 = new RequestMappingHandlerMapping();
		RequestMappingHandlerMapping mapping2 = new RequestMappingHandlerMapping();

		WebReactiveConfigurer configurer1 = mock(WebReactiveConfigurer.class);
		WebReactiveConfigurer configurer2 = mock(WebReactiveConfigurer.class);

		when(configurer1.createRequestMappingHandlerMapping()).thenReturn(Optional.of(mapping1));
		when(configurer2.createRequestMappingHandlerMapping()).thenReturn(Optional.of(mapping2));

		this.thrown.expectMessage("More than one WebReactiveConfigurer implements " +
				"RequestMappingHandlerMapping factory method.");

		delegatingConfig.setConfigurers(Arrays.asList(configurer1, configurer2));
		delegatingConfig.createRequestMappingHandlerMapping();
	}

	@Test
	public void requestMappingHandlerAdapter() throws Exception {
		delegatingConfig.setConfigurers(Collections.singletonList(webReactiveConfigurer));
		RequestMappingHandlerAdapter adapter = delegatingConfig.requestMappingHandlerAdapter();

		ConfigurableWebBindingInitializer initializer = (ConfigurableWebBindingInitializer) adapter.getWebBindingInitializer();
		ConversionService initializerConversionService = initializer.getConversionService();
		assertTrue(initializer.getValidator() instanceof LocalValidatorFactoryBean);

		verify(webReactiveConfigurer).createRequestMappingHandlerAdapter();
		verify(webReactiveConfigurer).configureMessageReaders(readers.capture());
		verify(webReactiveConfigurer).extendMessageReaders(readers.capture());
		verify(webReactiveConfigurer).getValidator();
		verify(webReactiveConfigurer).getMessageCodesResolver();
		verify(webReactiveConfigurer).addFormatters(formatterRegistry.capture());
		verify(webReactiveConfigurer).addArgumentResolvers(any());

		assertSame(formatterRegistry.getValue(), initializerConversionService);
		assertEquals(5, readers.getValue().size());
	}

	@Test
	public void resourceHandlerMapping() throws Exception {
		delegatingConfig.setConfigurers(Collections.singletonList(webReactiveConfigurer));
		doAnswer(invocation -> {
			ResourceHandlerRegistry registry = invocation.getArgumentAt(0, ResourceHandlerRegistry.class);
			registry.addResourceHandler("/static/**").addResourceLocations("classpath:/static");
			return null;
		}).when(webReactiveConfigurer).addResourceHandlers(any(ResourceHandlerRegistry.class));

		delegatingConfig.resourceHandlerMapping();
		verify(webReactiveConfigurer).addResourceHandlers(any(ResourceHandlerRegistry.class));
		verify(webReactiveConfigurer).configurePathMatching(any(PathMatchConfigurer.class));
	}

	@Test
	public void responseBodyResultHandler() throws Exception {
		delegatingConfig.setConfigurers(Collections.singletonList(webReactiveConfigurer));
		delegatingConfig.responseBodyResultHandler();

		verify(webReactiveConfigurer).configureMessageWriters(writers.capture());
		verify(webReactiveConfigurer).extendMessageWriters(writers.capture());
		verify(webReactiveConfigurer).configureRequestedContentTypeResolver(any(RequestedContentTypeResolverBuilder.class));
	}

	@Test
	public void viewResolutionResultHandler() throws Exception {
		delegatingConfig.setConfigurers(Collections.singletonList(webReactiveConfigurer));
		delegatingConfig.viewResolutionResultHandler();

		verify(webReactiveConfigurer).configureViewResolvers(any(ViewResolverRegistry.class));
	}

}
