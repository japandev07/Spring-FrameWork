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

package org.springframework.http.server.reactive;

import java.net.URI;

import reactor.core.publisher.Flux;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.ReactiveHttpInputMessage;
import org.springframework.util.MultiValueMap;

/**
 * Represents a reactive server-side HTTP request
 *
 * @author Arjen Poutsma
 * @author Rossen Stoyanchev
 * @since 5.0
 */
public interface ServerHttpRequest extends HttpRequest, ReactiveHttpInputMessage {

	/**
	 * Returns the portion of the URL path that represents the context path for
	 * the current {@link HttpHandler}. The context path is always at the
	 * beginning of the request path. It starts with "/" but but does not end
	 * with "/". This method may return an empty string if no context path is
	 * configured.
	 * @return the context path (not decoded) or an empty string
	 */
	default String getContextPath() {
		return "";
	}

	/**
	 * Return a read-only map with parsed and decoded query parameter values.
	 */
	MultiValueMap<String, String> getQueryParams();

	/**
	 * Return a read-only map of cookies sent by the client.
	 */
	MultiValueMap<String, HttpCookie> getCookies();


	/**
	 * Return a builder to mutate properties of this request. The resulting
	 * new request is an immutable {@link ServerHttpRequestDecorator decorator}
	 * around the current exchange instance returning mutated values.
	 */
	default ServerHttpRequest.Builder mutate() {
		return new DefaultServerHttpRequestBuilder(this);
	}


	/**
	 * Builder for mutating properties of a {@link ServerHttpRequest}.
	 */
	interface Builder {

		/**
		 * Set the HTTP method.
		 */
		Builder method(HttpMethod httpMethod);

		/**
		 * Set the request URI.
		 */
		Builder uri(URI uri);


		/**
		 * Set the contextPath for the request.
		 */
		Builder contextPath(String contextPath);

		/**
		 * Set the query params to return.
		 */
		Builder queryParams(MultiValueMap<String, String> queryParams);

		/**
		 * Set the headers to use.
		 */
		Builder headers(HttpHeaders headers);

		/**
		 * Set the cookies to use.
		 */
		Builder cookies(MultiValueMap<String, HttpCookie> cookies);

		/**
		 * Set the body to return.
		 */
		Builder body(Flux<DataBuffer> body);

		/**
		 * Build an immutable wrapper that returning the mutated properties.
		 */
		ServerHttpRequest build();

	}

}
