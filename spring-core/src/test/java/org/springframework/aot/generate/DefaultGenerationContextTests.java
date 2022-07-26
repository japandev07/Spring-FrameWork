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

package org.springframework.aot.generate;

import java.io.IOException;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import org.springframework.aot.generate.GeneratedFiles.Kind;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.core.testfixture.aot.generate.TestTarget;
import org.springframework.javapoet.TypeSpec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Tests for {@link DefaultGenerationContext}.
 *
 * @author Phillip Webb
 * @author Stephane Nicoll
 */
class DefaultGenerationContextTests {

	private static final Consumer<TypeSpec.Builder> typeSpecCustomizer = type -> {};

	private final GeneratedClasses generatedClasses = new GeneratedClasses(
			new ClassNameGenerator(TestTarget.class));

	private final InMemoryGeneratedFiles generatedFiles = new InMemoryGeneratedFiles();

	private final RuntimeHints runtimeHints = new RuntimeHints();


	@Test
	void createWithOnlyGeneratedFilesCreatesContext() {
		DefaultGenerationContext context = new DefaultGenerationContext(
				new ClassNameGenerator(TestTarget.class), this.generatedFiles);
		assertThat(context.getGeneratedFiles()).isSameAs(this.generatedFiles);
		assertThat(context.getRuntimeHints()).isInstanceOf(RuntimeHints.class);
	}

	@Test
	void createCreatesContext() {
		DefaultGenerationContext context = new DefaultGenerationContext(
				this.generatedClasses, this.generatedFiles, this.runtimeHints);
		assertThat(context.getGeneratedFiles()).isNotNull();
		assertThat(context.getRuntimeHints()).isNotNull();
	}

	@Test
	void createWhenGeneratedClassesIsNullThrowsException() {
		assertThatIllegalArgumentException()
				.isThrownBy(() -> new DefaultGenerationContext(null, this.generatedFiles,
						this.runtimeHints))
				.withMessage("'generatedClasses' must not be null");
	}

	@Test
	void createWhenGeneratedFilesIsNullThrowsException() {
		assertThatIllegalArgumentException()
				.isThrownBy(() -> new DefaultGenerationContext(this.generatedClasses,
						null, this.runtimeHints))
				.withMessage("'generatedFiles' must not be null");
	}

	@Test
	void createWhenRuntimeHintsIsNullThrowsException() {
		assertThatIllegalArgumentException()
				.isThrownBy(() -> new DefaultGenerationContext(this.generatedClasses,
						this.generatedFiles, null))
				.withMessage("'runtimeHints' must not be null");
	}

	@Test
	void getGeneratedClassesReturnsClassNameGenerator() {
		DefaultGenerationContext context = new DefaultGenerationContext(
				this.generatedClasses, this.generatedFiles, this.runtimeHints);
		assertThat(context.getGeneratedClasses()).isSameAs(this.generatedClasses);
	}

	@Test
	void getGeneratedFilesReturnsGeneratedFiles() {
		DefaultGenerationContext context = new DefaultGenerationContext(
				this.generatedClasses, this.generatedFiles, this.runtimeHints);
		assertThat(context.getGeneratedFiles()).isSameAs(this.generatedFiles);
	}

	@Test
	void getRuntimeHintsReturnsRuntimeHints() {
		DefaultGenerationContext context = new DefaultGenerationContext(
				this.generatedClasses, this.generatedFiles, this.runtimeHints);
		assertThat(context.getRuntimeHints()).isSameAs(this.runtimeHints);
	}

	@Test
	void withNameUpdateNamingConvention() {
		DefaultGenerationContext context = new DefaultGenerationContext(
				new ClassNameGenerator(TestTarget.class), this.generatedFiles);
		GenerationContext anotherContext = context.withName("Another");
		GeneratedClass generatedClass = anotherContext.getGeneratedClasses()
				.addForFeature("Test", typeSpecCustomizer);
		assertThat(generatedClass.getName().simpleName()).endsWith("__AnotherTest");
	}

	@Test
	void withNameKeepsTrackOfAllGeneratedFiles() throws IOException {
		DefaultGenerationContext context = new DefaultGenerationContext(
				new ClassNameGenerator(TestTarget.class), this.generatedFiles);
		context.getGeneratedClasses().addForFeature("Test", typeSpecCustomizer);
		GenerationContext anotherContext = context.withName("Another");
		assertThat(anotherContext.getGeneratedClasses()).isNotSameAs(context.getGeneratedClasses());
		assertThat(anotherContext.getGeneratedFiles()).isSameAs(context.getGeneratedFiles());
		assertThat(anotherContext.getRuntimeHints()).isSameAs(context.getRuntimeHints());
		anotherContext.getGeneratedClasses().addForFeature("Test", typeSpecCustomizer);
		context.writeGeneratedContent();
		assertThat(this.generatedFiles.getGeneratedFiles(Kind.SOURCE)).hasSize(2);
	}

	@Test
	void withNameGeneratesUniqueName() throws IOException {
		DefaultGenerationContext context = new DefaultGenerationContext(
				new ClassNameGenerator(Object.class), this.generatedFiles);
		context.withName("Test").getGeneratedClasses()
				.addForFeature("Feature", typeSpecCustomizer);
		context.withName("Test").getGeneratedClasses()
				.addForFeature("Feature", typeSpecCustomizer);
		context.withName("Test").getGeneratedClasses()
				.addForFeature("Feature", typeSpecCustomizer);
		context.writeGeneratedContent();
		assertThat(this.generatedFiles.getGeneratedFiles(Kind.SOURCE)).containsOnlyKeys(
				"java/lang/Object__TestFeature.java",
				"java/lang/Object__Test1Feature.java",
				"java/lang/Object__Test2Feature.java");
	}

}
