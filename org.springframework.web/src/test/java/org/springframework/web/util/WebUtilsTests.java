/*
 * Copyright 2002-2008 the original author or authors.
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

package org.springframework.web.util;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;

/**
 * @author Juergen Hoeller
 * @author Arjen Poutsma
 */
public class WebUtilsTests {

	@Test
	public void findParameterValue() {
		Map<String, Object> params = new HashMap<String, Object>();
		params.put("myKey1", "myValue1");
		params.put("myKey2_myValue2", "xxx");
		params.put("myKey3_myValue3.x", "xxx");
		params.put("myKey4_myValue4.y", new String[] {"yyy"});

		assertNull(WebUtils.findParameterValue(params, "myKey0"));
		assertEquals("myValue1", WebUtils.findParameterValue(params, "myKey1"));
		assertEquals("myValue2", WebUtils.findParameterValue(params, "myKey2"));
		assertEquals("myValue3", WebUtils.findParameterValue(params, "myKey3"));
		assertEquals("myValue4", WebUtils.findParameterValue(params, "myKey4"));
	}

	@Test
	public void extractFilenameFromUrlPath() {
		assertEquals("index", WebUtils.extractFilenameFromUrlPath("index.html"));
		assertEquals("index", WebUtils.extractFilenameFromUrlPath("/index.html"));
		assertEquals("view", WebUtils.extractFilenameFromUrlPath("/products/view.html"));
		assertEquals("view", WebUtils.extractFilenameFromUrlPath("/products/view.html?param=a"));
		assertEquals("view", WebUtils.extractFilenameFromUrlPath("/products/view.html?param=/path/a"));
		assertEquals("view", WebUtils.extractFilenameFromUrlPath("/products/view.html?param=/path/a.do"));
	}

	@Test
	public void extractFullFilenameFromUrlPath() {
		assertEquals("index.html", WebUtils.extractFullFilenameFromUrlPath("index.html"));
		assertEquals("index.html", WebUtils.extractFullFilenameFromUrlPath("/index.html"));
		assertEquals("view.html", WebUtils.extractFullFilenameFromUrlPath("/products/view.html"));
		assertEquals("view.html", WebUtils.extractFullFilenameFromUrlPath("/products/view.html?param=a"));
		assertEquals("view.html", WebUtils.extractFullFilenameFromUrlPath("/products/view.html?param=/path/a"));
		assertEquals("view.html", WebUtils.extractFullFilenameFromUrlPath("/products/view.html?param=/path/a.do"));
	}

	@Test
	public void extractUriPath() {
		assertEquals("", WebUtils.extractUrlPath("http://example.com"));
		assertEquals("/", WebUtils.extractUrlPath("http://example.com/"));
		assertEquals("/rfc/rfc3986.txt", WebUtils.extractUrlPath("http://www.ietf.org/rfc/rfc3986.txt"));
		assertEquals("/over/there", WebUtils.extractUrlPath("http://example.com/over/there?name=ferret#nose"));
		assertEquals("/over/there", WebUtils.extractUrlPath("http://example.com/over/there#nose"));
		assertEquals("/over/there", WebUtils.extractUrlPath("/over/there?name=ferret#nose"));
		assertEquals("/over/there", WebUtils.extractUrlPath("/over/there?url=http://example.com"));
	}
	
}
