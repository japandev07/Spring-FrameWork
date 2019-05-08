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

/**
 * Unit tests for {@link GenericXmlContextLoader}.
 *
 * @author Sam Brannen
 * @since 4.0.4
 * @see GenericXmlContextLoaderResourceLocationsTests
 */
public class GenericXmlContextLoaderTests {

	private static final String[] EMPTY_STRING_ARRAY = new String[0];


	@Test
	public void configMustNotContainAnnotatedClasses() throws Exception {
		GenericXmlContextLoader loader = new GenericXmlContextLoader();
		MergedContextConfiguration mergedConfig = new MergedContextConfiguration(getClass(), EMPTY_STRING_ARRAY,
			new Class<?>[] { getClass() }, EMPTY_STRING_ARRAY, loader);
		assertThatIllegalStateException().isThrownBy(() ->
				loader.loadContext(mergedConfig))
			.withMessageContaining("does not support annotated classes");
	}

}
