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

package org.springframework.web.socket.sockjs;

import java.io.EOFException;
import java.io.IOException;
import java.net.SocketException;
import java.net.URI;
import java.security.Principal;
import java.util.Date;
import java.util.concurrent.ScheduledFuture;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.util.Assert;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.adapter.ConfigurableWebSocketSession;

/**
 * An abstract base class SockJS sessions implementing {@link WebSocketSession}.
 *
 * @author Rossen Stoyanchev
 * @since 4.0
 */
public abstract class AbstractSockJsSession implements ConfigurableWebSocketSession {

	protected final Log logger = LogFactory.getLog(getClass());


	private final String id;

	private URI uri;

	private String remoteHostName;

	private String remoteAddress;

	private Principal principal;

	private final SockJsConfiguration sockJsConfig;

	private final WebSocketHandler handler;

	private State state = State.NEW;

	private final long timeCreated = System.currentTimeMillis();

	private long timeLastActive = this.timeCreated;

	private ScheduledFuture<?> heartbeatTask;


	/**
	 * @param sessionId the session ID
	 * @param config the sockJS configuration
	 * @param webSocketHandler the recipient of SockJS messages
	 */
	public AbstractSockJsSession(String sessionId, SockJsConfiguration config, WebSocketHandler webSocketHandler) {
		Assert.notNull(sessionId, "sessionId is required");
		Assert.notNull(config, "sockJsConfig is required");
		Assert.notNull(webSocketHandler, "webSocketHandler is required");
		this.id = sessionId;
		this.sockJsConfig = config;
		this.handler = webSocketHandler;
	}

	@Override
	public String getId() {
		return this.id;
	}

	@Override
	public URI getUri() {
		return this.uri;
	}

	@Override
	public void setUri(URI uri) {
		this.uri = uri;
	}

	@Override
	public boolean isSecure() {
		return "wss".equals(this.uri.getSchemeSpecificPart());
	}

	@Override
	public String getRemoteHostName() {
		return this.remoteHostName;
	}

	@Override
	public void setRemoteHostName(String remoteHostName) {
		this.remoteHostName = remoteHostName;
	}

	@Override
	public String getRemoteAddress() {
		return this.remoteAddress;
	}

	@Override
	public void setRemoteAddress(String remoteAddress) {
		this.remoteAddress = remoteAddress;
	}

	@Override
	public Principal getPrincipal() {
		return this.principal;
	}

	@Override
	public void setPrincipal(Principal principal) {
		this.principal = principal;
	}

	public SockJsConfiguration getSockJsConfig() {
		return this.sockJsConfig;
	}

	public boolean isNew() {
		return State.NEW.equals(this.state);
	}

	@Override
	public boolean isOpen() {
		return State.OPEN.equals(this.state);
	}

	public boolean isClosed() {
		return State.CLOSED.equals(this.state);
	}

	/**
	 * Polling and Streaming sessions periodically close the current HTTP request and
	 * wait for the next request to come through. During this "downtime" the session is
	 * still open but inactive and unable to send messages and therefore has to buffer
	 * them temporarily. A WebSocket session by contrast is stateful and remain active
	 * until closed.
	 */
	public abstract boolean isActive();

	/**
	 * Return the time since the session was last active, or otherwise if the
	 * session is new, the time since the session was created.
	 */
	public long getTimeSinceLastActive() {
		if (isNew()) {
			return (System.currentTimeMillis() - this.timeCreated);
		}
		else {
			return isActive() ? 0 : System.currentTimeMillis() - this.timeLastActive;
		}
	}

	/**
	 * Should be invoked whenever the session becomes inactive.
	 */
	protected void updateLastActiveTime() {
		this.timeLastActive = System.currentTimeMillis();
	}

	public void delegateConnectionEstablished() throws Exception {
		this.state = State.OPEN;
		this.handler.afterConnectionEstablished(this);
	}

	public void delegateMessages(String[] messages) throws Exception {
		for (String message : messages) {
			this.handler.handleMessage(this, new TextMessage(message));
		}
	}

	/**
	 * Invoked in reaction to the underlying connection being closed by the remote side
	 * (or the WebSocket container) in order to perform cleanup and notify the
	 * {@link WebSocketHandler}. This is in contrast to {@link #close()} that pro-actively
	 * closes the connection.
	 */
	public final void delegateConnectionClosed(CloseStatus status) throws Exception {
		if (!isClosed()) {
			if (logger.isDebugEnabled()) {
				logger.debug(this + " was closed, " + status);
			}
			try {
				updateLastActiveTime();
				cancelHeartbeat();
			}
			finally {
				this.state = State.CLOSED;
				this.handler.afterConnectionClosed(this, status);
			}
		}
	}

