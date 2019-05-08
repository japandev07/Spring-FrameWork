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

package org.springframework.test.context.support;

import org.junit.Test;

import org.springframework.test.context.MergedContextConfiguration;

import static org.assertj.core.api.Assertions.*;
import static org.junit.Assert.*;

/**
 * Unit tests for {@link AnnotationConfigContextLoader}.
 *
 * @author Sam Brannen
 * @since 3.1
 */
public class AnnotationConfigContextLoaderTests {

	private final AnnotationConfigContextLoader contextLoader = new AnnotationConfigContextLoader();

	private static final String[] EMPTY_STRING_ARRAY = new String[0];
	private static final Class<?>[] EMPTY_CLASS_ARRAY = new Class<?>[0];


	/**
	 * @since 4.0.4
	 */
	@Test
	public void configMustNotContainLocations() throws Exception {
		MergedContextConfiguration mergedConfig = new MergedContextConfiguration(getClass(),
			new String[] { "config.xml" }, EMPTY_CLASS_ARRAY, EMPTY_STRING_ARRAY, contextLoader);
		assertThatIllegalStateException().isThrownBy(() ->
				contextLoader.loadContext(mergedConfig))
			.withMessageContaining("does not support resource locations");
	}

	@Test
	public void detectDefaultConfigurationClassesForAnnotatedInnerClass() {
		Class<?>[] configClasses = contextLoader.detectDefaultConfigurationClasses(ContextConfigurationInnerClassTestCase.class);
		assertNotNull(configClasses);
		assertEquals("annotated static ContextConfiguration should be considered.", 1, configClasses.length);

		configClasses = contextLoader.detectDefaultConfigurationClasses(AnnotatedFooConfigInnerClassTestCase.class);
		assertNotNull(configClasses);
		assertEquals("annotated static FooConfig should be considered.", 1, configClasses.length);
	}

	@Test
	public void detectDefaultConfigurationClassesForMultipleAnnotatedInnerClasses() {
		Class<?>[] configClasses = contextLoader.detectDefaultConfigurationClasses(MultipleStaticConfigurationClassesTestCase.class);
		assertNotNull(configClasses);
		assertEquals("multiple annotated static classes should be considered.", 2, configClasses.length);
	}

	@Test
	public void detectDefaultConfigurationClassesForNonAnnotatedInnerClass() {
		Class<?>[] configClasses = contextLoader.detectDefaultConfigurationClasses(PlainVanillaFooConfigInnerClassTestCase.class);
		assertNotNull(configClasses);
		assertEquals("non-annotated static FooConfig should NOT be considered.", 0, configClasses.length);
	}

	@Test
	public void detectDefaultConfigurationClassesForFinalAnnotatedInnerClass() {
		Class<?>[] configClasses = contextLoader.detectDefaultConfigurationClasses(FinalConfigInnerClassTestCase.class);
		assertNotNull(configClasses);
		assertEquals("final annotated static Config should NOT be considered.", 0, configClasses.length);
	}

	@Test
	public void detectDefaultConfigurationClassesForPrivateAnnotatedInnerClass() {
		Class<?>[] configClasses = contextLoader.detectDefaultConfigurationClasses(PrivateConfigInnerClassTestCase.class);
		assertNotNull(configClasses);
		assertEquals("private annotated inner classes should NOT be considered.", 0, configClasses.length);
	}

	@Test
	public void detectDefaultConfigurationClassesForNonStaticAnnotatedInnerClass() {
		Class<?>[] configClasses = contextLoader.detectDefaultConfigurationClasses(NonStaticConfigInnerClassesTestCase.class);
		assertNotNull(configClasses);
		assertEquals("non-static annotated inner classes should NOT be considered.", 0, configClasses.length);
	}

}
