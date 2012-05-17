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

package org.springframework.test.context;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.style.ToStringCreator;
import org.springframework.util.Assert;

/**
 * Cache for Spring {@link ApplicationContext ApplicationContexts} in a test environment.
 *
 * <p>Maintains a cache of {@link ApplicationContext contexts} keyed by
 * {@link MergedContextConfiguration} instances. This has significant performance
 * benefits if initializing the context would take time. While initializing a
 * Spring context itself is very quick, some beans in a context, such as a
 * {@code LocalSessionFactoryBean} for working with Hibernate, may take some time
 * to initialize. Hence it often makes sense to perform that initialization only
 * once per test suite.
 *
 * @author Sam Brannen
 * @author Juergen Hoeller
 * @since 2.5
 */
class ContextCache {

	/**
	 * Map of context keys to Spring ApplicationContext instances.
	 */
	private final Map<MergedContextConfiguration, ApplicationContext> contextMap = new ConcurrentHashMap<MergedContextConfiguration, ApplicationContext>();

	private int hitCount;

	private int missCount;


	/**
	 * Clears all contexts from the cache.
	 */
	void clear() {
		this.contextMap.clear();
	}

	/**
	 * Clears hit and miss count statistics for the cache (i.e., resets counters
	 * to zero).
	 */
	void clearStatistics() {
		this.hitCount = 0;
		this.missCount = 0;
	}

	/**
	 * Return whether there is a cached context for the given key.
	 *
	 * @param key the context key (never <code>null</code>)
	 */
	boolean contains(MergedContextConfiguration key) {
		Assert.notNull(key, "Key must not be null");
		return this.contextMap.containsKey(key);
	}

	/**
	 * Obtain a cached ApplicationContext for the given key.
	 *
	 * <p>The {@link #getHitCount() hit} and {@link #getMissCount() miss}
	 * counts will be updated accordingly.
	 *
	 * @param key the context key (never <code>null</code>)
	 * @return the corresponding ApplicationContext instance,
	 * or <code>null</code> if not found in the cache.
	 * @see #remove
	 */
	ApplicationContext get(MergedContextConfiguration key) {
		Assert.notNull(key, "Key must not be null");
		ApplicationContext context = this.contextMap.get(key);
		if (context == null) {
			incrementMissCount();
		}
		else {
			incrementHitCount();
		}
		return context;
	}

	/**
	 * Increment the hit count by one. A <em>hit</em> is an access to the
	 * cache, which returned a non-null context for a queried key.
	 */
	private void incrementHitCount() {
		this.hitCount++;
	}

	/**
	 * Increment the miss count by one. A <em>miss</em> is an access to the
	 * cache, which returned a <code>null</code> context for a queried key.
	 */
	private void incrementMissCount() {
		this.missCount++;
	}

	/**
	 * Get the overall hit count for this cache. A <em>hit</em> is an access
	 * to the cache, which returned a non-null context for a queried key.
	 */
	int getHitCount() {
		return this.hitCount;
	}

	/**
	 * Get the overall miss count for this cache. A <em>miss</em> is an
	 * access to the cache, which returned a <code>null</code> context for a
	 * queried key.
	 */
	int getMissCount() {
		return this.missCount;
	}

	/**
	 * Explicitly add an ApplicationContext instance to the cache under the given key.
	 *
	 * @param key the context key (never <code>null</code>)
	 * @param context the ApplicationContext instance (never <code>null</code>)
	 */
	void put(MergedContextConfiguration key, ApplicationContext context) {
		Assert.notNull(key, "Key must not be null");
		Assert.notNull(context, "ApplicationContext must not be null");
		this.contextMap.put(key, context);
	}

	/**
	 * Remove the context with the given key.
	 *
	 * @param key the context key (never <code>null</code>)
	 * @return the corresponding ApplicationContext instance, or <code>null</code>
	 * if not found in the cache.
	 * @see #setDirty
	 */
	ApplicationContext remove(MergedContextConfiguration key) {
		return this.contextMap.remove(key);
	}

	/**
	 * Mark the context with the given key as dirty, effectively
	 * {@link #remove removing} the context from the cache and explicitly
	 * {@link ConfigurableApplicationContext#close() closing} it if it is an
	 * instance of {@link ConfigurableApplicationContext}.
	 *
	 * <p>Generally speaking, you would only call this method if you change the
	 * state of a singleton bean, potentially affecting future interaction with
	 * the context.
	 *
	 * @param key the context key (never <code>null</code>)
	 * @see #remove
	 */
	void setDirty(MergedContextConfiguration key) {
		Assert.notNull(key, "Key must not be null");
		ApplicationContext context = remove(key);
		if (context instanceof ConfigurableApplicationContext) {
			((ConfigurableApplicationContext) context).close();
		}
	}

	/**
	 * Determine the number of contexts currently stored in the cache. If the
	 * cache contains more than <tt>Integer.MAX_VALUE</tt> elements, returns
	 * <tt>Integer.MAX_VALUE</tt>.
	 */
	int size() {
		return this.contextMap.size();
	}

	/**
	 * Generates a text string, which contains the {@link #size() size} as well
	 * as the {@link #hitCount hit} and {@link #missCount miss} counts.
	 */
	public String toString() {
		return new ToStringCreator(this)//
		.append("size", size())//
		.append("hitCount", getHitCount())//
		.append("missCount", getMissCount())//
		.toString();
	}

}
