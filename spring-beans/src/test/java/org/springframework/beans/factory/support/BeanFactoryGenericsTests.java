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

package org.springframework.beans.factory.support;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;
import org.junit.Test;
import test.beans.GenericBean;
import test.beans.GenericIntegerBean;
import test.beans.GenericSetOfIntegerBean;
import test.beans.TestBean;

import org.springframework.beans.PropertyEditorRegistrar;
import org.springframework.beans.PropertyEditorRegistry;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.xml.XmlBeanFactory;
import org.springframework.beans.propertyeditors.CustomNumberEditor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.UrlResource;

/**
 * @author Juergen Hoeller
 * @author Chris Beams
 * @since 20.01.2006
 */
public class BeanFactoryGenericsTests {

	@Test
	public void testGenericSetProperty() {
		DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
		RootBeanDefinition rbd = new RootBeanDefinition(GenericBean.class);

		Set<String> input = new HashSet<String>();
		input.add("4");
		input.add("5");
		rbd.getPropertyValues().add("integerSet", input);

		bf.registerBeanDefinition("genericBean", rbd);
		GenericBean<?> gb = (GenericBean<?>) bf.getBean("genericBean");

		assertTrue(gb.getIntegerSet().contains(new Integer(4)));
		assertTrue(gb.getIntegerSet().contains(new Integer(5)));
	}

	@Test
	public void testGenericListProperty() throws MalformedURLException {
		DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
		RootBeanDefinition rbd = new RootBeanDefinition(GenericBean.class);

		List<String> input = new ArrayList<String>();
		input.add("http://localhost:8080");
		input.add("http://localhost:9090");
		rbd.getPropertyValues().add("resourceList", input);

		bf.registerBeanDefinition("genericBean", rbd);
		GenericBean<?> gb = (GenericBean<?>) bf.getBean("genericBean");

		assertEquals(new UrlResource("http://localhost:8080"), gb.getResourceList().get(0));
		assertEquals(new UrlResource("http://localhost:9090"), gb.getResourceList().get(1));
	}

	@Test
	public void testGenericListPropertyWithAutowiring() throws MalformedURLException {
		DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
		bf.registerSingleton("resource1", new UrlResource("http://localhost:8080"));
		bf.registerSingleton("resource2", new UrlResource("http://localhost:9090"));

		RootBeanDefinition rbd = new RootBeanDefinition(GenericIntegerBean.class, RootBeanDefinition.AUTOWIRE_BY_TYPE);
		bf.registerBeanDefinition("genericBean", rbd);
		GenericIntegerBean gb = (GenericIntegerBean) bf.getBean("genericBean");

		assertEquals(new UrlResource("http://localhost:8080"), gb.getResourceList().get(0));
		assertEquals(new UrlResource("http://localhost:9090"), gb.getResourceList().get(1));
	}

	@Test
	public void testGenericListPropertyWithInvalidElementType() {
		DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
		RootBeanDefinition rbd = new RootBeanDefinition(GenericIntegerBean.class);

		List input = new ArrayList();
		input.add(1);
		rbd.getPropertyValues().add("testBeanList", input);

		bf.registerBeanDefinition("genericBean", rbd);
		try {
			bf.getBean("genericBean");
			fail("Should have thrown BeanCreationException");
		}
		catch (BeanCreationException ex) {
			assertTrue(ex.getMessage().contains("genericBean") && ex.getMessage().contains("testBeanList[0]"));
			assertTrue(ex.getMessage().contains(TestBean.class.getName()) && ex.getMessage().contains("Integer"));
		}
	}

	@Test
	public void testGenericListPropertyWithOptionalAutowiring() throws MalformedURLException {
		DefaultListableBeanFactory bf = new DefaultListableBeanFactory();

		RootBeanDefinition rbd = new RootBeanDefinition(GenericBean.class, RootBeanDefinition.AUTOWIRE_BY_TYPE);
		bf.registerBeanDefinition("genericBean", rbd);
		GenericBean<?> gb = (GenericBean<?>) bf.getBean("genericBean");

		assertNull(gb.getResourceList());
	}

