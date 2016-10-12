/*
 * Copyright 2002-2016 the original author or authors.
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

package org.springframework.web.reactive.result.method.annotation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import reactor.core.publisher.Mono;

import org.springframework.beans.ConversionNotSupportedException;
import org.springframework.beans.TypeConverter;
import org.springframework.beans.TypeMismatchException;
import org.springframework.beans.factory.config.BeanExpressionContext;
import org.springframework.beans.factory.config.BeanExpressionResolver;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.core.MethodParameter;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.ValueConstants;
import org.springframework.web.reactive.result.method.BindingContext;
import org.springframework.web.reactive.result.method.HandlerMethodArgumentResolver;
import org.springframework.web.server.ServerErrorException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;

/**
 * Abstract base class for resolving method arguments from a named value.
 * Request parameters, request headers, and path variables are examples of named
 * values. Each may have a name, a required flag, and a default value.
 * <p>Subclasses define how to do the following:
 * <ul>
 * <li>Obtain named value information for a method parameter
 * <li>Resolve names into argument values
 * <li>Handle missing argument values when argument values are required
 * <li>Optionally handle a resolved value
 * </ul>
 * <p>A default value string can contain ${...} placeholders and Spring Expression
 * Language #{...} expressions. For this to work a
 * {@link ConfigurableBeanFactory} must be supplied to the class constructor.
 *
 * @author Rossen Stoyanchev
 * @since 5.0
 */
public abstract class AbstractNamedValueMethodArgumentResolver implements HandlerMethodArgumentResolver {

	private final ConfigurableBeanFactory configurableBeanFactory;

	private final BeanExpressionContext expressionContext;

	private final Map<MethodParameter, NamedValueInfo> namedValueInfoCache = new ConcurrentHashMap<>(256);


	/**
	 * @param beanFactory a bean factory to use for resolving ${...} placeholder
	 * and #{...} SpEL expressions in default values, or {@code null} if default
	 * values are not expected to contain expressions
	 */
	public AbstractNamedValueMethodArgumentResolver(ConfigurableBeanFactory beanFactory) {
		this.configurableBeanFactory = beanFactory;
		this.expressionContext = (beanFactory != null ? new BeanExpressionContext(beanFactory, null) : null);
	}


	@Override
	public Mono<Object> resolveArgument(MethodParameter parameter, BindingContext bindingContext,
			ServerWebExchange exchange) {

		NamedValueInfo namedValueInfo = getNamedValueInfo(parameter);
		MethodParameter nestedParameter = parameter.nestedIfOptional();

		Object resolvedName = resolveStringValue(namedValueInfo.name);
		if (resolvedName == null) {
			return Mono.error(new IllegalArgumentException(
					"Specified name must not resolve to null: [" + namedValueInfo.name + "]"));
		}

		ModelMap model = bindingContext.getModel();

		return resolveName(resolvedName.toString(), nestedParameter, exchange)
				.map(arg -> {
					if ("".equals(arg) && namedValueInfo.defaultValue != null) {
						arg = resolveStringValue(namedValueInfo.defaultValue);
					}
					arg = applyConversion(arg, parameter, bindingContext);
					handleResolvedValue(arg, namedValueInfo.name, parameter, model, exchange);
					return arg;
				})
				.otherwiseIfEmpty(getDefaultValue(
						namedValueInfo, parameter, bindingContext, model, exchange));
	}

	/**
	 * Obtain the named value for the given method parameter.
	 */
	private NamedValueInfo getNamedValueInfo(MethodParameter parameter) {
		NamedValueInfo namedValueInfo = this.namedValueInfoCache.get(parameter);
		if (namedValueInfo == null) {
			namedValueInfo = createNamedValueInfo(parameter);
			namedValueInfo = updateNamedValueInfo(parameter, namedValueInfo);
			this.namedValueInfoCache.put(parameter, namedValueInfo);
		}
		return namedValueInfo;
	}

	/**
	 * Create the {@link NamedValueInfo} object for the given method parameter.
	 * Implementations typically retrieve the method annotation by means of
	 * {@link MethodParameter#getParameterAnnotation(Class)}.
	 * @param parameter the method parameter
	 * @return the named value information
	 */
	protected abstract NamedValueInfo createNamedValueInfo(MethodParameter parameter);

	/**
	 * Create a new NamedValueInfo based on the given NamedValueInfo with
	 * sanitized values.
	 */
	private NamedValueInfo updateNamedValueInfo(MethodParameter parameter, NamedValueInfo info) {
		String name = info.name;
		if (info.name.length() == 0) {
			name = parameter.getParameterName();
			if (name == null) {
				String type = parameter.getNestedParameterType().getName();
				throw new IllegalArgumentException("Name for argument type [" + type + "] not " +
						"available, and parameter name information not found in class file either.");
			}
		}
		String defaultValue = (ValueConstants.DEFAULT_NONE.equals(info.defaultValue) ? null : info.defaultValue);
		return new NamedValueInfo(name, info.required, defaultValue);
	}

