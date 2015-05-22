/*
 * Copyright 2002-2015 the original author or authors.
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

package org.springframework.core.annotation;

import java.lang.annotation.Annotation;
import java.lang.annotation.Inherited;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.subpackage.NonPublicAnnotatedClass;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

import static java.util.stream.Collectors.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import static org.springframework.core.annotation.AnnotationUtils.*;

/**
 * Unit tests for {@link AnnotationUtils}.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @author Chris Beams
 * @author Phillip Webb
 */
public class AnnotationUtilsTests {

	@Rule
	public final ExpectedException exception = ExpectedException.none();

	@Test
	public void findMethodAnnotationOnLeaf() throws Exception {
		Method m = Leaf.class.getMethod("annotatedOnLeaf");
		assertNotNull(m.getAnnotation(Order.class));
		assertNotNull(getAnnotation(m, Order.class));
		assertNotNull(findAnnotation(m, Order.class));
	}

	/** @since 4.2 */
	@Test
	public void findMethodAnnotationWithAnnotationOnMethodInInterface() throws Exception {
		Method m = Leaf.class.getMethod("fromInterfaceImplementedByRoot");
		// @Order is not @Inherited
		assertNull(m.getAnnotation(Order.class));
		// getAnnotation() does not search on interfaces
		assertNull(getAnnotation(m, Order.class));
		// findAnnotation() does search on interfaces
		assertNotNull(findAnnotation(m, Order.class));
	}

	/** @since 4.2 */
	@Test
	public void findMethodAnnotationWithMetaAnnotationOnLeaf() throws Exception {
		Method m = Leaf.class.getMethod("metaAnnotatedOnLeaf");
		assertNull(m.getAnnotation(Order.class));
		assertNotNull(getAnnotation(m, Order.class));
		assertNotNull(findAnnotation(m, Order.class));
	}

	/** @since 4.2 */
	@Test
	public void findMethodAnnotationWithMetaMetaAnnotationOnLeaf() throws Exception {
		Method m = Leaf.class.getMethod("metaMetaAnnotatedOnLeaf");
		assertNull(m.getAnnotation(Component.class));
		assertNull(getAnnotation(m, Component.class));
		assertNotNull(findAnnotation(m, Component.class));
	}

	@Test
	public void findMethodAnnotationOnRoot() throws Exception {
		Method m = Leaf.class.getMethod("annotatedOnRoot");
		assertNotNull(m.getAnnotation(Order.class));
		assertNotNull(getAnnotation(m, Order.class));
		assertNotNull(findAnnotation(m, Order.class));
	}

	/** @since 4.2 */
	@Test
	public void findMethodAnnotationWithMetaAnnotationOnRoot() throws Exception {
		Method m = Leaf.class.getMethod("metaAnnotatedOnRoot");
		assertNull(m.getAnnotation(Order.class));
		assertNotNull(getAnnotation(m, Order.class));
		assertNotNull(findAnnotation(m, Order.class));
	}

	@Test
	public void findMethodAnnotationOnRootButOverridden() throws Exception {
		Method m = Leaf.class.getMethod("overrideWithoutNewAnnotation");
		assertNull(m.getAnnotation(Order.class));
		assertNull(getAnnotation(m, Order.class));
		assertNotNull(findAnnotation(m, Order.class));
	}

	@Test
	public void findMethodAnnotationNotAnnotated() throws Exception {
		Method m = Leaf.class.getMethod("notAnnotated");
		assertNull(findAnnotation(m, Order.class));
	}

	@Test
	public void findMethodAnnotationOnBridgeMethod() throws Exception {
		Method m = SimpleFoo.class.getMethod("something", Object.class);
		assertTrue(m.isBridge());
		assertNull(m.getAnnotation(Order.class));
		assertNull(getAnnotation(m, Order.class));
		assertNotNull(findAnnotation(m, Order.class));
		// TODO: getAnnotation() on bridge method actually found on OpenJDK 8 b99 and higher!
		// assertNull(m.getAnnotation(Transactional.class));
		assertNotNull(getAnnotation(m, Transactional.class));
		assertNotNull(findAnnotation(m, Transactional.class));
	}

	@Test
	public void findMethodAnnotationFromInterface() throws Exception {
		Method method = ImplementsInterfaceWithAnnotatedMethod.class.getMethod("foo");
		Order order = findAnnotation(method, Order.class);
		assertNotNull(order);
	}

	@Test
	public void findMethodAnnotationFromInterfaceOnSuper() throws Exception {
		Method method = SubOfImplementsInterfaceWithAnnotatedMethod.class.getMethod("foo");
		Order order = findAnnotation(method, Order.class);
		assertNotNull(order);
	}

	@Test
	public void findMethodAnnotationFromInterfaceWhenSuperDoesNotImplementMethod() throws Exception {
		Method method = SubOfAbstractImplementsInterfaceWithAnnotatedMethod.class.getMethod("foo");
		Order order = findAnnotation(method, Order.class);
		assertNotNull(order);
	}

	/** @since 4.1.2 */
	@Test
	public void findClassAnnotationFavorsMoreLocallyDeclaredComposedAnnotationsOverAnnotationsOnInterfaces() {
		Component component = findAnnotation(ClassWithLocalMetaAnnotationAndMetaAnnotatedInterface.class,
			Component.class);
		assertNotNull(component);
		assertEquals("meta2", component.value());
	}

