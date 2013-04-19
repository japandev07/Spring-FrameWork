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

package org.springframework.sockjs;

import java.io.IOException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.util.Assert;
import org.springframework.websocket.CloseStatus;


/**
 * TODO
 *
 * @author Rossen Stoyanchev
 * @since 4.0
 */
public abstract class SockJsSessionSupport implements SockJsSession {

	protected Log logger = LogFactory.getLog(this.getClass());

	private final String sessionId;

	private final SockJsHandler sockJsHandler;

	private State state = State.NEW;

	private long timeCreated = System.currentTimeMillis();

	private long timeLastActive = System.currentTimeMillis();


	/**
	 *
	 * @param sessionId
	 * @param sockJsHandler the recipient of SockJS messages
	 */
	public SockJsSessionSupport(String sessionId, SockJsHandler sockJsHandler) {
		Assert.notNull(sessionId, "sessionId is required");
		Assert.notNull(sockJsHandler, "sockJsHandler is required");
		this.sessionId = sessionId;
		this.sockJsHandler = sockJsHandler;
	}

	public String getId() {
		return this.sessionId;
	}

	public boolean isNew() {
		return State.NEW.equals(this.state);
	}

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
		this.sockJsHandler.afterConnectionEstablished(this);
	}

	public void delegateMessages(String[] messages) throws Exception {
		for (String message : messages) {
			this.sockJsHandler.handleMessage(message, this);
		}
	}

	public void delegateError(Throwable ex) {
		this.sockJsHandler.handleError(ex, this);
	}

	/**
	 * Invoked in reaction to the underlying connection being closed by the remote side
	 * (or the WebSocket container) in order to perform cleanup and notify the
	 * {@link SockJsHandler}. This is in contrast to {@link #close()} that pro-actively
	 * closes the connection.
	 */
	public final void delegateConnectionClosed(CloseStatus status) {
		if (!isClosed()) {
			if (logger.isDebugEnabled()) {
				logger.debug(this + " was closed, " + status);
			}
			try {
				connectionClosedInternal(status);
			}
			finally {
				this.state = State.CLOSED;
				this.sockJsHandler.afterConnectionClosed(status, this);
			}
		}
	}

	protected void connectionClosedInternal(CloseStatus status) {
	}

	/**
	 * {@inheritDoc}
	 * <p>Performs cleanup and notifies the {@link SockJsHandler}.
	 */
	public final void close() throws IOException {
		close(CloseStatus.NORMAL);
	}

	/**
	 * {@inheritDoc}
	 * <p>Performs cleanup and notifies the {@link SockJsHandler}.
	 */
	public final void close(CloseStatus status) throws IOException {
		if (!isClosed()) {
			if (logger.isDebugEnabled()) {
				logger.debug("Closing " + this + ", " + status);
			}
			try {
				closeInternal(status);
			}
			finally {
				this.state = State.CLOSED;
				this.sockJsHandler.afterConnectionClosed(status, this);
			}
		}
	}

	protected abstract void closeInternal(CloseStatus status) throws IOException;


	@Override
	public String toString() {
		return "SockJS session id=" + this.sessionId;
	}


	private enum State { NEW, OPEN, CLOSED }

}