	/**
	 * Resolve the given annotation-specified value,
	 * potentially containing placeholders and expressions.
	 */
	private Object resolveStringValue(String value) {
		if (this.configurableBeanFactory == null) {
			return value;
		}
		String placeholdersResolved = this.configurableBeanFactory.resolveEmbeddedValue(value);
		BeanExpressionResolver exprResolver = this.configurableBeanFactory.getBeanExpressionResolver();
		if (exprResolver == null) {
			return value;
		}
		return exprResolver.evaluate(placeholdersResolved, this.expressionContext);
	}

	/**
	 * Resolve the given parameter type and value name into an argument value.
	 * @param name the name of the value being resolved
	 * @param parameter the method parameter to resolve to an argument value
	 * (pre-nested in case of a {@link java.util.Optional} declaration)
	 * @param exchange the current exchange
	 * @return the resolved argument (may be {@code null})
	 */
	protected abstract Mono<Object> resolveName(String name, MethodParameter parameter,
			ServerWebExchange exchange);

	private Object applyConversion(Object value, MethodParameter parameter, BindingContext bindingContext) {
		try {
			TypeConverter typeConverter = bindingContext.getTypeConverter();
			value = typeConverter.convertIfNecessary(value, parameter.getParameterType(), parameter);
		}
		catch (ConversionNotSupportedException ex) {
			throw new ServerErrorException("Conversion not supported.", parameter, ex);
		}
		catch (TypeMismatchException ex) {
			throw new ServerWebInputException("Type mismatch.", parameter, ex);
		}
		return value;
	}

	private Mono<Object> getDefaultValue(NamedValueInfo namedValueInfo, MethodParameter parameter,
			BindingContext bindingContext, ModelMap model, ServerWebExchange exchange) {

		Object value = null;
		try {
			if (namedValueInfo.defaultValue != null) {
				value = resolveStringValue(namedValueInfo.defaultValue);
			}
			else if (namedValueInfo.required && !parameter.isOptional()) {
				handleMissingValue(namedValueInfo.name, parameter, exchange);
			}
			value = handleNullValue(namedValueInfo.name, value, parameter.getNestedParameterType());
			value = applyConversion(value, parameter, bindingContext);
			handleResolvedValue(value, namedValueInfo.name, parameter, model, exchange);
			return Mono.justOrEmpty(value);
		}
		catch (Throwable ex) {
			return Mono.error(ex);
		}
	}

	/**
	 * Invoked when a named value is required, but
	 * {@link #resolveName(String, MethodParameter, ServerWebExchange)} returned
	 * {@code null} and there is no default value. Subclasses typically throw an
	 * exception in this case.
	 * @param name the name for the value
	 * @param parameter the method parameter
	 * @param exchange the current exchange
	 */
	@SuppressWarnings("UnusedParameters")
	protected void handleMissingValue(String name, MethodParameter parameter, ServerWebExchange exchange) {
		handleMissingValue(name, parameter);
	}

	/**
	 * Invoked when a named value is required, but
	 * {@link #resolveName(String, MethodParameter, ServerWebExchange)} returned
	 * {@code null} and there is no default value. Subclasses typically throw an
	 * exception in this case.
	 * @param name the name for the value
	 * @param parameter the method parameter
	 */
	protected void handleMissingValue(String name, MethodParameter parameter) {
		String typeName = parameter.getNestedParameterType().getSimpleName();
		throw new ServerWebInputException("Missing argument '" + name + "' for method " +
				"parameter of type " + typeName, parameter);
	}

	/**
	 * A {@code null} results in a {@code false} value for {@code boolean}s or
	 * an exception for other primitives.
	 */
	private Object handleNullValue(String name, Object value, Class<?> paramType) {
		if (value == null) {
			if (Boolean.TYPE.equals(paramType)) {
				return Boolean.FALSE;
			}
			else if (paramType.isPrimitive()) {
				throw new IllegalStateException("Optional " + paramType.getSimpleName() +
						" parameter '" + name + "' is present but cannot be translated into a" +
						" null value due to being declared as a primitive type. " +
						"Consider declaring it as object wrapper for the corresponding primitive type.");
			}
		}
		return value;
	}

	/**
	 * Invoked after a value is resolved.
	 * @param arg the resolved argument value
	 * @param name the argument name
	 * @param parameter the argument parameter type
	 * @param model the model
	 * @param exchange the current exchange
	 */
	@SuppressWarnings("UnusedParameters")
	protected void handleResolvedValue(Object arg, String name, MethodParameter parameter,
			ModelMap model, ServerWebExchange exchange) {
	}


	/**
	 * Represents the information about a named value, including name, whether
	 * it's required and a default value.
	 */
	protected static class NamedValueInfo {

		private final String name;

		private final boolean required;

		private final String defaultValue;

		public NamedValueInfo(String name, boolean required, String defaultValue) {
			this.name = name;
			this.required = required;
			this.defaultValue = defaultValue;
		}
	}

}
