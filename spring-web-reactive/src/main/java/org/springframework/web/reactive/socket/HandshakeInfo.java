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
package org.springframework.web.reactive.socket;

import java.net.URI;
import java.security.Principal;

import reactor.core.publisher.Mono;

import org.springframework.http.HttpHeaders;
import org.springframework.util.Assert;

/**
 * Simple container of information from a WebSocket handshake request.
 *
 * @author Rossen Stoyanchev
 * @since 5.0
 */
public class HandshakeInfo {

	private final URI uri;

	private final HttpHeaders headers;

	private final Mono<Principal> principal;


	public HandshakeInfo(URI uri, HttpHeaders headers, Mono<Principal> principal) {
		Assert.notNull(uri, "URI is required.");
		Assert.notNull(headers, "HttpHeaders are required.");
		Assert.notNull(principal, "Prinicpal is required.");
		this.uri = uri;
		this.headers = headers;
		this.principal = principal;
	}


	public URI getUri() {
		return this.uri;
	}

	public HttpHeaders getHeaders() {
		return this.headers;
	}

	public Mono<Principal> getPrincipal() {
		return this.principal;
	}

	@Override
	public String toString() {
		return "HandshakeInfo[uri=" + this.uri + ", headers=" + this.headers + "]";
	}

}
