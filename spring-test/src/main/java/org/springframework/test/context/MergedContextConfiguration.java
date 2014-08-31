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

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.style.ToStringCreator;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

/**
 * {@code MergedContextConfiguration} encapsulates the <em>merged</em>
 * context configuration declared on a test class and all of its superclasses
 * via {@link ContextConfiguration @ContextConfiguration},
 * {@link ActiveProfiles @ActiveProfiles}, and
 * {@link TestPropertySource @TestPropertySource}.
 *
 * <p>Merged context resource locations, annotated classes, active profiles,
 * property resource locations, and in-lined properties represent all declared
 * values in the test class hierarchy taking into consideration the semantics
 * of the {@link ContextConfiguration#inheritLocations},
 * {@link ActiveProfiles#inheritProfiles},
 * {@link TestPropertySource#inheritLocations}, and
 * {@link TestPropertySource#inheritProperties} flags.
 *
 * <p>A {@link SmartContextLoader} uses {@code MergedContextConfiguration}
 * to load an {@link org.springframework.context.ApplicationContext ApplicationContext}.
 *
 * <p>{@code MergedContextConfiguration} is also used by the {@link TestContext}
 * as the context cache key for caching an
 * {@link org.springframework.context.ApplicationContext ApplicationContext}
 * that was loaded using properties of this {@code MergedContextConfiguration}.
 *
 * @author Sam Brannen
 * @since 3.1
 * @see ContextConfiguration
 * @see ContextHierarchy
 * @see ActiveProfiles
 * @see TestPropertySource
 * @see ContextConfigurationAttributes
 * @see SmartContextLoader#loadContext(MergedContextConfiguration)
 */
public class MergedContextConfiguration implements Serializable {

	private static final long serialVersionUID = -3290560718464957422L;

	private static final String[] EMPTY_STRING_ARRAY = new String[0];
	private static final Class<?>[] EMPTY_CLASS_ARRAY = new Class<?>[0];
	private static final Set<Class<? extends ApplicationContextInitializer<? extends ConfigurableApplicationContext>>> EMPTY_INITIALIZER_CLASSES = //
	Collections.<Class<? extends ApplicationContextInitializer<? extends ConfigurableApplicationContext>>> emptySet();

	private final Class<?> testClass;
	private final String[] locations;
	private final Class<?>[] classes;
	private final Set<Class<? extends ApplicationContextInitializer<? extends ConfigurableApplicationContext>>> contextInitializerClasses;
	private final String[] activeProfiles;
	private final String[] propertySourceLocations;
	private final String[] propertySourceProperties;
	private final ContextLoader contextLoader;
	private final CacheAwareContextLoaderDelegate cacheAwareContextLoaderDelegate;

	private final MergedContextConfiguration parent;


	private static String[] processStrings(String[] array) {
		return array == null ? EMPTY_STRING_ARRAY : array;
	}

	private static Class<?>[] processClasses(Class<?>[] classes) {
		return classes == null ? EMPTY_CLASS_ARRAY : classes;
	}

	private static Set<Class<? extends ApplicationContextInitializer<? extends ConfigurableApplicationContext>>> processContextInitializerClasses(
			Set<Class<? extends ApplicationContextInitializer<? extends ConfigurableApplicationContext>>> contextInitializerClasses) {
		return contextInitializerClasses == null ? EMPTY_INITIALIZER_CLASSES
				: Collections.unmodifiableSet(contextInitializerClasses);
	}

	private static String[] processActiveProfiles(String[] activeProfiles) {
		if (activeProfiles == null) {
			return EMPTY_STRING_ARRAY;
		}

		// Active profiles must be unique and sorted in order to support proper
		// cache key generation. Specifically, profile sets {foo,bar} and
		// {bar,foo} must both result in the same array (e.g., [bar,foo]).
		SortedSet<String> sortedProfilesSet = new TreeSet<String>(Arrays.asList(activeProfiles));
		return StringUtils.toStringArray(sortedProfilesSet);
	}

	/**
	 * Generate a null-safe {@link String} representation of the supplied
	 * {@link ContextLoader} based solely on the fully qualified name of the
	 * loader or &quot;null&quot; if the supplied loaded is {@code null}.
	 */
	protected static String nullSafeToString(ContextLoader contextLoader) {
		return contextLoader == null ? "null" : contextLoader.getClass().getName();
	}

