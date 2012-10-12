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

package org.springframework.test.web.mock.servlet.samples.standalone.resultmatchers;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.isIn;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.mock.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.mock.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.mock.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.mock.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.mock.servlet.setup.MockMvcBuilders.standaloneSetup;

import java.util.Arrays;

import org.junit.Before;
import org.junit.Test;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.mock.Person;
import org.springframework.test.web.mock.servlet.MockMvc;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Examples of defining expectations on JSON response content with
 * <a href="http://goessner.net/articles/JsonPath/">JSONPath</a> expressions.
 *
 * @author Rossen Stoyanchev
 *
 * @see ContentAssertionTests
 */
public class JsonPathAssertionTests {

	private MockMvc mockMvc;

	@Before
	public void setup() {
		this.mockMvc = standaloneSetup(new MusicController())
				.defaultRequest(get("/").accept(MediaType.APPLICATION_JSON))
				.alwaysExpect(status().isOk())
				.alwaysExpect(content().contentType("application/json;charset=UTF-8"))
				.build();
	}

	@Test
	public void testExists() throws Exception {

		String composerByName = "$.composers[?(@.name == '%s')]";
		String performerByName = "$.performers[?(@.name == '%s')]";

		this.mockMvc.perform(get("/music/people"))
			.andExpect(jsonPath(composerByName, "Johann Sebastian Bach").exists())
			.andExpect(jsonPath(composerByName, "Johannes Brahms").exists())
			.andExpect(jsonPath(composerByName, "Edvard Grieg").exists())
			.andExpect(jsonPath(composerByName, "Robert Schumann").exists())
			.andExpect(jsonPath(performerByName, "Vladimir Ashkenazy").exists())
			.andExpect(jsonPath(performerByName, "Yehudi Menuhin").exists())
			.andExpect(jsonPath("$.composers[0]").exists())
			.andExpect(jsonPath("$.composers[1]").exists())
			.andExpect(jsonPath("$.composers[2]").exists())
			.andExpect(jsonPath("$.composers[3]").exists());

	}

	@Test
	public void testDoesNotExist() throws Exception {
		this.mockMvc.perform(get("/music/people"))
			.andExpect(jsonPath("$.composers[?(@.name == 'Edvard Grieeeeeeg')]").doesNotExist())
			.andExpect(jsonPath("$.composers[?(@.name == 'Robert Schuuuuuuman')]").doesNotExist())
			.andExpect(jsonPath("$.composers[-1]").doesNotExist())
			.andExpect(jsonPath("$.composers[4]").doesNotExist());
	}

	@Test
	public void testEqualTo() throws Exception {
		this.mockMvc.perform(get("/music/people"))
			.andExpect(jsonPath("$.composers[0].name").value("Johann Sebastian Bach"))
			.andExpect(jsonPath("$.performers[1].name").value("Yehudi Menuhin"));

		// Hamcrest matchers...
		this.mockMvc.perform(get("/music/people"))
			.andExpect(jsonPath("$.composers[0].name").value(equalTo("Johann Sebastian Bach")))
			.andExpect(jsonPath("$.performers[1].name").value(equalTo("Yehudi Menuhin")));
	}

	@Test
	public void testHamcrestMatcher() throws Exception {
		this.mockMvc.perform(get("/music/people"))
			.andExpect(jsonPath("$.composers[0].name", startsWith("Johann")))
			.andExpect(jsonPath("$.performers[0].name", endsWith("Ashkenazy")))
			.andExpect(jsonPath("$.performers[1].name", containsString("di Me")))
			.andExpect(jsonPath("$.composers[1].name", isIn(Arrays.asList("Johann Sebastian Bach", "Johannes Brahms"))));
	}

	@Test
	public void testHamcrestMatcherWithParameterizedJsonPath() throws Exception {

		String composerName = "$.composers[%s].name";
		String performerName = "$.performers[%s].name";

		this.mockMvc.perform(get("/music/people"))
			.andExpect(jsonPath(composerName, 0).value(startsWith("Johann")))
			.andExpect(jsonPath(performerName, 0).value(endsWith("Ashkenazy")))
			.andExpect(jsonPath(performerName, 1).value(containsString("di Me")))
			.andExpect(jsonPath(composerName, 1).value(isIn(Arrays.asList("Johann Sebastian Bach", "Johannes Brahms"))));
	}


	@Controller
	private class MusicController {

		@RequestMapping(value="/music/people")
		public @ResponseBody MultiValueMap<String, Person> get() {
			MultiValueMap<String, Person> map = new LinkedMultiValueMap<String, Person>();

			map.add("composers", new Person("Johann Sebastian Bach"));
			map.add("composers", new Person("Johannes Brahms"));
			map.add("composers", new Person("Edvard Grieg"));
			map.add("composers", new Person("Robert Schumann"));

			map.add("performers", new Person("Vladimir Ashkenazy"));
			map.add("performers", new Person("Yehudi Menuhin"));

			return map;
		}
	}
}
