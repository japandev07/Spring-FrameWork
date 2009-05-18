/*
 * Copyright 2009 the original author or authors.
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
package org.springframework.core.convert;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import java.util.List;

import org.junit.Test;

/**
 * @author Andy Clement
 */
public class ConversionContextTests {

	List<String> listOfString;
	int[] intArray;
	List<String>[] arrayOfListOfString;

	@Test
	public void testWrapperType() {
		ConversionContext desc = ConversionContext.valueOf(int.class);
		assertEquals(Integer.class, desc.getType());
	}
	
	@Test
	public void listDescriptors() throws Exception {
		ConversionContext typeDescriptor = new ConversionContext(ConversionContextTests.class.getDeclaredField("listOfString"));
		assertFalse(typeDescriptor.isArray());
		assertEquals(List.class,typeDescriptor.getType());
		assertEquals(String.class,typeDescriptor.getElementType());
		// TODO caught shorten these names but it is OK that they are fully qualified for now
		assertEquals("java.util.List<java.lang.String>",typeDescriptor.asString());
	}
	
	@Test
	public void arrayTypeDescriptors() throws Exception {
		ConversionContext typeDescriptor = new ConversionContext(ConversionContextTests.class.getDeclaredField("intArray"));
		assertTrue(typeDescriptor.isArray());
		assertEquals(Integer.TYPE,typeDescriptor.getElementType());
		assertEquals("int[]",typeDescriptor.asString());
	}

	@Test
	public void buildingArrayTypeDescriptors() throws Exception {
		ConversionContext typeDescriptor = new ConversionContext(new int[0].getClass());
		assertTrue(typeDescriptor.isArray());
		assertEquals(Integer.TYPE,typeDescriptor.getElementType());
	}

	@Test
	public void complexTypeDescriptors() throws Exception {
		ConversionContext typeDescriptor = new ConversionContext(ConversionContextTests.class.getDeclaredField("arrayOfListOfString"));
		assertTrue(typeDescriptor.isArray());
		assertEquals(List.class,typeDescriptor.getElementType());
		// TODO asc notice that the type of the list elements is lost: typeDescriptor.getElementType() should return a TypeDescriptor
		assertEquals("java.util.List[]",typeDescriptor.asString());
	}
	
}
