/*
 * Copyright 2002-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.transaction.event;

import java.lang.reflect.Method;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import org.springframework.util.ReflectionUtils;

import static org.junit.Assert.*;

/**
 * @author Stephane Nicoll
 */
public class ApplicationListenerMethodTransactionalAdapterTests {

	@Rule
	public final ExpectedException thrown = ExpectedException.none();

	@Test
	public void noAnnotation() {
		Method m = ReflectionUtils.findMethod(PhaseConfigurationTestListener.class,
				"noAnnotation", String.class);

		thrown.expect(IllegalStateException.class);
		thrown.expectMessage("noAnnotation");
		ApplicationListenerMethodTransactionalAdapter.findAnnotation(m);
	}

	@Test
	public void defaultPhase() {
		Method m = ReflectionUtils.findMethod(PhaseConfigurationTestListener.class, "defaultPhase", String.class);
		assertPhase(m, TransactionPhase.AFTER_COMMIT);
	}

	@Test
	public void phaseSet() {
		Method m = ReflectionUtils.findMethod(PhaseConfigurationTestListener.class, "phaseSet", String.class);
		assertPhase(m, TransactionPhase.AFTER_ROLLBACK);
	}

	private void assertPhase(Method method, TransactionPhase expected) {
		assertNotNull("Method must not be null", method);
		TransactionalEventListener annotation = ApplicationListenerMethodTransactionalAdapter.findAnnotation(method);
		assertEquals("Wrong phase for '" + method + "'", expected, annotation.phase());
	}


	static class PhaseConfigurationTestListener {

		public void noAnnotation(String data) {
		}

		@TransactionalEventListener
		public void defaultPhase(String data) {
		}

		@TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
		public void phaseSet(String data) {
		}

	}

}
