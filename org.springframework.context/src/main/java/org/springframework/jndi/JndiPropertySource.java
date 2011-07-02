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

package org.springframework.jndi;

import javax.naming.NamingException;

import org.springframework.core.env.PropertySource;

/**
 * {@link PropertySource} implementation that reads properties from an underlying Spring
 * {@link JndiLocatorDelegate}.
 *
 * <p>By default, the underlying {@code JndiLocatorDelegate} will be configured with its
 * {@link JndiLocatorDelegate#setResourceRef(boolean) "resourceRef"} property set to
 * {@code true}, meaning that names looked up will automatically be prefixed with
 * "java:comp/env/" in alignment with published
 * <a href="http://download.oracle.com/javase/jndi/tutorial/beyond/misc/policy.html">JNDI
 * naming conventions</a>. To override this setting or to change the prefix, manually
 * configure a {@code JndiLocatorDelegate} and provide it to one of the constructors here
 * that accepts it. The same applies when providing custom JNDI properties. These should
 * be specified using {@link JndiLocatorDelegate#setJndiEnvironment(java.util.Properties)}
 * prior to construction of the {@code JndiPropertySource}.
 *
 * <p>{@link org.springframework.web.context.support.StandardServletEnvironment
 * StandardServletEnvironment} allows for declaratively including a
 * {@code JndiPropertySource} through its support for a {@link
 * org.springframework.web.context.support.StandardServletEnvironment#JNDI_PROPERTY_SOURCE_ENABLED
 * JNDI_PROPERTY_SOURCE_ENABLED} context-param, but any customization of the underlying
 * {@link JndiLocatorDelegate} will typically be done within an {@link
 * org.springframework.context.ApplicationContextInitializer ApplicationContextInitializer}
 * or {@link org.springframework.web.WebApplicationInitializer WebApplicationInitializer}.
 *
 * @author Chris Beams
 * @author Juergen Hoeller
 * @since 3.1
 * @see JndiLocatorDelegate
 * @see org.springframework.context.ApplicationContextInitializer
 * @see org.springframework.web.WebApplicationInitializer
 * @see org.springframework.web.context.support.StandardServletEnvironment
 */
public class JndiPropertySource extends PropertySource<JndiLocatorDelegate> {

	/** JNDI context property source name: {@value} */
	public static final String JNDI_PROPERTY_SOURCE_NAME = "jndiPropertySource";

	/**
	 * Create a new {@code JndiPropertySource} with the default name
	 * {@value #JNDI_PROPERTY_SOURCE_NAME} and a {@link JndiLocatorDelegate} configured
	 * to prefix any names with "java:comp/env/".
	 */
	public JndiPropertySource() {
		this(JNDI_PROPERTY_SOURCE_NAME);
	}

	/**
	 * Create a new {@code JndiPropertySource} with the given name
	 * and a {@link JndiLocatorDelegate} configured to prefix any names with
	 * "java:comp/env/".
	 */
	public JndiPropertySource(String name) {
		this(name, createDefaultJndiLocator());
	}

	/**
	 * Create a new {@code JndiPropertySource} with the default name
	 * {@value #JNDI_PROPERTY_SOURCE_NAME} and the given {@code JndiLocatorDelegate}.
	 */
	public JndiPropertySource(JndiLocatorDelegate jndiLocator) {
		this(JNDI_PROPERTY_SOURCE_NAME, jndiLocator);
	}

	/**
	 * Create a new {@code JndiPropertySource} with the given name and the given
	 * {@code JndiLocatorDelegate}.
	 */
	public JndiPropertySource(String name, JndiLocatorDelegate jndiLocator) {
		super(name, jndiLocator);
	}

	/**
	 * {@inheritDoc}
	 * <p>This implementation looks up and returns the value associated with the given
	 * name from the underlying {@link JndiLocatorDelegate}. If a {@link NamingException}
	 * is thrown during the call to {@link JndiLocatorDelegate#lookup(String)}, returns
	 * {@code null} and issues a DEBUG-level log statement with the exception message.
	 */
	@Override
	public Object getProperty(String name) {
		try {
			Object value = this.source.lookup(name);
			logger.debug("JNDI lookup for name [" + name + "] returned: [" + value + "]");
			return value;
		} catch (NamingException ex) {
			logger.debug("JNDI lookup for name [" + name + "] threw NamingException " +
					"with message: " + ex.getMessage() + ". Returning null.");
			return null;
		}
	}

	/**
	 * Configure a {@code JndiLocatorDelegate} with its "resourceRef" property set to true
	 * meaning that all names will be prefixed with "java:comp/env/".
	 * @return
	 */
	private static JndiLocatorDelegate createDefaultJndiLocator() {
		JndiLocatorDelegate jndiLocator = new JndiLocatorDelegate();
		jndiLocator.setResourceRef(true);
		return jndiLocator;
	}

}
