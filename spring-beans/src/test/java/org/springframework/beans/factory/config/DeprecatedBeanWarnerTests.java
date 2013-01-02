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

package org.springframework.beans.factory.config;

import org.junit.Test;

import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;

import static org.junit.Assert.*;

/**
 * @author Arjen Poutsma
 */
public class DeprecatedBeanWarnerTests {

	private DefaultListableBeanFactory beanFactory;

	private String beanName;

	private BeanDefinition beanDefinition;

	private DeprecatedBeanWarner warner;


	@Test
	@SuppressWarnings("deprecation")
	public void postProcess() {
		beanFactory = new DefaultListableBeanFactory();
		BeanDefinition def = new RootBeanDefinition(MyDeprecatedBean.class);
		String beanName = "deprecated";
		beanFactory.registerBeanDefinition(beanName, def);

		warner = new MyDeprecatedBeanWarner();
		warner.postProcessBeanFactory(beanFactory);
		assertEquals(beanName, this.beanName);
		assertEquals(def, this.beanDefinition);

	}


	private class MyDeprecatedBeanWarner extends DeprecatedBeanWarner {

		@Override
		protected void logDeprecatedBean(String beanName, Class<?> beanType, BeanDefinition beanDefinition) {
			DeprecatedBeanWarnerTests.this.beanName = beanName;
			DeprecatedBeanWarnerTests.this.beanDefinition = beanDefinition;
		}
	}

}
