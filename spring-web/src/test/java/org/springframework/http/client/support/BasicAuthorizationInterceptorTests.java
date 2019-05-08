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

package org.springframework.http.client.support;

import java.net.URI;

import org.junit.Test;

import org.springframework.beans.DirectFieldAccessor;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link BasicAuthorizationInterceptor}.
 *
 * @author Phillip Webb
 * @author Stephane Nicoll
 */
public class BasicAuthorizationInterceptorTests {

	@Test
	public void createWhenUsernameContainsColonShouldThrowException() {
		assertThatIllegalArgumentException().isThrownBy(() ->
				new BasicAuthorizationInterceptor("username:", "password"))
			.withMessageContaining("Username must not contain a colon");
	}

	@Test
	public void createWhenUsernameIsNullShouldUseEmptyUsername() throws Exception {
		BasicAuthorizationInterceptor interceptor = new BasicAuthorizationInterceptor(
				null, "password");
		assertEquals("", new DirectFieldAccessor(interceptor).getPropertyValue("username"));
	}

	@Test
	public void createWhenPasswordIsNullShouldUseEmptyPassword() throws Exception {
		BasicAuthorizationInterceptor interceptor = new BasicAuthorizationInterceptor(
				"username", null);
		assertEquals("", new DirectFieldAccessor(interceptor).getPropertyValue("password"));
	}

	@Test
	public void interceptShouldAddHeader() throws Exception {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		ClientHttpRequest request = requestFactory.createRequest(new URI("https://example.com"), HttpMethod.GET);
		ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class);
		byte[] body = new byte[] {};
		new BasicAuthorizationInterceptor("spring", "boot").intercept(request, body,
				execution);
		verify(execution).execute(request, body);
		assertEquals("Basic c3ByaW5nOmJvb3Q=", request.getHeaders().getFirst("Authorization"));
	}

}
