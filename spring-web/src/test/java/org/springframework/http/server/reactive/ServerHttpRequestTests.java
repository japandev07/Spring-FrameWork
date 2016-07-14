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

package org.springframework.http.server.reactive;

import java.util.Arrays;
import java.util.Collections;
import javax.servlet.http.HttpServletRequest;

import org.junit.Test;

import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.mock.web.test.MockHttpServletRequest;
import org.springframework.util.MultiValueMap;

import static org.junit.Assert.assertEquals;

/**
 * Unit tests for {@link AbstractServerHttpRequest}.
 *
 * @author Rossen Stoyanchev
 */
public class ServerHttpRequestTests {


	@Test
	public void queryParamsNone() throws Exception {
		MultiValueMap<String, String> params = createHttpRequest("/path").getQueryParams();
		assertEquals(0, params.size());
	}

	@Test
	public void queryParams() throws Exception {
		MultiValueMap<String, String> params = createHttpRequest("/path?a=A&b=B").getQueryParams();
		assertEquals(2, params.size());
		assertEquals(Collections.singletonList("A"), params.get("a"));
		assertEquals(Collections.singletonList("B"), params.get("b"));
	}

	@Test
	public void queryParamsWithMulitpleValues() throws Exception {
		MultiValueMap<String, String> params = createHttpRequest("/path?a=1&a=2").getQueryParams();
		assertEquals(1, params.size());
		assertEquals(Arrays.asList("1", "2"), params.get("a"));
	}

	@Test
	public void queryParamsWithEmptyValue() throws Exception {
		MultiValueMap<String, String> params = createHttpRequest("/path?a=").getQueryParams();
		assertEquals(1, params.size());
		assertEquals(Collections.singletonList(""), params.get("a"));
	}

	@Test
	public void queryParamsWithNoValue() throws Exception {
		MultiValueMap<String, String> params = createHttpRequest("/path?a").getQueryParams();
		assertEquals(1, params.size());
		assertEquals(Collections.singletonList(null), params.get("a"));
	}

	private ServerHttpRequest createHttpRequest(String path) throws Exception {
		HttpServletRequest servletRequest = new MockHttpServletRequest("GET", path);
		return new ServletServerHttpRequest(servletRequest,
				new DefaultDataBufferFactory(), 1024);
	}

}
