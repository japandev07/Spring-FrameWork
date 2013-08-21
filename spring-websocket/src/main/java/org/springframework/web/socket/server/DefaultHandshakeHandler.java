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

package org.springframework.web.socket.server;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;

/**
 * A default {@link HandshakeHandler} implementation. Performs initial validation of the
 * WebSocket handshake request -- possibly rejecting it through the appropriate HTTP
 * status code -- while also allowing sub-classes to override various parts of the
 * negotiation process (e.g. origin validation, sub-protocol negotiation, etc).
 *
 * <p>
 * If the negotiation succeeds, the actual upgrade is delegated to a server-specific
 * {@link RequestUpgradeStrategy}, which will update the response as necessary and
 * initialize the WebSocket. Currently supported servers are Tomcat 7 and 8, Jetty 9, and
 * Glassfish 4.
 *
 * @author Rossen Stoyanchev
 * @since 4.0
 */
public class DefaultHandshakeHandler implements HandshakeHandler {

	protected Log logger = LogFactory.getLog(getClass());

	private static final boolean tomcatWsPresent = ClassUtils.isPresent(
			"org.apache.tomcat.websocket.server.WsHttpUpgradeHandler", HandshakeHandler.class.getClassLoader());

	private static final boolean jettyWsPresent = ClassUtils.isPresent(
			"org.eclipse.jetty.websocket.server.WebSocketServerFactory", HandshakeHandler.class.getClassLoader());

	private static final boolean glassFishWsPresent = ClassUtils.isPresent(
			"org.glassfish.tyrus.servlet.TyrusHttpUpgradeHandler", HandshakeHandler.class.getClassLoader());


	private final RequestUpgradeStrategy requestUpgradeStrategy;

	private final List<String> supportedProtocols = new ArrayList<String>();



	/**
	 * Default constructor that auto-detects and instantiates a
	 * {@link RequestUpgradeStrategy} suitable for the runtime container.
	 *
	 * @throws IllegalStateException if no {@link RequestUpgradeStrategy} can be found.
	 */
	public DefaultHandshakeHandler() {
		this(initRequestUpgradeStrategy());
	}

	private static RequestUpgradeStrategy initRequestUpgradeStrategy() {
		String className;
		if (tomcatWsPresent) {
			className = "org.springframework.web.socket.server.support.TomcatRequestUpgradeStrategy";
		}
		else if (jettyWsPresent) {
			className = "org.springframework.web.socket.server.support.JettyRequestUpgradeStrategy";
		}
		else if (glassFishWsPresent) {
			className = "org.springframework.web.socket.server.support.GlassFishRequestUpgradeStrategy";
		}
		else {
			throw new IllegalStateException("No suitable " + RequestUpgradeStrategy.class.getSimpleName());
		}
		try {
			Class<?> clazz = ClassUtils.forName(className, DefaultHandshakeHandler.class.getClassLoader());
			return (RequestUpgradeStrategy) BeanUtils.instantiateClass(clazz.getConstructor());
		}
		catch (Throwable t) {
			throw new IllegalStateException("Failed to instantiate " + className, t);
		}
	}

	/**
	 * A constructor that accepts a runtime specific {@link RequestUpgradeStrategy}.
	 * @param upgradeStrategy the upgrade strategy
	 */
	public DefaultHandshakeHandler(RequestUpgradeStrategy upgradeStrategy) {
		this.requestUpgradeStrategy = upgradeStrategy;
	}

	/**
	 * Use this property to configure a list of sub-protocols that are supported.
	 * The first protocol that matches what the client requested is selected.
	 * If no protocol matches or this property is not configured, then the
	 * response will not contain a Sec-WebSocket-Protocol header.
	 */
	public void setSupportedProtocols(String... protocols) {
		this.supportedProtocols.clear();
		for (String protocol : protocols) {
			this.supportedProtocols.add(protocol.toLowerCase());
		}
	}

	/**
	 * Return the list of supported sub-protocols.
	 */
	public String[] getSupportedProtocols() {
		return this.supportedProtocols.toArray(new String[this.supportedProtocols.size()]);
	}

