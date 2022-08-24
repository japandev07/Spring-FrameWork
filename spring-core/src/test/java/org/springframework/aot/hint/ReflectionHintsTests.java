/*
 * Copyright 2002-2021 the original author or authors.
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

package org.springframework.aot.hint;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import org.springframework.lang.Nullable;
import org.springframework.util.ReflectionUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Tests for {@link ReflectionHints}.
 *
 * @author Stephane Nicoll
 */
class ReflectionHintsTests {

	private final ReflectionHints reflectionHints = new ReflectionHints();

	@Test
	void registerType() {
		this.reflectionHints.registerType(TypeReference.of(String.class),
				hint -> hint.withMembers(MemberCategory.DECLARED_FIELDS));
		assertThat(this.reflectionHints.typeHints()).singleElement().satisfies(
				typeWithMemberCategories(String.class, MemberCategory.DECLARED_FIELDS));
	}

	@Test
	void registerTypeIfPresentRegisterExistingClass() {
		this.reflectionHints.registerTypeIfPresent(null, String.class.getName(),
				hint -> hint.withMembers(MemberCategory.DECLARED_FIELDS));
		assertThat(this.reflectionHints.typeHints()).singleElement().satisfies(
				typeWithMemberCategories(String.class, MemberCategory.DECLARED_FIELDS));
	}

	@Test
	@SuppressWarnings("unchecked")
	void registerTypeIfPresentIgnoreMissingClass() {
		Consumer<TypeHint.Builder> hintBuilder = mock(Consumer.class);
		this.reflectionHints.registerTypeIfPresent(null, "com.example.DoesNotExist", hintBuilder);
		assertThat(this.reflectionHints.typeHints()).isEmpty();
		verifyNoInteractions(hintBuilder);
	}

	@Test
	void getTypeUsingType() {
		this.reflectionHints.registerType(TypeReference.of(String.class),
				hint -> hint.withMembers(MemberCategory.DECLARED_FIELDS));
		assertThat(this.reflectionHints.getTypeHint(String.class)).satisfies(
				typeWithMemberCategories(String.class, MemberCategory.DECLARED_FIELDS));
	}

	@Test
	void getTypeUsingTypeReference() {
		this.reflectionHints.registerType(String.class,
				hint -> hint.withMembers(MemberCategory.DECLARED_FIELDS));
		assertThat(this.reflectionHints.getTypeHint(TypeReference.of(String.class))).satisfies(
				typeWithMemberCategories(String.class, MemberCategory.DECLARED_FIELDS));
	}

	@Test
	void getTypeForNonExistingType() {
		assertThat(this.reflectionHints.getTypeHint(String.class)).isNull();
	}

	@Test
	void registerTypeReuseBuilder() {
		this.reflectionHints.registerType(TypeReference.of(String.class),
				typeHint -> typeHint.withMembers(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS));
		Field field = ReflectionUtils.findField(String.class, "value");
		assertThat(field).isNotNull();
		this.reflectionHints.registerField(field);
		assertThat(this.reflectionHints.typeHints()).singleElement().satisfies(typeHint -> {
			assertThat(typeHint.getType().getCanonicalName()).isEqualTo(String.class.getCanonicalName());
			assertThat(typeHint.fields()).singleElement().satisfies(fieldHint -> assertThat(fieldHint.getName()).isEqualTo("value"));
			assertThat(typeHint.getMemberCategories()).containsOnly(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);
		});
	}

	@Test
	void registerClass() {
		this.reflectionHints.registerType(Integer.class,
				hint -> hint.withMembers(MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS));
		assertThat(this.reflectionHints.typeHints()).singleElement().satisfies(
				typeWithMemberCategories(Integer.class, MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS));
	}

	@Test
	void registerTypesApplyTheSameHints() {
		this.reflectionHints.registerTypes(TypeReference.listOf(Integer.class, String.class, Double.class),
				hint -> hint.withMembers(MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS));
		assertThat(this.reflectionHints.typeHints())
				.anySatisfy(
						typeWithMemberCategories(Integer.class, MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS))
				.anySatisfy(
						typeWithMemberCategories(String.class, MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS))
				.anySatisfy(
						typeWithMemberCategories(Double.class, MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS))
				.hasSize(3);
	}

	@Test
	void registerFieldAllowsWriteByDefault() {
		Field field = ReflectionUtils.findField(TestType.class, "field");
		assertThat(field).isNotNull();
		this.reflectionHints.registerField(field);
		assertTestTypeFieldHint(fieldHint -> {
			assertThat(fieldHint.getName()).isEqualTo("field");
			assertThat(fieldHint.isAllowWrite()).isTrue();
			assertThat(fieldHint.isAllowUnsafeAccess()).isFalse();
		});
	}

	@Test
	void registerFieldWithEmptyCustomizerAppliesConsistentDefault() {
		Field field = ReflectionUtils.findField(TestType.class, "field");
		assertThat(field).isNotNull();
		this.reflectionHints.registerField(field, fieldHint -> {});
		assertTestTypeFieldHint(fieldHint -> {
			assertThat(fieldHint.getName()).isEqualTo("field");
			assertThat(fieldHint.isAllowWrite()).isTrue();
			assertThat(fieldHint.isAllowUnsafeAccess()).isFalse();
		});
	}

	@Test
	void registerFieldWithCustomizerAppliesCustomization() {
		Field field = ReflectionUtils.findField(TestType.class, "field");
		assertThat(field).isNotNull();
		this.reflectionHints.registerField(field, fieldHint ->
				fieldHint.allowWrite(false).allowUnsafeAccess(true));
		assertTestTypeFieldHint(fieldHint -> {
			assertThat(fieldHint.getName()).isEqualTo("field");
			assertThat(fieldHint.isAllowWrite()).isFalse();
			assertThat(fieldHint.isAllowUnsafeAccess()).isTrue();
		});
	}

