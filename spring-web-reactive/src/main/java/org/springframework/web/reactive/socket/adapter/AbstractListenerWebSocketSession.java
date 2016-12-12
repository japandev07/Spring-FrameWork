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

package org.springframework.web.reactive.socket.adapter;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.atomic.AtomicBoolean;

import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.http.server.reactive.AbstractListenerReadPublisher;
import org.springframework.http.server.reactive.AbstractListenerWriteProcessor;
import org.springframework.util.Assert;
import org.springframework.web.reactive.socket.CloseStatus;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketMessage.Type;
import org.springframework.web.reactive.socket.WebSocketSession;

/**
 * Base class for Listener-based {@link WebSocketSession} adapters.
 *
 * @author Violeta Georgieva
 * @author Rossen Stoyanchev
 * @since 5.0
 */
public abstract class AbstractListenerWebSocketSession<T> extends WebSocketSessionSupport<T> {

	/**
	 * The "back-pressure" buffer size to use if the underlying WebSocket API
	 * does not have flow control for receiving messages.
	 */
	private static final int RECEIVE_BUFFER_SIZE = 8192;


	private final String id;

	private final URI uri;

	private final WebSocketReceivePublisher receivePublisher = new WebSocketReceivePublisher();

	private volatile WebSocketSendProcessor sendProcessor;

	private final AtomicBoolean sendCalled = new AtomicBoolean();


	public AbstractListenerWebSocketSession(T delegate, String id, URI uri) {
		super(delegate);
		Assert.notNull(id, "'id' is required.");
		Assert.notNull(uri, "'uri' is required.");
		this.id = id;
		this.uri = uri;
	}


	@Override
	public String getId() {
		return this.id;
	}

	@Override
	public URI getUri() {
		return this.uri;
	}

	protected WebSocketSendProcessor getSendProcessor() {
		return this.sendProcessor;
	}

	@Override
	public Flux<WebSocketMessage> receive() {
		return canSuspendReceiving() ?
				Flux.from(this.receivePublisher) :
				Flux.from(this.receivePublisher).onBackpressureBuffer(RECEIVE_BUFFER_SIZE);
	}

	@Override
	public Mono<Void> send(Publisher<WebSocketMessage> messages) {
		if (this.sendCalled.compareAndSet(false, true)) {
			this.sendProcessor = new WebSocketSendProcessor();
			return Mono.from(subscriber -> {
					messages.subscribe(this.sendProcessor);
					this.sendProcessor.subscribe(subscriber);
			});
		}
		else {
			return Mono.error(new IllegalStateException("send() has already been called"));
		}
	}

	/**
	 * Whether the underlying WebSocket API has flow control and can suspend and
	 * resume the receiving of messages.
	 */
	protected abstract boolean canSuspendReceiving();

	/**
	 * Suspend receiving until received message(s) are processed and more demand
	 * is generated by the downstream Subscriber.
	 * <p><strong>Note:</strong> if the underlying WebSocket API does not provide
	 * flow control for receiving messages, and this method should be a no-op
	 * and {@link #canSuspendReceiving()} should return {@code false}.
	 */
	protected abstract void suspendReceiving();

	/**
	 * Resume receiving new message(s) after demand is generated by the
	 * downstream Subscriber.
	 * <p><strong>Note:</strong> if the underlying WebSocket API does not provide
	 * flow control for receiving messages, and this method should be a no-op
	 * and {@link #canSuspendReceiving()} should return {@code false}.
	 */
	protected abstract void resumeReceiving();

	/**
	 * Send the given WebSocket message.
	 */
	protected abstract boolean sendMessage(WebSocketMessage message) throws IOException;


	// WebSocketHandler adapter delegate methods

	/** Handle a message callback from the WebSocketHandler adapter */
	void handleMessage(Type type, WebSocketMessage message) {
		this.receivePublisher.handleMessage(message);
	}

	/** Handle an error callback from the WebSocketHandler adapter */
	void handleError(Throwable ex) {
		this.receivePublisher.onError(ex);
		if (this.sendProcessor != null) {
			this.sendProcessor.cancel();
			this.sendProcessor.onError(ex);
		}
	}

	/** Handle a close callback from the WebSocketHandler adapter */
	void handleClose(CloseStatus reason) {
		this.receivePublisher.onAllDataRead();
		if (this.sendProcessor != null) {
			this.sendProcessor.cancel();
			this.sendProcessor.onComplete();
		}
	}


	private final class WebSocketReceivePublisher extends AbstractListenerReadPublisher<WebSocketMessage> {

		private volatile WebSocketMessage webSocketMessage;

		@Override
		protected void checkOnDataAvailable() {
			if (this.webSocketMessage != null) {
				onDataAvailable();
			}
		}

		@Override
		protected WebSocketMessage read() throws IOException {
			if (this.webSocketMessage != null) {
				WebSocketMessage result = this.webSocketMessage;
				this.webSocketMessage = null;
				resumeReceiving();
				return result;
			}

			return null;
		}

		void handleMessage(WebSocketMessage webSocketMessage) {
			this.webSocketMessage = webSocketMessage;
			suspendReceiving();
			onDataAvailable();
		}
	}

	protected final class WebSocketSendProcessor extends AbstractListenerWriteProcessor<WebSocketMessage> {

		private volatile boolean isReady = true;

		@Override
		protected boolean write(WebSocketMessage message) throws IOException {
			return sendMessage(message);
		}

		@Override
		protected void releaseData() {
			if (logger.isTraceEnabled()) {
				logger.trace("releaseData: " + this.currentData);
			}
			this.currentData = null;
		}

		@Override
		protected boolean isDataEmpty(WebSocketMessage message) {
			return message.getPayload().readableByteCount() == 0;
		}

		@Override
		protected boolean isWritePossible() {
			return this.isReady && this.currentData != null;
		}

		/**
		 * Sub-classes can invoke this before sending a message (false) and
		 * after receiving the async send callback (true) effective translating
		 * async completion callback into simple flow control.
		 */
		public void setReadyToSend(boolean ready) {
			this.isReady = ready;
		}
	}

}