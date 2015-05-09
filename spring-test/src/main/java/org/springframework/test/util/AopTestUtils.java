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

package org.springframework.test.util;

import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;

/**
 * {@code AopTestUtils} is a collection of AOP-related utility methods for
 * use in unit and integration testing scenarios.
 *
 * <p>For Spring's core AOP utilities, see
 * {@link org.springframework.aop.support.AopUtils AopUtils} and
 * {@link org.springframework.aop.framework.AopProxyUtils AopProxyUtils}.
 *
 * @author Sam Brannen
 * @since 4.2
 * @see org.springframework.aop.support.AopUtils
 * @see org.springframework.aop.framework.AopProxyUtils
 */
public class AopTestUtils {

	/**
	 * Get the <em>target</em> object of the supplied {@code candidate} object.
	 * <p>If the supplied {@code candidate} is a Spring
	 * {@linkplain AopUtils#isAopProxy proxy}, the target of the proxy will
	 * be returned; otherwise, the {@code candidate} will be returned
	 * <em>as is</em>.
	 *
	 * @param candidate the instance to check (potentially a Spring AOP proxy)
	 * @return the target object or the {@code candidate}; never {@code null}
	 * @throws IllegalStateException if an error occurs while unwrapping a proxy
	 * @see Advised#getTargetSource()
	 * @see #getUltimateTargetObject
	 */
	@SuppressWarnings("unchecked")
	public static <T> T getTargetObject(Object candidate) {
		try {
			if (AopUtils.isAopProxy(candidate) && (candidate instanceof Advised)) {
				return (T) ((Advised) candidate).getTargetSource().getTarget();
			}
		}
		catch (Exception e) {
			throw new IllegalStateException("Failed to unwrap proxied object.", e);
		}

		// else
		return (T) candidate;
	}

	/**
	 * Get the ultimate <em>target</em> object of the supplied {@code candidate}
	 * object, unwrapping not only a top-level proxy but also any number of
	 * nested proxies.
	 * <p>If the supplied {@code candidate} is a Spring
	 * {@linkplain AopUtils#isAopProxy proxy}, the ultimate target of all
	 * nested proxies will be returned; otherwise, the {@code candidate}
	 * will be returned <em>as is</em>.
	 *
	 * @param candidate the instance to check (potentially a Spring AOP proxy)
	 * @return the ultimate target object or the {@code candidate}; never
	 * {@code null}
	 * @throws IllegalStateException if an error occurs while unwrapping a proxy
	 * @see Advised#getTargetSource()
	 * @see org.springframework.aop.framework.AopProxyUtils#ultimateTargetClass
	 */
	@SuppressWarnings("unchecked")
	public static <T> T getUltimateTargetObject(Object candidate) {
		try {
			if (AopUtils.isAopProxy(candidate) && (candidate instanceof Advised)) {
				return (T) getUltimateTargetObject(((Advised) candidate).getTargetSource().getTarget());
			}
		}
		catch (Exception e) {
			throw new IllegalStateException("Failed to unwrap proxied object.", e);
		}

		// else
		return (T) candidate;
	}

}