	private void assertTestTypeFieldHint(Consumer<FieldHint> fieldHint) {
		assertThat(this.reflectionHints.typeHints()).singleElement().satisfies(typeHint -> {
			assertThat(typeHint.getType().getCanonicalName()).isEqualTo(TestType.class.getCanonicalName());
			assertThat(typeHint.fields()).singleElement().satisfies(fieldHint);
			assertThat(typeHint.constructors()).isEmpty();
			assertThat(typeHint.methods()).isEmpty();
			assertThat(typeHint.getMemberCategories()).isEmpty();
		});
	}

	@Test
	void registerConstructor() {
		this.reflectionHints.registerConstructor(TestType.class.getDeclaredConstructors()[0]);
		assertTestTypeConstructorHint(constructorHint -> {
			assertThat(constructorHint.getParameterTypes()).isEmpty();
			assertThat(constructorHint.getMode()).isEqualTo(ExecutableMode.INVOKE);
		});
	}

	@Test
	void registerConstructorWithEmptyCustomizerAppliesConsistentDefault() {
		this.reflectionHints.registerConstructor(TestType.class.getDeclaredConstructors()[0],
				constructorHint -> {});
		assertTestTypeConstructorHint(constructorHint -> {
			assertThat(constructorHint.getParameterTypes()).isEmpty();
			assertThat(constructorHint.getMode()).isEqualTo(ExecutableMode.INVOKE);
		});
	}

	@Test
	void registerConstructorWithCustomizerAppliesCustomization() {
		this.reflectionHints.registerConstructor(TestType.class.getDeclaredConstructors()[0],
				constructorHint -> constructorHint.withMode(ExecutableMode.INTROSPECT));
		assertTestTypeConstructorHint(constructorHint -> {
			assertThat(constructorHint.getParameterTypes()).isEmpty();
			assertThat(constructorHint.getMode()).isEqualTo(ExecutableMode.INTROSPECT);
		});
	}

	private void assertTestTypeConstructorHint(Consumer<ExecutableHint> constructorHint) {
		assertThat(this.reflectionHints.typeHints()).singleElement().satisfies(typeHint -> {
			assertThat(typeHint.getMemberCategories()).isEmpty();
			assertThat(typeHint.getType().getCanonicalName()).isEqualTo(TestType.class.getCanonicalName());
			assertThat(typeHint.fields()).isEmpty();
			assertThat(typeHint.constructors()).singleElement().satisfies(constructorHint);
			assertThat(typeHint.methods()).isEmpty();
			assertThat(typeHint.getMemberCategories()).isEmpty();
		});
	}

	@Test
	void registerMethod() {
		Method method = ReflectionUtils.findMethod(TestType.class, "setName", String.class);
		assertThat(method).isNotNull();
		this.reflectionHints.registerMethod(method);
		assertTestTypeMethodHints(methodHint -> {
			assertThat(methodHint.getName()).isEqualTo("setName");
			assertThat(methodHint.getParameterTypes()).containsOnly(TypeReference.of(String.class));
			assertThat(methodHint.getMode()).isEqualTo(ExecutableMode.INVOKE);
		});
	}

	@Test
	void registerMethodWithEmptyCustomizerAppliesConsistentDefault() {
		Method method = ReflectionUtils.findMethod(TestType.class, "setName", String.class);
		assertThat(method).isNotNull();
		this.reflectionHints.registerMethod(method, methodHint -> {});
		assertTestTypeMethodHints(methodHint -> {
			assertThat(methodHint.getName()).isEqualTo("setName");
			assertThat(methodHint.getParameterTypes()).containsOnly(TypeReference.of(String.class));
			assertThat(methodHint.getMode()).isEqualTo(ExecutableMode.INVOKE);
		});
	}

	@Test
	void registerMethodWithCustomizerAppliesCustomization() {
		Method method = ReflectionUtils.findMethod(TestType.class, "setName", String.class);
		assertThat(method).isNotNull();
		this.reflectionHints.registerMethod(method, methodHint ->
				methodHint.withMode(ExecutableMode.INTROSPECT));
		assertTestTypeMethodHints(methodHint -> {
			assertThat(methodHint.getName()).isEqualTo("setName");
			assertThat(methodHint.getParameterTypes()).containsOnly(TypeReference.of(String.class));
			assertThat(methodHint.getMode()).isEqualTo(ExecutableMode.INTROSPECT);
		});
	}

	private void assertTestTypeMethodHints(Consumer<ExecutableHint> methodHint) {
		assertThat(this.reflectionHints.typeHints()).singleElement().satisfies(typeHint -> {
			assertThat(typeHint.getType().getCanonicalName()).isEqualTo(TestType.class.getCanonicalName());
			assertThat(typeHint.fields()).isEmpty();
			assertThat(typeHint.constructors()).isEmpty();
			assertThat(typeHint.methods()).singleElement().satisfies(methodHint);
		});
	}

	private Consumer<TypeHint> typeWithMemberCategories(Class<?> type, MemberCategory... memberCategories) {
		return typeHint -> {
			assertThat(typeHint.getType().getCanonicalName()).isEqualTo(type.getCanonicalName());
			assertThat(typeHint.fields()).isEmpty();
			assertThat(typeHint.constructors()).isEmpty();
			assertThat(typeHint.methods()).isEmpty();
			assertThat(typeHint.getMemberCategories()).containsExactly(memberCategories);
		};
	}


	@SuppressWarnings("unused")
	static class TestType {

		@Nullable
		private String field;

		void setName(String name) {

		}

	}

}
