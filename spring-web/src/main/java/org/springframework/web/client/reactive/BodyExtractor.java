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

import org.springframework.http.client.reactive.ClientHttpResponse;
import org.springframework.http.codec.HttpMessageReader;

/**
 * Contract to extract the content of a raw {@link ClientHttpResponse} decoding
 * the response body and using a target composition API.
 *
 * <p>See static factory methods in {@link ResponseExtractors} and
 * {@link org.springframework.web.client.reactive.support.RxJava1ResponseExtractors}.
 *
 * @author Brian Clozel
 * @since 5.0
 */
public interface BodyExtractor<T> {

	/**
	 * Extract content from the response body
	 * @param clientResponse the raw HTTP response
	 * @param messageReaders the message readers that decode the response body
	 * @return the relevant content
	 */
	T extract(ClientHttpResponse clientResponse, List<HttpMessageReader<?>> messageReaders);

}
