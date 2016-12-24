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

package org.springframework.web.reactive.socket.client;

import java.net.URI;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.UpgradeRequest;
import org.eclipse.jetty.websocket.api.UpgradeResponse;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.io.UpgradeListener;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoProcessor;

import org.springframework.context.Lifecycle;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.socket.HandshakeInfo;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.adapter.JettyWebSocketHandlerAdapter;
import org.springframework.web.reactive.socket.adapter.JettyWebSocketSession;

/**
 * Jetty based implementation of {@link WebSocketClient}.
 * 
 * @author Violeta Georgieva
 * @author Rossen Stoyanchev
 * @since 5.0
 */
public class JettyWebSocketClient extends WebSocketClientSupport implements WebSocketClient, Lifecycle {

	private final org.eclipse.jetty.websocket.client.WebSocketClient jettyClient;

	private final DataBufferFactory bufferFactory = new DefaultDataBufferFactory();

	private final Object lifecycleMonitor = new Object();


	/**
	 * Default constructor that creates an instance of
	 * {@link org.eclipse.jetty.websocket.client.WebSocketClient}.
	 */
	public JettyWebSocketClient() {
		this(new org.eclipse.jetty.websocket.client.WebSocketClient());
	}

	/**
	 * Constructor that accepts an existing
	 * {@link org.eclipse.jetty.websocket.client.WebSocketClient} instance.
	 * @param jettyClient a web socket client
	 */
	public JettyWebSocketClient(org.eclipse.jetty.websocket.client.WebSocketClient jettyClient) {
		this.jettyClient = jettyClient;
	}


	@Override
	public void start() {
		synchronized (this.lifecycleMonitor) {
			if (!isRunning()) {
				try {
					this.jettyClient.start();
				}
				catch (Exception ex) {
					throw new IllegalStateException("Failed to start Jetty WebSocketClient", ex);
				}
			}
		}
	}

	@Override
	public void stop() {
		synchronized (this.lifecycleMonitor) {
			if (isRunning()) {
				try {
					this.jettyClient.stop();
				}
				catch (Exception ex) {
					throw new IllegalStateException("Error stopping Jetty WebSocketClient", ex);
				}
			}
		}
	}

	@Override
	public boolean isRunning() {
		synchronized (this.lifecycleMonitor) {
			return this.jettyClient.isStarted();
		}
	}


	@Override
	public Mono<Void> execute(URI url, WebSocketHandler handler) {
		return execute(url, new HttpHeaders(), handler);
	}

	@Override
	public Mono<Void> execute(URI url, HttpHeaders headers, WebSocketHandler handler) {
		return executeInternal(url, headers, handler);
	}

	private Mono<Void> executeInternal(URI url, HttpHeaders headers, WebSocketHandler handler) {
		MonoProcessor<Void> completionMono = MonoProcessor.create();
		return Mono.fromCallable(
				() -> {
					String[] protocols = beforeHandshake(url, headers, handler);
					ClientUpgradeRequest upgradeRequest = new ClientUpgradeRequest();
					upgradeRequest.setSubProtocols(protocols);
					Object jettyHandler = createJettyHandler(url, handler, completionMono);
					UpgradeListener upgradeListener = new DefaultUpgradeListener(headers);
					return this.jettyClient.connect(jettyHandler, url, upgradeRequest, upgradeListener);
				})
				.then(completionMono);
	}

	private Object createJettyHandler(URI url, WebSocketHandler handler, MonoProcessor<Void> completion) {
		return new JettyWebSocketHandlerAdapter(handler,
				session -> createJettySession(url, completion, session));
	}

	private JettyWebSocketSession createJettySession(URI url, MonoProcessor<Void> completion, Session session) {
		UpgradeResponse response = session.getUpgradeResponse();
		HttpHeaders responseHeaders = new HttpHeaders();
		response.getHeaders().forEach(responseHeaders::put);
		HandshakeInfo info = afterHandshake(url, responseHeaders);
		return new JettyWebSocketSession(session, info, this.bufferFactory, completion);
	}


	private static class DefaultUpgradeListener implements UpgradeListener {

		private final HttpHeaders headers;


		public DefaultUpgradeListener(HttpHeaders headers) {
			this.headers = headers;
		}

		@Override
		public void onHandshakeRequest(UpgradeRequest request) {
			this.headers.forEach(request::setHeader);
		}

		@Override
		public void onHandshakeResponse(UpgradeResponse response) {
		}
	}

}