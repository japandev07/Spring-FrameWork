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

package org.springframework.web.client.reactive;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.client.reactive.ClientHttpResponse;
import org.springframework.http.codec.HttpMessageReader;

/**
 * Default implementation of the {@link ResponseErrorHandler} interface
 * that throws {@link WebClientErrorException}s for HTTP 4xx responses
 * and {@link WebServerErrorException}s for HTTP 5xx responses.
 *
 * @author Brian Clozel
 * @since 5.0
 */
public class DefaultResponseErrorHandler implements ResponseErrorHandler {

	@Override
	public void handleError(ClientHttpResponse response, List<HttpMessageReader<?>> messageReaders) {
		HttpStatus responseStatus = response.getStatusCode();
		if (responseStatus.is4xxClientError()) {
			throw new WebClientErrorException(response, messageReaders);
		}
		if (responseStatus.is5xxServerError()) {
			throw new WebServerErrorException(response, messageReaders);
		}
	}
}
