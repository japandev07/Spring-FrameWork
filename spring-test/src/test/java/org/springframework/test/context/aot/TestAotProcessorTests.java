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

package org.springframework.test.context.aot;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.test.context.aot.samples.basic.BasicSpringJupiterSharedConfigTests;
import org.springframework.test.context.aot.samples.basic.BasicSpringJupiterTests;
import org.springframework.test.context.aot.samples.basic.BasicSpringTestNGTests;
import org.springframework.test.context.aot.samples.basic.BasicSpringVintageTests;
import org.springframework.util.ClassUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link TestAotProcessor}.
 *
 * @author Sam Brannen
 * @since 6.0
 */
class TestAotProcessorTests extends AbstractAotTests {

	@Test
	void process(@TempDir(cleanup = CleanupMode.ON_SUCCESS) Path tempDir) throws Exception {
		// Limit the scope of this test by creating a new classpath root on the fly.
		Path classpathRoot = Files.createDirectories(tempDir.resolve("build/classes"));
		Stream.of(
				BasicSpringJupiterSharedConfigTests.class,
				BasicSpringJupiterTests.class,
				BasicSpringJupiterTests.NestedTests.class,
				BasicSpringTestNGTests.class,
				BasicSpringVintageTests.class
			).forEach(testClass -> copy(testClass, classpathRoot));

		Path[] classpathRoots = { classpathRoot };
		Path sourceOutput = tempDir.resolve("generated/sources");
		Path resourceOutput = tempDir.resolve("generated/resources");
		Path classOutput = tempDir.resolve("generated/classes");
		String groupId = "org.example";
		String artifactId = "app-tests";

		TestAotProcessor processor = new TestAotProcessor(classpathRoots, sourceOutput, resourceOutput, classOutput, groupId, artifactId);
		processor.process();

		assertThat(findFiles(sourceOutput)).containsExactlyInAnyOrder(
				expectedSourceFilesForBasicSpringTests);

		assertThat(findFiles(resourceOutput)).contains(
				"META-INF/native-image/org.example/app-tests/reflect-config.json",
				"META-INF/native-image/org.example/app-tests/resource-config.json");
	}

	private void copy(Class<?> testClass, Path destination) {
		String classFilename = ClassUtils.convertClassNameToResourcePath(testClass.getName()) + ".class";
		Path source = classpathRoot(testClass).resolve(classFilename);
		Path target = destination.resolve(classFilename);
		try {
			Files.createDirectories(target.getParent());
			Files.copy(source, target);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	private static List<String> findFiles(Path outputPath) throws IOException {
		int prefixLength = outputPath.toFile().getAbsolutePath().length() + 1;
		return Files.find(outputPath, Integer.MAX_VALUE, (path, attributes) -> attributes.isRegularFile())
				.map(Path::toAbsolutePath)
				.map(Path::toString)
				.map(path -> path.substring(prefixLength))
				.map(path -> path.replace('\\', '/')) // convert Windows path
				.toList();
	}

}
