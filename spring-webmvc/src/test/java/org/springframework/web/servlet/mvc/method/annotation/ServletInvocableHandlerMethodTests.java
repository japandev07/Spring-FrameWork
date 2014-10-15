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

package org.springframework.web.servlet.mvc.method.annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.servlet.http.HttpServletResponse;

import org.junit.Before;
import org.junit.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.mock.web.test.MockHttpServletRequest;
import org.springframework.mock.web.test.MockHttpServletResponse;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.method.annotation.RequestParamMethodArgumentResolver;
import org.springframework.web.method.support.HandlerMethodArgumentResolverComposite;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.method.support.HandlerMethodReturnValueHandlerComposite;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.view.RedirectView;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test fixture with {@link ServletInvocableHandlerMethod}.
 *
 * @author Rossen Stoyanchev
 */
public class ServletInvocableHandlerMethodTests {

	private HandlerMethodArgumentResolverComposite argumentResolvers;

	private HandlerMethodReturnValueHandlerComposite returnValueHandlers;

	private ModelAndViewContainer mavContainer;

	private ServletWebRequest webRequest;

	private MockHttpServletRequest request;

	private MockHttpServletResponse response;

	@Before
	public void setUp() throws Exception {
		returnValueHandlers = new HandlerMethodReturnValueHandlerComposite();
		argumentResolvers = new HandlerMethodArgumentResolverComposite();
		mavContainer = new ModelAndViewContainer();
		request = new MockHttpServletRequest();
		response = new MockHttpServletResponse();
		webRequest = new ServletWebRequest(request, response);
	}

	@Test
	public void invokeAndHandle_VoidWithResponseStatus() throws Exception {
		ServletInvocableHandlerMethod handlerMethod = getHandlerMethod(new Handler(), "responseStatus");
		handlerMethod.invokeAndHandle(webRequest, mavContainer);

		assertTrue("Null return value + @ResponseStatus should result in 'request handled'",
				mavContainer.isRequestHandled());
		assertEquals(HttpStatus.BAD_REQUEST.value(), response.getStatus());
	}

	@Test
	public void invokeAndHandle_VoidWithHttpServletResponseArgument() throws Exception {
		argumentResolvers.addResolver(new ServletResponseMethodArgumentResolver());

		ServletInvocableHandlerMethod handlerMethod = getHandlerMethod(new Handler(), "httpServletResponse", HttpServletResponse.class);
		handlerMethod.invokeAndHandle(webRequest, mavContainer);

		assertTrue("Null return value + HttpServletResponse arg should result in 'request handled'",
				mavContainer.isRequestHandled());
	}

	@Test
	public void invokeAndHandle_VoidRequestNotModified() throws Exception {
		webRequest.getNativeRequest(MockHttpServletRequest.class).addHeader("If-Modified-Since", 10 * 1000 * 1000);
		int lastModifiedTimestamp = 1000 * 1000;
		webRequest.checkNotModified(lastModifiedTimestamp);

		ServletInvocableHandlerMethod handlerMethod = getHandlerMethod(new Handler(), "notModified");
		handlerMethod.invokeAndHandle(webRequest, mavContainer);

		assertTrue("Null return value + 'not modified' request should result in 'request handled'",
				mavContainer.isRequestHandled());
	}

	// SPR-9159

	@Test
	public void invokeAndHandle_NotVoidWithResponseStatusAndReason() throws Exception {
		ServletInvocableHandlerMethod handlerMethod = getHandlerMethod(new Handler(), "responseStatusWithReason");
		handlerMethod.invokeAndHandle(webRequest, mavContainer);

		assertTrue("When a phrase is used, the response should not be used any more", mavContainer.isRequestHandled());
		assertEquals(HttpStatus.BAD_REQUEST.value(), response.getStatus());
		assertEquals("400 Bad Request", response.getErrorMessage());
	}

	@Test(expected=HttpMessageNotWritableException.class)
	public void invokeAndHandle_Exception() throws Exception {
		returnValueHandlers.addHandler(new ExceptionRaisingReturnValueHandler());

		ServletInvocableHandlerMethod handlerMethod = getHandlerMethod(new Handler(), "handle");
		handlerMethod.invokeAndHandle(webRequest, mavContainer);
		fail("Expected exception");
	}

	@Test
	public void invokeAndHandle_DynamicReturnValue() throws Exception {
		argumentResolvers.addResolver(new RequestParamMethodArgumentResolver(null, false));
		returnValueHandlers.addHandler(new ViewMethodReturnValueHandler());
		returnValueHandlers.addHandler(new ViewNameMethodReturnValueHandler());

		// Invoke without a request parameter (String return value)
		ServletInvocableHandlerMethod handlerMethod = getHandlerMethod(new Handler(), "dynamicReturnValue", String.class);
		handlerMethod.invokeAndHandle(webRequest, mavContainer);

		assertNotNull(mavContainer.getView());
		assertEquals(RedirectView.class, mavContainer.getView().getClass());

		// Invoke with a request parameter (RedirectView return value)
		request.setParameter("param", "value");
		handlerMethod.invokeAndHandle(webRequest, mavContainer);

		assertEquals("view", mavContainer.getViewName());
	}

	@Test
	public void wrapConcurrentResult_MethodLevelResponseBody() throws Exception {
		wrapConcurrentResult_ResponseBody(new MethodLevelResponseBodyHandler());
	}

