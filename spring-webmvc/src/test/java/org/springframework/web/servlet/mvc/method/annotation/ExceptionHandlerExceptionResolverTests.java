/*
 * Copyright 2002-2015 the original author or authors.
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

package org.springframework.web.servlet.mvc.method.annotation;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.util.Arrays;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.mock.web.test.MockHttpServletRequest;
import org.springframework.mock.web.test.MockHttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.annotation.ModelMethodProcessor;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.servlet.ModelAndView;

import static org.junit.Assert.*;

/**
 * Test fixture with {@link ExceptionHandlerExceptionResolver}.
 *
 * @author Rossen Stoyanchev
 * @author Arjen Poutsma
 * @since 3.1
 */
public class ExceptionHandlerExceptionResolverTests {

	private static int RESOLVER_COUNT;

	private static int HANDLER_COUNT;

	private ExceptionHandlerExceptionResolver resolver;

	private MockHttpServletRequest request;

	private MockHttpServletResponse response;

	@BeforeClass
	public static void setupOnce() {
		ExceptionHandlerExceptionResolver r = new ExceptionHandlerExceptionResolver();
		r.afterPropertiesSet();
		RESOLVER_COUNT = r.getArgumentResolvers().getResolvers().size();
		HANDLER_COUNT = r.getReturnValueHandlers().getHandlers().size();
	}

	@Before
	public void setUp() throws Exception {
		this.resolver = new ExceptionHandlerExceptionResolver();
		this.request = new MockHttpServletRequest("GET", "/");
		this.response = new MockHttpServletResponse();
	}

	@Test
	public void nullHandler() {
		Object handler = null;
		this.resolver.afterPropertiesSet();
		ModelAndView mav = this.resolver.resolveException(this.request, this.response, handler, null);
		assertNull("Exception can be resolved only if there is a HandlerMethod", mav);
	}

	@Test
	public void setCustomArgumentResolvers() throws Exception {
		HandlerMethodArgumentResolver resolver = new ServletRequestMethodArgumentResolver();
		this.resolver.setCustomArgumentResolvers(Arrays.asList(resolver));
		this.resolver.afterPropertiesSet();

		assertTrue(this.resolver.getArgumentResolvers().getResolvers().contains(resolver));
		assertMethodProcessorCount(RESOLVER_COUNT + 1, HANDLER_COUNT);
	}

	@Test
	public void setArgumentResolvers() throws Exception {
		HandlerMethodArgumentResolver resolver = new ServletRequestMethodArgumentResolver();
		this.resolver.setArgumentResolvers(Arrays.asList(resolver));
		this.resolver.afterPropertiesSet();

		assertMethodProcessorCount(1, HANDLER_COUNT);
	}

	@Test
	public void setCustomReturnValueHandlers() {
		HandlerMethodReturnValueHandler handler = new ViewNameMethodReturnValueHandler();
		this.resolver.setCustomReturnValueHandlers(Arrays.asList(handler));
		this.resolver.afterPropertiesSet();

		assertTrue(this.resolver.getReturnValueHandlers().getHandlers().contains(handler));
		assertMethodProcessorCount(RESOLVER_COUNT, HANDLER_COUNT + 1);
	}

	@Test
	public void setReturnValueHandlers() {
		HandlerMethodReturnValueHandler handler = new ModelMethodProcessor();
		this.resolver.setReturnValueHandlers(Arrays.asList(handler));
		this.resolver.afterPropertiesSet();

		assertMethodProcessorCount(RESOLVER_COUNT, 1);
	}

	@Test
	public void resolveNoExceptionHandlerForException() throws NoSuchMethodException {
		Exception npe = new NullPointerException();
		HandlerMethod handlerMethod = new HandlerMethod(new IoExceptionController(), "handle");
		this.resolver.afterPropertiesSet();
		ModelAndView mav = this.resolver.resolveException(this.request, this.response, handlerMethod, npe);

		assertNull("NPE should not have been handled", mav);
	}

	@Test
	public void resolveExceptionModelAndView() throws NoSuchMethodException {
		IllegalArgumentException ex = new IllegalArgumentException("Bad argument");
		HandlerMethod handlerMethod = new HandlerMethod(new ModelAndViewController(), "handle");
		this.resolver.afterPropertiesSet();
		ModelAndView mav = this.resolver.resolveException(this.request, this.response, handlerMethod, ex);

		assertNotNull(mav);
		assertFalse(mav.isEmpty());
		assertEquals("errorView", mav.getViewName());
		assertEquals("Bad argument", mav.getModel().get("detail"));
	}

