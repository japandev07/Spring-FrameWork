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

package org.springframework.sockjs.server;

import java.io.EOFException;
import java.io.IOException;
import java.net.SocketException;
import java.util.Date;
import java.util.concurrent.ScheduledFuture;

import org.springframework.sockjs.SockJsHandler;
import org.springframework.sockjs.SockJsSession;
import org.springframework.sockjs.SockJsSessionSupport;
import org.springframework.util.Assert;


/**
 * Provides partial implementations of {@link SockJsSession} methods to send messages,
 * including heartbeat messages and to manage session state.
 *
 * @author Rossen Stoyanchev
 * @since 4.0
 */
public abstract class AbstractServerSession extends SockJsSessionSupport {

	private final SockJsConfiguration sockJsConfig;

	private ScheduledFuture<?> heartbeatTask;


	public AbstractServerSession(String sessionId, SockJsConfiguration sockJsConfig) {
		super(sessionId, getSockJsHandler(sockJsConfig));
		this.sockJsConfig = sockJsConfig;
	}

	private static SockJsHandler getSockJsHandler(SockJsConfiguration sockJsConfig) {
		Assert.notNull(sockJsConfig, "sockJsConfig is required");
		return sockJsConfig.getSockJsHandler();
	}

	protected SockJsConfiguration getSockJsConfig() {
		return this.sockJsConfig;
	}

	public final synchronized void sendMessage(String message) throws IOException {
		Assert.isTrue(!isClosed(), "Cannot send a message, session has been closed");
		sendMessageInternal(message);
	}

	protected abstract void sendMessageInternal(String message) throws IOException;

	public final synchronized void close() {
		if (!isClosed()) {
			logger.debug("Closing session");

			if (isActive()) {
				// deliver messages "in flight" before sending close frame
				try {
					writeFrame(SockJsFrame.closeFrameGoAway());
				}
				catch (Exception e) {
					// ignore
				}
			}

			super.close();

			cancelHeartbeat();
			closeInternal();
		}
	}

	protected abstract void closeInternal();

	/**
	 * For internal use within a TransportHandler and the (TransportHandler-specific)
	 * session sub-class. The frame is written only if the connection is active.
	 */
	protected void writeFrame(SockJsFrame frame) throws IOException {
		if (logger.isTraceEnabled()) {
			logger.trace("Preparing to write " + frame);
		}
		try {
			writeFrameInternal(frame);
		}
		catch (IOException ex) {
			if (ex instanceof EOFException || ex instanceof SocketException) {
				logger.warn("Client went away. Terminating connection");
			}
			else {
				logger.warn("Failed to send message. Terminating connection: " + ex.getMessage());
			}
			deactivate();
			close();
			throw ex;
		}
		catch (Throwable t) {
			logger.warn("Failed to send message. Terminating connection: " + t.getMessage());
			deactivate();
			close();
			throw new NestedSockJsRuntimeException("Failed to write frame " + frame, t);
		}
	}

	protected abstract void writeFrameInternal(SockJsFrame frame) throws IOException;

	/**
	 * Some {@link TransportHandler} types cannot detect if a client connection is closed
	 * or lost and will eventually fail to send messages. When that happens, we need a way
	 * to disconnect the underlying connection before calling {@link #close()}.
	 */
	protected abstract void deactivate();

	public synchronized void sendHeartbeat() throws IOException {
		if (isActive()) {
			writeFrame(SockJsFrame.heartbeatFrame());
			scheduleHeartbeat();
		}
	}

	protected void scheduleHeartbeat() {
		Assert.notNull(getSockJsConfig().getHeartbeatScheduler(), "heartbeatScheduler not configured");
		cancelHeartbeat();
		if (!isActive()) {
			return;
		}
		Date time = new Date(System.currentTimeMillis() + getSockJsConfig().getHeartbeatTime());
		this.heartbeatTask = getSockJsConfig().getHeartbeatScheduler().schedule(new Runnable() {
			public void run() {
				try {
					sendHeartbeat();
				}
				catch (IOException e) {
					// ignore
				}
			}
		}, time);
		if (logger.isTraceEnabled()) {
			logger.trace("Scheduled heartbeat after " + getSockJsConfig().getHeartbeatTime()/1000 + " seconds");
		}
	}

	protected void cancelHeartbeat() {
		if ((this.heartbeatTask != null) && !this.heartbeatTask.isDone()) {
			if (logger.isTraceEnabled()) {
				logger.trace("Cancelling heartbeat");
			}
			this.heartbeatTask.cancel(false);
		}
		this.heartbeatTask = null;
	}


}
