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

package org.springframework.beans.factory.generator;

import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import org.springframework.beans.factory.config.BeanReference;
import org.springframework.beans.factory.support.ManagedList;
import org.springframework.beans.factory.support.ManagedSet;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.core.ResolvableType;
import org.springframework.core.io.ResourceLoader;
import org.springframework.javapoet.support.CodeSnippet;
import org.springframework.util.ReflectionUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link BeanParameterGenerator}.
 *
 * @author Stephane Nicoll
 */
class BeanParameterGeneratorTests {

	private final BeanParameterGenerator generator = new BeanParameterGenerator();

	@Test
	void writeCharArray() {
		char[] value = new char[] { 'v', 'a', 'l', 'u', 'e' };
		assertThat(write(value, ResolvableType.forArrayComponent(ResolvableType.forClass(char.class))))
				.isEqualTo("new char[] { 'v', 'a', 'l', 'u', 'e' }");
	}

	@Test
	void writeStringArray() {
		String[] value = new String[] { "a", "test" };
		assertThat(write(value, ResolvableType.forArrayComponent(ResolvableType.forClass(String.class))))
				.isEqualTo("new String[] { \"a\", \"test\" }");
	}

	@Test
	void writeStringList() {
		List<String> value = List.of("a", "test");
		CodeSnippet code = codeSnippet(value, ResolvableType.forClassWithGenerics(List.class, String.class));
		assertThat(code.getSnippet()).isEqualTo(
				"List.of(\"a\", \"test\")");
		assertThat(code.hasImport(List.class)).isTrue();
	}

	@Test
	void writeStringManagedList() {
		ManagedList<String> value = ManagedList.of("a", "test");
		CodeSnippet code = codeSnippet(value, ResolvableType.forClassWithGenerics(List.class, String.class));
		assertThat(code.getSnippet()).isEqualTo(
				"ManagedList.of(\"a\", \"test\")");
		assertThat(code.hasImport(ManagedList.class)).isTrue();
	}

	@Test
	void writeEmptyList() {
		List<String> value = List.of();
		CodeSnippet code = codeSnippet(value, ResolvableType.forClassWithGenerics(List.class, String.class));
		assertThat(code.getSnippet()).isEqualTo("Collections.emptyList()");
		assertThat(code.hasImport(Collections.class)).isTrue();
	}

	@Test
	void writeStringSet() {
		Set<String> value = Set.of("a", "test");
		CodeSnippet code = codeSnippet(value, ResolvableType.forClassWithGenerics(Set.class, String.class));
		assertThat(code.getSnippet()).startsWith("Set.of(").contains("a").contains("test");
		assertThat(code.hasImport(Set.class)).isTrue();
	}

	@Test
	void writeStringManagedSet() {
		Set<String> value = ManagedSet.of("a", "test");
		CodeSnippet code = codeSnippet(value, ResolvableType.forClassWithGenerics(Set.class, String.class));
		assertThat(code.getSnippet()).isEqualTo(
				"ManagedSet.of(\"a\", \"test\")");
		assertThat(code.hasImport(ManagedSet.class)).isTrue();
	}

	@Test
	void writeEmptySet() {
		Set<String> value = Set.of();
		CodeSnippet code = codeSnippet(value, ResolvableType.forClassWithGenerics(Set.class, String.class));
		assertThat(code.getSnippet()).isEqualTo("Collections.emptySet()");
		assertThat(code.hasImport(Collections.class)).isTrue();
	}

	@Test
	void writeMap() {
		Map<String, Object> value = new LinkedHashMap<>();
		value.put("name", "Hello");
		value.put("counter", 42);
		assertThat(write(value)).isEqualTo("Map.of(\"name\", \"Hello\", \"counter\", 42)");
	}

	@Test
	void writeMapWithEnum() {
		Map<String, Object> value = new HashMap<>();
		value.put("unit", ChronoUnit.DAYS);
		assertThat(write(value)).isEqualTo("Map.of(\"unit\", ChronoUnit.DAYS)");
	}

	@Test
	void writeEmptyMap() {
		assertThat(write(Map.of())).isEqualTo("Map.of()");
	}

	@Test
	void writeString() {
		assertThat(write("test", ResolvableType.forClass(String.class))).isEqualTo("\"test\"");
	}

