/*
 * Copyright 2002-2020 the original author or authors.
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;

import org.springframework.http.HttpMethod;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Unit tests for {@link CorsConfiguration}.
 *
 * @author Sebastien Deleuze
 * @author Sam Brannen
 */
public class CorsConfigurationTests {

	@Test
	public void setNullValues() {
		CorsConfiguration config = new CorsConfiguration();
		config.setAllowedOrigins(null);
		assertThat(config.getAllowedOrigins()).isNull();
		config.setAllowedHeaders(null);
		assertThat(config.getAllowedHeaders()).isNull();
		config.setAllowedMethods(null);
		assertThat(config.getAllowedMethods()).isNull();
		config.setExposedHeaders(null);
		assertThat(config.getExposedHeaders()).isNull();
		config.setAllowCredentials(null);
		assertThat(config.getAllowCredentials()).isNull();
		config.setMaxAge((Long) null);
		assertThat(config.getMaxAge()).isNull();
		config.setAllowedOriginPatterns(null);
		assertThat(config.getAllowedOriginPatterns()).isNull();
	}

	@Test
	public void setValues() {
		CorsConfiguration config = new CorsConfiguration();
		config.addAllowedOrigin("*");
		assertThat(config.getAllowedOrigins()).containsExactly("*");
		config.addAllowedHeader("*");
		assertThat(config.getAllowedHeaders()).containsExactly("*");
		config.addAllowedMethod("*");
		assertThat(config.getAllowedMethods()).containsExactly("*");
		config.addExposedHeader("header1");
		config.addExposedHeader("header2");
		assertThat(config.getExposedHeaders()).containsExactly("header1", "header2");
		config.setAllowCredentials(true);
		assertThat(config.getAllowCredentials()).isTrue();
		config.setMaxAge(123L);
		assertThat(config.getMaxAge()).isEqualTo(new Long(123));
		config.addAllowedOriginPattern(".*\\.example\\.com");
		assertThat(config.getAllowedOriginPatterns()).containsExactly(".*\\.example\\.com");
	}

	@Test
	public void asteriskWildCardOnAddExposedHeader() {
		CorsConfiguration config = new CorsConfiguration();
		assertThatIllegalArgumentException().isThrownBy(() ->
				config.addExposedHeader("*"));
	}

	@Test
	public void asteriskWildCardOnSetExposedHeaders() {
		CorsConfiguration config = new CorsConfiguration();
		assertThatIllegalArgumentException()
				.isThrownBy(() -> config.setExposedHeaders(Collections.singletonList("*")));
	}

	@Test
	public void combineWithNull() {
		CorsConfiguration config = new CorsConfiguration();
		config.setAllowedOrigins(Collections.singletonList("*"));
		config.combine(null);
		assertThat(config.getAllowedOrigins()).containsExactly("*");
	}

	@Test
	public void combineWithNullProperties() {
		CorsConfiguration config = new CorsConfiguration();
		config.addAllowedOrigin("*");
		config.addAllowedHeader("header1");
		config.addExposedHeader("header3");
		config.addAllowedMethod(HttpMethod.GET.name());
		config.setMaxAge(123L);
		config.setAllowCredentials(true);
		config.setAllowedOriginPatterns(Collections.singletonList(".*\\.example\\.com"));
		CorsConfiguration other = new CorsConfiguration();
		config = config.combine(other);
		assertThat(config).isNotNull();
		assertThat(config.getAllowedOrigins()).containsExactly("*");
		assertThat(config.getAllowedHeaders()).containsExactly("header1");
		assertThat(config.getExposedHeaders()).containsExactly("header3");
		assertThat(config.getAllowedMethods()).containsExactly(HttpMethod.GET.name());
		assertThat(config.getMaxAge()).isEqualTo(new Long(123));
		assertThat(config.getAllowCredentials()).isTrue();
		assertThat(config.getAllowedOriginPatterns()).containsExactly(".*\\.example\\.com");
	}

