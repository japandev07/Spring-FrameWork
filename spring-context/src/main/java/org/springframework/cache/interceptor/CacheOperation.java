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

package org.springframework.cache.interceptor;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.util.Assert;

/**
 * Base class for cache operations.
 *
 * @author Costin Leau
 * @author Stephane Nicoll
 * @since 3.1
 */
public abstract class CacheOperation implements BasicOperation {

	private String name = "";

	private Set<String> cacheNames = Collections.emptySet();

	private String key = "";

	private String keyGenerator = "";

	private String cacheManager = "";

	private String cacheResolver = "";

	private String condition = "";


	public void setName(String name) {
		Assert.hasText(name);
		this.name = name;
	}

	public String getName() {
		return this.name;
	}

	public void setCacheName(String cacheName) {
		Assert.hasText(cacheName);
		this.cacheNames = Collections.singleton(cacheName);
	}

	public void setCacheNames(String... cacheNames) {
		this.cacheNames = new LinkedHashSet<String>(cacheNames.length);
		for (String cacheName : cacheNames) {
			Assert.hasText(cacheName, "Cache name must be non-null if specified");
			this.cacheNames.add(cacheName);
		}
	}

	@Override
	public Set<String> getCacheNames() {
		return this.cacheNames;
	}

	public void setKey(String key) {
		Assert.notNull(key);
		this.key = key;
	}

	public String getKey() {
		return this.key;
	}

	public void setKeyGenerator(String keyGenerator) {
		Assert.notNull(keyGenerator);
		this.keyGenerator = keyGenerator;
	}

	public String getKeyGenerator() {
		return this.keyGenerator;
	}

	public void setCacheManager(String cacheManager) {
		Assert.notNull(cacheManager);
		this.cacheManager = cacheManager;
	}

	public String getCacheManager() {
		return this.cacheManager;
	}

	public void setCacheResolver(String cacheResolver) {
		Assert.notNull(cacheManager);
		this.cacheResolver = cacheResolver;
	}

	public String getCacheResolver() {
		return this.cacheResolver;
	}

	public void setCondition(String condition) {
		Assert.notNull(condition);
		this.condition = condition;
	}

	public String getCondition() {
		return this.condition;
	}


	/**
	 * This implementation compares the {@code toString()} results.
	 * @see #toString()
	 */
	@Override
	public boolean equals(Object other) {
		return (other instanceof CacheOperation && toString().equals(other.toString()));
	}

	/**
	 * This implementation returns {@code toString()}'s hash code.
	 * @see #toString()
	 */
	@Override
	public int hashCode() {
		return toString().hashCode();
	}

	/**
	 * Return an identifying description for this cache operation.
	 * <p>Has to be overridden in subclasses for correct {@code equals}
	 * and {@code hashCode} behavior. Alternatively, {@link #equals}
	 * and {@link #hashCode} can be overridden themselves.
	 */
	@Override
	public String toString() {
		return getOperationDescription().toString();
	}

	/**
	 * Return an identifying description for this caching operation.
	 * <p>Available to subclasses, for inclusion in their {@code toString()} result.
	 */
	protected StringBuilder getOperationDescription() {
		StringBuilder result = new StringBuilder(getClass().getSimpleName());
		result.append("[").append(this.name);
		result.append("] caches=").append(this.cacheNames);
		result.append(" | key='").append(this.key);
		result.append("' | keyGenerator='").append(this.keyGenerator);
		result.append("' | cacheManager='").append(this.cacheManager);
		result.append("' | cacheResolver='").append(this.cacheResolver);
		result.append("' | condition='").append(this.condition).append("'");
		return result;
	}

}