	@Test
	void writeCharEscapeBackslash() {
		assertThat(write('\\', ResolvableType.forType(char.class))).isEqualTo("'\\\\'");
	}

	@ParameterizedTest
	@MethodSource("primitiveValues")
	void writePrimitiveValue(Object value, String parameter) {
		assertThat(write(value, ResolvableType.forClass(value.getClass()))).isEqualTo(parameter);
	}

	private static Stream<Arguments> primitiveValues() {
		return Stream.of(Arguments.of((short) 0, "0"), Arguments.of((1), "1"), Arguments.of(2L, "2"),
				Arguments.of(2.5d, "2.5"), Arguments.of(2.7f, "2.7"), Arguments.of('c', "'c'"),
				Arguments.of((byte) 1, "1"), Arguments.of(true, "true"));
	}

	@Test
	void writeEnum() {
		assertThat(write(ChronoUnit.DAYS, ResolvableType.forClass(ChronoUnit.class)))
				.isEqualTo("ChronoUnit.DAYS");
	}

	@Test
	void writeClass() {
		assertThat(write(Integer.class, ResolvableType.forClass(Class.class)))
				.isEqualTo("Integer.class");
	}

	@Test
	void writeResolvableType() {
		ResolvableType type = ResolvableType.forClassWithGenerics(Consumer.class, Integer.class);
		assertThat(write(type, type))
				.isEqualTo("ResolvableType.forClassWithGenerics(Consumer.class, Integer.class)");
	}

	@Test
	void writeExecutableParameterTypesWithConstructor() {
		Constructor<?> constructor = TestSample.class.getDeclaredConstructors()[0];
		assertThat(CodeSnippet.process(this.generator.writeExecutableParameterTypes(constructor)))
				.isEqualTo("String.class, ResourceLoader.class");
	}

	@Test
	void writeExecutableParameterTypesWithNoArgConstructor() {
		Constructor<?> constructor = BeanParameterGeneratorTests.class.getDeclaredConstructors()[0];
		assertThat(CodeSnippet.process(this.generator.writeExecutableParameterTypes(constructor)))
				.isEmpty();
	}

	@Test
	void writeExecutableParameterTypesWithMethod() {
		Method method = ReflectionUtils.findMethod(TestSample.class, "createBean", String.class, Integer.class);
		assertThat(CodeSnippet.process(this.generator.writeExecutableParameterTypes(method)))
				.isEqualTo("String.class, Integer.class");
	}

	@Test
	void writeNull() {
		assertThat(write(null)).isEqualTo("null");
	}

	@Test
	void writeBeanReference() {
		BeanReference beanReference = mock(BeanReference.class);
		given(beanReference.getBeanName()).willReturn("testBean");
		assertThat(write(beanReference)).isEqualTo("new RuntimeBeanReference(\"testBean\")");
	}

	@Test
	void writeBeanDefinitionCallsConsumer() {
		BeanParameterGenerator customGenerator = new BeanParameterGenerator(
				((beanDefinition, builder) -> builder.add("test")));
		assertThat(CodeSnippet.process(customGenerator.writeParameterValue(new RootBeanDefinition()))).isEqualTo("test");
	}

	@Test
	void writeBeanDefinitionWithoutConsumerFails() {
		BeanParameterGenerator customGenerator = new BeanParameterGenerator();
		assertThatIllegalStateException().isThrownBy(() -> customGenerator
				.writeParameterValue(new RootBeanDefinition()));
	}

	@Test
	void writeUnsupportedParameter() {
		assertThatIllegalArgumentException().isThrownBy(() -> write(new StringWriter()))
				.withMessageContaining(StringWriter.class.getName());
	}

	private String write(Object value) {
		return CodeSnippet.process(this.generator.writeParameterValue(value));
	}

	private String write(Object value, ResolvableType resolvableType) {
		return codeSnippet(value, resolvableType).getSnippet();
	}

	private CodeSnippet codeSnippet(Object value, ResolvableType resolvableType) {
		return CodeSnippet.of(this.generator.writeParameterValue(value, () -> resolvableType));
	}


	@SuppressWarnings("unused")
	static class TestSample {

		public TestSample(String test, ResourceLoader resourceLoader) {
		}

		String createBean(String name, Integer counter) {
			return "test";
		}
	}

}
