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

package org.springframework.web.socket.server.support;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

import static org.junit.Assert.*;
import org.junit.Test;
import org.mockito.Mockito;

import org.springframework.http.HttpStatus;
import org.springframework.web.socket.AbstractHttpRequestTests;
import org.springframework.web.socket.WebSocketHandler;

/**
 * Test fixture for {@link OriginHandshakeInterceptor}.
 *
 * @author Sebastien Deleuze
 */
public class AllowedOriginsInterceptorTests extends AbstractHttpRequestTests {

	@Test
	public void originValueMatch() throws Exception {
		Map<String, Object> attributes = new HashMap<String, Object>();
		WebSocketHandler wsHandler = Mockito.mock(WebSocketHandler.class);
		setOrigin("http://mydomain1.com");
		OriginHandshakeInterceptor interceptor = new OriginHandshakeInterceptor();
		interceptor.setAllowedOrigins(Arrays.asList("http://mydomain1.com"));
		assertTrue(interceptor.beforeHandshake(request, response, wsHandler, attributes));
		assertNotEquals(servletResponse.getStatus(), HttpStatus.FORBIDDEN.value());
	}

	@Test
	public void originValueNoMatch() throws Exception {
		Map<String, Object> attributes = new HashMap<String, Object>();
		WebSocketHandler wsHandler = Mockito.mock(WebSocketHandler.class);
		setOrigin("http://mydomain1.com");
		OriginHandshakeInterceptor interceptor = new OriginHandshakeInterceptor();
		interceptor.setAllowedOrigins(Arrays.asList("http://mydomain2.com"));
		assertFalse(interceptor.beforeHandshake(request, response, wsHandler, attributes));
		assertEquals(servletResponse.getStatus(), HttpStatus.FORBIDDEN.value());
	}

	@Test
	public void originListMatch() throws Exception {
		Map<String, Object> attributes = new HashMap<String, Object>();
		WebSocketHandler wsHandler = Mockito.mock(WebSocketHandler.class);
		setOrigin("http://mydomain2.com");
		OriginHandshakeInterceptor interceptor = new OriginHandshakeInterceptor();
		interceptor.setAllowedOrigins(Arrays.asList("http://mydomain1.com", "http://mydomain2.com", "http://mydomain3.com"));
		assertTrue(interceptor.beforeHandshake(request, response, wsHandler, attributes));
		assertNotEquals(servletResponse.getStatus(), HttpStatus.FORBIDDEN.value());
	}

	@Test
	public void originListNoMatch() throws Exception {
		Map<String, Object> attributes = new HashMap<String, Object>();
		WebSocketHandler wsHandler = Mockito.mock(WebSocketHandler.class);
		setOrigin("http://mydomain4.com");
		OriginHandshakeInterceptor interceptor = new OriginHandshakeInterceptor();
		interceptor.setAllowedOrigins(Arrays.asList("http://mydomain1.com", "http://mydomain2.com", "http://mydomain3.com"));
		assertFalse(interceptor.beforeHandshake(request, response, wsHandler, attributes));
		assertEquals(servletResponse.getStatus(), HttpStatus.FORBIDDEN.value());
	}

	@Test
	public void noOriginNoMatchWithNullHostileCollection() throws Exception {
		Map<String, Object> attributes = new HashMap<String, Object>();
		WebSocketHandler wsHandler = Mockito.mock(WebSocketHandler.class);
		Set<String> allowedOrigins = new ConcurrentSkipListSet<String>();
		allowedOrigins.add("http://mydomain1.com");
		OriginHandshakeInterceptor interceptor = new OriginHandshakeInterceptor();
		interceptor.setAllowedOrigins(allowedOrigins);
		assertFalse(interceptor.beforeHandshake(request, response, wsHandler, attributes));
		assertEquals(servletResponse.getStatus(), HttpStatus.FORBIDDEN.value());
	}

	@Test
	 public void noOriginNoMatch() throws Exception {
		Map<String, Object> attributes = new HashMap<String, Object>();
		WebSocketHandler wsHandler = Mockito.mock(WebSocketHandler.class);
		OriginHandshakeInterceptor interceptor = new OriginHandshakeInterceptor();
		assertFalse(interceptor.beforeHandshake(request, response, wsHandler, attributes));
		assertEquals(servletResponse.getStatus(), HttpStatus.FORBIDDEN.value());
	}

}
