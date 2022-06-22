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

package org.springframework.beans.testfixture.beans.factory.aot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.aot.generate.GeneratedMethods;
import org.springframework.aot.generate.MethodReference;
import org.springframework.beans.factory.aot.BeanFactoryInitializationCode;

/**
 * Mock {@link BeanFactoryInitializationCode} implementation.
 *
 * @author Stephane Nicoll
 */
public class MockBeanFactoryInitializationCode implements BeanFactoryInitializationCode {

	private final GeneratedMethods generatedMethods = new GeneratedMethods();

	private final List<MethodReference> initializers = new ArrayList<>();

	@Override
	public GeneratedMethods getMethodGenerator() {
		return this.generatedMethods;
	}

	@Override
	public void addInitializer(MethodReference methodReference) {
		this.initializers.add(methodReference);
	}

	public List<MethodReference> getInitializers() {
		return Collections.unmodifiableList(this.initializers);
	}

}
