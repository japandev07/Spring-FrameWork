/*
 * Copyright 2002-2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.test.context;

/**
 * Collection of test-related utility methods for working with {@link TestContext TestContexts}.
 *
 * @author Sam Brannen
 * @since 4.1
 */
public abstract class TestContextTestUtils {

	private TestContextTestUtils() {
		/* no-op */
	}

	public static TestContext buildTestContext(Class<?> testClass, ContextCache contextCache) {
		CacheAwareContextLoaderDelegate cacheAwareContextLoaderDelegate = new DefaultCacheAwareContextLoaderDelegate(
			contextCache);
		return buildTestContext(testClass, null, cacheAwareContextLoaderDelegate);
	}

	public static TestContext buildTestContext(Class<?> testClass, String customDefaultContextLoaderClassName,
			CacheAwareContextLoaderDelegate cacheAwareContextLoaderDelegate) {
		BootstrapContext bootstrapContext = new DefaultBootstrapContext(testClass, cacheAwareContextLoaderDelegate);
		TestContextBootstrapper testContextBootstrapper = BootstrapUtils.resolveTestContextBootstrapper(bootstrapContext);

		return new DefaultTestContext(testContextBootstrapper);
	}

}