	@Test  // SPR-15772
	public void combineWithDefaultPermitValues() {
		CorsConfiguration config = new CorsConfiguration().applyPermitDefaultValues();
		CorsConfiguration other = new CorsConfiguration();
		other.addAllowedOrigin("https://domain.com");
		other.addAllowedHeader("header1");
		other.addAllowedMethod(HttpMethod.PUT.name());

		CorsConfiguration combinedConfig = config.combine(other);
		assertThat(combinedConfig).isNotNull();
		assertThat(combinedConfig.getAllowedOrigins()).containsExactly("https://domain.com");
		assertThat(combinedConfig.getAllowedHeaders()).containsExactly("header1");
		assertThat(combinedConfig.getAllowedMethods()).containsExactly(HttpMethod.PUT.name());

		combinedConfig = other.combine(config);
		assertThat(combinedConfig).isNotNull();
		assertThat(combinedConfig.getAllowedOrigins()).containsExactly("https://domain.com");
		assertThat(combinedConfig.getAllowedHeaders()).containsExactly("header1");
		assertThat(combinedConfig.getAllowedMethods()).containsExactly(HttpMethod.PUT.name());

		combinedConfig = config.combine(new CorsConfiguration());
		assertThat(config.getAllowedOrigins()).containsExactly("*");
		assertThat(config.getAllowedHeaders()).containsExactly("*");
		assertThat(combinedConfig).isNotNull();
		assertThat(combinedConfig.getAllowedMethods())
				.containsExactly(HttpMethod.GET.name(), HttpMethod.HEAD.name(), HttpMethod.POST.name());

		combinedConfig = new CorsConfiguration().combine(config);
		assertThat(config.getAllowedOrigins()).containsExactly("*");
		assertThat(config.getAllowedHeaders()).containsExactly("*");
		assertThat(combinedConfig).isNotNull();
		assertThat(combinedConfig.getAllowedMethods())
				.containsExactly(HttpMethod.GET.name(), HttpMethod.HEAD.name(), HttpMethod.POST.name());
	}

	@Test
	public void combinePatternWithDefaultPermitValues() {
		CorsConfiguration config = new CorsConfiguration().applyPermitDefaultValues();
		CorsConfiguration other = new CorsConfiguration();
		other.addAllowedOriginPattern(".*\\.com");

		CorsConfiguration combinedConfig = other.combine(config);
		assertThat(combinedConfig).isNotNull();
		assertThat(combinedConfig.getAllowedOrigins()).isNull();
		assertThat(combinedConfig.getAllowedOriginPatterns()).containsExactly(".*\\.com");

		combinedConfig = config.combine(other);
		assertThat(combinedConfig).isNotNull();
		assertThat(combinedConfig.getAllowedOrigins()).isNull();
		assertThat(combinedConfig.getAllowedOriginPatterns()).containsExactly(".*\\.com");
	}

	@Test
	public void combinePatternWithDefaultPermitValuesAndCustomOrigin() {
		CorsConfiguration config = new CorsConfiguration().applyPermitDefaultValues();
		config.setAllowedOrigins(Collections.singletonList("https://domain.com"));
		CorsConfiguration other = new CorsConfiguration();
		other.addAllowedOriginPattern(".*\\.com");

		CorsConfiguration combinedConfig = other.combine(config);
		assertThat(combinedConfig).isNotNull();
		assertThat(combinedConfig.getAllowedOrigins()).containsExactly("https://domain.com");
		assertThat(combinedConfig.getAllowedOriginPatterns()).containsExactly(".*\\.com");

		combinedConfig = config.combine(other);
		assertThat(combinedConfig).isNotNull();
		assertThat(combinedConfig.getAllowedOrigins()).containsExactly("https://domain.com");
		assertThat(combinedConfig.getAllowedOriginPatterns()).containsExactly(".*\\.com");
	}

	@Test
	public void combineWithAsteriskWildCard() {
		CorsConfiguration config = new CorsConfiguration();
		config.addAllowedOrigin("*");
		config.addAllowedHeader("*");
		config.addAllowedMethod("*");
		config.addAllowedOriginPattern(".*");
		CorsConfiguration other = new CorsConfiguration();
		other.addAllowedOrigin("https://domain.com");
		other.addAllowedHeader("header1");
		other.addExposedHeader("header2");
		other.addAllowedOriginPattern(".*\\.company\\.com");
		other.addAllowedMethod(HttpMethod.PUT.name());
		CorsConfiguration combinedConfig = config.combine(other);
		assertThat(combinedConfig).isNotNull();
		assertThat(combinedConfig.getAllowedOrigins()).containsExactly("*");
		assertThat(combinedConfig.getAllowedHeaders()).containsExactly("*");
		assertThat(combinedConfig.getAllowedMethods()).containsExactly("*");
		assertThat(combinedConfig.getAllowedOriginPatterns()).containsExactly(".*");
		combinedConfig = other.combine(config);
		assertThat(combinedConfig).isNotNull();
		assertThat(combinedConfig.getAllowedOrigins()).containsExactly("*");
		assertThat(combinedConfig.getAllowedHeaders()).containsExactly("*");
		assertThat(combinedConfig.getAllowedMethods()).containsExactly("*");
		assertThat(combinedConfig.getAllowedOriginPatterns()).containsExactly(".*");
	}

