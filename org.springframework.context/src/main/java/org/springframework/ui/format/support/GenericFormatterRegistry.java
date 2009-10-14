/*
 * Copyright 2002-2009 the original author or authors.
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

package org.springframework.ui.format.support;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.text.ParseException;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.BeanUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.GenericTypeResolver;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.ui.format.AnnotationFormatterFactory;
import org.springframework.ui.format.Formatted;
import org.springframework.ui.format.Formatter;
import org.springframework.ui.format.FormatterRegistry;
import org.springframework.util.Assert;
import org.springframework.util.ReflectionUtils;

/**
 * A generic implementation of {@link org.springframework.ui.format.FormatterRegistry}
 * suitable for use in most environments.
 *
 * @author Keith Donald
 * @author Juergen Hoeller
 * @since 3.0
 * @see #setFormatters(Set)
 * @see #setFormatterMap(Map)
 * @see #setAnnotationFormatterMap(Map)
 * @see #setAnnotationFormatterFactories(Set)
 * @see #setConversionService(ConversionService)
 * @see #addFormatterByType(Formatter)
 * @see #addFormatterByType(Class, Formatter)
 * @see #addFormatterByAnnotation(Class, Formatter)
 * @see #addFormatterByAnnotation(AnnotationFormatterFactory) 
 */
public class GenericFormatterRegistry implements FormatterRegistry, ApplicationContextAware, Cloneable {

	private final Map<Class, FormatterHolder> typeFormatters = new ConcurrentHashMap<Class, FormatterHolder>();

	private final Map<Class, AnnotationFormatterFactoryHolder> annotationFormatters = new ConcurrentHashMap<Class, AnnotationFormatterFactoryHolder>();

	private ConversionService conversionService;

	private ApplicationContext applicationContext;

	private boolean shared = true;

	/**
	 * Registers the formatters in the set provided.
	 * JavaBean-friendly alternative to calling {@link #addFormatterByType(Formatter)}.
	 * @see #add(Formatter)
	 */
	public void setFormatters(Set<Formatter<?>> formatters) {
		for (Formatter<?> formatter : formatters) {
			addFormatterByType(formatter);
		}
	}

	/**
	 * Registers the formatters in the map provided by type.
	 * JavaBean-friendly alternative to calling {@link #addFormatterByType(Class, Formatter)}.
	 * @see #add(Class, Formatter)
	 */
	public void setFormatterMap(Map<Class<?>, Formatter<?>> formatters) {
		for (Map.Entry<Class<?>, Formatter<?>> entry : formatters.entrySet()) {
			addFormatterByType(entry.getKey(), entry.getValue());
		}
	}

	/**
	 * Registers the formatters in the map provided by annotation type.
	 * JavaBean-friendly alternative to calling {@link #addFormatterByAnnotation(Class, Formatter)}.
	 * @see #add(Class, Formatter)
	 */
	public void setAnnotationFormatterMap(Map<Class<? extends Annotation>, Formatter<?>> formatters) {
		for (Map.Entry<Class<? extends Annotation>, Formatter<?>> entry : formatters.entrySet()) {
			addFormatterByAnnotation(entry.getKey(), entry.getValue());
		}
	}

	/**
	 * Registers the annotation formatter factories in the set provided.
	 * JavaBean-friendly alternative to calling {@link #addFormatterByAnnotation(AnnotationFormatterFactory)}.
	 * @see #add(AnnotationFormatterFactory)
	 */
	public void setAnnotationFormatterFactories(Set<AnnotationFormatterFactory<?, ?>> factories) {
		for (AnnotationFormatterFactory<?, ?> factory : factories) {
			addFormatterByAnnotation(factory);
		}
	}

	/**
	 * Specify the type conversion service that will be used to coerce objects to the
	 * types required for formatting. Defaults to a {@link DefaultConversionService}.
	 * @see #addFormatterByType(Class, Formatter)
	 */
	public void setConversionService(ConversionService conversionService) {
		Assert.notNull(conversionService, "ConversionService must not be null");
		this.conversionService = conversionService;
	}

