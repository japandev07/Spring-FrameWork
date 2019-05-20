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

package org.springframework.web.cors;

import org.junit.Test;

import org.springframework.http.HttpMethod;
import org.springframework.mock.web.test.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Unit tests for {@link UrlBasedCorsConfigurationSource}.
 * @author Sebastien Deleuze
 */
public class UrlBasedCorsConfigurationSourceTests {

	private final UrlBasedCorsConfigurationSource configSource = new UrlBasedCorsConfigurationSource();

	@Test
	public void empty() {
		MockHttpServletRequest request = new MockHttpServletRequest(HttpMethod.GET.name(), "/bar/test.html");
		assertNull(this.configSource.getCorsConfiguration(request));
	}

	@Test
	public void registerAndMatch() {
		CorsConfiguration config = new CorsConfiguration();
		this.configSource.registerCorsConfiguration("/bar/**", config);

		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/foo/test.html");
		assertNull(this.configSource.getCorsConfiguration(request));

		request.setRequestURI("/bar/test.html");
		assertEquals(config, this.configSource.getCorsConfiguration(request));
	}

	@Test
	public void unmodifiableConfigurationsMap() {
		assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() ->
				this.configSource.getCorsConfigurations().put("/**", new CorsConfiguration()));
	}

}
