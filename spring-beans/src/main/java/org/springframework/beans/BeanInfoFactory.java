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

package org.springframework.beans;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;

/**
 * Strategy for creating {@link BeanInfo} instances.
 *
 * @author Arjen Poutsma
 * @since 3.2
 */
public interface BeanInfoFactory {

	/**
	 * Indicates whether a bean with the given class is supported by this factory.
	 *
	 * @param beanClass the bean class
	 * @return {@code true} if supported; {@code false} otherwise
	 */
	boolean supports(Class<?> beanClass);

	/**
	 * Returns the bean info for the given class.
	 *
	 * @param beanClass the bean class
	 * @return the bean info
	 * @throws IntrospectionException in case of exceptions
	 */
	BeanInfo getBeanInfo(Class<?> beanClass) throws IntrospectionException;

}