	/** @since 4.0.3 */
	@Test
	public void findClassAnnotationFavorsMoreLocallyDeclaredComposedAnnotationsOverInheritedAnnotations() {
		Transactional transactional = findAnnotation(SubSubClassWithInheritedAnnotation.class, Transactional.class);
		assertNotNull(transactional);
		assertTrue("readOnly flag for SubSubClassWithInheritedAnnotation", transactional.readOnly());
	}

	/** @since 4.0.3 */
	@Test
	public void findClassAnnotationFavorsMoreLocallyDeclaredComposedAnnotationsOverInheritedComposedAnnotations() {
		Component component = findAnnotation(SubSubClassWithInheritedMetaAnnotation.class, Component.class);
		assertNotNull(component);
		assertEquals("meta2", component.value());
	}

	@Test
	public void findClassAnnotationOnMetaMetaAnnotatedClass() {
		Component component = findAnnotation(MetaMetaAnnotatedClass.class, Component.class);
		assertNotNull("Should find meta-annotation on composed annotation on class", component);
		assertEquals("meta2", component.value());
	}

	@Test
	public void findClassAnnotationOnMetaMetaMetaAnnotatedClass() {
		Component component = findAnnotation(MetaMetaMetaAnnotatedClass.class, Component.class);
		assertNotNull("Should find meta-annotation on meta-annotation on composed annotation on class", component);
		assertEquals("meta2", component.value());
	}

	@Test
	public void findClassAnnotationOnAnnotatedClassWithMissingTargetMetaAnnotation() {
		// TransactionalClass is NOT annotated or meta-annotated with @Component
		Component component = findAnnotation(TransactionalClass.class, Component.class);
		assertNull("Should not find @Component on TransactionalClass", component);
	}

	@Test
	public void findClassAnnotationOnMetaCycleAnnotatedClassWithMissingTargetMetaAnnotation() {
		Component component = findAnnotation(MetaCycleAnnotatedClass.class, Component.class);
		assertNull("Should not find @Component on MetaCycleAnnotatedClass", component);
	}

	/** @since 4.2 */
	@Test
	public void findClassAnnotationOnInheritedAnnotationInterface() {
		Transactional tx = findAnnotation(InheritedAnnotationInterface.class, Transactional.class);
		assertNotNull("Should find @Transactional on InheritedAnnotationInterface", tx);
	}

	/** @since 4.2 */
	@Test
	public void findClassAnnotationOnSubInheritedAnnotationInterface() {
		Transactional tx = findAnnotation(SubInheritedAnnotationInterface.class, Transactional.class);
		assertNotNull("Should find @Transactional on SubInheritedAnnotationInterface", tx);
	}

	/** @since 4.2 */
	@Test
	public void findClassAnnotationOnSubSubInheritedAnnotationInterface() {
		Transactional tx = findAnnotation(SubSubInheritedAnnotationInterface.class, Transactional.class);
		assertNotNull("Should find @Transactional on SubSubInheritedAnnotationInterface", tx);
	}

	/** @since 4.2 */
	@Test
	public void findClassAnnotationOnNonInheritedAnnotationInterface() {
		Order order = findAnnotation(NonInheritedAnnotationInterface.class, Order.class);
		assertNotNull("Should find @Order on NonInheritedAnnotationInterface", order);
	}

	/** @since 4.2 */
	@Test
	public void findClassAnnotationOnSubNonInheritedAnnotationInterface() {
		Order order = findAnnotation(SubNonInheritedAnnotationInterface.class, Order.class);
		assertNotNull("Should find @Order on SubNonInheritedAnnotationInterface", order);
	}

	/** @since 4.2 */
	@Test
	public void findClassAnnotationOnSubSubNonInheritedAnnotationInterface() {
		Order order = findAnnotation(SubSubNonInheritedAnnotationInterface.class, Order.class);
		assertNotNull("Should find @Order on SubSubNonInheritedAnnotationInterface", order);
	}

	@Test
	public void findAnnotationDeclaringClassForAllScenarios() throws Exception {
		// no class-level annotation
		assertNull(findAnnotationDeclaringClass(Transactional.class, NonAnnotatedInterface.class));
		assertNull(findAnnotationDeclaringClass(Transactional.class, NonAnnotatedClass.class));

		// inherited class-level annotation; note: @Transactional is inherited
		assertEquals(InheritedAnnotationInterface.class,
			findAnnotationDeclaringClass(Transactional.class, InheritedAnnotationInterface.class));
		assertNull(findAnnotationDeclaringClass(Transactional.class, SubInheritedAnnotationInterface.class));
		assertEquals(InheritedAnnotationClass.class,
			findAnnotationDeclaringClass(Transactional.class, InheritedAnnotationClass.class));
		assertEquals(InheritedAnnotationClass.class,
			findAnnotationDeclaringClass(Transactional.class, SubInheritedAnnotationClass.class));

		// non-inherited class-level annotation; note: @Order is not inherited,
		// but findAnnotationDeclaringClass() should still find it on classes.
		assertEquals(NonInheritedAnnotationInterface.class,
			findAnnotationDeclaringClass(Order.class, NonInheritedAnnotationInterface.class));
		assertNull(findAnnotationDeclaringClass(Order.class, SubNonInheritedAnnotationInterface.class));
		assertEquals(NonInheritedAnnotationClass.class,
			findAnnotationDeclaringClass(Order.class, NonInheritedAnnotationClass.class));
		assertEquals(NonInheritedAnnotationClass.class,
			findAnnotationDeclaringClass(Order.class, SubNonInheritedAnnotationClass.class));
	}

