/*
 * Copyright 2002-2022 the original author or authors.
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

package org.springframework.web.service.invoker;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.core.MethodParameter;
import org.springframework.core.convert.ConversionService;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.ValueConstants;


/**
 * Base class for arguments that resolve to a named request value such as a
 * request header, path variable, cookie, and others.
 *
 * @author Rossen Stoyanchev
 * @since 6.0
 */
public abstract class AbstractNamedValueArgumentResolver implements HttpServiceArgumentResolver {

	protected final Log logger = LogFactory.getLog(getClass());

	private final ConversionService conversionService;

	private final Map<MethodParameter, NamedValueInfo> namedValueInfoCache = new ConcurrentHashMap<>(256);


	/**
	 * Create an instance.
	 * @param conversionService the {@link ConversionService} to use to format
	 * Object to String values.
	 */
	protected AbstractNamedValueArgumentResolver(ConversionService conversionService) {
		Assert.notNull(conversionService, "ConversionService is required");
		this.conversionService = conversionService;
	}


	/**
	 * Return the configured {@link ConversionService}.
	 */
	public ConversionService getConversionService() {
		return this.conversionService;
	}


	@SuppressWarnings("unchecked")
	@Override
	public boolean resolve(
			@Nullable Object argument, MethodParameter parameter, HttpRequestValues.Builder requestValues) {

		NamedValueInfo info = getNamedValueInfo(parameter);
		if (info == null) {
			return false;
		}

		if (Map.class.isAssignableFrom(parameter.getParameterType())) {
			Assert.isInstanceOf(Map.class, argument);
			for (Map.Entry<String, ?> entry : ((Map<String, ?>) argument).entrySet()) {
				addSingleOrMultipleValues(
						entry.getKey(), entry.getValue(), false, null, info.label, info.multiValued,
						requestValues);
			}
		}
		else {
			addSingleOrMultipleValues(
					info.name, argument, info.required, info.defaultValue, info.label, info.multiValued,
					requestValues);
		}

		return true;
	}

	@Nullable
	private NamedValueInfo getNamedValueInfo(MethodParameter parameter) {
		NamedValueInfo info = this.namedValueInfoCache.get(parameter);
		if (info == null) {
			info = createNamedValueInfo(parameter);
			if (info == null) {
				return null;
			}
			info = updateNamedValueInfo(parameter, info);
			this.namedValueInfoCache.put(parameter, info);
		}
		return info;
	}

	/**
	 * Return information about the request value, or {@code null} if the
	 * parameter does not represent a request value of interest.
	 */
	@Nullable
	protected abstract NamedValueInfo createNamedValueInfo(MethodParameter parameter);

	private NamedValueInfo updateNamedValueInfo(MethodParameter parameter, NamedValueInfo info) {
		String name = info.name;
		if (info.name.isEmpty()) {
			name = parameter.getParameterName();
			if (name == null) {
				throw new IllegalArgumentException(
						"Name for argument of type [" + parameter.getParameterType().getName() + "] " +
								"not specified, and parameter name information not found in class file either.");
			}
		}
		boolean required = (info.required && !parameter.getParameterType().equals(Optional.class));
		String defaultValue = (ValueConstants.DEFAULT_NONE.equals(info.defaultValue) ? null : info.defaultValue);
		return info.update(name, required, defaultValue);
	}

	private void addSingleOrMultipleValues(
			String name, @Nullable Object value, boolean required, @Nullable Object defaultValue,
			String valueLabel, boolean supportsMultiValues, HttpRequestValues.Builder requestValues) {

		if (supportsMultiValues) {
			value = (ObjectUtils.isArray(value) ? Arrays.asList((Object[]) value) : value);
			if (value instanceof Collection<?> elements) {
				boolean hasValues = false;
				for (Object element : elements) {
					if (element != null) {
						hasValues = true;
						addSingleValue(name, element, false, null, valueLabel, requestValues);
					}
				}
				if (hasValues) {
					return;
				}
				value = null;
			}
		}

		addSingleValue(name, value, required, defaultValue, valueLabel, requestValues);
	}

	private void addSingleValue(
			String name, @Nullable Object value, boolean required, @Nullable Object defaultValue, String valueLabel,
			HttpRequestValues.Builder requestValues) {

		if (value instanceof Optional<?> optionalValue) {
			value = optionalValue.orElse(null);
		}

		if (value == null && defaultValue != null) {
			value = defaultValue;
		}

		if (!(value instanceof String)) {
			value = getConversionService().convert(value, String.class);
		}

		if (value == null) {
			Assert.isTrue(!required, "Missing " + valueLabel + " value '" + name + "'");
			return;
		}

		if (logger.isTraceEnabled()) {
			logger.trace("Resolved " + valueLabel + " value '" + name + ":" + value + "'");
		}

		addRequestValue(name, (String) value, requestValues);
	}

	/**
	 * Add the given, single request value. This may be called multiples times
	 * if the request value is multivalued.
	 * @param name the request value name
	 * @param value the value
	 * @param requestValues builder to add the request value to
	 */
	protected abstract void addRequestValue(String name, String value, HttpRequestValues.Builder requestValues);


	/**
	 * Info about a request value, typically extracted from a method parameter annotation.
	 */
	protected static class NamedValueInfo {

		private final String name;

		private final boolean required;

		@Nullable
		private final String defaultValue;

		private final String label;

		private final boolean multiValued;

		/**
		 * Create an instance.
		 * @param name the name to use, possibly empty if not specified
		 * @param required whether it is marked as required
		 * @param defaultValue fallback value, possibly {@link ValueConstants#DEFAULT_NONE}
		 * @param label how it should appear in error messages, e.g. "path variable", "request header"
		 */
		public NamedValueInfo(
				String name, boolean required, @Nullable String defaultValue, String label, boolean multiValued) {

			this.name = name;
			this.required = required;
			this.defaultValue = defaultValue;
			this.label = label;
			this.multiValued = multiValued;
		}

		public NamedValueInfo update(String name, boolean required, @Nullable String defaultValue) {
			return new NamedValueInfo(name, required, defaultValue, this.label, this.multiValued);
		}

	}

}
