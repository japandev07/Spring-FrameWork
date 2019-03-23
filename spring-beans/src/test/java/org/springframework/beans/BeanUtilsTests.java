/*
 * Copyright 2002-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.beans;

import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.propertyeditors.CustomDateEditor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceEditor;
import org.springframework.lang.Nullable;
import org.springframework.tests.sample.beans.DerivedTestBean;
import org.springframework.tests.sample.beans.ITestBean;
import org.springframework.tests.sample.beans.TestBean;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link BeanUtils}.
 *
 * @author Juergen Hoeller
 * @author Rob Harrop
 * @author Chris Beams
 * @author Sebastien Deleuze
 * @since 19.05.2003
 */
public class BeanUtilsTests {

	@Test(expected = FatalBeanException.class)
	public void testInstantiateClassGivenInterface() {
		BeanUtils.instantiateClass(ArrayList.class);
		BeanUtils.instantiateClass(List.class);
	}

	@Test(expected = FatalBeanException.class)
	public void testInstantiateClassGivenClassWithoutDefaultConstructor() {
		BeanUtils.instantiateClass(ArrayList.class);
		BeanUtils.instantiateClass(CustomDateEditor.class);
	}

	@Test  // gh-22531
	public void testInstantiateClassWithOptionalNullableType() throws NoSuchMethodException {
		Constructor<BeanWithNullableTypes> ctor = BeanWithNullableTypes.class.getDeclaredConstructor(
				Integer.class, Boolean.class, String.class);
		BeanWithNullableTypes bean = BeanUtils.instantiateClass(ctor, null, null, "foo");
		assertNull(bean.getCounter());
		assertNull(bean.isFlag());
		assertEquals("foo", bean.getValue());
	}

	@Test  // gh-22531
	public void testInstantiateClassWithOptionalPrimitiveType() throws NoSuchMethodException {
		Constructor<BeanWithPrimitiveTypes> ctor = BeanWithPrimitiveTypes.class.getDeclaredConstructor(int.class, boolean.class, String.class);
		BeanWithPrimitiveTypes bean = BeanUtils.instantiateClass(ctor, null, null, "foo");
		assertEquals(0, bean.getCounter());
		assertEquals(false, bean.isFlag());
		assertEquals("foo", bean.getValue());
	}

	@Test(expected = BeanInstantiationException.class)  // gh-22531
	public void testInstantiateClassWithMoreArgsThanParameters() throws NoSuchMethodException {
		Constructor<BeanWithPrimitiveTypes> ctor = BeanWithPrimitiveTypes.class.getDeclaredConstructor(int.class, boolean.class, String.class);
		BeanUtils.instantiateClass(ctor, null, null, "foo", null);
	}

	@Test
	public void testGetPropertyDescriptors() throws Exception {
		PropertyDescriptor[] actual = Introspector.getBeanInfo(TestBean.class).getPropertyDescriptors();
		PropertyDescriptor[] descriptors = BeanUtils.getPropertyDescriptors(TestBean.class);
		assertNotNull("Descriptors should not be null", descriptors);
		assertEquals("Invalid number of descriptors returned", actual.length, descriptors.length);
	}

	@Test
	public void testBeanPropertyIsArray() {
		PropertyDescriptor[] descriptors = BeanUtils.getPropertyDescriptors(ContainerBean.class);
		for (PropertyDescriptor descriptor : descriptors) {
			if ("containedBeans".equals(descriptor.getName())) {
				assertTrue("Property should be an array", descriptor.getPropertyType().isArray());
				assertEquals(descriptor.getPropertyType().getComponentType(), ContainedBean.class);
			}
		}
	}

	@Test
	public void testFindEditorByConvention() {
		assertEquals(ResourceEditor.class, BeanUtils.findEditorByConvention(Resource.class).getClass());
	}

	@Test
	public void testCopyProperties() throws Exception {
		TestBean tb = new TestBean();
		tb.setName("rod");
		tb.setAge(32);
		tb.setTouchy("touchy");
		TestBean tb2 = new TestBean();
		assertTrue("Name empty", tb2.getName() == null);
		assertTrue("Age empty", tb2.getAge() == 0);
		assertTrue("Touchy empty", tb2.getTouchy() == null);
		BeanUtils.copyProperties(tb, tb2);
		assertTrue("Name copied", tb2.getName().equals(tb.getName()));
		assertTrue("Age copied", tb2.getAge() == tb.getAge());
		assertTrue("Touchy copied", tb2.getTouchy().equals(tb.getTouchy()));
	}

	@Test
	public void testCopyPropertiesWithDifferentTypes1() throws Exception {
		DerivedTestBean tb = new DerivedTestBean();
		tb.setName("rod");
		tb.setAge(32);
		tb.setTouchy("touchy");
		TestBean tb2 = new TestBean();
		assertTrue("Name empty", tb2.getName() == null);
		assertTrue("Age empty", tb2.getAge() == 0);
		assertTrue("Touchy empty", tb2.getTouchy() == null);
		BeanUtils.copyProperties(tb, tb2);
		assertTrue("Name copied", tb2.getName().equals(tb.getName()));
		assertTrue("Age copied", tb2.getAge() == tb.getAge());
		assertTrue("Touchy copied", tb2.getTouchy().equals(tb.getTouchy()));
	}