	@Test
	public void testGenericMapProperty() {
		DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
		RootBeanDefinition rbd = new RootBeanDefinition(GenericBean.class);

		Map<String, String> input = new HashMap<String, String>();
		input.put("4", "5");
		input.put("6", "7");
		rbd.getPropertyValues().add("shortMap", input);

		bf.registerBeanDefinition("genericBean", rbd);
		GenericBean<?> gb = (GenericBean<?>) bf.getBean("genericBean");

		assertEquals(new Integer(5), gb.getShortMap().get(new Short("4")));
		assertEquals(new Integer(7), gb.getShortMap().get(new Short("6")));
	}

	@Test
	public void testGenericListOfArraysProperty() throws MalformedURLException {
		XmlBeanFactory bf = new XmlBeanFactory(new ClassPathResource("genericBeanTests.xml", getClass()));
		GenericBean<?> gb = (GenericBean<?>) bf.getBean("listOfArrays");

		assertEquals(1, gb.getListOfArrays().size());
		String[] array = gb.getListOfArrays().get(0);
		assertEquals(2, array.length);
		assertEquals("value1", array[0]);
		assertEquals("value2", array[1]);
	}


	@Test
	public void testGenericSetConstructor() {
		DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
		RootBeanDefinition rbd = new RootBeanDefinition(GenericBean.class);

		Set<String> input = new HashSet<String>();
		input.add("4");
		input.add("5");
		rbd.getConstructorArgumentValues().addGenericArgumentValue(input);

		bf.registerBeanDefinition("genericBean", rbd);
		GenericBean<?> gb = (GenericBean<?>) bf.getBean("genericBean");

		assertTrue(gb.getIntegerSet().contains(new Integer(4)));
		assertTrue(gb.getIntegerSet().contains(new Integer(5)));
	}

	@Test
	public void testGenericSetConstructorWithAutowiring() {
		DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
		bf.registerSingleton("integer1", new Integer(4));
		bf.registerSingleton("integer2", new Integer(5));

		RootBeanDefinition rbd = new RootBeanDefinition(GenericBean.class, RootBeanDefinition.AUTOWIRE_CONSTRUCTOR);
		bf.registerBeanDefinition("genericBean", rbd);
		GenericBean<?> gb = (GenericBean<?>) bf.getBean("genericBean");

		assertTrue(gb.getIntegerSet().contains(new Integer(4)));
		assertTrue(gb.getIntegerSet().contains(new Integer(5)));
	}

	@Test
	public void testGenericSetConstructorWithOptionalAutowiring() {
		DefaultListableBeanFactory bf = new DefaultListableBeanFactory();

		RootBeanDefinition rbd = new RootBeanDefinition(GenericBean.class, RootBeanDefinition.AUTOWIRE_CONSTRUCTOR);
		bf.registerBeanDefinition("genericBean", rbd);
		GenericBean<?> gb = (GenericBean<?>) bf.getBean("genericBean");

		assertNull(gb.getIntegerSet());
	}

	@Test
	public void testGenericSetListConstructor() throws MalformedURLException {
		DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
		RootBeanDefinition rbd = new RootBeanDefinition(GenericBean.class);

		Set<String> input = new HashSet<String>();
		input.add("4");
		input.add("5");
		List<String> input2 = new ArrayList<String>();
		input2.add("http://localhost:8080");
		input2.add("http://localhost:9090");
		rbd.getConstructorArgumentValues().addGenericArgumentValue(input);
		rbd.getConstructorArgumentValues().addGenericArgumentValue(input2);

		bf.registerBeanDefinition("genericBean", rbd);
		GenericBean<?> gb = (GenericBean<?>) bf.getBean("genericBean");

		assertTrue(gb.getIntegerSet().contains(new Integer(4)));
		assertTrue(gb.getIntegerSet().contains(new Integer(5)));
		assertEquals(new UrlResource("http://localhost:8080"), gb.getResourceList().get(0));
		assertEquals(new UrlResource("http://localhost:9090"), gb.getResourceList().get(1));
	}

