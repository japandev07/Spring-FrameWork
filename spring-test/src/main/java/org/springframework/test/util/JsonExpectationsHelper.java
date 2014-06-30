/*
 * Copyright 2002-2014 the original author or authors.
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

package org.springframework.test.util;

import org.skyscreamer.jsonassert.JSONAssert;

/**
 * A helper class for assertions on JSON content.
 *
 * <p>Use of this class requires the <a
 * href="http://jsonassert.skyscreamer.org/">JSONassert<a/> library.
 *
 * @author Sebastien Deleuze
 * @since 4.1
 */
public class JsonExpectationsHelper {

	/**
	 * Parse the expected and actual strings as JSON and assert the two
	 * are "similar" - i.e. they contain the same attribute-value pairs
	 * regardless of order and formatting.
	 *
	 * @param expected the expected JSON content
	 * @param actual the actual JSON content
	 * @since 4.1
	 */
	public void assertJsonEqual(String expected, String actual) throws Exception {
		JSONAssert.assertEquals(expected, actual, false);
	}

	/**
	 * Parse the expected and actual strings as JSON and assert the two
	 * are "not similar" - i.e. they contain different attribute-value pairs
	 * regardless of order and formatting.
	 *
	 * @param expected the expected JSON content
	 * @param actual the actual JSON content
	 * @since 4.1
	 */
	public void assertJsonNotEqual(String expected, String actual) throws Exception {
		JSONAssert.assertNotEquals(expected, actual, false);
	}
}
