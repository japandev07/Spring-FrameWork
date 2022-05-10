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

package org.springframework.core.annotation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.util.ClassUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link CoreAnnotationsRuntimeHintsRegistrar}.
 *
 * @author Phillip Webb
 */
class CoreAnnotationsRuntimeHintsRegistrarTests {

	private RuntimeHints hints;

	@BeforeEach
	void setup() {
		this.hints = new RuntimeHints();
		SpringFactoriesLoader.forResourceLocation("META-INF/spring/aot.factories")
				.load(RuntimeHintsRegistrar.class).forEach(registrar -> registrar
						.registerHints(this.hints, ClassUtils.getDefaultClassLoader()));
	}

	@Test
	void aliasForHasHints() {
		assertThat(this.hints.reflection().getTypeHint(TypeReference.of(AliasFor.class)))
				.satisfies((hint) -> assertThat(hint.getMemberCategories())
						.containsExactly(MemberCategory.INVOKE_DECLARED_METHODS));
	}

	@Test
	void orderAnnotationHasHints() {
		assertThat(this.hints.reflection().getTypeHint(TypeReference.of(Order.class)))
				.satisfies((hint) -> assertThat(hint.getMemberCategories())
						.containsExactly(MemberCategory.INVOKE_DECLARED_METHODS));
	}

}
