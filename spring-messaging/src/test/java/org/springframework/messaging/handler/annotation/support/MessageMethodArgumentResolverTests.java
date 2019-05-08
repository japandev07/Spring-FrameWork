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

package org.springframework.messaging.handler.annotation.support;

import java.lang.reflect.Method;
import java.util.Locale;

import org.junit.Before;
import org.junit.Test;

import org.springframework.core.MethodParameter;
import org.springframework.messaging.Message;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.support.ErrorMessage;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.messaging.support.MessageBuilder;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link MessageMethodArgumentResolver}.
 *
 * @author Stephane Nicoll
 * @author Juergen Hoeller
 */
public class MessageMethodArgumentResolverTests {

	private MessageConverter converter;

	private MessageMethodArgumentResolver resolver;

	private Method method;


	@Before
	public void setup() throws Exception {
		this.method = MessageMethodArgumentResolverTests.class.getDeclaredMethod("handle",
				Message.class, Message.class, Message.class, Message.class, ErrorMessage.class, Message.class);

		this.converter = mock(MessageConverter.class);
		this.resolver = new MessageMethodArgumentResolver(this.converter);
	}


	@Test
	public void resolveWithPayloadTypeAsWildcard() throws Exception {
		Message<String> message = MessageBuilder.withPayload("test").build();
		MethodParameter parameter = new MethodParameter(this.method, 0);

		assertTrue(this.resolver.supportsParameter(parameter));
		assertSame(message, this.resolver.resolveArgument(parameter, message));
	}

	@Test
	public void resolveWithMatchingPayloadType() throws Exception {
		Message<Integer> message = MessageBuilder.withPayload(123).build();
		MethodParameter parameter = new MethodParameter(this.method, 1);

		assertTrue(this.resolver.supportsParameter(parameter));
		assertSame(message, this.resolver.resolveArgument(parameter, message));
	}

	@Test
	public void resolveWithPayloadTypeSubclass() throws Exception {
		Message<Integer> message = MessageBuilder.withPayload(123).build();
		MethodParameter parameter = new MethodParameter(this.method, 2);

		assertTrue(this.resolver.supportsParameter(parameter));
		assertSame(message, this.resolver.resolveArgument(parameter, message));
	}

	@Test
	public void resolveWithConversion() throws Exception {
		Message<String> message = MessageBuilder.withPayload("test").build();
		MethodParameter parameter = new MethodParameter(this.method, 1);

		when(this.converter.fromMessage(message, Integer.class)).thenReturn(4);

		@SuppressWarnings("unchecked")
		Message<Integer> actual = (Message<Integer>) this.resolver.resolveArgument(parameter, message);

		assertNotNull(actual);
		assertSame(message.getHeaders(), actual.getHeaders());
		assertEquals(new Integer(4), actual.getPayload());
	}

	@Test
	public void resolveWithConversionNoMatchingConverter() throws Exception {
		Message<String> message = MessageBuilder.withPayload("test").build();
		MethodParameter parameter = new MethodParameter(this.method, 1);

		assertTrue(this.resolver.supportsParameter(parameter));
		assertThatExceptionOfType(MessageConversionException.class).isThrownBy(() ->
				this.resolver.resolveArgument(parameter, message))
			.withMessageContaining(Integer.class.getName())
			.withMessageContaining(String.class.getName());
	}

	@Test
	public void resolveWithConversionEmptyPayload() throws Exception {
		Message<String> message = MessageBuilder.withPayload("").build();
		MethodParameter parameter = new MethodParameter(this.method, 1);

		assertTrue(this.resolver.supportsParameter(parameter));
		assertThatExceptionOfType(MessageConversionException.class).isThrownBy(() ->
				this.resolver.resolveArgument(parameter, message))
			.withMessageContaining("payload is empty")
			.withMessageContaining(Integer.class.getName())
			.withMessageContaining(String.class.getName());
	}

	@Test
	public void resolveWithPayloadTypeUpperBound() throws Exception {
		Message<Integer> message = MessageBuilder.withPayload(123).build();
		MethodParameter parameter = new MethodParameter(this.method, 3);

		assertTrue(this.resolver.supportsParameter(parameter));
		assertSame(message, this.resolver.resolveArgument(parameter, message));
	}