	/**
	 * Create a new {@code MergedContextConfiguration} instance for the
	 * supplied parameters.
	 * <p>Delegates to
	 * {@link #MergedContextConfiguration(Class, String[], Class[], Set, String[], String[], String[], ContextLoader, CacheAwareContextLoaderDelegate, MergedContextConfiguration)}.
	 *
	 * @param testClass the test class for which the configuration was merged
	 * @param locations the merged context resource locations
	 * @param classes the merged annotated classes
	 * @param activeProfiles the merged active bean definition profiles
	 * @param contextLoader the resolved {@code ContextLoader}
	 */
	public MergedContextConfiguration(Class<?> testClass, String[] locations, Class<?>[] classes,
			String[] activeProfiles, ContextLoader contextLoader) {
		this(testClass, locations, classes, null, activeProfiles, contextLoader);
	}

	/**
	 * Create a new {@code MergedContextConfiguration} instance for the
	 * supplied parameters.
	 * <p>Delegates to
	 * {@link #MergedContextConfiguration(Class, String[], Class[], Set, String[], String[], String[], ContextLoader, CacheAwareContextLoaderDelegate, MergedContextConfiguration)}.
	 *
	 * @param testClass the test class for which the configuration was merged
	 * @param locations the merged context resource locations
	 * @param classes the merged annotated classes
	 * @param contextInitializerClasses the merged context initializer classes
	 * @param activeProfiles the merged active bean definition profiles
	 * @param contextLoader the resolved {@code ContextLoader}
	 * @see #MergedContextConfiguration(Class, String[], Class[], Set, String[], ContextLoader, CacheAwareContextLoaderDelegate, MergedContextConfiguration)
	 */
	public MergedContextConfiguration(
			Class<?> testClass,
			String[] locations,
			Class<?>[] classes,
			Set<Class<? extends ApplicationContextInitializer<? extends ConfigurableApplicationContext>>> contextInitializerClasses,
			String[] activeProfiles, ContextLoader contextLoader) {
		this(testClass, locations, classes, contextInitializerClasses, activeProfiles, contextLoader, null, null);
	}

	/**
	 * Create a new {@code MergedContextConfiguration} instance for the
	 * supplied parameters.
	 * <p>Delegates to
	 * {@link #MergedContextConfiguration(Class, String[], Class[], Set, String[], String[], String[], ContextLoader, CacheAwareContextLoaderDelegate, MergedContextConfiguration)}.
	 *
	 * @param testClass the test class for which the configuration was merged
	 * @param locations the merged context resource locations
	 * @param classes the merged annotated classes
	 * @param contextInitializerClasses the merged context initializer classes
	 * @param activeProfiles the merged active bean definition profiles
	 * @param contextLoader the resolved {@code ContextLoader}
	 * @param cacheAwareContextLoaderDelegate a cache-aware context loader
	 * delegate with which to retrieve the parent context
	 * @param parent the parent configuration or {@code null} if there is no parent
	 * @since 3.2.2
	 */
	public MergedContextConfiguration(
			Class<?> testClass,
			String[] locations,
			Class<?>[] classes,
			Set<Class<? extends ApplicationContextInitializer<? extends ConfigurableApplicationContext>>> contextInitializerClasses,
			String[] activeProfiles, ContextLoader contextLoader,
			CacheAwareContextLoaderDelegate cacheAwareContextLoaderDelegate, MergedContextConfiguration parent) {
		this(testClass, locations, classes, contextInitializerClasses, activeProfiles, null, null, contextLoader,
			cacheAwareContextLoaderDelegate, parent);
	}

	/**
	 * Create a new {@code MergedContextConfiguration} instance by copying
	 * all fields from the supplied {@code MergedContextConfiguration}.
	 * @since 4.1
	 */
	public MergedContextConfiguration(MergedContextConfiguration mergedConfig) {
		this(mergedConfig.testClass, mergedConfig.locations, mergedConfig.classes,
			mergedConfig.contextInitializerClasses, mergedConfig.activeProfiles, mergedConfig.propertySourceLocations,
			mergedConfig.propertySourceProperties, mergedConfig.contextLoader,
			mergedConfig.cacheAwareContextLoaderDelegate, mergedConfig.parent);
	}

