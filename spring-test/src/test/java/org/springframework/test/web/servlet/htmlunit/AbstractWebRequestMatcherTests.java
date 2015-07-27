/*
 * Copyright 2002-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.springframework.test.web.servlet.htmlunit;

import java.net.MalformedURLException;
import java.net.URL;

import com.gargoylesoftware.htmlunit.WebRequest;

import static org.junit.Assert.*;

/**
 * Abstract base class for testing {@link WebRequestMatcher} implementations.
 *
 * @author Sam Brannen
 * @since 4.2
 */
public class AbstractWebRequestMatcherTests {

	protected void assertMatches(WebRequestMatcher matcher, String url) throws MalformedURLException {
		assertTrue(matcher.matches(new WebRequest(new URL(url))));
	}

	protected void assertDoesNotMatch(WebRequestMatcher matcher, String url) throws MalformedURLException {
		assertFalse(matcher.matches(new WebRequest(new URL(url))));
	}

}
