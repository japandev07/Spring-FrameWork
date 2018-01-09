/*
 * Copyright 2002-2015 the original author or authors.
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

package org.springframework.web.cors.reactive;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.Assert;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

;

/**
 * Utility class for CORS reactive request handling based on the
 * <a href="http://www.w3.org/TR/cors/">CORS W3C recommendation</a>.
 *
 * @author Sebastien Deleuze
 * @since 5.0
 */
public abstract class CorsUtils {

	/**
	 * Returns {@code true} if the request is a valid CORS one.
	 */
	public static boolean isCorsRequest(ServerHttpRequest request) {
		return (request.getHeaders().get(HttpHeaders.ORIGIN) != null);
	}

	/**
	 * Returns {@code true} if the request is a valid CORS pre-flight one.
	 */
	public static boolean isPreFlightRequest(ServerHttpRequest request) {
		return (isCorsRequest(request) && HttpMethod.OPTIONS == request.getMethod() &&
				request.getHeaders().get(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD) != null);
	}

	/**
	 * Check if the request is a same-origin one, based on {@code Origin}, {@code Host},
	 * {@code Forwarded}, {@code X-Forwarded-Proto}, {@code X-Forwarded-Host} and
	 * @code X-Forwarded-Port} headers.
	 * @return {@code true} if the request is a same-origin one, {@code false} in case
	 * of cross-origin request.
	 */
	public static boolean isSameOrigin(ServerHttpRequest request) {
		String origin = request.getHeaders().getOrigin();
		if (origin == null) {
			return true;
		}
		UriComponentsBuilder urlBuilder = UriComponentsBuilder.fromHttpRequest(request);
		UriComponents actualUrl = urlBuilder.build();
		String actualHost = actualUrl.getHost();
		int actualPort = getPort(actualUrl.getScheme(), actualUrl.getPort());
		Assert.notNull(actualHost, "Actual request host must not be null");
		Assert.isTrue(actualPort != -1, "Actual request port must not be undefined");
		UriComponents originUrl = UriComponentsBuilder.fromOriginHeader(origin).build();
		return (actualHost.equals(originUrl.getHost()) && actualPort == getPort(originUrl.getScheme(), originUrl.getPort()));
	}

	private static int getPort(String scheme, int port) {
		if (port == -1) {
			if ("http".equals(scheme) || "ws".equals(scheme)) {
				port = 80;
			}
			else if ("https".equals(scheme) || "wss".equals(scheme)) {
				port = 443;
			}
		}
		return port;
	}

}
