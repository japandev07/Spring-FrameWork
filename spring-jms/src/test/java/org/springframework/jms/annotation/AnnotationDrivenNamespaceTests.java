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

package org.springframework.jms.annotation;

import javax.jms.JMSException;
import javax.jms.MessageListener;

import org.hamcrest.core.Is;
import org.junit.Test;

import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.jms.config.JmsListenerContainerFactory;
import org.springframework.jms.config.JmsListenerEndpointRegistrar;
import org.springframework.jms.config.SimpleJmsListenerEndpoint;
import org.springframework.jms.listener.adapter.ListenerExecutionFailedException;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;

/**
 *
 * @author Stephane Nicoll
 */
public class AnnotationDrivenNamespaceTests extends AbstractJmsAnnotationDrivenTests {

	@Override
	@Test
	public void sampleConfiguration() {
		ApplicationContext context = new ClassPathXmlApplicationContext(
				"annotation-driven-sample-config.xml", getClass());
		testSampleConfiguration(context);
	}

	@Override
	@Test
	public void fullConfiguration() {
		ApplicationContext context = new ClassPathXmlApplicationContext(
				"annotation-driven-full-config.xml", getClass());
		testFullConfiguration(context);
	}

	@Override
	@Test
	public void customConfiguration() {
		ApplicationContext context = new ClassPathXmlApplicationContext(
				"annotation-driven-custom-registry.xml", getClass());
		testCustomConfiguration(context);
	}

	@Override
	@Test
	public void defaultContainerFactoryConfiguration() {
		ApplicationContext context = new ClassPathXmlApplicationContext(
				"annotation-driven-custom-container-factory.xml", getClass());
		testDefaultContainerFactoryConfiguration(context);
	}

	@Override
	public void jmsHandlerMethodFactoryConfiguration() throws JMSException {
		ApplicationContext context = new ClassPathXmlApplicationContext(
				"annotation-driven-custom-handler-method-factory.xml", getClass());

		thrown.expect(ListenerExecutionFailedException.class);
		thrown.expectCause(Is.<MethodArgumentNotValidException>isA(MethodArgumentNotValidException.class));
		testJmsHandlerMethodFactoryConfiguration(context);
	}

	static class CustomJmsListenerConfigurer implements JmsListenerConfigurer {

		private MessageListener messageListener;

		private JmsListenerContainerFactory<?> containerFactory;

		@Override
		public void configureJmsListeners(JmsListenerEndpointRegistrar registrar) {
			SimpleJmsListenerEndpoint endpoint = new SimpleJmsListenerEndpoint();
			endpoint.setId("myCustomEndpointId");
			endpoint.setDestination("myQueue");
			endpoint.setMessageListener(messageListener);
			registrar.registerEndpoint(endpoint, containerFactory);
		}

		public void setMessageListener(MessageListener messageListener) {
			this.messageListener = messageListener;
		}

		public void setContainerFactory(JmsListenerContainerFactory<?> containerFactory) {
			this.containerFactory = containerFactory;
		}
	}
}
