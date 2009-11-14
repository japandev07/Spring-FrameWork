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

package org.springframework.core.convert.support;

import static org.springframework.core.convert.support.ConversionUtils.asList;

import org.springframework.core.convert.TypeDescriptor;

/**
 * Converts from an array to a Map.
 *
 * @author Keith Donald
 * @since 3.0
 */
final class ArrayToMapConverter implements GenericConverter {

	private final CollectionToMapConverter helperConverter;

	public ArrayToMapConverter(GenericConversionService conversionService) {
		this.helperConverter = new CollectionToMapConverter(conversionService);
	}

	public Object convert(Object source, TypeDescriptor sourceType, TypeDescriptor targetType) {
		return this.helperConverter.convert(asList(source), sourceType, targetType);
	}

}
