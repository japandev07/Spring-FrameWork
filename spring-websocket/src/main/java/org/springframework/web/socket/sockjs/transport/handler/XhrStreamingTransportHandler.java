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

package org.springframework.web.socket.sockjs.transport.handler;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.sockjs.support.frame.SockJsFrame.DefaultFrameFormat;
import org.springframework.web.socket.sockjs.support.frame.SockJsFrame.FrameFormat;
import org.springframework.web.socket.sockjs.transport.TransportHandler;
import org.springframework.web.socket.sockjs.transport.TransportType;
import org.springframework.web.socket.sockjs.transport.session.SockJsServiceConfig;
import org.springframework.web.socket.sockjs.transport.session.StreamingSockJsSession;

/**
 * A {@link TransportHandler} that sends messages over an HTTP streaming request.
 *
 * @author Rossen Stoyanchev
 * @since 4.0
 */
public class XhrStreamingTransportHandler extends AbstractHttpSendingTransportHandler {


	@Override
	public TransportType getTransportType() {
		return TransportType.XHR_STREAMING;
	}

	@Override
	protected MediaType getContentType() {
		return new MediaType("application", "javascript", Charset.forName("UTF-8"));
	}

	@Override
	public StreamingSockJsSession createSession(String sessionId, WebSocketHandler wsHandler,
			Map<String, Object> attributes) {

		return new XhrStreamingSockJsSession(sessionId, getSockJsServiceConfig(), wsHandler, attributes);
	}

	@Override
	protected FrameFormat getFrameFormat(ServerHttpRequest request) {
		return new DefaultFrameFormat("%s\n");
	}


	private final class XhrStreamingSockJsSession extends StreamingSockJsSession {

		private XhrStreamingSockJsSession(String sessionId, SockJsServiceConfig config,
				WebSocketHandler wsHandler, Map<String, Object> attributes) {

			super(sessionId, config, wsHandler, attributes);
		}

		@Override
		protected void writePrelude() throws IOException {
			for (int i=0; i < 2048; i++) {
				getResponse().getBody().write('h');
			}
			getResponse().getBody().write('\n');
			getResponse().flush();
		}
	}
}
