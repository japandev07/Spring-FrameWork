/*
 * Copyright 2002-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.mock.http.client;

import java.io.IOException;
import java.io.InputStream;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.util.Assert;

/**
 * Mock implementation of {@link ClientHttpResponse}.
 *
 * @author Rossen Stoyanchev
 * @since 3.2
 */
public class MockClientHttpResponse extends MockHttpInputMessage implements ClientHttpResponse {

	private final HttpStatusCode statusCode;


	/**
	 * Constructor with response body as a byte array.
	 */
	public MockClientHttpResponse(byte[] body, HttpStatusCode statusCode) {
		super(body);
		Assert.notNull(statusCode, "HttpStatusCode must not be null");
		this.statusCode = statusCode;
	}

	/**
	 * Variant of {@link #MockClientHttpResponse(byte[], HttpStatusCode)} with a
	 * custom HTTP status code.
	 * @since 5.3.17
	 */
	public MockClientHttpResponse(byte[] body, int statusCode) {
		this(body, HttpStatusCode.valueOf(statusCode));
	}

	/**
	 * Constructor with response body as InputStream.
	 */
	public MockClientHttpResponse(InputStream body, HttpStatusCode statusCode) {
		super(body);
		Assert.notNull(statusCode, "HttpStatusCode must not be null");
		this.statusCode = statusCode;
	}

	/**
	 * Variant of {@link #MockClientHttpResponse(InputStream, HttpStatusCode)} with a
	 * custom HTTP status code.
	 * @since 5.3.17
	 */
	public MockClientHttpResponse(InputStream body, int statusCode) {
		this(body, HttpStatusCode.valueOf(statusCode));
	}


	@Override
	public HttpStatusCode getStatusCode() {
		return this.statusCode;
	}

	@Override
	@Deprecated
	public int getRawStatusCode() {
		return this.statusCode.value();
	}

	@Override
	public String getStatusText() {
		if (this.statusCode instanceof HttpStatus status) {
			return status.getReasonPhrase();
		}
		else {
			return "";
		}
	}

	@Override
	public void close() {
		try {
			getBody().close();
		}
		catch (IOException ex) {
			// ignore
		}
	}

}