	/**
	 * Return the type conversion service which this FormatterRegistry delegates to.
	 */
	public ConversionService getConversionService() {
		return this.conversionService;
	}

	/**
	 * Take the context's default ConversionService if none specified locally.
	 */
	public void setApplicationContext(ApplicationContext context) {
		if (this.conversionService == null
				&& context.containsBean(ConfigurableApplicationContext.CONVERSION_SERVICE_BEAN_NAME)) {
			this.conversionService = context.getBean(ConfigurableApplicationContext.CONVERSION_SERVICE_BEAN_NAME,
					ConversionService.class);
		}
		this.applicationContext = context;
	}

	// cloning support

	/**
	 * Specify whether this FormatterRegistry is shared, in which case newly
	 * registered Formatters will be visible to other callers as well.
	 * <p>A new GenericFormatterRegistry is considered as shared by default,
	 * whereas a cloned GenericFormatterRegistry will be non-shared by default.
	 * @see #clone()
	 */
	public void setShared(boolean shared) {
		this.shared = shared;
	}

	/**
	 * Return whether this FormatterRegistry is shared, in which case newly
	 * registered Formatters will be visible to other callers as well.
	 */
	public boolean isShared() {
		return this.shared;
	}

	/**
	 * Create an independent clone of this FormatterRegistry.
	 * @see #setShared
	 */
	@Override
	public GenericFormatterRegistry clone() {
		GenericFormatterRegistry clone = new GenericFormatterRegistry();
		clone.typeFormatters.putAll(this.typeFormatters);
		clone.annotationFormatters.putAll(this.annotationFormatters);
		clone.conversionService = this.conversionService;
		clone.applicationContext = applicationContext;
		clone.shared = false;
		return clone;
	}

	// implementing FormatterRegistry

	public void addFormatterByType(Class<?> type, Formatter<?> formatter) {
		Class<?> formattedObjectType = GenericTypeResolver.resolveTypeArgument(formatter.getClass(), Formatter.class);
		if (formattedObjectType != null && !type.isAssignableFrom(formattedObjectType)) {
			if (this.conversionService == null) {
				throw new IllegalStateException("Unable to index Formatter " + formatter + " under type ["
						+ type.getName() + "]; unable to convert from [" + formattedObjectType.getName()
						+ "] parsed by Formatter because this.conversionService is null");
			}
			if (!this.conversionService.canConvert(formattedObjectType, type)) {
				throw new IllegalArgumentException("Unable to index Formatter " + formatter + " under type ["
						+ type.getName() + "]; not able to convert from [" + formattedObjectType.getName()
						+ "] to parse");
			}
			if (!this.conversionService.canConvert(type, formattedObjectType)) {
				throw new IllegalArgumentException("Unable to index Formatter " + formatter + " under type ["
						+ type.getName() + "]; not able to convert to [" + formattedObjectType.getName()
						+ "] to format");
			}
		}
		this.typeFormatters.put(type, new FormatterHolder(formattedObjectType, formatter));
	}

	public void addFormatterByType(Formatter<?> formatter) {
		Class<?> formattedObjectType = GenericTypeResolver.resolveTypeArgument(formatter.getClass(), Formatter.class);
		if (formattedObjectType == null) {
			throw new IllegalArgumentException("Unable to register Formatter " + formatter
					+ "; cannot determine parameterized object type <T>");
		}
		this.typeFormatters.put(formattedObjectType, new FormatterHolder(formattedObjectType, formatter));
	}

	public void addFormatterByAnnotation(Class<? extends Annotation> annotationType, Formatter<?> formatter) {
		Class<?> formattedObjectType = GenericTypeResolver.resolveTypeArgument(formatter.getClass(), Formatter.class);
		SimpleAnnotationFormatterFactory factory = new SimpleAnnotationFormatterFactory(formatter);
		this.annotationFormatters.put(annotationType,
				new AnnotationFormatterFactoryHolder(formattedObjectType, factory));
	}

