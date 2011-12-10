/*
 * Copyright 2002-2011 the original author or authors.
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

package org.springframework.core.env;

import static java.lang.String.format;
import static org.springframework.util.StringUtils.commaDelimitedListToStringArray;
import static org.springframework.util.StringUtils.trimAllWhitespace;

import java.security.AccessControlException;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.core.convert.support.ConfigurableConversionService;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Abstract base class for {@link Environment} implementations. Supports the notion of
 * reserved default profile names and enables specifying active and default profiles
 * through the {@link #ACTIVE_PROFILES_PROPERTY_NAME} and
 * {@link #DEFAULT_PROFILES_PROPERTY_NAME} properties.
 *
 * <p>Concrete subclasses differ primarily on which {@link PropertySource} objects they
 * add by default. {@code AbstractEnvironment} adds none. Subclasses should contribute
 * property sources through the protected {@link #customizePropertySources()} hook, while
 * clients should customize using {@link ConfigurableEnvironment#getPropertySources()} and
 * working against the {@link MutablePropertySources} API. See
 * {@link ConfigurableEnvironment} Javadoc for usage examples.
 *
 * @author Chris Beams
 * @since 3.1
 * @see ConfigurableEnvironment
 * @see StandardEnvironment
 */
public abstract class AbstractEnvironment implements ConfigurableEnvironment {

	/**
	 * Name of property to set to specify active profiles: {@value}. Value may be comma
	 * delimited.
	 * <p>Note that certain shell environments such as Bash disallow the use of the period
	 * character in variable names. Assuming that Spring's {@link SystemEnvironmentPropertySource}
	 * is in use, this property may be specified as an environment variable as
	 * {@code SPRING_PROFILES_ACTIVE}.
	 * @see ConfigurableEnvironment#setActiveProfiles
	 */
	public static final String ACTIVE_PROFILES_PROPERTY_NAME = "spring.profiles.active";

	/**
	 * Name of property to set to specify profiles active by default: {@value}. Value may
	 * be comma delimited.
	 * <p>Note that certain shell environments such as Bash disallow the use of the period
	 * character in variable names. Assuming that Spring's {@link SystemEnvironmentPropertySource}
	 * is in use, this property may be specified as an environment variable as
	 * {@code SPRING_PROFILES_DEFAULT}.
	 * @see ConfigurableEnvironment#setDefaultProfiles
	 */
	public static final String DEFAULT_PROFILES_PROPERTY_NAME = "spring.profiles.default";

	/**
	 * Name of reserved default profile name: {@value}. If no default profile names are
	 * explicitly and no active profile names are explicitly set, this profile will
	 * automatically be activated by default.
	 * @see #getReservedDefaultProfiles
	 * @see ConfigurableEnvironment#setDefaultProfiles
	 * @see ConfigurableEnvironment#setActiveProfiles
	 * @see AbstractEnvironment#DEFAULT_PROFILES_PROPERTY_NAME
	 * @see AbstractEnvironment#ACTIVE_PROFILES_PROPERTY_NAME
	 */
	protected static final String RESERVED_DEFAULT_PROFILE_NAME = "default";

	protected final Log logger = LogFactory.getLog(getClass());

	private Set<String> activeProfiles = new LinkedHashSet<String>();
	private Set<String> defaultProfiles = new LinkedHashSet<String>(this.getReservedDefaultProfiles());

	private final MutablePropertySources propertySources = new MutablePropertySources(logger);
	private final ConfigurablePropertyResolver propertyResolver = new PropertySourcesPropertyResolver(propertySources);


	public AbstractEnvironment() {
		String name = this.getClass().getSimpleName();
		logger.debug(String.format("Initializing new %s", name));
		this.customizePropertySources(propertySources);
		logger.debug(String.format("Initialized %s with PropertySources %s", name, propertySources));
	}

