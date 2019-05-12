/*
 * Copyright 2002-2019 the original author or authors.
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

package org.springframework.web.servlet.view.script;

import org.junit.Test;

import org.springframework.beans.DirectFieldAccessor;

import static org.junit.Assert.assertEquals;

/**
 * Unit tests for {@link ScriptTemplateViewResolver}.
 *
 * @author Sebastien Deleuze
 */
public class ScriptTemplateViewResolverTests {

	@Test
	public void viewClass() throws Exception {
		ScriptTemplateViewResolver resolver = new ScriptTemplateViewResolver();
		assertEquals(ScriptTemplateView.class, resolver.requiredViewClass());
		DirectFieldAccessor viewAccessor = new DirectFieldAccessor(resolver);
		Class<?> viewClass = (Class<?>) viewAccessor.getPropertyValue("viewClass");
		assertEquals(ScriptTemplateView.class, viewClass);
	}

}
