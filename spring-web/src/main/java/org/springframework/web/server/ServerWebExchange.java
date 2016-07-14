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
package org.springframework.web.server;

import java.util.Map;
import java.util.Optional;

import reactor.core.publisher.Mono;

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;

/**
 * Contract for an HTTP request-response interaction. Provides access to the HTTP
 * request and response and also exposes additional server-side processing
 * related properties and features such as request attributes.
 *
 * @author Rossen Stoyanchev
 * @since 5.0
 */
public interface ServerWebExchange {

	/**
	 * Return the current HTTP request.
	 */
	ServerHttpRequest getRequest();

	/**
	 * Return the current HTTP response.
	 */
	ServerHttpResponse getResponse();

	/**
	 * Return a mutable map of request attributes for the current exchange.
	 */
	Map<String, Object> getAttributes();

	/**
	 * Return the request attribute value if present.
	 * @param name the attribute name
	 * @param <T> the attribute type
	 * @return the attribute value
	 */
	<T> Optional<T> getAttribute(String name);

	/**
	 * Return the web session for the current request. Always guaranteed  to
	 * return an instance either matching to the session id requested by the
	 * client, or with a new session id either because the client did not
	 * specify one or because the underlying session had expired. Use of this
	 * method does not automatically create a session. See {@link WebSession}
	 * for more details.
	 */
	Mono<WebSession> getSession();

}
