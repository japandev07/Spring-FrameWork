/*
 * Copyright 2002-2013 the original author or authors.
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
package org.springframework.sockjs.server.transport;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import org.springframework.http.server.AsyncServerHttpRequest;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.sockjs.SockJsHandler;
import org.springframework.sockjs.server.AbstractServerSession;
import org.springframework.sockjs.server.SockJsConfiguration;
import org.springframework.sockjs.server.SockJsFrame;
import org.springframework.sockjs.server.TransportHandler;
import org.springframework.sockjs.server.SockJsFrame.FrameFormat;
import org.springframework.util.Assert;

/**
 * An abstract base class for use with HTTP-based transports.
 *
 * @author Rossen Stoyanchev
 * @since 4.0
 */
public abstract class AbstractHttpServerSession extends AbstractServerSession {

	private FrameFormat frameFormat;

	private final BlockingQueue<String> messageCache = new ArrayBlockingQueue<String>(100);

	private AsyncServerHttpRequest asyncRequest;

	private OutputStream outputStream;


	public AbstractHttpServerSession(String sessionId, SockJsHandler delegate, SockJsConfiguration sockJsConfig) {
		super(sessionId, delegate, sockJsConfig);
	}

	public void setFrameFormat(FrameFormat frameFormat) {
		this.frameFormat = frameFormat;
	}

	public synchronized void setCurrentRequest(ServerHttpRequest request, ServerHttpResponse response,
			FrameFormat frameFormat) throws IOException {

		if (isClosed()) {
			logger.debug("connection already closed");
			writeFrame(response.getBody(), SockJsFrame.closeFrameGoAway());
			return;
		}

		Assert.isInstanceOf(AsyncServerHttpRequest.class, request, "Expected AsyncServerHttpRequest");

		this.asyncRequest = (AsyncServerHttpRequest) request;
		this.asyncRequest.setTimeout(-1);
		this.asyncRequest.startAsync();

		this.outputStream = response.getBody();
		this.frameFormat = frameFormat;

		scheduleHeartbeat();
		tryFlush();
	}

	public synchronized boolean isActive() {
		return ((this.asyncRequest != null) && (!this.asyncRequest.isAsyncCompleted()));
	}

	protected BlockingQueue<String> getMessageCache() {
		return this.messageCache;
	}

	protected final synchronized void sendMessageInternal(String message) {
		// assert close() was not called
		// threads: TH-Session-Endpoint or any other thread
		this.messageCache.add(message);
		tryFlush();
	}

	private void tryFlush() {
		if (isActive() && !getMessageCache().isEmpty()) {
			logger.trace("Flushing messages");
			flush();
		}
	}

	/**
	 * Only called if the connection is currently active
	 */
	protected abstract void flush();

	protected void closeInternal() {
		resetRequest();
	}

	protected synchronized void writeFrameInternal(SockJsFrame frame) throws IOException {
		if (isActive()) {
			writeFrame(this.outputStream, frame);
		}
	}

	/**
	 * This method may be called by a {@link TransportHandler} to write a frame
	 * even when the connection is not active, as long as a valid OutputStream
	 * is provided.
	 */
	public void writeFrame(OutputStream outputStream, SockJsFrame frame) throws IOException {
		frame = this.frameFormat.format(frame);
		if (logger.isTraceEnabled()) {
			logger.trace("Writing " + frame);
		}
		outputStream.write(frame.getContentBytes());
	}

	@Override
	protected void deactivate() {
		this.outputStream = null;
		this.asyncRequest = null;
		updateLastActiveTime();
	}

	protected synchronized void resetRequest() {
		if (isActive()) {
			this.asyncRequest.completeAsync();
		}
		this.outputStream = null;
		this.asyncRequest = null;
		updateLastActiveTime();
	}

}