	@Test
	public void testGenericSetListConstructorWithAutowiring() throws MalformedURLException {
		DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
		bf.registerSingleton("integer1", new Integer(4));
		bf.registerSingleton("integer2", new Integer(5));
		bf.registerSingleton("resource1", new UrlResource("http://localhost:8080"));
		bf.registerSingleton("resource2", new UrlResource("http://localhost:9090"));

		RootBeanDefinition rbd = new RootBeanDefinition(GenericBean.class, RootBeanDefinition.AUTOWIRE_CONSTRUCTOR);
		bf.registerBeanDefinition("genericBean", rbd);
		GenericBean<?> gb = (GenericBean<?>) bf.getBean("genericBean");

		assertTrue(gb.getIntegerSet().contains(new Integer(4)));
		assertTrue(gb.getIntegerSet().contains(new Integer(5)));
		assertEquals(new UrlResource("http://localhost:8080"), gb.getResourceList().get(0));
		assertEquals(new UrlResource("http://localhost:9090"), gb.getResourceList().get(1));
	}

	@Test
	public void testGenericSetListConstructorWithOptionalAutowiring() throws MalformedURLException {
		DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
		bf.registerSingleton("resource1", new UrlResource("http://localhost:8080"));
		bf.registerSingleton("resource2", new UrlResource("http://localhost:9090"));

		RootBeanDefinition rbd = new RootBeanDefinition(GenericBean.class, RootBeanDefinition.AUTOWIRE_CONSTRUCTOR);
		bf.registerBeanDefinition("genericBean", rbd);
		GenericBean<?> gb = (GenericBean<?>) bf.getBean("genericBean");

		assertNull(gb.getIntegerSet());
		assertNull(gb.getResourceList());
	}

	@Test
	public void testGenericSetMapConstructor() throws MalformedURLException {
		DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
		RootBeanDefinition rbd = new RootBeanDefinition(GenericBean.class);

		Set<String> input = new HashSet<String>();
		input.add("4");
		input.add("5");
		Map<String, String> input2 = new HashMap<String, String>();
		input2.put("4", "5");
		input2.put("6", "7");
		rbd.getConstructorArgumentValues().addGenericArgumentValue(input);
		rbd.getConstructorArgumentValues().addGenericArgumentValue(input2);

		bf.registerBeanDefinition("genericBean", rbd);
		GenericBean<?> gb = (GenericBean<?>) bf.getBean("genericBean");

		assertTrue(gb.getIntegerSet().contains(new Integer(4)));
		assertTrue(gb.getIntegerSet().contains(new Integer(5)));
		assertEquals(new Integer(5), gb.getShortMap().get(new Short("4")));
		assertEquals(new Integer(7), gb.getShortMap().get(new Short("6")));
	}

	@Test
	public void testGenericMapResourceConstructor() throws MalformedURLException {
		DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
		RootBeanDefinition rbd = new RootBeanDefinition(GenericBean.class);

		Map<String, String> input = new HashMap<String, String>();
		input.put("4", "5");
		input.put("6", "7");
		rbd.getConstructorArgumentValues().addGenericArgumentValue(input);
		rbd.getConstructorArgumentValues().addGenericArgumentValue("http://localhost:8080");

		bf.registerBeanDefinition("genericBean", rbd);
		GenericBean<?> gb = (GenericBean<?>) bf.getBean("genericBean");

		assertEquals(new Integer(5), gb.getShortMap().get(new Short("4")));
		assertEquals(new Integer(7), gb.getShortMap().get(new Short("6")));
		assertEquals(new UrlResource("http://localhost:8080"), gb.getResourceList().get(0));
	}

