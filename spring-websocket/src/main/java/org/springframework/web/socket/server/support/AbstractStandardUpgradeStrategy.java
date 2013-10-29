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

package org.springframework.web.socket.server.support;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;

import javax.websocket.Endpoint;
import javax.websocket.Extension;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketExtension;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.adapter.StandardWebSocketHandlerAdapter;
import org.springframework.web.socket.adapter.StandardWebSocketSession;
import org.springframework.web.socket.server.HandshakeFailureException;
import org.springframework.web.socket.server.RequestUpgradeStrategy;

/**
 * A base class for {@link RequestUpgradeStrategy} implementations that build on the
 * standard WebSocket API for Java.
 *
 * @author Rossen Stoyanchev
 * @since 4.0
 */
public abstract class AbstractStandardUpgradeStrategy implements RequestUpgradeStrategy {

	protected final Log logger = LogFactory.getLog(getClass());


	@Override
	public void upgrade(ServerHttpRequest request, ServerHttpResponse response, String acceptedProtocol,
			WebSocketHandler wsHandler, Map<String, Object> attributes) throws HandshakeFailureException {

		HttpHeaders headers = request.getHeaders();

		InetSocketAddress localAddr = request.getLocalAddress();
		InetSocketAddress remoteAddr = request.getRemoteAddress();

		StandardWebSocketSession session = new StandardWebSocketSession(headers, attributes, localAddr, remoteAddr);
		StandardWebSocketHandlerAdapter endpoint = new StandardWebSocketHandlerAdapter(wsHandler, session);

		upgradeInternal(request, response, acceptedProtocol, endpoint);
	}

	protected abstract void upgradeInternal(ServerHttpRequest request, ServerHttpResponse response,
			String selectedProtocol, Endpoint endpoint) throws HandshakeFailureException;

	protected WebSocketExtension parseStandardExtension(Extension extension) {
		Map<String, String> params = new HashMap<String,String>(extension.getParameters().size());
		for(Extension.Parameter param : extension.getParameters()) {
			params.put(param.getName(),param.getValue());
		}
		return new WebSocketExtension(extension.getName(),params);
	}

}
