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

package org.springframework.test.context.aot.hint;

import java.util.Arrays;
import java.util.List;

import org.springframework.aot.hint.RuntimeHints;
import org.springframework.test.context.ContextLoader;
import org.springframework.test.context.MergedContextConfiguration;
import org.springframework.test.context.aot.TestRuntimeHintsRegistrar;
import org.springframework.test.context.web.WebMergedContextConfiguration;

import static org.springframework.aot.hint.MemberCategory.INVOKE_DECLARED_CONSTRUCTORS;
import static org.springframework.util.ResourceUtils.CLASSPATH_URL_PREFIX;

/**
 * {@link TestRuntimeHintsRegistrar} implementation that registers run-time hints
 * for standard functionality in the <em>Spring TestContext Framework</em>.
 *
 * @author Sam Brannen
 * @since 6.0
 * @see TestContextRuntimeHints
 */
class StandardTestRuntimeHints implements TestRuntimeHintsRegistrar {

	private static final String SLASH = "/";


	@Override
	public void registerHints(RuntimeHints runtimeHints, MergedContextConfiguration mergedConfig,
			List<Class<?>> testClasses, ClassLoader classLoader) {

		registerHintsForMergedContextConfiguration(runtimeHints, mergedConfig);
	}

	private void registerHintsForMergedContextConfiguration(
			RuntimeHints runtimeHints, MergedContextConfiguration mergedConfig) {

		// @ContextConfiguration(loader = ...)
		ContextLoader contextLoader = mergedConfig.getContextLoader();
		if (contextLoader != null) {
			registerDeclaredConstructors(runtimeHints, contextLoader.getClass());
		}

		// @ContextConfiguration(initializers = ...)
		mergedConfig.getContextInitializerClasses()
				.forEach(clazz -> registerDeclaredConstructors(runtimeHints, clazz));

		// @ContextConfiguration(locations = ...)
		registerClasspathResources(runtimeHints, mergedConfig.getLocations());

		// @TestPropertySource(locations = ... )
		registerClasspathResources(runtimeHints, mergedConfig.getPropertySourceLocations());

		// @WebAppConfiguration(value = ...)
		if (mergedConfig instanceof WebMergedContextConfiguration webConfig) {
			registerClasspathResourceDirectoryStructure(runtimeHints, webConfig.getResourceBasePath());
		}
	}

	private void registerDeclaredConstructors(RuntimeHints runtimeHints, Class<?> type) {
		runtimeHints.reflection().registerType(type, INVOKE_DECLARED_CONSTRUCTORS);
	}

	private void registerClasspathResources(RuntimeHints runtimeHints, String... locations) {
		Arrays.stream(locations)
				.filter(location -> location.startsWith(CLASSPATH_URL_PREFIX))
				.map(this::cleanClasspathResource)
				.forEach(runtimeHints.resources()::registerPattern);
	}

	private void registerClasspathResourceDirectoryStructure(RuntimeHints runtimeHints, String directory) {
		if (directory.startsWith(CLASSPATH_URL_PREFIX)) {
			String pattern = cleanClasspathResource(directory);
			if (!pattern.endsWith(SLASH)) {
				pattern += SLASH;
			}
			pattern += "*";
			runtimeHints.resources().registerPattern(pattern);
		}
	}

	private String cleanClasspathResource(String location) {
		location = location.substring(CLASSPATH_URL_PREFIX.length());
		if (!location.startsWith(SLASH)) {
			location = SLASH + location;
		}
		return location;
	}

}
