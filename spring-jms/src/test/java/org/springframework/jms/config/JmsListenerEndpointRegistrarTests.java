/*
 * Copyright 2002-2014 the original author or authors.
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

package org.springframework.jms.config;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

/**
 *
 * @author Stephane Nicoll
 */
public class JmsListenerEndpointRegistrarTests {

	@Rule
	public final ExpectedException thrown = ExpectedException.none();

	private final JmsListenerEndpointRegistrar registrar = new JmsListenerEndpointRegistrar();

	private final JmsListenerEndpointRegistry registry = new JmsListenerEndpointRegistry();

	private final JmsListenerContainerTestFactory containerFactory = new JmsListenerContainerTestFactory();

	@Before
	public void setup() {
		registrar.setEndpointRegistry(registry);
	}

	@Test
	public void registerNullEndpoint() {
		thrown.expect(IllegalArgumentException.class);
		registrar.registerEndpoint(null, containerFactory);
	}

	@Test
	public void registerNullEndpointId() {
		thrown.expect(IllegalArgumentException.class);
		registrar.registerEndpoint(new SimpleJmsListenerEndpoint(), containerFactory);
	}

	@Test
	public void registerNullContainerFactoryIsAllowed() throws Exception {
		SimpleJmsListenerEndpoint endpoint = new SimpleJmsListenerEndpoint();
		endpoint.setId("some id");
		registrar.setDefaultContainerFactory(containerFactory);
		registrar.registerEndpoint(endpoint, null);
		registrar.afterPropertiesSet();
		assertNotNull("Container not created", registry.getContainer("some id"));
		assertEquals(1, registry.getContainers().size());
	}

	@Test
	public void registerNullContainerFactoryWithNoDefault() throws Exception {
		SimpleJmsListenerEndpoint endpoint = new SimpleJmsListenerEndpoint();
		endpoint.setId("some id");
		registrar.registerEndpoint(endpoint, null);

		thrown.expect(IllegalStateException.class);
		thrown.expectMessage(endpoint.toString());
		registrar.afterPropertiesSet();
	}

	@Test
	public void registerContainerWithoutFactory() throws Exception {
		SimpleJmsListenerEndpoint endpoint = new SimpleJmsListenerEndpoint();
		endpoint.setId("myEndpoint");
		registrar.setDefaultContainerFactory(containerFactory);
		registrar.registerEndpoint(endpoint);
		registrar.afterPropertiesSet();
		assertNotNull("Container not created", registry.getContainer("myEndpoint"));
		assertEquals(1, registry.getContainers().size());
	}

}