	/**
	 * Customize the set of {@link PropertySource} objects to be searched by this
	 * {@code Environment} during calls to {@link #getProperty(String)} and related
	 * methods.
	 *
	 * <p>Subclasses that override this method are encouraged to add property
	 * sources using {@link MutablePropertySources#addLast(PropertySource)} such that
	 * further subclasses may call {@code super.customizePropertySources()} with
	 * predictable results. For example:
	 * <pre class="code">
	 * public class Level1Environment extends AbstractEnvironment {
	 *     &#064;Override
	 *     protected void customizePropertySources(MutablePropertySources propertySources) {
	 *         super.customizePropertySources(propertySources); // no-op from base class
	 *         propertySources.addLast(new PropertySourceA(...));
	 *         propertySources.addLast(new PropertySourceB(...));
	 *     }
	 * }
	 *
	 * public class Level2Environment extends Level1Environment {
	 *     &#064;Override
	 *     protected void customizePropertySources(MutablePropertySources propertySources) {
	 *         super.customizePropertySources(propertySources); // add all from superclass
	 *         propertySources.addLast(new PropertySourceC(...));
	 *         propertySources.addLast(new PropertySourceD(...));
	 *     }
	 * }
	 * </pre>
	 * In this arrangement, properties will be resolved against sources A, B, C, D in that
	 * order. That is to say that property source "A" has precedence over property source
	 * "D". If the {@code Level2Environment} subclass wished to give property sources C
	 * and D higher precedence than A and B, it could simply call
	 * {@code super.customizePropertySources} after, rather than before adding its own:
	 * <pre class="code">
	 * public class Level2Environment extends Level1Environment {
	 *     &#064;Override
	 *     protected void customizePropertySources(MutablePropertySources propertySources) {
	 *         propertySources.addLast(new PropertySourceC(...));
	 *         propertySources.addLast(new PropertySourceD(...));
	 *         super.customizePropertySources(propertySources); // add all from superclass
	 *     }
	 * }
	 * </pre>
	 * The search order is now C, D, A, B as desired.
	 *
	 * <p>Beyond these recommendations, subclasses may use any of the <code>add&#42;</code>,
	 * {@code remove}, or {@code replace} methods exposed by {@link MutablePropertySources}
	 * in order to create the exact arrangement of property sources desired.
	 *
	 * <p>The base implementation in {@link AbstractEnvironment#customizePropertySources}
	 * registers no property sources.
	 *
	 * <p>Note that clients of any {@link ConfigurableEnvironment} may further customize
	 * property sources via the {@link #getPropertySources()} accessor, typically within
	 * an {@link org.springframework.context.ApplicationContextInitializer
	 * ApplicationContextInitializer}. For example:
	 * <pre class="code">
	 * ConfigurableEnvironment env = new StandardEnvironment();
	 * env.getPropertySources().addLast(new PropertySourceX(...));
	 * </pre>
	 *
	 * @see MutablePropertySources
	 * @see PropertySourcesPropertyResolver
	 * @see org.springframework.context.ApplicationContextInitializer
	 */
	protected void customizePropertySources(MutablePropertySources propertySources) {
	}

	/**
	 * Return the set of reserved default profile names. This implementation returns
	 * {@value #RESERVED_DEFAULT_PROFILE_NAME}. Subclasses may override in order to
	 * customize the set of reserved names.
	 * @see #RESERVED_DEFAULT_PROFILE_NAME
	 * @see #doGetDefaultProfiles()
	 */
	protected Set<String> getReservedDefaultProfiles() {
		return Collections.singleton(RESERVED_DEFAULT_PROFILE_NAME);
	}


	//---------------------------------------------------------------------
	// Implementation of ConfigurableEnvironment interface
	//---------------------------------------------------------------------

	public String[] getActiveProfiles() {
		return StringUtils.toStringArray(doGetActiveProfiles());
	}