	@Test
	public void resolveExceptionResponseBody() throws UnsupportedEncodingException, NoSuchMethodException {
		IllegalArgumentException ex = new IllegalArgumentException();
		HandlerMethod handlerMethod = new HandlerMethod(new ResponseBodyController(), "handle");
		this.resolver.afterPropertiesSet();
		ModelAndView mav = this.resolver.resolveException(this.request, this.response, handlerMethod, ex);

		assertNotNull(mav);
		assertTrue(mav.isEmpty());
		assertEquals("IllegalArgumentException", this.response.getContentAsString());
	}

	@Test
	public void resolveExceptionResponseWriter() throws Exception {
		IllegalArgumentException ex = new IllegalArgumentException();
		HandlerMethod handlerMethod = new HandlerMethod(new ResponseWriterController(), "handle");
		this.resolver.afterPropertiesSet();
		ModelAndView mav = this.resolver.resolveException(this.request, this.response, handlerMethod, ex);

		assertNotNull(mav);
		assertTrue(mav.isEmpty());
		assertEquals("IllegalArgumentException", this.response.getContentAsString());
	}

	@Test
	public void resolveExceptionGlobalHandler() throws Exception {
		AnnotationConfigApplicationContext cxt = new AnnotationConfigApplicationContext(MyConfig.class);
		this.resolver.setApplicationContext(cxt);
		this.resolver.afterPropertiesSet();

		IllegalAccessException ex = new IllegalAccessException();
		HandlerMethod handlerMethod = new HandlerMethod(new ResponseBodyController(), "handle");
		ModelAndView mav = this.resolver.resolveException(this.request, this.response, handlerMethod, ex);

		assertNotNull("Exception was not handled", mav);
		assertTrue(mav.isEmpty());
		assertEquals("AnotherTestExceptionResolver: IllegalAccessException", this.response.getContentAsString());
	}

	@Test
	public void resolveExceptionGlobalHandlerOrdered() throws Exception {
		AnnotationConfigApplicationContext cxt = new AnnotationConfigApplicationContext(MyConfig.class);
		this.resolver.setApplicationContext(cxt);
		this.resolver.afterPropertiesSet();

		IllegalStateException ex = new IllegalStateException();
		HandlerMethod handlerMethod = new HandlerMethod(new ResponseBodyController(), "handle");
		ModelAndView mav = this.resolver.resolveException(this.request, this.response, handlerMethod, ex);

		assertNotNull("Exception was not handled", mav);
		assertTrue(mav.isEmpty());
		assertEquals("TestExceptionResolver: IllegalStateException", this.response.getContentAsString());
	}

	// SPR-12605

	@Test
	public void resolveExceptionWithHandlerMethodArg() throws Exception {
		AnnotationConfigApplicationContext cxt = new AnnotationConfigApplicationContext(MyConfig.class);
		this.resolver.setApplicationContext(cxt);
		this.resolver.afterPropertiesSet();

		ArrayIndexOutOfBoundsException ex = new ArrayIndexOutOfBoundsException();
		HandlerMethod handlerMethod = new HandlerMethod(new ResponseBodyController(), "handle");
		ModelAndView mav = this.resolver.resolveException(this.request, this.response, handlerMethod, ex);

		assertNotNull("Exception was not handled", mav);
		assertTrue(mav.isEmpty());
		assertEquals("HandlerMethod: handle", this.response.getContentAsString());
	}

	@Test
	public void resolveExceptionControllerAdviceHandler() throws Exception {
		AnnotationConfigApplicationContext cxt = new AnnotationConfigApplicationContext(MyControllerAdviceConfig.class);
		this.resolver.setApplicationContext(cxt);
		this.resolver.afterPropertiesSet();

		IllegalStateException ex = new IllegalStateException();
		HandlerMethod handlerMethod = new HandlerMethod(new ResponseBodyController(), "handle");
		ModelAndView mav = this.resolver.resolveException(this.request, this.response, handlerMethod, ex);

		assertNotNull("Exception was not handled", mav);
		assertTrue(mav.isEmpty());
		assertEquals("BasePackageTestExceptionResolver: IllegalStateException", this.response.getContentAsString());
	}

