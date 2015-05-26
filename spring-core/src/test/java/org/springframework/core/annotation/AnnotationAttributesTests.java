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

package org.springframework.core.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

/**
 * Unit tests for {@link AnnotationAttributes}.
 *
 * @author Chris Beams
 * @author Sam Brannen
 * @since 3.1.1
 */
public class AnnotationAttributesTests {

	private final AnnotationAttributes attributes = new AnnotationAttributes();

	@Rule
	public final ExpectedException exception = ExpectedException.none();


	@Test
	public void typeSafeAttributeAccess() {
		AnnotationAttributes nestedAttributes = new AnnotationAttributes();
		nestedAttributes.put("value", 10);
		nestedAttributes.put("name", "algernon");

		attributes.put("name", "dave");
		attributes.put("names", new String[] { "dave", "frank", "hal" });
		attributes.put("bool1", true);
		attributes.put("bool2", false);
		attributes.put("color", Color.RED);
		attributes.put("class", Integer.class);
		attributes.put("classes", new Class<?>[] { Number.class, Short.class, Integer.class });
		attributes.put("number", 42);
		attributes.put("anno", nestedAttributes);
		attributes.put("annoArray", new AnnotationAttributes[] { nestedAttributes });

		assertThat(attributes.getString("name"), equalTo("dave"));
		assertThat(attributes.getStringArray("names"), equalTo(new String[] { "dave", "frank", "hal" }));
		assertThat(attributes.getBoolean("bool1"), equalTo(true));
		assertThat(attributes.getBoolean("bool2"), equalTo(false));
		assertThat(attributes.<Color>getEnum("color"), equalTo(Color.RED));
		assertTrue(attributes.getClass("class").equals(Integer.class));
		assertThat(attributes.getClassArray("classes"), equalTo(new Class[] { Number.class, Short.class, Integer.class }));
		assertThat(attributes.<Integer>getNumber("number"), equalTo(42));
		assertThat(attributes.getAnnotation("anno").<Integer>getNumber("value"), equalTo(10));
		assertThat(attributes.getAnnotationArray("annoArray")[0].getString("name"), equalTo("algernon"));
	}

	@Test
	public void singleElementToSingleElementArrayConversionSupport() throws Exception {
		Filter filter = FilteredClass.class.getAnnotation(Filter.class);

		AnnotationAttributes nestedAttributes = new AnnotationAttributes();
		nestedAttributes.put("name", "Dilbert");

		// Store single elements
		attributes.put("names", "Dogbert");
		attributes.put("classes", Number.class);
		attributes.put("nestedAttributes", nestedAttributes);
		attributes.put("filters", filter);

		// Get back arrays of single elements
		assertThat(attributes.getStringArray("names"), equalTo(new String[] { "Dogbert" }));
		assertThat(attributes.getClassArray("classes"), equalTo(new Class[] { Number.class }));

		AnnotationAttributes[] array = attributes.getAnnotationArray("nestedAttributes");
		assertNotNull(array);
		assertThat(array.length, is(1));
		assertThat(array[0].getString("name"), equalTo("Dilbert"));

		Filter[] filters = attributes.getAnnotationArray("filters", Filter.class);
		assertNotNull(filters);
		assertThat(filters.length, is(1));
		assertThat(filters[0].pattern(), equalTo("foo"));
	}

	@Test
	public void nestedAnnotations() throws Exception {
		Filter filter = FilteredClass.class.getAnnotation(Filter.class);

		attributes.put("filter", filter);
		attributes.put("filters", new Filter[] { filter, filter });

		Filter retrievedFilter = attributes.getAnnotation("filter", Filter.class);
		assertThat(retrievedFilter, equalTo(filter));
		assertThat(retrievedFilter.pattern(), equalTo("foo"));

		Filter[] retrievedFilters = attributes.getAnnotationArray("filters", Filter.class);
		assertNotNull(retrievedFilters);
		assertEquals(2, retrievedFilters.length);
		assertThat(retrievedFilters[1].pattern(), equalTo("foo"));
	}

	@Test
	public void getEnumWithNullAttributeName() {
		exception.expect(IllegalArgumentException.class);
		exception.expectMessage(containsString("attributeName must not be null or empty"));
		attributes.getEnum(null);
	}

	@Test
	public void getEnumWithEmptyAttributeName() {
		exception.expect(IllegalArgumentException.class);
		exception.expectMessage(containsString("attributeName must not be null or empty"));
		attributes.getEnum("");
	}

	@Test
	public void getEnumWithUnknownAttributeName() {
		exception.expect(IllegalArgumentException.class);
		exception.expectMessage(containsString("Attribute 'bogus' not found"));
		attributes.getEnum("bogus");
	}

	@Test
	public void getEnumWithTypeMismatch() {
		attributes.put("color", "RED");
		exception.expect(IllegalArgumentException.class);
		exception.expectMessage(containsString("Attribute 'color' is of type [String], but [Enum] was expected"));
		attributes.getEnum("color");
	}


	enum Color {
		RED, WHITE, BLUE
	}

	@Retention(RetentionPolicy.RUNTIME)
	@interface Filter {
		String pattern();
	}

	@Filter(pattern = "foo")
	static class FilteredClass {
	}
}