	@Test
	public void testCopyPropertiesWithDifferentTypes2() throws Exception {
		TestBean tb = new TestBean();
		tb.setName("rod");
		tb.setAge(32);
		tb.setTouchy("touchy");
		DerivedTestBean tb2 = new DerivedTestBean();
		assertTrue("Name empty", tb2.getName() == null);
		assertTrue("Age empty", tb2.getAge() == 0);
		assertTrue("Touchy empty", tb2.getTouchy() == null);
		BeanUtils.copyProperties(tb, tb2);
		assertTrue("Name copied", tb2.getName().equals(tb.getName()));
		assertTrue("Age copied", tb2.getAge() == tb.getAge());
		assertTrue("Touchy copied", tb2.getTouchy().equals(tb.getTouchy()));
	}

	@Test
	public void testCopyPropertiesWithEditable() throws Exception {
		TestBean tb = new TestBean();
		assertTrue("Name empty", tb.getName() == null);
		tb.setAge(32);
		tb.setTouchy("bla");
		TestBean tb2 = new TestBean();
		tb2.setName("rod");
		assertTrue("Age empty", tb2.getAge() == 0);
		assertTrue("Touchy empty", tb2.getTouchy() == null);

		// "touchy" should not be copied: it's not defined in ITestBean
		BeanUtils.copyProperties(tb, tb2, ITestBean.class);
		assertTrue("Name copied", tb2.getName() == null);
		assertTrue("Age copied", tb2.getAge() == 32);
		assertTrue("Touchy still empty", tb2.getTouchy() == null);
	}

	@Test
	public void testCopyPropertiesWithIgnore() throws Exception {
		TestBean tb = new TestBean();
		assertTrue("Name empty", tb.getName() == null);
		tb.setAge(32);
		tb.setTouchy("bla");
		TestBean tb2 = new TestBean();
		tb2.setName("rod");
		assertTrue("Age empty", tb2.getAge() == 0);
		assertTrue("Touchy empty", tb2.getTouchy() == null);

		// "spouse", "touchy", "age" should not be copied
		BeanUtils.copyProperties(tb, tb2, "spouse", "touchy", "age");
		assertTrue("Name copied", tb2.getName() == null);
		assertTrue("Age still empty", tb2.getAge() == 0);
		assertTrue("Touchy still empty", tb2.getTouchy() == null);
	}

	@Test
	public void testCopyPropertiesWithIgnoredNonExistingProperty() {
		NameAndSpecialProperty source = new NameAndSpecialProperty();
		source.setName("name");
		TestBean target = new TestBean();
		BeanUtils.copyProperties(source, target, "specialProperty");
		assertEquals(target.getName(), "name");
	}

	@Test
	public void testCopyPropertiesWithInvalidProperty() {
		InvalidProperty source = new InvalidProperty();
		source.setName("name");
		source.setFlag1(true);
		source.setFlag2(true);
		InvalidProperty target = new InvalidProperty();
		BeanUtils.copyProperties(source, target);
		assertEquals("name", target.getName());
		assertTrue(target.getFlag1());
		assertTrue(target.getFlag2());
	}

	@Test
	public void testResolveSimpleSignature() throws Exception {
		Method desiredMethod = MethodSignatureBean.class.getMethod("doSomething");
		assertSignatureEquals(desiredMethod, "doSomething");
		assertSignatureEquals(desiredMethod, "doSomething()");
	}

