/*
 * Copyright 2004-2009 the original author or authors.
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
package org.springframework.ui.binding.support;

import java.lang.reflect.Array;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.context.expression.MapAccessor;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.GenericTypeResolver;
import org.springframework.core.convert.ConversionFailedException;
import org.springframework.core.convert.TypeConverter;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.support.DefaultTypeConverter;
import org.springframework.core.style.StylerUtils;
import org.springframework.expression.AccessException;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionException;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.SpelEvaluationException;
import org.springframework.expression.spel.SpelMessage;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParserConfiguration;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.expression.spel.support.StandardTypeConverter;
import org.springframework.ui.alert.Alert;
import org.springframework.ui.alert.Severity;
import org.springframework.ui.binding.Binder;
import org.springframework.ui.binding.Binding;
import org.springframework.ui.binding.BindingConfiguration;
import org.springframework.ui.binding.BindingResult;
import org.springframework.ui.binding.BindingResults;
import org.springframework.ui.binding.FormatterRegistry;
import org.springframework.ui.format.AnnotationFormatterFactory;
import org.springframework.ui.format.Formatter;
import org.springframework.util.Assert;

/**
 * A generic {@link Binder binder} suitable for use in most environments.
 * TODO - localization of alert messages using MessageResolver/MessageSource
 * @author Keith Donald
 * @since 3.0
 * @see #configureBinding(BindingConfiguration)
 * @see #bind(UserValues)
 */
@SuppressWarnings("unchecked")
public class GenericBinder implements Binder {

	private static final String[] EMPTY_STRING_ARRAY = new String[0];

	private Object model;

	private Map<String, Binding> bindings;

	private FormatterRegistry formatterRegistry = new GenericFormatterRegistry();

	private ExpressionParser expressionParser;

	private TypeConverter typeConverter;

	private boolean strict = false;

	private static Formatter defaultFormatter = new DefaultFormatter();

	/**
	 * Creates a new binder for the model object.
	 * @param model the model object containing properties this binder will bind to
	 */
	public GenericBinder(Object model) {
		Assert.notNull(model, "The model Object is required");
		this.model = model;
		bindings = new HashMap<String, Binding>();
		int parserConfig = SpelExpressionParserConfiguration.CreateListsOnAttemptToIndexIntoNull
				| SpelExpressionParserConfiguration.GrowListsOnIndexBeyondSize;
		expressionParser = new SpelExpressionParser(parserConfig);
		typeConverter = new DefaultTypeConverter();
	}

	public Object getModel() {
		return model;
	}

	public boolean isStrict() {
		return strict;
	}

	public void setStrict(boolean strict) {
		this.strict = strict;
	}

	public void setFormatterRegistry(FormatterRegistry formatterRegistry) {
		Assert.notNull(formatterRegistry, "The FormatterRegistry is required");
		this.formatterRegistry = formatterRegistry;
	}

	public Binding configureBinding(BindingConfiguration configuration) {
		Binding binding;
		try {
			binding = new BindingImpl(configuration);
		} catch (org.springframework.expression.ParseException e) {
			throw new IllegalArgumentException(e);
		}
		bindings.put(configuration.getProperty(), binding);
		return binding;
	}

	public void registerFormatter(Class<?> propertyType, Formatter<?> formatter) {
		formatterRegistry.add(propertyType, formatter);
	}

	public void registerFormatterFactory(AnnotationFormatterFactory<?, ?> factory) {
		formatterRegistry.add(factory);
	}

	public Binding getBinding(String property) {
		Binding binding = bindings.get(property);
		if (binding == null && !strict) {
			return configureBinding(new BindingConfiguration(property, null));
		} else {
			return binding;
		}
	}

	public BindingResults bind(Map<String, ? extends Object> sourceValues) {
		sourceValues = filter(sourceValues);
		ArrayListBindingResults results = new ArrayListBindingResults(sourceValues.size());
		for (Map.Entry<String, ? extends Object> sourceValue : sourceValues.entrySet()) {
			String property = sourceValue.getKey();
			Object value = sourceValue.getValue();
			BindingImpl binding = (BindingImpl) getBinding(property);
			if (binding != null) {
				results.add(binding.setValue(value));
			} else {
				results.add(new NoSuchBindingResult(property, value));
			}
		}		
		return results;
	}
	
	// subclassing hooks
	
	/**
	 * Hook subclasses may use to filter the source values to bind.
	 * @param sourceValues the original source values map provided by the caller
	 * @return the filtered source values map that will be used to bind
	 */
	protected Map<String, ? extends Object> filter(Map<String, ? extends Object> sourceValues) {
		return sourceValues;
	}

