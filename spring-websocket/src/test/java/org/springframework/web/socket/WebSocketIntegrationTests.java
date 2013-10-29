/*
 * Copyright 2002-2013 the original author or authors.
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

package org.springframework.web.socket;


import java.net.URI;
import java.util.Arrays;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.adapter.TextWebSocketHandlerAdapter;
import org.springframework.web.socket.adapter.WebSocketHandlerAdapter;
import org.springframework.web.socket.client.endpoint.StandardWebSocketClient;
import org.springframework.web.socket.client.jetty.JettyWebSocketClient;
import org.springframework.web.socket.server.DefaultHandshakeHandler;
import org.springframework.web.socket.server.config.EnableWebSocket;
import org.springframework.web.socket.server.config.WebSocketConfigurer;
import org.springframework.web.socket.server.config.WebSocketHandlerRegistry;
import org.springframework.web.socket.support.WebSocketHttpHeaders;

import static org.junit.Assert.*;

/**
 * Client and server-side WebSocket integration tests.
 *
 * @author Rossen Stoyanchev
 */
@RunWith(Parameterized.class)
public class WebSocketIntegrationTests extends  AbstractWebSocketIntegrationTests {

	@Parameterized.Parameters
	public static Iterable<Object[]> arguments() {
		return Arrays.asList(new Object[][]{
				{new JettyWebSocketTestServer(), new JettyWebSocketClient()},
				{new TomcatWebSocketTestServer(), new StandardWebSocketClient()}
		});
	};


	@Override
	protected Class<?>[] getAnnotatedConfigClasses() {
		return new Class<?>[] { TestWebSocketConfigurer.class };
	}

	@Test
	public void subProtocolNegotiation() throws Exception {

		WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
		headers.setSecWebSocketProtocol("foo");

		WebSocketSession session = this.webSocketClient.doHandshake(
				new WebSocketHandlerAdapter(), headers, new URI(getWsBaseUrl() + "/ws")).get();

		assertEquals("foo", session.getAcceptedProtocol());
	}


	@Configuration
	@EnableWebSocket
	static class TestWebSocketConfigurer implements WebSocketConfigurer {

		@Autowired
		private DefaultHandshakeHandler handshakeHandler; // can't rely on classpath for server detection

		@Override
		public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
			this.handshakeHandler.setSupportedProtocols("foo", "bar", "baz");
			registry.addHandler(serverHandler(), "/ws").setHandshakeHandler(this.handshakeHandler);
		}

		@Bean
		public TestServerWebSocketHandler serverHandler() {
			return new TestServerWebSocketHandler();
		}
	}

	private static class TestServerWebSocketHandler extends TextWebSocketHandlerAdapter {
	}

}
