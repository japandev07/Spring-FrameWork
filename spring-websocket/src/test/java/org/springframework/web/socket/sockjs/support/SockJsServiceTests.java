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

package org.springframework.web.socket.sockjs.support;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;

import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.Test;

import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.AbstractHttpRequestTests;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.sockjs.SockJsException;

import static org.junit.Assert.*;
import static org.mockito.BDDMockito.*;

/**
 * Test fixture for {@link AbstractSockJsService}.
 *
 * @author Rossen Stoyanchev
 * @author Sebastien Deleuze
 */
public class SockJsServiceTests extends AbstractHttpRequestTests {

	private TestSockJsService service;

	private WebSocketHandler handler;


	@Override
	@Before
	public void setUp() {
		super.setUp();
		this.service = new TestSockJsService(new ThreadPoolTaskScheduler());
	}


	@Test
	public void validateRequest() throws Exception {

		this.service.setWebSocketEnabled(false);
		resetResponseAndHandleRequest("GET", "/echo/server/session/websocket", HttpStatus.NOT_FOUND);

		this.service.setWebSocketEnabled(true);
		resetResponseAndHandleRequest("GET", "/echo/server/session/websocket", HttpStatus.OK);

		resetResponseAndHandleRequest("GET", "/echo//", HttpStatus.NOT_FOUND);
		resetResponseAndHandleRequest("GET", "/echo///", HttpStatus.NOT_FOUND);
		resetResponseAndHandleRequest("GET", "/echo/other", HttpStatus.NOT_FOUND);
		resetResponseAndHandleRequest("GET", "/echo//service/websocket", HttpStatus.NOT_FOUND);
		resetResponseAndHandleRequest("GET", "/echo/server//websocket", HttpStatus.NOT_FOUND);
		resetResponseAndHandleRequest("GET", "/echo/server/session/", HttpStatus.NOT_FOUND);
		resetResponseAndHandleRequest("GET", "/echo/s.erver/session/websocket", HttpStatus.NOT_FOUND);
		resetResponseAndHandleRequest("GET", "/echo/server/s.ession/websocket", HttpStatus.NOT_FOUND);
	}

	@Test
	public void handleInfoGet() throws Exception {
		resetResponseAndHandleRequest("GET", "/echo/info", HttpStatus.OK);

		assertEquals("application/json;charset=UTF-8", this.servletResponse.getContentType());
		assertEquals("no-store, no-cache, must-revalidate, max-age=0", this.servletResponse.getHeader("Cache-Control"));
		assertNull(this.servletResponse.getHeader("Access-Control-Allow-Origin"));
		assertNull(this.servletResponse.getHeader("Access-Control-Allow-Credentials"));
		assertNull(this.servletResponse.getHeader("Vary"));

		String body = this.servletResponse.getContentAsString();
		assertEquals("{\"entropy\"", body.substring(0, body.indexOf(':')));
		assertEquals(",\"origins\":[\"*:*\"],\"cookie_needed\":true,\"websocket\":true}",
				body.substring(body.indexOf(',')));

		this.service.setSessionCookieNeeded(false);
		this.service.setWebSocketEnabled(false);
		resetResponseAndHandleRequest("GET", "/echo/info", HttpStatus.OK);

		body = this.servletResponse.getContentAsString();
		assertEquals(",\"origins\":[\"*:*\"],\"cookie_needed\":false,\"websocket\":false}",
				body.substring(body.indexOf(',')));

		this.service.setAllowedOrigins(Arrays.asList("http://mydomain1.com"));
		resetResponseAndHandleRequest("GET", "/echo/info", HttpStatus.FORBIDDEN);
		assertNull(this.servletResponse.getHeader("Access-Control-Allow-Origin"));
		assertNull(this.servletResponse.getHeader("Access-Control-Allow-Credentials"));
		assertNull(this.servletResponse.getHeader("Vary"));
	}

