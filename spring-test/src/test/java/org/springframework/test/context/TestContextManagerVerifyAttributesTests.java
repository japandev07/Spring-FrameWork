/*
 * Copyright 2002-2016 the original author or authors.
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

package org.springframework.test.context;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

import static org.hamcrest.CoreMatchers.*;

/**
 * JUnit 4 based unit test for {@link TestContextManager}, which verifies
 * ContextConfiguration attributes are defined.
 *
 * @author Phillip Webb
 * @since 4.3
 */
public class TestContextManagerVerifyAttributesTests {

	@Rule
	public ExpectedException expectedException = ExpectedException.none();

	@Test
	public void processContextConfigurationWithMissingContextConfigAttributes() {
		expectedException.expect(IllegalStateException.class);
		expectedException.expectMessage(containsString("was unable to detect defaults, "
				+ "and no ApplicationContextInitializers or ContextCustomizers were "
				+ "declared for context configuration"));
		new TestContextManager(MissingContextAttributes.class);
	}

	@Test
	public void processContextConfigurationWitListener() {
		new TestContextManager(WithInitializer.class);
	}


	@ContextConfiguration
	private static class MissingContextAttributes {

	}

	@ContextConfiguration(initializers=ExampleInitializer.class)
	private static class WithInitializer {

	}

	static class ExampleInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

		@Override
		public void initialize(ConfigurableApplicationContext applicationContext) {
		}

	}
}
