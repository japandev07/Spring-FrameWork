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

package org.springframework.aot.hint;

import java.util.function.Predicate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ReflectionHintsPredicates}.
 *
 * @author Brian Clozel
 */
class ResourceHintsPredicatesTests {

	private final ResourceHintsPredicates resources = new ResourceHintsPredicates();

	private RuntimeHints runtimeHints;


	@BeforeEach
	void setup() {
		this.runtimeHints = new RuntimeHints();
	}

	@Test
	void resourcePatternMatchesResourceName() {
		this.runtimeHints.resources().registerPattern("/test/*");
		assertPredicateMatches(resources.forResource("/test/spring.properties"));
	}

	@Test
	void resourcePatternDoesNotMatchResourceName() {
		this.runtimeHints.resources().registerPattern("/test/spring.*");
		assertPredicateDoesNotMatch(resources.forResource("/test/other.properties"));
	}

	@Test
	void resourcePatternMatchesTypeAndResourceName() {
		this.runtimeHints.resources().registerPattern("/org/springframework/aot/hint/spring.*");
		assertPredicateMatches(resources.forResource(TypeReference.of(getClass()), "spring.properties"));
	}

	@Test
	void resourcePatternDoesNotMatchTypeAndResourceName() {
		this.runtimeHints.resources().registerPattern("/spring.*");
		assertPredicateDoesNotMatch(resources.forResource(TypeReference.of(getClass()), "spring.properties"));
	}

	@Test
	void resourceBundleMatchesBundleName() {
		this.runtimeHints.resources().registerResourceBundle("spring");
		assertPredicateMatches(resources.forBundle("spring"));
	}

	@Test
	void resourceBundleDoesNotMatchBundleName() {
		this.runtimeHints.resources().registerResourceBundle("spring");
		assertPredicateDoesNotMatch(resources.forBundle("other"));
	}


	private void assertPredicateMatches(Predicate<RuntimeHints> predicate) {
		assertThat(predicate.test(this.runtimeHints)).isTrue();
	}

	private void assertPredicateDoesNotMatch(Predicate<RuntimeHints> predicate) {
		assertThat(predicate.test(this.runtimeHints)).isFalse();
	}

}
