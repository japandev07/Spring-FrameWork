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

package org.springframework.test.context;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import static org.junit.Assert.*;
import static org.springframework.test.context.ContextLoaderUtils.*;

/**
 * Unit tests for {@link ContextLoaderUtils} involving resolution of active bean
 * definition profiles.
 *
 * @author Sam Brannen
 * @author Michail Nikolaev
 * @since 3.1
 */
public class ContextLoaderUtilsActiveProfilesTests extends AbstractContextLoaderUtilsTests {

	@Test
	public void resolveActiveProfilesWithoutAnnotation() {
		String[] profiles = resolveActiveProfiles(Enigma.class);
		assertArrayEquals(EMPTY_STRING_ARRAY, profiles);
	}

	@Test
	public void resolveActiveProfilesWithNoProfilesDeclared() {
		String[] profiles = resolveActiveProfiles(BareAnnotations.class);
		assertArrayEquals(EMPTY_STRING_ARRAY, profiles);
	}

	@Test
	public void resolveActiveProfilesWithEmptyProfiles() {
		String[] profiles = resolveActiveProfiles(EmptyProfiles.class);
		assertArrayEquals(EMPTY_STRING_ARRAY, profiles);
	}

	@Test
	public void resolveActiveProfilesWithDuplicatedProfiles() {
		String[] profiles = resolveActiveProfiles(DuplicatedProfiles.class);
		assertNotNull(profiles);
		assertEquals(3, profiles.length);

		List<String> list = Arrays.asList(profiles);
		assertTrue(list.contains("foo"));
		assertTrue(list.contains("bar"));
		assertTrue(list.contains("baz"));
	}

	@Test
	public void resolveActiveProfilesWithLocalAnnotation() {
		String[] profiles = resolveActiveProfiles(LocationsFoo.class);
		assertNotNull(profiles);
		assertArrayEquals(new String[] { "foo" }, profiles);
	}

	@Test
	public void resolveActiveProfilesWithInheritedAnnotationAndLocations() {
		String[] profiles = resolveActiveProfiles(InheritedLocationsFoo.class);
		assertNotNull(profiles);
		assertArrayEquals(new String[] { "foo" }, profiles);
	}

	@Test
	public void resolveActiveProfilesWithInheritedAnnotationAndClasses() {
		String[] profiles = resolveActiveProfiles(InheritedClassesFoo.class);
		assertNotNull(profiles);
		assertArrayEquals(new String[] { "foo" }, profiles);
	}

	@Test
	public void resolveActiveProfilesWithLocalAndInheritedAnnotations() {
		String[] profiles = resolveActiveProfiles(LocationsBar.class);
		assertNotNull(profiles);
		assertEquals(2, profiles.length);

		List<String> list = Arrays.asList(profiles);
		assertTrue(list.contains("foo"));
		assertTrue(list.contains("bar"));
	}

	@Test
	public void resolveActiveProfilesWithOverriddenAnnotation() {
		String[] profiles = resolveActiveProfiles(Animals.class);
		assertNotNull(profiles);
		assertEquals(2, profiles.length);

		List<String> list = Arrays.asList(profiles);
		assertTrue(list.contains("dog"));
		assertTrue(list.contains("cat"));
	}

	/**
	 * @since 4.0
	 */
	@Test
	public void resolveActiveProfilesWithMetaAnnotation() {
		String[] profiles = resolveActiveProfiles(MetaLocationsFoo.class);
		assertNotNull(profiles);
		assertArrayEquals(new String[] { "foo" }, profiles);
	}

	/**
	 * @since 4.0
	 */
	@Test
	public void resolveActiveProfilesWithMetaAnnotationAndOverrides() {
		String[] profiles = resolveActiveProfiles(MetaLocationsFooWithOverrides.class);
		assertNotNull(profiles);
		assertArrayEquals(new String[] { "foo" }, profiles);
	}

	/**
	 * @since 4.0
	 */
	@Test
	public void resolveActiveProfilesWithMetaAnnotationAndOverriddenAttributes() {
		String[] profiles = resolveActiveProfiles(MetaLocationsFooWithOverriddenAttributes.class);
		assertNotNull(profiles);
		assertArrayEquals(new String[] { "foo1", "foo2" }, profiles);
	}

	/**
	 * @since 4.0
	 */
	@Test
	public void resolveActiveProfilesWithLocalAndInheritedMetaAnnotations() {
		String[] profiles = resolveActiveProfiles(MetaLocationsBar.class);
		assertNotNull(profiles);
		assertEquals(2, profiles.length);

		List<String> list = Arrays.asList(profiles);
		assertTrue(list.contains("foo"));
		assertTrue(list.contains("bar"));
	}

	/**
	 * @since 4.0
	 */
	@Test
	public void resolveActiveProfilesWithOverriddenMetaAnnotation() {
		String[] profiles = resolveActiveProfiles(MetaAnimals.class);
		assertNotNull(profiles);
		assertEquals(2, profiles.length);

		List<String> list = Arrays.asList(profiles);
		assertTrue(list.contains("dog"));
		assertTrue(list.contains("cat"));
	}

	/**
	 * @since 4.0
	 */
	@Test
	public void resolveActiveProfilesWithResolver() {
		String[] profiles = resolveActiveProfiles(FooActiveProfilesResolverTestCase.class);
		assertNotNull(profiles);
		assertEquals(1, profiles.length);
		assertArrayEquals(new String[] { "foo" }, profiles);
	}

	/**
	 * @since 4.0
	 */
	@Test
	public void resolveActiveProfilesWithInheritedResolver() {
		String[] profiles = resolveActiveProfiles(InheritedFooActiveProfilesResolverTestCase.class);
		assertNotNull(profiles);
		assertEquals(1, profiles.length);
		assertArrayEquals(new String[] { "foo" }, profiles);
	}