	@Test
	public void testGenericMapMapConstructor() throws MalformedURLException {
		DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
		RootBeanDefinition rbd = new RootBeanDefinition(GenericBean.class);

		Map<String, String> input = new HashMap<String, String>();
		input.put("1", "0");
		input.put("2", "3");
		Map<String, String> input2 = new HashMap<String, String>();
		input2.put("4", "5");
		input2.put("6", "7");
		rbd.getConstructorArgumentValues().addGenericArgumentValue(input);
		rbd.getConstructorArgumentValues().addGenericArgumentValue(input2);

		bf.registerBeanDefinition("genericBean", rbd);
		GenericBean<?> gb = (GenericBean<?>) bf.getBean("genericBean");

		assertNotSame(gb.getPlainMap(), gb.getShortMap());
		assertEquals(2, gb.getPlainMap().size());
		assertEquals("0", gb.getPlainMap().get("1"));
		assertEquals("3", gb.getPlainMap().get("2"));
		assertEquals(2, gb.getShortMap().size());
		assertEquals(new Integer(5), gb.getShortMap().get(new Short("4")));
		assertEquals(new Integer(7), gb.getShortMap().get(new Short("6")));
	}

	@Test
	public void testGenericMapMapConstructorWithSameRefAndConversion() throws MalformedURLException {
		DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
		RootBeanDefinition rbd = new RootBeanDefinition(GenericBean.class);

		Map<String, String> input = new HashMap<String, String>();
		input.put("1", "0");
		input.put("2", "3");
		rbd.getConstructorArgumentValues().addGenericArgumentValue(input);
		rbd.getConstructorArgumentValues().addGenericArgumentValue(input);

		bf.registerBeanDefinition("genericBean", rbd);
		GenericBean<?> gb = (GenericBean<?>) bf.getBean("genericBean");

		assertNotSame(gb.getPlainMap(), gb.getShortMap());
		assertEquals(2, gb.getPlainMap().size());
		assertEquals("0", gb.getPlainMap().get("1"));
		assertEquals("3", gb.getPlainMap().get("2"));
		assertEquals(2, gb.getShortMap().size());
		assertEquals(new Integer(0), gb.getShortMap().get(new Short("1")));
		assertEquals(new Integer(3), gb.getShortMap().get(new Short("2")));
	}

	@Test
	public void testGenericMapMapConstructorWithSameRefAndNoConversion() throws MalformedURLException {
		DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
		RootBeanDefinition rbd = new RootBeanDefinition(GenericBean.class);

		Map<Short, Integer> input = new HashMap<Short, Integer>();
		input.put(new Short((short) 1), new Integer(0));
		input.put(new Short((short) 2), new Integer(3));
		rbd.getConstructorArgumentValues().addGenericArgumentValue(input);
		rbd.getConstructorArgumentValues().addGenericArgumentValue(input);

		bf.registerBeanDefinition("genericBean", rbd);
		GenericBean<?> gb = (GenericBean<?>) bf.getBean("genericBean");

		assertSame(gb.getPlainMap(), gb.getShortMap());
		assertEquals(2, gb.getShortMap().size());
		assertEquals(new Integer(0), gb.getShortMap().get(new Short("1")));
		assertEquals(new Integer(3), gb.getShortMap().get(new Short("2")));
	}

	@Test
	public void testGenericMapWithKeyTypeConstructor() throws MalformedURLException {
		DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
		RootBeanDefinition rbd = new RootBeanDefinition(GenericBean.class);

		Map<String, String> input = new HashMap<String, String>();
		input.put("4", "5");
		input.put("6", "7");
		rbd.getConstructorArgumentValues().addGenericArgumentValue(input);

		bf.registerBeanDefinition("genericBean", rbd);
		GenericBean<?> gb = (GenericBean<?>) bf.getBean("genericBean");

		assertEquals("5", gb.getLongMap().get(new Long("4")));
		assertEquals("7", gb.getLongMap().get(new Long("6")));
	}

