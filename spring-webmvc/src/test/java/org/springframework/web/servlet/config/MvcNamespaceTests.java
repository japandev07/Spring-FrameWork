/*
 * Copyright 2002-2014 the original author or authors.
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

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Method;
import java.util.*;

import javax.servlet.RequestDispatcher;
import javax.validation.constraints.NotNull;

import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.DirectFieldAccessor;
import org.springframework.beans.TypeMismatchException;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.Ordered;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.format.support.FormattingConversionServiceFactoryBean;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.mock.web.test.MockHttpServletRequest;
import org.springframework.mock.web.test.MockHttpServletResponse;
import org.springframework.mock.web.test.MockRequestDispatcher;
import org.springframework.mock.web.test.MockServletContext;
import org.springframework.scheduling.concurrent.ConcurrentTaskExecutor;
import org.springframework.stereotype.Controller;
import org.springframework.util.PathMatcher;
import org.springframework.validation.BindingResult;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;
import org.springframework.validation.annotation.Validated;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.accept.ContentNegotiationManager;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.async.CallableProcessingInterceptor;
import org.springframework.web.context.request.async.CallableProcessingInterceptorAdapter;
import org.springframework.web.context.request.async.DeferredResultProcessingInterceptor;
import org.springframework.web.context.request.async.DeferredResultProcessingInterceptorAdapter;
import org.springframework.web.context.support.GenericWebApplicationContext;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.support.CompositeUriComponentsContributor;
import org.springframework.web.method.support.InvocableHandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.handler.*;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;
import org.springframework.web.servlet.mvc.HttpRequestHandlerAdapter;
import org.springframework.web.servlet.mvc.SimpleControllerHandlerAdapter;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.MvcUriComponentsBuilder;
import org.springframework.web.servlet.resource.DefaultServletHttpRequestHandler;
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler;
import org.springframework.web.servlet.theme.ThemeChangeInterceptor;
import org.springframework.web.util.UrlPathHelper;

import static org.junit.Assert.*;

/**
 * @author Keith Donald
 * @author Arjen Poutsma
 * @author Jeremy Grelle
 */
public class MvcNamespaceTests {

	public static final String VIEWCONTROLLER_BEAN_NAME = "org.springframework.web.servlet.config.viewControllerHandlerMapping";
	
	private GenericWebApplicationContext appContext;

	private TestController handler;

	private HandlerMethod handlerMethod;

	@Before
	public void setUp() throws Exception {
		appContext = new GenericWebApplicationContext();
		appContext.setServletContext(new TestMockServletContext());
		LocaleContextHolder.setLocale(Locale.US);

		handler = new TestController();
		Method method = TestController.class.getMethod("testBind", Date.class, TestBean.class, BindingResult.class);
		handlerMethod = new InvocableHandlerMethod(handler, method);
	}

	@Test
	public void testDefaultConfig() throws Exception {
		loadBeanDefinitions("mvc-config.xml", 13);

		RequestMappingHandlerMapping mapping = appContext.getBean(RequestMappingHandlerMapping.class);
		assertNotNull(mapping);
		assertEquals(0, mapping.getOrder());
		assertTrue(mapping.getUrlPathHelper().shouldRemoveSemicolonContent());
		mapping.setDefaultHandler(handlerMethod);

		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/foo.json");
		NativeWebRequest webRequest = new ServletWebRequest(request);
		ContentNegotiationManager manager = mapping.getContentNegotiationManager();
		assertEquals(Arrays.asList(MediaType.APPLICATION_JSON), manager.resolveMediaTypes(webRequest));

		RequestMappingHandlerAdapter adapter = appContext.getBean(RequestMappingHandlerAdapter.class);
		assertNotNull(adapter);
		assertEquals(false, new DirectFieldAccessor(adapter).getPropertyValue("ignoreDefaultModelOnRedirect"));

		List<HttpMessageConverter<?>> messageConverters = adapter.getMessageConverters();
		assertTrue(messageConverters.size() > 0);

		assertNotNull(appContext.getBean(FormattingConversionServiceFactoryBean.class));
		assertNotNull(appContext.getBean(ConversionService.class));
		assertNotNull(appContext.getBean(LocalValidatorFactoryBean.class));
		assertNotNull(appContext.getBean(Validator.class));

		// default web binding initializer behavior test
		request = new MockHttpServletRequest("GET", "/");
		request.addParameter("date", "2009-10-31");
		MockHttpServletResponse response = new MockHttpServletResponse();

		HandlerExecutionChain chain = mapping.getHandler(request);
		assertEquals(1, chain.getInterceptors().length);
		assertTrue(chain.getInterceptors()[0] instanceof ConversionServiceExposingInterceptor);
		ConversionServiceExposingInterceptor interceptor = (ConversionServiceExposingInterceptor) chain.getInterceptors()[0];
		interceptor.preHandle(request, response, handlerMethod);
		assertSame(appContext.getBean(ConversionService.class), request.getAttribute(ConversionService.class.getName()));

		adapter.handle(request, response, handlerMethod);
		assertTrue(handler.recordedValidationError);

		CompositeUriComponentsContributor uriComponentsContributor = this.appContext.getBean(
				MvcUriComponentsBuilder.MVC_URI_COMPONENTS_CONTRIBUTOR_BEAN_NAME,
				CompositeUriComponentsContributor.class);

		assertNotNull(uriComponentsContributor);
	}

