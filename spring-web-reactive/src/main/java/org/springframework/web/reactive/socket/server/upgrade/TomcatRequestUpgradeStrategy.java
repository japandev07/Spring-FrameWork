/*
 * Copyright 2002-2016 the original author or authors.
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

package org.springframework.web.reactive.socket.server.upgrade;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.websocket.Endpoint;
import javax.websocket.server.ServerEndpointConfig;

import org.apache.tomcat.websocket.server.WsServerContainer;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServletServerHttpRequest;
import org.springframework.http.server.reactive.ServletServerHttpResponse;
import org.springframework.util.Assert;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.adapter.TomcatWebSocketHandlerAdapter;
import org.springframework.web.reactive.socket.server.RequestUpgradeStrategy;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

/**
 * A {@link RequestUpgradeStrategy} for use with Tomcat.
 *
 * @author Violeta Georgieva
 * @since 5.0
 */
public class TomcatRequestUpgradeStrategy implements RequestUpgradeStrategy {

	private static final String SERVER_CONTAINER_ATTR = "javax.websocket.server.ServerContainer";


	@Override
	public Mono<Void> upgrade(ServerWebExchange exchange, WebSocketHandler handler){

		ServerHttpRequest request = exchange.getRequest();
		ServerHttpResponse response = exchange.getResponse();
		Endpoint endpoint = new TomcatWebSocketHandlerAdapter(request, response, handler).getEndpoint();

		HttpServletRequest servletRequest = getHttpServletRequest(request);
		HttpServletResponse servletResponse = getHttpServletResponse(response);

		String requestURI = servletRequest.getRequestURI();
		ServerEndpointConfig config = new ServerEndpointRegistration(requestURI, endpoint);
		try {
			WsServerContainer container = getContainer(servletRequest);
			container.doUpgrade(servletRequest, servletResponse, config, Collections.emptyMap());
		}
		catch (ServletException | IOException ex) {
			return Mono.error(ex);
		}

		return Mono.empty();
	}

	private HttpServletRequest getHttpServletRequest(ServerHttpRequest request) {
		Assert.isTrue(request instanceof ServletServerHttpRequest);
		return ((ServletServerHttpRequest) request).getServletRequest();
	}

	private HttpServletResponse getHttpServletResponse(ServerHttpResponse response) {
		Assert.isTrue(response instanceof ServletServerHttpResponse);
		return ((ServletServerHttpResponse) response).getServletResponse();
	}

	private WsServerContainer getContainer(HttpServletRequest request) {
		ServletContext servletContext = request.getServletContext();
		Object container = servletContext.getAttribute(SERVER_CONTAINER_ATTR);
		Assert.notNull(container,
				"No 'javax.websocket.server.ServerContainer' ServletContext attribute. " +
						"Are you running in a Servlet container that supports JSR-356?");
		Assert.isTrue(container instanceof WsServerContainer);
		return (WsServerContainer) container;
	}

}
