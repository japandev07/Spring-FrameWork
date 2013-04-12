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

package org.springframework.websocket.server;

import java.util.Collection;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.websocket.WebSocketHandler;


/**
 * Contract for processing a WebSocket handshake request.
 *
 * @author Rossen Stoyanchev
 * @since 4.0
 */
public interface HandshakeHandler {

	/**
	 * Pre-register {@link WebSocketHandler} instances so they can be adapted to the
	 * underlying runtime and hence re-used at runtime when
	 * {@link #doHandshake(ServerHttpRequest, ServerHttpResponse, WebSocketHandler) doHandshake}
	 * is called.
	 */
	void registerWebSocketHandlers(Collection<WebSocketHandler> webSocketHandlers);

	/**
	 *
	 * @param request the HTTP request
	 * @param response the HTTP response
	 * @param webSocketMessageHandler the handler to process WebSocket messages with
	 * @return a boolean indicating whether the handshake negotiation was successful
	 *
	 * @throws Exception
	 */
	boolean doHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler webSocketHandler)
			throws Exception;

}
