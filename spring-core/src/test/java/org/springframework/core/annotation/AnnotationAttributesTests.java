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

package org.springframework.core.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import org.springframework.core.annotation.AnnotationUtilsTests.ImplicitAliasesContextConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for {@link AnnotationAttributes}.
 *
 * @author Chris Beams
 * @author Sam Brannen
 * @author Juergen Hoeller
 * @since 3.1.1
 */
public class AnnotationAttributesTests {

	private AnnotationAttributes attributes = new AnnotationAttributes();


	@Test
	public void typeSafeAttributeAccess() {
		AnnotationAttributes nestedAttributes = new AnnotationAttributes();
		nestedAttributes.put("value", 10);
		nestedAttributes.put("name", "algernon");

		attributes.put("name", "dave");
		attributes.put("names", new String[] {"dave", "frank", "hal"});
		attributes.put("bool1", true);
		attributes.put("bool2", false);
		attributes.put("color", Color.RED);
		attributes.put("class", Integer.class);
		attributes.put("classes", new Class<?>[] {Number.class, Short.class, Integer.class});
		attributes.put("number", 42);
		attributes.put("anno", nestedAttributes);
		attributes.put("annoArray", new AnnotationAttributes[] {nestedAttributes});

		assertThat(attributes.getString("name")).isEqualTo("dave");
		assertThat(attributes.getStringArray("names")).isEqualTo(new String[] {"dave", "frank", "hal"});
		assertThat(attributes.getBoolean("bool1")).isEqualTo(true);
		assertThat(attributes.getBoolean("bool2")).isEqualTo(false);
		assertThat(attributes.<Color>getEnum("color")).isEqualTo(Color.RED);
		assertTrue(attributes.getClass("class").equals(Integer.class));
		assertThat(attributes.getClassArray("classes")).isEqualTo(new Class<?>[] {Number.class, Short.class, Integer.class});
		assertThat(attributes.<Integer>getNumber("number")).isEqualTo(42);
		assertThat(attributes.getAnnotation("anno").<Integer>getNumber("value")).isEqualTo(10);
		assertThat(attributes.getAnnotationArray("annoArray")[0].getString("name")).isEqualTo("algernon");

	}

	@Test
	public void unresolvableClass() throws Exception {
		attributes.put("unresolvableClass", new ClassNotFoundException("myclass"));
		assertThatIllegalArgumentException().isThrownBy(() ->
				attributes.getClass("unresolvableClass"))
			.withMessageContaining("myclass");
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
		assertThat(attributes.getStringArray("names")).isEqualTo(new String[] {"Dogbert"});
		assertThat(attributes.getClassArray("classes")).isEqualTo(new Class<?>[] {Number.class});

		AnnotationAttributes[] array = attributes.getAnnotationArray("nestedAttributes");
		assertNotNull(array);
		assertThat(array.length).isEqualTo(1);
		assertThat(array[0].getString("name")).isEqualTo("Dilbert");

		Filter[] filters = attributes.getAnnotationArray("filters", Filter.class);
		assertNotNull(filters);
		assertThat(filters.length).isEqualTo(1);
		assertThat(filters[0].pattern()).isEqualTo("foo");
	}

	@Test
	public void nestedAnnotations() throws Exception {
		Filter filter = FilteredClass.class.getAnnotation(Filter.class);

		attributes.put("filter", filter);
		attributes.put("filters", new Filter[] {filter, filter});

		Filter retrievedFilter = attributes.getAnnotation("filter", Filter.class);
		assertThat(retrievedFilter).isEqualTo(filter);
		assertThat(retrievedFilter.pattern()).isEqualTo("foo");

		Filter[] retrievedFilters = attributes.getAnnotationArray("filters", Filter.class);
		assertNotNull(retrievedFilters);
		assertEquals(2, retrievedFilters.length);
		assertThat(retrievedFilters[1].pattern()).isEqualTo("foo");
	}

	@Test
	public void getEnumWithNullAttributeName() {
		assertThatIllegalArgumentException().isThrownBy(() ->
				attributes.getEnum(null))
			.withMessageContaining("must not be null or empty");
	}

	@Test
	public void getEnumWithEmptyAttributeName() {
		assertThatIllegalArgumentException().isThrownBy(() ->
				attributes.getEnum(""))
			.withMessageContaining("must not be null or empty");
	}

