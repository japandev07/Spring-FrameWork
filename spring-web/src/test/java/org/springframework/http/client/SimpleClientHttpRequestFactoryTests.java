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

package org.springframework.http.client;

import java.net.HttpURLConnection;

import org.junit.jupiter.api.Test;

import org.springframework.http.HttpHeaders;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * @author Stephane Nicoll
 */
public class SimpleClientHttpRequestFactoryTests {


	@Test // SPR-13225
	public void headerWithNullValue() {
		HttpURLConnection urlConnection = mock(HttpURLConnection.class);
		given(urlConnection.getRequestMethod()).willReturn("GET");
		HttpHeaders headers = new HttpHeaders();
		headers.set("foo", null);
		SimpleBufferingClientHttpRequest.addHeaders(urlConnection, headers);
		verify(urlConnection, times(1)).addRequestProperty("foo", "");
	}

}
