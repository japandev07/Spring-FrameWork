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

package org.springframework.context.index;

import java.io.IOException;
import java.util.Set;

import org.junit.Test;

import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;






/**
 * Tests for {@link CandidateComponentsIndexLoader}.
 *
 * @author Stephane Nicoll
 */
public class CandidateComponentsIndexLoaderTests {

	@Test
	public void validateIndexIsDisabledByDefault() {
		CandidateComponentsIndex index = CandidateComponentsIndexLoader.loadIndex(null);
		assertThat(index).as("No spring.components should be available at the default location").isNull();
	}

	@Test
	public void loadIndexSeveralMatches() {
		CandidateComponentsIndex index = CandidateComponentsIndexLoader.loadIndex(
				CandidateComponentsTestClassLoader.index(getClass().getClassLoader(),
						new ClassPathResource("spring.components", getClass())));
		Set<String> components = index.getCandidateTypes("org.springframework", "foo");
		assertThat(components).contains(
				"org.springframework.context.index.Sample1",
				"org.springframework.context.index.Sample2");
	}

	@Test
	public void loadIndexSingleMatch() {
		CandidateComponentsIndex index = CandidateComponentsIndexLoader.loadIndex(
				CandidateComponentsTestClassLoader.index(getClass().getClassLoader(),
						new ClassPathResource("spring.components", getClass())));
		Set<String> components = index.getCandidateTypes("org.springframework", "biz");
		assertThat(components).contains(
				"org.springframework.context.index.Sample3");
	}

	@Test
	public void loadIndexNoMatch() {
		CandidateComponentsIndex index = CandidateComponentsIndexLoader.loadIndex(
				CandidateComponentsTestClassLoader.index(getClass().getClassLoader(),
						new ClassPathResource("spring.components", getClass())));
		Set<String> components = index.getCandidateTypes("org.springframework", "none");
		assertThat(components).isEmpty();
	}

	@Test
	public void loadIndexNoPackage() {
		CandidateComponentsIndex index = CandidateComponentsIndexLoader.loadIndex(
				CandidateComponentsTestClassLoader.index(getClass().getClassLoader(),
						new ClassPathResource("spring.components", getClass())));
		Set<String> components = index.getCandidateTypes("com.example", "foo");
		assertThat(components).isEmpty();
	}

	@Test
	public void loadIndexNoSpringComponentsResource() {
		CandidateComponentsIndex index = CandidateComponentsIndexLoader.loadIndex(
				CandidateComponentsTestClassLoader.disableIndex(getClass().getClassLoader()));
		assertThat(index).isNull();
	}

	@Test
	public void loadIndexNoEntry() throws IOException {
		CandidateComponentsIndex index = CandidateComponentsIndexLoader.loadIndex(
				CandidateComponentsTestClassLoader.index(getClass().getClassLoader(),
						new ClassPathResource("empty-spring.components", getClass())));
		assertThat(index).isNull();
	}

	@Test
	public void loadIndexWithException() throws IOException {
		final IOException cause = new IOException("test exception");
		assertThatIllegalStateException().isThrownBy(() -> {
				CandidateComponentsTestClassLoader classLoader = new CandidateComponentsTestClassLoader(getClass().getClassLoader(), cause);
				CandidateComponentsIndexLoader.loadIndex(classLoader);
			}).withMessageContaining("Unable to load indexes").withCause(cause);
	}

}