	@Test
	public void testGenericMapWithCollectionValueConstructor() throws MalformedURLException {
		DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
		bf.addPropertyEditorRegistrar(new PropertyEditorRegistrar() {
			public void registerCustomEditors(PropertyEditorRegistry registry) {
				registry.registerCustomEditor(Number.class, new CustomNumberEditor(Integer.class, false));
			}
		});
		RootBeanDefinition rbd = new RootBeanDefinition(GenericBean.class);

		Map<String, AbstractCollection<?>> input = new HashMap<String, AbstractCollection<?>>();
		HashSet<Integer> value1 = new HashSet<Integer>();
		value1.add(new Integer(1));
		input.put("1", value1);
		ArrayList<Boolean> value2 = new ArrayList<Boolean>();
		value2.add(Boolean.TRUE);
		input.put("2", value2);
		rbd.getConstructorArgumentValues().addGenericArgumentValue(Boolean.TRUE);
		rbd.getConstructorArgumentValues().addGenericArgumentValue(input);

		bf.registerBeanDefinition("genericBean", rbd);
		GenericBean<?> gb = (GenericBean<?>) bf.getBean("genericBean");

		assertTrue(gb.getCollectionMap().get(new Integer(1)) instanceof HashSet);
		assertTrue(gb.getCollectionMap().get(new Integer(2)) instanceof ArrayList);
	}


	@Test
	public void testGenericSetFactoryMethod() {
		DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
		RootBeanDefinition rbd = new RootBeanDefinition(GenericBean.class);
		rbd.setFactoryMethodName("createInstance");

		Set<String> input = new HashSet<String>();
		input.add("4");
		input.add("5");
		rbd.getConstructorArgumentValues().addGenericArgumentValue(input);

		bf.registerBeanDefinition("genericBean", rbd);
		GenericBean<?> gb = (GenericBean<?>) bf.getBean("genericBean");

		assertTrue(gb.getIntegerSet().contains(new Integer(4)));
		assertTrue(gb.getIntegerSet().contains(new Integer(5)));
	}

	@Test
	public void testGenericSetListFactoryMethod() throws MalformedURLException {
		DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
		RootBeanDefinition rbd = new RootBeanDefinition(GenericBean.class);
		rbd.setFactoryMethodName("createInstance");

		Set<String> input = new HashSet<String>();
		input.add("4");
		input.add("5");
		List<String> input2 = new ArrayList<String>();
		input2.add("http://localhost:8080");
		input2.add("http://localhost:9090");
		rbd.getConstructorArgumentValues().addGenericArgumentValue(input);
		rbd.getConstructorArgumentValues().addGenericArgumentValue(input2);

		bf.registerBeanDefinition("genericBean", rbd);
		GenericBean<?> gb = (GenericBean<?>) bf.getBean("genericBean");

		assertTrue(gb.getIntegerSet().contains(new Integer(4)));
		assertTrue(gb.getIntegerSet().contains(new Integer(5)));
		assertEquals(new UrlResource("http://localhost:8080"), gb.getResourceList().get(0));
		assertEquals(new UrlResource("http://localhost:9090"), gb.getResourceList().get(1));
	}

	@Test
	public void testGenericSetMapFactoryMethod() throws MalformedURLException {
		DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
		RootBeanDefinition rbd = new RootBeanDefinition(GenericBean.class);
		rbd.setFactoryMethodName("createInstance");

		Set<String> input = new HashSet<String>();
		input.add("4");
		input.add("5");
		Map<String, String> input2 = new HashMap<String, String>();
		input2.put("4", "5");
		input2.put("6", "7");
		rbd.getConstructorArgumentValues().addGenericArgumentValue(input);
		rbd.getConstructorArgumentValues().addGenericArgumentValue(input2);

		bf.registerBeanDefinition("genericBean", rbd);
		GenericBean<?> gb = (GenericBean<?>) bf.getBean("genericBean");

		assertTrue(gb.getIntegerSet().contains(new Integer(4)));
		assertTrue(gb.getIntegerSet().contains(new Integer(5)));
		assertEquals(new Integer(5), gb.getShortMap().get(new Short("4")));
		assertEquals(new Integer(7), gb.getShortMap().get(new Short("6")));
	}