	/**
	 * Return the set of active profiles as explicitly set through
	 * {@link #setActiveProfiles} or if the current set of active profiles
	 * is empty, check for the presence of the {@value #ACTIVE_PROFILES_PROPERTY_NAME}
	 * property and assign its value to the set of active profiles.
	 * @see #getActiveProfiles()
	 * @see #ACTIVE_PROFILES_PROPERTY_NAME
	 */
	protected Set<String> doGetActiveProfiles() {
		if (this.activeProfiles.isEmpty()) {
			String profiles = this.getProperty(ACTIVE_PROFILES_PROPERTY_NAME);
			if (StringUtils.hasText(profiles)) {
				setActiveProfiles(commaDelimitedListToStringArray(trimAllWhitespace(profiles)));
			}
		}
		return this.activeProfiles;
	}

	public void setActiveProfiles(String... profiles) {
		Assert.notNull(profiles, "Profile array must not be null");
		this.activeProfiles.clear();
		for (String profile : profiles) {
			this.addActiveProfile(profile);
		}
	}

	public void addActiveProfile(String profile) {
		logger.debug(String.format("Activating profile '%s'", profile));
		this.validateProfile(profile);
		this.activeProfiles.add(profile);
	}

	public String[] getDefaultProfiles() {
		return StringUtils.toStringArray(doGetDefaultProfiles());
	}

	/**
	 * Return the set of default profiles explicitly set via
	 * {@link #setDefaultProfiles(String...)} or if the current set of default profiles
	 * consists only of {@linkplain #getReservedDefaultProfiles() reserved default
	 * profiles}, then check for the presence of the
	 * {@value #DEFAULT_PROFILES_PROPERTY_NAME} property and assign its value (if any)
	 * to the set of default profiles.
	 * @see #AbstractEnvironment()
	 * @see #getDefaultProfiles()
	 * @see #DEFAULT_PROFILES_PROPERTY_NAME
	 * @see #getReservedDefaultProfiles()
	 */
	protected Set<String> doGetDefaultProfiles() {
		if (this.defaultProfiles.equals(this.getReservedDefaultProfiles())) {
			String profiles = this.getProperty(DEFAULT_PROFILES_PROPERTY_NAME);
			if (StringUtils.hasText(profiles)) {
				this.setDefaultProfiles(commaDelimitedListToStringArray(trimAllWhitespace(profiles)));
			}
		}
		return this.defaultProfiles;
	}

	/**
	 * {@inheritDoc}
	 * <p>Calling this method removes overrides any reserved default profiles
	 * that may have been added during construction of the environment.
	 * @see #AbstractEnvironment()
	 * @see #getReservedDefaultProfiles()
	 */
	public void setDefaultProfiles(String... profiles) {
		Assert.notNull(profiles, "Profile array must not be null");
		this.defaultProfiles.clear();
		for (String profile : profiles) {
			this.validateProfile(profile);
			this.defaultProfiles.add(profile);
		}
	}

	public boolean acceptsProfiles(String... profiles) {
		Assert.notEmpty(profiles, "Must specify at least one profile");
		boolean activeProfileFound = false;
		Set<String> activeProfiles = this.doGetActiveProfiles();
		Set<String> defaultProfiles = this.doGetDefaultProfiles();
		for (String profile : profiles) {
			this.validateProfile(profile);
			if (activeProfiles.contains(profile)
					|| (activeProfiles.isEmpty() && defaultProfiles.contains(profile))) {
				activeProfileFound = true;
				break;
			}
		}
		return activeProfileFound;
	}

	/**
	 * Validate the given profile, called internally prior to adding to the set of
	 * active or default profiles.
	 * <p>Subclasses may override to impose further restrictions on profile syntax.
	 * @throws IllegalArgumentException if the profile is null, empty or whitespace-only
	 * @see #acceptsProfiles
	 * @see #addActiveProfile
	 * @see #setDefaultProfiles
	 */
	protected void validateProfile(String profile) {
		Assert.hasText(profile, "Invalid profile [" + profile + "]: must contain text");
	}

	public MutablePropertySources getPropertySources() {
		return this.propertySources;
	}

