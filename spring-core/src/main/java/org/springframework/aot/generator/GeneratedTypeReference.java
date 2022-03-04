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

package org.springframework.aot.generator;

import org.springframework.aot.hint.AbstractTypeReference;
import org.springframework.aot.hint.TypeReference;
import org.springframework.javapoet.ClassName;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * A {@link TypeReference} for a generated {@linkplain ClassName type}.
 *
 * @author Stephane Nicoll
 * @since 6.0
 */
public final class GeneratedTypeReference extends AbstractTypeReference {

	private final ClassName className;

	@Nullable
	private final TypeReference enclosingType;

	private GeneratedTypeReference(ClassName className) {
		this.className = className;
		this.enclosingType = (className.enclosingClassName() != null
				? new GeneratedTypeReference(className.enclosingClassName())
				: null);
	}

	public static GeneratedTypeReference of(ClassName className) {
		Assert.notNull(className, "ClassName must not be null");
		return new GeneratedTypeReference(className);
	}

	@Override
	public String getCanonicalName() {
		return this.className.canonicalName();
	}

	@Override
	public String getPackageName() {
		return this.className.packageName();
	}

	@Override
	public String getSimpleName() {
		return this.className.simpleName();
	}

	@Override
	public TypeReference getEnclosingType() {
		return this.enclosingType;
	}

}
