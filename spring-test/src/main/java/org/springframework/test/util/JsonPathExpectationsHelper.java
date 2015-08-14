/*
 * Copyright 2002-2015 the original author or authors.
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

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.text.ParseException;
import java.util.List;
import java.util.Map;

import org.hamcrest.Matcher;

import org.springframework.util.Assert;
import org.springframework.util.ReflectionUtils;

import com.jayway.jsonpath.InvalidPathException;
import com.jayway.jsonpath.JsonPath;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.springframework.test.util.AssertionErrors.assertEquals;
import static org.springframework.test.util.AssertionErrors.assertTrue;
import static org.springframework.test.util.AssertionErrors.fail;

/**
 * A helper class for applying assertions via JSON path expressions.
 *
 * <p>Based on the <a href="https://github.com/jayway/JsonPath">JsonPath</a>
 * project: requiring version 0.9+, with 1.1+ strongly recommended.
 *
 * @author Rossen Stoyanchev
 * @author Juergen Hoeller
 * @author Craig Andrews
 * @author Sam Brannen
 * @since 3.2
 */
public class JsonPathExpectationsHelper {

	private static Method compileMethod;

	private static Object emptyFilters;

	static {
		// Reflective bridging between JsonPath 0.9.x and 1.x
		for (Method candidate : JsonPath.class.getMethods()) {
			if (candidate.getName().equals("compile")) {
				Class<?>[] paramTypes = candidate.getParameterTypes();
				if (paramTypes.length == 2 && String.class == paramTypes[0] && paramTypes[1].isArray()) {
					compileMethod = candidate;
					emptyFilters = Array.newInstance(paramTypes[1].getComponentType(), 0);
					break;
				}
			}
		}
		Assert.state(compileMethod != null, "Unexpected JsonPath API - no compile(String, ...) method found");
	}


	private final String expression;

	private final JsonPath jsonPath;


	/**
	 * Construct a new {@code JsonPathExpectationsHelper}.
	 * @param expression the {@link JsonPath} expression; never {@code null} or empty
	 * @param args arguments to parameterize the {@code JsonPath} expression, with
	 * formatting specifiers defined in {@link String#format(String, Object...)}
	 */
	public JsonPathExpectationsHelper(String expression, Object... args) {
		Assert.hasText(expression, "expression must not be null or empty");
		this.expression = String.format(expression, args);
		this.jsonPath = (JsonPath) ReflectionUtils.invokeMethod(
				compileMethod, null, this.expression, emptyFilters);
	}


	/**
	 * Evaluate the JSON path expression against the supplied {@code content}
	 * and assert the resulting value with the given {@code Matcher}.
	 * @param content the JSON content
	 * @param matcher the matcher with which to assert the result
	 */
	@SuppressWarnings("unchecked")
	public <T> void assertValue(String content, Matcher<T> matcher) throws ParseException {
		T value = (T) evaluateJsonPath(content);
		assertThat("JSON path \"" + this.expression + "\"", value, matcher);
	}

	/**
	 * Evaluate the JSON path expression against the supplied {@code content}
	 * and assert that the result is equal to the expected value.
	 * @param content the JSON content
	 * @param expectedValue the expected value
	 */
	public void assertValue(String content, Object expectedValue) throws ParseException {
		Object actualValue = evaluateJsonPath(content);
		if ((actualValue instanceof List) && !(expectedValue instanceof List)) {
			@SuppressWarnings("rawtypes")
			List actualValueList = (List) actualValue;
			if (actualValueList.isEmpty()) {
				fail("No matching value for JSON path \"" + this.expression + "\"");
			}
			if (actualValueList.size() != 1) {
				fail("Got a list of values " + actualValue + " instead of the expected single value " + expectedValue);
			}
			actualValue = actualValueList.get(0);
		}
		else if (actualValue != null && expectedValue != null) {
			assertEquals("For JSON path \"" + this.expression + "\", type of value",
					expectedValue.getClass().getName(), actualValue.getClass().getName());
		}
		assertEquals("JSON path \"" + this.expression + "\"", expectedValue, actualValue);
	}