	@Test(expected=TypeMismatchException.class)
	public void testCustomConversionService() throws Exception {
		loadBeanDefinitions("mvc-config-custom-conversion-service.xml", 13);

		RequestMappingHandlerMapping mapping = appContext.getBean(RequestMappingHandlerMapping.class);
		assertNotNull(mapping);
		mapping.setDefaultHandler(handlerMethod);

		// default web binding initializer behavior test
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
		request.setRequestURI("/accounts/12345");
		request.addParameter("date", "2009-10-31");
		MockHttpServletResponse response = new MockHttpServletResponse();

		HandlerExecutionChain chain = mapping.getHandler(request);
		assertEquals(1, chain.getInterceptors().length);
		assertTrue(chain.getInterceptors()[0] instanceof ConversionServiceExposingInterceptor);
		ConversionServiceExposingInterceptor interceptor = (ConversionServiceExposingInterceptor) chain.getInterceptors()[0];
		interceptor.preHandle(request, response, handler);
		assertSame(appContext.getBean("conversionService"), request.getAttribute(ConversionService.class.getName()));

		RequestMappingHandlerAdapter adapter = appContext.getBean(RequestMappingHandlerAdapter.class);
		assertNotNull(adapter);
		adapter.handle(request, response, handlerMethod);
	}

	@Test
	public void testCustomValidator() throws Exception {
		doTestCustomValidator("mvc-config-custom-validator.xml");
	}

	@Test
	public void testCustomValidator32() throws Exception {
		doTestCustomValidator("mvc-config-custom-validator-32.xml");
	}

	/**
	 * @throws Exception
	 */
	private void doTestCustomValidator(String xml) throws Exception {
		loadBeanDefinitions(xml, 13);

		RequestMappingHandlerMapping mapping = appContext.getBean(RequestMappingHandlerMapping.class);
		assertNotNull(mapping);
		assertFalse(mapping.getUrlPathHelper().shouldRemoveSemicolonContent());

		RequestMappingHandlerAdapter adapter = appContext.getBean(RequestMappingHandlerAdapter.class);
		assertNotNull(adapter);
		assertEquals(true, new DirectFieldAccessor(adapter).getPropertyValue("ignoreDefaultModelOnRedirect"));

		// default web binding initializer behavior test
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addParameter("date", "2009-10-31");
		MockHttpServletResponse response = new MockHttpServletResponse();
		adapter.handle(request, response, handlerMethod);

		assertTrue(appContext.getBean(TestValidator.class).validatorInvoked);
		assertFalse(handler.recordedValidationError);
	}

	@Test
	public void testInterceptors() throws Exception {
		loadBeanDefinitions("mvc-config-interceptors.xml", 20);

		RequestMappingHandlerMapping mapping = appContext.getBean(RequestMappingHandlerMapping.class);
		assertNotNull(mapping);
		mapping.setDefaultHandler(handlerMethod);

		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
		request.setRequestURI("/accounts/12345");
		request.addParameter("locale", "en");
		request.addParameter("theme", "green");

		HandlerExecutionChain chain = mapping.getHandler(request);
		assertEquals(5, chain.getInterceptors().length);
		assertTrue(chain.getInterceptors()[0] instanceof ConversionServiceExposingInterceptor);
		assertTrue(chain.getInterceptors()[1] instanceof LocaleChangeInterceptor);
		assertTrue(chain.getInterceptors()[2] instanceof WebRequestHandlerInterceptorAdapter);
		assertTrue(chain.getInterceptors()[3] instanceof ThemeChangeInterceptor);
		assertTrue(chain.getInterceptors()[4] instanceof UserRoleAuthorizationInterceptor);

		request.setRequestURI("/admin/users");
		chain = mapping.getHandler(request);
		assertEquals(3, chain.getInterceptors().length);

		request.setRequestURI("/logged/accounts/12345");
		chain = mapping.getHandler(request);
		assertEquals(5, chain.getInterceptors().length);
		assertTrue(chain.getInterceptors()[4] instanceof WebRequestHandlerInterceptorAdapter);

		request.setRequestURI("/foo/logged");
		chain = mapping.getHandler(request);
		assertEquals(5, chain.getInterceptors().length);
		assertTrue(chain.getInterceptors()[4] instanceof WebRequestHandlerInterceptorAdapter);
	}