	@Test
	public void findAnnotationDeclaringClassForTypesWithSingleCandidateType() {
		// no class-level annotation
		List<Class<? extends Annotation>> transactionalCandidateList = Arrays.<Class<? extends Annotation>> asList(Transactional.class);
		assertNull(findAnnotationDeclaringClassForTypes(transactionalCandidateList, NonAnnotatedInterface.class));
		assertNull(findAnnotationDeclaringClassForTypes(transactionalCandidateList, NonAnnotatedClass.class));

		// inherited class-level annotation; note: @Transactional is inherited
		assertEquals(InheritedAnnotationInterface.class,
			findAnnotationDeclaringClassForTypes(transactionalCandidateList, InheritedAnnotationInterface.class));
		assertNull(findAnnotationDeclaringClassForTypes(transactionalCandidateList, SubInheritedAnnotationInterface.class));
		assertEquals(InheritedAnnotationClass.class,
			findAnnotationDeclaringClassForTypes(transactionalCandidateList, InheritedAnnotationClass.class));
		assertEquals(InheritedAnnotationClass.class,
			findAnnotationDeclaringClassForTypes(transactionalCandidateList, SubInheritedAnnotationClass.class));

		// non-inherited class-level annotation; note: @Order is not inherited,
		// but findAnnotationDeclaringClassForTypes() should still find it on classes.
		List<Class<? extends Annotation>> orderCandidateList = Arrays.<Class<? extends Annotation>> asList(Order.class);
		assertEquals(NonInheritedAnnotationInterface.class,
			findAnnotationDeclaringClassForTypes(orderCandidateList, NonInheritedAnnotationInterface.class));
		assertNull(findAnnotationDeclaringClassForTypes(orderCandidateList, SubNonInheritedAnnotationInterface.class));
		assertEquals(NonInheritedAnnotationClass.class,
			findAnnotationDeclaringClassForTypes(orderCandidateList, NonInheritedAnnotationClass.class));
		assertEquals(NonInheritedAnnotationClass.class,
			findAnnotationDeclaringClassForTypes(orderCandidateList, SubNonInheritedAnnotationClass.class));
	}

	@Test
	public void findAnnotationDeclaringClassForTypesWithMultipleCandidateTypes() {
		List<Class<? extends Annotation>> candidates = Arrays.<Class<? extends Annotation>> asList(Transactional.class, Order.class);

		// no class-level annotation
		assertNull(findAnnotationDeclaringClassForTypes(candidates, NonAnnotatedInterface.class));
		assertNull(findAnnotationDeclaringClassForTypes(candidates, NonAnnotatedClass.class));

		// inherited class-level annotation; note: @Transactional is inherited
		assertEquals(InheritedAnnotationInterface.class,
			findAnnotationDeclaringClassForTypes(candidates, InheritedAnnotationInterface.class));
		assertNull(findAnnotationDeclaringClassForTypes(candidates, SubInheritedAnnotationInterface.class));
		assertEquals(InheritedAnnotationClass.class,
			findAnnotationDeclaringClassForTypes(candidates, InheritedAnnotationClass.class));
		assertEquals(InheritedAnnotationClass.class,
			findAnnotationDeclaringClassForTypes(candidates, SubInheritedAnnotationClass.class));

		// non-inherited class-level annotation; note: @Order is not inherited,
		// but findAnnotationDeclaringClassForTypes() should still find it on classes.
		assertEquals(NonInheritedAnnotationInterface.class,
			findAnnotationDeclaringClassForTypes(candidates, NonInheritedAnnotationInterface.class));
		assertNull(findAnnotationDeclaringClassForTypes(candidates, SubNonInheritedAnnotationInterface.class));
		assertEquals(NonInheritedAnnotationClass.class,
			findAnnotationDeclaringClassForTypes(candidates, NonInheritedAnnotationClass.class));
		assertEquals(NonInheritedAnnotationClass.class,
			findAnnotationDeclaringClassForTypes(candidates, SubNonInheritedAnnotationClass.class));

		// class hierarchy mixed with @Transactional and @Order declarations
		assertEquals(TransactionalClass.class,
			findAnnotationDeclaringClassForTypes(candidates, TransactionalClass.class));
		assertEquals(TransactionalAndOrderedClass.class,
			findAnnotationDeclaringClassForTypes(candidates, TransactionalAndOrderedClass.class));
		assertEquals(TransactionalAndOrderedClass.class,
			findAnnotationDeclaringClassForTypes(candidates, SubTransactionalAndOrderedClass.class));
	}

	@Test
	public void isAnnotationDeclaredLocallyForAllScenarios() throws Exception {
		// no class-level annotation
		assertFalse(isAnnotationDeclaredLocally(Transactional.class, NonAnnotatedInterface.class));
		assertFalse(isAnnotationDeclaredLocally(Transactional.class, NonAnnotatedClass.class));

		// inherited class-level annotation; note: @Transactional is inherited
		assertTrue(isAnnotationDeclaredLocally(Transactional.class, InheritedAnnotationInterface.class));
		assertFalse(isAnnotationDeclaredLocally(Transactional.class, SubInheritedAnnotationInterface.class));
		assertTrue(isAnnotationDeclaredLocally(Transactional.class, InheritedAnnotationClass.class));
		assertFalse(isAnnotationDeclaredLocally(Transactional.class, SubInheritedAnnotationClass.class));

		// non-inherited class-level annotation; note: @Order is not inherited
		assertTrue(isAnnotationDeclaredLocally(Order.class, NonInheritedAnnotationInterface.class));
		assertFalse(isAnnotationDeclaredLocally(Order.class, SubNonInheritedAnnotationInterface.class));
		assertTrue(isAnnotationDeclaredLocally(Order.class, NonInheritedAnnotationClass.class));
		assertFalse(isAnnotationDeclaredLocally(Order.class, SubNonInheritedAnnotationClass.class));
	}

