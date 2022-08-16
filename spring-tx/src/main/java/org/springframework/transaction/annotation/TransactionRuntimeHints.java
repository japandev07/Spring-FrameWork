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

package org.springframework.transaction.annotation;

import java.util.List;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.aot.hint.support.RuntimeHintsUtils;
import org.springframework.transaction.TransactionDefinition;

/**
 * {@link RuntimeHintsRegistrar} implementation that registers runtime hints for
 * transaction management.
 *
 * @author Sebastien Deleuze
 * @since 6.0
 * @see TransactionBeanRegistrationAotProcessor
 */
class TransactionRuntimeHints implements RuntimeHintsRegistrar {

	@Override
	public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
		RuntimeHintsUtils.registerSynthesizedAnnotation(hints, Transactional.class);
		hints.reflection()
				.registerTypes(List.of(
								TypeReference.of(Isolation.class),
								TypeReference.of(Propagation.class),
								TypeReference.of(TransactionDefinition.class)),
						builder -> builder.withMembers(MemberCategory.DECLARED_FIELDS));
	}
}
