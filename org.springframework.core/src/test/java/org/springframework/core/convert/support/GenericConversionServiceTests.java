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

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.fail;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Test;
import org.springframework.core.convert.ConversionFailedException;
import org.springframework.core.convert.ConverterNotFoundException;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.Converter;

/**
 * @author Keith Donald
 */
public class GenericConversionServiceTests {

	private GenericConversionService conversionService = new GenericConversionService();

	@Test
	public void executeConversion() {
		conversionService.addConverterFactory(new StringToNumberConverterFactory());
		assertEquals(new Integer(3), conversionService.convert("3", Integer.class));
	}

	@Test
	public void executeConversionNullSource() {
		assertEquals(null, conversionService.convert(null, Integer.class));
	}

	@Test
	public void executeCompatibleSource() {
		assertEquals(Boolean.FALSE, conversionService.convert(false, boolean.class));
	}

	@Test
	public void converterNotFound() {
		try {
			conversionService.convert("3", Integer.class);
			fail("Should have thrown an exception");
		} catch (ConverterNotFoundException e) {
		}
	}

	@Test
	public void addConverterNoSourceTargetClassInfoAvailable() {
		try {
			conversionService.addConverter(new Converter() {
				public Object convert(Object source) {
					return source;
				}
			});
			fail("Should have failed");
		} catch (IllegalArgumentException e) {

		}
	}

	@Test
	public void convertNull() {
		assertNull(conversionService.convert(null, Integer.class));
	}

	public void convertNullTargetClass() {
		assertNull(conversionService.convert("3", (Class<?>) null));
	}

	@Test
	public void convertNullTypeDescriptor() {
		assertNull(conversionService.convert("3", TypeDescriptor.valueOf(String.class), TypeDescriptor.NULL));
	}

	@Test
	public void convertWrongTypeArgument() {
		conversionService.addConverterFactory(new StringToNumberConverterFactory());
		try {
			conversionService.convert("BOGUS", Integer.class);
			fail("Should have failed");
		} catch (ConversionFailedException e) {

		}
	}

	@Test
	public void convertSuperSourceType() {
		conversionService.addConverter(new Converter<CharSequence, Integer>() {
			public Integer convert(CharSequence source) {
				return Integer.valueOf(source.toString());
			}
		});
		Integer result = conversionService.convert("3", Integer.class);
		assertEquals(new Integer(3), result);
	}

	@Test
	public void convertObjectToPrimitive() {
		assertFalse(conversionService.canConvert(String.class, boolean.class));
		conversionService.addConverter(new StringToBooleanConverter());
		assertTrue(conversionService.canConvert(String.class, boolean.class));
		Boolean b = conversionService.convert("true", boolean.class);
		assertEquals(Boolean.TRUE, b);
		assertTrue(conversionService.canConvert(TypeDescriptor.valueOf(String.class), TypeDescriptor
				.valueOf(boolean.class)));
		b = (Boolean) conversionService.convert("true", TypeDescriptor.valueOf(String.class), TypeDescriptor
				.valueOf(boolean.class));
		assertEquals(Boolean.TRUE, b);
	}

	@Test
	public void convertObjectToPrimitiveViaConverterFactory() {
		conversionService.addConverterFactory(new StringToNumberConverterFactory());
		Integer three = conversionService.convert("3", int.class);
		assertEquals(3, three.intValue());
	}

	@Test
	public void convertArrayToArray() {
		conversionService.addGenericConverter(Object[].class, Object[].class, new ArrayToArrayConverter(
				conversionService));
		conversionService.addConverterFactory(new StringToNumberConverterFactory());
		Integer[] result = conversionService.convert(new String[] { "1", "2", "3" }, Integer[].class);
		assertEquals(new Integer(1), result[0]);
		assertEquals(new Integer(2), result[1]);
		assertEquals(new Integer(3), result[2]);
	}

	@Test
	public void convertArrayToPrimitiveArray() {
		conversionService.addGenericConverter(Object[].class, Object[].class, new ArrayToArrayConverter(
				conversionService));
		conversionService.addConverterFactory(new StringToNumberConverterFactory());
		int[] result = conversionService.convert(new String[] { "1", "2", "3" }, int[].class);
		assertEquals(1, result[0]);
		assertEquals(2, result[1]);
		assertEquals(3, result[2]);
	}

	@Test
	public void convertArrayToArrayAssignable() {
		int[] result = conversionService.convert(new int[] { 1, 2, 3 }, int[].class);
		assertEquals(1, result[0]);
		assertEquals(2, result[1]);
		assertEquals(3, result[2]);
	}

