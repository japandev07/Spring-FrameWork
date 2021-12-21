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

package org.springframework.core.hint;

/**
 * Gather hints that can be used to optimize the application runtime.
 *
 * <p>Use of reflection can be recorded for individual members of a type, as
 * well as broader {@linkplain MemberCategory member categories}. Access to
 * resources can be specified using patterns or the base name of a resource
 * bundle.
 *
 * <p>Hints that require the need for Java serialization of proxies can be
 * recorded as well.
 *
 * @author Stephane Nicoll
 * @since 6.0
 */
public class RuntimeHints {

	private final ReflectionHints reflection = new ReflectionHints();

	private final ResourceHints resources = new ResourceHints();

	private final JavaSerializationHints javaSerialization = new JavaSerializationHints();

	private final ProxyHints proxies = new ProxyHints();


	/**
	 * Provide access to reflection-based hints.
	 * @return reflection hints
	 */
	public ReflectionHints reflection() {
		return this.reflection;
	}

	/**
	 * Provide access to resource-based hints.
	 * @return resource hints
	 */
	public ResourceHints resources() {
		return this.resources;
	}

	/**
	 * Provide access to serialization-based hints.
	 * @return java serialization hints
	 */
	public JavaSerializationHints javaSerialization() {
		return this.javaSerialization;
	}

	/**
	 * Provide access to proxy-based hints.
	 * @return proxy hints
	 */
	public ProxyHints proxies() {
		return this.proxies;
	}

}