	@Test
	public void testResources() throws Exception {
		loadBeanDefinitions("mvc-config-resources.xml", 7);

		HttpRequestHandlerAdapter adapter = appContext.getBean(HttpRequestHandlerAdapter.class);
		assertNotNull(adapter);

		ResourceHttpRequestHandler handler = appContext.getBean(ResourceHttpRequestHandler.class);
		assertNotNull(handler);

		SimpleUrlHandlerMapping mapping = appContext.getBean(SimpleUrlHandlerMapping.class);
		assertNotNull(mapping);
		assertEquals(Ordered.LOWEST_PRECEDENCE - 1, mapping.getOrder());

		BeanNameUrlHandlerMapping beanNameMapping = appContext.getBean(BeanNameUrlHandlerMapping.class);
		assertNotNull(beanNameMapping);
		assertEquals(2, beanNameMapping.getOrder());

		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRequestURI("/resources/foo.css");
		request.setMethod("GET");

		HandlerExecutionChain chain = mapping.getHandler(request);
		assertTrue(chain.getHandler() instanceof ResourceHttpRequestHandler);

		MockHttpServletResponse response = new MockHttpServletResponse();
		for (HandlerInterceptor interceptor : chain.getInterceptors()) {
			interceptor.preHandle(request, response, chain.getHandler());
		}
		ModelAndView mv = adapter.handle(request, response, chain.getHandler());
		assertNull(mv);
	}

	@Test
	public void testResourcesWithOptionalAttributes() throws Exception {
		loadBeanDefinitions("mvc-config-resources-optional-attrs.xml", 7);

		SimpleUrlHandlerMapping mapping = appContext.getBean(SimpleUrlHandlerMapping.class);
		assertNotNull(mapping);
		assertEquals(5, mapping.getOrder());
	}

	@Test
	public void testDefaultServletHandler() throws Exception {
		loadBeanDefinitions("mvc-config-default-servlet.xml", 5);

		HttpRequestHandlerAdapter adapter = appContext.getBean(HttpRequestHandlerAdapter.class);
		assertNotNull(adapter);

		DefaultServletHttpRequestHandler handler = appContext.getBean(DefaultServletHttpRequestHandler.class);
		assertNotNull(handler);

		SimpleUrlHandlerMapping mapping = appContext.getBean(SimpleUrlHandlerMapping.class);
		assertNotNull(mapping);
		assertEquals(Ordered.LOWEST_PRECEDENCE, mapping.getOrder());

		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRequestURI("/foo.css");
		request.setMethod("GET");

		HandlerExecutionChain chain = mapping.getHandler(request);
		assertTrue(chain.getHandler() instanceof DefaultServletHttpRequestHandler);

		MockHttpServletResponse response = new MockHttpServletResponse();
		ModelAndView mv = adapter.handle(request, response, chain.getHandler());
		assertNull(mv);
	}

	@Test
	public void testDefaultServletHandlerWithOptionalAttributes() throws Exception {
		loadBeanDefinitions("mvc-config-default-servlet-optional-attrs.xml", 5);

		HttpRequestHandlerAdapter adapter = appContext.getBean(HttpRequestHandlerAdapter.class);
		assertNotNull(adapter);

		DefaultServletHttpRequestHandler handler = appContext.getBean(DefaultServletHttpRequestHandler.class);
		assertNotNull(handler);

		SimpleUrlHandlerMapping mapping = appContext.getBean(SimpleUrlHandlerMapping.class);
		assertNotNull(mapping);
		assertEquals(Ordered.LOWEST_PRECEDENCE, mapping.getOrder());

		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRequestURI("/foo.css");
		request.setMethod("GET");

		HandlerExecutionChain chain = mapping.getHandler(request);
		assertTrue(chain.getHandler() instanceof DefaultServletHttpRequestHandler);

		MockHttpServletResponse response = new MockHttpServletResponse();
		ModelAndView mv = adapter.handle(request, response, chain.getHandler());
		assertNull(mv);
	}