	/**
	 * @since 4.0
	 */
	@Test
	public void resolveActiveProfilesWithMergedInheritedResolver() {
		String[] profiles = resolveActiveProfiles(MergedInheritedFooActiveProfilesResolverTestCase.class);
		assertNotNull(profiles);
		assertEquals(2, profiles.length);
		List<String> list = Arrays.asList(profiles);
		assertTrue(list.contains("foo"));
		assertTrue(list.contains("bar"));
	}

	/**
	 * @since 4.0
	 */
	@Test
	public void resolveActiveProfilesWithOverridenInheritedResolver() {
		String[] profiles = resolveActiveProfiles(OverridenInheritedFooActiveProfilesResolverTestCase.class);
		assertNotNull(profiles);
		assertEquals(1, profiles.length);
		assertArrayEquals(new String[] { "bar" }, profiles);
	}

	/**
	 * @since 4.0
	 */
	@Test(expected = IllegalStateException.class)
	public void resolveActiveProfilesWithConflictingResolverAndProfiles() {
		resolveActiveProfiles(ConflictingResolverAndProfilesTestCase.class);
	}

	/**
	 * @since 4.0
	 */
	@Test(expected = IllegalStateException.class)
	public void resolveActiveProfilesWithConflictingResolverAndValue() {
		resolveActiveProfiles(ConflictingResolverAndValueTestCase.class);
	}

	/**
	 * @since 4.0
	 */
	@Test(expected = IllegalStateException.class)
	public void resolveActiveProfilesWithConflictingProfilesAndValue() {
		resolveActiveProfiles(ConflictingProfilesAndValueTestCase.class);
	}

	/**
	 * @since 4.0
	 */
	@Test(expected = IllegalStateException.class)
	public void resolveActiveProfilesWithResolverWithoutDefaultConstructor() {
		resolveActiveProfiles(NoDefaultConstructorActiveProfilesResolverTestCase.class);
	}

	/**
	 * @since 4.0
	 */
	@Test(expected = IllegalStateException.class)
	public void resolveActiveProfilesWithResolverThatReturnsNull() {
		resolveActiveProfiles(NullActiveProfilesResolverTestCase.class);
	}


	// -------------------------------------------------------------------------

	@ActiveProfiles({ "    ", "\t" })
	private static class EmptyProfiles {
	}

	@ActiveProfiles({ "foo", "bar", "  foo", "bar  ", "baz" })
	private static class DuplicatedProfiles {
	}

	@ActiveProfiles(profiles = { "dog", "cat" }, inheritProfiles = false)
	private static class Animals extends LocationsBar {
	}

	@ActiveProfiles(profiles = { "dog", "cat" }, inheritProfiles = false)
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.TYPE)
	private static @interface MetaAnimalsConfig {
	}

	@ActiveProfiles
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.TYPE)
	private static @interface MetaProfilesWithOverrides {

		String[] profiles() default { "dog", "cat" };

		Class<? extends ActiveProfilesResolver> resolver() default ActiveProfilesResolver.class;

		boolean inheritProfiles() default false;
	}

	@MetaAnimalsConfig
	private static class MetaAnimals extends MetaLocationsBar {
	}

	private static class InheritedLocationsFoo extends LocationsFoo {
	}

	private static class InheritedClassesFoo extends ClassesFoo {
	}

	@ActiveProfiles(resolver = NullActiveProfilesResolver.class)
	private static class NullActiveProfilesResolverTestCase {
	}

	@ActiveProfiles(resolver = NoDefaultConstructorActiveProfilesResolver.class)
	private static class NoDefaultConstructorActiveProfilesResolverTestCase {
	}

	@ActiveProfiles(resolver = FooActiveProfilesResolver.class)
	private static class FooActiveProfilesResolverTestCase {
	}

	private static class InheritedFooActiveProfilesResolverTestCase extends FooActiveProfilesResolverTestCase {
	}

	@ActiveProfiles(resolver = BarActiveProfilesResolver.class)
	private static class MergedInheritedFooActiveProfilesResolverTestCase extends
			InheritedFooActiveProfilesResolverTestCase {
	}

	@ActiveProfiles(resolver = BarActiveProfilesResolver.class, inheritProfiles = false)
	private static class OverridenInheritedFooActiveProfilesResolverTestCase extends
			InheritedFooActiveProfilesResolverTestCase {
	}

	@ActiveProfiles(resolver = BarActiveProfilesResolver.class, profiles = "conflict")
	private static class ConflictingResolverAndProfilesTestCase {
	}

	@ActiveProfiles(resolver = BarActiveProfilesResolver.class, value = "conflict")
	private static class ConflictingResolverAndValueTestCase {
	}

	@ActiveProfiles(profiles = "conflict", value = "conflict")
	private static class ConflictingProfilesAndValueTestCase {
	}

	private static class FooActiveProfilesResolver implements ActiveProfilesResolver {

		@Override
		public String[] resolve(Class<?> testClass) {
			return new String[] { "foo" };
		}
	}

	private static class BarActiveProfilesResolver implements ActiveProfilesResolver {

		@Override
		public String[] resolve(Class<?> testClass) {
			return new String[] { "bar" };
		}
	}

	private static class NullActiveProfilesResolver implements ActiveProfilesResolver {

		@Override
		public String[] resolve(Class<?> testClass) {
			return null;
		}
	}

	private static class NoDefaultConstructorActiveProfilesResolver implements ActiveProfilesResolver {

		@SuppressWarnings("unused")
		NoDefaultConstructorActiveProfilesResolver(Object agument) {
		}

		@Override
		public String[] resolve(Class<?> testClass) {
			return null;
		}
	}

}