	// internal helpers

	static class ArrayListBindingResults implements BindingResults {

		private List<BindingResult> results;

		public ArrayListBindingResults() {
			results = new ArrayList<BindingResult>();
		}

		public ArrayListBindingResults(int size) {
			results = new ArrayList<BindingResult>(size);
		}

		public void add(BindingResult result) {
			results.add(result);
		}

		// implementing Iterable

		public Iterator<BindingResult> iterator() {
			return results.iterator();
		}

		// implementing BindingResults

		public BindingResults successes() {
			ArrayListBindingResults results = new ArrayListBindingResults();
			for (BindingResult result : this) {
				if (!result.isFailure()) {
					results.add(result);
				}
			}
			return results;
		}

		public BindingResults failures() {
			ArrayListBindingResults results = new ArrayListBindingResults();
			for (BindingResult result : this) {
				if (result.isFailure()) {
					results.add(result);
				}
			}
			return results;
		}

		public BindingResult get(int index) {
			return results.get(index);
		}

		public List<String> properties() {
			List<String> properties = new ArrayList<String>(results.size());
			for (BindingResult result : this) {
				properties.add(result.getProperty());
			}
			return properties;
		}

		public int size() {
			return results.size();
		}

	}

	class BindingImpl implements Binding {

		private Expression property;

		private Formatter formatter;

		public BindingImpl(BindingConfiguration config) throws org.springframework.expression.ParseException {
			property = expressionParser.parseExpression(config.getProperty());
			formatter = config.getFormatter();
		}

		// implementing Binding

		public String getValue() {
			Object value;
			try {
				value = property.getValue(createEvaluationContext());
			} catch (ExpressionException e) {
				throw new IllegalStateException("Failed to get property expression value - this should not happen", e);
			}
			return format(value);
		}

		public BindingResult setValue(Object value) {
			if (value instanceof String) {
				return setStringValue((String) value);
			} else if (value instanceof String[]) {
				return setStringValues((String[]) value);
			} else {
				return setObjectValue(value);
			}
		}

		public String format(Object selectableValue) {
			Formatter formatter;
			try {
				formatter = getFormatter();
			} catch (EvaluationException e) {
				throw new IllegalStateException(
						"Failed to get property expression value type - this should not happen", e);
			}
			Class<?> formattedType = getFormattedObjectType(formatter);
			selectableValue = typeConverter.convert(selectableValue, formattedType);
			return formatter.format(selectableValue, LocaleContextHolder.getLocale());
		}

		public boolean isCollection() {
			Class type = getType();
			TypeDescriptor<?> typeDesc = TypeDescriptor.valueOf(type);
			return typeDesc.isCollection() || typeDesc.isArray();
		}

		public String[] getCollectionValues() {
			Object multiValue;
			try {
				multiValue = property.getValue(createEvaluationContext());
			} catch (EvaluationException e) {
				throw new IllegalStateException("Failed to get property expression value - this should not happen", e);
			}
			if (multiValue == null) {
				return EMPTY_STRING_ARRAY;
			}
			TypeDescriptor<?> type = TypeDescriptor.valueOf(multiValue.getClass());
			String[] formattedValues;
			if (type.isCollection()) {
				Collection<?> values = ((Collection<?>) multiValue);
				formattedValues = (String[]) Array.newInstance(String.class, values.size());
				copy(values, formattedValues);
			} else if (type.isArray()) {
				formattedValues = (String[]) Array.newInstance(String.class, Array.getLength(multiValue));
				copy((Iterable<?>) multiValue, formattedValues);
			} else {
				throw new IllegalStateException();
			}
			return formattedValues;
		}

		// public impl only

		public Class<?> getType() {
			Class<?> type;
			try {
				type = property.getValueType(createEvaluationContext());
			} catch (EvaluationException e) {
				throw new IllegalArgumentException(
						"Failed to get property expression value type - this should not happen", e);
			}
			return type;
		}

		// internal helpers

		private BindingResult setStringValue(String formatted) {
			Formatter formatter;
			try {
				formatter = getFormatter();
			} catch (EvaluationException e) {
				// could occur the property was not found or is not readable
				// TODO probably should not handle all EL failures, only type conversion & property not found?
				return new ExpressionEvaluationErrorResult(property.getExpressionString(), formatted, e);
			}
			Object parsed;
			try {
				parsed = formatter.parse(formatted, LocaleContextHolder.getLocale());
			} catch (ParseException e) {
				return new InvalidFormatResult(property.getExpressionString(), formatted, e);
			}
			return setValue(parsed, formatted);
		}

