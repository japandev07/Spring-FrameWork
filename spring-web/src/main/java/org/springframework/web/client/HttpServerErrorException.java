/*
 * Copyright 2002-2019 the original author or authors.
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

package org.springframework.web.client;

import java.nio.charset.Charset;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;

/**
 * Exception thrown when an HTTP 5xx is received.
 *
 * @author Arjen Poutsma
 * @since 3.0
 * @see DefaultResponseErrorHandler
 */
public class HttpServerErrorException extends HttpStatusCodeException {

	private static final long serialVersionUID = -2915754006618138282L;


	/**
	 * Constructor with a status code only.
	 */
	public HttpServerErrorException(HttpStatus statusCode) {
		super(statusCode);
	}

	/**
	 * Constructor with a status code and status text.
	 */
	public HttpServerErrorException(HttpStatus statusCode, String statusText) {
		super(statusCode, statusText);
	}

	/**
	 * Constructor with a status code and status text, and content.
	 */
	public HttpServerErrorException(
			HttpStatus statusCode, String statusText, @Nullable byte[] body, @Nullable Charset charset) {

		super(statusCode, statusText, body, charset);
	}

	/**
	 * Constructor with a status code and status text, headers, and content.
	 */
	public HttpServerErrorException(HttpStatus statusCode, String statusText,
			@Nullable HttpHeaders headers, @Nullable byte[] body, @Nullable Charset charset) {

		super(statusCode, statusText, headers, body, charset);
	}

	/**
	 * Constructor with a status code and status text, headers, content, and a
	 * prepared message.
	 * @since 5.2.2
	 */
	public HttpServerErrorException(String message, HttpStatus statusCode, String statusText,
			@Nullable HttpHeaders headers, @Nullable byte[] body, @Nullable Charset charset) {

		super(message, statusCode, statusText, headers, body, charset);
	}

	/**
	 * Create an {@code HttpServerErrorException} or an HTTP status specific subclass.
	 * @since 5.1
	 */
	public static HttpServerErrorException create(HttpStatus statusCode,
			String statusText, HttpHeaders headers, byte[] body, @Nullable Charset charset) {

		return create(null, statusCode, statusText, headers, body, charset);
	}

	/**
	 * Variant of {@link #create(String, HttpStatus, String, HttpHeaders, byte[], Charset)}
	 * with an optional prepared message.
	 * @since 5.2.2.
	 */
	public static HttpServerErrorException create(@Nullable String message, HttpStatus statusCode,
			String statusText, HttpHeaders headers, byte[] body, @Nullable Charset charset) {

		switch (statusCode) {
			case INTERNAL_SERVER_ERROR:
				return message != null ?
						new HttpServerErrorException.InternalServerError(message, statusText, headers, body, charset) :
						new HttpServerErrorException.InternalServerError(statusText, headers, body, charset);
			case NOT_IMPLEMENTED:
				return message != null ?
						new HttpServerErrorException.NotImplemented(message, statusText, headers, body, charset) :
						new HttpServerErrorException.NotImplemented(statusText, headers, body, charset);
			case BAD_GATEWAY:
				return message != null ?
						new HttpServerErrorException.BadGateway(message, statusText, headers, body, charset) :
						new HttpServerErrorException.BadGateway(statusText, headers, body, charset);
			case SERVICE_UNAVAILABLE:
				return message != null ?
						new HttpServerErrorException.ServiceUnavailable(message, statusText, headers, body, charset) :
						new HttpServerErrorException.ServiceUnavailable(statusText, headers, body, charset);
			case GATEWAY_TIMEOUT:
				return message != null ?
						new HttpServerErrorException.GatewayTimeout(message, statusText, headers, body, charset) :
						new HttpServerErrorException.GatewayTimeout(statusText, headers, body, charset);
			default:
				return message != null ?
						new HttpServerErrorException(message, statusCode, statusText, headers, body, charset) :
						new HttpServerErrorException(statusCode, statusText, headers, body, charset);
		}
	}


	// Subclasses for specific HTTP status codes

	/**
	 * {@link HttpServerErrorException} for status HTTP 500 Internal Server Error.
	 * @since 5.1
	 */
	@SuppressWarnings("serial")
	public static final class InternalServerError extends HttpServerErrorException {

		private InternalServerError(String statusText, HttpHeaders headers, byte[] body, @Nullable Charset charset) {
			super(HttpStatus.INTERNAL_SERVER_ERROR, statusText, headers, body, charset);
		}

		private InternalServerError(String message, String statusText,
				HttpHeaders headers, byte[] body, @Nullable Charset charset) {

			super(message, HttpStatus.INTERNAL_SERVER_ERROR, statusText, headers, body, charset);
		}
	}

	/**
	 * {@link HttpServerErrorException} for status HTTP 501 Not Implemented.
	 * @since 5.1
	 */
	@SuppressWarnings("serial")
	public static final class NotImplemented extends HttpServerErrorException {

		private NotImplemented(String statusText, HttpHeaders headers, byte[] body, @Nullable Charset charset) {
			super(HttpStatus.NOT_IMPLEMENTED, statusText, headers, body, charset);
		}

		private NotImplemented(String message, String statusText,
				HttpHeaders headers, byte[] body, @Nullable Charset charset) {

			super(message, HttpStatus.NOT_IMPLEMENTED, statusText, headers, body, charset);
		}
	}

	/**
	 * {@link HttpServerErrorException} for HTTP status 502 Bad Gateway.
	 * @since 5.1
	 */
	@SuppressWarnings("serial")
	public static final class BadGateway extends HttpServerErrorException {

		private BadGateway(String statusText, HttpHeaders headers, byte[] body, @Nullable Charset charset) {
			super(HttpStatus.BAD_GATEWAY, statusText, headers, body, charset);
		}

		private BadGateway(String message, String statusText,
				HttpHeaders headers, byte[] body, @Nullable Charset charset) {

			super(message, HttpStatus.BAD_GATEWAY, statusText, headers, body, charset);
		}
	}

	/**
	 * {@link HttpServerErrorException} for status HTTP 503 Service Unavailable.
	 * @since 5.1
	 */
	@SuppressWarnings("serial")
	public static final class ServiceUnavailable extends HttpServerErrorException {

		private ServiceUnavailable(String statusText, HttpHeaders headers, byte[] body, @Nullable Charset charset) {
			super(HttpStatus.SERVICE_UNAVAILABLE, statusText, headers, body, charset);
		}

		private ServiceUnavailable(String message, String statusText,
				HttpHeaders headers, byte[] body, @Nullable Charset charset) {

			super(message, HttpStatus.SERVICE_UNAVAILABLE, statusText, headers, body, charset);
		}
	}

	/**
	 * {@link HttpServerErrorException} for status HTTP 504 Gateway Timeout.
	 * @since 5.1
	 */
	@SuppressWarnings("serial")
	public static final class GatewayTimeout extends HttpServerErrorException {

		private GatewayTimeout(String statusText, HttpHeaders headers, byte[] body, @Nullable Charset charset) {
			super(HttpStatus.GATEWAY_TIMEOUT, statusText, headers, body, charset);
		}

		private GatewayTimeout(String message, String statusText,
				HttpHeaders headers, byte[] body, @Nullable Charset charset) {

			super(message, HttpStatus.GATEWAY_TIMEOUT, statusText, headers, body, charset);
		}
	}

}
