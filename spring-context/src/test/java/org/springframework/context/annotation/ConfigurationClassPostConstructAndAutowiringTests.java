/*
 * Copyright 2002-2014 the original author or authors.
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

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import javax.annotation.PostConstruct;

import org.junit.Test;
import org.springframework.tests.sample.beans.TestBean;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Tests cornering the issue reported in SPR-8080. If the product of a @Bean method
 * was @Autowired into a configuration class while at the same time the declaring
 * configuration class for the @Bean method in question has a @PostConstruct
 * (or other initializer) method, the container would become confused about the
 * 'currently in creation' status of the autowired bean and result in creating multiple
 * instances of the given @Bean, violating container scoping / singleton semantics.
 *
 * This is resolved through no longer relying on 'currently in creation' status, but
 * rather on a thread local that informs the enhanced bean method implementation whether
 * the factory is the caller or not.
 *
 * @author Chris Beams
 * @since 3.1
 */
public class ConfigurationClassPostConstructAndAutowiringTests {

	/**
	 * Prior to the fix for SPR-8080, this method would succeed due to ordering of
	 * configuration class registration.
	 */
	@Test
	public void control() {
		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
		ctx.register(Config1.class, Config2.class);
		ctx.refresh();

		assertions(ctx);

		Config2 config2 = ctx.getBean(Config2.class);
		assertThat(config2.testBean, is(ctx.getBean(TestBean.class)));
	}

	/**
	 * Prior to the fix for SPR-8080, this method would fail due to ordering of
	 * configuration class registration.
	 */
	@Test
	public void originalReproCase() {
		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
		ctx.register(Config2.class, Config1.class);
		ctx.refresh();

		assertions(ctx);
	}

	private void assertions(AnnotationConfigApplicationContext ctx) {
		Config1 config1 = ctx.getBean(Config1.class);
		TestBean testBean = ctx.getBean(TestBean.class);
		assertThat(config1.beanMethodCallCount, is(1));
		assertThat(testBean.getAge(), is(2));
	}


	@Configuration
	static class Config1 {

		int beanMethodCallCount = 0;

		@PostConstruct
		public void init() {
			beanMethod().setAge(beanMethod().getAge() + 1); // age == 2
		}

		@Bean
		public TestBean beanMethod() {
			beanMethodCallCount++;
			TestBean testBean = new TestBean();
			testBean.setAge(1);
			return testBean;
		}
	}


	@Configuration
	static class Config2 {

		TestBean testBean;

		@Autowired
		void setTestBean(TestBean testBean) {
			this.testBean = testBean;
		}
	}

}
