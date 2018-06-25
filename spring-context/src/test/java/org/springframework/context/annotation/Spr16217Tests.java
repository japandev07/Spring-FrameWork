/*
 * Copyright 2002-2018 the original author or authors.
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

import org.junit.Ignore;
import org.junit.Test;

import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * @author Andy Wilkinson
 * @author Juergen Hoeller
 */
public class Spr16217Tests {

	@Test
	@Ignore("TODO")
	public void baseConfigurationIsIncludedWhenFirstSuperclassReferenceIsSkippedInRegisterBeanPhase() {
		try (AnnotationConfigApplicationContext context =
					new AnnotationConfigApplicationContext(RegisterBeanPhaseImportingConfiguration.class)) {
			context.getBean("someBean");
		}
	}

	@Test
	public void baseConfigurationIsIncludedWhenFirstSuperclassReferenceIsSkippedInParseConfigurationPhase() {
		try (AnnotationConfigApplicationContext context =
					new AnnotationConfigApplicationContext(ParseConfigurationPhaseImportingConfiguration.class)) {
			context.getBean("someBean");
		}
	}

	@Test
	public void baseConfigurationIsIncludedOnceWhenBothConfigurationClassesAreActive() {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
		context.setAllowBeanDefinitionOverriding(false);
		context.register(UnconditionalImportingConfiguration.class);
		context.refresh();
		try {
			context.getBean("someBean");
		}
		finally {
			context.close();
		}
	}


	public static class RegisterBeanPhaseCondition implements ConfigurationCondition {

		@Override
		public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
			return false;
		}

		@Override
		public ConfigurationPhase getConfigurationPhase() {
			return ConfigurationPhase.REGISTER_BEAN;
		}
	}


	public static class ParseConfigurationPhaseCondition implements ConfigurationCondition {

		@Override
		public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
			return false;
		}

		@Override
		public ConfigurationPhase getConfigurationPhase() {
			return ConfigurationPhase.PARSE_CONFIGURATION;
		}
	}


	@Import({RegisterBeanPhaseConditionConfiguration.class, BarConfiguration.class})
	public static class RegisterBeanPhaseImportingConfiguration {
	}


	@Import({ParseConfigurationPhaseConditionConfiguration.class, BarConfiguration.class})
	public static class ParseConfigurationPhaseImportingConfiguration {
	}


	@Import({UnconditionalConfiguration.class, BarConfiguration.class})
	public static class UnconditionalImportingConfiguration {
	}


	public static class BaseConfiguration {

		@Bean
		public String someBean() {
			return "foo";
		}
	}


	@Conditional(RegisterBeanPhaseCondition.class)
	public static class RegisterBeanPhaseConditionConfiguration extends BaseConfiguration {
	}


	@Conditional(ParseConfigurationPhaseCondition.class)
	public static class ParseConfigurationPhaseConditionConfiguration extends BaseConfiguration {
	}


	public static class UnconditionalConfiguration extends BaseConfiguration {
	}


	public static class BarConfiguration extends BaseConfiguration {
	}

}
