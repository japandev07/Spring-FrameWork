/*
 * Copyright 2002-2007 the original author or authors.
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

package org.springframework.aop.target;

import org.springframework.beans.BeansException;

/**
 * TargetSource that creates a new instance of the target bean for each
 * request, destroying each instance on release (after each request).
 * Obtains bean instances from its containing
 * {@link org.springframework.beans.factory.BeanFactory}.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @see #setBeanFactory
 * @see #setTargetBeanName
 */
public class PrototypeTargetSource extends AbstractPrototypeBasedTargetSource {

	private static final long serialVersionUID = 1L;

	/**
	 * Obtain a new prototype instance for every call.
	 * @see #newPrototypeInstance()
	 */
	public Object getTarget() throws BeansException {
		return newPrototypeInstance();
	}

	/**
	 * Destroy the given independent instance.
	 * @see #destroyPrototypeInstance
	 */
	@Override
	public void releaseTarget(Object target) {
		destroyPrototypeInstance(target);
	}

	@Override
	public String toString() {
		return "PrototypeTargetSource for target bean with name '" + getTargetBeanName() + "'";
	}

}
