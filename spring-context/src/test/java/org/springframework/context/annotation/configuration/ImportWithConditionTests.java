/*
 * Copyright 2012-2014 the original author or authors.
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

package org.springframework.context.annotation.configuration;

import org.junit.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ConfigurationCondition;
import org.springframework.context.annotation.Import;
import org.springframework.core.type.AnnotatedTypeMetadata;

import static org.junit.Assert.*;

/**
 * @author Andy Wilkinson
 */
public class ImportWithConditionTests {

	private AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

	@Test
	public void conditionalThenUnconditional() throws Exception {
		this.context.register(ConditionalThenUnconditional.class);
		this.context.refresh();
		assertFalse(this.context.containsBean("beanTwo"));
		assertTrue(this.context.containsBean("beanOne"));
	}

	@Test
	public void unconditionalThenConditional() throws Exception {
		this.context.register(UnconditionalThenConditional.class);
		this.context.refresh();
		assertFalse(this.context.containsBean("beanTwo"));
		assertTrue(this.context.containsBean("beanOne"));
	}


	@Configuration
	@Import({ConditionalConfiguration.class, UnconditionalConfiguration.class})
	protected static class ConditionalThenUnconditional {

		@Autowired
		private BeanOne beanOne;
	}


	@Configuration
	@Import({UnconditionalConfiguration.class, ConditionalConfiguration.class})
	protected static class UnconditionalThenConditional {

		@Autowired
		private BeanOne beanOne;
	}


	@Configuration
	@Import(BeanProvidingConfiguration.class)
	protected static class UnconditionalConfiguration {
	}


	@Configuration
	@Conditional(NeverMatchingCondition.class)
	@Import(BeanProvidingConfiguration.class)
	protected static class ConditionalConfiguration {
	}


	@Configuration
	protected static class BeanProvidingConfiguration {

		@Bean
		BeanOne beanOne() {
			return new BeanOne();
		}
	}


	private static final class BeanOne {
	}


	private static final class NeverMatchingCondition implements ConfigurationCondition {

		@Override
		public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
			return false;
		}

		@Override
		public ConfigurationPhase getConfigurationPhase() {
			return ConfigurationPhase.REGISTER_BEAN;
		}
	}

}
