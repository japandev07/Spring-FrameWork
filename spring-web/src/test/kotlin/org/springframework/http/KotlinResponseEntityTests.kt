/*
 * Copyright 2002-2023 the original author or authors.
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

package org.springframework.http

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test

/**
 * Kotlin tests for [ResponseEntity].
 *
 * @author Sebastien Deleuze
 */
class KotlinResponseEntityTests {

	@Test
	fun ofNullable() {
		val entity = 42
		val responseEntity = ResponseEntity.ofNullable(entity)
		Assertions.assertThat(responseEntity).isNotNull()
		Assertions.assertThat(responseEntity.statusCode).isEqualTo(HttpStatus.OK)
		Assertions.assertThat(responseEntity.body as Int).isEqualTo(entity)
	}

	@Test
	fun ofNullNullable() {
		val responseEntity = ResponseEntity.ofNullable<Int>(null)
		Assertions.assertThat(responseEntity).isNotNull()
		Assertions.assertThat(responseEntity.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
		Assertions.assertThat(responseEntity.body).isNull()
	}

}
