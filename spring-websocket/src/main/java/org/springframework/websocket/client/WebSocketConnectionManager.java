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
package org.springframework.websocket.client;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.websocket.HandlerProvider;
import org.springframework.websocket.WebSocketHandler;
import org.springframework.websocket.WebSocketSession;
import org.springframework.websocket.support.SimpleHandlerProvider;


/**
 *
 * @author Rossen Stoyanchev
 * @since 4.0
 */
public class WebSocketConnectionManager extends AbstractWebSocketConnectionManager {

	private final WebSocketClient client;

	private final HandlerProvider<WebSocketHandler> handlerProvider;

	private WebSocketSession webSocketSession;

	private List<String> subProtocols;


	public WebSocketConnectionManager(WebSocketClient webSocketClient,
			WebSocketHandler webSocketHandler, String uriTemplate, Object... uriVariables) {

		super(uriTemplate, uriVariables);
		this.client = webSocketClient;
		this.handlerProvider = new SimpleHandlerProvider<WebSocketHandler>(webSocketHandler);
	}

	public WebSocketConnectionManager(WebSocketClient webSocketClient,
			HandlerProvider<WebSocketHandler> handlerProvider, String uriTemplate, Object... uriVariables) {

		super(uriTemplate, uriVariables);
		this.client = webSocketClient;
		this.handlerProvider = handlerProvider;
	}

	public void setSubProtocols(List<String> subProtocols) {
		this.subProtocols = subProtocols;
	}

	public List<String> getSubProtocols() {
		return this.subProtocols;
	}

	@Override
	protected void openConnection() throws Exception {
		HttpHeaders headers = new HttpHeaders();
		headers.setSecWebSocketProtocol(this.subProtocols);
		this.webSocketSession = this.client.doHandshake(this.handlerProvider, headers, getUri());
	}

	@Override
	protected void closeConnection() throws Exception {
		this.webSocketSession.close();
	}

	@Override
	protected boolean isConnected() {
		return ((this.webSocketSession != null) && (this.webSocketSession.isOpen()));
	}

}
