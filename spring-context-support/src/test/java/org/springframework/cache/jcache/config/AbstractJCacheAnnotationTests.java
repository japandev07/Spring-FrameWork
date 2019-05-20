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

package org.springframework.cache.jcache.config;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.interceptor.SimpleKeyGenerator;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIOException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * @author Stephane Nicoll
 */
public abstract class AbstractJCacheAnnotationTests {

	public static final String DEFAULT_CACHE = "default";

	public static final String EXCEPTION_CACHE = "exception";

	@Rule
	public final TestName name = new TestName();

	protected ApplicationContext ctx;

	private JCacheableService<?> service;

	private CacheManager cacheManager;

	protected abstract ApplicationContext getApplicationContext();

	@Before
	public void setUp() {
		ctx = getApplicationContext();
		service = ctx.getBean(JCacheableService.class);
		cacheManager = ctx.getBean("cacheManager", CacheManager.class);
	}

	@Test
	public void cache() {
		String keyItem = name.getMethodName();

		Object first = service.cache(keyItem);
		Object second = service.cache(keyItem);
		assertSame(first, second);
	}

	@Test
	public void cacheNull() {
		Cache cache = getCache(DEFAULT_CACHE);

		String keyItem = name.getMethodName();
		assertNull(cache.get(keyItem));

		Object first = service.cacheNull(keyItem);
		Object second = service.cacheNull(keyItem);
		assertSame(first, second);

		Cache.ValueWrapper wrapper = cache.get(keyItem);
		assertNotNull(wrapper);
		assertSame(first, wrapper.get());
		assertNull("Cached value should be null", wrapper.get());
	}

	@Test
	public void cacheException() {
		String keyItem = name.getMethodName();
		Cache cache = getCache(EXCEPTION_CACHE);

		Object key = createKey(keyItem);
		assertNull(cache.get(key));

		assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() ->
				service.cacheWithException(keyItem, true));

