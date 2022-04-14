/*
 * Copyright 2002-2022 the original author or authors.
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

package org.springframework.beans.factory.aot;

import java.lang.annotation.Annotation;
import java.lang.reflect.Executable;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.beans.testfixture.beans.factory.generator.factory.NumberHolder;
import org.springframework.beans.testfixture.beans.factory.generator.factory.NumberHolderFactoryBean;
import org.springframework.beans.testfixture.beans.factory.generator.factory.SampleFactory;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.MergedAnnotations.SearchStrategy;
import org.springframework.lang.Nullable;
import org.springframework.util.ReflectionUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/**
 * Tests for {@link ConstructorOrFactoryMethodResolver}.
 *
 * @author Stephane Nicoll
 * @author Phillip Webb
 */
class ConstructorOrFactoryMethodResolverTests {

	@Test
	void detectBeanInstanceExecutableWithBeanClassAndFactoryMethodName() {
		DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
		beanFactory.registerSingleton("testBean", "test");
		BeanDefinition beanDefinition = BeanDefinitionBuilder
				.rootBeanDefinition(SampleFactory.class).setFactoryMethod("create")
				.addConstructorArgReference("testBean").getBeanDefinition();
		Executable executable = resolve(beanFactory, beanDefinition);
		assertThat(executable).isNotNull().isEqualTo(
				ReflectionUtils.findMethod(SampleFactory.class, "create", String.class));
	}

	@Test
	void detectBeanInstanceExecutableWithBeanClassNameAndFactoryMethodName() {
		DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
		beanFactory.registerSingleton("testBean", "test");
		BeanDefinition beanDefinition = BeanDefinitionBuilder
				.rootBeanDefinition(SampleFactory.class.getName())
				.setFactoryMethod("create").addConstructorArgReference("testBean")
				.getBeanDefinition();
		Executable executable = resolve(beanFactory, beanDefinition);
		assertThat(executable).isNotNull().isEqualTo(
				ReflectionUtils.findMethod(SampleFactory.class, "create", String.class));
	}

	@Test
	void beanDefinitionWithFactoryMethodNameAndAssignableConstructorArg() {
		DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
		beanFactory.registerSingleton("testNumber", 1L);
		beanFactory.registerSingleton("testBean", "test");
		BeanDefinition beanDefinition = BeanDefinitionBuilder
				.rootBeanDefinition(SampleFactory.class).setFactoryMethod("create")
				.addConstructorArgReference("testNumber")
				.addConstructorArgReference("testBean").getBeanDefinition();
		Executable executable = resolve(beanFactory, beanDefinition);
		assertThat(executable).isNotNull().isEqualTo(ReflectionUtils
				.findMethod(SampleFactory.class, "create", Number.class, String.class));
	}

	@Test
	void beanDefinitionWithFactoryMethodNameAndMatchingMethodNamesThatShouldBeIgnored() {
		DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
		BeanDefinition beanDefinition = BeanDefinitionBuilder
				.rootBeanDefinition(DummySampleFactory.class).setFactoryMethod("of")
				.addConstructorArgValue(42).getBeanDefinition();
		Executable executable = resolve(beanFactory, beanDefinition);
		assertThat(executable).isNotNull().isEqualTo(ReflectionUtils
				.findMethod(DummySampleFactory.class, "of", Integer.class));
	}

	@Test
	void detectBeanInstanceExecutableWithBeanClassAndFactoryMethodNameIgnoreTargetType() {
		DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
		beanFactory.registerSingleton("testBean", "test");
		RootBeanDefinition beanDefinition = (RootBeanDefinition) BeanDefinitionBuilder
				.rootBeanDefinition(SampleFactory.class).setFactoryMethod("create")
				.addConstructorArgReference("testBean").getBeanDefinition();
		beanDefinition.setTargetType(String.class);
		Executable executable = resolve(beanFactory, beanDefinition);
		assertThat(executable).isNotNull().isEqualTo(
				ReflectionUtils.findMethod(SampleFactory.class, "create", String.class));
	}

	@Test
	void beanDefinitionWithConstructorArgsForMultipleConstructors() throws Exception {
		DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
		beanFactory.registerSingleton("testNumber", 1L);
		beanFactory.registerSingleton("testBean", "test");
		BeanDefinition beanDefinition = BeanDefinitionBuilder
				.rootBeanDefinition(SampleBeanWithConstructors.class)
				.addConstructorArgReference("testNumber")
				.addConstructorArgReference("testBean").getBeanDefinition();
		Executable executable = resolve(beanFactory, beanDefinition);
		assertThat(executable).isNotNull().isEqualTo(SampleBeanWithConstructors.class
				.getDeclaredConstructor(Number.class, String.class));
	}

