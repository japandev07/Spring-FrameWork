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

package org.springframework.context.aot

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.ThrowingConsumer
import org.junit.jupiter.api.Test
import org.springframework.aot.hint.*

/**
 * Tests for Kotlin support in [BindingReflectionHintsRegistrar].
 *
 * @author Sebastien Deleuze
 */
class KotlinBindingReflectionHintsRegistrarTests {

	private val bindingRegistrar = BindingReflectionHintsRegistrar()

	private val hints = RuntimeHints()

	@Test
	fun `Register type for Kotlinx serialization`() {
		bindingRegistrar.registerReflectionHints(hints.reflection(), SampleSerializableClass::class.java)
		assertThat(hints.reflection().typeHints()).satisfiesExactlyInAnyOrder(
			ThrowingConsumer { typeHint: TypeHint ->
				assertThat(typeHint.type).isEqualTo(TypeReference.of(String::class.java))
				assertThat(typeHint.memberCategories).isEmpty()
				assertThat(typeHint.constructors()).isEmpty()
				assertThat(typeHint.fields()).isEmpty()
				assertThat(typeHint.methods()).isEmpty()
			},
			ThrowingConsumer { typeHint: TypeHint ->
				assertThat(typeHint.type).isEqualTo(TypeReference.of(SampleSerializableClass::class.java))
				assertThat(typeHint.methods()).singleElement()
					.satisfies(ThrowingConsumer { methodHint: ExecutableHint ->
						assertThat(methodHint.name).isEqualTo("getName")
						assertThat(methodHint.modes)
							.containsOnly(ExecutableMode.INVOKE)
					})
			},
			ThrowingConsumer { typeHint: TypeHint ->
				assertThat(typeHint.type).isEqualTo(TypeReference.of(SampleSerializableClass::class.qualifiedName + "\$Companion"))
				assertThat(typeHint.methods()).singleElement()
					.satisfies(ThrowingConsumer { methodHint: ExecutableHint ->
						assertThat(methodHint.name).isEqualTo("serializer")
						assertThat(methodHint.modes).containsOnly(ExecutableMode.INVOKE)
					})
			})
	}
}

@kotlinx.serialization.Serializable
class SampleSerializableClass(val name: String)
