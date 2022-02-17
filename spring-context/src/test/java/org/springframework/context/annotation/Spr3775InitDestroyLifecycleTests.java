/*
 * Copyright 2002-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.context.annotation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.util.ObjectUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test which verifies expected <em>init</em> and <em>destroy</em>
 * bean lifecycle behavior as requested in
 * <a href="https://opensource.atlassian.com/projects/spring/browse/SPR-3775"
 * target="_blank">SPR-3775</a>.
 *
 * <p>Specifically, combinations of the following are tested:
 * <ul>
 * <li>{@link InitializingBean} &amp; {@link DisposableBean} interfaces</li>
 * <li>Custom {@link RootBeanDefinition#getInitMethodName() init} &amp;
 * {@link RootBeanDefinition#getDestroyMethodName() destroy} methods</li>
 * <li>JSR 250's {@link jakarta.annotation.PostConstruct @PostConstruct} &amp;
 * {@link jakarta.annotation.PreDestroy @PreDestroy} annotations</li>
 * </ul>
 *
 * @author Sam Brannen
 * @since 2.5
 */
public class Spr3775InitDestroyLifecycleTests {

	private static final String LIFECYCLE_TEST_BEAN = "lifecycleTestBean";


	private void assertMethodOrdering(String category, List<String> expectedMethods, List<String> actualMethods) {
		assertThat(ObjectUtils.nullSafeEquals(expectedMethods, actualMethods)).
				as("Verifying " + category + ": expected<" + expectedMethods + "> but got<" + actualMethods + ">.").isTrue();
	}

	private DefaultListableBeanFactory createBeanFactoryAndRegisterBean(
			Class<?> beanClass, String initMethodName, String destroyMethodName) {

		DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
		RootBeanDefinition beanDefinition = new RootBeanDefinition(beanClass);
		beanDefinition.setInitMethodName(initMethodName);
		beanDefinition.setDestroyMethodName(destroyMethodName);
		beanFactory.addBeanPostProcessor(new CommonAnnotationBeanPostProcessor());
		beanFactory.registerBeanDefinition(LIFECYCLE_TEST_BEAN, beanDefinition);
		return beanFactory;
	}

	@Test
	public void testInitDestroyMethods() {
		Class<?> beanClass = InitDestroyBean.class;
		DefaultListableBeanFactory beanFactory = createBeanFactoryAndRegisterBean(beanClass,
				"afterPropertiesSet", "destroy");
		InitDestroyBean bean = (InitDestroyBean) beanFactory.getBean(LIFECYCLE_TEST_BEAN);
		assertMethodOrdering("init-methods", Arrays.asList("afterPropertiesSet"), bean.initMethods);
		beanFactory.destroySingletons();
		assertMethodOrdering("destroy-methods", Arrays.asList("destroy"), bean.destroyMethods);
	}

	@Test
	public void testInitializingDisposableInterfaces() {
		Class<?> beanClass = CustomInitializingDisposableBean.class;
		DefaultListableBeanFactory beanFactory = createBeanFactoryAndRegisterBean(beanClass, "customInit",
				"customDestroy");
		CustomInitializingDisposableBean bean = (CustomInitializingDisposableBean) beanFactory.getBean(LIFECYCLE_TEST_BEAN);
		assertMethodOrdering("init-methods", Arrays.asList("afterPropertiesSet", "customInit"),
				bean.initMethods);
		beanFactory.destroySingletons();
		assertMethodOrdering("destroy-methods", Arrays.asList("destroy", "customDestroy"),
				bean.destroyMethods);
	}

	@Test
	public void testInitializingDisposableInterfacesWithShadowedMethods() {
		Class<?> beanClass = InitializingDisposableWithShadowedMethodsBean.class;
		DefaultListableBeanFactory beanFactory = createBeanFactoryAndRegisterBean(beanClass,
				"afterPropertiesSet", "destroy");
		InitializingDisposableWithShadowedMethodsBean bean =
				(InitializingDisposableWithShadowedMethodsBean) beanFactory.getBean(LIFECYCLE_TEST_BEAN);
		assertMethodOrdering("init-methods", Arrays.asList("InitializingBean.afterPropertiesSet"),
				bean.initMethods);
		beanFactory.destroySingletons();
		assertMethodOrdering("destroy-methods", Arrays.asList("DisposableBean.destroy"), bean.destroyMethods);
	}