	@Test
	public void resolveWithPayloadTypeOutOfBound() throws Exception {
		Message<Locale> message = MessageBuilder.withPayload(Locale.getDefault()).build();
		MethodParameter parameter = new MethodParameter(this.method, 3);

		assertTrue(this.resolver.supportsParameter(parameter));
		assertThatExceptionOfType(MessageConversionException.class).isThrownBy(() ->
				this.resolver.resolveArgument(parameter, message))
			.withMessageContaining(Number.class.getName())
			.withMessageContaining(Locale.class.getName());
	}

	@Test
	public void resolveMessageSubclassMatch() throws Exception {
		ErrorMessage message = new ErrorMessage(new UnsupportedOperationException());
		MethodParameter parameter = new MethodParameter(this.method, 4);

		assertTrue(this.resolver.supportsParameter(parameter));
		assertSame(message, this.resolver.resolveArgument(parameter, message));
	}

	@Test
	public void resolveWithMessageSubclassAndPayloadWildcard() throws Exception {
		ErrorMessage message = new ErrorMessage(new UnsupportedOperationException());
		MethodParameter parameter = new MethodParameter(this.method, 0);

		assertTrue(this.resolver.supportsParameter(parameter));
		assertSame(message, this.resolver.resolveArgument(parameter, message));
	}

	@Test
	public void resolveWithWrongMessageType() throws Exception {
		UnsupportedOperationException ex = new UnsupportedOperationException();
		Message<? extends Throwable> message = new GenericMessage<Throwable>(ex);
		MethodParameter parameter = new MethodParameter(this.method, 4);

		assertTrue(this.resolver.supportsParameter(parameter));
		assertThatExceptionOfType(MethodArgumentTypeMismatchException.class).isThrownBy(() ->
				this.resolver.resolveArgument(parameter, message))
			.withMessageContaining(ErrorMessage.class.getName())
			.withMessageContaining(GenericMessage.class.getName());
	}

	@Test
	public void resolveWithPayloadTypeAsWildcardAndNoConverter() throws Exception {
		this.resolver = new MessageMethodArgumentResolver();

		Message<String> message = MessageBuilder.withPayload("test").build();
		MethodParameter parameter = new MethodParameter(this.method, 0);

		assertTrue(this.resolver.supportsParameter(parameter));
		assertSame(message, this.resolver.resolveArgument(parameter, message));
	}

	@Test
	public void resolveWithConversionNeededButNoConverter() throws Exception {
		this.resolver = new MessageMethodArgumentResolver();

		Message<String> message = MessageBuilder.withPayload("test").build();
		MethodParameter parameter = new MethodParameter(this.method, 1);

		assertTrue(this.resolver.supportsParameter(parameter));
		assertThatExceptionOfType(MessageConversionException.class).isThrownBy(() ->
				this.resolver.resolveArgument(parameter, message))
			.withMessageContaining(Integer.class.getName())
			.withMessageContaining(String.class.getName());
	}

	@Test
	public void resolveWithConversionEmptyPayloadButNoConverter() throws Exception {
		this.resolver = new MessageMethodArgumentResolver();

		Message<String> message = MessageBuilder.withPayload("").build();
		MethodParameter parameter = new MethodParameter(this.method, 1);

		assertTrue(this.resolver.supportsParameter(parameter));
		assertThatExceptionOfType(MessageConversionException.class).isThrownBy(() ->
				this.resolver.resolveArgument(parameter, message))
			.withMessageContaining("payload is empty")
			.withMessageContaining(Integer.class.getName())
			.withMessageContaining(String.class.getName());
	}

	@Test // SPR-16486
	public void resolveWithJacksonConverter() throws Exception {
		Message<String> inMessage = MessageBuilder.withPayload("{\"foo\":\"bar\"}").build();
		MethodParameter parameter = new MethodParameter(this.method, 5);

		this.resolver = new MessageMethodArgumentResolver(new MappingJackson2MessageConverter());
		Object actual = this.resolver.resolveArgument(parameter, inMessage);

		assertTrue(actual instanceof Message);
		Message<?> outMessage = (Message<?>) actual;
		assertTrue(outMessage.getPayload() instanceof Foo);
		assertEquals("bar", ((Foo) outMessage.getPayload()).getFoo());
	}


	@SuppressWarnings("unused")
	private void handle(
			Message<?> wildcardPayload,
			Message<Integer> integerPayload,
			Message<Number> numberPayload,
			Message<? extends Number> anyNumberPayload,
			ErrorMessage subClass,
			Message<Foo> fooPayload) {
	}


	static class Foo {

		private String foo;

		public String getFoo() {
			return foo;
		}

		public void setFoo(String foo) {
			this.foo = foo;
		}

	}

}
