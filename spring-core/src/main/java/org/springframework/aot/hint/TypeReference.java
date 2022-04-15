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

package org.springframework.aot.hint;

import org.springframework.lang.Nullable;

/**
 * Type abstraction that can be used to refer to types that are not available as
 * a {@link Class} yet.
 *
 * @author Stephane Nicoll
 * @since 6.0
 */
public interface TypeReference {

	/**
	 * Return the fully qualified name of this type reference.
	 * @return the reflection target name
	 */
	String getName();

	/**
	 * Return the {@linkplain Class#getCanonicalName() canonical name} of this
	 * type reference.
	 * @return the canonical name
	 */
	String getCanonicalName();

	/**
	 * Return the package name of this type.
	 * @return the package name
	 */
	String getPackageName();

	/**
	 * Return the {@linkplain Class#getSimpleName() simple name} of this type
	 * reference.
	 * @return the simple name
	 */
	String getSimpleName();

	/**
	 * Return the enclosing type reference, or {@code null} if this type reference
	 * does not have an enclosing type.
	 * @return the enclosing type, if any
	 */
	@Nullable
	TypeReference getEnclosingType();

	/**
	 * Create an instance based on the specified type.
	 * @param type the type to wrap
	 * @return a type reference for the specified type
	 */
	static TypeReference of(Class<?> type) {
		return ReflectionTypeReference.of(type);
	}

	/**
	 * Create an instance based on the specified class name.
	 * The format of the class name must follow {@linkplain Class#getName()},
	 * in particular inner classes should be separated by a {@code $}.
	 * @param className the class name of the type to wrap
	 * @return a type reference for the specified class name
	 */
	static TypeReference of(String className) {
		return SimpleTypeReference.of(className);
	}

}
