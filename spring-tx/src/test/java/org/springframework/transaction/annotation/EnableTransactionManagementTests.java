/*
 * Copyright 2002-2015 the original author or authors.
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

package org.springframework.transaction.annotation;

import java.util.Map;

import javax.annotation.PostConstruct;

import org.junit.Test;

import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AdviceMode;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;
import org.springframework.tests.transaction.CallCountingTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionNamespaceHandlerTests.TransactionalTestBean;
import org.springframework.transaction.config.TransactionManagementConfigUtils;
import org.springframework.transaction.event.TransactionalEventListenerFactory;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

/**
 * Tests demonstrating use of @EnableTransactionManagement @Configuration classes.
 *
 * @author Chris Beams
 * @author Stephane Nicoll
 * @author Sam Brannen
 * @since 3.1
 */
public class EnableTransactionManagementTests {

	@Test
	public void transactionProxyIsCreated() {
		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(EnableTxConfig.class, TxManagerConfig.class);
		TransactionalTestBean bean = ctx.getBean(TransactionalTestBean.class);
		assertTrue("testBean is not a proxy", AopUtils.isAopProxy(bean));
		Map<?,?> services = ctx.getBeansWithAnnotation(Service.class);
		assertTrue("Stereotype annotation not visible", services.containsKey("testBean"));
		ctx.close();
	}

	@Test
	public void transactionProxyIsCreatedWithEnableOnSuperclass() {
		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(InheritedEnableTxConfig.class, TxManagerConfig.class);
		TransactionalTestBean bean = ctx.getBean(TransactionalTestBean.class);
		assertTrue("testBean is not a proxy", AopUtils.isAopProxy(bean));
		Map<?,?> services = ctx.getBeansWithAnnotation(Service.class);
		assertTrue("Stereotype annotation not visible", services.containsKey("testBean"));
		ctx.close();
	}

	@Test
	public void txManagerIsResolvedOnInvocationOfTransactionalMethod() {
		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(EnableTxConfig.class, TxManagerConfig.class);
		TransactionalTestBean bean = ctx.getBean(TransactionalTestBean.class);

		// invoke a transactional method, causing the PlatformTransactionManager bean to be resolved.
		bean.findAllFoos();
		ctx.close();
	}

	@Test
	public void txManagerIsResolvedCorrectlyWhenMultipleManagersArePresent() {
		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(EnableTxConfig.class, MultiTxManagerConfig.class);
		TransactionalTestBean bean = ctx.getBean(TransactionalTestBean.class);

		// invoke a transactional method, causing the PlatformTransactionManager bean to be resolved.
		bean.findAllFoos();
		ctx.close();
	}

	/**
	 * A cheap test just to prove that in ASPECTJ mode, the AnnotationTransactionAspect does indeed
	 * get loaded -- or in this case, attempted to be loaded at which point the test fails.
	 */
	@Test
	@SuppressWarnings("resource")
	public void proxyTypeAspectJCausesRegistrationOfAnnotationTransactionAspect() {
		try {
			new AnnotationConfigApplicationContext(EnableAspectJTxConfig.class, TxManagerConfig.class);
			fail("should have thrown CNFE when trying to load AnnotationTransactionAspect. " +
					"Do you actually have org.springframework.aspects on the classpath?");
		}
		catch (Exception ex) {
			assertThat(ex.getMessage(), containsString("AspectJTransactionManagementConfiguration"));
		}
	}

	@Test
	public void transactionalEventListenerRegisteredProperly() {
		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(EnableTxConfig.class);
		assertTrue(ctx.containsBean(TransactionManagementConfigUtils.TRANSACTIONAL_EVENT_LISTENER_FACTORY_BEAN_NAME));
		assertEquals(1, ctx.getBeansOfType(TransactionalEventListenerFactory.class).size());
		ctx.close();
	}

	@Test
	public void spr11915() {
		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(Spr11915Config.class);
		TransactionalTestBean bean = ctx.getBean(TransactionalTestBean.class);
		CallCountingTransactionManager txManager = ctx.getBean("qualifiedTransactionManager", CallCountingTransactionManager.class);

		bean.saveQualifiedFoo();
		assertThat(txManager.begun, equalTo(1));
		assertThat(txManager.commits, equalTo(1));
		assertThat(txManager.rollbacks, equalTo(0));

		bean.saveQualifiedFooWithAttributeAlias();
		assertThat(txManager.begun, equalTo(2));
		assertThat(txManager.commits, equalTo(2));
		assertThat(txManager.rollbacks, equalTo(0));

		ctx.close();
	}


	@Configuration
	@EnableTransactionManagement
	static class EnableTxConfig {
	}

	@Configuration
	static class InheritedEnableTxConfig extends EnableTxConfig {
	}

	@Configuration
	@EnableTransactionManagement(mode=AdviceMode.ASPECTJ)
	static class EnableAspectJTxConfig {
	}

	@Configuration
	@EnableTransactionManagement
	static class Spr11915Config {

		@Autowired
		private ConfigurableApplicationContext applicationContext;

		@PostConstruct
		public void initializeApp() {
			applicationContext.getBeanFactory().registerSingleton(
					"qualifiedTransactionManager", new CallCountingTransactionManager());
		}

		@Bean
		public TransactionalTestBean testBean() {
			return new TransactionalTestBean();
		}
	}

	@Configuration
	static class TxManagerConfig {

		@Bean
		public TransactionalTestBean testBean() {
			return new TransactionalTestBean();
		}

		@Bean
		public PlatformTransactionManager txManager() {
			return new CallCountingTransactionManager();
		}

	}

	@Configuration
	static class MultiTxManagerConfig extends TxManagerConfig implements TransactionManagementConfigurer {

		@Bean
		public PlatformTransactionManager txManager2() {
			return new CallCountingTransactionManager();
		}

		@Override
		public PlatformTransactionManager annotationDrivenTransactionManager() {
			return txManager2();
		}
	}

}
