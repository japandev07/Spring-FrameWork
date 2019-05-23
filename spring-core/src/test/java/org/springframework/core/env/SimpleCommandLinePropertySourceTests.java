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

package org.springframework.core.env;

import java.util.List;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;


/**
 * Unit tests for {@link SimpleCommandLinePropertySource}.
 *
 * @author Chris Beams
 * @since 3.1
 */
public class SimpleCommandLinePropertySourceTests {

	@Test
	public void withDefaultName() {
		PropertySource<?> ps = new SimpleCommandLinePropertySource();
		assertThat(ps.getName())
				.isEqualTo(CommandLinePropertySource.COMMAND_LINE_PROPERTY_SOURCE_NAME);
	}

	@Test
	public void withCustomName() {
		PropertySource<?> ps = new SimpleCommandLinePropertySource("ps1", new String[0]);
		assertThat(ps.getName()).isEqualTo("ps1");
	}

	@Test
	public void withNoArgs() {
		PropertySource<?> ps = new SimpleCommandLinePropertySource();
		assertThat(ps.containsProperty("foo")).isFalse();
		assertThat(ps.getProperty("foo")).isNull();
	}

	@Test
	public void withOptionArgsOnly() {
		CommandLinePropertySource<?> ps =
			new SimpleCommandLinePropertySource("--o1=v1", "--o2");
		assertThat(ps.containsProperty("o1")).isTrue();
		assertThat(ps.containsProperty("o2")).isTrue();
		assertThat(ps.containsProperty("o3")).isFalse();
		assertThat(ps.getProperty("o1")).isEqualTo("v1");
		assertThat(ps.getProperty("o2")).isEqualTo("");
		assertThat(ps.getProperty("o3")).isNull();
	}

	@Test
	public void withDefaultNonOptionArgsNameAndNoNonOptionArgsPresent() {
		EnumerablePropertySource<?> ps = new SimpleCommandLinePropertySource("--o1=v1", "--o2");

		assertThat(ps.containsProperty("nonOptionArgs")).isFalse();
		assertThat(ps.containsProperty("o1")).isTrue();
		assertThat(ps.containsProperty("o2")).isTrue();

		assertThat(ps.containsProperty("nonOptionArgs")).isFalse();
		assertThat(ps.getProperty("nonOptionArgs")).isNull();
		assertThat(ps.getPropertyNames().length).isEqualTo(2);
	}

	@Test
	public void withDefaultNonOptionArgsNameAndNonOptionArgsPresent() {
		CommandLinePropertySource<?> ps =
			new SimpleCommandLinePropertySource("--o1=v1", "noa1", "--o2", "noa2");

		assertThat(ps.containsProperty("nonOptionArgs")).isTrue();
		assertThat(ps.containsProperty("o1")).isTrue();
		assertThat(ps.containsProperty("o2")).isTrue();

		String nonOptionArgs = ps.getProperty("nonOptionArgs");
		assertThat(nonOptionArgs).isEqualTo("noa1,noa2");
	}

	@Test
	public void withCustomNonOptionArgsNameAndNoNonOptionArgsPresent() {
		CommandLinePropertySource<?> ps =
			new SimpleCommandLinePropertySource("--o1=v1", "noa1", "--o2", "noa2");
		ps.setNonOptionArgsPropertyName("NOA");

		assertThat(ps.containsProperty("nonOptionArgs")).isFalse();
		assertThat(ps.containsProperty("NOA")).isTrue();
		assertThat(ps.containsProperty("o1")).isTrue();
		assertThat(ps.containsProperty("o2")).isTrue();
		String nonOptionArgs = ps.getProperty("NOA");
		assertThat(nonOptionArgs).isEqualTo("noa1,noa2");
	}

	@Test
	public void covertNonOptionArgsToStringArrayAndList() {
		CommandLinePropertySource<?> ps =
			new SimpleCommandLinePropertySource("--o1=v1", "noa1", "--o2", "noa2");
		StandardEnvironment env = new StandardEnvironment();
		env.getPropertySources().addFirst(ps);

		String nonOptionArgs = env.getProperty("nonOptionArgs");
		assertThat(nonOptionArgs).isEqualTo("noa1,noa2");

		String[] nonOptionArgsArray = env.getProperty("nonOptionArgs", String[].class);
		assertThat(nonOptionArgsArray[0]).isEqualTo("noa1");
		assertThat(nonOptionArgsArray[1]).isEqualTo("noa2");

		@SuppressWarnings("unchecked")
		List<String> nonOptionArgsList = env.getProperty("nonOptionArgs", List.class);
		assertThat(nonOptionArgsList.get(0)).isEqualTo("noa1");
		assertThat(nonOptionArgsList.get(1)).isEqualTo("noa2");
	}

}
