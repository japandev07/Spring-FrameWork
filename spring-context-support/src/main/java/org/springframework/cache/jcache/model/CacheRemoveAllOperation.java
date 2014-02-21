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

import javax.cache.annotation.CacheMethodDetails;
import javax.cache.annotation.CacheRemoveAll;

import org.springframework.cache.interceptor.CacheResolver;
import org.springframework.util.filter.ExceptionTypeFilter;

/**
 * The {@link JCacheOperation} implementation for a {@link CacheRemoveAll} operation.
 *
 * @author Stephane Nicoll
 * @since 4.1
 * @see CacheRemoveAll
 */
public class CacheRemoveAllOperation extends BaseCacheOperation<CacheRemoveAll> {

	private final ExceptionTypeFilter exceptionTypeFilter;

	public CacheRemoveAllOperation(CacheMethodDetails<CacheRemoveAll> methodDetails, CacheResolver cacheResolver) {
		super(methodDetails, cacheResolver);
		CacheRemoveAll ann = methodDetails.getCacheAnnotation();
		this.exceptionTypeFilter = createExceptionTypeFiler(ann.evictFor(), ann.noEvictFor());
	}

	@Override
	public ExceptionTypeFilter getExceptionTypeFilter() {
		return exceptionTypeFilter;
	}

	/**
	 * Specify if the cache should be cleared before invoking the method. By default, the
	 * cache is cleared after the method invocation.
	 * @see javax.cache.annotation.CacheRemoveAll#afterInvocation()
	 */
	public boolean isEarlyRemove() {
		return !getCacheAnnotation().afterInvocation();
	}

}
