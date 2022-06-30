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

package org.springframework.http.converter.json;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.ReflectionHints;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;

/**
 * {@link RuntimeHintsRegistrar} implementation that registers reflection entries
 * for {@link Jackson2ObjectMapperBuilder} well-known modules.
 *
 * @author Sebastien Deleuze
 * @since 6.0
 */
public class JacksonBuilderRuntimeHintsRegistrar implements RuntimeHintsRegistrar {
	@Override
	public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
		ReflectionHints reflectionHints = hints.reflection();
		reflectionHints.registerType(TypeReference.of("com.fasterxml.jackson.datatype.jdk8.Jdk8Module"),
				builder -> builder.withMembers(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS));
		reflectionHints.registerType(TypeReference.of("com.fasterxml.jackson.datatype.jsr310.JavaTimeModule"),
				builder -> builder.withMembers(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS));
		reflectionHints.registerType(TypeReference.of("com.fasterxml.jackson.module.kotlin.KotlinModule"),
				builder -> builder.withMembers(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS));
	}
}
