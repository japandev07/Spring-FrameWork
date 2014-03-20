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

package org.springframework.messaging.handler.annotation.support;

import static org.junit.Assert.*;

import java.lang.reflect.Method;
import java.util.Locale;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import org.springframework.core.MethodParameter;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.ErrorMessage;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.messaging.support.MessageBuilder;

/**
 *
 * @author Stephane Nicoll
 */
public class MessageMethodArgumentResolverTests {

	@Rule
	public final ExpectedException thrown = ExpectedException.none();

	private final MessageMethodArgumentResolver resolver = new MessageMethodArgumentResolver();

	private Method method;

	@Before
	public void setup() throws Exception {
		method = MessageMethodArgumentResolverTests.class.getDeclaredMethod("handleMessage",
				Message.class, Message.class, Message.class, Message.class, ErrorMessage.class);
	}

	@Test
	public void resolveAnyPayloadType() throws Exception {
		Message<String> message = MessageBuilder.withPayload("test").build();
		MethodParameter parameter = new MethodParameter(method, 0);

		assertTrue("Parameter '" + parameter + "' should be supported", resolver.supportsParameter(parameter));
		assertSame(message, resolver.resolveArgument(parameter, message));
	}

	@Test
	public void resolvePayloadTypeExactType() throws Exception {
		Message<Integer> message = MessageBuilder.withPayload(123).build();
		MethodParameter parameter = new MethodParameter(method, 1);

		assertTrue("Parameter '" + parameter + "' should be supported", resolver.supportsParameter(parameter));
		assertSame(message, resolver.resolveArgument(parameter, message));
	}

	@Test
	public void resolvePayloadTypeSubClass() throws Exception {
		Message<Integer> message = MessageBuilder.withPayload(123).build();
		MethodParameter parameter = new MethodParameter(method, 2);

		assertTrue("Parameter '" + parameter + "' should be supported", resolver.supportsParameter(parameter));
		assertSame(message, resolver.resolveArgument(parameter, message));
	}

	@Test
	public void resolveInvalidPayloadType() throws Exception {
		Message<String> message = MessageBuilder.withPayload("test").build();
		MethodParameter parameter = new MethodParameter(method, 1);

		assertTrue("Parameter '" + parameter + "' should be supported", resolver.supportsParameter(parameter));
		thrown.expect(MethodArgumentTypeMismatchException.class);
		thrown.expectMessage(Integer.class.getName());
		thrown.expectMessage(String.class.getName());
		resolver.resolveArgument(parameter, message);
	}

	@Test
	public void resolveUpperBoundPayloadType() throws Exception {
		Message<Integer> message = MessageBuilder.withPayload(123).build();
		MethodParameter parameter = new MethodParameter(method, 3);

		assertTrue("Parameter '" + parameter + "' should be supported", resolver.supportsParameter(parameter));
		assertSame(message, resolver.resolveArgument(parameter, message));
	}

	@Test
	public void resolveOutOfBoundPayloadType() throws Exception {
		Message<Locale> message = MessageBuilder.withPayload(Locale.getDefault()).build();
		MethodParameter parameter = new MethodParameter(method, 3);

		assertTrue("Parameter '" + parameter + "' should be supported", resolver.supportsParameter(parameter));
		thrown.expect(MethodArgumentTypeMismatchException.class);
		thrown.expectMessage(Number.class.getName());
		thrown.expectMessage(Locale.class.getName());
		resolver.resolveArgument(parameter, message);
	}

	@Test
	public void resolveMessageSubTypeExactMatch() throws Exception {
		ErrorMessage message = new ErrorMessage(new UnsupportedOperationException());
		MethodParameter parameter = new MethodParameter(method, 4);

		assertTrue("Parameter '" + parameter + "' should be supported", resolver.supportsParameter(parameter));
		assertSame(message, resolver.resolveArgument(parameter, message));
	}

	@Test
	public void resolveMessageSubTypeSubClass() throws Exception {
		ErrorMessage message = new ErrorMessage(new UnsupportedOperationException());
		MethodParameter parameter = new MethodParameter(method, 0);

		assertTrue("Parameter '" + parameter + "' should be supported", resolver.supportsParameter(parameter));
		assertSame(message, resolver.resolveArgument(parameter, message));
	}

	@Test
	public void resolveWrongMessageType() throws Exception {
		Message<? extends Throwable> message = new GenericMessage<Throwable>(
				new UnsupportedOperationException());
		MethodParameter parameter = new MethodParameter(method, 4);

		assertTrue("Parameter '" + parameter + "' should be supported", resolver.supportsParameter(parameter));
		thrown.expect(MethodArgumentTypeMismatchException.class);
		thrown.expectMessage(ErrorMessage.class.getName());
		thrown.expectMessage(GenericMessage.class.getName());
		assertSame(message, resolver.resolveArgument(parameter, message));
	}

	@SuppressWarnings("unused")
	private void handleMessage(
			Message<?> wildcardPayload,
			Message<Integer> integerPayload,
			Message<Number> numberPayload,
			Message<? extends Number> anyNumberPayload,
			ErrorMessage subClass) {
	}

}