	public void delegateError(Throwable ex) throws Exception {
		this.handler.handleTransportError(this, ex);
	}

	public final synchronized void sendMessage(WebSocketMessage message) throws IOException {
		Assert.isTrue(!isClosed(), "Cannot send a message when session is closed");
		Assert.isInstanceOf(TextMessage.class, message, "Expected text message: " + message);
		sendMessageInternal(((TextMessage) message).getPayload());
	}

	protected abstract void sendMessageInternal(String message) throws IOException;

	/**
	 * {@inheritDoc}
	 *
	 * <p>Performs cleanup and notifies the {@link WebSocketHandler}.
	 */
	@Override
	public final void close() throws IOException {
		close(new CloseStatus(3000, "Go away!"));
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>Performs cleanup and notifies the {@link WebSocketHandler}.
	 */
	@Override
	public final void close(CloseStatus status) throws IOException {
		if (isOpen()) {
			if (logger.isDebugEnabled()) {
				logger.debug("Closing " + this + ", " + status);
			}
			try {
				if (isActive()) {
					// TODO: deliver messages "in flight" before sending close frame
					try {
						// bypass writeFrame
						writeFrameInternal(SockJsFrame.closeFrame(status.getCode(), status.getReason()));
					}
					catch (Throwable ex) {
						logger.warn("Failed to send SockJS close frame: " + ex.getMessage());
					}
				}
				updateLastActiveTime();
				cancelHeartbeat();
				disconnect(status);
			}
			finally {
				this.state = State.CLOSED;
				try {
					this.handler.afterConnectionClosed(this, status);
				}
				catch (Throwable t) {
					logger.error("Unhandled error for " + this, t);
				}
			}
		}
	}

	protected abstract void disconnect(CloseStatus status) throws IOException;

	/**
	 * Close due to error arising from SockJS transport handling.
	 */
	protected void tryCloseWithSockJsTransportError(Throwable ex, CloseStatus closeStatus) {
		logger.error("Closing due to transport error for " + this, ex);
		try {
			delegateError(ex);
		}
		catch (Throwable delegateEx) {
			logger.error("Unhandled error for " + this, delegateEx);
			try {
				close(closeStatus);
			}
			catch (Throwable closeEx) {
				logger.error("Unhandled error for " + this, closeEx);
			}
		}
	}

	/**
	 * For internal use within a TransportHandler and the (TransportHandler-specific)
	 * session sub-class.
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
				logger.warn("Terminating connection due to failure to send message: " + ex.getMessage());
			}
			disconnect(CloseStatus.SERVER_ERROR);
			close(CloseStatus.SERVER_ERROR);
			throw ex;
		}
		catch (Throwable ex) {
			logger.warn("Terminating connection due to failure to send message: " + ex.getMessage());
			disconnect(CloseStatus.SERVER_ERROR);
			close(CloseStatus.SERVER_ERROR);
			throw new TransportErrorException("Failed to write " + frame, ex, this.getId());
		}
	}

	protected abstract void writeFrameInternal(SockJsFrame frame) throws Exception;

	public synchronized void sendHeartbeat() throws Exception {
		if (isActive()) {
			writeFrame(SockJsFrame.heartbeatFrame());
			scheduleHeartbeat();
		}
	}

	protected void scheduleHeartbeat() {
		Assert.state(this.sockJsConfig.getTaskScheduler() != null, "heartbeatScheduler not configured");
		cancelHeartbeat();
		if (!isActive()) {
			return;
		}
		Date time = new Date(System.currentTimeMillis() + this.sockJsConfig.getHeartbeatTime());
		this.heartbeatTask = this.sockJsConfig.getTaskScheduler().schedule(new Runnable() {
			public void run() {
				try {
					sendHeartbeat();
				}
				catch (Throwable t) {
					// ignore
				}
			}
		}, time);
		if (logger.isTraceEnabled()) {
			logger.trace("Scheduled heartbeat after " + this.sockJsConfig.getHeartbeatTime()/1000 + " seconds");
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


	@Override
	public String toString() {
		return "SockJS session id=" + this.id;
	}


	private enum State { NEW, OPEN, CLOSED }

}