	/**
	 * Create a new {@code MergedContextConfiguration} instance for the
	 * supplied parameters.
	 *
	 * <p>If a {@code null} value is supplied for {@code locations},
	 * {@code classes}, {@code activeProfiles}, {@code propertySourceLocations},
	 * or {@code propertySourceProperties} an empty array will be stored instead.
	 * If a {@code null} value is supplied for the
	 * {@code contextInitializerClasses} an empty set will be stored instead.
	 * Furthermore, active profiles will be sorted, and duplicate profiles
	 * will be removed.
	 *
	 * @param testClass the test class for which the configuration was merged
	 * @param locations the merged context resource locations
	 * @param classes the merged annotated classes
	 * @param contextInitializerClasses the merged context initializer classes
	 * @param activeProfiles the merged active bean definition profiles
	 * @param propertySourceLocations the merged {@code PropertySource} locations
	 * @param propertySourceProperties the merged {@code PropertySource} properties
	 * @param contextLoader the resolved {@code ContextLoader}
	 * @param cacheAwareContextLoaderDelegate a cache-aware context loader
	 * delegate with which to retrieve the parent context
	 * @param parent the parent configuration or {@code null} if there is no parent
	 * @since 4.1
	 */
	public MergedContextConfiguration(
			Class<?> testClass,
			String[] locations,
			Class<?>[] classes,
			Set<Class<? extends ApplicationContextInitializer<? extends ConfigurableApplicationContext>>> contextInitializerClasses,
			String[] activeProfiles, String[] propertySourceLocations, String[] propertySourceProperties,
			ContextLoader contextLoader, CacheAwareContextLoaderDelegate cacheAwareContextLoaderDelegate,
			MergedContextConfiguration parent) {
		this.testClass = testClass;
		this.locations = processStrings(locations);
		this.classes = processClasses(classes);
		this.contextInitializerClasses = processContextInitializerClasses(contextInitializerClasses);
		this.activeProfiles = processActiveProfiles(activeProfiles);
		this.propertySourceLocations = processStrings(propertySourceLocations);
		this.propertySourceProperties = processStrings(propertySourceProperties);
		this.contextLoader = contextLoader;
		this.cacheAwareContextLoaderDelegate = cacheAwareContextLoaderDelegate;
		this.parent = parent;
	}

	/**
	 * Get the {@linkplain Class test class} associated with this {@code MergedContextConfiguration}.
	 */
	public Class<?> getTestClass() {
		return testClass;
	}

	/**
	 * Get the merged resource locations for {@code ApplicationContext}
	 * configuration files for the {@linkplain #getTestClass() test class}.
	 * <p>Context resource locations typically represent XML configuration
	 * files or Groovy scripts.
	 */
	public String[] getLocations() {
		return locations;
	}

	/**
	 * Get the merged annotated classes for the {@linkplain #getTestClass() test class}.
	 */
	public Class<?>[] getClasses() {
		return classes;
	}

	/**
	 * Determine if this {@code MergedContextConfiguration} instance has
	 * path-based context resource locations.
	 *
	 * @return {@code true} if the {@link #getLocations() locations} array is not empty
	 * @since 4.0.4
	 * @see #hasResources()
	 * @see #hasClasses()
	 */
	public boolean hasLocations() {
		return !ObjectUtils.isEmpty(getLocations());
	}

	/**
	 * Determine if this {@code MergedContextConfiguration} instance has
	 * class-based resources.
	 *
	 * @return {@code true} if the {@link #getClasses() classes} array is not empty
	 * @since 4.0.4
	 * @see #hasResources()
	 * @see #hasLocations()
	 */
	public boolean hasClasses() {
		return !ObjectUtils.isEmpty(getClasses());
	}

	/**
	 * Determine if this {@code MergedContextConfiguration} instance has
	 * either path-based context resource locations or class-based resources.
	 *
	 * @return {@code true} if either the {@link #getLocations() locations}
	 * or the {@link #getClasses() classes} array is not empty
	 * @since 4.0.4
	 * @see #hasLocations()
	 * @see #hasClasses()
	 */
	public boolean hasResources() {
		return hasLocations() || hasClasses();
	}

	/**
	 * Get the merged {@code ApplicationContextInitializer} classes for the
	 * {@linkplain #getTestClass() test class}.
	 */
	public Set<Class<? extends ApplicationContextInitializer<? extends ConfigurableApplicationContext>>> getContextInitializerClasses() {
		return contextInitializerClasses;
	}

	/**
	 * Get the merged active bean definition profiles for the
	 * {@linkplain #getTestClass() test class}.
	 * @see ActiveProfiles
	 */
	public String[] getActiveProfiles() {
		return activeProfiles;
	}

	/**
	 * Get the merged resource locations for test {@code PropertySources} for the
	 * {@linkplain #getTestClass() test class}.
	 * @see TestPropertySource#locations
	 * @see java.util.Properties
	 */
	public String[] getPropertySourceLocations() {
		return propertySourceLocations;
	}

	/**
	 * Get the merged test {@code PropertySource} properties for the
	 * {@linkplain #getTestClass() test class}.
	 * <p>Properties will be loaded into the {@code Environment}'s set of
	 * {@code PropertySources}.
	 * @see TestPropertySource#properties
	 * @see java.util.Properties
	 */
	public String[] getPropertySourceProperties() {
		return propertySourceProperties;
	}

	/**
	 * Get the resolved {@link ContextLoader} for the {@linkplain #getTestClass() test class}.
	 */
	public ContextLoader getContextLoader() {
		return contextLoader;
	}

