/*
 * Copyright 2002-2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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

import org.springframework.core.LocalVariableTableParameterNameDiscoverer;
import org.springframework.core.MethodParameter;
import org.springframework.messaging.Message;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.Assert;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;
import org.springframework.validation.annotation.Validated;

/**
 * Test fixture for {@link PayloadArgumentResolver}.
 *
 * @author Rossen Stoyanchev
 * @author Brian Clozel
 * @author Stephane Nicoll
 */
public class PayloadArgumentResolverTests {

	private PayloadArgumentResolver resolver;

	private Method payloadMethod;

	private MethodParameter paramAnnotated;

	private MethodParameter paramAnnotatedNotRequired;

	private MethodParameter paramAnnotatedRequired;

	private MethodParameter paramWithSpelExpression;

	private MethodParameter paramNotAnnotated;

	private MethodParameter paramValidatedNotAnnotated;

	private MethodParameter paramValidated;


	@Rule
	public final ExpectedException thrown = ExpectedException.none();


	@Before
	public void setup() throws Exception {

		this.resolver = new PayloadArgumentResolver(new StringMessageConverter(), testValidator());

		payloadMethod = PayloadArgumentResolverTests.class.getDeclaredMethod("handleMessage",
				String.class, String.class, Locale.class, String.class, String.class, String.class, String.class);

		this.paramAnnotated = getMethodParameter(this.payloadMethod, 0);
		this.paramAnnotatedNotRequired = getMethodParameter(this.payloadMethod, 1);
		this.paramAnnotatedRequired = getMethodParameter(payloadMethod, 2);
		this.paramWithSpelExpression = getMethodParameter(payloadMethod, 3);
		this.paramValidated = getMethodParameter(this.payloadMethod, 4);
		this.paramValidated.initParameterNameDiscovery(new LocalVariableTableParameterNameDiscoverer());
		this.paramValidatedNotAnnotated = getMethodParameter(this.payloadMethod, 5);
		this.paramNotAnnotated = getMethodParameter(this.payloadMethod, 6);
	}


	@Test
	public void resolveRequired() throws Exception {
		Message<?> message = MessageBuilder.withPayload("ABC".getBytes()).build();
		Object actual = this.resolver.resolveArgument(paramAnnotated, message);

		assertEquals("ABC", actual);
	}

	@Test
	public void resolveRequiredEmpty() throws Exception {
		Message<?> message = MessageBuilder.withPayload("").build();

		thrown.expect(MethodArgumentNotValidException.class); // Required but empty
		this.resolver.resolveArgument(paramAnnotated, message);
	}

	@Test
	public void resolveRequiredEmptyNonAnnotatedParameter() throws Exception {
		Message<?> message = MessageBuilder.withPayload("").build();

		thrown.expect(MethodArgumentNotValidException.class); // Required but empty
		this.resolver.resolveArgument(this.paramNotAnnotated, message);
	}

	@Test
	public void resolveNotRequired() throws Exception {

		Message<?> emptyByteArrayMessage = MessageBuilder.withPayload(new byte[0]).build();
		assertNull(this.resolver.resolveArgument(this.paramAnnotatedNotRequired, emptyByteArrayMessage));

		Message<?> emptyStringMessage = MessageBuilder.withPayload("").build();
		assertNull(this.resolver.resolveArgument(this.paramAnnotatedNotRequired, emptyStringMessage));

		Message<?> notEmptyMessage = MessageBuilder.withPayload("ABC".getBytes()).build();
		assertEquals("ABC", this.resolver.resolveArgument(this.paramAnnotatedNotRequired, notEmptyMessage));
	}

	@Test
	public void resolveNonConvertibleParam() throws Exception {
		Message<?> notEmptyMessage = MessageBuilder.withPayload(123).build();

		thrown.expect(MessageConversionException.class);
		thrown.expectMessage("No converter found");
		this.resolver.resolveArgument(this.paramAnnotatedRequired, notEmptyMessage);
	}

	@Test
	public void resolveSpelExpressionNotSupported() throws Exception {
		Message<?> message = MessageBuilder.withPayload("ABC".getBytes()).build();

		thrown.expect(IllegalStateException.class);
		this.resolver.resolveArgument(paramWithSpelExpression, message);
	}

	@Test
	public void resolveValidation() throws Exception {
		Message<?> message = MessageBuilder.withPayload("ABC".getBytes()).build();
		this.resolver.resolveArgument(this.paramValidated, message);
	}

	@Test
	public void resolveFailValidation() throws Exception {
		// See testValidator()
		Message<?> message = MessageBuilder.withPayload("invalidValue".getBytes()).build();

		thrown.expect(MethodArgumentNotValidException.class);
		this.resolver.resolveArgument(this.paramValidated, message);
	}

	@Test
	public void resolveFailValidationNoConversionNecessary() throws Exception {
		Message<?> message = MessageBuilder.withPayload("invalidValue").build();

		thrown.expect(MethodArgumentNotValidException.class);
		this.resolver.resolveArgument(this.paramValidated, message);
	}

	@Test
	public void resolveNonAnnotatedParameter() throws Exception {

		Message<?> notEmptyMessage = MessageBuilder.withPayload("ABC".getBytes()).build();
		assertEquals("ABC", this.resolver.resolveArgument(this.paramNotAnnotated, notEmptyMessage));

		Message<?> emptyStringMessage = MessageBuilder.withPayload("").build();
		thrown.expect(MethodArgumentNotValidException.class);
		this.resolver.resolveArgument(this.paramValidated, emptyStringMessage);

	}

	@Test
	public void resolveNonAnnotatedParameterFailValidation() throws Exception {
		// See testValidator()
		Message<?> message = MessageBuilder.withPayload("invalidValue".getBytes()).build();

		thrown.expect(MethodArgumentNotValidException.class);
		thrown.expectMessage("invalid value");
		assertEquals("invalidValue", this.resolver.resolveArgument(this.paramValidatedNotAnnotated, message));
	}

	private Validator testValidator() {

		return new Validator() {
			@Override
			public boolean supports(Class<?> clazz) {
				return String.class.isAssignableFrom(clazz);
			}
			@Override
			public void validate(Object target, Errors errors) {
				String value = (String) target;
				if ("invalidValue".equals(value)) {
					errors.reject("invalid value");
				}
			}
		};
	}

	private MethodParameter getMethodParameter(Method method, int index) {
		Assert.notNull(method, "Method must be set");
		return new MethodParameter(method, index);
	}

	@SuppressWarnings("unused")
	private void handleMessage(
			@Payload String param,
			@Payload(required=false) String paramNotRequired,
			@Payload(required=true) Locale nonConvertibleRequiredParam,
			@Payload("foo.bar") String paramWithSpelExpression,
			@Validated @Payload String validParam,
			@Validated String validParamNotAnnotated,
			String paramNotAnnotated) {
	}

}
