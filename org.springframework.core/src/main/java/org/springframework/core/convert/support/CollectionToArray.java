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

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Iterator;

import org.springframework.core.convert.TypeDescriptor;

/**
 * Special converter that converts from target collection to a source array.
 *
 * @author Keith Donald
 * @author Juergen Hoeller
 * @since 3.0
 */
class CollectionToArray extends AbstractCollectionConverter {

	public CollectionToArray(TypeDescriptor sourceCollectionType, TypeDescriptor targetArrayType,
			GenericConversionService conversionService) {
		super(sourceCollectionType, targetArrayType, conversionService);
	}

	@Override
	protected Object doExecute(Object source) throws Exception {
		Collection<?> collection = (Collection<?>) source;
		Object array = Array.newInstance(getTargetElementType(), collection.size());
		int i = 0;
		ConversionExecutor elementConverter = getElementConverter(collection);
		for (Iterator<?> it = collection.iterator(); it.hasNext(); i++) {
			Array.set(array, i, elementConverter.execute(it.next()));
		}
		return array;
	}
	
	private ConversionExecutor getElementConverter(Collection<?> source) {
		ConversionExecutor elementConverter = getElementConverter();
		if (elementConverter == NoOpConversionExecutor.INSTANCE) {
			for (Object value : source) {
				if (value != null) {
					elementConverter = getConversionService()
							.getConversionExecutor(value.getClass(), TypeDescriptor.valueOf(getTargetElementType()));
					break;
				}
			}
		}
		return (elementConverter != null ? elementConverter : NoOpConversionExecutor.INSTANCE);
	}

}
