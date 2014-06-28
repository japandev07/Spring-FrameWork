/*
 * Copyright 2002-2014 the original author or authors.
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

package org.springframework.web.socket.messaging;


import org.springframework.context.ApplicationEvent;
import org.springframework.messaging.Message;
import org.springframework.util.Assert;

/**
 * A base class for events for a message received from a WebSocket client and
 * parsed into a higher level sub-protocol (e.g. STOMP).
 *
 * @author Rossen Stoyanchev
 * @since 4.0.3
 */
@SuppressWarnings("serial")
public abstract class AbstractSubProtocolEvent extends ApplicationEvent {

	private final Message<byte[]> message;


	/**
	 * Create a new SessionConnectEvent.
	 *
	 * @param source the component that published the event (never {@code null})
	 * @param message the connect message
	 */
	protected AbstractSubProtocolEvent(Object source, Message<byte[]> message) {
		super(source);
		Assert.notNull(message, "'message' must not be null");
		this.message = message;
	}

	/**
	 * Return the Message associated with the event. Here is an example of
	 * obtaining information about the session id or any headers in the
	 * message:
	 *
	 * <pre class="code">
	 * StompHeaderAccessor headers = StompHeaderAccessor.wrap(message);
	 * headers.getSessionId();
	 * headers.getSessionAttributes();
	 * headers.getPrincipal();
	 * </pre>
	 *
	 */
	public Message<byte[]> getMessage() {
		return this.message;
	}


	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + this.message + "]";
	}
}