	@Test
	public void testBeanDecoration() throws Exception {
		loadBeanDefinitions("mvc-config-bean-decoration.xml", 15);

		RequestMappingHandlerMapping mapping = appContext.getBean(RequestMappingHandlerMapping.class);
		assertNotNull(mapping);
		mapping.setDefaultHandler(handlerMethod);

		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");

		HandlerExecutionChain chain = mapping.getHandler(request);
		assertEquals(3, chain.getInterceptors().length);
		assertTrue(chain.getInterceptors()[0] instanceof ConversionServiceExposingInterceptor);
		assertTrue(chain.getInterceptors()[1] instanceof LocaleChangeInterceptor);
		assertTrue(chain.getInterceptors()[2] instanceof ThemeChangeInterceptor);
		LocaleChangeInterceptor interceptor = (LocaleChangeInterceptor) chain.getInterceptors()[1];
		assertEquals("lang", interceptor.getParamName());
		ThemeChangeInterceptor interceptor2 = (ThemeChangeInterceptor) chain.getInterceptors()[2];
		assertEquals("style", interceptor2.getParamName());
	}

	@Test
	public void testViewControllers() throws Exception {
		loadBeanDefinitions("mvc-config-view-controllers.xml", 18);

		RequestMappingHandlerMapping mapping = appContext.getBean(RequestMappingHandlerMapping.class);
		assertNotNull(mapping);
		mapping.setDefaultHandler(handlerMethod);

		BeanNameUrlHandlerMapping beanNameMapping = appContext.getBean(BeanNameUrlHandlerMapping.class);
		assertNotNull(beanNameMapping);
		assertEquals(2, beanNameMapping.getOrder());

		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setMethod("GET");

		HandlerExecutionChain chain = mapping.getHandler(request);
		assertEquals(3, chain.getInterceptors().length);
		assertTrue(chain.getInterceptors()[0] instanceof ConversionServiceExposingInterceptor);
		assertTrue(chain.getInterceptors()[1] instanceof LocaleChangeInterceptor);
		assertTrue(chain.getInterceptors()[2] instanceof ThemeChangeInterceptor);

		SimpleUrlHandlerMapping mapping2 = appContext.getBean(SimpleUrlHandlerMapping.class);
		assertNotNull(mapping2);

		SimpleControllerHandlerAdapter adapter = appContext.getBean(SimpleControllerHandlerAdapter.class);
		assertNotNull(adapter);

		request.setRequestURI("/foo");
		chain = mapping2.getHandler(request);
		assertEquals(4, chain.getInterceptors().length);
		assertTrue(chain.getInterceptors()[1] instanceof ConversionServiceExposingInterceptor);
		assertTrue(chain.getInterceptors()[2] instanceof LocaleChangeInterceptor);
		assertTrue(chain.getInterceptors()[3] instanceof ThemeChangeInterceptor);
		ModelAndView mv = adapter.handle(request, new MockHttpServletResponse(), chain.getHandler());
		assertNull(mv.getViewName());

		request.setRequestURI("/myapp/app/bar");
		request.setContextPath("/myapp");
		request.setServletPath("/app");
		chain = mapping2.getHandler(request);
		assertEquals(4, chain.getInterceptors().length);
		assertTrue(chain.getInterceptors()[1] instanceof ConversionServiceExposingInterceptor);
		assertTrue(chain.getInterceptors()[2] instanceof LocaleChangeInterceptor);
		assertTrue(chain.getInterceptors()[3] instanceof ThemeChangeInterceptor);
		ModelAndView mv2 = adapter.handle(request, new MockHttpServletResponse(), chain.getHandler());
		assertEquals("baz", mv2.getViewName());

		request.setRequestURI("/myapp/app/");
		request.setContextPath("/myapp");
		request.setServletPath("/app");
		chain = mapping2.getHandler(request);
		assertEquals(4, chain.getInterceptors().length);
		assertTrue(chain.getInterceptors()[1] instanceof ConversionServiceExposingInterceptor);
		assertTrue(chain.getInterceptors()[2] instanceof LocaleChangeInterceptor);
		assertTrue(chain.getInterceptors()[3] instanceof ThemeChangeInterceptor);
		ModelAndView mv3 = adapter.handle(request, new MockHttpServletResponse(), chain.getHandler());
		assertEquals("root", mv3.getViewName());
	}

