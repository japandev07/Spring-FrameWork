/*
 * Copyright 2002-2012 the original author or authors.
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

package org.springframework.mock.web.portlet;

import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Vector;
import javax.portlet.PortletContext;
import javax.portlet.PortletSession;
import javax.servlet.http.HttpSessionBindingEvent;
import javax.servlet.http.HttpSessionBindingListener;

import org.springframework.mock.web.MockHttpSession;

/**
 * Mock implementation of the {@link javax.portlet.PortletSession} interface.
 *
 * @author John A. Lewis
 * @author Juergen Hoeller
 * @since 2.0
 */
public class MockPortletSession implements PortletSession {

	private static int nextId = 1;


	private final String id = Integer.toString(nextId++);

	private final long creationTime = System.currentTimeMillis();

	private int maxInactiveInterval;

	private long lastAccessedTime = System.currentTimeMillis();

	private final PortletContext portletContext;

	private final Map<String, Object> portletAttributes = new HashMap<String, Object>();

	private final Map<String, Object> applicationAttributes = new HashMap<String, Object>();

	private boolean invalid = false;

	private boolean isNew = true;


	/**
	 * Create a new MockPortletSession with a default {@link MockPortletContext}.
	 * @see MockPortletContext
	 */
	public MockPortletSession() {
		this(null);
	}

	/**
	 * Create a new MockPortletSession.
	 * @param portletContext the PortletContext that the session runs in
	 */
	public MockPortletSession(PortletContext portletContext) {
		this.portletContext = (portletContext != null ? portletContext : new MockPortletContext());
	}


	public Object getAttribute(String name) {
		return this.portletAttributes.get(name);
	}

	public Object getAttribute(String name, int scope) {
		if (scope == PortletSession.PORTLET_SCOPE) {
			return this.portletAttributes.get(name);
		}
		else if (scope == PortletSession.APPLICATION_SCOPE) {
			return this.applicationAttributes.get(name);
		}
		return null;
	}

	public Enumeration<String> getAttributeNames() {
		return new Vector<String>(this.portletAttributes.keySet()).elements();
	}

	public Enumeration<String> getAttributeNames(int scope) {
		if (scope == PortletSession.PORTLET_SCOPE) {
			return new Vector<String>(this.portletAttributes.keySet()).elements();
		}
		else if (scope == PortletSession.APPLICATION_SCOPE) {
			return new Vector<String>(this.applicationAttributes.keySet()).elements();
		}
		return null;
	}

	public long getCreationTime() {
		return this.creationTime;
	}

	public String getId() {
		return this.id;
	}

	public void access() {
		this.lastAccessedTime = System.currentTimeMillis();
		setNew(false);
	}

	public long getLastAccessedTime() {
		return this.lastAccessedTime;
	}

	public int getMaxInactiveInterval() {
		return this.maxInactiveInterval;
	}

	/**
	 * Clear all of this session's attributes.
	 */
	public void clearAttributes() {
		doClearAttributes(this.portletAttributes);
		doClearAttributes(this.applicationAttributes);
	}

	protected void doClearAttributes(Map<String, Object> attributes) {
		for (Iterator<Map.Entry<String, Object>> it = attributes.entrySet().iterator(); it.hasNext();) {
			Map.Entry<String, Object> entry = it.next();
			String name = entry.getKey();
			Object value = entry.getValue();
			it.remove();
			if (value instanceof HttpSessionBindingListener) {
				((HttpSessionBindingListener) value).valueUnbound(
						new HttpSessionBindingEvent(new MockHttpSession(), name, value));
			}
		}
	}

	public void invalidate() {
		this.invalid = true;
		clearAttributes();
	}

	public boolean isInvalid() {
		return this.invalid;
	}

	public void setNew(boolean value) {
		this.isNew = value;
	}

	public boolean isNew() {
		return this.isNew;
	}

	public void removeAttribute(String name) {
		this.portletAttributes.remove(name);
	}

	public void removeAttribute(String name, int scope) {
		if (scope == PortletSession.PORTLET_SCOPE) {
			this.portletAttributes.remove(name);
		}
		else if (scope == PortletSession.APPLICATION_SCOPE) {
			this.applicationAttributes.remove(name);
		}
	}

	public void setAttribute(String name, Object value) {
		if (value != null) {
			this.portletAttributes.put(name, value);
		}
		else {
			this.portletAttributes.remove(name);
		}
	}

	public void setAttribute(String name, Object value, int scope) {
		if (scope == PortletSession.PORTLET_SCOPE) {
			if (value != null) {
				this.portletAttributes.put(name, value);
			}
			else {
				this.portletAttributes.remove(name);
			}
		}
		else if (scope == PortletSession.APPLICATION_SCOPE) {
			if (value != null) {
				this.applicationAttributes.put(name, value);
			}
			else {
				this.applicationAttributes.remove(name);
			}
		}
	}

	public void setMaxInactiveInterval(int interval) {
		this.maxInactiveInterval = interval;
	}

	public PortletContext getPortletContext() {
		return this.portletContext;
	}

	public Map<String, Object> getAttributeMap() {
		return Collections.unmodifiableMap(this.portletAttributes);
	}

	public Map<String, Object> getAttributeMap(int scope) {
		if (scope == PortletSession.PORTLET_SCOPE) {
			return Collections.unmodifiableMap(this.portletAttributes);
		}
		else if (scope == PortletSession.APPLICATION_SCOPE) {
			return Collections.unmodifiableMap(this.applicationAttributes);
		}
		else {
			return Collections.emptyMap();
		}
	}

}
