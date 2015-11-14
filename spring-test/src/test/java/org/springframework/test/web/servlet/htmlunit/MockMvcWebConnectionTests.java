/*
 * Copyright 2002-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.springframework.test.web.servlet.htmlunit;

import java.io.IOException;

import org.junit.Before;
import org.junit.Test;

import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.WebClient;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

/**
 * Integration tests for {@link MockMvcWebConnection}.
 *
 * @author Rob Winch
 * @since 4.2
 */
public class MockMvcWebConnectionTests {

	private final WebClient webClient = new WebClient();

	private MockMvc mockMvc;


	@Before
	public void setup() {
		this.mockMvc = MockMvcBuilders.standaloneSetup(new HelloController(), new ForwardController()).build();
	}

	@Test
	public void contextPathNull() throws IOException {
		this.webClient.setWebConnection(new MockMvcWebConnection(this.mockMvc, null));

		Page page = this.webClient.getPage("http://localhost/context/a");

		assertThat(page.getWebResponse().getStatusCode(), equalTo(200));
	}

	@Test
	public void contextPathExplicit() throws IOException {
		this.webClient.setWebConnection(new MockMvcWebConnection(this.mockMvc, "/context"));

		Page page = this.webClient.getPage("http://localhost/context/a");

		assertThat(page.getWebResponse().getStatusCode(), equalTo(200));
	}

	@Test
	public void contextPathEmpty() throws IOException {
		this.webClient.setWebConnection(new MockMvcWebConnection(this.mockMvc, ""));

		Page page = this.webClient.getPage("http://localhost/context/a");

		assertThat(page.getWebResponse().getStatusCode(), equalTo(200));
	}

	@Test
	public void forward() throws IOException {
		this.webClient.setWebConnection(new MockMvcWebConnection(this.mockMvc, ""));

		Page page = this.webClient.getPage("http://localhost/forward");

		assertThat(page.getWebResponse().getContentAsString(), equalTo("hello"));
	}

	@Test(expected = IllegalArgumentException.class)
	@SuppressWarnings("resource")
	public void contextPathDoesNotStartWithSlash() throws IOException {
		new MockMvcWebConnection(this.mockMvc, "context");
	}

	@Test(expected = IllegalArgumentException.class)
	@SuppressWarnings("resource")
	public void contextPathEndsWithSlash() throws IOException {
		new MockMvcWebConnection(this.mockMvc, "/context/");
	}

}
