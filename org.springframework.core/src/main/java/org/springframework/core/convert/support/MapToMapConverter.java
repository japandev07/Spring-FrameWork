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

import java.util.Map;

import org.springframework.core.CollectionFactory;
import org.springframework.core.convert.TypeDescriptor;

/**
 * Converts from a source Map to a target Map type.
 *
 * @author Keith Donald
 * @since 3.0
 */
final class MapToMapConverter implements GenericConverter {

	private final GenericConversionService conversionService;

	public MapToMapConverter(GenericConversionService conversionService) {
		this.conversionService = conversionService;
	}

	@SuppressWarnings("unchecked")
	public Object convert(Object source, TypeDescriptor sourceType, TypeDescriptor targetType) {
		if (source == null) {
			return this.conversionService.convertNullSource(sourceType, targetType);
		}
		Map sourceMap = (Map) source;
		TypeDescriptor targetKeyType = targetType.getMapKeyTypeDescriptor();
		TypeDescriptor targetValueType = targetType.getMapValueTypeDescriptor();
		if (targetKeyType == TypeDescriptor.NULL && targetValueType == TypeDescriptor.NULL) {
			return compatibleMapWithoutEntryConversion(sourceMap, targetType);
		}
		TypeDescriptor[] sourceEntryTypes = getMapEntryTypes(sourceMap);
		TypeDescriptor sourceKeyType = sourceEntryTypes[0];
		TypeDescriptor sourceValueType = sourceEntryTypes[1];
		if (sourceKeyType == TypeDescriptor.NULL && sourceValueType == TypeDescriptor.NULL) {
			return compatibleMapWithoutEntryConversion(sourceMap, targetType);
		}
		boolean keysCompatible = false;
		if (sourceKeyType != TypeDescriptor.NULL && targetKeyType != TypeDescriptor.NULL
				&& sourceKeyType.isAssignableTo(targetKeyType)) {
			keysCompatible = true;
		}
		boolean valuesCompatible = false;
		if (sourceValueType != TypeDescriptor.NULL && targetValueType != TypeDescriptor.NULL
				&& sourceValueType.isAssignableTo(targetValueType)) {
			valuesCompatible = true;
		}
		if (keysCompatible && valuesCompatible) {
			return compatibleMapWithoutEntryConversion(sourceMap, targetType);
		}
		Map targetMap = CollectionFactory.createMap(targetType.getType(), sourceMap.size());
		MapEntryConverter converter = new MapEntryConverter(sourceKeyType, sourceValueType, targetKeyType,
				targetValueType, keysCompatible, valuesCompatible, conversionService);
		for (Object entry : sourceMap.entrySet()) {
			Map.Entry sourceMapEntry = (Map.Entry) entry;
			Object targetKey = converter.convertKey(sourceMapEntry.getKey());
			Object targetValue = converter.convertValue(sourceMapEntry.getValue());
			targetMap.put(targetKey, targetValue);
		}
		return targetMap;
	}

	private TypeDescriptor[] getMapEntryTypes(Map sourceMap) {
		Class keyType = null;
		Class valueType = null;
		for (Object entry : sourceMap.entrySet()) {
			Map.Entry mapEntry = (Map.Entry) entry;
			Object key = mapEntry.getKey();
			if (keyType == null && key != null) {
				keyType = key.getClass();
			}
			Object value = mapEntry.getValue();
			if (valueType == null && value != null) {
				valueType = value.getClass();
			}
			if (mapEntry.getKey() != null && mapEntry.getValue() != null) {
				break;
			}
		}
		return new TypeDescriptor[] { TypeDescriptor.valueOf(keyType), TypeDescriptor.valueOf(valueType) };
	}

	@SuppressWarnings("unchecked")
	private Map compatibleMapWithoutEntryConversion(Map source, TypeDescriptor targetType) {
		if (targetType.getType().isAssignableFrom(source.getClass())) {
			return source;
		} else {
			Map target = CollectionFactory.createMap(targetType.getType(), source.size());
			target.putAll(source);
			return target;
		}
	}

}
