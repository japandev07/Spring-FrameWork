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

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Before;
import org.junit.Test;

import org.springframework.core.MethodParameter;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.test.MockAsyncContext;
import org.springframework.mock.web.test.MockHttpServletRequest;
import org.springframework.mock.web.test.MockHttpServletResponse;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.async.AsyncWebRequest;
import org.springframework.web.context.request.async.StandardServletAsyncWebRequest;
import org.springframework.web.context.request.async.WebAsyncUtils;
import org.springframework.web.method.support.ModelAndViewContainer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event;


/**
 * Unit tests for ResponseBodyEmitterReturnValueHandler.
 * @author Rossen Stoyanchev
 */
public class ResponseBodyEmitterReturnValueHandlerTests {

	private ResponseBodyEmitterReturnValueHandler handler;

	private ModelAndViewContainer mavContainer;

	private NativeWebRequest webRequest;

	private MockHttpServletRequest request;

	private MockHttpServletResponse response;


	@Before
	public void setUp() throws Exception {

		List<HttpMessageConverter<?>> converters = Arrays.asList(
				new StringHttpMessageConverter(), new MappingJackson2HttpMessageConverter());

		this.handler = new ResponseBodyEmitterReturnValueHandler(converters);
		this.mavContainer = new ModelAndViewContainer();

		this.request = new MockHttpServletRequest();
		this.response = new MockHttpServletResponse();
		this.webRequest = new ServletWebRequest(this.request, this.response);

		AsyncWebRequest asyncWebRequest = new StandardServletAsyncWebRequest(this.request, this.response);
		WebAsyncUtils.getAsyncManager(this.webRequest).setAsyncWebRequest(asyncWebRequest);
		this.request.setAsyncSupported(true);
	}

	@Test
	public void supportsReturnType() throws Exception {
		assertTrue(this.handler.supportsReturnType(returnType(TestController.class, "handle")));
		assertTrue(this.handler.supportsReturnType(returnType(TestController.class, "handleSse")));
		assertTrue(this.handler.supportsReturnType(returnType(TestController.class, "handleResponseEntity")));
		assertFalse(this.handler.supportsReturnType(returnType(TestController.class, "handleResponseEntityString")));
		assertFalse(this.handler.supportsReturnType(returnType(TestController.class, "handleResponseEntityParameterized")));
	}

	@Test
	public void responseBodyEmitter() throws Exception {
		MethodParameter returnType = returnType(TestController.class, "handle");
		ResponseBodyEmitter emitter = new ResponseBodyEmitter();
		this.handler.handleReturnValue(emitter, returnType, this.mavContainer, this.webRequest);

		assertTrue(this.request.isAsyncStarted());
		assertEquals("", this.response.getContentAsString());

		SimpleBean bean = new SimpleBean();
		bean.setId(1L);
		bean.setName("Joe");
		emitter.send(bean);
		emitter.send("\n");

		bean.setId(2L);
		bean.setName("John");
		emitter.send(bean);
		emitter.send("\n");

		bean.setId(3L);
		bean.setName("Jason");
		emitter.send(bean);

		assertEquals("{\"id\":1,\"name\":\"Joe\"}\n" +
						"{\"id\":2,\"name\":\"John\"}\n" +
						"{\"id\":3,\"name\":\"Jason\"}",
				this.response.getContentAsString());

		MockAsyncContext asyncContext = (MockAsyncContext) this.request.getAsyncContext();
		assertNull(asyncContext.getDispatchedPath());

		emitter.complete();
		assertNotNull(asyncContext.getDispatchedPath());
	}

	@Test
	public void sseEmitter() throws Exception {
		MethodParameter returnType = returnType(TestController.class, "handleSse");
		SseEmitter emitter = new SseEmitter();
		this.handler.handleReturnValue(emitter, returnType, this.mavContainer, this.webRequest);

		assertTrue(this.request.isAsyncStarted());
		assertEquals(200, this.response.getStatus());
		assertEquals("text/event-stream", this.response.getContentType());

		SimpleBean bean1 = new SimpleBean();
		bean1.setId(1L);
		bean1.setName("Joe");

		SimpleBean bean2 = new SimpleBean();
		bean2.setId(2L);
		bean2.setName("John");

		emitter.send(event().comment("a test").name("update").id("1").reconnectTime(5000L).data(bean1).data(bean2));

		assertEquals(":a test\n" +
						"name:update\n" +
						"id:1\n" +
						"retry:5000\n" +
						"data:{\"id\":1,\"name\":\"Joe\"}\n" +
						"data:{\"id\":2,\"name\":\"John\"}\n" +
						"\n",
				this.response.getContentAsString());
	}

	@Test
	public void responseEntitySse() throws Exception {
		MethodParameter returnType = returnType(TestController.class, "handleResponseEntitySse");
		ResponseEntity<SseEmitter> emitter = ResponseEntity.ok().header("foo", "bar").body(new SseEmitter());
		this.handler.handleReturnValue(emitter, returnType, this.mavContainer, this.webRequest);

		assertTrue(this.request.isAsyncStarted());
		assertEquals(200, this.response.getStatus());
		assertEquals("text/event-stream", this.response.getContentType());
		assertEquals("bar", this.response.getHeader("foo"));
	}

	@Test
	public void responseEntitySseNoContent() throws Exception {
		MethodParameter returnType = returnType(TestController.class, "handleResponseEntitySse");
		ResponseEntity<?> emitter = ResponseEntity.noContent().build();
		this.handler.handleReturnValue(emitter, returnType, this.mavContainer, this.webRequest);

		assertFalse(this.request.isAsyncStarted());
		assertEquals(204, this.response.getStatus());
	}


	private MethodParameter returnType(Class<?> clazz, String methodName) throws NoSuchMethodException {
		Method method = clazz.getDeclaredMethod(methodName);
		return new MethodParameter(method, -1);
	}



	@SuppressWarnings("unused")
	private static class TestController {

		private ResponseBodyEmitter handle() {
			return null;
		}

		private ResponseEntity<ResponseBodyEmitter> handleResponseEntity() {
			return null;
		}

		private SseEmitter handleSse() {
			return null;
		}

		private ResponseEntity<SseEmitter> handleResponseEntitySse() {
			return null;
		}

		private ResponseEntity<String> handleResponseEntityString() {
			return null;
		}

		private ResponseEntity<AtomicReference<String>> handleResponseEntityParameterized() {
			return null;
		}

	}

	private static class SimpleBean {

		private Long id;

		private String name;

		public Long getId() {
			return id;
		}

		public void setId(Long id) {
			this.id = id;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}
	}

}