	/** WebSphere gives trailing servlet path slashes by default!! */
	@Test
	public void testViewControllersOnWebSphere() throws Exception {
		loadBeanDefinitions("mvc-config-view-controllers.xml", 18);

		SimpleUrlHandlerMapping mapping2 = appContext.getBean(SimpleUrlHandlerMapping.class);
		SimpleControllerHandlerAdapter adapter = appContext.getBean(SimpleControllerHandlerAdapter.class);

		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setMethod("GET");
		request.setRequestURI("/myapp/app/bar");
		request.setContextPath("/myapp");
		request.setServletPath("/app/");
		request.setAttribute("com.ibm.websphere.servlet.uri_non_decoded", "/myapp/app/bar");
		HandlerExecutionChain chain = mapping2.getHandler(request);
		assertEquals(4, chain.getInterceptors().length);
		assertTrue(chain.getInterceptors()[1] instanceof ConversionServiceExposingInterceptor);
		assertTrue(chain.getInterceptors()[2] instanceof LocaleChangeInterceptor);
		assertTrue(chain.getInterceptors()[3] instanceof ThemeChangeInterceptor);
		ModelAndView mv2 = adapter.handle(request, new MockHttpServletResponse(), chain.getHandler());
		assertEquals("baz", mv2.getViewName());

		request.setRequestURI("/myapp/app/");
		request.setContextPath("/myapp");
		request.setServletPath("/app/");
		chain = mapping2.getHandler(request);
		assertEquals(4, chain.getInterceptors().length);
		assertTrue(chain.getInterceptors()[1] instanceof ConversionServiceExposingInterceptor);
		assertTrue(chain.getInterceptors()[2] instanceof LocaleChangeInterceptor);
		assertTrue(chain.getInterceptors()[3] instanceof ThemeChangeInterceptor);
		ModelAndView mv3 = adapter.handle(request, new MockHttpServletResponse(), chain.getHandler());
		assertEquals("root", mv3.getViewName());

		request.setRequestURI("/myapp/");
		request.setContextPath("/myapp");
		request.setServletPath("/");
		chain = mapping2.getHandler(request);
		assertEquals(4, chain.getInterceptors().length);
		assertTrue(chain.getInterceptors()[1] instanceof ConversionServiceExposingInterceptor);
		assertTrue(chain.getInterceptors()[2] instanceof LocaleChangeInterceptor);
		assertTrue(chain.getInterceptors()[3] instanceof ThemeChangeInterceptor);
		mv3 = adapter.handle(request, new MockHttpServletResponse(), chain.getHandler());
		assertEquals("root", mv3.getViewName());
	}

	@Test
	public void testViewControllersDefaultConfig() {
		loadBeanDefinitions("mvc-config-view-controllers-minimal.xml", 6);

		BeanNameUrlHandlerMapping beanNameMapping = appContext.getBean(BeanNameUrlHandlerMapping.class);
		assertNotNull(beanNameMapping);
		assertEquals(2, beanNameMapping.getOrder());
	}

	@Test
	public void testContentNegotiationManager() throws Exception {
		loadBeanDefinitions("mvc-config-content-negotiation-manager.xml", 13);

		RequestMappingHandlerMapping mapping = appContext.getBean(RequestMappingHandlerMapping.class);
		ContentNegotiationManager manager = mapping.getContentNegotiationManager();

		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/foo.xml");
		NativeWebRequest webRequest = new ServletWebRequest(request);
		assertEquals(Arrays.asList(MediaType.valueOf("application/rss+xml")), manager.resolveMediaTypes(webRequest));
	}

	@Test
	public void testAsyncSupportOptions() throws Exception {
		loadBeanDefinitions("mvc-config-async-support.xml", 14);

		RequestMappingHandlerAdapter adapter = appContext.getBean(RequestMappingHandlerAdapter.class);
		assertNotNull(adapter);

		DirectFieldAccessor fieldAccessor = new DirectFieldAccessor(adapter);
		assertEquals(ConcurrentTaskExecutor.class, fieldAccessor.getPropertyValue("taskExecutor").getClass());
		assertEquals(2500L, fieldAccessor.getPropertyValue("asyncRequestTimeout"));

		CallableProcessingInterceptor[] callableInterceptors =
				(CallableProcessingInterceptor[]) fieldAccessor.getPropertyValue("callableInterceptors");
		assertEquals(1, callableInterceptors.length);

		DeferredResultProcessingInterceptor[] deferredResultInterceptors =
				(DeferredResultProcessingInterceptor[]) fieldAccessor.getPropertyValue("deferredResultInterceptors");
		assertEquals(1, deferredResultInterceptors.length);
	}