	@Test
	public void convertArrayToListInterface() {
		conversionService.addGenericConverter(Object[].class, Collection.class, new ArrayToCollectionConverter(
				conversionService));
		List<?> result = conversionService.convert(new String[] { "1", "2", "3" }, List.class);
		assertEquals("1", result.get(0));
		assertEquals("2", result.get(1));
		assertEquals("3", result.get(2));
	}

	public List<Integer> genericList = new ArrayList<Integer>();

	@Test
	public void convertArrayToListGenericTypeConversion() throws Exception {
		conversionService.addGenericConverter(Object[].class, Collection.class, new ArrayToCollectionConverter(
				conversionService));
		conversionService.addConverterFactory(new StringToNumberConverterFactory());
		List<Integer> result = (List<Integer>) conversionService.convert(new String[] { "1", "2", "3" }, TypeDescriptor
				.valueOf(String[].class), new TypeDescriptor(getClass().getDeclaredField("genericList")));
		assertEquals(new Integer("1"), result.get(0));
		assertEquals(new Integer("2"), result.get(1));
		assertEquals(new Integer("3"), result.get(2));
	}

	@Test
	public void convertArrayToListImpl() {
		conversionService.addGenericConverter(Object[].class, Collection.class, new ArrayToCollectionConverter(
				conversionService));
		LinkedList<?> result = conversionService.convert(new String[] { "1", "2", "3" }, LinkedList.class);
		assertEquals("1", result.get(0));
		assertEquals("2", result.get(1));
		assertEquals("3", result.get(2));
	}

	@Test(expected = ConversionFailedException.class)
	public void convertArrayToAbstractList() {
		conversionService.addGenericConverter(Object[].class, Collection.class, new ArrayToCollectionConverter(
				conversionService));
		conversionService.convert(new String[] { "1", "2", "3" }, AbstractList.class);
	}

	@Test
	public void convertArrayToMap() {
		conversionService.addGenericConverter(Object[].class, Map.class, new ArrayToMapConverter(conversionService));
		Map result = conversionService.convert(new String[] { "foo=bar", "bar=baz", "baz=boop" }, Map.class);
		assertEquals("bar", result.get("foo"));
		assertEquals("baz", result.get("bar"));
		assertEquals("boop", result.get("baz"));
	}

	@Test
	public void convertArrayToMapWithElementConversion() throws Exception {
		conversionService.addGenericConverter(Object[].class, Map.class, new ArrayToMapConverter(conversionService));
		conversionService.addConverterFactory(new StringToNumberConverterFactory());
		conversionService.addConverterFactory(new StringToEnumConverterFactory());
		Map result = (Map) conversionService.convert(new String[] { "1=BAR", "2=BAZ" }, TypeDescriptor
				.valueOf(String[].class), new TypeDescriptor(getClass().getField("genericMap")));
		assertEquals(FooEnum.BAR, result.get(1));
		assertEquals(FooEnum.BAZ, result.get(2));
	}

	@Test
	public void convertArrayToString() {
		conversionService.addGenericConverter(Object[].class, Object.class, new ArrayToObjectConverter(
				conversionService));
		String result = conversionService.convert(new String[] { "1", "2", "3" }, String.class);
		assertEquals("1,2,3", result);
	}

	@Test
	public void convertArrayToStringWithElementConversion() {
		conversionService.addGenericConverter(Object[].class, Object.class, new ArrayToObjectConverter(
				conversionService));
		conversionService.addConverter(new ObjectToStringConverter());
		String result = conversionService.convert(new Integer[] { 1, 2, 3 }, String.class);
		assertEquals("1,2,3", result);
	}

	@Test
	public void convertEmptyArrayToString() {
		conversionService.addGenericConverter(Object[].class, Object.class, new ArrayToObjectConverter(
				conversionService));
		String result = conversionService.convert(new String[0], String.class);
		assertEquals("", result);
	}

	@Test
	public void convertArrayToObject() {
		conversionService.addGenericConverter(Object[].class, Object.class, new ArrayToObjectConverter(
				conversionService));
		Object[] array = new Object[] { 3L };
		Object result = conversionService.convert(array, Object.class);
		assertEquals(3L, result);
	}

