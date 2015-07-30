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

package org.springframework.web.servlet.mvc.annotation;

import static org.junit.Assert.*;

import java.util.Locale;

import org.junit.Before;
import org.junit.Test;

import org.springframework.beans.TypeMismatchException;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.test.MockHttpServletRequest;
import org.springframework.mock.web.test.MockHttpServletResponse;
import org.springframework.tests.sample.beans.ITestBean;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.ModelAndView;

/** @author Arjen Poutsma */
public class ResponseStatusExceptionResolverTests {

	private ResponseStatusExceptionResolver exceptionResolver;

	private MockHttpServletRequest request;

	private MockHttpServletResponse response;

	@Before
	public void setUp() {
		exceptionResolver = new ResponseStatusExceptionResolver();
		request = new MockHttpServletRequest();
		response = new MockHttpServletResponse();
		request.setMethod("GET");
	}

	@Test
	public void statusCode() {
		StatusCodeException ex = new StatusCodeException();
		ModelAndView mav = exceptionResolver.resolveException(request, response, null, ex);
		assertNotNull("No ModelAndView returned", mav);
		assertTrue("No Empty ModelAndView returned", mav.isEmpty());
		assertEquals("Invalid status code", 400, response.getStatus());
		assertTrue("Response has not been committed", response.isCommitted());
	}

	@Test
	public void statusCodeAndReason() {
		StatusCodeAndReasonException ex = new StatusCodeAndReasonException();
		ModelAndView mav = exceptionResolver.resolveException(request, response, null, ex);
		assertNotNull("No ModelAndView returned", mav);
		assertTrue("No Empty ModelAndView returned", mav.isEmpty());
		assertEquals("Invalid status code", 410, response.getStatus());
		assertEquals("Invalid status reason", "You suck!", response.getErrorMessage());
		assertTrue("Response has not been committed", response.isCommitted());
	}

	@Test
	public void statusCodeAndReasonMessage() {
		Locale locale = Locale.CHINESE;
		LocaleContextHolder.setLocale(locale);
		try {
			StaticMessageSource messageSource = new StaticMessageSource();
			messageSource.addMessage("gone.reason", locale, "Gone reason message");
			exceptionResolver.setMessageSource(messageSource);

			StatusCodeAndReasonMessageException ex = new StatusCodeAndReasonMessageException();
			exceptionResolver.resolveException(request, response, null, ex);
			assertEquals("Invalid status reason", "Gone reason message", response.getErrorMessage());
		}
		finally {
			LocaleContextHolder.resetLocaleContext();
		}
	}

	@Test
	public void notAnnotated() {
		Exception ex = new Exception();
		exceptionResolver.resolveException(request, response, null, ex);
		ModelAndView mav = exceptionResolver.resolveException(request, response, null, ex);
		assertNull("ModelAndView returned", mav);
	}

	// SPR-12903

	@Test
	public void nestedException() throws Exception {
		Exception cause = new StatusCodeAndReasonMessageException();
		TypeMismatchException ex = new TypeMismatchException("value", ITestBean.class, cause);
		ModelAndView mav = exceptionResolver.resolveException(request, response, null, ex);
		assertNotNull("No ModelAndView returned", mav);
		assertTrue("No Empty ModelAndView returned", mav.isEmpty());
		assertEquals("Invalid status code", 410, response.getStatus());
	}

	@ResponseStatus(HttpStatus.BAD_REQUEST)
	@SuppressWarnings("serial")
	private static class StatusCodeException extends Exception {
	}

	@ResponseStatus(code = HttpStatus.GONE, reason = "You suck!")
	@SuppressWarnings("serial")
	private static class StatusCodeAndReasonException extends Exception {
	}

	@ResponseStatus(code = HttpStatus.GONE, reason = "gone.reason")
	@SuppressWarnings("serial")
	private static class StatusCodeAndReasonMessageException extends Exception {
	}

}