	@Test
	public void testGenericMapResourceFactoryMethod() throws MalformedURLException {
		DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
		RootBeanDefinition rbd = new RootBeanDefinition(GenericBean.class);
		rbd.setFactoryMethodName("createInstance");

		Map<String, String> input = new HashMap<String, String>();
		input.put("4", "5");
		input.put("6", "7");
		rbd.getConstructorArgumentValues().addGenericArgumentValue(input);
		rbd.getConstructorArgumentValues().addGenericArgumentValue("http://localhost:8080");

		bf.registerBeanDefinition("genericBean", rbd);
		GenericBean<?> gb = (GenericBean<?>) bf.getBean("genericBean");

		assertEquals(new Integer(5), gb.getShortMap().get(new Short("4")));
		assertEquals(new Integer(7), gb.getShortMap().get(new Short("6")));
		assertEquals(new UrlResource("http://localhost:8080"), gb.getResourceList().get(0));
	}

	@Test
	public void testGenericMapMapFactoryMethod() throws MalformedURLException {
		DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
		RootBeanDefinition rbd = new RootBeanDefinition(GenericBean.class);
		rbd.setFactoryMethodName("createInstance");

		Map<String, String> input = new HashMap<String, String>();
		input.put("1", "0");
		input.put("2", "3");
		Map<String, String> input2 = new HashMap<String, String>();
		input2.put("4", "5");
		input2.put("6", "7");
		rbd.getConstructorArgumentValues().addGenericArgumentValue(input);
		rbd.getConstructorArgumentValues().addGenericArgumentValue(input2);

		bf.registerBeanDefinition("genericBean", rbd);
		GenericBean<?> gb = (GenericBean<?>) bf.getBean("genericBean");

		assertEquals("0", gb.getPlainMap().get("1"));
		assertEquals("3", gb.getPlainMap().get("2"));
		assertEquals(new Integer(5), gb.getShortMap().get(new Short("4")));
		assertEquals(new Integer(7), gb.getShortMap().get(new Short("6")));
	}

	@Test
	public void testGenericMapWithKeyTypeFactoryMethod() throws MalformedURLException {
		DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
		RootBeanDefinition rbd = new RootBeanDefinition(GenericBean.class);
		rbd.setFactoryMethodName("createInstance");

		Map<String, String> input = new HashMap<String, String>();
		input.put("4", "5");
		input.put("6", "7");
		rbd.getConstructorArgumentValues().addGenericArgumentValue(input);

		bf.registerBeanDefinition("genericBean", rbd);
		GenericBean<?> gb = (GenericBean<?>) bf.getBean("genericBean");

		assertEquals("5", gb.getLongMap().get(new Long("4")));
		assertEquals("7", gb.getLongMap().get(new Long("6")));
	}

	@Test
	public void testGenericMapWithCollectionValueFactoryMethod() throws MalformedURLException {
		DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
		bf.addPropertyEditorRegistrar(new PropertyEditorRegistrar() {
			public void registerCustomEditors(PropertyEditorRegistry registry) {
				registry.registerCustomEditor(Number.class, new CustomNumberEditor(Integer.class, false));
			}
		});
		RootBeanDefinition rbd = new RootBeanDefinition(GenericBean.class);
		rbd.setFactoryMethodName("createInstance");

		Map<String, AbstractCollection<?>> input = new HashMap<String, AbstractCollection<?>>();
		HashSet<Integer> value1 = new HashSet<Integer>();
		value1.add(new Integer(1));
		input.put("1", value1);
		ArrayList<Boolean> value2 = new ArrayList<Boolean>();
		value2.add(Boolean.TRUE);
		input.put("2", value2);
		rbd.getConstructorArgumentValues().addGenericArgumentValue(Boolean.TRUE);
		rbd.getConstructorArgumentValues().addGenericArgumentValue(input);

		bf.registerBeanDefinition("genericBean", rbd);
		GenericBean<?> gb = (GenericBean<?>) bf.getBean("genericBean");

		assertTrue(gb.getCollectionMap().get(new Integer(1)) instanceof HashSet);
		assertTrue(gb.getCollectionMap().get(new Integer(2)) instanceof ArrayList);
	}