	@Test
	public void isAnnotationInheritedForAllScenarios() throws Exception {
		// no class-level annotation
		assertFalse(isAnnotationInherited(Transactional.class, NonAnnotatedInterface.class));
		assertFalse(isAnnotationInherited(Transactional.class, NonAnnotatedClass.class));

		// inherited class-level annotation; note: @Transactional is inherited
		assertFalse(isAnnotationInherited(Transactional.class, InheritedAnnotationInterface.class));
		// isAnnotationInherited() does not currently traverse interface
		// hierarchies. Thus the following, though perhaps counter intuitive,
		// must be false:
		assertFalse(isAnnotationInherited(Transactional.class, SubInheritedAnnotationInterface.class));
		assertFalse(isAnnotationInherited(Transactional.class, InheritedAnnotationClass.class));
		assertTrue(isAnnotationInherited(Transactional.class, SubInheritedAnnotationClass.class));

		// non-inherited class-level annotation; note: @Order is not inherited
		assertFalse(isAnnotationInherited(Order.class, NonInheritedAnnotationInterface.class));
		assertFalse(isAnnotationInherited(Order.class, SubNonInheritedAnnotationInterface.class));
		assertFalse(isAnnotationInherited(Order.class, NonInheritedAnnotationClass.class));
		assertFalse(isAnnotationInherited(Order.class, SubNonInheritedAnnotationClass.class));
	}

	@Test
	public void getAnnotationAttributesWithoutAttributeAliases() {
		Component component = WebController.class.getAnnotation(Component.class);
		assertNotNull(component);

		AnnotationAttributes attributes = (AnnotationAttributes) getAnnotationAttributes(component);
		assertNotNull(attributes);
		assertEquals("value attribute: ", "webController", attributes.getString(VALUE));
		assertEquals(Component.class, attributes.annotationType());
	}

	@Test
	public void getAnnotationAttributesWithAttributeAliases() throws Exception {
		Method method = WebController.class.getMethod("handleMappedWithValueAttribute");
		WebMapping webMapping = method.getAnnotation(WebMapping.class);
		AnnotationAttributes attributes = (AnnotationAttributes) getAnnotationAttributes(webMapping);
		assertNotNull(attributes);
		assertEquals(WebMapping.class, attributes.annotationType());
		assertEquals("name attribute: ", "foo", attributes.getString("name"));
		assertEquals("value attribute: ", "/test", attributes.getString(VALUE));
		assertEquals("path attribute: ", "/test", attributes.getString("path"));

		method = WebController.class.getMethod("handleMappedWithPathAttribute");
		webMapping = method.getAnnotation(WebMapping.class);
		attributes = (AnnotationAttributes) getAnnotationAttributes(webMapping);
		assertNotNull(attributes);
		assertEquals(WebMapping.class, attributes.annotationType());
		assertEquals("name attribute: ", "bar", attributes.getString("name"));
		assertEquals("value attribute: ", "/test", attributes.getString(VALUE));
		assertEquals("path attribute: ", "/test", attributes.getString("path"));

		method = WebController.class.getMethod("handleMappedWithPathValueAndAttributes");
		webMapping = method.getAnnotation(WebMapping.class);
		exception.expect(AnnotationConfigurationException.class);
		exception.expectMessage(containsString("attribute [value] and its alias [path]"));
		exception.expectMessage(containsString("values of [/enigma] and [/test]"));
		exception.expectMessage(containsString("but only one declaration is permitted"));
		getAnnotationAttributes(webMapping);
	}

	@Test
	public void getValueFromAnnotation() throws Exception {
		Method method = SimpleFoo.class.getMethod("something", Object.class);
		Order order = findAnnotation(method, Order.class);

		assertEquals(1, getValue(order, VALUE));
		assertEquals(1, getValue(order));
	}

	@Test
	public void getValueFromNonPublicAnnotation() throws Exception {
		Annotation[] declaredAnnotations = NonPublicAnnotatedClass.class.getDeclaredAnnotations();
		assertEquals(1, declaredAnnotations.length);
		Annotation annotation = declaredAnnotations[0];
		assertNotNull(annotation);
		assertEquals("NonPublicAnnotation", annotation.annotationType().getSimpleName());
		assertEquals(42, getValue(annotation, VALUE));
		assertEquals(42, getValue(annotation));
	}

	@Test
	public void getDefaultValueFromAnnotation() throws Exception {
		Method method = SimpleFoo.class.getMethod("something", Object.class);
		Order order = findAnnotation(method, Order.class);

		assertEquals(Ordered.LOWEST_PRECEDENCE, getDefaultValue(order, VALUE));
		assertEquals(Ordered.LOWEST_PRECEDENCE, getDefaultValue(order));
	}

	@Test
	public void getDefaultValueFromNonPublicAnnotation() throws Exception {
		Annotation[] declaredAnnotations = NonPublicAnnotatedClass.class.getDeclaredAnnotations();
		assertEquals(1, declaredAnnotations.length);
		Annotation annotation = declaredAnnotations[0];
		assertNotNull(annotation);
		assertEquals("NonPublicAnnotation", annotation.annotationType().getSimpleName());
		assertEquals(-1, getDefaultValue(annotation, VALUE));
		assertEquals(-1, getDefaultValue(annotation));
	}

