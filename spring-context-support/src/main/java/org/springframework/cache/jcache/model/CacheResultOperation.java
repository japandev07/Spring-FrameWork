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
import javax.cache.annotation.CacheResult;

import org.springframework.cache.interceptor.CacheResolver;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.util.StringUtils;
import org.springframework.util.filter.ExceptionTypeFilter;

/**
 * The {@link JCacheOperation} implementation for a {@link CacheResult} operation.
 *
 * @author Stephane Nicoll
 * @since 4.1
 * @see CacheResult
 */
public class CacheResultOperation extends BaseKeyCacheOperation<CacheResult> {

	private final ExceptionTypeFilter exceptionTypeFilter;

	private final CacheResolver exceptionCacheResolver;

	private final String exceptionCacheName;

	public CacheResultOperation(CacheMethodDetails<CacheResult> methodDetails,
			CacheResolver cacheResolver, KeyGenerator keyGenerator,
			CacheResolver exceptionCacheResolver) {
		super(methodDetails, cacheResolver, keyGenerator);
		CacheResult ann = methodDetails.getCacheAnnotation();
		this.exceptionTypeFilter = createExceptionTypeFiler(ann.cachedExceptions(), ann.nonCachedExceptions());
		this.exceptionCacheResolver = exceptionCacheResolver;
		String exceptionCacheNameCandidate = ann.exceptionCacheName();
		this.exceptionCacheName = StringUtils.hasText(exceptionCacheNameCandidate) ? exceptionCacheNameCandidate : null;
	}

	@Override
	public ExceptionTypeFilter getExceptionTypeFilter() {
		return exceptionTypeFilter;
	}

	/**
	 * Specify if the method should always be invoked regardless of a cache hit. By
	 * default, the method is only invoked in case of a cache miss.
	 * @see javax.cache.annotation.CacheResult#skipGet()
	 */
	public boolean isAlwaysInvoked() {
		return getCacheAnnotation().skipGet();
	}

	/**
	 * Return the {@link CacheResolver} instance to use to resolve the cache to
	 * use for matching exceptions thrown by this operation.
	 */
	public CacheResolver getExceptionCacheResolver() {
		return exceptionCacheResolver;
	}

	/**
	 * Return the name of the cache to cache exceptions. Return {@link null} if
	 * caching exceptions should be disabled.
	 * @see javax.cache.annotation.CacheResult#exceptionCacheName()
	 */
	public String getExceptionCacheName() {
		return exceptionCacheName;
	}

}