	@Test(expected = IllegalArgumentException.class)
	public void testResolveInvalidSignatureEndParen() {
		BeanUtils.resolveSignature("doSomething(", MethodSignatureBean.class);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testResolveInvalidSignatureStartParen() {
		BeanUtils.resolveSignature("doSomething)", MethodSignatureBean.class);
	}

	@Test
	public void testResolveWithAndWithoutArgList() throws Exception {
		Method desiredMethod = MethodSignatureBean.class.getMethod("doSomethingElse", String.class, int.class);
		assertSignatureEquals(desiredMethod, "doSomethingElse");
		assertNull(BeanUtils.resolveSignature("doSomethingElse()", MethodSignatureBean.class));
	}

	@Test
	public void testResolveTypedSignature() throws Exception {
		Method desiredMethod = MethodSignatureBean.class.getMethod("doSomethingElse", String.class, int.class);
		assertSignatureEquals(desiredMethod, "doSomethingElse(java.lang.String, int)");
	}

	@Test
	public void testResolveOverloadedSignature() throws Exception {
		// test resolve with no args
		Method desiredMethod = MethodSignatureBean.class.getMethod("overloaded");
		assertSignatureEquals(desiredMethod, "overloaded()");

		// resolve with single arg
		desiredMethod = MethodSignatureBean.class.getMethod("overloaded", String.class);
		assertSignatureEquals(desiredMethod, "overloaded(java.lang.String)");

		// resolve with two args
		desiredMethod = MethodSignatureBean.class.getMethod("overloaded", String.class, BeanFactory.class);
		assertSignatureEquals(desiredMethod, "overloaded(java.lang.String, org.springframework.beans.factory.BeanFactory)");
	}

	@Test
	public void testResolveSignatureWithArray() throws Exception {
		Method desiredMethod = MethodSignatureBean.class.getMethod("doSomethingWithAnArray", String[].class);
		assertSignatureEquals(desiredMethod, "doSomethingWithAnArray(java.lang.String[])");

		desiredMethod = MethodSignatureBean.class.getMethod("doSomethingWithAMultiDimensionalArray", String[][].class);
		assertSignatureEquals(desiredMethod, "doSomethingWithAMultiDimensionalArray(java.lang.String[][])");
	}

	@Test
	public void testSPR6063() {
		PropertyDescriptor[] descrs = BeanUtils.getPropertyDescriptors(Bean.class);

		PropertyDescriptor keyDescr = BeanUtils.getPropertyDescriptor(Bean.class, "value");
		assertEquals(String.class, keyDescr.getPropertyType());
		for (PropertyDescriptor propertyDescriptor : descrs) {
			if (propertyDescriptor.getName().equals(keyDescr.getName())) {
				assertEquals(propertyDescriptor.getName() + " has unexpected type",
						keyDescr.getPropertyType(), propertyDescriptor.getPropertyType());
			}
		}
	}

	private void assertSignatureEquals(Method desiredMethod, String signature) {
		assertEquals(desiredMethod, BeanUtils.resolveSignature(signature, MethodSignatureBean.class));
	}


	@SuppressWarnings("unused")
	private static class NameAndSpecialProperty {

		private String name;

		private int specialProperty;

		public void setName(String name) {
			this.name = name;
		}

		public String getName() {
			return this.name;
		}

		public void setSpecialProperty(int specialProperty) {
			this.specialProperty = specialProperty;
		}

		public int getSpecialProperty() {
			return specialProperty;
		}
	}


	@SuppressWarnings("unused")
	private static class InvalidProperty {

		private String name;

		private String value;

		private boolean flag1;

		private boolean flag2;

		public void setName(String name) {
			this.name = name;
		}

		public String getName() {
			return this.name;
		}

		public void setValue(int value) {
			this.value = Integer.toString(value);
		}

		public String getValue() {
			return this.value;
		}

		public void setFlag1(boolean flag1) {
			this.flag1 = flag1;
		}

		public Boolean getFlag1() {
			return this.flag1;
		}

		public void setFlag2(Boolean flag2) {
			this.flag2 = flag2;
		}

		public boolean getFlag2() {
			return this.flag2;
		}
	}


	@SuppressWarnings("unused")
	private static class ContainerBean {

		private ContainedBean[] containedBeans;

		public ContainedBean[] getContainedBeans() {
			return containedBeans;
		}

		public void setContainedBeans(ContainedBean[] containedBeans) {
			this.containedBeans = containedBeans;
		}
	}


	@SuppressWarnings("unused")
	private static class ContainedBean {

		private String name;

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}
	}


	@SuppressWarnings("unused")
	private static class MethodSignatureBean {

		public void doSomething() {
		}

		public void doSomethingElse(String s, int x) {
		}

		public void overloaded() {
		}

		public void overloaded(String s) {
		}

		public void overloaded(String s, BeanFactory beanFactory) {
		}

		public void doSomethingWithAnArray(String[] strings) {
		}

		public void doSomethingWithAMultiDimensionalArray(String[][] strings) {
		}
	}


	private interface MapEntry<K, V> {

		K getKey();

		void setKey(V value);

		V getValue();

		void setValue(V value);
	}


	private static class Bean implements MapEntry<String, String> {

		private String key;

		private String value;

		@Override
		public String getKey() {
			return key;
		}

		@Override
		public void setKey(String aKey) {
			key = aKey;
		}

		@Override
		public String getValue() {
			return value;
		}

		@Override
		public void setValue(String aValue) {
			value = aValue;
		}
	}

	private static class BeanWithSingleNonDefaultConstructor {

		private final String name;

		public BeanWithSingleNonDefaultConstructor(String name) {
			this.name = name;
		}

		public String getName() {
			return name;
		}
	}

	private static class BeanWithNullableTypes {

		private Integer counter;

		private Boolean flag;

		private String value;

		public BeanWithNullableTypes(@Nullable Integer counter, @Nullable Boolean flag, String value) {
			this.counter = counter;
			this.flag = flag;
			this.value = value;
		}

		@Nullable
		public Integer getCounter() {
			return counter;
		}

		@Nullable
		public Boolean isFlag() {
			return flag;
		}

		public String getValue() {
			return value;
		}
	}

	private static class BeanWithPrimitiveTypes {

		private int counter;

		private boolean flag;

		private String value;

		public BeanWithPrimitiveTypes(int counter, boolean flag, String value) {
			this.counter = counter;
			this.flag = flag;
			this.value = value;
		}

		public int getCounter() {
			return counter;
		}

		public boolean isFlag() {
			return flag;
		}

		public String getValue() {
			return value;
		}
	}

}
