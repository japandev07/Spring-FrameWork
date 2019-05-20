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

package org.springframework.transaction.interceptor;

import java.io.IOException;

import org.junit.Test;

import org.springframework.beans.FatalBeanException;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for the {@link RollbackRuleAttribute} class.
 *
 * @author Rod Johnson
 * @author Rick Evans
 * @author Chris Beams
 * @author Sam Brannen
 * @since 09.04.2003
 */
public class RollbackRuleTests {

	@Test
	public void foundImmediatelyWithString() {
		RollbackRuleAttribute rr = new RollbackRuleAttribute(java.lang.Exception.class.getName());
		assertEquals(0, rr.getDepth(new Exception()));
	}

	@Test
	public void foundImmediatelyWithClass() {
		RollbackRuleAttribute rr = new RollbackRuleAttribute(Exception.class);
		assertEquals(0, rr.getDepth(new Exception()));
	}

	@Test
	public void notFound() {
		RollbackRuleAttribute rr = new RollbackRuleAttribute(java.io.IOException.class.getName());
		assertEquals(-1, rr.getDepth(new MyRuntimeException("")));
	}

	@Test
	public void ancestry() {
		RollbackRuleAttribute rr = new RollbackRuleAttribute(java.lang.Exception.class.getName());
		// Exception -> Runtime -> NestedRuntime -> MyRuntimeException
		assertThat(rr.getDepth(new MyRuntimeException("")), equalTo(3));
	}

	@Test
	public void alwaysTrueForThrowable() {
		RollbackRuleAttribute rr = new RollbackRuleAttribute(java.lang.Throwable.class.getName());
		assertTrue(rr.getDepth(new MyRuntimeException("")) > 0);
		assertTrue(rr.getDepth(new IOException()) > 0);
		assertTrue(rr.getDepth(new FatalBeanException(null,null)) > 0);
		assertTrue(rr.getDepth(new RuntimeException()) > 0);
	}

	@Test
	public void ctorArgMustBeAThrowableClassWithNonThrowableType() {
		assertThatIllegalArgumentException().isThrownBy(() ->
				new RollbackRuleAttribute(StringBuffer.class));
	}

	@Test
	public void ctorArgMustBeAThrowableClassWithNullThrowableType() {
		assertThatIllegalArgumentException().isThrownBy(() ->
				new RollbackRuleAttribute((Class<?>) null));
	}

	@Test
	public void ctorArgExceptionStringNameVersionWithNull() {
		assertThatIllegalArgumentException().isThrownBy(() ->
				new RollbackRuleAttribute((String) null));
	}

}
