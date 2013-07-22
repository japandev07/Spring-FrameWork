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

package org.springframework.web.servlet.tags;

import javax.servlet.jsp.JspException;
import javax.servlet.jsp.PageContext;
import javax.servlet.jsp.tagext.Tag;
import javax.servlet.jsp.tagext.TagSupport;

import org.springframework.mock.web.test.MockBodyContent;
import org.springframework.mock.web.test.MockHttpServletResponse;

/**
 * Unit tests for ArgumentTag
 *
 * @author Nicholas Williams
 */
public class ArgumentTagTests extends AbstractTagTests {

	private ArgumentTag tag;

	private MockArgumentSupportTag parent;

	@Override
	protected void setUp() throws Exception {
		PageContext context = createPageContext();
		parent = new MockArgumentSupportTag();
		tag = new ArgumentTag();
		tag.setPageContext(context);
		tag.setParent(parent);
	}

	public void testArgumentWithStringValue() throws JspException {
		tag.setValue("value1");

		int action = tag.doEndTag();

		assertEquals(Tag.EVAL_PAGE, action);
		assertEquals("value1", parent.getArgument());
	}

	public void testArgumentWithImplicitNullValue() throws JspException {
		int action = tag.doEndTag();

		assertEquals(Tag.EVAL_PAGE, action);
		assertNull(parent.getArgument());
	}

	public void testArgumentWithExplicitNullValue() throws JspException {
		tag.setValue(null);

		int action = tag.doEndTag();

		assertEquals(Tag.EVAL_PAGE, action);
		assertNull(parent.getArgument());
	}

	public void testArgumentWithBodyValue() throws JspException {
		tag.setBodyContent(new MockBodyContent("value2",
				new MockHttpServletResponse()));

		int action = tag.doEndTag();

		assertEquals(Tag.EVAL_PAGE, action);
		assertEquals("value2", parent.getArgument());
	}

	public void testArgumentWithValueThenReleaseThenBodyValue() throws JspException {
		tag.setValue("value3");

		int action = tag.doEndTag();

		assertEquals(Tag.EVAL_PAGE, action);
		assertEquals("value3", parent.getArgument());

		tag.release();

		parent = new MockArgumentSupportTag();
		tag.setPageContext(createPageContext());
		tag.setParent(parent);
		tag.setBodyContent(new MockBodyContent("value4",
				new MockHttpServletResponse()));

		action = tag.doEndTag();

		assertEquals(Tag.EVAL_PAGE, action);
		assertEquals("value4", parent.getArgument());
	}

	@SuppressWarnings("serial")
	private class MockArgumentSupportTag extends TagSupport implements ArgumentAware {

		Object argument;

		@Override
		public void addArgument(Object argument) {
			this.argument = argument;
		}

		private Object getArgument() {
			return argument;
		}
	}

}