	@Test
	public void wrapConcurrentResult_TypeLevelResponseBody() throws Exception {
		wrapConcurrentResult_ResponseBody(new TypeLevelResponseBodyHandler());
	}

	private void wrapConcurrentResult_ResponseBody(Object handler) throws Exception {
		List<HttpMessageConverter<?>> converters = new ArrayList<HttpMessageConverter<?>>();
		converters.add(new StringHttpMessageConverter());
		returnValueHandlers.addHandler(new RequestResponseBodyMethodProcessor(converters));
		ServletInvocableHandlerMethod handlerMethod = getHandlerMethod(handler, "handle");
		handlerMethod = handlerMethod.wrapConcurrentResult("bar");
		handlerMethod.invokeAndHandle(webRequest, mavContainer);

		assertEquals("bar", response.getContentAsString());
	}

	@Test
	public void wrapConcurrentResult_ResponseEntity() throws Exception {
		List<HttpMessageConverter<?>> converters = new ArrayList<HttpMessageConverter<?>>();
		converters.add(new StringHttpMessageConverter());
		returnValueHandlers.addHandler(new HttpEntityMethodProcessor(converters));
		ServletInvocableHandlerMethod handlerMethod = getHandlerMethod(new ResponseEntityHandler(), "handle");
		handlerMethod = handlerMethod.wrapConcurrentResult(new ResponseEntity<>("bar", HttpStatus.OK));
		handlerMethod.invokeAndHandle(webRequest, mavContainer);

		assertEquals("bar", response.getContentAsString());
	}

	// SPR-12287

	@Test
	public void wrapConcurrentResult_ResponseEntityNullBody() throws Exception {
		List<HttpMessageConverter<?>> converters = new ArrayList<HttpMessageConverter<?>>();
		converters.add(new StringHttpMessageConverter());
		List<Object> advice = Arrays.asList(mock(ResponseBodyAdvice.class));
		HttpEntityMethodProcessor processor = new HttpEntityMethodProcessor(converters, null, advice);
		returnValueHandlers.addHandler(processor);
		ServletInvocableHandlerMethod handlerMethod = getHandlerMethod(new ResponseEntityHandler(), "handle");
		handlerMethod = handlerMethod.wrapConcurrentResult(new ResponseEntity<>(HttpStatus.OK));
		handlerMethod.invokeAndHandle(webRequest, mavContainer);

		assertEquals(200, response.getStatus());
		assertEquals("", response.getContentAsString());
	}

	@Test
	public void wrapConcurrentResult_ResponseEntityNullReturnValue() throws Exception {
		List<HttpMessageConverter<?>> converters = new ArrayList<HttpMessageConverter<?>>();
		converters.add(new StringHttpMessageConverter());
		List<Object> advice = Arrays.asList(mock(ResponseBodyAdvice.class));
		HttpEntityMethodProcessor processor = new HttpEntityMethodProcessor(converters, null, advice);
		returnValueHandlers.addHandler(processor);
		ServletInvocableHandlerMethod handlerMethod = getHandlerMethod(new ResponseEntityHandler(), "handle");
		handlerMethod = handlerMethod.wrapConcurrentResult(null);
		handlerMethod.invokeAndHandle(webRequest, mavContainer);

		assertEquals(200, response.getStatus());
		assertEquals("", response.getContentAsString());
	}

	private ServletInvocableHandlerMethod getHandlerMethod(Object controller,
			String methodName, Class<?>... argTypes) throws NoSuchMethodException {

		Method method = controller.getClass().getDeclaredMethod(methodName, argTypes);
		ServletInvocableHandlerMethod handlerMethod = new ServletInvocableHandlerMethod(controller, method);
		handlerMethod.setHandlerMethodArgumentResolvers(argumentResolvers);
		handlerMethod.setHandlerMethodReturnValueHandlers(returnValueHandlers);
		return handlerMethod;
	}

	@SuppressWarnings("unused")
	private static class Handler {

		public String handle() {
			return "view";
		}

		@ResponseStatus(value = HttpStatus.BAD_REQUEST)
		public void responseStatus() {
		}

		@ResponseStatus(value = HttpStatus.BAD_REQUEST, reason = "400 Bad Request")
		public String responseStatusWithReason() {
			return "foo";
		}

		public void httpServletResponse(HttpServletResponse response) {
		}

		public void notModified() {
		}

		public Object dynamicReturnValue(@RequestParam(required=false) String param) {
			return (param != null) ? "view" : new RedirectView("redirectView");
		}
	}

	private static class MethodLevelResponseBodyHandler {

		@ResponseBody
		public DeferredResult<String> handle() {
			return new DeferredResult<>();
		}
	}

	@ResponseBody
	private static class TypeLevelResponseBodyHandler {

		@SuppressWarnings("unused")
		public DeferredResult<String> handle() {
			return new DeferredResult<String>();
		}
	}

	private static class ResponseEntityHandler {

		@SuppressWarnings("unused")
		public DeferredResult<ResponseEntity<String>> handle() {
			return new DeferredResult<>();
		}
	}


	private static class ExceptionRaisingReturnValueHandler implements HandlerMethodReturnValueHandler {

		@Override
		public boolean supportsReturnType(MethodParameter returnType) {
			return true;
		}

		@Override
		public void handleReturnValue(Object returnValue, MethodParameter returnType,
				ModelAndViewContainer mavContainer, NativeWebRequest webRequest) throws Exception {
			throw new HttpMessageNotWritableException("oops, can't write");
		}
	}

}