	@Test
	public void getDefaultValueFromAnnotationType() throws Exception {
		assertEquals(Ordered.LOWEST_PRECEDENCE, getDefaultValue(Order.class, VALUE));
		assertEquals(Ordered.LOWEST_PRECEDENCE, getDefaultValue(Order.class));
	}

	@Test
	public void findRepeatableAnnotationOnComposedAnnotation() {
		Repeatable repeatable = findAnnotation(MyRepeatableMeta.class, Repeatable.class);
		assertNotNull(repeatable);
		assertEquals(MyRepeatableContainer.class, repeatable.value());
	}

	@Test
	public void getRepeatableFromMethod() throws Exception {
		Method method = InterfaceWithRepeated.class.getMethod("foo");
		Set<MyRepeatable> annotations = getRepeatableAnnotation(method, MyRepeatableContainer.class, MyRepeatable.class);
		assertNotNull(annotations);
		List<String> values = annotations.stream().map(MyRepeatable::value).collect(toList());
		assertThat(values, equalTo(Arrays.asList("a", "b", "c", "meta")));
	}

	@Test
	public void getRepeatableWithAttributeAliases() throws Exception {
		Set<ContextConfig> annotations = getRepeatableAnnotation(TestCase.class, Hierarchy.class, ContextConfig.class);
		assertNotNull(annotations);

		List<String> locations = annotations.stream().map(ContextConfig::locations).collect(toList());
		assertThat(locations, equalTo(Arrays.asList("A", "B")));

		List<String> values = annotations.stream().map(ContextConfig::value).collect(toList());
		assertThat(values, equalTo(Arrays.asList("A", "B")));
	}

	@Test
	public void getAliasedAttributeNameFromAliasedComposedAnnotation() throws Exception {
		Method attribute = AliasedComposedContextConfig.class.getDeclaredMethod("xmlConfigFile");
		assertEquals("locations", getAliasedAttributeName(attribute, ContextConfig.class));
	}

	@Test
	public void synthesizeAnnotationWithoutAttributeAliases() throws Exception {
		Component component = findAnnotation(WebController.class, Component.class);
		assertNotNull(component);
		Component synthesizedComponent = synthesizeAnnotation(component);
		assertNotNull(synthesizedComponent);
		assertSame(component, synthesizedComponent);
		assertEquals("value attribute: ", "webController", synthesizedComponent.value());
	}

	@Test
	public void synthesizeAnnotationWithAttributeAliasForNonexistentAttribute() throws Exception {
		AliasForNonexistentAttribute annotation = AliasForNonexistentAttributeClass.class.getAnnotation(AliasForNonexistentAttribute.class);
		exception.expect(AnnotationConfigurationException.class);
		exception.expectMessage(containsString("Attribute [foo] in"));
		exception.expectMessage(containsString(AliasForNonexistentAttribute.class.getName()));
		exception.expectMessage(containsString("is declared as an @AliasFor nonexistent attribute [bar]"));
		synthesizeAnnotation(annotation);
	}

	@Test
	public void synthesizeAnnotationWithAttributeAliasWithoutMirroredAliasFor() throws Exception {
		AliasForWithoutMirroredAliasFor annotation = AliasForWithoutMirroredAliasForClass.class.getAnnotation(AliasForWithoutMirroredAliasFor.class);
		exception.expect(AnnotationConfigurationException.class);
		exception.expectMessage(containsString("Attribute [bar] in"));
		exception.expectMessage(containsString(AliasForWithoutMirroredAliasFor.class.getName()));
		exception.expectMessage(containsString("must be declared as an @AliasFor [foo]"));
		synthesizeAnnotation(annotation);
	}

	@Test
	public void synthesizeAnnotationWithAttributeAliasWithMirroredAliasForWrongAttribute() throws Exception {
		AliasForWithMirroredAliasForWrongAttribute annotation = AliasForWithMirroredAliasForWrongAttributeClass.class.getAnnotation(AliasForWithMirroredAliasForWrongAttribute.class);

		// Since JDK 7+ does not guarantee consistent ordering of methods returned using
		// reflection, we cannot make the test dependent on any specific ordering.
		//
		// In other words, we can't be certain which type of exception message we'll get,
		// so we allow for both possibilities.
		exception.expect(AnnotationConfigurationException.class);
		exception.expectMessage(containsString("Attribute [bar] in"));
		exception.expectMessage(containsString(AliasForWithMirroredAliasForWrongAttribute.class.getName()));
		exception.expectMessage(either(containsString("must be declared as an @AliasFor [foo], not [quux]")).
			or(containsString("is declared as an @AliasFor nonexistent attribute [quux]")));
		synthesizeAnnotation(annotation);
	}

	@Test
	public void synthesizeAnnotationWithAttributeAliasForAttributeOfDifferentType() throws Exception {
		AliasForAttributeOfDifferentType annotation = AliasForAttributeOfDifferentTypeClass.class.getAnnotation(AliasForAttributeOfDifferentType.class);
		exception.expect(AnnotationConfigurationException.class);
		exception.expectMessage(startsWith("Misconfigured aliases"));
		exception.expectMessage(containsString(AliasForAttributeOfDifferentType.class.getName()));
		// Since JDK 7+ does not guarantee consistent ordering of methods returned using
		// reflection, we cannot make the test dependent on any specific ordering.
		//
		// In other words, we don't know if "foo" or "bar" will come first.
		exception.expectMessage(containsString("attribute [foo]"));
		exception.expectMessage(containsString("attribute [bar]"));
		exception.expectMessage(containsString("must declare the same return type"));
		synthesizeAnnotation(annotation);
	}

