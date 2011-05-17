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

package org.springframework.web.servlet.mvc.method.annotation.support;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;

import org.junit.Before;
import org.junit.Test;

import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.HandlerMapping;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;

/**
 * Test fixture with {@link RequestResponseBodyMethodProcessor} and mock {@link HttpMessageConverter}.
 * 
 * @author Arjen Poutsma
 * @author Rossen Stoyanchev
 */
public class RequestResponseBodyMethodProcessorTests {

	private RequestResponseBodyMethodProcessor processor;

	private HttpMessageConverter<String> messageConverter;

	private MethodParameter paramRequestBodyString;
	private MethodParameter paramInt;
	private MethodParameter returnTypeString;
	private MethodParameter returnTypeInt;

	private MethodParameter returnTypeStringProduces;

	private ModelAndViewContainer mavContainer;

	private NativeWebRequest webRequest;

	private MockHttpServletRequest servletRequest;

	private MockHttpServletResponse servletResponse;

	@SuppressWarnings("unchecked")
	@Before
	public void setUp() throws Exception {
		messageConverter = createMock(HttpMessageConverter.class);
		expect(messageConverter.getSupportedMediaTypes()).andReturn(Collections.singletonList(MediaType.TEXT_PLAIN));
		replay(messageConverter);

		processor = new RequestResponseBodyMethodProcessor(Collections.<HttpMessageConverter<?>>singletonList(messageConverter));
		reset(messageConverter);
		
		Method handle = getClass().getMethod("handle1", String.class, Integer.TYPE);
		paramRequestBodyString = new MethodParameter(handle, 0);
		paramInt = new MethodParameter(handle, 1);
		returnTypeString = new MethodParameter(handle, -1);

		returnTypeInt = new MethodParameter(getClass().getMethod("handle2"), -1);

		returnTypeStringProduces = new MethodParameter(getClass().getMethod("handle3"), -1);

		mavContainer = new ModelAndViewContainer();
		
		servletRequest = new MockHttpServletRequest();
		servletResponse = new MockHttpServletResponse();
		webRequest = new ServletWebRequest(servletRequest, servletResponse);
	}

	@Test
	public void supportsParameter() {
		assertTrue("RequestBody parameter not supported", processor.supportsParameter(paramRequestBodyString));
		assertFalse("non-RequestBody parameter supported", processor.supportsParameter(paramInt));
	}

	@Test
	public void supportsReturnType() {
		assertTrue("ResponseBody return type not supported", processor.supportsReturnType(returnTypeString));
		assertFalse("non-ResponseBody return type supported", processor.supportsReturnType(returnTypeInt));
	}

	@Test
	public void resolveArgument() throws Exception {
		MediaType contentType = MediaType.TEXT_PLAIN;
		servletRequest.addHeader("Content-Type", contentType.toString());

		String body = "Foo";
		expect(messageConverter.canRead(String.class, contentType)).andReturn(true);
		expect(messageConverter.read(eq(String.class), isA(HttpInputMessage.class))).andReturn(body);

		replay(messageConverter);

		Object result = processor.resolveArgument(paramRequestBodyString, mavContainer, webRequest, null);

		assertEquals("Invalid argument", body, result);
		assertTrue("The ResolveView flag shouldn't change", mavContainer.isResolveView());
		verify(messageConverter);
	}

	@Test(expected = HttpMediaTypeNotSupportedException.class)
	public void resolveArgumentNotReadable() throws Exception {
		MediaType contentType = MediaType.TEXT_PLAIN;
		servletRequest.addHeader("Content-Type", contentType.toString());

		expect(messageConverter.getSupportedMediaTypes()).andReturn(Arrays.asList(contentType));
		expect(messageConverter.canRead(String.class, contentType)).andReturn(false);
		replay(messageConverter);

		processor.resolveArgument(paramRequestBodyString, mavContainer, webRequest, null);

		fail("Expected exception");
	}

	@Test(expected = HttpMediaTypeNotSupportedException.class)
	public void resolveArgumentNoContentType() throws Exception {
		processor.resolveArgument(paramRequestBodyString, mavContainer, webRequest, null);
		fail("Expected exception");
	}

	@Test
	public void handleReturnValue() throws Exception {
		MediaType accepted = MediaType.TEXT_PLAIN;
		servletRequest.addHeader("Accept", accepted.toString());

		String body = "Foo";
		expect(messageConverter.canWrite(String.class, accepted)).andReturn(true);
		messageConverter.write(eq(body), eq(accepted), isA(HttpOutputMessage.class));
		replay(messageConverter);

		processor.handleReturnValue(body, returnTypeString, mavContainer, webRequest);

		assertFalse("The ResolveView flag wasn't turned off", mavContainer.isResolveView());
		verify(messageConverter);
	}

	@Test
	public void handleReturnValueProduces() throws Exception {
		String body = "Foo";

		servletRequest.addHeader("Accept", "text/*");
		servletRequest.setAttribute(HandlerMapping.PRODUCIBLE_MEDIA_TYPES_ATTRIBUTE, Collections.singleton(MediaType.TEXT_HTML));

		expect(messageConverter.canWrite(String.class, MediaType.TEXT_HTML)).andReturn(true);
		messageConverter.write(eq(body), eq(MediaType.TEXT_HTML), isA(HttpOutputMessage.class));
		replay(messageConverter);

		processor.handleReturnValue(body, returnTypeStringProduces, mavContainer, webRequest);

		assertFalse(mavContainer.isResolveView());
		verify(messageConverter);
	}


	@Test(expected = HttpMediaTypeNotAcceptableException.class)
	public void handleReturnValueNotAcceptable() throws Exception {
		MediaType accepted = MediaType.APPLICATION_ATOM_XML;
		servletRequest.addHeader("Accept", accepted.toString());

		expect(messageConverter.canWrite(String.class, null)).andReturn(true);
		expect(messageConverter.getSupportedMediaTypes()).andReturn(Arrays.asList(MediaType.TEXT_PLAIN));
		expect(messageConverter.canWrite(String.class, accepted)).andReturn(false);
		replay(messageConverter);

		processor.handleReturnValue("Foo", returnTypeString, mavContainer, webRequest);

		fail("Expected exception");
	}
	
	@Test(expected = HttpMediaTypeNotAcceptableException.class)
	public void handleReturnValueNotAcceptableProduces() throws Exception {
		MediaType accepted = MediaType.TEXT_PLAIN;
		servletRequest.addHeader("Accept", accepted.toString());

		expect(messageConverter.canWrite(String.class, accepted)).andReturn(false);
		replay(messageConverter);

		processor.handleReturnValue("Foo", returnTypeStringProduces, mavContainer, webRequest);

		fail("Expected exception");
	}

	@ResponseBody
	public String handle1(@RequestBody String s, int i) {
		return s;
	}

	public int handle2() {
		return 42;
	}

	@RequestMapping(produces = {"text/html", "application/xhtml+xml"})
	@ResponseBody
	public String handle3() {
		return null;
	}

}