	@Test
	public void testJsr250Annotations() {
		Class<?> beanClass = CustomAnnotatedInitDestroyBean.class;
		DefaultListableBeanFactory beanFactory = createBeanFactoryAndRegisterBean(beanClass, "customInit",
				"customDestroy");
		CustomAnnotatedInitDestroyBean bean = (CustomAnnotatedInitDestroyBean) beanFactory.getBean(LIFECYCLE_TEST_BEAN);
		assertMethodOrdering("init-methods", Arrays.asList("postConstruct", "afterPropertiesSet",
				"customInit"), bean.initMethods);
		beanFactory.destroySingletons();
		assertMethodOrdering("destroy-methods", Arrays.asList("preDestroy", "destroy", "customDestroy"),
				bean.destroyMethods);
	}

	@Test
	public void testJsr250AnnotationsWithShadowedMethods() {
		Class<?> beanClass = CustomAnnotatedInitDestroyWithShadowedMethodsBean.class;
		DefaultListableBeanFactory beanFactory = createBeanFactoryAndRegisterBean(beanClass, "customInit",
				"customDestroy");
		CustomAnnotatedInitDestroyWithShadowedMethodsBean bean =
				(CustomAnnotatedInitDestroyWithShadowedMethodsBean) beanFactory.getBean(LIFECYCLE_TEST_BEAN);
		assertMethodOrdering("init-methods",
				Arrays.asList("@PostConstruct.afterPropertiesSet", "customInit"), bean.initMethods);
		beanFactory.destroySingletons();
		assertMethodOrdering("destroy-methods", Arrays.asList("@PreDestroy.destroy", "customDestroy"),
				bean.destroyMethods);
	}

	@Test
	public void testAllLifecycleMechanismsAtOnce() {
		Class<?> beanClass = AllInOneBean.class;
		DefaultListableBeanFactory beanFactory = createBeanFactoryAndRegisterBean(beanClass,
				"afterPropertiesSet", "destroy");
		AllInOneBean bean = (AllInOneBean) beanFactory.getBean(LIFECYCLE_TEST_BEAN);
		assertMethodOrdering("init-methods", Arrays.asList("afterPropertiesSet"), bean.initMethods);
		beanFactory.destroySingletons();
		assertMethodOrdering("destroy-methods", Arrays.asList("destroy"), bean.destroyMethods);
	}


	public static class InitDestroyBean {

		final List<String> initMethods = new ArrayList<>();
		final List<String> destroyMethods = new ArrayList<>();

		public void afterPropertiesSet() throws Exception {
			this.initMethods.add("afterPropertiesSet");
		}

		public void destroy() throws Exception {
			this.destroyMethods.add("destroy");
		}
	}


	public static class InitializingDisposableWithShadowedMethodsBean extends InitDestroyBean
			implements InitializingBean, DisposableBean {

		@Override
		public void afterPropertiesSet() throws Exception {
			this.initMethods.add("InitializingBean.afterPropertiesSet");
		}

		@Override
		public void destroy() throws Exception {
			this.destroyMethods.add("DisposableBean.destroy");
		}
	}


	public static class CustomInitDestroyBean {

		final List<String> initMethods = new ArrayList<>();
		final List<String> destroyMethods = new ArrayList<>();

		public void customInit() throws Exception {
			this.initMethods.add("customInit");
		}

		public void customDestroy() throws Exception {
			this.destroyMethods.add("customDestroy");
		}
	}


	public static class CustomInitializingDisposableBean extends CustomInitDestroyBean
			implements InitializingBean, DisposableBean {

		@Override
		public void afterPropertiesSet() throws Exception {
			this.initMethods.add("afterPropertiesSet");
		}

		@Override
		public void destroy() throws Exception {
			this.destroyMethods.add("destroy");
		}
	}


	public static class CustomAnnotatedInitDestroyBean extends CustomInitializingDisposableBean {

		@PostConstruct
		public void postConstruct() throws Exception {
			this.initMethods.add("postConstruct");
		}

		@PreDestroy
		public void preDestroy() throws Exception {
			this.destroyMethods.add("preDestroy");
		}
	}


	public static class CustomAnnotatedInitDestroyWithShadowedMethodsBean extends CustomInitializingDisposableBean {

		@PostConstruct
		@Override
		public void afterPropertiesSet() throws Exception {
			this.initMethods.add("@PostConstruct.afterPropertiesSet");
		}

		@PreDestroy
		@Override
		public void destroy() throws Exception {
			this.destroyMethods.add("@PreDestroy.destroy");
		}
	}


	public static class AllInOneBean implements InitializingBean, DisposableBean {

		final List<String> initMethods = new ArrayList<>();
		final List<String> destroyMethods = new ArrayList<>();

		@PostConstruct
		@Override
		public void afterPropertiesSet() throws Exception {
			this.initMethods.add("afterPropertiesSet");
		}

		@PreDestroy
		@Override
		public void destroy() throws Exception {
			this.destroyMethods.add("destroy");
		}
	}

}