	@Test  // SPR-12226
	public void handleInfoGetWithOrigin() throws Exception {
		setOrigin("http://mydomain2.com");
		resetResponseAndHandleRequest("GET", "/echo/info", HttpStatus.OK);

		assertEquals("application/json;charset=UTF-8", this.servletResponse.getContentType());
		assertEquals("no-store, no-cache, must-revalidate, max-age=0", this.servletResponse.getHeader("Cache-Control"));
		assertEquals("http://mydomain2.com", this.servletResponse.getHeader("Access-Control-Allow-Origin"));
		assertEquals("true", this.servletResponse.getHeader("Access-Control-Allow-Credentials"));
		assertEquals("Origin", this.servletResponse.getHeader("Vary"));

		String body = this.servletResponse.getContentAsString();
		assertEquals("{\"entropy\"", body.substring(0, body.indexOf(':')));
		assertEquals(",\"origins\":[\"*:*\"],\"cookie_needed\":true,\"websocket\":true}",
				body.substring(body.indexOf(',')));

		this.service.setAllowedOrigins(null);
		resetResponseAndHandleRequest("GET", "/echo/info", HttpStatus.FORBIDDEN);
		assertNull(this.servletResponse.getHeader("Access-Control-Allow-Origin"));
		assertNull(this.servletResponse.getHeader("Access-Control-Allow-Credentials"));
		assertNull(this.servletResponse.getHeader("Vary"));

		this.service.setAllowedOrigins(Arrays.asList("http://mydomain1.com"));
		resetResponseAndHandleRequest("GET", "/echo/info", HttpStatus.FORBIDDEN);
		assertNull(this.servletResponse.getHeader("Access-Control-Allow-Origin"));
		assertNull(this.servletResponse.getHeader("Access-Control-Allow-Credentials"));
		assertNull(this.servletResponse.getHeader("Vary"));

		this.service.setAllowedOrigins(Arrays.asList("http://mydomain1.com", "http://mydomain2.com", "http://mydomain3.com"));
		resetResponseAndHandleRequest("GET", "/echo/info", HttpStatus.OK);
		assertEquals("http://mydomain2.com", this.servletResponse.getHeader("Access-Control-Allow-Origin"));
		assertEquals("true", this.servletResponse.getHeader("Access-Control-Allow-Credentials"));
		assertEquals("Origin", this.servletResponse.getHeader("Vary"));
	}

	@Test  // SPR-11443
	public void handleInfoGetCorsFilter() throws Exception {

		// Simulate scenario where Filter would have already set CORS headers
		this.servletResponse.setHeader("Access-Control-Allow-Origin", "foobar:123");

		handleRequest("GET", "/echo/info", HttpStatus.OK);

		assertEquals("foobar:123", this.servletResponse.getHeader("Access-Control-Allow-Origin"));
	}

	@Test  // SPR-11919
	public void handleInfoGetWildflyNPE() throws Exception {
		HttpServletResponse mockResponse = mock(HttpServletResponse.class);
		ServletOutputStream ous = mock(ServletOutputStream.class);
		given(mockResponse.getHeaders("Access-Control-Allow-Origin")).willThrow(NullPointerException.class);
		given(mockResponse.getOutputStream()).willReturn(ous);
		this.response = new ServletServerHttpResponse(mockResponse);

		handleRequest("GET", "/echo/info", HttpStatus.OK);

		verify(mockResponse, times(1)).getOutputStream();
	}

	@Test
	public void handleInfoOptions() throws Exception {
		this.servletRequest.addHeader("Access-Control-Request-Headers", "Last-Modified");
		resetResponseAndHandleRequest("OPTIONS", "/echo/info", HttpStatus.NO_CONTENT);
		this.response.flush();

		assertNull(this.servletResponse.getHeader("Access-Control-Allow-Origin"));
		assertNull(this.servletResponse.getHeader("Access-Control-Allow-Credentials"));
		assertNull(this.servletResponse.getHeader("Access-Control-Allow-Headers"));
		assertNull(this.servletResponse.getHeader("Access-Control-Allow-Methods"));
		assertNull(this.servletResponse.getHeader("Access-Control-Max-Age"));
		assertEquals("Origin", this.servletResponse.getHeader("Vary"));

		this.service.setAllowedOrigins(Arrays.asList("http://mydomain1.com"));
		resetResponseAndHandleRequest("OPTIONS", "/echo/info", HttpStatus.FORBIDDEN);
		assertNull(this.servletResponse.getHeader("Access-Control-Allow-Origin"));
		assertNull(this.servletResponse.getHeader("Access-Control-Allow-Credentials"));
		assertNull(this.servletResponse.getHeader("Access-Control-Allow-Headers"));
		assertNull(this.servletResponse.getHeader("Access-Control-Allow-Methods"));
		assertNull(this.servletResponse.getHeader("Access-Control-Max-Age"));
		assertNull(this.servletResponse.getHeader("Vary"));
	}

