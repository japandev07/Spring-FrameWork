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
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.core.MethodParameter;
import org.springframework.core.convert.ConversionService;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ValueConstants;

/**
 * An implementation of {@link HttpServiceArgumentResolver} that resolves
 * request headers based on method arguments annotated
 * with {@link  RequestHeader}. {@code null} values are allowed only
 * if {@link RequestHeader#required()} is {@code true}. {@code null}
 * values are replaced with {@link RequestHeader#defaultValue()} if it
 * is not equal to {@link ValueConstants#DEFAULT_NONE}.
 *
 * @author Olga Maciaszek-Sharma
 * @since 6.0
 */
public class RequestHeaderArgumentResolver implements HttpServiceArgumentResolver {

	private static final Log logger = LogFactory.getLog(RequestHeaderArgumentResolver.class);

	private final ConversionService conversionService;

	public RequestHeaderArgumentResolver(ConversionService conversionService) {
		Assert.notNull(conversionService, "ConversionService is required");
		this.conversionService = conversionService;
	}

	@Override
	public boolean resolve(@Nullable Object argument, MethodParameter parameter,
			HttpRequestValues.Builder requestValues) {
		RequestHeader annotation = parameter.getParameterAnnotation(RequestHeader.class);

		if (annotation == null) {
			return false;
		}

		if (Map.class.isAssignableFrom(parameter.getParameterType())) {
			if (argument != null) {
				Assert.isInstanceOf(Map.class, argument);
				((Map<?, ?>) argument).forEach((key, value) ->
						addRequestHeader(key, value, annotation.required(), annotation.defaultValue(),
								requestValues));
			}
		}
		else {
			String name = StringUtils.hasText(annotation.value()) ?
					annotation.value() : annotation.name();
			name = StringUtils.hasText(name) ? name : parameter.getParameterName();
			Assert.notNull(name, "Failed to determine request header name for parameter: " + parameter);
			addRequestHeader(name, argument, annotation.required(), annotation.defaultValue(),
					requestValues);
		}
		return true;
	}

	private void addRequestHeader(
			Object name, @Nullable Object value, boolean required, String defaultValue,
			HttpRequestValues.Builder requestValues) {

		String stringName = this.conversionService.convert(name, String.class);
		Assert.notNull(stringName, "Failed to convert request header name '" +
				name + "' to String");

		if (value instanceof Optional) {
			value = ((Optional<?>) value).orElse(null);
		}

		if (value == null) {
			if (!ValueConstants.DEFAULT_NONE.equals(defaultValue)) {
				value = defaultValue;
			}
			else {
				Assert.isTrue(!required, "Missing required request header '" + stringName + "'");
				return;
			}
		}

		String[] headerValues = toStringArray(value);

		if (logger.isTraceEnabled()) {
			logger.trace("Resolved request header '" + stringName + "' to list of values: " +
					String.join(", ", headerValues));
		}

		requestValues.addHeader(stringName, headerValues);
	}

	private String[] toStringArray(Object value) {
		return toValueStream(value)
				.filter(Objects::nonNull)
				.map(headerElement -> headerElement instanceof String
						? (String) headerElement :
						this.conversionService.convert(headerElement, String.class))
				.filter(Objects::nonNull)
				.toArray(String[]::new);
	}

	private Stream<?> toValueStream(Object value) {
		if (value instanceof Object[]) {
			return Arrays.stream((Object[]) value);
		}
		if (value instanceof Collection<?>) {
			return ((Collection<?>) value).stream();
		}
		return Stream.of(value);
	}

}
