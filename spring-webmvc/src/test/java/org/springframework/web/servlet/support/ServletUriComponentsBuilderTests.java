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

package org.springframework.web.servlet.support;

import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.test.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.util.UriComponents;

import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;

/**
 * Unit tests for
 * {@link org.springframework.web.servlet.support.ServletUriComponentsBuilder}.
 *
 * @author Rossen Stoyanchev
 */
public class ServletUriComponentsBuilderTests {

	private MockHttpServletRequest request;

	@Before
	public void setup() {
		this.request = new MockHttpServletRequest();
		this.request.setScheme("http");
		this.request.setServerName("localhost");
		this.request.setServerPort(80);
		this.request.setContextPath("/mvc-showcase");
	}

	@Test
	public void fromRequest() {
		request.setRequestURI("/mvc-showcase/data/param");
		request.setQueryString("foo=123");
		String result = ServletUriComponentsBuilder.fromRequest(request).build().toUriString();
		assertEquals("http://localhost/mvc-showcase/data/param?foo=123", result);
	}

	@Test
	public void fromRequestEncodedPath() {
		request.setRequestURI("/mvc-showcase/data/foo%20bar");
		String result = ServletUriComponentsBuilder.fromRequest(request).build().toUriString();
		assertEquals("http://localhost/mvc-showcase/data/foo%20bar", result);
	}

	@Test
	public void fromRequestAtypicalHttpPort() {
		request.setServerPort(8080);
		String result = ServletUriComponentsBuilder.fromRequest(request).build().toUriString();
		assertEquals("http://localhost:8080", result);
	}

	@Test
	public void fromRequestAtypicalHttpsPort() {
		request.setScheme("https");
		request.setServerPort(9043);
		String result = ServletUriComponentsBuilder.fromRequest(request).build().toUriString();
		assertEquals("https://localhost:9043", result);
	}

	@Test
	public void fromRequestUri() {
		request.setRequestURI("/mvc-showcase/data/param");
		request.setQueryString("foo=123");
		String result = ServletUriComponentsBuilder.fromRequestUri(request).build().toUriString();
		assertEquals("http://localhost/mvc-showcase/data/param", result);
	}

	@Test
	public void fromRequestWithForwardedHost() {
		request.addHeader("X-Forwarded-Host", "anotherHost");
		request.setRequestURI("/mvc-showcase/data/param");
		request.setQueryString("foo=123");
		String result = ServletUriComponentsBuilder.fromRequest(request).build().toUriString();
		assertEquals("http://anotherHost/mvc-showcase/data/param?foo=123", result);
	}

	// SPR-10701

	@Test
	public void fromRequestWithForwardedHostIncludingPort() {
		request.addHeader("X-Forwarded-Host", "webtest.foo.bar.com:443");
		request.setRequestURI("/mvc-showcase/data/param");
		request.setQueryString("foo=123");
		UriComponents result = ServletUriComponentsBuilder.fromRequest(request).build();

		assertEquals("webtest.foo.bar.com", result.getHost());
		assertEquals(443, result.getPort());
	}

	// SPR-11140

	@Test
	public void fromRequestWithForwardedHostMultiValuedHeader() {
		this.request.addHeader("X-Forwarded-Host", "a.example.org, b.example.org, c.example.org");
		assertEquals("a.example.org", ServletUriComponentsBuilder.fromRequest(this.request).build().getHost());
	}

	// SPR-11855

	@Test
	public void fromRequestWithForwardedHostAndPort() {
		this.request.addHeader("X-Forwarded-Host", "foobarhost");
		this.request.addHeader("X-Forwarded-Port", "9090");
		this.request.setServerPort(8080);
		UriComponents uriComponents = ServletUriComponentsBuilder.fromRequest(this.request).build();

		assertEquals("foobarhost", uriComponents.getHost());
		assertEquals(9090, uriComponents.getPort());
	}

	// SPR-11872

	@Test
	public void fromRequestWithForwardedHostWithDefaultPort() {
		this.request.setServerPort(10080);
		this.request.addHeader("X-Forwarded-Host", "example.org");
		UriComponents result = ServletUriComponentsBuilder.fromRequest(request).build();

		assertEquals("example.org", result.getHost());
		assertEquals("should have used the default port of the forwarded request", -1, result.getPort());
	}

	@Test
	public void fromRequestWithForwardedHostWithForwardedScheme() {
		this.request.setServerPort(10080);
		this.request.addHeader("X-Forwarded-Proto", "https");
		this.request.addHeader("X-Forwarded-Host", "example.org");
		UriComponents result = ServletUriComponentsBuilder.fromRequest(request).build();

		assertEquals("example.org", result.getHost());
		assertEquals("should have derived scheme from header", "https", result.getScheme());
		assertEquals("should have used the default port of the forwarded request", -1, result.getPort());
	}

	@Test
	public void fromContextPath() {
		request.setRequestURI("/mvc-showcase/data/param");
		request.setQueryString("foo=123");
		String result = ServletUriComponentsBuilder.fromContextPath(request).build().toUriString();
		assertEquals("http://localhost/mvc-showcase", result);
	}

	@Test
	public void fromServletMapping() {
		request.setRequestURI("/mvc-showcase/app/simple");
		request.setServletPath("/app");
		request.setQueryString("foo=123");
		String result = ServletUriComponentsBuilder.fromServletMapping(request).build().toUriString();
		assertEquals("http://localhost/mvc-showcase/app", result);
	}

	@Test
	public void fromCurrentRequest() {
		request.setRequestURI("/mvc-showcase/data/param");
		request.setQueryString("foo=123");
		RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(this.request));
		try {
			String result = ServletUriComponentsBuilder.fromCurrentRequest().build().toUriString();
			assertEquals("http://localhost/mvc-showcase/data/param?foo=123", result);
		}
		finally {
			RequestContextHolder.resetRequestAttributes();
		}
	}

	// SPR-10272

	@Test
	public void pathExtension() {
		this.request.setRequestURI("/rest/books/6.json");
		ServletUriComponentsBuilder builder = ServletUriComponentsBuilder.fromRequestUri(this.request);
		String extension = builder.removePathExtension();
		String result = builder.path("/pages/1.{ext}").buildAndExpand(extension).toUriString();
		assertEquals("http://localhost/rest/books/6/pages/1.json", result);
	}

	@Test
	public void pathExtensionNone() {
		this.request.setRequestURI("/rest/books/6");
		ServletUriComponentsBuilder builder = ServletUriComponentsBuilder.fromRequestUri(this.request);
		assertNull(builder.removePathExtension());
	}
}