	@Test
	void genericBeanDefinitionWithConstructorArgsForMultipleConstructors()
			throws Exception {
		DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
		beanFactory.registerSingleton("testNumber", 1L);
		beanFactory.registerSingleton("testBean", "test");
		BeanDefinition beanDefinition = BeanDefinitionBuilder
				.genericBeanDefinition(SampleBeanWithConstructors.class)
				.addConstructorArgReference("testNumber")
				.addConstructorArgReference("testBean").getBeanDefinition();
		Executable executable = resolve(beanFactory, beanDefinition);
		assertThat(executable).isNotNull().isEqualTo(SampleBeanWithConstructors.class
				.getDeclaredConstructor(Number.class, String.class));
	}

	@Test
	void beanDefinitionWithMultiArgConstructorAndMatchingValue()
			throws NoSuchMethodException {
		BeanDefinition beanDefinition = BeanDefinitionBuilder
				.rootBeanDefinition(MultiConstructorSample.class)
				.addConstructorArgValue(42).getBeanDefinition();
		Executable executable = resolve(new DefaultListableBeanFactory(), beanDefinition);
		assertThat(executable).isNotNull().isEqualTo(
				MultiConstructorSample.class.getDeclaredConstructor(Integer.class));
	}

	@Test
	void beanDefinitionWithMultiArgConstructorAndMatchingArrayValue()
			throws NoSuchMethodException {
		BeanDefinition beanDefinition = BeanDefinitionBuilder
				.rootBeanDefinition(MultiConstructorArraySample.class)
				.addConstructorArgValue(42).getBeanDefinition();
		Executable executable = resolve(new DefaultListableBeanFactory(), beanDefinition);
		assertThat(executable).isNotNull().isEqualTo(MultiConstructorArraySample.class
				.getDeclaredConstructor(Integer[].class));
	}

	@Test
	void beanDefinitionWithMultiArgConstructorAndMatchingListValue()
			throws NoSuchMethodException {
		BeanDefinition beanDefinition = BeanDefinitionBuilder
				.rootBeanDefinition(MultiConstructorListSample.class)
				.addConstructorArgValue(42).getBeanDefinition();
		Executable executable = resolve(new DefaultListableBeanFactory(), beanDefinition);
		assertThat(executable).isNotNull().isEqualTo(
				MultiConstructorListSample.class.getDeclaredConstructor(List.class));
	}

	@Test
	void beanDefinitionWithMultiArgConstructorAndMatchingValueAsInnerBean()
			throws NoSuchMethodException {
		BeanDefinition beanDefinition = BeanDefinitionBuilder
				.rootBeanDefinition(MultiConstructorSample.class)
				.addConstructorArgValue(
						BeanDefinitionBuilder.rootBeanDefinition(Integer.class, "valueOf")
								.addConstructorArgValue("42").getBeanDefinition())
				.getBeanDefinition();
		Executable executable = resolve(new DefaultListableBeanFactory(), beanDefinition);
		assertThat(executable).isNotNull().isEqualTo(
				MultiConstructorSample.class.getDeclaredConstructor(Integer.class));
	}

	@Test
	void beanDefinitionWithMultiArgConstructorAndMatchingValueAsInnerBeanFactory()
			throws NoSuchMethodException {
		BeanDefinition beanDefinition = BeanDefinitionBuilder
				.rootBeanDefinition(MultiConstructorSample.class)
				.addConstructorArgValue(BeanDefinitionBuilder
						.rootBeanDefinition(IntegerFactoryBean.class).getBeanDefinition())
				.getBeanDefinition();
		Executable executable = resolve(new DefaultListableBeanFactory(), beanDefinition);
		assertThat(executable).isNotNull().isEqualTo(
				MultiConstructorSample.class.getDeclaredConstructor(Integer.class));
	}

	@Test
	void beanDefinitionWithMultiArgConstructorAndNonMatchingValue() {
		BeanDefinition beanDefinition = BeanDefinitionBuilder
				.rootBeanDefinition(MultiConstructorSample.class)
				.addConstructorArgValue(Locale.ENGLISH).getBeanDefinition();
		Executable executable = resolve(new DefaultListableBeanFactory(), beanDefinition);
		assertThat(executable).isNull();
	}

	@Test
	void beanDefinitionWithMultiArgConstructorAndNonMatchingValueAsInnerBean() {
		BeanDefinition beanDefinition = BeanDefinitionBuilder
				.rootBeanDefinition(MultiConstructorSample.class)
				.addConstructorArgValue(BeanDefinitionBuilder
						.rootBeanDefinition(Locale.class, "getDefault")
						.getBeanDefinition())
				.getBeanDefinition();
		Executable executable = resolve(new DefaultListableBeanFactory(), beanDefinition);
		assertThat(executable).isNull();
	}

