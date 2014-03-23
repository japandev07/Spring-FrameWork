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

package org.springframework.web.socket.sockjs.transport.session;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;

import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.sockjs.SockJsTransportFailureException;
import org.springframework.web.socket.sockjs.frame.SockJsFrame;
import org.springframework.web.socket.sockjs.frame.SockJsMessageCodec;
import org.springframework.web.socket.sockjs.transport.SockJsServiceConfig;

/**
 * A SockJS session for use with polling HTTP transports.
 *
 * @author Rossen Stoyanchev
 * @since 4.0
 */
public class PollingSockJsSession extends AbstractHttpSockJsSession {

	public PollingSockJsSession(String sessionId, SockJsServiceConfig config,
			WebSocketHandler wsHandler, Map<String, Object> attributes) {

		super(sessionId, config, wsHandler, attributes);
	}


	@Override
	protected void flushCache() throws SockJsTransportFailureException {
		cancelHeartbeat();
		Queue<String> messageCache = getMessageCache();
		String[] messages = messageCache.toArray(new String[messageCache.size()]);
		messageCache.clear();

		SockJsMessageCodec messageCodec = getSockJsServiceConfig().getMessageCodec();
		SockJsFrame frame = SockJsFrame.messageFrame(messageCodec, messages);
		writeFrame(frame);
	}

	@Override
	protected void writeFrame(SockJsFrame frame) throws SockJsTransportFailureException {
		super.writeFrame(frame);
		resetRequest();
	}

}