	/**
	 * Get the {@link MergedContextConfiguration} for the parent application context in a
	 * context hierarchy.
	 *
	 * @return the parent configuration or {@code null} if there is no parent
	 * @see #getParentApplicationContext()
	 * @since 3.2.2
	 */
	public MergedContextConfiguration getParent() {
		return this.parent;
	}

	/**
	 * Get the parent {@link ApplicationContext} for the context defined by this
	 * {@code MergedContextConfiguration} from the context cache.
	 * <p>
	 * If the parent context has not yet been loaded, it will be loaded, stored in the
	 * cache, and then returned.
	 *
	 * @return the parent {@code ApplicationContext} or {@code null} if there is no parent
	 * @see #getParent()
	 * @since 3.2.2
	 */
	public ApplicationContext getParentApplicationContext() {
		if (parent == null) {
			return null;
		}

		Assert.state(cacheAwareContextLoaderDelegate != null,
			"Cannot retrieve a parent application context without access to the CacheAwareContextLoaderDelegate.");
		return cacheAwareContextLoaderDelegate.loadContext(parent);
	}

	/**
	 * Generate a unique hash code for all properties of this
	 * {@code MergedContextConfiguration} excluding the
	 * {@linkplain #getTestClass() test class}.
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + Arrays.hashCode(locations);
		result = prime * result + Arrays.hashCode(classes);
		result = prime * result + contextInitializerClasses.hashCode();
		result = prime * result + Arrays.hashCode(activeProfiles);
		result = prime * result + Arrays.hashCode(propertySourceLocations);
		result = prime * result + Arrays.hashCode(propertySourceProperties);
		result = prime * result + (parent == null ? 0 : parent.hashCode());
		result = prime * result + nullSafeToString(contextLoader).hashCode();
		return result;
	}

	/**
	 * Determine if the supplied object is equal to this {@code MergedContextConfiguration}
	 * instance by comparing both object's {@linkplain #getLocations() locations},
	 * {@linkplain #getClasses() annotated classes},
	 * {@linkplain #getContextInitializerClasses() context initializer classes},
	 * {@linkplain #getActiveProfiles() active profiles},
	 * {@linkplain #getPropertySourceLocations() property source locations},
	 * {@linkplain #getPropertySourceProperties() property source properties},
	 * {@linkplain #getParent() parents}, and the fully qualified names of their
	 * {@link #getContextLoader() ContextLoaders}.
	 */
	@Override
	public boolean equals(Object obj) {

		if (this == obj) {
			return true;
		}
		if (!(obj instanceof MergedContextConfiguration)) {
			return false;
		}

		final MergedContextConfiguration that = (MergedContextConfiguration) obj;

		if (!Arrays.equals(this.locations, that.locations)) {
			return false;
		}

		if (!Arrays.equals(this.classes, that.classes)) {
			return false;
		}

		if (!this.contextInitializerClasses.equals(that.contextInitializerClasses)) {
			return false;
		}

		if (!Arrays.equals(this.activeProfiles, that.activeProfiles)) {
			return false;
		}

		if (!Arrays.equals(this.propertySourceLocations, that.propertySourceLocations)) {
			return false;
		}

		if (!Arrays.equals(this.propertySourceProperties, that.propertySourceProperties)) {
			return false;
		}

		if (this.parent == null) {
			if (that.parent != null) {
				return false;
			}
		}
		else if (!this.parent.equals(that.parent)) {
			return false;
		}

		if (!nullSafeToString(this.contextLoader).equals(nullSafeToString(that.contextLoader))) {
			return false;
		}

		return true;
	}

	/**
	 * Provide a String representation of the {@linkplain #getTestClass() test class},
	 * {@linkplain #getLocations() locations}, {@linkplain #getClasses() annotated classes},
	 * {@linkplain #getContextInitializerClasses() context initializer classes},
	 * {@linkplain #getActiveProfiles() active profiles},
	 * {@linkplain #getPropertySourceLocations() property source locations},
	 * {@linkplain #getPropertySourceProperties() property source properties},
	 * the name of the {@link #getContextLoader() ContextLoader}, and the
	 * {@linkplain #getParent() parent configuration}.
	 */
	@Override
	public String toString() {
		return new ToStringCreator(this)//
		.append("testClass", testClass)//
		.append("locations", ObjectUtils.nullSafeToString(locations))//
		.append("classes", ObjectUtils.nullSafeToString(classes))//
		.append("contextInitializerClasses", ObjectUtils.nullSafeToString(contextInitializerClasses))//
		.append("activeProfiles", ObjectUtils.nullSafeToString(activeProfiles))//
		.append("propertySourceLocations", ObjectUtils.nullSafeToString(propertySourceLocations))//
		.append("propertySourceProperties", ObjectUtils.nullSafeToString(propertySourceProperties))//
		.append("contextLoader", nullSafeToString(contextLoader))//
		.append("parent", parent)//
		.toString();
	}

}
