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

package org.springframework.test.context.cache;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import org.springframework.core.SpringProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.context.cache.ContextCache.DEFAULT_MAX_CONTEXT_CACHE_SIZE;
import static org.springframework.test.context.cache.ContextCache.MAX_CONTEXT_CACHE_SIZE_PROPERTY_NAME;
import static org.springframework.test.context.cache.ContextCacheUtils.retrieveMaxCacheSize;

/**
 * Unit tests for {@link ContextCacheUtils}.
 *
 * @author Sam Brannen
 * @since 4.3
 */
public class ContextCacheUtilsTests {

	@Before
	@After
	public void clearProperties() {
		System.clearProperty(MAX_CONTEXT_CACHE_SIZE_PROPERTY_NAME);
		SpringProperties.setProperty(MAX_CONTEXT_CACHE_SIZE_PROPERTY_NAME, null);
	}

	@Test
	public void retrieveMaxCacheSizeFromDefault() {
		assertDefaultValue();
	}

	@Test
	public void retrieveMaxCacheSizeFromBogusSystemProperty() {
		System.setProperty(MAX_CONTEXT_CACHE_SIZE_PROPERTY_NAME, "bogus");
		assertDefaultValue();
	}

	@Test
	public void retrieveMaxCacheSizeFromBogusSpringProperty() {
		SpringProperties.setProperty(MAX_CONTEXT_CACHE_SIZE_PROPERTY_NAME, "bogus");
		assertDefaultValue();
	}

	@Test
	public void retrieveMaxCacheSizeFromDecimalSpringProperty() {
		SpringProperties.setProperty(MAX_CONTEXT_CACHE_SIZE_PROPERTY_NAME, "3.14");
		assertDefaultValue();
	}

	@Test
	public void retrieveMaxCacheSizeFromSystemProperty() {
		System.setProperty(MAX_CONTEXT_CACHE_SIZE_PROPERTY_NAME, "42");
		assertThat(retrieveMaxCacheSize()).isEqualTo(42);
	}

	@Test
	public void retrieveMaxCacheSizeFromSystemPropertyContainingWhitespace() {
		System.setProperty(MAX_CONTEXT_CACHE_SIZE_PROPERTY_NAME, "42\t");
		assertThat(retrieveMaxCacheSize()).isEqualTo(42);
	}

	@Test
	public void retrieveMaxCacheSizeFromSpringProperty() {
		SpringProperties.setProperty(MAX_CONTEXT_CACHE_SIZE_PROPERTY_NAME, "99");
		assertThat(retrieveMaxCacheSize()).isEqualTo(99);
	}

	private static void assertDefaultValue() {
		assertThat(retrieveMaxCacheSize()).isEqualTo(DEFAULT_MAX_CONTEXT_CACHE_SIZE);
	}

}
