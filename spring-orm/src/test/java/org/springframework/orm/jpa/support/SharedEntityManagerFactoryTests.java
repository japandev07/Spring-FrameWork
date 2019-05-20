/*
 * Copyright 2002-2019 the original author or authors.
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

package org.springframework.orm.jpa.support;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

import org.junit.Test;

import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.orm.jpa.EntityManagerProxy;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Phillip Webb
 */
public class SharedEntityManagerFactoryTests {

	@Test
	public void testValidUsage() {
		Object o = new Object();

		EntityManager mockEm = mock(EntityManager.class);
		given(mockEm.isOpen()).willReturn(true);

		EntityManagerFactory mockEmf = mock(EntityManagerFactory.class);
		given(mockEmf.createEntityManager()).willReturn(mockEm);

		SharedEntityManagerBean proxyFactoryBean = new SharedEntityManagerBean();
		proxyFactoryBean.setEntityManagerFactory(mockEmf);
		proxyFactoryBean.afterPropertiesSet();

		assertTrue(EntityManager.class.isAssignableFrom(proxyFactoryBean.getObjectType()));
		assertTrue(proxyFactoryBean.isSingleton());

		EntityManager proxy = proxyFactoryBean.getObject();
		assertSame(proxy, proxyFactoryBean.getObject());
		assertFalse(proxy.contains(o));

		assertTrue(proxy instanceof EntityManagerProxy);
		EntityManagerProxy emProxy = (EntityManagerProxy) proxy;
		assertThatIllegalStateException().as("outside of transaction").isThrownBy(
				emProxy::getTargetEntityManager);

		TransactionSynchronizationManager.bindResource(mockEmf, new EntityManagerHolder(mockEm));
		try {
			assertSame(mockEm, emProxy.getTargetEntityManager());
		}
		finally {
			TransactionSynchronizationManager.unbindResource(mockEmf);
		}

		assertTrue(TransactionSynchronizationManager.getResourceMap().isEmpty());
		verify(mockEm).contains(o);
		verify(mockEm).close();
	}

}
