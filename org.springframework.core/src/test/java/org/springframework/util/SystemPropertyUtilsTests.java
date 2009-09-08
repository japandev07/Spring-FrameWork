/*
 * Copyright 2002-2009 the original author or authors.
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

package org.springframework.util;

import java.util.Map;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

/** @author Rob Harrop */
public class SystemPropertyUtilsTests {

	@Test
	public void testReplaceFromSystemProperty() {
		System.setProperty("test.prop", "bar");
		String resolved = SystemPropertyUtils.resolvePlaceholders("${test.prop}");
		assertEquals("bar", resolved);
	}

	@Test
	public void testRecursiveFromSystemProperty() {
		System.setProperty("test.prop", "foo=${bar}");
		System.setProperty("bar", "baz");
		String resolved = SystemPropertyUtils.resolvePlaceholders("${test.prop}");
		assertEquals("foo=baz", resolved);
	}

	@Test
	public void testReplaceFromEnv() {
		Map<String,String> env = System.getenv();
		if(env.containsKey("PATH")) {
			String text = "${PATH}";
			assertEquals(env.get("PATH"), SystemPropertyUtils.resolvePlaceholders(text));
		}
	}
}
