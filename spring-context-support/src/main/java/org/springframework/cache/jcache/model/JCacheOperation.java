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

package org.springframework.cache.jcache.model;

import java.lang.annotation.Annotation;

import javax.cache.annotation.CacheInvocationParameter;
import javax.cache.annotation.CacheMethodDetails;

import org.springframework.cache.interceptor.BasicCacheOperation;
import org.springframework.cache.interceptor.CacheResolver;

/**
 * Model the base of JSR-107 cache operation.
 * <p>A cache operation can be statically cached as it does not contain
 * any runtime operation of a specific cache invocation.
 *
 * @param <A> the type of the JSR-107 annotation
 * @author Stephane Nicoll
 * @since 4.1
 */
public interface JCacheOperation<A extends Annotation>
		extends CacheMethodDetails<A>, BasicCacheOperation {

	/**
	 * Return the {@link CacheResolver} instance to use to resolve the cache to
	 * use for this operation.
	 */
	CacheResolver getCacheResolver();

	/**
	 * Return the {@link CacheInvocationParameter} instances based on the specified
	 * method arguments.
	 * <p>The method arguments must match the signature of the related method invocation
	 * @param values the parameters value for a particular invocation
	 */
	CacheInvocationParameter[] getAllParameters(Object... values);

}