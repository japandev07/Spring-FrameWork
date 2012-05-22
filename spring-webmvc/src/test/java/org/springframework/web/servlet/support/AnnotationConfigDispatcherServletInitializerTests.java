/*
 * Copyright 2002-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.web.servlet.support;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.ServletRegistration;

import org.junit.Before;
import org.junit.Test;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;

import static org.junit.Assert.*;

/**
 * Test case for {@link AbstractAnnotationConfigDispatcherServletInitializer}.
 *
 * @author Arjen Poutsma
 */
public class AnnotationConfigDispatcherServletInitializerTests {

	private static final String SERVLET_NAME = "myservlet";

	private static final String ROLE_NAME = "role";

	private static final String SERVLET_MAPPING = "/myservlet";

	private AbstractDispatcherServletInitializer initializer;

	private MockServletContext servletContext;

	private Map<String, Servlet> servlets;

	private Map<String, MockDynamic> registrations;

	@Before
	public void setUp() throws Exception {
		servletContext = new MyMockServletContext();
		initializer = new MyAnnotationConfigDispatcherServletInitializer();
		servlets = new LinkedHashMap<String, Servlet>(2);
		registrations = new LinkedHashMap<String, MockDynamic>(2);
	}

	@Test
	public void register() throws ServletException {
		initializer.onStartup(servletContext);

		assertEquals(1, servlets.size());
		assertNotNull(servlets.get(SERVLET_NAME));

		DispatcherServlet servlet = (DispatcherServlet) servlets.get(SERVLET_NAME);
		WebApplicationContext servletContext = servlet.getWebApplicationContext();
		((AnnotationConfigWebApplicationContext) servletContext).refresh();

		assertTrue(servletContext.containsBean("bean"));
		assertTrue(servletContext.getBean("bean") instanceof MyBean);

		assertEquals(1, registrations.size());
		assertNotNull(registrations.get(SERVLET_NAME));

		MockDynamic registration = registrations.get(SERVLET_NAME);
		assertEquals(Collections.singleton(SERVLET_MAPPING), registration.getMappings());
		assertEquals(1, registration.getLoadOnStartup());
		assertEquals(ROLE_NAME, registration.getRunAsRole());
	}

	private class MyMockServletContext extends MockServletContext {

		@Override
		public ServletRegistration.Dynamic addServlet(String servletName,
		                                              Servlet servlet) {
			servlets.put(servletName, servlet);
			MockDynamic registration = new MockDynamic();
			registrations.put(servletName, registration);
			return registration;
		}
	}

	private static class MyAnnotationConfigDispatcherServletInitializer
			extends AbstractAnnotationConfigDispatcherServletInitializer {

		@Override
		protected String getServletName() {
			return SERVLET_NAME;
		}

		@Override
		protected Class<?>[] getServletConfigClasses() {
			return new Class[]{MyConfiguration.class};
		}

		@Override
		protected String[] getServletMappings() {
			return new String[]{"/myservlet"};
		}

		@Override
		protected void customizeRegistration(ServletRegistration.Dynamic registration) {
			registration.setRunAsRole("role");
		}

		@Override
		protected Class<?>[] getRootConfigClasses() {
			return null;
		}

	}

	private static class MyBean {

	}

	@Configuration
	@SuppressWarnings("unused")
	private static class MyConfiguration {

		public MyConfiguration() {
		}

		@Bean
		public MyBean bean() {
			return new MyBean();
		}

	}

}
