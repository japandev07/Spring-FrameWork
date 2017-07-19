/*
 * Copyright 2002-2012 the original author or authors.
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

package org.springframework.jms.listener;

import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests for the {@link AbstractMessageListenerContainer} class.
 *
 * @author Rick Evans
 * @author Chris Beams
 */
public abstract class AbstractMessageListenerContainerTests {

	protected abstract AbstractMessageListenerContainer getContainer();

	
	public void testSettingMessageListenerToANullType() {
		getContainer().setMessageListener(null);
		Assert.assertNull(getContainer().getMessageListener());
	}

	@Test(expected = IllegalArgumentException.class)
	public void testSettingMessageListenerToAnUnsupportedType() throws Exception {
		getContainer().setMessageListener("Bingo");
	}

}