	@Test
	public void testGenericListBean() throws Exception {
		XmlBeanFactory bf = new XmlBeanFactory(new ClassPathResource("genericBeanTests.xml", getClass()));
		List<?> list = (List<?>) bf.getBean("list");
		assertEquals(1, list.size());
		assertEquals(new URL("http://localhost:8080"), list.get(0));
	}

	@Test
	public void testGenericSetBean() throws Exception {
		XmlBeanFactory bf = new XmlBeanFactory(new ClassPathResource("genericBeanTests.xml", getClass()));
		Set<?> set = (Set<?>) bf.getBean("set");
		assertEquals(1, set.size());
		assertEquals(new URL("http://localhost:8080"), set.iterator().next());
	}

	@Test
	public void testGenericMapBean() throws Exception {
		XmlBeanFactory bf = new XmlBeanFactory(new ClassPathResource("genericBeanTests.xml", getClass()));
		Map<?, ?> map = (Map<?, ?>) bf.getBean("map");
		assertEquals(1, map.size());
		assertEquals(new Integer(10), map.keySet().iterator().next());
		assertEquals(new URL("http://localhost:8080"), map.values().iterator().next());
	}

	@Test
	public void testGenericallyTypedIntegerBean() throws Exception {
		XmlBeanFactory bf = new XmlBeanFactory(new ClassPathResource("genericBeanTests.xml", getClass()));
		GenericIntegerBean gb = (GenericIntegerBean) bf.getBean("integerBean");
		assertEquals(new Integer(10), gb.getGenericProperty());
		assertEquals(new Integer(20), gb.getGenericListProperty().get(0));
		assertEquals(new Integer(30), gb.getGenericListProperty().get(1));
	}

	@Test
	public void testGenericallyTypedSetOfIntegerBean() throws Exception {
		XmlBeanFactory bf = new XmlBeanFactory(new ClassPathResource("genericBeanTests.xml", getClass()));
		GenericSetOfIntegerBean gb = (GenericSetOfIntegerBean) bf.getBean("setOfIntegerBean");
		assertEquals(new Integer(10), gb.getGenericProperty().iterator().next());
		assertEquals(new Integer(20), gb.getGenericListProperty().get(0).iterator().next());
		assertEquals(new Integer(30), gb.getGenericListProperty().get(1).iterator().next());
	}

	@Test
	public void testSetBean() throws Exception {
		XmlBeanFactory bf = new XmlBeanFactory(new ClassPathResource("genericBeanTests.xml", getClass()));
		UrlSet us = (UrlSet) bf.getBean("setBean");
		assertEquals(1, us.size());
		assertEquals(new URL("http://www.springframework.org"), us.iterator().next());
	}


	@SuppressWarnings("serial")
	public static class NamedUrlList extends LinkedList<URL> {
	}


	@SuppressWarnings("serial")
	public static class NamedUrlSet extends HashSet<URL> {
	}


	@SuppressWarnings("serial")
	public static class NamedUrlMap extends HashMap<Integer, URL> {
	}


	public static class CollectionDependentBean {

		public CollectionDependentBean(NamedUrlList list, NamedUrlSet set, NamedUrlMap map) {
			assertEquals(1, list.size());
			assertEquals(1, set.size());
			assertEquals(1, map.size());
		}
	}


	@SuppressWarnings("serial")
	public static class UrlSet extends HashSet<URL> {

		public UrlSet() {
			super();
		}

		public UrlSet(Set<? extends URL> urls) {
			super();
		}

		public void setUrlNames(Set<URI> urlNames) throws MalformedURLException {
			for (URI urlName : urlNames) {
				add(urlName.toURL());
			}
		}
	}

}
