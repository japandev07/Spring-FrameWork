/*
 * Copyright 2002-2009 the original author or authors.
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

package org.springframework.context.annotation;

import static java.lang.String.format;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.matchers.JUnitMatchers.*;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import org.junit.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;

public class ConfigurationClassApplicationContextTests {
	
	@Test(expected=IllegalStateException.class)
	public void emptyConstructorRequiresManualRefresh() {
		ConfigurationClassApplicationContext context = new ConfigurationClassApplicationContext();
		context.getBean("foo");
	}
	
	@Test
	public void classesMissingConfigurationAnnotationAddedToContextAreDisallowed() {
		ConfigurationClassApplicationContext ctx =
			new ConfigurationClassApplicationContext(Config.class);
		
		// should be fine
		ctx.addConfigurationClass(ConfigWithCustomName.class);
		
		// should cause immediate failure (no refresh necessary)
		try {
			ctx.addConfigurationClass(ConfigMissingAnnotation.class);
			fail("expected exception");
		} catch (IllegalArgumentException ex) {
			assertThat(ex.getMessage(),
					equalTo("Class [" + ConfigMissingAnnotation.class.getName() + "] " +
							"is not annotated with @Configuration"));
		}
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void classesMissingConfigurationAnnotationSuppliedToConstructorAreDisallowed() {
		new ConfigurationClassApplicationContext(ConfigMissingAnnotation.class);
	}
	
	
	@Test(expected=IllegalArgumentException.class)
	public void nullGetBeanParameterIsDisallowed() {
		ConfigurationClassApplicationContext context = new ConfigurationClassApplicationContext(Config.class);
		context.getBean((Class<?>)null);
	}
	
	@Test
	public void addConfigurationClass() {
		ConfigurationClassApplicationContext context = new ConfigurationClassApplicationContext();
		context.addConfigurationClass(Config.class);
		context.refresh();
		context.getBean("testBean");
		context.addConfigurationClass(NameConfig.class);
		context.refresh();
		context.getBean("name");
	}
	
	@Test
	public void getBeanByType() {
		ConfigurationClassApplicationContext context = new ConfigurationClassApplicationContext(Config.class);
		TestBean testBean = context.getBean(TestBean.class);
		assertNotNull("getBean() should not return null", testBean);
		assertThat(testBean.name, equalTo("foo"));
	}
	
	/**
	 * Tests that Configuration classes are registered according to convention
	 * @see org.springframework.beans.factory.support.DefaultBeanNameGenerator#generateBeanName
	 */
	@Test
	public void defaultConfigClassBeanNameIsGeneratedProperly() {
		ConfigurationClassApplicationContext context = new ConfigurationClassApplicationContext(Config.class);
		
		// attempt to retrieve the instance by its generated bean name
		Config configObject = (Config) context.getBean(Config.class.getName() + "#0");
		assertNotNull(configObject);
	}
	
	/**
	 * Tests that specifying @Configuration(value="foo") results in registering
	 * the configuration class with bean name 'foo'.
	 */
	@Test
	public void explicitConfigClassBeanNameIsRespected() {
		ConfigurationClassApplicationContext context =
			new ConfigurationClassApplicationContext(ConfigWithCustomName.class);
		
		// attempt to retrieve the instance by its specified name
		ConfigWithCustomName configObject =
			(ConfigWithCustomName) context.getBean("customConfigBeanName");
		assertNotNull(configObject);
	}
	
	@Test
	public void getBeanByTypeRaisesNoSuchBeanDefinitionException() {
		ConfigurationClassApplicationContext context = new ConfigurationClassApplicationContext(Config.class);
		
		// attempt to retrieve a bean that does not exist
		Class<?> targetType = java.util.regex.Pattern.class;
		try {
			Object bean = context.getBean(targetType);
			fail("should have thrown NoSuchBeanDefinitionException, instead got: " + bean);
		} catch (NoSuchBeanDefinitionException ex) {
			assertThat(ex.getMessage(), equalTo(
					format("No unique bean of type [%s] is defined", targetType.getName())));
		}
	}
	
	@SuppressWarnings("unchecked")
	@Test
	public void getBeanByTypeAmbiguityRaisesException() {
		ConfigurationClassApplicationContext context = new ConfigurationClassApplicationContext(TwoTestBeanConfig.class);
		
		try {
			context.getBean(TestBean.class);
		} catch (RuntimeException ex) {
			assertThat(ex.getMessage(),
					allOf(
						containsString("No unique bean of type [" + TestBean.class.getName() + "] is defined"),
						containsString("2 matching bean definitions found"),
						containsString("tb1"),
						containsString("tb2"),
						containsString("Consider qualifying with")
					)
				);
		}
	}
	
	@Test
	public void autowiringIsEnabledByDefault() {
		ConfigurationClassApplicationContext context = new ConfigurationClassApplicationContext(AutowiredConfig.class);
		assertThat(context.getBean(TestBean.class).name, equalTo("foo"));
	}
	
	
	@Configuration
	static class Config {
		@Bean
		public TestBean testBean() {
			TestBean testBean = new TestBean();
			testBean.name = "foo";
			return testBean;
		}
	}
	
	@Configuration("customConfigBeanName")
	static class ConfigWithCustomName {
		@Bean
		public TestBean testBean() {
			return new TestBean();
		}
	}
	
	static class ConfigMissingAnnotation {
		@Bean
		public TestBean testBean() {
			return new TestBean();
		}
	}
	
	@Configuration
	static class TwoTestBeanConfig {
		@Bean TestBean tb1() { return new TestBean(); }
		@Bean TestBean tb2() { return new TestBean(); }
	}
	
	@Configuration
	static class NameConfig {
		@Bean String name() { return "foo"; }
	}
	
	@Configuration
	@Import(NameConfig.class)
	static class AutowiredConfig {
		@Autowired String autowiredName;
		
		@Bean TestBean testBean() {
			TestBean testBean = new TestBean();
			testBean.name = autowiredName;
			return testBean;
		}
	}
	
}

class TestBean {
	String name;

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		TestBean other = (TestBean) obj;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		return true;
	}
	
	
}