	@Test
	void detectBeanInstanceExecutableWithFactoryBeanSetInBeanClass() {
		DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
		RootBeanDefinition beanDefinition = new RootBeanDefinition();
		beanDefinition.setTargetType(
				ResolvableType.forClassWithGenerics(NumberHolder.class, Integer.class));
		beanDefinition.setBeanClass(NumberHolderFactoryBean.class);
		Executable executable = resolve(beanFactory, beanDefinition);
		assertThat(executable).isNotNull()
				.isEqualTo(NumberHolderFactoryBean.class.getDeclaredConstructors()[0]);
	}

	@Test
	void detectBeanInstanceExecutableWithFactoryBeanSetInBeanClassAndNoResolvableType() {
		DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
		RootBeanDefinition beanDefinition = new RootBeanDefinition();
		beanDefinition.setBeanClass(NumberHolderFactoryBean.class);
		Executable executable = resolve(beanFactory, beanDefinition);
		assertThat(executable).isNotNull()
				.isEqualTo(NumberHolderFactoryBean.class.getDeclaredConstructors()[0]);
	}

	@Test
	void detectBeanInstanceExecutableWithFactoryBeanSetInBeanClassThatDoesNotMatchTargetType() {
		DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
		RootBeanDefinition beanDefinition = new RootBeanDefinition();
		beanDefinition.setTargetType(
				ResolvableType.forClassWithGenerics(NumberHolder.class, String.class));
		beanDefinition.setBeanClass(NumberHolderFactoryBean.class);
		assertThatIllegalStateException()
				.isThrownBy(() -> resolve(beanFactory, beanDefinition))
				.withMessageContaining("Incompatible target type")
				.withMessageContaining(NumberHolder.class.getName())
				.withMessageContaining(NumberHolderFactoryBean.class.getName());
	}

	@Test
	void beanDefinitionWithClassArrayConstructorArgAndStringArrayValueType()
			throws NoSuchMethodException {
		DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
		BeanDefinition beanDefinition = BeanDefinitionBuilder
				.rootBeanDefinition(ConstructorClassArraySample.class.getName())
				.addConstructorArgValue(new String[] { "test1, test2" })
				.getBeanDefinition();
		Executable executable = resolve(beanFactory, beanDefinition);
		assertThat(executable).isNotNull().isEqualTo(
				ConstructorClassArraySample.class.getDeclaredConstructor(Class[].class));
	}

	@Test
	void beanDefinitionWithClassArrayConstructorArgAndStringValueType() {
		DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
		BeanDefinition beanDefinition = BeanDefinitionBuilder
				.rootBeanDefinition(ConstructorClassArraySample.class.getName())
				.addConstructorArgValue("test1").getBeanDefinition();
		Executable executable = resolve(beanFactory, beanDefinition);
		assertThat(executable).isNotNull().isEqualTo(
				ConstructorClassArraySample.class.getDeclaredConstructors()[0]);
	}

	@Test
	void beanDefinitionWithClassArrayConstructorArgAndAnotherMatchingConstructor()
			throws NoSuchMethodException {
		DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
		BeanDefinition beanDefinition = BeanDefinitionBuilder
				.rootBeanDefinition(MultiConstructorClassArraySample.class.getName())
				.addConstructorArgValue(new String[] { "test1, test2" })
				.getBeanDefinition();
		Executable executable = resolve(beanFactory, beanDefinition);
		assertThat(executable).isNotNull()
				.isEqualTo(MultiConstructorClassArraySample.class
						.getDeclaredConstructor(String[].class));
	}

	@Test
	void beanDefinitionWithClassArrayFactoryMethodArgAndStringArrayValueType() {
		DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
		BeanDefinition beanDefinition = BeanDefinitionBuilder
				.rootBeanDefinition(ClassArrayFactoryMethodSample.class.getName())
				.setFactoryMethod("of")
				.addConstructorArgValue(new String[] { "test1, test2" })
				.getBeanDefinition();
		Executable executable = resolve(beanFactory, beanDefinition);
		assertThat(executable).isNotNull().isEqualTo(ReflectionUtils
				.findMethod(ClassArrayFactoryMethodSample.class, "of", Class[].class));
	}

	@Test
	void beanDefinitionWithClassArrayFactoryMethodArgAndAnotherMatchingConstructor() {
		DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
		BeanDefinition beanDefinition = BeanDefinitionBuilder.rootBeanDefinition(
				ClassArrayFactoryMethodSampleWithAnotherFactoryMethod.class.getName())
				.setFactoryMethod("of").addConstructorArgValue("test1")
				.getBeanDefinition();
		Executable executable = resolve(beanFactory, beanDefinition);
		assertThat(executable).isNotNull()
				.isEqualTo(ReflectionUtils.findMethod(
						ClassArrayFactoryMethodSampleWithAnotherFactoryMethod.class, "of",
						String[].class));
	}

