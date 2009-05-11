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
package org.springframework.core.convert.service;

import org.springframework.core.convert.ConversionExecutionException;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.style.ToStringCreator;

/**
 * Default conversion executor implementation for converters.
 * @author Keith Donald
 */
@SuppressWarnings("unchecked")
class StaticConversionExecutor implements ConversionExecutor {

	private final TypeDescriptor sourceType;

	private final TypeDescriptor targetType;

	private final Converter converter;

	public StaticConversionExecutor(TypeDescriptor sourceType, TypeDescriptor targetType, Converter converter) {
		this.sourceType = sourceType;
		this.targetType = targetType;
		this.converter = converter;
	}

	public Object execute(Object source) throws ConversionExecutionException {
		if (source == null) {
			return null;
		}
		if (sourceType != null && !sourceType.isInstance(source)) {
			throw new ConversionExecutionException(source, sourceType.getType(), targetType, "Source object "
					+ source + " to convert is expected to be an instance of [" + sourceType.getName() + "]");
		}
		try {
			return converter.convert(source);
		} catch (Exception e) {
			throw new ConversionExecutionException(source, sourceType.getType(), targetType, e);
		}
	}

	public boolean equals(Object o) {
		if (!(o instanceof StaticConversionExecutor)) {
			return false;
		}
		StaticConversionExecutor other = (StaticConversionExecutor) o;
		return sourceType.equals(other.sourceType) && targetType.equals(other.targetType);
	}

	public int hashCode() {
		return sourceType.hashCode() + targetType.hashCode();
	}

	public String toString() {
		return new ToStringCreator(this).append("sourceClass", sourceType).append("targetClass", targetType)
				.toString();
	}
}