	@Test  // SPR-14792
	public void combineWithDuplicatedElements() {
		CorsConfiguration config = new CorsConfiguration();
		config.addAllowedOrigin("https://domain1.com");
		config.addAllowedOrigin("https://domain2.com");
		config.addAllowedHeader("header1");
		config.addAllowedHeader("header2");
		config.addExposedHeader("header3");
		config.addExposedHeader("header4");
		config.addAllowedMethod(HttpMethod.GET.name());
		config.addAllowedMethod(HttpMethod.PUT.name());
		config.addAllowedOriginPattern(".*\\.domain1\\.com");
		config.addAllowedOriginPattern(".*\\.domain2\\.com");
		CorsConfiguration other = new CorsConfiguration();
		other.addAllowedOrigin("https://domain1.com");
		other.addAllowedHeader("header1");
		other.addExposedHeader("header3");
		other.addAllowedMethod(HttpMethod.GET.name());
		other.addAllowedOriginPattern(".*\\.domain1\\.com");
		CorsConfiguration combinedConfig = config.combine(other);
		assertThat(combinedConfig).isNotNull();
		assertThat(combinedConfig.getAllowedOrigins()).containsExactly("https://domain1.com", "https://domain2.com");
		assertThat(combinedConfig.getAllowedHeaders()).containsExactly("header1", "header2");
		assertThat(combinedConfig.getExposedHeaders()).containsExactly("header3", "header4");
		assertThat(combinedConfig.getAllowedMethods()).containsExactly(HttpMethod.GET.name(), HttpMethod.PUT.name());
		assertThat(combinedConfig.getAllowedOriginPatterns()).containsExactly(".*\\.domain1\\.com", ".*\\.domain2\\.com");
	}

	@Test
	public void combine() {
		CorsConfiguration config = new CorsConfiguration();
		config.addAllowedOrigin("https://domain1.com");
		config.addAllowedHeader("header1");
		config.addExposedHeader("header3");
		config.addAllowedMethod(HttpMethod.GET.name());
		config.setMaxAge(123L);
		config.setAllowCredentials(true);
		config.addAllowedOriginPattern(".*\\.domain1\\.com");
		CorsConfiguration other = new CorsConfiguration();
		other.addAllowedOrigin("https://domain2.com");
		other.addAllowedHeader("header2");
		other.addExposedHeader("header4");
		other.addAllowedMethod(HttpMethod.PUT.name());
		other.setMaxAge(456L);
		other.setAllowCredentials(false);
		other.addAllowedOriginPattern(".*\\.domain2\\.com");
		config = config.combine(other);
		assertThat(config).isNotNull();
		assertThat(config.getAllowedOrigins()).containsExactly("https://domain1.com", "https://domain2.com");
		assertThat(config.getAllowedHeaders()).containsExactly("header1", "header2");
		assertThat(config.getExposedHeaders()).containsExactly("header3", "header4");
		assertThat(config.getAllowedMethods()).containsExactly(HttpMethod.GET.name(), HttpMethod.PUT.name());
		assertThat(config.getMaxAge()).isEqualTo(new Long(456));
		assertThat(config).isNotNull();
		assertThat(config.getAllowCredentials()).isFalse();
		assertThat(config.getAllowedOriginPatterns()).containsExactly(".*\\.domain1\\.com", ".*\\.domain2\\.com");
	}

	@Test
	public void checkOriginAllowed() {
		CorsConfiguration config = new CorsConfiguration();
		config.setAllowedOrigins(Collections.singletonList("*"));
		assertThat(config.checkOrigin("https://domain.com")).isEqualTo("*");
		config.setAllowCredentials(true);
		assertThat(config.checkOrigin("https://domain.com")).isEqualTo("https://domain.com");
		config.setAllowedOrigins(Collections.singletonList("https://domain.com"));
		assertThat(config.checkOrigin("https://domain.com")).isEqualTo("https://domain.com");
		config.setAllowCredentials(false);
		assertThat(config.checkOrigin("https://domain.com")).isEqualTo("https://domain.com");
	}

	@Test
	public void checkOriginNotAllowed() {
		CorsConfiguration config = new CorsConfiguration();
		assertThat(config.checkOrigin(null)).isNull();
		assertThat(config.checkOrigin("https://domain.com")).isNull();
		config.addAllowedOrigin("*");
		assertThat(config.checkOrigin(null)).isNull();
		config.setAllowedOrigins(Collections.singletonList("https://domain1.com"));
		assertThat(config.checkOrigin("https://domain2.com")).isNull();
		config.setAllowedOrigins(new ArrayList<>());
		assertThat(config.checkOrigin("https://domain.com")).isNull();
	}

