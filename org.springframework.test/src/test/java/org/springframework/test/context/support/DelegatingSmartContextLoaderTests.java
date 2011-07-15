/*
 * Copyright 2002-2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.test.context.support;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.springframework.test.context.MergedContextConfiguration;

/**
 * Unit tests for {@link DelegatingSmartContextLoader}.
 * 
 * @author Sam Brannen
 * @since 3.1
 */
public class DelegatingSmartContextLoaderTests {

	private static final String[] EMPTY_STRING_ARRAY = new String[0];
	private static final Class<?>[] EMPTY_CLASS_ARRAY = new Class<?>[0];

	private final DelegatingSmartContextLoader loader = new DelegatingSmartContextLoader();


	// --- SmartContextLoader --------------------------------------------------

	@Test
	public void generatesDefaults() {
		assertTrue(loader.generatesDefaults());
	}

	@Test
	public void processContextConfiguration() {
		// TODO test processContextConfiguration().
	}

	@Test(expected = IllegalArgumentException.class)
	public void doesNotSupportNullConfig() {
		MergedContextConfiguration mergedConfig = null;
		loader.supports(mergedConfig);
	}

	@Test
	public void doesNotSupportEmptyConfig() {
		MergedContextConfiguration mergedConfig = new MergedContextConfiguration(getClass(), EMPTY_STRING_ARRAY,
			EMPTY_CLASS_ARRAY, EMPTY_STRING_ARRAY, loader);
		assertFalse(loader.supports(mergedConfig));
	}

	@Test
	public void doesNotSupportLocationsAndConfigurationClasses() {
		MergedContextConfiguration mergedConfig = new MergedContextConfiguration(getClass(),
			new String[] { "foo.xml" }, new Class<?>[] { getClass() }, EMPTY_STRING_ARRAY, loader);
		assertFalse(loader.supports(mergedConfig));
	}

	@Test
	public void supportsLocations() {
		MergedContextConfiguration mergedConfig = new MergedContextConfiguration(getClass(),
			new String[] { "foo.xml" }, EMPTY_CLASS_ARRAY, EMPTY_STRING_ARRAY, loader);
		assertTrue(loader.supports(mergedConfig));
	}

	@Test
	public void supportsConfigurationClasses() {
		MergedContextConfiguration mergedConfig = new MergedContextConfiguration(getClass(), EMPTY_STRING_ARRAY,
			new Class<?>[] { getClass() }, EMPTY_STRING_ARRAY, loader);
		assertTrue(loader.supports(mergedConfig));
	}

	@Test(expected = IllegalArgumentException.class)
	public void loadContextWithNullConfig() throws Exception {
		MergedContextConfiguration mergedConfig = null;
		loader.loadContext(mergedConfig);
	}

	@Test(expected = IllegalStateException.class)
	public void loadContextWithoutLocationsAndConfigurationClasses() throws Exception {
		MergedContextConfiguration mergedConfig = new MergedContextConfiguration(getClass(), EMPTY_STRING_ARRAY,
			EMPTY_CLASS_ARRAY, EMPTY_STRING_ARRAY, loader);
		loader.loadContext(mergedConfig);
	}

	@Test
	public void loadContext() {
		// TODO test loadContext().
	}

	// --- ContextLoader -------------------------------------------------------

	@Test(expected = UnsupportedOperationException.class)
	public void processLocations() {
		loader.processLocations(getClass(), EMPTY_STRING_ARRAY);
	}

	@Test(expected = UnsupportedOperationException.class)
	public void loadContextFromLocations() throws Exception {
		loader.loadContext(EMPTY_STRING_ARRAY);
	}

}
