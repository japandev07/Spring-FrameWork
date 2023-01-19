/*
 * Copyright 2002-2023 the original author or authors.
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

package org.springframework.web.servlet.mvc.method.annotation;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.stereotype.Controller;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.method.ControllerAdviceBean;
import org.springframework.web.testfixture.servlet.MockHttpServletRequest;
import org.springframework.web.testfixture.servlet.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link RequestResponseBodyAdviceChain}.
 *
 * @author Rossen Stoyanchev
 * @since 4.2
 */
class RequestResponseBodyAdviceChainTests {

	private String body = "body";

	private MediaType contentType = MediaType.TEXT_PLAIN;

	private Class<? extends HttpMessageConverter<?>> converterType = StringHttpMessageConverter.class;

	private MethodParameter paramType = new MethodParameter(ClassUtils.getMethod(this.getClass(), "handle", String.class), 0);
	private MethodParameter returnType = new MethodParameter(ClassUtils.getMethod(this.getClass(), "handle", String.class), -1);

	private ServerHttpRequest request = new ServletServerHttpRequest(new MockHttpServletRequest());
	private ServerHttpResponse response = new ServletServerHttpResponse(new MockHttpServletResponse());


	@Test
	@SuppressWarnings("unchecked")
	void requestBodyAdvice() throws IOException {
		RequestBodyAdvice requestAdvice = mock();
		ResponseBodyAdvice<String> responseAdvice = mock();
		List<Object> advice = Arrays.asList(requestAdvice, responseAdvice);
		RequestResponseBodyAdviceChain chain = new RequestResponseBodyAdviceChain(advice);

		HttpInputMessage wrapped = new ServletServerHttpRequest(new MockHttpServletRequest());
		given(requestAdvice.supports(this.paramType, String.class, this.converterType)).willReturn(true);
		given(requestAdvice.beforeBodyRead(eq(this.request), eq(this.paramType), eq(String.class),
				eq(this.converterType))).willReturn(wrapped);

		assertThat(chain.beforeBodyRead(this.request, this.paramType, String.class, this.converterType)).isSameAs(wrapped);

		String modified = "body++";
		given(requestAdvice.afterBodyRead(eq(this.body), eq(this.request), eq(this.paramType),
				eq(String.class), eq(this.converterType))).willReturn(modified);

		assertThat(chain.afterBodyRead(this.body, this.request, this.paramType,
				String.class, this.converterType)).isEqualTo(modified);
	}

	@Test
	@SuppressWarnings("unchecked")
	void responseBodyAdvice() {
		RequestBodyAdvice requestAdvice = mock();
		ResponseBodyAdvice<String> responseAdvice = mock();
		List<Object> advice = Arrays.asList(requestAdvice, responseAdvice);
		RequestResponseBodyAdviceChain chain = new RequestResponseBodyAdviceChain(advice);

		String expected = "body++";
		given(responseAdvice.supports(this.returnType, this.converterType)).willReturn(true);
		given(responseAdvice.beforeBodyWrite(eq(this.body), eq(this.returnType), eq(this.contentType),
				eq(this.converterType), same(this.request), same(this.response))).willReturn(expected);

		String actual = (String) chain.beforeBodyWrite(this.body, this.returnType, this.contentType,
				this.converterType, this.request, this.response);

		assertThat(actual).isEqualTo(expected);
	}

	@Test
	void controllerAdvice() {
		Object adviceBean = new ControllerAdviceBean(new MyControllerAdvice());
		RequestResponseBodyAdviceChain chain = new RequestResponseBodyAdviceChain(Collections.singletonList(adviceBean));

		String actual = (String) chain.beforeBodyWrite(this.body, this.returnType, this.contentType,
				this.converterType, this.request, this.response);

		assertThat(actual).isEqualTo("body-MyControllerAdvice");
	}

	@Test
	void controllerAdviceNotApplicable() {
		Object adviceBean = new ControllerAdviceBean(new TargetedControllerAdvice());
		RequestResponseBodyAdviceChain chain = new RequestResponseBodyAdviceChain(Collections.singletonList(adviceBean));

		String actual = (String) chain.beforeBodyWrite(this.body, this.returnType, this.contentType,
				this.converterType, this.request, this.response);

		assertThat(actual).isEqualTo(this.body);
	}


	@ControllerAdvice
	private static class MyControllerAdvice implements ResponseBodyAdvice<String> {

		@Override
		public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
			return true;
		}

		@Override
		public String beforeBodyWrite(String body, MethodParameter returnType,
				MediaType contentType, Class<? extends HttpMessageConverter<?>> converterType,
				ServerHttpRequest request, ServerHttpResponse response) {

			return body + "-MyControllerAdvice";
		}
	}


	@ControllerAdvice(annotations = Controller.class)
	private static class TargetedControllerAdvice implements ResponseBodyAdvice<String> {

		@Override
		public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
			return true;
		}

		@Override
		public String beforeBodyWrite(String body, MethodParameter returnType,
				MediaType contentType, Class<? extends HttpMessageConverter<?>> converterType,
				ServerHttpRequest request, ServerHttpResponse response) {

			return body + "-TargetedControllerAdvice";
		}
	}


	@SuppressWarnings("unused")
	@ResponseBody
	public String handle(String body) {
		return "";
	}

}
