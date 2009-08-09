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

package org.springframework.expression.spel.support;

import org.springframework.core.convert.ConversionException;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.ConverterNotFoundException;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.TypeConverter;
import org.springframework.expression.spel.SpelEvaluationException;
import org.springframework.expression.spel.SpelMessage;
import org.springframework.util.Assert;

/**
 * Default implementation of the {@link TypeConverter} interface,
 * delegating to a core Spring {@link ConversionService}.
 *
 * @author Juergen Hoeller
 * @author Andy Clement
 * @since 3.0
 * @see org.springframework.core.convert.ConversionService
 */
public class StandardTypeConverter implements TypeConverter {

	private final ConversionService typeConverter;


	public StandardTypeConverter() {
		this.typeConverter = new DefaultConversionService();
	}

	public StandardTypeConverter(ConversionService typeConverter) {
		Assert.notNull(typeConverter, "ConversionService must not be null");
		this.typeConverter = typeConverter;
	}


	public boolean canConvert(Class<?> sourceType, Class<?> targetType) {
		return this.typeConverter.canConvert(sourceType, targetType);
	}

	public Object convertValue(Object value, TypeDescriptor typeDescriptor) throws EvaluationException {
		try {
			return this.typeConverter.convert(value, typeDescriptor);
		}
		catch (ConverterNotFoundException cenfe) {
			throw new SpelEvaluationException(cenfe, SpelMessage.TYPE_CONVERSION_ERROR, value.getClass(), typeDescriptor.asString());
		}
		catch (ConversionException ce) {
			throw new SpelEvaluationException(ce, SpelMessage.TYPE_CONVERSION_ERROR, value.getClass(), typeDescriptor.asString());
		}
	}

}
