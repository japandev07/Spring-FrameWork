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
package org.springframework.web.reactive.socket.server;

import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.hamcrest.Matchers;
import org.junit.Ignore;
import org.junit.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoProcessor;
import reactor.core.publisher.ReplayProcessor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.bootstrap.RxNettyHttpServer;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.HandshakeInfo;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.client.JettyWebSocketClient;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.springframework.web.reactive.socket.client.RxNettyWebSocketClient;
import org.springframework.web.reactive.socket.client.StandardWebSocketClient;
import org.springframework.web.reactive.socket.client.UndertowWebSocketClient;
import org.springframework.web.reactive.socket.client.WebSocketClient;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

/**
 * Integration tests with server-side {@link WebSocketHandler}s.
 * @author Rossen Stoyanchev
 */
@SuppressWarnings({"unused", "WeakerAccess"})
public class WebSocketIntegrationTests extends AbstractWebSocketIntegrationTests {


	@Override
	protected Class<?> getWebConfigClass() {
		return WebConfig.class;
	}


	@Test
	public void echoReactorClient() throws Exception {
		testEcho(new ReactorNettyWebSocketClient());
	}

	@Test
	public void echoRxNettyClient() throws Exception {
		testEcho(new RxNettyWebSocketClient());
	}

	@Test
	public void echoJettyClient() throws Exception {
		JettyWebSocketClient client = new JettyWebSocketClient();
		client.start();
		testEcho(client);
		client.stop();
	}

	@Test
	public void echoStandardClient() throws Exception {
		testEcho(new StandardWebSocketClient());
	}

	@Test
	public void echoUndertowClient() throws Exception {
		if (server instanceof RxNettyHttpServer) {
			// Caused by: java.io.IOException: Upgrade responses cannot have a transfer coding
			// at org.xnio.http.HttpUpgrade$HttpUpgradeState.handleUpgrade(HttpUpgrade.java:490)
			// at org.xnio.http.HttpUpgrade$HttpUpgradeState.access$1200(HttpUpgrade.java:165)
			// at org.xnio.http.HttpUpgrade$HttpUpgradeState$UpgradeResultListener.handleEvent(HttpUpgrade.java:461)
			// at org.xnio.http.HttpUpgrade$HttpUpgradeState$UpgradeResultListener.handleEvent(HttpUpgrade.java:400)
			// at org.xnio.ChannelListeners.invokeChannelListener(ChannelListeners.java:92)

			return;
		}
		testEcho(new UndertowWebSocketClient());
	}

	private void testEcho(WebSocketClient client) throws URISyntaxException {
		int count = 100;
		Flux<String> input = Flux.range(1, count).map(index -> "msg-" + index);
		ReplayProcessor<Object> output = ReplayProcessor.create(count);

		client.execute(getUrl("/echo"),
				session -> session
						.send(input.map(session::textMessage))
						.thenMany(session.receive().take(count).map(WebSocketMessage::getPayloadAsText))
						.subscribeWith(output)
						.then())
				.blockMillis(5000);

		assertEquals(input.collectList().blockMillis(5000), output.collectList().blockMillis(5000));
	}

	@Test
	@Ignore("https://github.com/reactor/reactor-netty/issues/20")
	public void subProtocolReactorClient() throws Exception {
		testSubProtocol(new ReactorNettyWebSocketClient());
	}

	@Test
	public void subProtocolRxNettyClient() throws Exception {
		testSubProtocol(new RxNettyWebSocketClient());
	}

	@Test
	public void subProtocolJettyClient() throws Exception {
		JettyWebSocketClient client = new JettyWebSocketClient();
		client.start();
		testSubProtocol(client);
		client.stop();
	}

	@Test
	public void subProtocolStandardClient() throws Exception {
		testSubProtocol(new StandardWebSocketClient());
	}

	@Test
	public void subProtocolUndertowClient() throws Exception {
		if (server instanceof RxNettyHttpServer) {
			// Caused by: java.io.IOException: Upgrade responses cannot have a transfer coding
			// at org.xnio.http.HttpUpgrade$HttpUpgradeState.handleUpgrade(HttpUpgrade.java:490)
			// at org.xnio.http.HttpUpgrade$HttpUpgradeState.access$1200(HttpUpgrade.java:165)
			// at org.xnio.http.HttpUpgrade$HttpUpgradeState$UpgradeResultListener.handleEvent(HttpUpgrade.java:461)
			// at org.xnio.http.HttpUpgrade$HttpUpgradeState$UpgradeResultListener.handleEvent(HttpUpgrade.java:400)
			// at org.xnio.ChannelListeners.invokeChannelListener(ChannelListeners.java:92)

			return;
		}
		testSubProtocol(new UndertowWebSocketClient());
	}

