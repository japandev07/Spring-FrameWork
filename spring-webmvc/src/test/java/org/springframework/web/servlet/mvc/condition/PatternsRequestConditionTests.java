/*
 * Copyright 2002-2011 the original author or authors.
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

package org.springframework.web.servlet.mvc.condition;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import javax.servlet.http.HttpServletRequest;

import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * @author Rossen Stoyanchev
 */
public class PatternsRequestConditionTests {

	@Test
	public void prependSlash() {
		PatternsRequestCondition c = new PatternsRequestCondition("foo");
		assertEquals("/foo", c.getPatterns().iterator().next());
	}

	@Test
	public void prependNonEmptyPatternsOnly() {
		PatternsRequestCondition c = new PatternsRequestCondition("");
		assertEquals("Do not prepend empty patterns (SPR-8255)", "", c.getPatterns().iterator().next());
	}

	@Test
	public void combineEmptySets() {
		PatternsRequestCondition c1 = new PatternsRequestCondition();
		PatternsRequestCondition c2 = new PatternsRequestCondition();
		
		assertEquals(new PatternsRequestCondition(""), c1.combine(c2));
	}
	
	@Test
	public void combineOnePatternWithEmptySet() {
		PatternsRequestCondition c1 = new PatternsRequestCondition("/type1", "/type2");
		PatternsRequestCondition c2 = new PatternsRequestCondition();
		
		assertEquals(new PatternsRequestCondition("/type1", "/type2"), c1.combine(c2));

		c1 = new PatternsRequestCondition();
		c2 = new PatternsRequestCondition("/method1", "/method2");
		
		assertEquals(new PatternsRequestCondition("/method1", "/method2"), c1.combine(c2));
	}

	@Test
	public void combineMultiplePatterns() {
		PatternsRequestCondition c1 = new PatternsRequestCondition("/t1", "/t2");
		PatternsRequestCondition c2 = new PatternsRequestCondition("/m1", "/m2");
		
		assertEquals(new PatternsRequestCondition("/t1/m1", "/t1/m2", "/t2/m1", "/t2/m2"), c1.combine(c2));
	}	

	@Test
	public void matchDirectPath() {
		PatternsRequestCondition condition = new PatternsRequestCondition("/foo");
		PatternsRequestCondition match = condition.getMatchingCondition(new MockHttpServletRequest("GET", "/foo"));

		assertNotNull(match);
	}
	
	@Test
	public void matchPattern() {
		PatternsRequestCondition condition = new PatternsRequestCondition("/foo/*");
		PatternsRequestCondition match = condition.getMatchingCondition(new MockHttpServletRequest("GET", "/foo/bar"));
		
		assertNotNull(match);
	}
	
	@Test
	public void matchSortPatterns() {
		PatternsRequestCondition condition = new PatternsRequestCondition("/**", "/foo/bar", "/foo/*");
		PatternsRequestCondition match = condition.getMatchingCondition(new MockHttpServletRequest("GET", "/foo/bar"));
		PatternsRequestCondition expected = new PatternsRequestCondition("/foo/bar", "/foo/*", "/**");
		
		assertEquals(expected, match);
	}

	@Test
	public void matchSuffixPattern() { 
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/foo.html");
		
		PatternsRequestCondition condition = new PatternsRequestCondition("/{foo}");
		PatternsRequestCondition match = condition.getMatchingCondition(request);

		assertNotNull(match);
		assertEquals("/{foo}.*", match.getPatterns().iterator().next());

		condition = new PatternsRequestCondition(new String[] {"/{foo}"}, null, null, false, false);
		match = condition.getMatchingCondition(request);

		assertNotNull(match);
		assertEquals("/{foo}", match.getPatterns().iterator().next());
	}

	@Test
	public void matchTrailingSlash() {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/foo/");
		
		PatternsRequestCondition condition = new PatternsRequestCondition("/foo");
		PatternsRequestCondition match = condition.getMatchingCondition(request);

		assertNotNull(match);
		assertEquals("Should match by default", "/foo/", match.getPatterns().iterator().next());
		
		condition = new PatternsRequestCondition(new String[] {"/foo"}, null, null, false, true);
		match = condition.getMatchingCondition(request);

		assertNotNull(match);
		assertEquals("Trailing slash should be insensitive to useSuffixPatternMatch settings (SPR-6164, SPR-5636)", 
				"/foo/", match.getPatterns().iterator().next());

		condition = new PatternsRequestCondition(new String[] {"/foo"}, null, null, false, false);
		match = condition.getMatchingCondition(request);

		assertNull(match);
	}

	@Test
	public void matchPatternContainsExtension() {
		PatternsRequestCondition condition = new PatternsRequestCondition("/foo.jpg");
		PatternsRequestCondition match = condition.getMatchingCondition(new MockHttpServletRequest("GET", "/foo.html"));

		assertNull(match);
	}

	@Test
	public void compareEqualPatterns() {
		PatternsRequestCondition c1 = new PatternsRequestCondition("/foo*");
		PatternsRequestCondition c2 = new PatternsRequestCondition("/foo*");

		assertEquals(0, c1.compareTo(c2, new MockHttpServletRequest("GET", "/foo")));
	}

	@Test
	public void comparePatternSpecificity() {
		PatternsRequestCondition c1 = new PatternsRequestCondition("/fo*");
		PatternsRequestCondition c2 = new PatternsRequestCondition("/foo");
		
		assertEquals(1, c1.compareTo(c2, new MockHttpServletRequest("GET", "/foo")));
	}

	@Test
	public void compareNumberOfMatchingPatterns() throws Exception {
		HttpServletRequest request = new MockHttpServletRequest("GET", "/foo.html");
		
		PatternsRequestCondition c1 = new PatternsRequestCondition("/foo", "*.jpeg");
		PatternsRequestCondition c2 = new PatternsRequestCondition("/foo", "*.html");

		PatternsRequestCondition match1 = c1.getMatchingCondition(request);
		PatternsRequestCondition match2 = c2.getMatchingCondition(request);
		
		assertEquals(1, match1.compareTo(match2, request));
	}
	
}
