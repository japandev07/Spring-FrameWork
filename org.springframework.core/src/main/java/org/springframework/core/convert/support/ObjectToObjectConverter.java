/*
 * Copyright 2002-2010 the original author or authors.
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

package org.springframework.core.convert.support;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Set;

import org.springframework.core.convert.ConversionFailedException;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.ConditionalGenericConverter;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

/**
 * Generic Converter that attempts to convert a source Object to a target type
 * by delegating to methods on the target type.
 *
 * <p>Calls the static <code>valueOf(sourceType)</code> method on the target type
 * to perform the conversion, if such a method exists. Else calls the target type's
 * Constructor that accepts a single sourceType argument, if such a Constructor exists.
 * Else throws a ConversionFailedException.
 *
 * @author Keith Donald
 * @author Juergen Hoeller
 * @since 3.0
 */
final class ObjectToObjectConverter implements ConditionalGenericConverter {

	public Set<ConvertiblePair> getConvertibleTypes() {
		return Collections.singleton(new ConvertiblePair(Object.class, Object.class));
	}

	public boolean matches(TypeDescriptor sourceType, TypeDescriptor targetType) {
		Class<?> source = sourceType.getType();
		Class<?> target = targetType.getType();
		return !source.equals(target) && hasValueOfMethodOrConstructor(target, source);
	}

	public Object convert(Object source, TypeDescriptor sourceType, TypeDescriptor targetType) {
		Class<?> sourceClass = sourceType.getType();
		Class<?> targetClass = targetType.getType();
		Method method = getValueOfMethodOn(targetClass, sourceClass);
		try {
			if (method != null) {
				ReflectionUtils.makeAccessible(method);
				return method.invoke(null, source);
			}
			else {
				Constructor<?> constructor = getConstructor(targetClass, sourceClass);
				if (constructor != null) {
					return constructor.newInstance(source);
				}
			}
		}
		catch (InvocationTargetException ex) {
			throw new ConversionFailedException(sourceType, targetType, source, ex.getTargetException());
		}
		catch (Throwable ex) {
			throw new ConversionFailedException(sourceType, targetType, source, ex);
		}
		throw new IllegalStateException("No static valueOf(" + sourceClass.getName() +
				") method or Constructor(" + sourceClass.getName() + ") exists on " + targetClass.getName());
	}

	public static boolean hasValueOfMethodOrConstructor(Class<?> targetClass, Class<?> sourceClass) {
		return getValueOfMethodOn(targetClass, sourceClass) != null || getConstructor(targetClass, sourceClass) != null;
	}

	private static Method getValueOfMethodOn(Class<?> targetClass, Class<?> sourceClass) {
		return ClassUtils.getStaticMethod(targetClass, "valueOf", sourceClass);
	}
	
	private static Constructor<?> getConstructor(Class<?> targetClass, Class<?> sourceClass) {
		return ClassUtils.getConstructorIfAvailable(targetClass, sourceClass);
	}

}