	private void testSubProtocol(WebSocketClient client) throws URISyntaxException {
		String protocol = "echo-v1";
		AtomicReference<HandshakeInfo> infoRef = new AtomicReference<>();
		MonoProcessor<Object> output = MonoProcessor.create();

		client.execute(getUrl("/sub-protocol"),
				new WebSocketHandler() {

					@Override
					public String[] getSubProtocols() {
						return new String[] {protocol};
					}

					@Override
					public Mono<Void> handle(WebSocketSession session) {
						infoRef.set(session.getHandshakeInfo());
						return session.receive()
								.map(WebSocketMessage::getPayloadAsText)
								.subscribeWith(output)
								.then();
					}
				})
				.blockMillis(5000);

		HandshakeInfo info = infoRef.get();
		assertThat(info.getHeaders().getFirst("Upgrade"), Matchers.equalToIgnoringCase("websocket"));
		assertEquals(protocol, info.getHeaders().getFirst("Sec-WebSocket-Protocol"));
		assertEquals("Wrong protocol accepted", protocol, info.getSubProtocol().orElse("none"));
		assertEquals("Wrong protocol detected on the server side", protocol, output.blockMillis(5000));
	}

	@Test
	public void customHeaderReactorClient() throws Exception {
		testCustomHeader(new ReactorNettyWebSocketClient());
	}

	@Test
	public void customHeaderRxNettyClient() throws Exception {
		testCustomHeader(new RxNettyWebSocketClient());
	}

	private void testCustomHeader(WebSocketClient client) throws Exception {

		HttpHeaders headers = new HttpHeaders();
		headers.add("my-header", "my-value");
		MonoProcessor<Object> output = MonoProcessor.create();

		client.execute(getUrl("/custom-header"), headers,
				session -> session.receive()
						.map(WebSocketMessage::getPayloadAsText)
						.subscribeWith(output)
						.then())
				.blockMillis(5000);

		assertEquals("my-header:my-value", output.blockMillis(5000));
	}


	@Configuration
	static class WebConfig {

		@Bean
		public HandlerMapping handlerMapping() {

			Map<String, WebSocketHandler> map = new HashMap<>();
			map.put("/echo", new EchoWebSocketHandler());
			map.put("/sub-protocol", new SubProtocolWebSocketHandler());
			map.put("/custom-header", new CustomHeaderHandler());

			SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
			mapping.setUrlMap(map);
			return mapping;
		}

	}

	private static class EchoWebSocketHandler implements WebSocketHandler {

		@Override
		public Mono<Void> handle(WebSocketSession session) {
			// Use retain() for Reactor Netty
			return session.send(session.receive().doOnNext(WebSocketMessage::retain));
		}
	}

	private static class SubProtocolWebSocketHandler implements WebSocketHandler {

		@Override
		public String[] getSubProtocols() {
			return new String[] {"echo-v1"};
		}

		@Override
		public Mono<Void> handle(WebSocketSession session) {
			String protocol = session.getHandshakeInfo().getSubProtocol().orElse("none");
			WebSocketMessage message = session.textMessage(protocol);
			return doSend(session, Mono.just(message));
		}
	}

	private static class CustomHeaderHandler implements WebSocketHandler {

		@Override
		public Mono<Void> handle(WebSocketSession session) {
			HttpHeaders headers = session.getHandshakeInfo().getHeaders();
			String payload = "my-header:" + headers.getFirst("my-header");
			WebSocketMessage message = session.textMessage(payload);
			return doSend(session, Mono.just(message));
		}
	}

	// TODO: workaround for suspected RxNetty WebSocket client issue
	// https://github.com/ReactiveX/RxNetty/issues/560

	private static Mono<Void> doSend(WebSocketSession session, Publisher<WebSocketMessage> output) {
		return session.send(Mono.delayMillis(100).thenMany(output));
	}

}