	@Test
	public void checkOriginPatternAllowed() {
		CorsConfiguration config = new CorsConfiguration();
		config.setAllowedOriginPatterns(Collections.singletonList(".*"));
		assertThat(config.checkOrigin("https://domain.com")).isEqualTo("*");
		config.setAllowCredentials(true);
		assertThat(config.checkOrigin("https://domain.com")).isEqualTo("https://domain.com");
		config.setAllowedOriginPatterns(Collections.singletonList(".*\\.domain\\.com"));
		assertThat(config.checkOrigin("https://example.domain.com")).isEqualTo("https://example.domain.com");
		config.setAllowCredentials(false);
		assertThat(config.checkOrigin("https://example.domain.com")).isEqualTo("https://example.domain.com");
	}

	@Test
	public void checkOriginPatternNotAllowed() {
		CorsConfiguration config = new CorsConfiguration();
		assertThat(config.checkOrigin(null)).isNull();
		assertThat(config.checkOrigin("https://domain.com")).isNull();
		config.addAllowedOriginPattern(".*");
		assertThat(config.checkOrigin(null)).isNull();
		config.setAllowedOriginPatterns(Collections.singletonList(".*\\.domain1\\.com"));
		assertThat(config.checkOrigin("https://domain2.com")).isNull();
		config.setAllowedOriginPatterns(new ArrayList<>());
		assertThat(config.checkOrigin("https://domain.com")).isNull();
	}

	@Test
	public void checkMethodAllowed() {
		CorsConfiguration config = new CorsConfiguration();
		assertThat(config.checkHttpMethod(HttpMethod.GET)).containsExactly(HttpMethod.GET, HttpMethod.HEAD);
		config.addAllowedMethod("GET");
		assertThat(config.checkHttpMethod(HttpMethod.GET)).containsExactly(HttpMethod.GET);
		config.addAllowedMethod("POST");
		assertThat(config.checkHttpMethod(HttpMethod.GET)).containsExactly(HttpMethod.GET, HttpMethod.POST);
		assertThat(config.checkHttpMethod(HttpMethod.POST)).containsExactly(HttpMethod.GET, HttpMethod.POST);
	}

	@Test
	public void checkMethodNotAllowed() {
		CorsConfiguration config = new CorsConfiguration();
		assertThat(config.checkHttpMethod(null)).isNull();
		assertThat(config.checkHttpMethod(HttpMethod.DELETE)).isNull();
		config.setAllowedMethods(new ArrayList<>());
		assertThat(config.checkHttpMethod(HttpMethod.POST)).isNull();
	}

	@Test
	public void checkHeadersAllowed() {
		CorsConfiguration config = new CorsConfiguration();
		assertThat(config.checkHeaders(Collections.emptyList())).isEqualTo(Collections.emptyList());
		config.addAllowedHeader("header1");
		config.addAllowedHeader("header2");
		assertThat(config.checkHeaders(Collections.singletonList("header1"))).containsExactly("header1");
		assertThat(config.checkHeaders(Arrays.asList("header1", "header2"))).containsExactly("header1", "header2");
		assertThat(config.checkHeaders(Arrays.asList("header1", "header2", "header3"))).containsExactly("header1", "header2");
	}

	@Test
	public void checkHeadersNotAllowed() {
		CorsConfiguration config = new CorsConfiguration();
		assertThat(config.checkHeaders(null)).isNull();
		assertThat(config.checkHeaders(Collections.singletonList("header1"))).isNull();
		config.setAllowedHeaders(Collections.emptyList());
		assertThat(config.checkHeaders(Collections.singletonList("header1"))).isNull();
		config.addAllowedHeader("header2");
		config.addAllowedHeader("header3");
		assertThat(config.checkHeaders(Collections.singletonList("header1"))).isNull();
	}

	@Test  // SPR-15772
	public void changePermitDefaultValues() {
		CorsConfiguration config = new CorsConfiguration().applyPermitDefaultValues();
		config.addAllowedOrigin("https://domain.com");
		config.addAllowedHeader("header1");
		config.addAllowedMethod("PATCH");
		assertThat(config.getAllowedOrigins()).containsExactly("*", "https://domain.com");
		assertThat(config.getAllowedHeaders()).containsExactly("*", "header1");
		assertThat(config.getAllowedMethods()).containsExactly("GET", "HEAD", "POST", "PATCH");
	}

	@Test
	public void permitDefaultDoesntSetOriginWhenPatternPresent() {
		CorsConfiguration config = new CorsConfiguration();
		config.addAllowedOriginPattern(".*\\.com");
		config = config.applyPermitDefaultValues();
		assertThat(config.getAllowedOrigins()).isNull();
		assertThat(config.getAllowedOriginPatterns()).containsExactly(".*\\.com");
	}
}