		private BindingResult setStringValues(String[] formatted) {
			Formatter formatter;
			try {
				formatter = getFormatter();
			} catch (EvaluationException e) {
				// could occur the property was not found or is not readable
				// TODO probably should not handle all EL failures, only type conversion & property not found?
				return new ExpressionEvaluationErrorResult(property.getExpressionString(), formatted, e);
			}
			Class parsedType = getFormattedObjectType(formatter);
			if (parsedType == null) {
				parsedType = String.class;
			}
			Object parsed = Array.newInstance(parsedType, formatted.length);
			for (int i = 0; i < formatted.length; i++) {
				Object parsedValue;
				try {
					parsedValue = formatter.parse(formatted[i], LocaleContextHolder.getLocale());
				} catch (ParseException e) {
					return new InvalidFormatResult(property.getExpressionString(), formatted, e);
				}
				Array.set(parsed, i, parsedValue);
			}
			return setValue(parsed, formatted);
		}

		private BindingResult setObjectValue(Object value) {
			return setValue(value, value);
		}

		private Formatter getFormatter() throws EvaluationException {
			if (formatter != null) {
				return formatter;
			} else {
				Formatter<?> formatter = formatterRegistry.getFormatter(property
						.getValueTypeDescriptor(createEvaluationContext()));
				return formatter != null ? formatter : defaultFormatter;
			}
		}

		private void copy(Iterable<?> values, String[] formattedValues) {
			int i = 0;
			for (Object value : values) {
				formattedValues[i] = format(value);
				i++;
			}
		}

		private BindingResult setValue(Object parsedValue, Object userValue) {
			try {
				property.setValue(createEvaluationContext(), parsedValue);
				return new SuccessResult(property.getExpressionString(), userValue);
			} catch (EvaluationException e) {
				return new ExpressionEvaluationErrorResult(property.getExpressionString(), userValue, e);
			}
		}

		private EvaluationContext createEvaluationContext() {
			StandardEvaluationContext context = new StandardEvaluationContext();
			context.setRootObject(model);
			context.addPropertyAccessor(new MapAccessor());
			context.setTypeConverter(new StandardTypeConverter(typeConverter));
			return context;
		}

	}

	private Class getFormattedObjectType(Formatter formatter) {
		// TODO consider caching this info
		Class classToIntrospect = formatter.getClass();
		while (classToIntrospect != null) {
			Type[] genericInterfaces = classToIntrospect.getGenericInterfaces();
			for (Type genericInterface : genericInterfaces) {
				if (genericInterface instanceof ParameterizedType) {
					ParameterizedType pInterface = (ParameterizedType) genericInterface;
					if (Formatter.class.isAssignableFrom((Class) pInterface.getRawType())) {
						return getParameterClass(pInterface.getActualTypeArguments()[0], formatter.getClass());
					}
				}
			}
			classToIntrospect = classToIntrospect.getSuperclass();
		}
		return null;
	}

	private Class getParameterClass(Type parameterType, Class converterClass) {
		if (parameterType instanceof TypeVariable) {
			parameterType = GenericTypeResolver.resolveTypeVariable((TypeVariable) parameterType, converterClass);
		}
		if (parameterType instanceof Class) {
			return (Class) parameterType;
		}
		throw new IllegalArgumentException("Unable to obtain the java.lang.Class for parameterType [" + parameterType
				+ "] on Formatter [" + converterClass.getName() + "]");
	}

	static class DefaultFormatter implements Formatter {
		public String format(Object object, Locale locale) {
			if (object == null) {
				return "";
			} else {
				return object.toString();
			}
		}

		public Object parse(String formatted, Locale locale) throws ParseException {
			if (formatted == "") {
				return null;
			} else {
				return formatted;
			}
		}
	}

	static class NoSuchBindingResult implements BindingResult {
		private String property;
		
		private Object sourceValue;
		
		public NoSuchBindingResult(String property, Object sourceValue) {
			this.property = property;
			this.sourceValue = sourceValue;
		}
		
		public String getProperty() {
			return property;
		}

		public Object getSourceValue() {
			return sourceValue;
		}

		public boolean isFailure() {
			return true;
		}

		public Alert getAlert() {
			return new AbstractAlert() {
				public String getElement() {
					// TODO append model first? e.g. model.property
					return getProperty();
				}

				public String getCode() {
					return "noSuchBinding";
				}

				public Severity getSeverity() {
					return Severity.WARNING;
				}

				public String getMessage() {
					return "Failed to bind to property '" + property + "'; no binding has been added for the property";
				}
			};
		}		
	}
	
	static class InvalidFormatResult implements BindingResult {

		private String property;

		private Object formatted;