	@Test
	public void convertArrayToObjectWithElementConversion() {
		conversionService.addGenericConverter(Object[].class, Object.class, new ArrayToObjectConverter(
				conversionService));
		conversionService.addConverterFactory(new StringToNumberConverterFactory());
		String[] array = new String[] { "3" };
		Integer result = conversionService.convert(array, Integer.class);
		assertEquals(new Integer(3), result);
	}

	@Test
	public void convertCollectionToArray() {
		conversionService.addGenericConverter(Collection.class, Object[].class, new CollectionToArrayConverter(
				conversionService));
		List<String> list = new ArrayList<String>();
		list.add("1");
		list.add("2");
		list.add("3");
		String[] result = conversionService.convert(list, String[].class);
		assertEquals("1", result[0]);
		assertEquals("2", result[1]);
		assertEquals("3", result[2]);
	}

	@Test
	public void convertCollectionToArrayWithElementConversion() {
		conversionService.addGenericConverter(Collection.class, Object[].class, new CollectionToArrayConverter(
				conversionService));
		conversionService.addConverterFactory(new StringToNumberConverterFactory());
		List<String> list = new ArrayList<String>();
		list.add("1");
		list.add("2");
		list.add("3");
		Integer[] result = conversionService.convert(list, Integer[].class);
		assertEquals(new Integer(1), result[0]);
		assertEquals(new Integer(2), result[1]);
		assertEquals(new Integer(3), result[2]);
	}

	@Test
	public void convertCollectionToCollection() throws Exception {
		conversionService.addGenericConverter(Collection.class, Collection.class, new CollectionToCollectionConverter(
				conversionService));
		conversionService.addConverterFactory(new StringToNumberConverterFactory());
		Set<String> foo = new LinkedHashSet<String>();
		foo.add("1");
		foo.add("2");
		foo.add("3");
		List<Integer> bar = (List<Integer>) conversionService.convert(foo, TypeDescriptor.valueOf(LinkedHashSet.class),
				new TypeDescriptor(getClass().getField("genericList")));
		assertEquals(new Integer(1), bar.get(0));
		assertEquals(new Integer(2), bar.get(1));
		assertEquals(new Integer(3), bar.get(2));
	}

	@Test
	public void convertCollectionToCollectionNull() throws Exception {
		conversionService.addGenericConverter(Collection.class, Collection.class, new CollectionToCollectionConverter(
				conversionService));
		List<Integer> bar = (List<Integer>) conversionService.convert(null,
				TypeDescriptor.valueOf(LinkedHashSet.class), new TypeDescriptor(getClass().getField("genericList")));
		assertNull(bar);
	}

	@Test
	public void convertCollectionToCollectionNotGeneric() throws Exception {
		conversionService.addGenericConverter(Collection.class, Collection.class, new CollectionToCollectionConverter(
				conversionService));
		conversionService.addConverterFactory(new StringToNumberConverterFactory());
		Set<String> foo = new LinkedHashSet<String>();
		foo.add("1");
		foo.add("2");
		foo.add("3");
		List bar = (List) conversionService.convert(foo, TypeDescriptor.valueOf(LinkedHashSet.class), TypeDescriptor
				.valueOf(List.class));
		assertEquals("1", bar.get(0));
		assertEquals("2", bar.get(1));
		assertEquals("3", bar.get(2));
	}

	@Test
	public void convertCollectionToCollectionSpecialCaseSourceImpl() throws Exception {
		conversionService.addGenericConverter(Collection.class, Collection.class, new CollectionToCollectionConverter(
				conversionService));
		conversionService.addConverterFactory(new StringToNumberConverterFactory());
		Map map = new LinkedHashMap();
		map.put("1", "1");
		map.put("2", "2");
		map.put("3", "3");
		Collection values = map.values();
		List<Integer> bar = (List<Integer>) conversionService.convert(values,
				TypeDescriptor.valueOf(values.getClass()), new TypeDescriptor(getClass().getField("genericList")));
		assertEquals(3, bar.size());
		assertEquals(new Integer(1), bar.get(0));
		assertEquals(new Integer(2), bar.get(1));
		assertEquals(new Integer(3), bar.get(2));
	}

	@Test
	public void convertCollectionToMap() {
		conversionService.addGenericConverter(Collection.class, Map.class, new CollectionToMapConverter(
				conversionService));
		List<String> list = new ArrayList<String>();
		list.add("foo=bar");
		list.add("bar=baz");
		list.add("baz=boop");
		Map result = conversionService.convert(list, Map.class);
		assertEquals("bar", result.get("foo"));
		assertEquals("baz", result.get("bar"));
		assertEquals("boop", result.get("baz"));
	}

