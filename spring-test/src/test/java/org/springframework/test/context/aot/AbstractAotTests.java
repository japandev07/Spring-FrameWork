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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.stream.Stream;

/**
 * @author Sam Brannen
 * @since 6.0
 */
abstract class AbstractAotTests {

	static final String[] expectedSourceFilesForBasicSpringTests = {
			// Global
			"org/springframework/test/context/aot/AotTestMappings__Generated.java",
			// BasicSpringJupiterSharedConfigTests
			"org/springframework/context/event/DefaultEventListenerFactory__TestContext001_BeanDefinitions.java",
			"org/springframework/context/event/EventListenerMethodProcessor__TestContext001_BeanDefinitions.java",
			"org/springframework/test/context/aot/samples/basic/BasicSpringJupiterSharedConfigTests__TestContext001_ApplicationContextInitializer.java",
			"org/springframework/test/context/aot/samples/basic/BasicSpringJupiterSharedConfigTests__TestContext001_BeanFactoryRegistrations.java",
			"org/springframework/test/context/aot/samples/basic/BasicTestConfiguration__TestContext001_BeanDefinitions.java",
			// BasicSpringJupiterTests -- not generated b/c already generated for BasicSpringJupiterSharedConfigTests.
			// "org/springframework/context/event/DefaultEventListenerFactory__TestContext00?_BeanDefinitions.java",
			// "org/springframework/context/event/EventListenerMethodProcessor__TestContext00?_BeanDefinitions.java",
			// "org/springframework/test/context/aot/samples/basic/BasicSpringJupiterTests__TestContext00?_ApplicationContextInitializer.java",
			// "org/springframework/test/context/aot/samples/basic/BasicSpringJupiterTests__TestContext00?_BeanFactoryRegistrations.java",
			// "org/springframework/test/context/aot/samples/basic/BasicTestConfiguration__TestContext00?_BeanDefinitions.java",
			// BasicSpringJupiterTests.NestedTests
			"org/springframework/context/event/DefaultEventListenerFactory__TestContext002_BeanDefinitions.java",
			"org/springframework/context/event/EventListenerMethodProcessor__TestContext002_BeanDefinitions.java",
			"org/springframework/test/context/aot/samples/basic/BasicSpringJupiterTests_NestedTests__TestContext002_ApplicationContextInitializer.java",
			"org/springframework/test/context/aot/samples/basic/BasicSpringJupiterTests_NestedTests__TestContext002_BeanFactoryRegistrations.java",
			"org/springframework/test/context/aot/samples/basic/BasicTestConfiguration__TestContext002_BeanDefinitions.java",
			// BasicSpringTestNGTests
			"org/springframework/context/event/DefaultEventListenerFactory__TestContext003_BeanDefinitions.java",
			"org/springframework/context/event/EventListenerMethodProcessor__TestContext003_BeanDefinitions.java",
			"org/springframework/test/context/aot/samples/basic/BasicSpringTestNGTests__TestContext003_ApplicationContextInitializer.java",
			"org/springframework/test/context/aot/samples/basic/BasicSpringTestNGTests__TestContext003_BeanFactoryRegistrations.java",
			"org/springframework/test/context/aot/samples/basic/BasicTestConfiguration__TestContext003_BeanDefinitions.java",
			// BasicSpringVintageTests
			"org/springframework/context/event/DefaultEventListenerFactory__TestContext004_BeanDefinitions.java",
			"org/springframework/context/event/EventListenerMethodProcessor__TestContext004_BeanDefinitions.java",
			"org/springframework/test/context/aot/samples/basic/BasicSpringVintageTests__TestContext004_ApplicationContextInitializer.java",
			"org/springframework/test/context/aot/samples/basic/BasicSpringVintageTests__TestContext004_BeanFactoryRegistrations.java",
			"org/springframework/test/context/aot/samples/basic/BasicTestConfiguration__TestContext004_BeanDefinitions.java"
		};

	Stream<Class<?>> scan() {
		return new TestClassScanner(classpathRoots()).scan();
	}

	Stream<Class<?>> scan(String... packageNames) {
		return new TestClassScanner(classpathRoots()).scan(packageNames);
	}

	Set<Path> classpathRoots() {
		try {
			return Set.of(classpathRoot());
		}
		catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}

	Path classpathRoot() {
		try {
			return Paths.get(getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
		}
		catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}

}