	@Test
	public void synthesizeAnnotationWithAttributeAliasForWithMissingDefaultValues() throws Exception {
		AliasForWithMissingDefaultValues annotation = AliasForWithMissingDefaultValuesClass.class.getAnnotation(AliasForWithMissingDefaultValues.class);
		exception.expectMessage(startsWith("Misconfigured aliases"));
		exception.expectMessage(containsString(AliasForWithMissingDefaultValues.class.getName()));
		// Since JDK 7+ does not guarantee consistent ordering of methods returned using
		// reflection, we cannot make the test dependent on any specific ordering.
		//
		// In other words, we don't know if "foo" or "bar" will come first.
		exception.expectMessage(containsString("attribute [foo]"));
		exception.expectMessage(containsString("attribute [bar]"));
		exception.expectMessage(containsString("must declare default values"));
		synthesizeAnnotation(annotation);
	}

	@Test
	public void synthesizeAnnotationWithAttributeAliasForAttributeWithDifferentDefaultValue() throws Exception {
		AliasForAttributeWithDifferentDefaultValue annotation = AliasForAttributeWithDifferentDefaultValueClass.class.getAnnotation(AliasForAttributeWithDifferentDefaultValue.class);
		exception.expectMessage(startsWith("Misconfigured aliases"));
		exception.expectMessage(containsString(AliasForAttributeWithDifferentDefaultValue.class.getName()));
		// Since JDK 7+ does not guarantee consistent ordering of methods returned using
		// reflection, we cannot make the test dependent on any specific ordering.
		//
		// In other words, we don't know if "foo" or "bar" will come first.
		exception.expectMessage(containsString("attribute [foo]"));
		exception.expectMessage(containsString("attribute [bar]"));
		exception.expectMessage(containsString("must declare the same default value"));
		synthesizeAnnotation(annotation);
	}

	@Test
	public void synthesizeAnnotationWithAttributeAliases() throws Exception {
		Method method = WebController.class.getMethod("handleMappedWithValueAttribute");
		WebMapping webMapping = method.getAnnotation(WebMapping.class);
		assertNotNull(webMapping);
		WebMapping synthesizedWebMapping = synthesizeAnnotation(webMapping);
		assertNotSame(webMapping, synthesizedWebMapping);
		assertThat(synthesizedWebMapping, instanceOf(SynthesizedAnnotation.class));

		assertNotNull(synthesizedWebMapping);
		assertEquals("name attribute: ", "foo", synthesizedWebMapping.name());
		assertEquals("aliased path attribute: ", "/test", synthesizedWebMapping.path());
		assertEquals("actual value attribute: ", "/test", synthesizedWebMapping.value());
	}

	/**
	 * Fully reflection-based test that verifies support for
	 * {@linkplain AnnotationUtils#synthesizeAnnotation synthesizing annotations}
	 * across packages with non-public visibility of user types (e.g., a non-public
	 * annotation that uses {@code @AliasFor}).
	 */
	@Test
	@SuppressWarnings("unchecked")
	public void synthesizeNonPublicAnnotationWithAttributeAliasesFromDifferentPackage() throws Exception {

		Class<?> clazz =
			ClassUtils.forName("org.springframework.core.annotation.subpackage.NonPublicAliasedAnnotatedClass", null);
		Class<? extends Annotation> annotationType = (Class<? extends Annotation>)
			ClassUtils.forName("org.springframework.core.annotation.subpackage.NonPublicAliasedAnnotation", null);

		Annotation annotation = clazz.getAnnotation(annotationType);
		assertNotNull(annotation);
		Annotation synthesizedAnnotation = synthesizeAnnotation(annotation);
		assertNotSame(annotation, synthesizedAnnotation);

		assertNotNull(synthesizedAnnotation);
		assertEquals("name attribute: ", "test", getValue(synthesizedAnnotation, "name"));
		assertEquals("aliased path attribute: ", "/test", getValue(synthesizedAnnotation, "path"));
		assertEquals("aliased path attribute: ", "/test", getValue(synthesizedAnnotation, "value"));
	}

	@Test
	public void synthesizeAnnotationWithAttributeAliasesInNestedAnnotations() throws Exception {
		Hierarchy hierarchy = TestCase.class.getAnnotation(Hierarchy.class);
		assertNotNull(hierarchy);
		Hierarchy synthesizedHierarchy = synthesizeAnnotation(hierarchy);
		assertNotSame(hierarchy, synthesizedHierarchy);
		assertThat(synthesizedHierarchy, instanceOf(SynthesizedAnnotation.class));

		ContextConfig[] configs = synthesizedHierarchy.value();
		assertNotNull(configs);
		assertTrue("nested annotations must be synthesized",
			Arrays.stream(configs).allMatch(c -> c instanceof SynthesizedAnnotation));

		List<String> locations = Arrays.stream(configs).map(ContextConfig::locations).collect(toList());
		assertThat(locations, equalTo(Arrays.asList("A", "B")));

		List<String> values = Arrays.stream(configs).map(ContextConfig::value).collect(toList());
		assertThat(values, equalTo(Arrays.asList("A", "B")));
	}