	@Override
	public final boolean doHandshake(ServerHttpRequest request, ServerHttpResponse response,
			WebSocketHandler wsHandler, Map<String, Object> attributes) throws HandshakeFailureException {

		if (logger.isDebugEnabled()) {
			logger.debug("Initiating handshake for " + request.getURI() + ", headers=" + request.getHeaders());
		}

		try {
			if (!HttpMethod.GET.equals(request.getMethod())) {
				response.setStatusCode(HttpStatus.METHOD_NOT_ALLOWED);
				response.getHeaders().setAllow(Collections.singleton(HttpMethod.GET));
				logger.debug("Only HTTP GET is allowed, current method is " + request.getMethod());
				return false;
			}
			if (!"WebSocket".equalsIgnoreCase(request.getHeaders().getUpgrade())) {
				handleInvalidUpgradeHeader(request, response);
				return false;
			}
			if (!request.getHeaders().getConnection().contains("Upgrade") &&
					!request.getHeaders().getConnection().contains("upgrade")) {
				handleInvalidConnectHeader(request, response);
				return false;
			}
			if (!isWebSocketVersionSupported(request)) {
				handleWebSocketVersionNotSupported(request, response);
				return false;
			}
			if (!isValidOrigin(request)) {
				response.setStatusCode(HttpStatus.FORBIDDEN);
				return false;
			}
			String wsKey = request.getHeaders().getSecWebSocketKey();
			if (wsKey == null) {
				logger.debug("Missing \"Sec-WebSocket-Key\" header");
				response.setStatusCode(HttpStatus.BAD_REQUEST);
				return false;
			}
		}
		catch (IOException ex) {
			throw new HandshakeFailureException(
					"Response update failed during upgrade to WebSocket, uri=" + request.getURI(), ex);
		}

		String subProtocol = selectProtocol(request.getHeaders().getSecWebSocketProtocol());

		if (logger.isDebugEnabled()) {
			logger.debug("Upgrading request, sub-protocol=" + subProtocol);
		}

		this.requestUpgradeStrategy.upgrade(request, response, subProtocol, wsHandler, attributes);

		return true;
	}

	protected void handleInvalidUpgradeHeader(ServerHttpRequest request, ServerHttpResponse response) throws IOException {
		logger.debug("Invalid Upgrade header " + request.getHeaders().getUpgrade());
		response.setStatusCode(HttpStatus.BAD_REQUEST);
		response.getBody().write("Can \"Upgrade\" only to \"WebSocket\".".getBytes("UTF-8"));
	}

	protected void handleInvalidConnectHeader(ServerHttpRequest request, ServerHttpResponse response) throws IOException {
		logger.debug("Invalid Connection header " + request.getHeaders().getConnection());
		response.setStatusCode(HttpStatus.BAD_REQUEST);
		response.getBody().write("\"Connection\" must be \"upgrade\".".getBytes("UTF-8"));
	}

	protected boolean isWebSocketVersionSupported(ServerHttpRequest request) {
		String version = request.getHeaders().getSecWebSocketVersion();
		String[] supportedVersions = getSupportedVerions();
		if (logger.isDebugEnabled()) {
			logger.debug("Requested version=" + version + ", supported=" + Arrays.toString(supportedVersions));
		}
		for (String supportedVersion : supportedVersions) {
			if (supportedVersion.trim().equals(version)) {
				return true;
			}
		}
		if (logger.isDebugEnabled()) {
			logger.debug("Version=" + version + " is not a supported version");
		}
		return false;
	}

	protected String[] getSupportedVerions() {
		return this.requestUpgradeStrategy.getSupportedVersions();
	}

	protected void handleWebSocketVersionNotSupported(ServerHttpRequest request, ServerHttpResponse response) {
		logger.debug("WebSocket version not supported " + request.getHeaders().get("Sec-WebSocket-Version"));
		response.setStatusCode(HttpStatus.UPGRADE_REQUIRED);
		response.getHeaders().setSecWebSocketVersion(StringUtils.arrayToCommaDelimitedString(getSupportedVerions()));
	}

	protected boolean isValidOrigin(ServerHttpRequest request) {
		String origin = request.getHeaders().getOrigin();
		if (origin != null) {
			// UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(origin);
			// TODO
			// A simple strategy checks against the current request's scheme/port/host
			// Or match scheme, port, and host against configured allowed origins (wild cards for hosts?)
			// return false;
		}
		return true;
	}

	protected String selectProtocol(List<String> requestedProtocols) {
		if (requestedProtocols != null) {
			if (logger.isDebugEnabled()) {
				logger.debug("Requested sub-protocol(s): " + requestedProtocols
						+ ", supported sub-protocol(s): " + this.supportedProtocols);
			}
			for (String protocol : requestedProtocols) {
				if (this.supportedProtocols.contains(protocol.toLowerCase())) {
					if (logger.isDebugEnabled()) {
						logger.debug("Selected sub-protocol: '" + protocol + "'");
					}
					return protocol;
				}
			}
		}
		return null;
	}

}
