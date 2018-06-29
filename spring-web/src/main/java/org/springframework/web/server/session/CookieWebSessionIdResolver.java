/*
 * Copyright 2002-2018 the original author or authors.
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

package org.springframework.web.server.session;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.HttpCookie;
import org.springframework.http.ResponseCookie;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ServerWebExchange;

/**
 * Cookie-based {@link WebSessionIdResolver}.
 *
 * @author Rossen Stoyanchev
 * @author Brian Clozel
 * @since 5.0
 */
public class CookieWebSessionIdResolver implements WebSessionIdResolver {

	private String cookieName = "SESSION";

	private Duration cookieMaxAge = Duration.ofSeconds(-1);

	private String sameSite = "Strict";


	/**
	 * Set the name of the cookie to use for the session id.
	 * <p>By default set to "SESSION".
	 * @param cookieName the cookie name
	 */
	public void setCookieName(String cookieName) {
		Assert.hasText(cookieName, "'cookieName' must not be empty");
		this.cookieName = cookieName;
	}

	/**
	 * Return the configured cookie name.
	 */
	public String getCookieName() {
		return this.cookieName;
	}

	/**
	 * Set the value for the "Max-Age" attribute of the cookie that holds the
	 * session id. For the range of values see {@link ResponseCookie#getMaxAge()}.
	 * <p>By default set to -1.
	 * @param maxAge the maxAge duration value
	 */
	public void setCookieMaxAge(Duration maxAge) {
		this.cookieMaxAge = maxAge;
	}

	/**
	 * Return the configured "Max-Age" attribute value for the session cookie.
	 */
	public Duration getCookieMaxAge() {
		return this.cookieMaxAge;
	}

	/**
	 * Set the value for the "SameSite" attribute of the cookie that holds the
	 * session id. For its meaning and possible values, see
	 * {@link ResponseCookie#getSameSite()}.
	 * <p>By default set to {@code "Strict"}.
	 * @param sameSite the SameSite value
	 * @since 5.1
	 */
	public void setSameSite(String sameSite) {
		this.sameSite = sameSite;
	}

	/**
	 * Return the configured "SameSite" attribute value for the session cookie.
	 * @since 5.1
	 */
	public String getSameSite() {
		return this.sameSite;
	}


	@Override
	public List<String> resolveSessionIds(ServerWebExchange exchange) {
		MultiValueMap<String, HttpCookie> cookieMap = exchange.getRequest().getCookies();
		List<HttpCookie> cookies = cookieMap.get(getCookieName());
		if (cookies == null) {
			return Collections.emptyList();
		}
		return cookies.stream().map(HttpCookie::getValue).collect(Collectors.toList());
	}

	@Override
	public void setSessionId(ServerWebExchange exchange, String id) {
		Assert.notNull(id, "'id' is required");
		ResponseCookie cookie = initSessionCookie(exchange, id, getCookieMaxAge(), getSameSite());
		exchange.getResponse().getCookies().set(this.cookieName, cookie);
	}

	@Override
	public void expireSession(ServerWebExchange exchange) {
		ResponseCookie cookie = initSessionCookie(exchange, "", Duration.ZERO, null);
		exchange.getResponse().getCookies().set(this.cookieName, cookie);
	}

	private ResponseCookie initSessionCookie(
			ServerWebExchange exchange, String id, Duration maxAge, @Nullable String sameSite) {

		return ResponseCookie.from(this.cookieName, id)
				.path(exchange.getRequest().getPath().contextPath().value() + "/")
				.maxAge(maxAge)
				.httpOnly(true)
				.secure("https".equalsIgnoreCase(exchange.getRequest().getURI().getScheme()))
				.sameSite(sameSite)
				.build();
	}

}
