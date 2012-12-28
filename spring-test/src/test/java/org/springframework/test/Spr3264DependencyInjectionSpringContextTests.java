/*
 * Copyright 2002-2012 the original author or authors.
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

package org.springframework.test;

/**
 * JUnit 3.8 based unit test which verifies new functionality requested in <a
 * href="http://opensource.atlassian.com/projects/spring/browse/SPR-3264"
 * target="_blank">SPR-3264</a>.
 *
 * @author Sam Brannen
 * @since 2.5
 * @see Spr3264SingleSpringContextTests
 */
@SuppressWarnings("deprecation")
public class Spr3264DependencyInjectionSpringContextTests extends AbstractDependencyInjectionSpringContextTests {

	public Spr3264DependencyInjectionSpringContextTests() {
		super();
	}

	public Spr3264DependencyInjectionSpringContextTests(String name) {
		super(name);
	}

	/**
	 * <p>
	 * Test which addresses the following issue raised in SPR-3264:
	 * </p>
	 * <p>
	 * AbstractDependencyInjectionSpringContextTests will try to apply
	 * auto-injection; this can be disabled but it has to be done manually
	 * inside the onSetUp...
	 * </p>
	 */
	public void testInjectDependenciesThrowsIllegalStateException() {

		// Re-assert issues covered by Spr3264SingleSpringContextTests as a
		// safety net.
		assertNull("The ApplicationContext should NOT be automatically created if no 'locations' are defined.",
			this.applicationContext);
		assertEquals("Verifying the ApplicationContext load count.", 0, super.getLoadCount());

		// Assert changes to AbstractDependencyInjectionSpringContextTests:
		new AssertThrows(IllegalStateException.class) {

			@Override
			public void test() throws Exception {
				Spr3264DependencyInjectionSpringContextTests.super.injectDependencies();
			}
		}.runTest();
	}

}
