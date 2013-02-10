/*
 * Copyright 2002-2013 the original author or authors.
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
package org.springframework.jms.core.support;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.List;

import javax.jms.ConnectionFactory;

import org.junit.Test;
import org.springframework.jms.core.JmsTemplate;

/**
 * @author Mark Pollack
 * @since 24.9.2004
 */
public class JmsGatewaySupportTests {

	@Test
	public void testJmsGatewaySupportWithConnectionFactory() throws Exception {
		ConnectionFactory mockConnectionFactory = mock(ConnectionFactory.class);
		final List test = new ArrayList();
		JmsGatewaySupport gateway = new JmsGatewaySupport() {
			@Override
			protected void initGateway() {
				test.add("test");
			}
		};
		gateway.setConnectionFactory(mockConnectionFactory);
		gateway.afterPropertiesSet();
		assertEquals("Correct ConnectionFactory", mockConnectionFactory, gateway.getConnectionFactory());
		assertEquals("Correct JmsTemplate", mockConnectionFactory, gateway.getJmsTemplate().getConnectionFactory());
		assertEquals("initGatway called", test.size(), 1);
	}

	@Test
	public void testJmsGatewaySupportWithJmsTemplate() throws Exception {
		JmsTemplate template = new JmsTemplate();
		final List test = new ArrayList();
		JmsGatewaySupport gateway = new JmsGatewaySupport() {
			@Override
			protected void initGateway() {
				test.add("test");
			}
		};
		gateway.setJmsTemplate(template);
		gateway.afterPropertiesSet();
		assertEquals("Correct JmsTemplate", template, gateway.getJmsTemplate());
		assertEquals("initGateway called", test.size(), 1);
	}

}