	public void addFormatterByAnnotation(AnnotationFormatterFactory<?, ?> factory) {
		Class[] typeArgs = GenericTypeResolver.resolveTypeArguments(factory.getClass(),
				AnnotationFormatterFactory.class);
		if (typeArgs == null || typeArgs.length != 2) {
			throw new IllegalArgumentException(
					"Unable to extract parameterized type arguments from AnnotationFormatterFactory ["
							+ factory.getClass().getName()
							+ "]; does the factory parameterize the <A> and <T> generic types?");
		}
		this.annotationFormatters.put(typeArgs[0], new AnnotationFormatterFactoryHolder(typeArgs[1], factory));
	}

	public Formatter<Object> getFormatter(TypeDescriptor type) {
		Assert.notNull(type, "TypeDescriptor is required");
		FormatterHolder holder = findFormatterHolderForAnnotatedProperty(type.getAnnotations());
		if (holder == null) {
			holder = findFormatterHolderForType(type.getType());
		}
		if (holder == null) {
			holder = getDefaultFormatterHolder(type);
		}
		if (holder == null) {
			return null;
		}
		Class formattedObjectType = holder.getFormattedObjectType();
		if (formattedObjectType != null && !type.getType().isAssignableFrom(formattedObjectType)) {
			if (this.conversionService != null) {
				return new ConvertingFormatter(type, holder);
			} else {
				return null;
			}
		} else {
			return holder.getFormatter();
		}
	}

	// internal helpers

	private FormatterHolder findFormatterHolderForAnnotatedProperty(Annotation[] annotations) {
		for (Annotation annotation : annotations) {
			FormatterHolder holder = findFormatterHolderForAnnotation(annotation);
			if (holder != null) {
				return holder;
			}
		}
		return null;
	}

	private FormatterHolder findFormatterHolderForAnnotation(Annotation annotation) {
		Class<? extends Annotation> annotationType = annotation.annotationType();
		AnnotationFormatterFactoryHolder factory = this.annotationFormatters.get(annotationType);
		if (factory != null) {
			return factory.getFormatterHolder(annotation);
		} else {
			Formatted formattedAnnotation = annotationType.getAnnotation(Formatted.class);
			if (formattedAnnotation != null) {
				// annotation has @Formatted meta-annotation
				Formatter formatter = createFormatter(formattedAnnotation.value());
				addFormatterByAnnotation(annotationType, formatter);
				return findFormatterHolderForAnnotation(annotation);
			} else {
				return null;
			}
		}
	}

	private FormatterHolder findFormatterHolderForType(Class type) {
		LinkedList<Class> classQueue = new LinkedList<Class>();
		classQueue.addFirst(type);
		while (!classQueue.isEmpty()) {
			Class currentClass = classQueue.removeLast();
			FormatterHolder holder = this.typeFormatters.get(currentClass);
			if (holder != null) {
				return holder;
			}
			if (currentClass.getSuperclass() != null) {
				classQueue.addFirst(currentClass.getSuperclass());
			}
			Class[] interfaces = currentClass.getInterfaces();
			for (Class ifc : interfaces) {
				classQueue.addFirst(ifc);
			}
		}
		return null;
	}

	private FormatterHolder getDefaultFormatterHolder(TypeDescriptor typeDescriptor) {
		Class type = typeDescriptor.getType();
		Formatted formatted = AnnotationUtils.findAnnotation(type, Formatted.class);
		if (formatted != null) {
			Formatter formatter = createFormatter(formatted.value());
			addFormatterByType(type, formatter);
			return findFormatterHolderForType(type);
		} else {
			Method valueOfMethod = getValueOfMethod(type);
			if (valueOfMethod != null) {
				Formatter formatter = createFormatter(valueOfMethod);
				addFormatterByType(type, formatter);
				return findFormatterHolderForType(type);
			} else {
				return null;
			}
		}
	}

