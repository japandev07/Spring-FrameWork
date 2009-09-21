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

import org.springframework.core.convert.TypeDescriptor;

class CollectionToObjectGenericConverter implements GenericConverter {

	private GenericConversionService conversionService;

	public CollectionToObjectGenericConverter(GenericConversionService conversionService) {
		this.conversionService = conversionService;
	}
	
	public Object convert(Object source, TypeDescriptor sourceType, TypeDescriptor targetType) {
		throw new UnsupportedOperationException("Not yet implemented");
	}

}