	@Test
	public void convertCollectionToMapWithElementConversion() throws Exception {
		conversionService.addGenericConverter(Collection.class, Map.class, new CollectionToMapConverter(
				conversionService));
		conversionService.addConverterFactory(new StringToNumberConverterFactory());
		conversionService.addConverterFactory(new StringToEnumConverterFactory());
		List<String> list = new ArrayList<String>();
		list.add("1=BAR");
		list.add("2=BAZ");
		Map result = (Map) conversionService.convert(list, TypeDescriptor.valueOf(List.class), new TypeDescriptor(
				getClass().getField("genericMap")));
		assertEquals(FooEnum.BAR, result.get(1));
		assertEquals(FooEnum.BAZ, result.get(2));
	}

	@Test
	public void convertCollectionToString() {
		conversionService.addGenericConverter(Collection.class, Object.class, new CollectionToObjectConverter(
				conversionService));
		List<String> list = Arrays.asList(new String[] { "foo", "bar" });
		String result = conversionService.convert(list, String.class);
		assertEquals("foo,bar", result);
	}

	@Test
	public void convertCollectionToStringWithElementConversion() throws Exception {
		conversionService.addGenericConverter(Collection.class, Object.class, new CollectionToObjectConverter(
				conversionService));
		conversionService.addConverter(new ObjectToStringConverter());
		List<Integer> list = Arrays.asList(new Integer[] { 3, 5 });
		String result = (String) conversionService.convert(list,
				new TypeDescriptor(getClass().getField("genericList")), TypeDescriptor.valueOf(String.class));
		assertEquals("3,5", result);
	}

	@Test
	public void convertCollectionToObject() {
		conversionService.addGenericConverter(Collection.class, Object.class, new CollectionToObjectConverter(
				conversionService));
		List<Long> list = Collections.singletonList(3L);
		Long result = conversionService.convert(list, Long.class);
		assertEquals(new Long(3), result);
	}

	@Test
	public void convertCollectionToObjectWithElementConversion() {
		conversionService.addGenericConverter(Collection.class, Object.class, new CollectionToObjectConverter(
				conversionService));
		conversionService.addConverterFactory(new StringToNumberConverterFactory());
		List<String> list = Collections.singletonList("3");
		Integer result = conversionService.convert(list, Integer.class);
		assertEquals(new Integer(3), result);
	}

	public Map<Integer, FooEnum> genericMap = new HashMap<Integer, FooEnum>();

	@Test
	public void convertMapToMap() throws Exception {
		conversionService.addGenericConverter(Map.class, Map.class, new MapToMapConverter(conversionService));
		conversionService.addConverterFactory(new StringToNumberConverterFactory());
		conversionService.addConverterFactory(new StringToEnumConverterFactory());
		Map<String, String> foo = new HashMap<String, String>();
		foo.put("1", "BAR");
		foo.put("2", "BAZ");
		Map<String, FooEnum> map = (Map<String, FooEnum>) conversionService.convert(foo, TypeDescriptor
				.valueOf(Map.class), new TypeDescriptor(getClass().getField("genericMap")));
		assertEquals(FooEnum.BAR, map.get(1));
		assertEquals(FooEnum.BAZ, map.get(2));
	}

	@Test
	public void convertMapToStringArray() throws Exception {
		conversionService.addGenericConverter(Map.class, Object[].class, new MapToArrayConverter(conversionService));
		Map<String, String> foo = new LinkedHashMap<String, String>();
		foo.put("1", "BAR");
		foo.put("2", "BAZ");
		String[] result = conversionService.convert(foo, String[].class);
		assertEquals("1=BAR", result[0]);
		assertEquals("2=BAZ", result[1]);
	}

	@Test
	public void convertMapToStringArrayWithElementConversion() throws Exception {
		conversionService.addGenericConverter(Map.class, Object[].class, new MapToArrayConverter(conversionService));
		conversionService.addConverter(new ObjectToStringConverter());
		Map<Integer, FooEnum> foo = new LinkedHashMap<Integer, FooEnum>();
		foo.put(1, FooEnum.BAR);
		foo.put(2, FooEnum.BAZ);
		String[] result = (String[]) conversionService.convert(foo, new TypeDescriptor(getClass()
				.getField("genericMap")), TypeDescriptor.valueOf(String[].class));
		assertEquals("1=BAR", result[0]);
		assertEquals("2=BAZ", result[1]);
	}