		public InvalidFormatResult(String property, Object formatted, ParseException e) {
			this.property = property;
			this.formatted = formatted;
		}

		public String getProperty() {
			return property;
		}

		public Object getSourceValue() {
			return formatted;
		}

		public boolean isFailure() {
			return true;
		}

		public Alert getAlert() {
			return new AbstractAlert() {
				public String getElement() {
					// TODO append model first? e.g. model.property
					return getProperty();
				}

				public String getCode() {
					return "invalidFormat";
				}

				public Severity getSeverity() {
					return Severity.ERROR;
				}

				public String getMessage() {
					return "Failed to bind to property '" + property + "'; the user value "
							+ StylerUtils.style(formatted) + " has an invalid format and could no be parsed";
				}
			};
		}
	}

	// TODO the if branching in here is not very clean
	static class ExpressionEvaluationErrorResult implements BindingResult {

		private String property;

		private Object formatted;

		private EvaluationException e;

		public ExpressionEvaluationErrorResult(String property, Object formatted, EvaluationException e) {
			this.property = property;
			this.formatted = formatted;
			this.e = e;
		}

		public String getProperty() {
			return property;
		}

		public Object getSourceValue() {
			return formatted;
		}

		public boolean isFailure() {
			return true;
		}

		public Alert getAlert() {
			return new AbstractAlert() {
				public String getElement() {
					// TODO append model first? e.g. model.property
					return getProperty();
				}

				public String getCode() {
					return getFailureCode();
				}
				
				public Severity getSeverity() {
					return getFailureSeverity();
				}

				public String getMessage() {
					return getFailureMessage();
				}
			};
		}

		public String getFailureCode() {
			SpelMessage spelCode = ((SpelEvaluationException) e).getMessageCode();
			if (spelCode == SpelMessage.EXCEPTION_DURING_PROPERTY_WRITE) {
				return "typeConversionFailure";
			} else if (spelCode == SpelMessage.PROPERTY_OR_FIELD_NOT_READABLE) {
				return "propertyNotFound";
			} else {
				// TODO return more specific code based on underlying EvaluationException error code
				return "couldNotSetValue";
			}
		}
		
		public Severity getFailureSeverity() {
			SpelMessage spelCode = ((SpelEvaluationException) e).getMessageCode();
			if (spelCode == SpelMessage.EXCEPTION_DURING_PROPERTY_WRITE) {
				return Severity.FATAL;
			} else if (spelCode == SpelMessage.PROPERTY_OR_FIELD_NOT_READABLE) {
				return Severity.WARNING;
			} else {
				return Severity.FATAL;
			}
		}

		public String getFailureMessage() {
			SpelMessage spelCode = ((SpelEvaluationException) e).getMessageCode();
			if (spelCode == SpelMessage.EXCEPTION_DURING_PROPERTY_WRITE) {
				AccessException accessException = (AccessException) e.getCause();
				if (accessException.getCause() != null) {
					Throwable cause = accessException.getCause();
					if (cause instanceof SpelEvaluationException
							&& ((SpelEvaluationException) cause).getMessageCode() == SpelMessage.TYPE_CONVERSION_ERROR) {
						ConversionFailedException failure = (ConversionFailedException) cause.getCause();
						return "Failed to bind to property '" + property + "'; user value "
								+ StylerUtils.style(formatted) + " could not be converted to property type ["
								+ failure.getTargetType().getName() + "]";
					}
				}
			} else if (spelCode == SpelMessage.PROPERTY_OR_FIELD_NOT_READABLE) {
				return "Failed to bind to property '" + property + "'; no such property exists on model";
			}
			return "Failed to bind to property '" + property + "'; reason = " + e.getLocalizedMessage();
		}
	}

	static class SuccessResult implements BindingResult {

		private String property;

		private Object formatted;

		public SuccessResult(String property, Object formatted) {
			this.property = property;
			this.formatted = formatted;
		}

		public String getProperty() {
			return property;
		}
		
		public Object getSourceValue() {
			return formatted;
		}
		
		public boolean isFailure() {
			return false;
		}
		
		public Alert getAlert() {
			return new AbstractAlert() {
				public String getElement() {
					// TODO append model first? e.g. model.property					
					return getProperty();
				}

				public String getCode() {
					return "bindSuccess";
				}

				public Severity getSeverity() {
					return Severity.INFO;
				}

				public String getMessage() {
					return "Sucessfully bound user value " + StylerUtils.style(formatted) + "to property '" + property + "'";
				}
			};
		}

	}
	
	static abstract class AbstractAlert implements Alert {
		public String toString() {
			return getElement() + ":" + getCode() + " - " + getMessage();
		}
	}
}
