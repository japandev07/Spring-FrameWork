/*
 * Copyright 2004-2015 the original author or authors.
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

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.hamcrest.CoreMatchers.*;

/**
 * Unit tests for {@link JsonPathExpectationsHelper}.
 *
 * @author Rossen Stoyanchev
 * @author Sam Brannen
 * @since 3.2
 */
public class JsonPathExpectationsHelperTests {

	@Rule
	public final ExpectedException exception = ExpectedException.none();


	@Test
	public void assertValueWithDifferentExpectedType() throws Exception {
		exception.expect(AssertionError.class);
		exception.expectMessage(equalTo("For JSON path \"$.nr\", type of value expected:<java.lang.String> but was:<java.lang.Integer>"));
		new JsonPathExpectationsHelper("$.nr").assertValue("{ \"nr\" : 5 }", "5");
	}

}