	/**
	 * Evaluate the JSON path expression against the supplied {@code content}
	 * and assert that the resulting value is a {@link String}.
	 * @param content the JSON content
	 * @since 4.2.1
	 */
	public void assertValueIsString(String content) throws ParseException {
		Object value = assertExistsAndReturn(content);
		String reason = "Expected string at JSON path \"" + this.expression + "\" but found " + value;
		assertThat(reason, value, instanceOf(String.class));
	}

	/**
	 * Evaluate the JSON path expression against the supplied {@code content}
	 * and assert that the resulting value is a {@link Boolean}.
	 * @param content the JSON content
	 * @since 4.2.1
	 */
	public void assertValueIsBoolean(String content) throws ParseException {
		Object value = assertExistsAndReturn(content);
		String reason = "Expected boolean at JSON path \"" + this.expression + "\" but found " + value;
		assertThat(reason, value, instanceOf(Boolean.class));
	}

	/**
	 * Evaluate the JSON path expression against the supplied {@code content}
	 * and assert that the resulting value is a {@link Number}.
	 * @param content the JSON content
	 * @since 4.2.1
	 */
	public void assertValueIsNumber(String content) throws ParseException {
		Object value = assertExistsAndReturn(content);
		String reason = "Expected number at JSON path \"" + this.expression + "\" but found " + value;
		assertThat(reason, value, instanceOf(Number.class));
	}

	/**
	 * Evaluate the JSON path expression against the supplied {@code content}
	 * and assert that the resulting value is an array.
	 * @param content the JSON content
	 */
	public void assertValueIsArray(String content) throws ParseException {
		Object value = assertExistsAndReturn(content);
		String reason = "Expected array for JSON path \"" + this.expression + "\" but found " + value;
		assertTrue(reason, value instanceof List);
	}

	/**
	 * Evaluate the JSON path expression against the supplied {@code content}
	 * and assert that the resulting value is a {@link Map}.
	 * @param content the JSON content
	 * @since 4.2.1
	 */
	public void assertValueIsMap(String content) throws ParseException {
		Object value = assertExistsAndReturn(content);
		String reason = "Expected map at JSON path \"" + this.expression + "\" but found " + value;
		assertThat(reason, value, instanceOf(Map.class));
	}

	/**
	 * Evaluate the JSON path expression against the supplied {@code content}
	 * and assert that the resulting value exists.
	 * @param content the JSON content
	 */
	public void exists(String content) throws ParseException {
		assertExistsAndReturn(content);
	}

	/**
	 * Evaluate the JSON path expression against the supplied {@code content}
	 * and assert that the resulting value is empty (i.e., that a match for
	 * the JSON path expression does not exist in the supplied content).
	 * @param content the JSON content
	 */
	public void doesNotExist(String content) throws ParseException {
		Object value;
		try {
			value = evaluateJsonPath(content);
		}
		catch (AssertionError ex) {
			return;
		}
		String reason = String.format("Expected no value for JSON path: %s but found: %s", this.expression, value);
		if (List.class.isInstance(value)) {
			assertTrue(reason, ((List<?>) value).isEmpty());
		}
		else {
			assertTrue(reason, value == null);
		}
	}

	private Object evaluateJsonPath(String content) throws ParseException {
		String message = "No value for JSON path \"" + this.expression + "\", exception: ";
		try {
			return this.jsonPath.read(content);
		}
		catch (InvalidPathException ex) {
			throw new AssertionError(message + ex.getMessage());
		}
		catch (ArrayIndexOutOfBoundsException ex) {
			throw new AssertionError(message + ex.getMessage());
		}
		catch (IndexOutOfBoundsException ex) {
			throw new AssertionError(message + ex.getMessage());
		}
	}

	private Object assertExistsAndReturn(String content) throws ParseException {
		Object value = evaluateJsonPath(content);
		String reason = "No value for JSON path \"" + this.expression + "\"";
		assertTrue(reason, value != null);
		return value;
	}

}