	@Test
	public void convertMapToString() {
		conversionService.addGenericConverter(Map.class, Object.class, new MapToObjectConverter(conversionService));
		Map<String, String> foo = new LinkedHashMap<String, String>();
		foo.put("1", "BAR");
		foo.put("2", "BAZ");
		String result = conversionService.convert(foo, String.class);
		System.out.println(result);
		assertTrue(result.contains("1=BAR"));
		assertTrue(result.contains("2=BAZ"));
	}

	@Test
	public void convertMapToStringWithConversion() throws Exception {
		conversionService.addGenericConverter(Map.class, Object.class, new MapToObjectConverter(conversionService));
		Map<Integer, FooEnum> foo = new LinkedHashMap<Integer, FooEnum>();
		foo.put(1, FooEnum.BAR);
		foo.put(2, FooEnum.BAZ);
		conversionService.addConverter(new ObjectToStringConverter());
		String result = (String) conversionService.convert(foo, new TypeDescriptor(getClass().getField("genericMap")),
				TypeDescriptor.valueOf(String.class));
		assertTrue(result.contains("1=BAR"));
		assertTrue(result.contains("2=BAZ"));
	}

	@Test
	public void convertMapToObject() {
		conversionService.addGenericConverter(Map.class, Object.class, new MapToObjectConverter(conversionService));
		Map<Long, Long> foo = new LinkedHashMap<Long, Long>();
		foo.put(1L, 1L);
		foo.put(2L, 2L);
		Long result = conversionService.convert(foo, Long.class);
		assertEquals(new Long(1), result);
	}

	public Map<Long, Long> genericMap2 = new HashMap<Long, Long>();

	@Test
	public void convertMapToObjectWithConversion() throws Exception {
		conversionService.addGenericConverter(Map.class, Object.class, new MapToObjectConverter(conversionService));
		conversionService.addConverterFactory(new NumberToNumberConverterFactory());
		Map<Long, Long> foo = new LinkedHashMap<Long, Long>();
		foo.put(1L, 1L);
		foo.put(2L, 2L);
		Integer result = (Integer) conversionService.convert(foo,
				new TypeDescriptor(getClass().getField("genericMap2")), TypeDescriptor.valueOf(Integer.class));
		assertEquals(new Integer(1), result);
	}

	@Test
	public void convertStringToArray() {
		conversionService.addGenericConverter(Object.class, Object[].class, new ObjectToArrayConverter(
				conversionService));
		String[] result = conversionService.convert("1,2,3", String[].class);
		assertEquals(3, result.length);
		assertEquals("1", result[0]);
		assertEquals("2", result[1]);
		assertEquals("3", result[2]);
	}

	@Test
	public void convertStringToArrayWithElementConversion() {
		conversionService.addGenericConverter(Object.class, Object[].class, new ObjectToArrayConverter(
				conversionService));
		conversionService.addConverterFactory(new StringToNumberConverterFactory());
		Integer[] result = conversionService.convert("1,2,3", Integer[].class);
		assertEquals(3, result.length);
		assertEquals(new Integer(1), result[0]);
		assertEquals(new Integer(2), result[1]);
		assertEquals(new Integer(3), result[2]);
	}

	@Test
	public void convertEmptyStringToArray() {
		conversionService.addGenericConverter(Object.class, Object[].class, new ObjectToArrayConverter(
				conversionService));
		String[] result = conversionService.convert("", String[].class);
		assertEquals(0, result.length);
	}

	@Test
	public void convertObjectToArray() {
		conversionService.addGenericConverter(Object.class, Object[].class, new ObjectToArrayConverter(
				conversionService));
		Object[] result = conversionService.convert(3L, Object[].class);
		assertEquals(1, result.length);
		assertEquals(3L, result[0]);
	}

	@Test
	public void convertObjectToArrayWithElementConversion() {
		conversionService.addGenericConverter(Object.class, Object[].class, new ObjectToArrayConverter(
				conversionService));
		conversionService.addConverterFactory(new NumberToNumberConverterFactory());
		Integer[] result = conversionService.convert(3L, Integer[].class);
		assertEquals(1, result.length);
		assertEquals(new Integer(3), result[0]);
	}

	@Test
	public void convertStringToCollection() {
		conversionService.addGenericConverter(Object.class, Collection.class, new ObjectToCollectionConverter(
				conversionService));
		List result = conversionService.convert("1,2,3", List.class);
		assertEquals(3, result.size());
		assertEquals("1", result.get(0));
		assertEquals("2", result.get(1));
		assertEquals("3", result.get(2));
	}

