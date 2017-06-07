/*
 * Copyright 2002-2017 the original author or authors.
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

package org.springframework.web.socket.messaging;

import java.security.Principal;

import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;

/**
 * Event raised when a new WebSocket client using a Simple Messaging Protocol
 * (e.g. STOMP) sends a request to remove a subscription.
 *
 * @author Rossen Stoyanchev
 * @since 4.0.3
 */
@SuppressWarnings("serial")
public class SessionUnsubscribeEvent extends AbstractSubProtocolEvent {

	public SessionUnsubscribeEvent(Object source, Message<byte[]> message) {
		super(source, message);
	}

	public SessionUnsubscribeEvent(Object source, Message<byte[]> message, @Nullable Principal user) {
		super(source, message, user);
	}

}
