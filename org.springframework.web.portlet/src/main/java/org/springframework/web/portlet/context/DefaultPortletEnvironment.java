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

package org.springframework.web.portlet.context;

import javax.portlet.PortletConfig;
import javax.portlet.PortletContext;
import javax.servlet.ServletContext;

import org.springframework.core.env.DefaultEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.PropertySource.StubPropertySource;
import org.springframework.web.context.support.DefaultWebEnvironment;

/**
 * {@link Environment} implementation to be used by {@code Servlet}-based web
 * applications. All Portlet-related {@code ApplicationContext} classes initialize an instance
 * by default.
 *
 * <p>Contributes {@code ServletContext}-, {@code PortletContext}-, and {@code PortletConfig}-based
 * {@link PropertySource} instances. See the {@link #DefaultPortletEnvironment()} constructor
 * for details.
 *
 * @author Chris Beams
 * @since 3.1
 * @see DefaultEnvironment
 * @see DefaultWebEnvironment
 */
public class DefaultPortletEnvironment extends DefaultEnvironment {

	/** Portlet context init parameters property source name: {@value} */
	public static final String PORTLET_CONTEXT_PROPERTY_SOURCE_NAME = "portletContextInitParams";

	/** Portlet config init parameters property source name: {@value} */
	public static final String PORTLET_CONFIG_PROPERTY_SOURCE_NAME = "portletConfigInitParams";

	/**
	 * Create a new {@code Environment} populated with the property sources contributed by
	 * superclasses as well as:
	 * <ul>
	 *   <li>{@value #PORTLET_CONFIG_PROPERTY_SOURCE_NAME}
	 *   <li>{@value #PORTLET_CONTEXT_PROPERTY_SOURCE_NAME}
	 *   <li>{@linkplain DefaultWebEnvironment#SERVLET_CONTEXT_PROPERTY_SOURCE_NAME "servletContextInitParams"}
	 * </ul>
	 * <p>Properties present in {@value #PORTLET_CONFIG_PROPERTY_SOURCE_NAME} will
	 * take precedence over those in {@value #PORTLET_CONTEXT_PROPERTY_SOURCE_NAME},
	 * which takes precedence over those in .
	 * Properties in either will take precedence over system properties and environment
	 * variables.
	 * <p>The property sources are added as stubs for now, and will be
	 * {@linkplain PortletApplicationContextUtils#initPortletPropertySources fully initialized}
	 * once the actual {@link PortletConfig}, {@link PortletContext}, and {@link ServletContext}
	 * objects are available.
	 * @see DefaultEnvironment#DefaultEnvironment
	 * @see PortletConfigPropertySource
	 * @see PortletContextPropertySource
	 * @see AbstractRefreshablePortletApplicationContext#initPropertySources
	 * @see PortletApplicationContextUtils#initPortletPropertySources
	 */
	public DefaultPortletEnvironment() {
		this.getPropertySources().addFirst(new StubPropertySource(DefaultWebEnvironment.SERVLET_CONTEXT_PROPERTY_SOURCE_NAME));
		this.getPropertySources().addFirst(new StubPropertySource(PORTLET_CONTEXT_PROPERTY_SOURCE_NAME));
		this.getPropertySources().addFirst(new StubPropertySource(PORTLET_CONFIG_PROPERTY_SOURCE_NAME));
	}
}