	@Test
	public void synthesizeAlreadySynthesizedAnnotation() throws Exception {
		Method method = WebController.class.getMethod("handleMappedWithValueAttribute");
		WebMapping webMapping = method.getAnnotation(WebMapping.class);
		assertNotNull(webMapping);
		WebMapping synthesizedWebMapping = synthesizeAnnotation(webMapping);
		assertNotSame(webMapping, synthesizedWebMapping);
		WebMapping synthesizedAgainWebMapping = synthesizeAnnotation(synthesizedWebMapping);
		assertSame(synthesizedWebMapping, synthesizedAgainWebMapping);
		assertThat(synthesizedAgainWebMapping, instanceOf(SynthesizedAnnotation.class));

		assertNotNull(synthesizedAgainWebMapping);
		assertEquals("name attribute: ", "foo", synthesizedAgainWebMapping.name());
		assertEquals("aliased path attribute: ", "/test", synthesizedAgainWebMapping.path());
		assertEquals("actual value attribute: ", "/test", synthesizedAgainWebMapping.value());
	}


	@Component(value = "meta1")
	@Order
	@Retention(RetentionPolicy.RUNTIME)
	@Inherited
	@interface Meta1 {
	}

	@Component(value = "meta2")
	@Transactional(readOnly = true)
	@Retention(RetentionPolicy.RUNTIME)
	@interface Meta2 {
	}

	@Meta2
	@Retention(RetentionPolicy.RUNTIME)
	@interface MetaMeta {
	}

	@MetaMeta
	@Retention(RetentionPolicy.RUNTIME)
	@interface MetaMetaMeta {
	}

	@MetaCycle3
	@Retention(RetentionPolicy.RUNTIME)
	@interface MetaCycle1 {
	}

	@MetaCycle1
	@Retention(RetentionPolicy.RUNTIME)
	@interface MetaCycle2 {
	}

	@MetaCycle2
	@Retention(RetentionPolicy.RUNTIME)
	@interface MetaCycle3 {
	}

	@Meta1
	interface InterfaceWithMetaAnnotation {
	}

	@Meta2
	static class ClassWithLocalMetaAnnotationAndMetaAnnotatedInterface implements InterfaceWithMetaAnnotation {
	}

	@Meta1
	static class ClassWithInheritedMetaAnnotation {
	}

	@Meta2
	static class SubClassWithInheritedMetaAnnotation extends ClassWithInheritedMetaAnnotation {
	}

	static class SubSubClassWithInheritedMetaAnnotation extends SubClassWithInheritedMetaAnnotation {
	}

	@Transactional
	static class ClassWithInheritedAnnotation {
	}

	@Meta2
	static class SubClassWithInheritedAnnotation extends ClassWithInheritedAnnotation {
	}

	static class SubSubClassWithInheritedAnnotation extends SubClassWithInheritedAnnotation {
	}

	@MetaMeta
	static class MetaMetaAnnotatedClass {
	}

	@MetaMetaMeta
	static class MetaMetaMetaAnnotatedClass {
	}

	@MetaCycle3
	static class MetaCycleAnnotatedClass {
	}

	public interface AnnotatedInterface {

		@Order(0)
		void fromInterfaceImplementedByRoot();
	}

	public static class Root implements AnnotatedInterface {

		@Order(27)
		public void annotatedOnRoot() {
		}

		@Meta1
		public void metaAnnotatedOnRoot() {
		}

		public void overrideToAnnotate() {
		}

		@Order(27)
		public void overrideWithoutNewAnnotation() {
		}

		public void notAnnotated() {
		}

		@Override
		public void fromInterfaceImplementedByRoot() {
		}
	}

	public static class Leaf extends Root {

		@Order(25)
		public void annotatedOnLeaf() {
		}

		@Meta1
		public void metaAnnotatedOnLeaf() {
		}

		@MetaMeta
		public void metaMetaAnnotatedOnLeaf() {
		}

		@Override
		@Order(1)
		public void overrideToAnnotate() {
		}

		@Override
		public void overrideWithoutNewAnnotation() {
		}
	}

	@Retention(RetentionPolicy.RUNTIME)
	@Inherited
	@interface Transactional {

		boolean readOnly() default false;
	}

	public static abstract class Foo<T> {

		@Order(1)
		public abstract void something(T arg);
	}

	public static class SimpleFoo extends Foo<String> {

		@Override
		@Transactional
		public void something(final String arg) {
		}
	}

	@Transactional
	public interface InheritedAnnotationInterface {
	}

	public interface SubInheritedAnnotationInterface extends InheritedAnnotationInterface {
	}

	public interface SubSubInheritedAnnotationInterface extends SubInheritedAnnotationInterface {
	}

	@Order
	public interface NonInheritedAnnotationInterface {
	}

	public interface SubNonInheritedAnnotationInterface extends NonInheritedAnnotationInterface {
	}

	public interface SubSubNonInheritedAnnotationInterface extends SubNonInheritedAnnotationInterface {
	}

	public static class NonAnnotatedClass {
	}

	public interface NonAnnotatedInterface {
	}

	@Transactional
	public static class InheritedAnnotationClass {
	}

	public static class SubInheritedAnnotationClass extends InheritedAnnotationClass {
	}

	@Order
	public static class NonInheritedAnnotationClass {
	}

	public static class SubNonInheritedAnnotationClass extends NonInheritedAnnotationClass {
	}

	@Transactional
	public static class TransactionalClass {
	}