	@Test
	public void convertStringToCollectionWithElementConversion() throws Exception {
		conversionService.addGenericConverter(Object.class, Collection.class, new ObjectToCollectionConverter(
				conversionService));
		conversionService.addConverterFactory(new StringToNumberConverterFactory());
		List result = (List) conversionService.convert("1,2,3", TypeDescriptor.valueOf(String.class),
				new TypeDescriptor(getClass().getField("genericList")));
		assertEquals(3, result.size());
		assertEquals(new Integer(1), result.get(0));
		assertEquals(new Integer(2), result.get(1));
		assertEquals(new Integer(3), result.get(2));
	}

	@Test
	public void convertEmptyStringToCollection() {
		conversionService.addGenericConverter(Object.class, Collection.class, new ObjectToCollectionConverter(
				conversionService));
		Collection result = conversionService.convert("", Collection.class);
		assertEquals(0, result.size());
	}

	@Test
	public void convertObjectToCollection() {
		conversionService.addGenericConverter(Object.class, Collection.class, new ObjectToCollectionConverter(
				conversionService));
		List<String> result = (List<String>) conversionService.convert(3L, List.class);
		assertEquals(1, result.size());
		assertEquals(3L, result.get(0));
	}

	@Test
	public void convertObjectToCollectionWithElementConversion() throws Exception {
		conversionService.addGenericConverter(Object.class, Collection.class, new ObjectToCollectionConverter(
				conversionService));
		conversionService.addConverterFactory(new NumberToNumberConverterFactory());
		List<Integer> result = (List<Integer>) conversionService.convert(3L, TypeDescriptor.valueOf(Long.class),
				new TypeDescriptor(getClass().getField("genericList")));
		assertEquals(1, result.size());
		assertEquals(new Integer(3), result.get(0));
	}

	@Test
	public void convertStringToMap() {
		conversionService.addGenericConverter(Object.class, Map.class, new ObjectToMapConverter(conversionService));
		Map result = conversionService.convert("   foo=bar\n   bar=baz\n    baz=boop", Map.class);
		assertEquals("bar", result.get("foo"));
		assertEquals("baz", result.get("bar"));
		assertEquals("boop", result.get("baz"));
	}

	@Test
	public void convertStringToMapWithElementConversion() throws Exception {
		conversionService.addGenericConverter(Object.class, Map.class, new ObjectToMapConverter(conversionService));
		conversionService.addConverterFactory(new StringToNumberConverterFactory());
		conversionService.addConverterFactory(new StringToEnumConverterFactory());
		Map result = (Map) conversionService.convert("1=BAR\n 2=BAZ", TypeDescriptor.valueOf(String.class),
				new TypeDescriptor(getClass().getField("genericMap")));
		assertEquals(FooEnum.BAR, result.get(1));
		assertEquals(FooEnum.BAZ, result.get(2));
	}

	@Test
	public void convertObjectToMap() {
		conversionService.addGenericConverter(Object.class, Map.class, new ObjectToMapConverter(conversionService));
		Map result = conversionService.convert(new Integer(3), Map.class);
		assertEquals(new Integer(3), result.get(new Integer(3)));
	}

	@Test
	public void convertObjectToMapWithConversion() throws Exception {
		conversionService.addGenericConverter(Object.class, Map.class, new ObjectToMapConverter(conversionService));
		conversionService.addConverterFactory(new NumberToNumberConverterFactory());
		Map result = (Map) conversionService.convert(1L, TypeDescriptor.valueOf(Integer.class), new TypeDescriptor(
				getClass().getField("genericMap2")));
		assertEquals(new Long(1), result.get(1L));
	}

	@Test
	public void genericConverterDelegatingBackToConversionServiceConverterNotFound() {
		conversionService.addGenericConverter(Object.class, Object[].class, new ObjectToArrayConverter(
				conversionService));
		try {
			conversionService.convert("1", Integer[].class);
		} catch (ConversionFailedException e) {
			assertTrue(e.getCause() instanceof ConverterNotFoundException);
		}
	}

	@Test
	public void parent() {
		GenericConversionService parent = new GenericConversionService();
		conversionService.setParent(parent);
		assertFalse(conversionService.canConvert(String.class, Integer.class));
		try {
			conversionService.convert("3", Integer.class);
		} catch (ConverterNotFoundException e) {

		}
	}
	
	public static enum FooEnum {
		BAR, BAZ
	}

}