	@SuppressWarnings("unchecked")
	public Map<String, Object> getSystemEnvironment() {
		Map<String, ?> systemEnvironment;
		try {
			systemEnvironment = System.getenv();
		}
		catch (AccessControlException ex) {
			systemEnvironment = new ReadOnlySystemAttributesMap() {
				@Override
				protected String getSystemAttribute(String variableName) {
					try {
						return System.getenv(variableName);
					}
					catch (AccessControlException ex) {
						if (logger.isInfoEnabled()) {
							logger.info(format("Caught AccessControlException when accessing system environment variable " +
									"[%s]; its value will be returned [null]. Reason: %s", variableName, ex.getMessage()));
						}
						return null;
					}
				}
			};
		}
		return (Map<String, Object>) systemEnvironment;
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	public Map<String, Object> getSystemProperties() {
		Map systemProperties;
		try {
			systemProperties = System.getProperties();
		}
		catch (AccessControlException ex) {
			systemProperties = new ReadOnlySystemAttributesMap() {
				@Override
				protected String getSystemAttribute(String propertyName) {
					try {
						return System.getProperty(propertyName);
					}
					catch (AccessControlException ex) {
						if (logger.isInfoEnabled()) {
							logger.info(format("Caught AccessControlException when accessing system property " +
									"[%s]; its value will be returned [null]. Reason: %s", propertyName, ex.getMessage()));
						}
						return null;
					}
				}
			};
		}
		return systemProperties;
	}


	//---------------------------------------------------------------------
	// Implementation of ConfigurablePropertyResolver interface
	//---------------------------------------------------------------------

	public boolean containsProperty(String key) {
		return this.propertyResolver.containsProperty(key);
	}

	public String getProperty(String key) {
		return this.propertyResolver.getProperty(key);
	}

	public String getProperty(String key, String defaultValue) {
		return this.propertyResolver.getProperty(key, defaultValue);
	}

	public <T> T getProperty(String key, Class<T> targetType) {
		return this.propertyResolver.getProperty(key, targetType);
	}

	public <T> T getProperty(String key, Class<T> targetType, T defaultValue) {
		return this.propertyResolver.getProperty(key, targetType, defaultValue);
	};

	public <T> Class<T> getPropertyAsClass(String key, Class<T> targetType) {
		return this.propertyResolver.getPropertyAsClass(key, targetType);
	}

	public String getRequiredProperty(String key) throws IllegalStateException {
		return this.propertyResolver.getRequiredProperty(key);
	}

	public <T> T getRequiredProperty(String key, Class<T> targetType) throws IllegalStateException {
		return this.propertyResolver.getRequiredProperty(key, targetType);
	}

	public void setRequiredProperties(String... requiredProperties) {
		this.propertyResolver.setRequiredProperties(requiredProperties);
	}

	public void validateRequiredProperties() throws MissingRequiredPropertiesException {
		this.propertyResolver.validateRequiredProperties();
	}

	public String resolvePlaceholders(String text) {
		return this.propertyResolver.resolvePlaceholders(text);
	}

	public String resolveRequiredPlaceholders(String text) throws IllegalArgumentException {
		return this.propertyResolver.resolveRequiredPlaceholders(text);
	}

	public void setConversionService(ConfigurableConversionService conversionService) {
		this.propertyResolver.setConversionService(conversionService);
	}

	public ConfigurableConversionService getConversionService() {
		return this.propertyResolver.getConversionService();
	}

	public void setPlaceholderPrefix(String placeholderPrefix) {
		this.propertyResolver.setPlaceholderPrefix(placeholderPrefix);
	}


	public void setPlaceholderSuffix(String placeholderSuffix) {
		this.propertyResolver.setPlaceholderSuffix(placeholderSuffix);
	}


	public void setValueSeparator(String valueSeparator) {
		this.propertyResolver.setValueSeparator(valueSeparator);
	}


	@Override
	public String toString() {
		return format("%s {activeProfiles=%s, defaultProfiles=%s, propertySources=%s}",
				getClass().getSimpleName(), this.activeProfiles, this.defaultProfiles, this.propertySources);
	}

}
