/*
 * Copyright 2002-2017 the original author or authors.
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
package org.springframework.http.server.reactive;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Test;

import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

/**
 * Unit tests for {@link DefaultPathContainer}.
 * @author Rossen Stoyanchev
 */
public class DefaultPathContainerTests {

	@Test
	public void pathSegment() throws Exception {
		// basic
		testPathSegment("cars", "", "cars", "cars", new LinkedMultiValueMap<>());

		// empty
		testPathSegment("", "", "", "", new LinkedMultiValueMap<>());

		// spaces
		testPathSegment("%20%20", "", "%20%20", "  ", new LinkedMultiValueMap<>());
		testPathSegment("%20a%20", "", "%20a%20", " a ", new LinkedMultiValueMap<>());
	}

	@Test
	public void pathSegmentParams() throws Exception {
		// basic
		LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
		params.add("colors", "red");
		params.add("colors", "blue");
		params.add("colors", "green");
		params.add("year", "2012");
		testPathSegment("cars", ";colors=red,blue,green;year=2012", "cars", "cars", params);

		// trailing semicolon
		params = new LinkedMultiValueMap<>();
		params.add("p", "1");
		testPathSegment("path", ";p=1;", "path", "path", params);

		// params with spaces
		params = new LinkedMultiValueMap<>();
		params.add("param name", "param value");
		testPathSegment("path", ";param%20name=param%20value;%20", "path", "path", params);

		// empty params
		params = new LinkedMultiValueMap<>();
		params.add("p", "1");
		testPathSegment("path", ";;;%20;%20;p=1;%20", "path", "path", params);
	}

	private void testPathSegment(String rawValue, String semicolonContent,
			String value, String valueDecoded, MultiValueMap<String, String> params) {

		PathContainer container = DefaultPathContainer.parsePath(rawValue + semicolonContent, UTF_8);

		if ("".equals(value)) {
			assertEquals(0, container.elements().size());
			return;
		}

		assertEquals(1, container.elements().size());
		PathContainer.Segment segment = (PathContainer.Segment) container.elements().get(0);

		assertEquals("value: '" + rawValue + "'", value, segment.value());
		assertEquals("valueDecoded: '" + rawValue + "'", valueDecoded, segment.valueDecoded());
		assertEquals("semicolonContent: '" + rawValue + "'", semicolonContent, segment.semicolonContent());
		assertEquals("params: '" + rawValue + "'", params, segment.parameters());
	}

	@Test
	public void path() throws Exception {
		// basic
		testPath("/a/b/c", "/a/b/c", Arrays.asList("/", "a", "/", "b", "/", "c"));

		// root path
		testPath("/", "/", Collections.singletonList("/"));

		// empty path
		testPath("", "", Collections.emptyList());
		testPath("%20%20", "%20%20", Collections.singletonList("%20%20"));

		// trailing slash
		testPath("/a/b/", "/a/b/", Arrays.asList("/", "a", "/", "b", "/"));
		testPath("/a/b//", "/a/b//", Arrays.asList("/", "a", "/", "b", "/", "/"));

		// extra slashes and spaces
		testPath("/%20", "/%20", Arrays.asList("/", "%20"));
		testPath("//%20/%20", "//%20/%20", Arrays.asList("/", "/", "%20", "/", "%20"));
	}

	private void testPath(String input, String value, List<String> expectedElements) {

		PathContainer path = PathContainer.parse(input, UTF_8);

		assertEquals("value: '" + input + "'", value, path.value());
		assertEquals("elements: " + input, expectedElements, path.elements().stream()
				.map(PathContainer.Element::value).collect(Collectors.toList()));
	}

	@Test
	public void subPath() throws Exception {
		// basic
		PathContainer path = PathContainer.parse("/a/b/c", UTF_8);
		assertSame(path, path.subPath(0));
		assertEquals("/b/c", path.subPath(2).value());
		assertEquals("/c", path.subPath(4).value());

		// root path
		path = PathContainer.parse("/", UTF_8);
		assertEquals("/", path.subPath(0).value());

		// trailing slash
		path = PathContainer.parse("/a/b/", UTF_8);
		assertEquals("/b/", path.subPath(2).value());
	}

}
