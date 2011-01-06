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

package org.springframework.web.context.support;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;

import org.springframework.core.env.DefaultEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.PropertySource.StubPropertySource;
import org.springframework.core.env.PropertySources;
import org.springframework.jndi.JndiPropertySource;

/**
 * {@link Environment} implementation to be used by {@code Servlet}-based web
 * applications. All web-related (servlet-based) {@code ApplicationContext} classes
 * initialize an instance by default.
 *
 * <p>Contributes {@code ServletConfig}- and {@code ServletContext}-based {@link PropertySource}
 * instances. See the {@link #DefaultWebEnvironment()} constructor for details.
 *
 * <p>After initial bootstrapping, property sources will be searched for the presence of a
 * "jndiPropertySourceEnabled" property; if found, a {@link JndiPropertySource} will be added
 * to this environment's {@link PropertySources}, with precedence higher than system properties
 * and environment variables, but lower than that of ServletContext and ServletConfig init params.
 *
 * @author Chris Beams
 * @since 3.1
 * @see DefaultEnvironment
 * @see DefaultPortletEnvironment
 */
public class DefaultWebEnvironment extends DefaultEnvironment {

	/** Servlet context init parameters property source name: {@value} */
	public static final String SERVLET_CONTEXT_PROPERTY_SOURCE_NAME = "servletContextInitParams";

	/** Servlet config init parameters property source name: {@value} */
	public static final String SERVLET_CONFIG_PROPERTY_SOURCE_NAME = "servletConfigInitParams";

	/**
	 * Create a new {@code Environment} populated with the property sources contributed by
	 * superclasses as well as:
	 * <ul>
	 *   <li>{@value #SERVLET_CONFIG_PROPERTY_SOURCE_NAME}
	 *   <li>{@value #SERVLET_CONTEXT_PROPERTY_SOURCE_NAME}
	 *   <li>(optionally) {@link JndiPropertySource#JNDI_PROPERTY_SOURCE_NAME "jndiPropertySource"}
	 * </ul>
	 * <p>Properties present in {@value #SERVLET_CONFIG_PROPERTY_SOURCE_NAME} will
	 * take precedence over those in {@value #SERVLET_CONTEXT_PROPERTY_SOURCE_NAME}.
	 * Properties in either will take precedence over system properties and environment
	 * variables.
	 * <p>The {@code Servlet}-related property sources are added as stubs for now, and will be
	 * {@linkplain WebApplicationContextUtils#initServletPropertySources fully initialized}
	 * once the actual {@link ServletConfig} and {@link ServletContext} objects are available.
	 *
	 * <p>If the {@link JndiPropertySource#JNDI_PROPERTY_SOURCE_ENABLED_FLAG "jndiPropertySourceEnabled"}
	 * property is present in any of the default property sources, a {@link JndiPropertySource} will
	 * be added as well, with precedence lower than servlet property sources, but higher than system
	 * properties and environment variables.
	 * @see DefaultEnvironment#DefaultEnvironment
	 * @see ServletConfigPropertySource
	 * @see ServletContextPropertySource
	 * @see org.springframework.jndi.JndiPropertySource
	 * @see org.springframework.context.support.AbstractApplicationContext#initPropertySources
	 * @see WebApplicationContextUtils#initServletPropertySources
	 */
	public DefaultWebEnvironment() {
		this.getPropertySources().addFirst(new StubPropertySource(SERVLET_CONTEXT_PROPERTY_SOURCE_NAME));
		this.getPropertySources().addFirst(new StubPropertySource(SERVLET_CONFIG_PROPERTY_SOURCE_NAME));

		Boolean jndiPropertySourceEnabled = this.getProperty(JndiPropertySource.JNDI_PROPERTY_SOURCE_ENABLED_FLAG, boolean.class);
		if (jndiPropertySourceEnabled != null && jndiPropertySourceEnabled != false) {
			this.getPropertySources().addAfter(SERVLET_CONTEXT_PROPERTY_SOURCE_NAME, new JndiPropertySource());
		}
	}
}