	@Test  // SPR-12226
	public void handleInfoOptionsWithOrigin() throws Exception {
		setOrigin("http://mydomain2.com");
		this.servletRequest.addHeader("Access-Control-Request-Headers", "Last-Modified");
		resetResponseAndHandleRequest("OPTIONS", "/echo/info", HttpStatus.NO_CONTENT);
		this.response.flush();
		assertEquals("http://mydomain2.com", this.servletResponse.getHeader("Access-Control-Allow-Origin"));
		assertEquals("true", this.servletResponse.getHeader("Access-Control-Allow-Credentials"));
		assertEquals("Last-Modified", this.servletResponse.getHeader("Access-Control-Allow-Headers"));
		assertEquals("OPTIONS, GET", this.servletResponse.getHeader("Access-Control-Allow-Methods"));
		assertEquals("31536000", this.servletResponse.getHeader("Access-Control-Max-Age"));
		assertEquals("Origin", this.servletResponse.getHeader("Vary"));

		this.service.setAllowedOrigins(null);
		resetResponseAndHandleRequest("OPTIONS", "/echo/info", HttpStatus.FORBIDDEN);
		this.response.flush();
		assertNull(this.servletResponse.getHeader("Access-Control-Allow-Origin"));
		assertNull(this.servletResponse.getHeader("Access-Control-Allow-Credentials"));
		assertNull(this.servletResponse.getHeader("Access-Control-Allow-Headers"));
		assertNull(this.servletResponse.getHeader("Access-Control-Allow-Methods"));
		assertNull(this.servletResponse.getHeader("Access-Control-Max-Age"));
		assertNull(this.servletResponse.getHeader("Vary"));

		this.service.setAllowedOrigins(Arrays.asList("http://mydomain1.com"));
		resetResponseAndHandleRequest("OPTIONS", "/echo/info", HttpStatus.FORBIDDEN);
		this.response.flush();
		assertNull(this.servletResponse.getHeader("Access-Control-Allow-Origin"));
		assertNull(this.servletResponse.getHeader("Access-Control-Allow-Credentials"));
		assertNull(this.servletResponse.getHeader("Access-Control-Allow-Headers"));
		assertNull(this.servletResponse.getHeader("Access-Control-Allow-Methods"));
		assertNull(this.servletResponse.getHeader("Access-Control-Max-Age"));
		assertNull(this.servletResponse.getHeader("Vary"));

		this.service.setAllowedOrigins(Arrays.asList("http://mydomain1.com", "http://mydomain2.com", "http://mydomain3.com"));
		resetResponseAndHandleRequest("OPTIONS", "/echo/info", HttpStatus.NO_CONTENT);
		this.response.flush();
		assertEquals("http://mydomain2.com", this.servletResponse.getHeader("Access-Control-Allow-Origin"));
		assertEquals("true", this.servletResponse.getHeader("Access-Control-Allow-Credentials"));
		assertEquals("Last-Modified", this.servletResponse.getHeader("Access-Control-Allow-Headers"));
		assertEquals("OPTIONS, GET", this.servletResponse.getHeader("Access-Control-Allow-Methods"));
		assertEquals("31536000", this.servletResponse.getHeader("Access-Control-Max-Age"));
		assertEquals("Origin", this.servletResponse.getHeader("Vary"));
	}