	@Order
	public static class TransactionalAndOrderedClass extends TransactionalClass {
	}

	public static class SubTransactionalAndOrderedClass extends TransactionalAndOrderedClass {
	}

	public interface InterfaceWithAnnotatedMethod {

		@Order
		void foo();
	}

	public static class ImplementsInterfaceWithAnnotatedMethod implements InterfaceWithAnnotatedMethod {

		@Override
		public void foo() {
		}
	}

	public static class SubOfImplementsInterfaceWithAnnotatedMethod extends ImplementsInterfaceWithAnnotatedMethod {

		@Override
		public void foo() {
		}
	}

	public abstract static class AbstractDoesNotImplementInterfaceWithAnnotatedMethod
			implements InterfaceWithAnnotatedMethod {
	}

	public static class SubOfAbstractImplementsInterfaceWithAnnotatedMethod
			extends AbstractDoesNotImplementInterfaceWithAnnotatedMethod {

		@Override
		public void foo() {
		}
	}

	@Retention(RetentionPolicy.RUNTIME)
	@Inherited
	@interface MyRepeatableContainer {

		MyRepeatable[] value();
	}

	@Retention(RetentionPolicy.RUNTIME)
	@Inherited
	@Repeatable(MyRepeatableContainer.class)
	@interface MyRepeatable {

		String value();
	}

	@Retention(RetentionPolicy.RUNTIME)
	@Inherited
	@MyRepeatable("meta")
	@interface MyRepeatableMeta {
	}

	public interface InterfaceWithRepeated {

		@MyRepeatable("a")
		@MyRepeatableContainer({ @MyRepeatable("b"), @MyRepeatable("c") })
		@MyRepeatableMeta
		void foo();
	}

	/**
	 * Mock of {@code org.springframework.web.bind.annotation.RequestMapping}.
	 */
	@Retention(RetentionPolicy.RUNTIME)
	@interface WebMapping {

		String name();

		@AliasFor(attribute = "path")
		String value() default "";

		@AliasFor(attribute = "value")
		String path() default "";
	}

	@Component("webController")
	static class WebController {

		@WebMapping(value = "/test", name = "foo")
		public void handleMappedWithValueAttribute() {
		}

		@WebMapping(path = "/test", name = "bar")
		public void handleMappedWithPathAttribute() {
		}

		@WebMapping(value = "/enigma", path = "/test", name = "baz")
		public void handleMappedWithPathValueAndAttributes() {
		}
	}

	/**
	 * Mock of {@code org.springframework.test.context.ContextConfiguration}.
	 */
	@Retention(RetentionPolicy.RUNTIME)
	@interface ContextConfig {

		@AliasFor(attribute = "locations")
		String value() default "";

		@AliasFor(attribute = "value")
		String locations() default "";
	}

	/**
	 * Mock of {@code org.springframework.test.context.ContextHierarchy}.
	 */
	@Retention(RetentionPolicy.RUNTIME)
	@interface Hierarchy {

		ContextConfig[] value();
	}

	@Hierarchy({ @ContextConfig("A"), @ContextConfig(locations = "B") })
	static class TestCase {
	}

	@Retention(RetentionPolicy.RUNTIME)
	@interface AliasForNonexistentAttribute {

		@AliasFor(attribute = "bar")
		String foo() default "";
	}

	@AliasForNonexistentAttribute
	static class AliasForNonexistentAttributeClass {
	}

	@Retention(RetentionPolicy.RUNTIME)
	@interface AliasForWithoutMirroredAliasFor {

		@AliasFor(attribute = "bar")
		String foo() default "";

		String bar() default "";
	}

	@AliasForWithoutMirroredAliasFor
	static class AliasForWithoutMirroredAliasForClass {
	}

	@Retention(RetentionPolicy.RUNTIME)
	@interface AliasForWithMirroredAliasForWrongAttribute {

		@AliasFor(attribute = "bar")
		String[] foo() default "";

		@AliasFor(attribute = "quux")
		String[] bar() default "";
	}

	@AliasForWithMirroredAliasForWrongAttribute
	static class AliasForWithMirroredAliasForWrongAttributeClass {
	}

	@Retention(RetentionPolicy.RUNTIME)
	@interface AliasForAttributeOfDifferentType {

		@AliasFor(attribute = "bar")
		String[] foo() default "";

		@AliasFor(attribute = "foo")
		boolean bar() default true;
	}

	@AliasForAttributeOfDifferentType
	static class AliasForAttributeOfDifferentTypeClass {
	}

	@Retention(RetentionPolicy.RUNTIME)
	@interface AliasForWithMissingDefaultValues {

		@AliasFor(attribute = "bar")
		String foo();

		@AliasFor(attribute = "foo")
		String bar();
	}

	@AliasForWithMissingDefaultValues(foo = "foo", bar = "bar")
	static class AliasForWithMissingDefaultValuesClass {
	}

	@Retention(RetentionPolicy.RUNTIME)
	@interface AliasForAttributeWithDifferentDefaultValue {

		@AliasFor(attribute = "bar")
		String foo() default "X";

		@AliasFor(attribute = "foo")
		String bar() default "Z";
	}

	@AliasForAttributeWithDifferentDefaultValue
	static class AliasForAttributeWithDifferentDefaultValueClass {
	}

	@ContextConfig
	@Retention(RetentionPolicy.RUNTIME)
	@interface AliasedComposedContextConfig {

		@AliasFor(annotation = ContextConfig.class, attribute = "locations")
		String xmlConfigFile();
	}

}
