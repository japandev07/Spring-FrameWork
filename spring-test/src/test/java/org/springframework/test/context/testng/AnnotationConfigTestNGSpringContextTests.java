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

package org.springframework.test.context.testng;

import org.testng.annotations.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.tests.sample.beans.Employee;
import org.springframework.tests.sample.beans.Pet;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

/**
 * Integration tests that verify support for
 * {@link org.springframework.context.annotation.Configuration @Configuration} classes
 * with TestNG-based tests.
 *
 * <p>Configuration will be loaded from
 * {@link AnnotationConfigTestNGSpringContextTests.Config}.
 *
 * @author Sam Brannen
 * @since 5.1
 */
@ContextConfiguration
public class AnnotationConfigTestNGSpringContextTests extends AbstractTestNGSpringContextTests {

	@Autowired
	Employee employee;

	@Autowired
	Pet pet;

	@Test
	void autowiringFromConfigClass() {
		assertNotNull(employee, "The employee should have been autowired.");
		assertEquals(employee.getName(), "John Smith");

		assertNotNull(pet, "The pet should have been autowired.");
		assertEquals(pet.getName(), "Fido");
	}


	@Configuration
	static class Config {

		@Bean
		Employee employee() {
			return new Employee("John Smith");
		}

		@Bean
		Pet pet() {
			return new Pet("Fido");
		}

	}

}