	@Test
	public void testPathMatchingHandlerMappings() throws Exception {
		loadBeanDefinitions("mvc-config-path-matching-mappings.xml", 20);

		RequestMappingHandlerMapping requestMapping = appContext.getBean(RequestMappingHandlerMapping.class);
		assertNotNull(requestMapping);
		assertEquals(TestPathHelper.class, requestMapping.getUrlPathHelper().getClass());
		assertEquals(TestPathMatcher.class, requestMapping.getPathMatcher().getClass());

		SimpleUrlHandlerMapping viewController = appContext.getBean(VIEWCONTROLLER_BEAN_NAME, SimpleUrlHandlerMapping.class);
		assertNotNull(viewController);
		assertEquals(TestPathHelper.class, viewController.getUrlPathHelper().getClass());
		assertEquals(TestPathMatcher.class, viewController.getPathMatcher().getClass());

		for(SimpleUrlHandlerMapping handlerMapping : appContext.getBeansOfType(SimpleUrlHandlerMapping.class).values()) {
			assertNotNull(handlerMapping);
			assertEquals(TestPathHelper.class, handlerMapping.getUrlPathHelper().getClass());
			assertEquals(TestPathMatcher.class, handlerMapping.getPathMatcher().getClass());
		}

	}


	private void loadBeanDefinitions(String fileName, int expectedBeanCount) {
		XmlBeanDefinitionReader reader = new XmlBeanDefinitionReader(appContext);
		ClassPathResource resource = new ClassPathResource(fileName, AnnotationDrivenBeanDefinitionParserTests.class);
		reader.loadBeanDefinitions(resource);
		assertEquals(expectedBeanCount, appContext.getBeanDefinitionCount());
		appContext.refresh();
	}


	@Controller
	public static class TestController {

		private boolean recordedValidationError;

		@RequestMapping
		public void testBind(@RequestParam @DateTimeFormat(iso=ISO.DATE) Date date, @Validated(MyGroup.class) TestBean bean, BindingResult result) {
			this.recordedValidationError = (result.getErrorCount() == 1);
		}
	}

	public static class TestValidator implements Validator {

		boolean validatorInvoked;

		@Override
		public boolean supports(Class<?> clazz) {
			return true;
		}

		@Override
		public void validate(Object target, Errors errors) {
			this.validatorInvoked = true;
		}
	}

	@Retention(RetentionPolicy.RUNTIME)
	public @interface MyGroup {
	}

	private static class TestBean {

		@NotNull(groups=MyGroup.class)
		private String field;

		@SuppressWarnings("unused")
		public String getField() {
			return field;
		}

		@SuppressWarnings("unused")
		public void setField(String field) {
			this.field = field;
		}
	}

	private static class TestMockServletContext extends MockServletContext {

		@Override
		public RequestDispatcher getNamedDispatcher(String path) {
			if (path.equals("default") || path.equals("custom")) {
				return new MockRequestDispatcher("/");
			} else {
				return null;
			}
		}
	}

	public static class TestCallableProcessingInterceptor extends CallableProcessingInterceptorAdapter { }

	public static class TestDeferredResultProcessingInterceptor extends DeferredResultProcessingInterceptorAdapter { }

	public static class TestPathMatcher implements PathMatcher {

		@Override
		public boolean isPattern(String path) {
			return false;
		}

		@Override
		public boolean match(String pattern, String path) {
			return path.matches(pattern);
		}

		@Override
		public boolean matchStart(String pattern, String path) {
			return false;
		}

		@Override
		public String extractPathWithinPattern(String pattern, String path) {
			return null;
		}

		@Override
		public Map<String, String> extractUriTemplateVariables(String pattern, String path) {
			return null;
		}

		@Override
		public Comparator<String> getPatternComparator(String path) {
			return null;
		}

		@Override
		public String combine(String pattern1, String pattern2) {
			return null;
		}
	}

	public static class TestPathHelper extends UrlPathHelper { }


}
