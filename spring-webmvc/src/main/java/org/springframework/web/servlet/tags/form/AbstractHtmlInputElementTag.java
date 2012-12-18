/*
 * Copyright 2002-2010 the original author or authors.
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

package org.springframework.web.servlet.tags.form;

import javax.servlet.jsp.JspException;

/**
 * Base class for databinding-aware JSP tags that render HTML form input element.
 *
 * <p>Provides a set of properties corresponding to the set of HTML attributes
 * that are common across form input elements.
 *
 * @author Rob Harrop
 * @author Rick Evans
 * @author Juergen Hoeller
 * @since 2.0
 */
public abstract class AbstractHtmlInputElementTag extends AbstractHtmlElementTag {

	/**
	 * The name of the '<code>onfocus</code>' attribute.
	 */
	public static final String ONFOCUS_ATTRIBUTE = "onfocus";

	/**
	 * The name of the '<code>onblur</code>' attribute.
	 */
	public static final String ONBLUR_ATTRIBUTE = "onblur";

	/**
	 * The name of the '<code>onchange</code>' attribute.
	 */
	public static final String ONCHANGE_ATTRIBUTE = "onchange";

	/**
	 * The name of the '<code>accesskey</code>' attribute.
	 */
	public static final String ACCESSKEY_ATTRIBUTE = "accesskey";

	/**
	 * The name of the '<code>disabled</code>' attribute.
	 */
	public static final String DISABLED_ATTRIBUTE = "disabled";

	/**
	 * The name of the '<code>readonly</code>' attribute.
	 */
	public static final String READONLY_ATTRIBUTE = "readonly";


	private String onfocus;

	private String onblur;

	private String onchange;

	private String accesskey;

	private String disabled;

	private String readonly;


	/**
	 * Set the value of the '<code>onfocus</code>' attribute.
	 * May be a runtime expression.
	 */
	public void setOnfocus(String onfocus) {
		this.onfocus = onfocus;
	}

	/**
	 * Get the value of the '<code>onfocus</code>' attribute.
	 */
	protected String getOnfocus() {
		return this.onfocus;
	}

	/**
	 * Set the value of the '<code>onblur</code>' attribute.
	 * May be a runtime expression.
	 */
	public void setOnblur(String onblur) {
		this.onblur = onblur;
	}

	/**
	 * Get the value of the '<code>onblur</code>' attribute.
	 */
	protected String getOnblur() {
		return this.onblur;
	}

	/**
	 * Set the value of the '<code>onchange</code>' attribute.
	 * May be a runtime expression.
	 */
	public void setOnchange(String onchange) {
		this.onchange = onchange;
	}

	/**
	 * Get the value of the '<code>onchange</code>' attribute.
	 */
	protected String getOnchange() {
		return this.onchange;
	}

	/**
	 * Set the value of the '<code>accesskey</code>' attribute.
	 * May be a runtime expression.
	 */
	public void setAccesskey(String accesskey) {
		this.accesskey = accesskey;
	}

	/**
	 * Get the value of the '<code>accesskey</code>' attribute.
	 */
	protected String getAccesskey() {
		return this.accesskey;
	}

	/**
	 * Set the value of the '<code>disabled</code>' attribute.
	 * May be a runtime expression.
	 */
	public void setDisabled(String disabled) {
		this.disabled = disabled;
	}

	/**
	 * Get the value of the '<code>disabled</code>' attribute.
	 */
	protected String getDisabled() {
		return this.disabled;
	}

	/**
	 * Sets the value of the '<code>readonly</code>' attribute.
	 * May be a runtime expression.
	 * @see #isReadonly()
	 */
	public void setReadonly(String readonly) {
		this.readonly = readonly;
	}

	/**
	 * Gets the value of the '<code>readonly</code>' attribute.
	 * May be a runtime expression.
	 * @see #isReadonly()
	 */
	protected String getReadonly() {
		return this.readonly;
	}


	/**
	 * Adds input-specific optional attributes as defined by this base class.
	 */
	@Override
	protected void writeOptionalAttributes(TagWriter tagWriter) throws JspException {
		super.writeOptionalAttributes(tagWriter);

		writeOptionalAttribute(tagWriter, ONFOCUS_ATTRIBUTE, getOnfocus());
		writeOptionalAttribute(tagWriter, ONBLUR_ATTRIBUTE, getOnblur());
		writeOptionalAttribute(tagWriter, ONCHANGE_ATTRIBUTE, getOnchange());
		writeOptionalAttribute(tagWriter, ACCESSKEY_ATTRIBUTE, getAccesskey());
		if (isDisabled()) {
			tagWriter.writeAttribute(DISABLED_ATTRIBUTE, "disabled");
		}
		if (isReadonly()) {
			writeOptionalAttribute(tagWriter, READONLY_ATTRIBUTE, "readonly");
		}
	}

	/**
	 * Is the current HTML tag disabled?
	 */
	protected boolean isDisabled() throws JspException {
		return evaluateBoolean(DISABLED_ATTRIBUTE, getDisabled());
	}

	/**
	 * Is the current HTML tag readonly?
	 * <p>Note: some {@link AbstractHtmlInputElementTag} subclasses (such a those
	 * for checkboxes and radiobuttons)  may contain readonly attributes, but are
	 * not affected by them since their values don't change (only their status does.)
	 */
	protected boolean isReadonly() throws JspException {
		return evaluateBoolean(READONLY_ATTRIBUTE, getReadonly());
	}

}
