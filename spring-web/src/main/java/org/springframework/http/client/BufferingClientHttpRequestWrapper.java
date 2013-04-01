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

package org.springframework.http.client;

import java.io.IOException;
import java.net.URI;

import org.springframework.http.Cookies;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.util.Assert;
import org.springframework.util.StreamUtils;

/**
 * Simple implementation of {@link ClientHttpRequest} that wraps another request.
 *
 * @author Arjen Poutsma
 * @since 3.1
 */
final class BufferingClientHttpRequestWrapper extends AbstractBufferingClientHttpRequest {

	private final ClientHttpRequest request;


	BufferingClientHttpRequestWrapper(ClientHttpRequest request) {
		Assert.notNull(request, "'request' must not be null");
		this.request = request;
	}


	public HttpMethod getMethod() {
		return this.request.getMethod();
	}

	public URI getURI() {
		return this.request.getURI();
	}

	@Override
	protected ClientHttpResponse executeInternal(HttpHeaders headers, byte[] bufferedOutput) throws IOException {
		this.request.getHeaders().putAll(headers);
		StreamUtils.copy(bufferedOutput, this.request.getBody());
		ClientHttpResponse response = this.request.execute();
		return new BufferingClientHttpResponseWrapper(response);
	}

	@Override
	public Cookies getCookies() {
		return this.request.getCookies();
	}

}
