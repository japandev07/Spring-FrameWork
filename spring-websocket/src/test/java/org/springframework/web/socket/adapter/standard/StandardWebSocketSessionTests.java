/* Copyright 2002-2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.web.socket.adapter.standard;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpHeaders;
import org.springframework.web.socket.handler.TestPrincipal;

import javax.websocket.Session;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link org.springframework.web.socket.adapter.standard.StandardWebSocketSession}.
 *
 * @author Rossen Stoyanchev
 */
public class StandardWebSocketSessionTests {

	private HttpHeaders headers;

	private Map<String,Object> attributes;


	@Before
	public void setup() {
		this.headers = new HttpHeaders();
		this.attributes = new HashMap<>();
	}


	@Test
	public void getPrincipalWithConstructorArg() {
		TestPrincipal user = new TestPrincipal("joe");
		StandardWebSocketSession session = new StandardWebSocketSession(this.headers, this.attributes, null, null, user);

		assertSame(user, session.getPrincipal());
	}

	@Test
	public void getPrincipalWithNativeSession() {

		TestPrincipal user = new TestPrincipal("joe");

		Session nativeSession = Mockito.mock(Session.class);
		when(nativeSession.getUserPrincipal()).thenReturn(user);

		StandardWebSocketSession session = new StandardWebSocketSession(this.headers, this.attributes, null, null);
		session.initializeNativeSession(nativeSession);

		assertSame(user, session.getPrincipal());
	}

	@Test
	public void getPrincipalNone() {

		Session nativeSession = Mockito.mock(Session.class);
		when(nativeSession.getUserPrincipal()).thenReturn(null);

		StandardWebSocketSession session = new StandardWebSocketSession(this.headers, this.attributes, null, null);
		session.initializeNativeSession(nativeSession);

		reset(nativeSession);

		assertNull(session.getPrincipal());
		verify(nativeSession).isOpen();
		verifyNoMoreInteractions(nativeSession);
	}

	@Test
	public void getAcceptedProtocol() {

		String protocol = "foo";

		Session nativeSession = Mockito.mock(Session.class);
		when(nativeSession.getNegotiatedSubprotocol()).thenReturn(protocol);

		StandardWebSocketSession session = new StandardWebSocketSession(this.headers, this.attributes, null, null);
		session.initializeNativeSession(nativeSession);

		reset(nativeSession);

		assertEquals(protocol, session.getAcceptedProtocol());
		verifyNoMoreInteractions(nativeSession);
	}

}
