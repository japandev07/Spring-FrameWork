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

package org.springframework.web.server.adapter;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import reactor.core.publisher.Mono;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebSession;
import org.springframework.web.server.session.WebSessionManager;

/**
 * Default implementation of {@link ServerWebExchange}.
 *
 * @author Rossen Stoyanchev
 * @since 5.0
 */
public class DefaultServerWebExchange implements ServerWebExchange {

	private static final List<HttpMethod> SAFE_METHODS = Arrays.asList(HttpMethod.GET, HttpMethod.HEAD);


	private final ServerHttpRequest request;

	private final ServerHttpResponse response;

	private final Map<String, Object> attributes = new ConcurrentHashMap<>();

	private final Mono<WebSession> sessionMono;

	private volatile boolean notModified;


	public DefaultServerWebExchange(ServerHttpRequest request, ServerHttpResponse response,
			WebSessionManager sessionManager) {

		Assert.notNull(request, "'request' is required");
		Assert.notNull(response, "'response' is required");
		Assert.notNull(response, "'sessionManager' is required");
		this.request = request;
		this.response = response;
		this.sessionMono = sessionManager.getSession(this).cache();
	}


	@Override
	public ServerHttpRequest getRequest() {
		return this.request;
	}

	private HttpHeaders getRequestHeaders() {
		return getRequest().getHeaders();
	}

	@Override
	public ServerHttpResponse getResponse() {
		return this.response;
	}

	private HttpHeaders getResponseHeaders() {
		return getResponse().getHeaders();
	}

	@Override
	public Map<String, Object> getAttributes() {
		return this.attributes;
	}

	@Override @SuppressWarnings("unchecked")
	public <T> Optional<T> getAttribute(String name) {
		return Optional.ofNullable((T) this.attributes.get(name));
	}

	@Override
	public Mono<WebSession> getSession() {
		return this.sessionMono;
	}

	@Override
	public boolean isNotModified() {
		return this.notModified;
	}

	@Override
	public boolean checkNotModified(Instant lastModified) {
		return checkNotModified(null, lastModified);
	}

	@Override
	public boolean checkNotModified(String etag) {
		return checkNotModified(etag, Instant.MIN);
	}

	@Override
	public boolean checkNotModified(String etag, Instant lastModified) {
		HttpStatus status = getResponse().getStatusCode();
		if (this.notModified || (status != null && !HttpStatus.OK.equals(status))) {
			return this.notModified;
		}

		// Evaluate conditions in order of precedence.
		// See https://tools.ietf.org/html/rfc7232#section-6

		if (validateIfUnmodifiedSince(lastModified)) {
			if (this.notModified) {
				getResponse().setStatusCode(HttpStatus.PRECONDITION_FAILED);
			}
			return this.notModified;
		}

		boolean validated = validateIfNoneMatch(etag);

		if (!validated) {
			validateIfModifiedSince(lastModified);
		}

		// Update response

		boolean isHttpGetOrHead = SAFE_METHODS.contains(getRequest().getMethod());
		if (this.notModified) {
			getResponse().setStatusCode(isHttpGetOrHead ?
					HttpStatus.NOT_MODIFIED : HttpStatus.PRECONDITION_FAILED);
		}
		if (isHttpGetOrHead) {
			if (lastModified.isAfter(Instant.EPOCH) && getResponseHeaders().getLastModified() == -1) {
				getResponseHeaders().setLastModified(lastModified.toEpochMilli());
			}
			if (StringUtils.hasLength(etag) && getResponseHeaders().getETag() == null) {
				getResponseHeaders().setETag(padEtagIfNecessary(etag));
			}
		}

		return this.notModified;
	}

	private boolean validateIfUnmodifiedSince(Instant lastModified) {
		if (lastModified.isBefore(Instant.EPOCH)) {
			return false;
		}
		long ifUnmodifiedSince = getRequestHeaders().getIfUnmodifiedSince();
		if (ifUnmodifiedSince == -1) {
			return false;
		}
		// We will perform this validation...
		Instant sinceInstant = Instant.ofEpochMilli(ifUnmodifiedSince);
		this.notModified = sinceInstant.isBefore(lastModified.truncatedTo(ChronoUnit.SECONDS));
		return true;
	}

	private boolean validateIfNoneMatch(String etag) {
		if (!StringUtils.hasLength(etag)) {
			return false;
		}
		List<String> ifNoneMatch;
		try {
			ifNoneMatch = getRequestHeaders().getIfNoneMatch();
		}
		catch (IllegalArgumentException ex) {
			return false;
		}
		if (ifNoneMatch.isEmpty()) {
			return false;
		}
		// We will perform this validation...
		etag = padEtagIfNecessary(etag);
		for (String clientETag : ifNoneMatch) {
			// Compare weak/strong ETags as per https://tools.ietf.org/html/rfc7232#section-2.3
			if (StringUtils.hasLength(clientETag) &&
					clientETag.replaceFirst("^W/", "").equals(etag.replaceFirst("^W/", ""))) {
				this.notModified = true;
				break;
			}
		}
		return true;
	}

	private String padEtagIfNecessary(String etag) {
		if (!StringUtils.hasLength(etag)) {
			return etag;
		}
		if ((etag.startsWith("\"") || etag.startsWith("W/\"")) && etag.endsWith("\"")) {
			return etag;
		}
		return "\"" + etag + "\"";
	}

	private boolean validateIfModifiedSince(Instant lastModified) {
		if (lastModified.isBefore(Instant.EPOCH)) {
			return false;
		}
		long ifModifiedSince = getRequestHeaders().getIfModifiedSince();
		if (ifModifiedSince == -1) {
			return false;
		}
		// We will perform this validation...
		this.notModified = ChronoUnit.SECONDS.between(lastModified, Instant.ofEpochMilli(ifModifiedSince)) >= 0;
		return true;
	}

}