		Cache.ValueWrapper result = cache.get(key);
		assertNotNull(result);
		assertEquals(UnsupportedOperationException.class, result.get().getClass());
	}

	@Test
	public void cacheExceptionVetoed() {
		String keyItem = name.getMethodName();
		Cache cache = getCache(EXCEPTION_CACHE);

		Object key = createKey(keyItem);
		assertNull(cache.get(key));

		assertThatNullPointerException().isThrownBy(() ->
				service.cacheWithException(keyItem, false));
		assertNull(cache.get(key));
	}

	@Test
	public void cacheCheckedException() {
		String keyItem = name.getMethodName();
		Cache cache = getCache(EXCEPTION_CACHE);

		Object key = createKey(keyItem);
		assertNull(cache.get(key));
		assertThatIOException().isThrownBy(() ->
				service.cacheWithCheckedException(keyItem, true));

		Cache.ValueWrapper result = cache.get(key);
		assertNotNull(result);
		assertEquals(IOException.class, result.get().getClass());
	}


	@SuppressWarnings("ThrowableResultOfMethodCallIgnored")
	@Test
	public void cacheExceptionRewriteCallStack() {
		String keyItem = name.getMethodName();
		long ref = service.exceptionInvocations();
		assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() ->
				service.cacheWithException(keyItem, true))
			.satisfies(first -> {
				// Sanity check, this particular call has called the service
				// First call should not have been cached
				assertThat(service.exceptionInvocations()).isEqualTo(ref + 1);

				UnsupportedOperationException second = methodInCallStack(keyItem);
				// Sanity check, this particular call has *not* called the service
				// Second call should have been cached
				assertThat(service.exceptionInvocations()).isEqualTo(ref + 1);

				assertThat(first).hasCause(second.getCause());
				assertThat(first).hasMessage(second.getMessage());
				// Original stack must not contain any reference to methodInCallStack
				assertThat(contain(first, AbstractJCacheAnnotationTests.class.getName(), "methodInCallStack")).isFalse();
				assertThat(contain(second, AbstractJCacheAnnotationTests.class.getName(), "methodInCallStack")).isTrue();
			});
	}

	@Test
	public void cacheAlwaysInvoke() {
		String keyItem = name.getMethodName();

		Object first = service.cacheAlwaysInvoke(keyItem);
		Object second = service.cacheAlwaysInvoke(keyItem);
		assertNotSame(first, second);
	}

	@Test
	public void cacheWithPartialKey() {
		String keyItem = name.getMethodName();

		Object first = service.cacheWithPartialKey(keyItem, true);
		Object second = service.cacheWithPartialKey(keyItem, false);
		assertSame(first, second); // second argument not used, see config
	}

	@Test
	public void cacheWithCustomCacheResolver() {
		String keyItem = name.getMethodName();
		Cache cache = getCache(DEFAULT_CACHE);

		Object key = createKey(keyItem);
		service.cacheWithCustomCacheResolver(keyItem);

		assertNull(cache.get(key)); // Cache in mock cache
	}

	@Test
	public void cacheWithCustomKeyGenerator() {
		String keyItem = name.getMethodName();
		Cache cache = getCache(DEFAULT_CACHE);

		Object key = createKey(keyItem);
		service.cacheWithCustomKeyGenerator(keyItem, "ignored");

		assertNull(cache.get(key));
	}

	@Test
	public void put() {
		String keyItem = name.getMethodName();
		Cache cache = getCache(DEFAULT_CACHE);

		Object key = createKey(keyItem);
		Object value = new Object();
		assertNull(cache.get(key));

		service.put(keyItem, value);

		Cache.ValueWrapper result = cache.get(key);
		assertNotNull(result);
		assertEquals(value, result.get());
	}

	@Test
	public void putWithException() {
		String keyItem = name.getMethodName();
		Cache cache = getCache(DEFAULT_CACHE);

		Object key = createKey(keyItem);
		Object value = new Object();
		assertNull(cache.get(key));

		assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() ->
				service.putWithException(keyItem, value, true));

		Cache.ValueWrapper result = cache.get(key);
		assertNotNull(result);
		assertEquals(value, result.get());
	}

	@Test
	public void putWithExceptionVetoPut() {
		String keyItem = name.getMethodName();
		Cache cache = getCache(DEFAULT_CACHE);

		Object key = createKey(keyItem);
		Object value = new Object();
		assertNull(cache.get(key));

		assertThatNullPointerException().isThrownBy(() ->
				service.putWithException(keyItem, value, false));
		assertNull(cache.get(key));
	}

	@Test
	public void earlyPut() {
		String keyItem = name.getMethodName();
		Cache cache = getCache(DEFAULT_CACHE);

		Object key = createKey(keyItem);
		Object value = new Object();
		assertNull(cache.get(key));

		service.earlyPut(keyItem, value);

		Cache.ValueWrapper result = cache.get(key);
		assertNotNull(result);
		assertEquals(value, result.get());
	}

	@Test
	public void earlyPutWithException() {
		String keyItem = name.getMethodName();
		Cache cache = getCache(DEFAULT_CACHE);

		Object key = createKey(keyItem);
		Object value = new Object();
		assertNull(cache.get(key));

		assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() ->
				service.earlyPutWithException(keyItem, value, true));

		Cache.ValueWrapper result = cache.get(key);
		assertNotNull(result);
		assertEquals(value, result.get());
	}

	@Test
	public void earlyPutWithExceptionVetoPut() {
		String keyItem = name.getMethodName();
		Cache cache = getCache(DEFAULT_CACHE);

		Object key = createKey(keyItem);
		Object value = new Object();
		assertNull(cache.get(key));
		assertThatNullPointerException().isThrownBy(() ->
				service.earlyPutWithException(keyItem, value, false));
		// This will be cached anyway as the earlyPut has updated the cache before
		Cache.ValueWrapper result = cache.get(key);
		assertNotNull(result);
		assertEquals(value, result.get());
	}

	@Test
	public void remove() {
		String keyItem = name.getMethodName();
		Cache cache = getCache(DEFAULT_CACHE);

		Object key = createKey(keyItem);
		Object value = new Object();
		cache.put(key, value);

		service.remove(keyItem);

		assertNull(cache.get(key));
	}

	@Test
	public void removeWithException() {
		String keyItem = name.getMethodName();
		Cache cache = getCache(DEFAULT_CACHE);

		Object key = createKey(keyItem);
		Object value = new Object();
		cache.put(key, value);

		assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() ->
				service.removeWithException(keyItem, true));

		assertNull(cache.get(key));
	}

	@Test
	public void removeWithExceptionVetoRemove() {
		String keyItem = name.getMethodName();
		Cache cache = getCache(DEFAULT_CACHE);

		Object key = createKey(keyItem);
		Object value = new Object();
		cache.put(key, value);

		assertThatNullPointerException().isThrownBy(() ->
				service.removeWithException(keyItem, false));
		Cache.ValueWrapper wrapper = cache.get(key);
		assertNotNull(wrapper);
		assertEquals(value, wrapper.get());
	}

	@Test
	public void earlyRemove() {
		String keyItem = name.getMethodName();
		Cache cache = getCache(DEFAULT_CACHE);

		Object key = createKey(keyItem);
		Object value = new Object();
		cache.put(key, value);

		service.earlyRemove(keyItem);

		assertNull(cache.get(key));
	}

	@Test
	public void earlyRemoveWithException() {
		String keyItem = name.getMethodName();
		Cache cache = getCache(DEFAULT_CACHE);

		Object key = createKey(keyItem);
		Object value = new Object();
		cache.put(key, value);

		assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() ->
				service.earlyRemoveWithException(keyItem, true));
		assertNull(cache.get(key));
	}

	@Test
	public void earlyRemoveWithExceptionVetoRemove() {
		String keyItem = name.getMethodName();
		Cache cache = getCache(DEFAULT_CACHE);

		Object key = createKey(keyItem);
		Object value = new Object();
		cache.put(key, value);

		assertThatNullPointerException().isThrownBy(() ->
				service.earlyRemoveWithException(keyItem, false));
		// This will be remove anyway as the earlyRemove has removed the cache before
		assertNull(cache.get(key));
	}

	@Test
	public void removeAll() {
		Cache cache = getCache(DEFAULT_CACHE);

		Object key = createKey(name.getMethodName());
		cache.put(key, new Object());

		service.removeAll();

		assertTrue(isEmpty(cache));
	}

	@Test
	public void removeAllWithException() {
		Cache cache = getCache(DEFAULT_CACHE);

		Object key = createKey(name.getMethodName());
		cache.put(key, new Object());

		assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() ->
				service.removeAllWithException(true));

		assertTrue(isEmpty(cache));
	}

	@Test
	public void removeAllWithExceptionVetoRemove() {
		Cache cache = getCache(DEFAULT_CACHE);

		Object key = createKey(name.getMethodName());
		cache.put(key, new Object());

		assertThatNullPointerException().isThrownBy(() ->
				service.removeAllWithException(false));
		assertNotNull(cache.get(key));
	}

	@Test
	public void earlyRemoveAll() {
		Cache cache = getCache(DEFAULT_CACHE);

		Object key = createKey(name.getMethodName());
		cache.put(key, new Object());

		service.earlyRemoveAll();

		assertTrue(isEmpty(cache));
	}

	@Test
	public void earlyRemoveAllWithException() {
		Cache cache = getCache(DEFAULT_CACHE);

		Object key = createKey(name.getMethodName());
		cache.put(key, new Object());

		assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() ->
				service.earlyRemoveAllWithException(true));
		assertTrue(isEmpty(cache));
	}

	@Test
	public void earlyRemoveAllWithExceptionVetoRemove() {
		Cache cache = getCache(DEFAULT_CACHE);

		Object key = createKey(name.getMethodName());
		cache.put(key, new Object());

		assertThatNullPointerException().isThrownBy(() ->
				service.earlyRemoveAllWithException(false));
		// This will be remove anyway as the earlyRemove has removed the cache before
		assertTrue(isEmpty(cache));
	}

	protected boolean isEmpty(Cache cache) {
		ConcurrentHashMap<?, ?> nativeCache = (ConcurrentHashMap<?, ?>) cache.getNativeCache();
		return nativeCache.isEmpty();
	}


	private Object createKey(Object... params) {
		return SimpleKeyGenerator.generateKey(params);
	}

	private Cache getCache(String name) {
		Cache cache = cacheManager.getCache(name);
		assertNotNull("required cache " + name + " does not exist", cache);
		return cache;
	}

	/**
	 * The only purpose of this method is to invoke a particular method on the
	 * service so that the call stack is different.
	 */
	private UnsupportedOperationException methodInCallStack(String keyItem) {
		try {
			service.cacheWithException(keyItem, true);
			throw new IllegalStateException("Should have thrown an exception");
		}
		catch (UnsupportedOperationException e) {
			return e;
		}
	}

	private boolean contain(Throwable t, String className, String methodName) {
		for (StackTraceElement element : t.getStackTrace()) {
			if (className.equals(element.getClassName()) && methodName.equals(element.getMethodName())) {
				return true;
			}
		}
		return false;
	}

}