	@Test
	public void getEnumWithUnknownAttributeName() {
		assertThatIllegalArgumentException().isThrownBy(() ->
				attributes.getEnum("bogus"))
			.withMessageContaining("Attribute 'bogus' not found");
	}

	@Test
	public void getEnumWithTypeMismatch() {
		attributes.put("color", "RED");
		assertThatIllegalArgumentException().isThrownBy(() ->
				attributes.getEnum("color"))
			.withMessageContaining("Attribute 'color' is of type String, but Enum was expected");
	}

	@Test
	public void getAliasedStringWithImplicitAliases() {
		String value = "metaverse";
		List<String> aliases = Arrays.asList("value", "location1", "location2", "location3", "xmlFile", "groovyScript");

		attributes = new AnnotationAttributes(ImplicitAliasesContextConfig.class);
		attributes.put("value", value);
		AnnotationUtils.postProcessAnnotationAttributes(null, attributes, false);
		aliases.stream().forEach(alias -> assertEquals(value, attributes.getString(alias)));

		attributes = new AnnotationAttributes(ImplicitAliasesContextConfig.class);
		attributes.put("location1", value);
		AnnotationUtils.postProcessAnnotationAttributes(null, attributes, false);
		aliases.stream().forEach(alias -> assertEquals(value, attributes.getString(alias)));

		attributes = new AnnotationAttributes(ImplicitAliasesContextConfig.class);
		attributes.put("value", value);
		attributes.put("location1", value);
		attributes.put("xmlFile", value);
		attributes.put("groovyScript", value);
		AnnotationUtils.postProcessAnnotationAttributes(null, attributes, false);
		aliases.stream().forEach(alias -> assertEquals(value, attributes.getString(alias)));
	}

	@Test
	public void getAliasedStringArrayWithImplicitAliases() {
		String[] value = new String[] {"test.xml"};
		List<String> aliases = Arrays.asList("value", "location1", "location2", "location3", "xmlFile", "groovyScript");

		attributes = new AnnotationAttributes(ImplicitAliasesContextConfig.class);
		attributes.put("location1", value);
		AnnotationUtils.postProcessAnnotationAttributes(null, attributes, false);
		aliases.stream().forEach(alias -> assertArrayEquals(value, attributes.getStringArray(alias)));

		attributes = new AnnotationAttributes(ImplicitAliasesContextConfig.class);
		attributes.put("value", value);
		AnnotationUtils.postProcessAnnotationAttributes(null, attributes, false);
		aliases.stream().forEach(alias -> assertArrayEquals(value, attributes.getStringArray(alias)));

		attributes = new AnnotationAttributes(ImplicitAliasesContextConfig.class);
		attributes.put("location1", value);
		attributes.put("value", value);
		AnnotationUtils.postProcessAnnotationAttributes(null, attributes, false);
		aliases.stream().forEach(alias -> assertArrayEquals(value, attributes.getStringArray(alias)));

		attributes = new AnnotationAttributes(ImplicitAliasesContextConfig.class);
		attributes.put("location1", value);
		AnnotationUtils.registerDefaultValues(attributes);
		AnnotationUtils.postProcessAnnotationAttributes(null, attributes, false);
		aliases.stream().forEach(alias -> assertArrayEquals(value, attributes.getStringArray(alias)));

		attributes = new AnnotationAttributes(ImplicitAliasesContextConfig.class);
		attributes.put("value", value);
		AnnotationUtils.registerDefaultValues(attributes);
		AnnotationUtils.postProcessAnnotationAttributes(null, attributes, false);
		aliases.stream().forEach(alias -> assertArrayEquals(value, attributes.getStringArray(alias)));

		attributes = new AnnotationAttributes(ImplicitAliasesContextConfig.class);
		AnnotationUtils.registerDefaultValues(attributes);
		AnnotationUtils.postProcessAnnotationAttributes(null, attributes, false);
		aliases.stream().forEach(alias -> assertArrayEquals(new String[] {""}, attributes.getStringArray(alias)));
	}


	enum Color {

		RED, WHITE, BLUE
	}


	@Retention(RetentionPolicy.RUNTIME)
	@interface Filter {

		@AliasFor(attribute = "classes")
		Class<?>[] value() default {};

		@AliasFor(attribute = "value")
		Class<?>[] classes() default {};

		String pattern();
	}


	@Filter(pattern = "foo")
	static class FilteredClass {
	}

}