	private Formatter createFormatter(Class<? extends Formatter> formatterClass) {
		return (this.applicationContext != null ? this.applicationContext.getAutowireCapableBeanFactory().createBean(
				formatterClass) : BeanUtils.instantiate(formatterClass));
	}

	private Method getValueOfMethod(Class type) {
		Method[] methods = type.getDeclaredMethods();
		for (int i = 0; i < methods.length; i++) {
			Method method = methods[i];
			if ("valueOf".equals(method.getName()) && acceptsSingleStringParameterType(method)
					&& Modifier.isPublic(method.getModifiers()) && Modifier.isStatic(method.getModifiers())) {
				return method;
			}
		}
		return null;
	}

	private boolean acceptsSingleStringParameterType(Method method) {
		Class[] paramTypes = method.getParameterTypes();
		if (paramTypes == null) {
			return false;
		} else {
			return paramTypes.length == 1 && paramTypes[0] == String.class;
		}
	}

	private Formatter createFormatter(Method valueOfMethod) {
		return new ValueOfMethodFormatter(valueOfMethod);
	}

	private abstract static class AbstractFormatterHolder {

		private Class formattedObjectType;

		public AbstractFormatterHolder(Class formattedObjectType) {
			this.formattedObjectType = formattedObjectType;
		}

		public Class<?> getFormattedObjectType() {
			return formattedObjectType;
		}

	}

	private static class FormatterHolder extends AbstractFormatterHolder {

		private Formatter formatter;

		public FormatterHolder(Class formattedObjectType, Formatter formatter) {
			super(formattedObjectType);
			this.formatter = formatter;
		}

		public Formatter getFormatter() {
			return this.formatter;
		}

	}

	private static class AnnotationFormatterFactoryHolder extends AbstractFormatterHolder {

		private AnnotationFormatterFactory factory;

		public AnnotationFormatterFactoryHolder(Class formattedObjectType, AnnotationFormatterFactory factory) {
			super(formattedObjectType);
			this.factory = factory;
		}

		public FormatterHolder getFormatterHolder(Annotation annotation) {
			return new FormatterHolder(getFormattedObjectType(), this.factory.getFormatter(annotation));
		}

	}

	private static class SimpleAnnotationFormatterFactory implements AnnotationFormatterFactory {

		private final Formatter instance;

		public SimpleAnnotationFormatterFactory(Formatter instance) {
			this.instance = instance;
		}

		public Formatter getFormatter(Annotation annotation) {
			return this.instance;
		}
	}

	private static class ValueOfMethodFormatter implements Formatter {

		private Method valueOfMethod;

		public ValueOfMethodFormatter(Method valueOfMethod) {
			this.valueOfMethod = valueOfMethod;
		}

		public String format(Object object, Locale locale) {
			if (object == null) {
				return "";
			} else {
				return object.toString();
			}
		}

		public Object parse(String formatted, Locale locale) throws ParseException {
			return ReflectionUtils.invokeMethod(valueOfMethod, null, formatted);
		}

	}

	private class ConvertingFormatter implements Formatter {

		private final TypeDescriptor type;

		private final FormatterHolder formatterHolder;

		public ConvertingFormatter(TypeDescriptor type, FormatterHolder formatterHolder) {
			this.type = type;
			this.formatterHolder = formatterHolder;
		}

		public String format(Object object, Locale locale) {
			object = GenericFormatterRegistry.this.conversionService.convert(object, this.formatterHolder
					.getFormattedObjectType());
			return this.formatterHolder.getFormatter().format(object, locale);
		}

		public Object parse(String formatted, Locale locale) throws ParseException {
			Object parsed = this.formatterHolder.getFormatter().parse(formatted, locale);
			parsed = GenericFormatterRegistry.this.conversionService.convert(parsed, TypeDescriptor
					.valueOf(this.formatterHolder.getFormattedObjectType()), this.type);
			return parsed;
		}
	}

}