	@Test
	public void resolveExceptionControllerAdviceNoHandler() throws Exception {
		AnnotationConfigApplicationContext cxt = new AnnotationConfigApplicationContext(MyControllerAdviceConfig.class);
		this.resolver.setApplicationContext(cxt);
		this.resolver.afterPropertiesSet();

		IllegalStateException ex = new IllegalStateException();
		ModelAndView mav = this.resolver.resolveException(this.request, this.response, null, ex);

		assertNotNull("Exception was not handled", mav);
		assertTrue(mav.isEmpty());
		assertEquals("DefaultTestExceptionResolver: IllegalStateException", this.response.getContentAsString());
	}


	private void assertMethodProcessorCount(int resolverCount, int handlerCount) {
		assertEquals(resolverCount, this.resolver.getArgumentResolvers().getResolvers().size());
		assertEquals(handlerCount, this.resolver.getReturnValueHandlers().getHandlers().size());
	}

	@Controller
	static class ModelAndViewController {

		public void handle() {}

		@ExceptionHandler
		public ModelAndView handle(Exception ex) throws IOException {
			return new ModelAndView("errorView", "detail", ex.getMessage());
		}
	}

	@Controller
	static class ResponseWriterController {

		public void handle() {}

		@ExceptionHandler
		public void handleException(Exception ex, Writer writer) throws IOException {
			writer.write(ClassUtils.getShortName(ex.getClass()));
		}
	}

	@Controller
	static class ResponseBodyController {

		public void handle() {}

		@ExceptionHandler
		@ResponseBody
		public String handleException(IllegalArgumentException ex) {
			return ClassUtils.getShortName(ex.getClass());
		}
	}

	@Controller
	static class IoExceptionController {

		public void handle() {}

		@ExceptionHandler(value=IOException.class)
		public void handleException() {
		}
	}

	@ControllerAdvice
	@Order(1)
	static class TestExceptionResolver {

		@ExceptionHandler
		@ResponseBody
		public String handleException(IllegalStateException ex) {
			return "TestExceptionResolver: " + ClassUtils.getShortName(ex.getClass());
		}

		@ExceptionHandler(ArrayIndexOutOfBoundsException.class)
		@ResponseBody
		public String handleWithHandlerMethod(HandlerMethod handlerMethod) {
			return "HandlerMethod: " + handlerMethod.getMethod().getName();
		}
	}

	@ControllerAdvice
	@Order(2)
	static class AnotherTestExceptionResolver {

		@ExceptionHandler({IllegalStateException.class, IllegalAccessException.class})
		@ResponseBody
		public String handleException(Exception ex) {
			return "AnotherTestExceptionResolver: " + ClassUtils.getShortName(ex.getClass());
		}
	}

	@Configuration
	static class MyConfig {

		@Bean public TestExceptionResolver testExceptionResolver() {
			return new TestExceptionResolver();
		}

		@Bean public AnotherTestExceptionResolver anotherTestExceptionResolver() {
			return new AnotherTestExceptionResolver();
		}
	}

	@ControllerAdvice("java.lang")
	@Order(1)
	static class NotCalledTestExceptionResolver {

		@ExceptionHandler
		@ResponseBody
		public String handleException(IllegalStateException ex) {
			return "NotCalledTestExceptionResolver: " + ClassUtils.getShortName(ex.getClass());
		}
	}

	@ControllerAdvice("org.springframework.web.servlet.mvc.method.annotation")
	@Order(2)
	static class BasePackageTestExceptionResolver {

		@ExceptionHandler
		@ResponseBody
		public String handleException(IllegalStateException ex) {
			return "BasePackageTestExceptionResolver: " + ClassUtils.getShortName(ex.getClass());
		}
	}

	@ControllerAdvice
	@Order(3)
	static class DefaultTestExceptionResolver {

		@ExceptionHandler
		@ResponseBody
		public String handleException(IllegalStateException ex) {
			return "DefaultTestExceptionResolver: " + ClassUtils.getShortName(ex.getClass());
		}
	}

	@Configuration
	static class MyControllerAdviceConfig {
		@Bean public NotCalledTestExceptionResolver notCalledTestExceptionResolver() {
			return new NotCalledTestExceptionResolver();
		}

		@Bean public BasePackageTestExceptionResolver basePackageTestExceptionResolver() {
			return new BasePackageTestExceptionResolver();
		}

		@Bean public DefaultTestExceptionResolver defaultTestExceptionResolver() {
			return new DefaultTestExceptionResolver();
		}
	}
}