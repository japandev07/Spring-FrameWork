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

package org.springframework.test.context.configuration.interfaces;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.test.fixtures.beans.Employee;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Sam Brannen
 * @since 4.3
 */
@ExtendWith(SpringExtension.class)
class ActiveProfilesInterfaceTests implements ActiveProfilesTestInterface {

	@Autowired
	Employee employee;


	@Test
	void profileFromTestInterface() {
		assertThat(employee).isNotNull();
		assertThat(employee.getName()).isEqualTo("dev");
	}


	@Configuration
	static class Config {

		@Bean
		@Profile("dev")
		Employee employee1() {
			return new Employee("dev");
		}

		@Bean
		@Profile("prod")
		Employee employee2() {
			return new Employee("prod");
		}
	}

}
