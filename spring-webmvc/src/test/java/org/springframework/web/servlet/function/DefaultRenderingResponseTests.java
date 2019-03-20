/*
 * Copyright 2002-2019 the original author or authors.
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

package org.springframework.web.servlet.function;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.servlet.http.Cookie;

import org.junit.Test;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.mock.web.test.MockHttpServletRequest;
import org.springframework.mock.web.test.MockHttpServletResponse;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.servlet.ModelAndView;

import static org.junit.Assert.*;

/**
 * @author Arjen Poutsma
 */
public class DefaultRenderingResponseTests {

	static final ServerResponse.Context EMPTY_CONTEXT = new ServerResponse.Context() {
		@Override
		public List<HttpMessageConverter<?>> messageConverters() {
			return Collections.emptyList();
		}

	};

	@Test
	public void create() throws Exception {
		String name = "foo";
		RenderingResponse result = RenderingResponse.create(name).build();

		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();
		ModelAndView mav = result.writeTo(request, response, EMPTY_CONTEXT);

		assertEquals(name, mav.getViewName());
	}

	@Test
	public void status() throws Exception {
		HttpStatus status = HttpStatus.I_AM_A_TEAPOT;
		RenderingResponse result = RenderingResponse.create("foo").status(status).build();

		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();
		ModelAndView mav = result.writeTo(request, response, EMPTY_CONTEXT);
		assertNotNull(mav);
		assertEquals(status.value(), response.getStatus());
	}

	@Test
	public void headers() throws Exception {
		HttpHeaders headers = new HttpHeaders();
		headers.set("foo", "bar");
		RenderingResponse result = RenderingResponse.create("foo").headers(headers).build();

		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();
		ModelAndView mav = result.writeTo(request, response, EMPTY_CONTEXT);
		assertNotNull(mav);

		assertEquals("bar", response.getHeader("foo"));
	}

	@Test
	public void modelAttribute() throws Exception {
		RenderingResponse result = RenderingResponse.create("foo")
				.modelAttribute("foo", "bar").build();

		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();
		ModelAndView mav = result.writeTo(request, response, EMPTY_CONTEXT);
		assertNotNull(mav);

		assertEquals("bar", mav.getModel().get("foo"));
	}


	@Test
	public void modelAttributeConventions() throws Exception {
		RenderingResponse result = RenderingResponse.create("foo")
				.modelAttribute("bar").build();
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();
		ModelAndView mav = result.writeTo(request, response, EMPTY_CONTEXT);
		assertNotNull(mav);
		assertEquals("bar", mav.getModel().get("string"));
	}

	@Test
	public void modelAttributes() throws Exception {
		Map<String, String> model = Collections.singletonMap("foo", "bar");
		RenderingResponse result = RenderingResponse.create("foo")
				.modelAttributes(model).build();
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();
		ModelAndView mav = result.writeTo(request, response, EMPTY_CONTEXT);
		assertNotNull(mav);
		assertEquals("bar", mav.getModel().get("foo"));
	}

	@Test
	public void modelAttributesConventions() throws Exception {
		RenderingResponse result = RenderingResponse.create("foo")
				.modelAttributes("bar").build();
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();
		ModelAndView mav = result.writeTo(request, response, EMPTY_CONTEXT);
		assertNotNull(mav);
		assertEquals("bar", mav.getModel().get("string"));
	}

	@Test
	public void cookies() throws Exception {
		MultiValueMap<String, Cookie> newCookies = new LinkedMultiValueMap<>();
		newCookies.add("name", new Cookie("name", "value"));
		RenderingResponse result =
				RenderingResponse.create("foo").cookies(cookies -> cookies.addAll(newCookies)).build();
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();
		ModelAndView mav = result.writeTo(request, response, EMPTY_CONTEXT);
		assertNotNull(mav);
		assertEquals(1, response.getCookies().length);
		assertEquals("name", response.getCookies()[0].getName());
		assertEquals("value", response.getCookies()[0].getValue());
	}

	@Test
	public void notModifiedEtag() throws Exception {
		String etag = "\"foo\"";
		RenderingResponse result = RenderingResponse.create("bar")
				.header(HttpHeaders.ETAG, etag)
				.build();

		MockHttpServletRequest request = new MockHttpServletRequest("GET", "https://example.com");
		request.addHeader(HttpHeaders.IF_NONE_MATCH, etag);
		MockHttpServletResponse response = new MockHttpServletResponse();

		ModelAndView mav = result.writeTo(request, response, EMPTY_CONTEXT);
		assertNull(mav);
		assertEquals(HttpStatus.NOT_MODIFIED.value(), response.getStatus());
	}


	@Test
	public void notModifiedLastModified() throws Exception {
		ZonedDateTime now = ZonedDateTime.now();
		ZonedDateTime oneMinuteBeforeNow = now.minus(1, ChronoUnit.MINUTES);

		RenderingResponse result = RenderingResponse.create("bar")
				.header(HttpHeaders.LAST_MODIFIED, DateTimeFormatter.RFC_1123_DATE_TIME.format(oneMinuteBeforeNow))
				.build();

		MockHttpServletRequest request = new MockHttpServletRequest("GET", "https://example.com");
		request.addHeader(HttpHeaders.IF_MODIFIED_SINCE,DateTimeFormatter.RFC_1123_DATE_TIME.format(now));
		MockHttpServletResponse response = new MockHttpServletResponse();

		ModelAndView mav = result.writeTo(request, response, EMPTY_CONTEXT);
		assertNull(mav);
		assertEquals(HttpStatus.NOT_MODIFIED.value(), response.getStatus());
	}


}
