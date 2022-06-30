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

package org.springframework.aot.agent;


import org.springframework.aot.hint.ClassProxyHint;
import org.springframework.aot.hint.JavaSerializationHint;
import org.springframework.aot.hint.JdkProxyHint;
import org.springframework.aot.hint.ReflectionHints;
import org.springframework.aot.hint.ResourceBundleHint;
import org.springframework.aot.hint.ResourcePatternHint;

/**
 * Main types of {@link org.springframework.aot.hint.RuntimeHints}.
 * <p>This allows to sort {@link RecordedInvocation recorded invocations}
 * into hint categories.
 *
 * @author Brian Clozel
 * @since 6.0
 */
public enum HintType {

	/**
	 * Reflection hint, as described by {@link org.springframework.aot.hint.ReflectionHints}.
	 */
	REFLECTION(ReflectionHints.class),

	/**
	 * Resource pattern hint, as described by {@link org.springframework.aot.hint.ResourceHints#resourcePatterns()}.
	 */
	RESOURCE_PATTERN(ResourcePatternHint.class),

	/**
	 * Resource bundle hint, as described by {@link org.springframework.aot.hint.ResourceHints#resourceBundles()}.
	 */
	RESOURCE_BUNDLE(ResourceBundleHint.class),

	/**
	 * Java serialization hint, as described by {@link org.springframework.aot.hint.JavaSerializationHint}.
	 */
	JAVA_SERIALIZATION(JavaSerializationHint.class),

	/**
	 * JDK proxies hint, as described by {@link org.springframework.aot.hint.ProxyHints#jdkProxies()}.
	 */
	JDK_PROXIES(JdkProxyHint.class),

	/**
	 * Class proxies hint, as described by {@link org.springframework.aot.hint.ProxyHints#classProxies()}.
	 */
	CLASS_PROXIES(ClassProxyHint.class);

	private final Class<?> hintClass;

	HintType(Class<?> hintClass) {
		this.hintClass = hintClass;
	}

	public String hintClassName() {
		return this.hintClass.getSimpleName();
	}
}