	@Test
	void beanDefinitionWithMultiArgConstructorAndPrimitiveConversion()
			throws NoSuchMethodException {
		BeanDefinition beanDefinition = BeanDefinitionBuilder
				.rootBeanDefinition(ConstructorPrimitiveFallback.class)
				.addConstructorArgValue("true").getBeanDefinition();
		Executable executable = resolve(new DefaultListableBeanFactory(), beanDefinition);
		assertThat(executable).isEqualTo(
				ConstructorPrimitiveFallback.class.getDeclaredConstructor(boolean.class));
	}

	@Test
	void beanDefinitionWithFactoryWithOverloadedClassMethodsOnInterface() {
		BeanDefinition beanDefinition = BeanDefinitionBuilder
				.rootBeanDefinition(FactoryWithOverloadedClassMethodsOnInterface.class)
				.setFactoryMethod("byAnnotation").addConstructorArgValue(Nullable.class)
				.getBeanDefinition();
		Executable executable = resolve(new DefaultListableBeanFactory(), beanDefinition);
		assertThat(executable).isEqualTo(ReflectionUtils.findMethod(
				FactoryWithOverloadedClassMethodsOnInterface.class, "byAnnotation",
				Class.class));
	}

	private Executable resolve(DefaultListableBeanFactory beanFactory,
			BeanDefinition beanDefinition) {
		return new ConstructorOrFactoryMethodResolver(beanFactory)
				.resolve(beanDefinition);
	}

	static class IntegerFactoryBean implements FactoryBean<Integer> {

		@Override
		public Integer getObject() {
			return 42;
		}

		@Override
		public Class<?> getObjectType() {
			return Integer.class;
		}
	}

	@SuppressWarnings("unused")
	static class MultiConstructorSample {

		MultiConstructorSample(String name) {
		}

		MultiConstructorSample(Integer value) {
		}

	}

	@SuppressWarnings("unused")
	static class MultiConstructorArraySample {

		public MultiConstructorArraySample(String... names) {
		}

		public MultiConstructorArraySample(Integer... values) {
		}
	}

	@SuppressWarnings("unused")
	static class MultiConstructorListSample {

		public MultiConstructorListSample(String name) {
		}

		public MultiConstructorListSample(List<Integer> values) {
		}

	}

	interface DummyInterface {

		static String of(Object o) {
			return o.toString();
		}
	}

	@SuppressWarnings("unused")
	static class DummySampleFactory implements DummyInterface {

		static String of(Integer value) {
			return value.toString();
		}

		private String of(String ignored) {
			return ignored;
		}
	}

	@SuppressWarnings("unused")
	static class ConstructorClassArraySample {

		ConstructorClassArraySample(Class<?>... classArrayArg) {
		}

		ConstructorClassArraySample(Executor somethingElse) {
		}
	}

	@SuppressWarnings("unused")
	static class MultiConstructorClassArraySample {

		MultiConstructorClassArraySample(Class<?>... classArrayArg) {
		}

		MultiConstructorClassArraySample(String... stringArrayArg) {
		}
	}

	@SuppressWarnings("unused")
	static class ClassArrayFactoryMethodSample {

		static String of(Class<?>[] classArrayArg) {
			return "test";
		}

	}

	@SuppressWarnings("unused")
	static class ClassArrayFactoryMethodSampleWithAnotherFactoryMethod {

		static String of(Class<?>[] classArrayArg) {
			return "test";
		}

		static String of(String[] classArrayArg) {
			return "test";
		}

	}

	@SuppressWarnings("unnused")
	static class ConstructorPrimitiveFallback {

		public ConstructorPrimitiveFallback(boolean useDefaultExecutor) {
		}

		public ConstructorPrimitiveFallback(Executor executor) {
		}

	}

	static class SampleBeanWithConstructors {

		public SampleBeanWithConstructors() {
		}

		public SampleBeanWithConstructors(String name) {
		}

		public SampleBeanWithConstructors(Number number, String name) {
		}

	}

	interface FactoryWithOverloadedClassMethodsOnInterface {

		static FactoryWithOverloadedClassMethodsOnInterface byAnnotation(
				Class<? extends Annotation> annotationType) {
			return byAnnotation(annotationType, SearchStrategy.INHERITED_ANNOTATIONS);
		}

		static FactoryWithOverloadedClassMethodsOnInterface byAnnotation(
				Class<? extends Annotation> annotationType,
				SearchStrategy searchStrategy) {
			return null;
		}

	}

}