	@Test  // SPR-12283
	public void handleInfoOptionsWithOriginAndCorsDisabled() throws Exception {
		setOrigin("http://mydomain2.com");
		this.service.setSuppressCors(true);

		this.servletRequest.addHeader("Access-Control-Request-Headers", "Last-Modified");
		resetResponseAndHandleRequest("OPTIONS", "/echo/info", HttpStatus.NO_CONTENT);
		this.response.flush();
		assertNull(this.servletResponse.getHeader("Access-Control-Allow-Origin"));
		assertNull(this.servletResponse.getHeader("Access-Control-Allow-Credentials"));
		assertNull(this.servletResponse.getHeader("Access-Control-Allow-Headers"));
		assertNull(this.servletResponse.getHeader("Access-Control-Allow-Methods"));
		assertNull(this.servletResponse.getHeader("Access-Control-Max-Age"));
		assertEquals("Origin", this.servletResponse.getHeader("Vary"));

		this.service.setAllowedOrigins(Arrays.asList("http://mydomain1.com"));
		resetResponseAndHandleRequest("OPTIONS", "/echo/info", HttpStatus.FORBIDDEN);
		this.response.flush();
		assertNull(this.servletResponse.getHeader("Access-Control-Allow-Origin"));
		assertNull(this.servletResponse.getHeader("Access-Control-Allow-Credentials"));
		assertNull(this.servletResponse.getHeader("Access-Control-Allow-Headers"));
		assertNull(this.servletResponse.getHeader("Access-Control-Allow-Methods"));
		assertNull(this.servletResponse.getHeader("Access-Control-Max-Age"));
		assertNull(this.servletResponse.getHeader("Vary"));

		this.service.setAllowedOrigins(Arrays.asList("http://mydomain1.com", "http://mydomain2.com", "http://mydomain3.com"));
		resetResponseAndHandleRequest("OPTIONS", "/echo/info", HttpStatus.NO_CONTENT);
		this.response.flush();
		assertNull(this.servletResponse.getHeader("Access-Control-Allow-Origin"));
		assertNull(this.servletResponse.getHeader("Access-Control-Allow-Credentials"));
		assertNull(this.servletResponse.getHeader("Access-Control-Allow-Headers"));
		assertNull(this.servletResponse.getHeader("Access-Control-Allow-Methods"));
		assertNull(this.servletResponse.getHeader("Access-Control-Max-Age"));
		assertEquals("Origin", this.servletResponse.getHeader("Vary"));
	}

	@Test
	public void handleIframeRequest() throws Exception {
		resetResponseAndHandleRequest("GET", "/echo/iframe.html", HttpStatus.OK);

		assertEquals("text/html;charset=UTF-8", this.servletResponse.getContentType());
		assertTrue(this.servletResponse.getContentAsString().startsWith("<!DOCTYPE html>\n"));
		assertEquals(490, this.servletResponse.getContentLength());
		assertEquals("public, max-age=31536000", this.response.getHeaders().getCacheControl());
		assertEquals("\"06b486b3208b085d9e3220f456a6caca4\"", this.response.getHeaders().getETag());
	}

	@Test
	public void handleIframeRequestNotModified() throws Exception {
		this.servletRequest.addHeader("If-None-Match", "\"06b486b3208b085d9e3220f456a6caca4\"");
		resetResponseAndHandleRequest("GET", "/echo/iframe.html", HttpStatus.NOT_MODIFIED);
	}

	@Test
	public void handleRawWebSocketRequest() throws Exception {
		resetResponseAndHandleRequest("GET", "/echo", HttpStatus.OK);
		assertEquals("Welcome to SockJS!\n", this.servletResponse.getContentAsString());

		resetResponseAndHandleRequest("GET", "/echo/websocket", HttpStatus.OK);
		assertNull("Raw WebSocket should not open a SockJS session", this.service.sessionId);
		assertSame(this.handler, this.service.handler);
	}

	@Test
	public void handleEmptyContentType() throws Exception {
		this.servletRequest.setContentType("");
		resetResponseAndHandleRequest("GET", "/echo/info", HttpStatus.OK);

		assertEquals("Invalid/empty content should have been ignored", 200, this.servletResponse.getStatus());
	}


	private void resetResponseAndHandleRequest(String httpMethod, String uri, HttpStatus httpStatus) throws IOException {
		resetResponse();
		handleRequest(httpMethod, uri, httpStatus);
	}

	private void handleRequest(String httpMethod, String uri, HttpStatus httpStatus) throws IOException {
		setRequest(httpMethod, uri);
		String sockJsPath = uri.substring("/echo".length());
		this.service.handleRequest(this.request, this.response, sockJsPath, this.handler);

		assertEquals(httpStatus.value(), this.servletResponse.getStatus());
	}


	private static class TestSockJsService extends AbstractSockJsService {

		private String sessionId;

		@SuppressWarnings("unused")
		private String transport;

		private WebSocketHandler handler;

		public TestSockJsService(TaskScheduler scheduler) {
			super(scheduler);
		}

		@Override
		protected void handleRawWebSocketRequest(ServerHttpRequest req, ServerHttpResponse res,
				WebSocketHandler handler) throws IOException {
			this.handler = handler;
		}

		@Override
		protected void handleTransportRequest(ServerHttpRequest req, ServerHttpResponse res, WebSocketHandler handler,
				String sessionId, String transport) throws SockJsException {
			this.sessionId = sessionId;
			this.transport = transport;
			this.handler = handler;
		}
	}

}
