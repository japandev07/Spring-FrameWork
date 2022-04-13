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

package org.springframework.aot.test.generator.compile;

import org.junit.jupiter.api.Test;

import org.springframework.aot.test.generator.file.ResourceFiles;
import org.springframework.aot.test.generator.file.SourceFiles;

import static org.assertj.core.api.Assertions.assertThat;


/**
 * Tests for {@link CompilationException}.
 *
 * @author Phillip Webb
 * @since 6.0
 */
class CompilationExceptionTests {

	@Test
	void getMessageReturnsMessage() {
		CompilationException exception = new CompilationException("message", SourceFiles.none(), ResourceFiles.none());
		assertThat(exception).hasMessageContaining("message");
	}

}
