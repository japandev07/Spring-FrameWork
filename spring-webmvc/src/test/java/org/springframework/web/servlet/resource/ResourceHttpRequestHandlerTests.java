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

package org.springframework.web.servlet.resource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.servlet.http.HttpServletResponse;

import org.junit.Before;
import org.junit.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.test.MockHttpServletRequest;
import org.springframework.mock.web.test.MockHttpServletResponse;
import org.springframework.mock.web.test.MockServletContext;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.HandlerMapping;

import static org.junit.Assert.*;

/**
 * Unit tests for ResourceHttpRequestHandler.
 *
 * @author Keith Donald
 * @author Jeremy Grelle
 * @author Rossen Stoyanchev
 */
public class ResourceHttpRequestHandlerTests {

	private ResourceHttpRequestHandler handler;

	private MockHttpServletRequest request;

	private MockHttpServletResponse response;


	@Before
	public void setUp() throws Exception {
		List<Resource> paths = new ArrayList<>(2);
		paths.add(new ClassPathResource("test/", getClass()));
		paths.add(new ClassPathResource("testalternatepath/", getClass()));

		this.handler = new ResourceHttpRequestHandler();
		this.handler.setLocations(paths);
		this.handler.setCacheSeconds(3600);
		this.handler.setServletContext(new TestServletContext());
		this.handler.afterPropertiesSet();

		this.request = new MockHttpServletRequest("GET", "");
		this.response = new MockHttpServletResponse();
	}

	@Test
	public void getResource() throws Exception {
		this.request.setAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE, "foo.css");
		this.handler.handleRequest(this.request, this.response);

		assertEquals("text/css", this.response.getContentType());
		assertEquals(17, this.response.getContentLength());
		assertTrue(headerAsLong("Expires") >= System.currentTimeMillis() - 1000 + (3600 * 1000));
		assertEquals("max-age=3600, must-revalidate", this.response.getHeader("Cache-Control"));
		assertTrue(this.response.containsHeader("Last-Modified"));
		assertEquals(headerAsLong("Last-Modified"), resourceLastModified("test/foo.css"));
		assertEquals("h1 { color:red; }", this.response.getContentAsString());
	}

	@Test
	public void getResourceWithHtmlMediaType() throws Exception {
		this.request.setAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE, "foo.html");
		this.handler.handleRequest(this.request, this.response);

		assertEquals("text/html", this.response.getContentType());
		assertTrue(headerAsLong("Expires") >= System.currentTimeMillis() - 1000 + (3600 * 1000));
		assertEquals("max-age=3600, must-revalidate", this.response.getHeader("Cache-Control"));
		assertTrue(this.response.containsHeader("Last-Modified"));
		assertEquals(headerAsLong("Last-Modified"), resourceLastModified("test/foo.html"));
	}

	@Test
	public void getResourceFromAlternatePath() throws Exception {
		this.request.setAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE, "baz.css");
		this.handler.handleRequest(this.request, this.response);

		assertEquals("text/css", this.response.getContentType());
		assertEquals(17, this.response.getContentLength());
		assertTrue(headerAsLong("Expires") >= System.currentTimeMillis() - 1000 + (3600 * 1000));
		assertEquals("max-age=3600, must-revalidate", this.response.getHeader("Cache-Control"));
		assertTrue(this.response.containsHeader("Last-Modified"));
		assertEquals(headerAsLong("Last-Modified"), resourceLastModified("testalternatepath/baz.css"));
		assertEquals("h1 { color:red; }", this.response.getContentAsString());
	}

	@Test
	public void getResourceFromSubDirectory() throws Exception {
		this.request.setAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE, "js/foo.js");
		this.handler.handleRequest(this.request, this.response);

		assertEquals("text/javascript", this.response.getContentType());
		assertEquals("function foo() { console.log(\"hello world\"); }", this.response.getContentAsString());
	}

	@Test
	public void getResourceFromSubDirectoryOfAlternatePath() throws Exception {
		this.request.setAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE, "js/baz.js");
		this.handler.handleRequest(this.request, this.response);

		assertEquals("text/javascript", this.response.getContentType());
		assertEquals("function foo() { console.log(\"hello world\"); }", this.response.getContentAsString());
	}

	@Test
	public void getResourceViaDirectoryTraversal() throws Exception {
		this.request.setAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE, "../testsecret/secret.txt");
		this.handler.handleRequest(this.request, this.response);
		assertEquals(404, this.response.getStatus());

		this.request.setAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE, "test/../../testsecret/secret.txt");
		this.response = new MockHttpServletResponse();
		this.handler.handleRequest(this.request, this.response);
		assertEquals(404, this.response.getStatus());

		this.handler.setLocations(Arrays.<Resource>asList(new ClassPathResource("testsecret/", getClass())));
		this.request.setAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE, "secret.txt");
		this.response = new MockHttpServletResponse();
		this.handler.handleRequest(this.request, this.response);
		assertEquals(200, this.response.getStatus());
		assertEquals("text/plain", this.response.getContentType());
		assertEquals("big secret", this.response.getContentAsString());
	}

	@Test
	public void notModified() throws Exception {
		this.request.setAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE, "foo.css");
		this.request.addHeader("If-Modified-Since", resourceLastModified("test/foo.css"));
		this.handler.handleRequest(this.request, this.response);
		assertEquals(HttpServletResponse.SC_NOT_MODIFIED, this.response.getStatus());
	}

	@Test
	public void modified() throws Exception {
		this.request.setAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE, "foo.css");
		this.request.addHeader("If-Modified-Since", resourceLastModified("test/foo.css") / 1000 * 1000 - 1);
		this.handler.handleRequest(this.request, this.response);
		assertEquals(HttpServletResponse.SC_OK, this.response.getStatus());
		assertEquals("h1 { color:red; }", this.response.getContentAsString());
	}

	@Test
	public void directory() throws Exception {
		this.request.setAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE, "js/");
		this.handler.handleRequest(this.request, this.response);
		assertEquals(404, this.response.getStatus());
	}

	@Test
	public void missingResourcePath() throws Exception {
		this.request.setAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE, "");
		this.handler.handleRequest(this.request, this.response);
		assertEquals(404, this.response.getStatus());
	}

	@Test(expected=IllegalStateException.class)
	public void noPathWithinHandlerMappingAttribute() throws Exception {
		this.handler.handleRequest(this.request, this.response);
	}

	@Test(expected=HttpRequestMethodNotSupportedException.class)
	public void unsupportedHttpMethod() throws Exception {
		this.request.setAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE, "foo.css");
		this.request.setMethod("POST");
		this.handler.handleRequest(this.request, this.response);
	}

	@Test
	public void resourceNotFound() throws Exception {
		this.request.setAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE, "not-there.css");
		this.handler.handleRequest(this.request, this.response);
		assertEquals(404, this.response.getStatus());
	}

	private long headerAsLong(String responseHeaderName) {
		return Long.valueOf(this.response.getHeader(responseHeaderName));
	}

	private long resourceLastModified(String resourceName) throws IOException {
		return new ClassPathResource(resourceName, getClass()).getFile().lastModified();
	}


	private static class TestServletContext extends MockServletContext {

		@Override
		public String getMimeType(String filePath) {
			if (filePath.endsWith(".css")) {
				return "text/css";
			}
			else if (filePath.endsWith(".js")) {
				return "text/javascript";
			}
			else {
				return super.getMimeType(filePath);
			}
		}
	}

}
