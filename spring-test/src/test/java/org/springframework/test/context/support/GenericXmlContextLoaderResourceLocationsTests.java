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

import java.util.Arrays;
import java.util.Collection;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.ContextLoader;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test which verifies proper
 * {@link ContextLoader#processLocations(Class, String...) processing} of
 * {@code resource locations} by a {@link GenericXmlContextLoader}
 * configured via {@link ContextConfiguration @ContextConfiguration}.
 * Specifically, this test addresses the issues raised in <a
 * href="https://opensource.atlassian.com/projects/spring/browse/SPR-3949"
 * target="_blank">SPR-3949</a>:
 * <em>ContextConfiguration annotation should accept not only classpath resources</em>.
 *
 * @author Sam Brannen
 * @since 2.5
 */
class GenericXmlContextLoaderResourceLocationsTests {

	private static final Log logger = LogFactory.getLog(GenericXmlContextLoaderResourceLocationsTests.class);


	static Collection<Object[]> contextConfigurationLocationsData() {
		@ContextConfiguration
		class ClasspathNonExistentDefaultLocationsTestCase {
		}

		@ContextConfiguration
		class ClasspathExistentDefaultLocationsTestCase {
		}

		@ContextConfiguration({ "context1.xml", "context2.xml" })
		class ImplicitClasspathLocationsTestCase {
		}

		@ContextConfiguration("classpath:context.xml")
		class ExplicitClasspathLocationsTestCase {
		}

		@ContextConfiguration("file:/testing/directory/context.xml")
		class ExplicitFileLocationsTestCase {
		}

		@ContextConfiguration("https://example.com/context.xml")
		class ExplicitUrlLocationsTestCase {
		}

		@ContextConfiguration({ "context1.xml", "classpath:context2.xml", "/context3.xml",
			"file:/testing/directory/context.xml", "https://example.com/context.xml" })
		class ExplicitMixedPathTypesLocationsTestCase {
		}

		return Arrays.asList(new Object[][] {

			{ ClasspathNonExistentDefaultLocationsTestCase.class.getSimpleName(), new String[] {} },

			{
				ClasspathExistentDefaultLocationsTestCase.class.getSimpleName(),
				new String[] { "classpath:org/springframework/test/context/support/GenericXmlContextLoaderResourceLocationsTests$1ClasspathExistentDefaultLocationsTestCase-context.xml" } },

			{
				ImplicitClasspathLocationsTestCase.class.getSimpleName(),
				new String[] { "classpath:/org/springframework/test/context/support/context1.xml",
					"classpath:/org/springframework/test/context/support/context2.xml" } },

			{ ExplicitClasspathLocationsTestCase.class.getSimpleName(), new String[] { "classpath:context.xml" } },

			{ ExplicitFileLocationsTestCase.class.getSimpleName(), new String[] { "file:/testing/directory/context.xml" } },

			{ ExplicitUrlLocationsTestCase.class.getSimpleName(), new String[] { "https://example.com/context.xml" } },

			{
				ExplicitMixedPathTypesLocationsTestCase.class.getSimpleName(),
				new String[] { "classpath:/org/springframework/test/context/support/context1.xml",
					"classpath:context2.xml", "classpath:/context3.xml", "file:/testing/directory/context.xml",
					"https://example.com/context.xml" } }

		});
	}


	@ParameterizedTest(name = "{0}")
	@MethodSource("contextConfigurationLocationsData")
	void assertContextConfigurationLocations(String testClassName, String[] expectedLocations) throws Exception {
		Class<?> testClass = ClassUtils.forName(getClass().getName() + "$1" + testClassName, getClass().getClassLoader());

		final ContextConfiguration contextConfig = testClass.getAnnotation(ContextConfiguration.class);
		final ContextLoader contextLoader = new GenericXmlContextLoader();
		final String[] configuredLocations = (String[]) AnnotationUtils.getValue(contextConfig);
		final String[] processedLocations = contextLoader.processLocations(testClass, configuredLocations);

		if (logger.isDebugEnabled()) {
			logger.debug("----------------------------------------------------------------------");
			logger.debug("Configured locations: " + ObjectUtils.nullSafeToString(configuredLocations));
			logger.debug("Expected   locations: " + ObjectUtils.nullSafeToString(expectedLocations));
			logger.debug("Processed  locations: " + ObjectUtils.nullSafeToString(processedLocations));
		}

		assertThat(processedLocations).as("Verifying locations for test [" + testClass + "].").isEqualTo(expectedLocations);
	}

}
