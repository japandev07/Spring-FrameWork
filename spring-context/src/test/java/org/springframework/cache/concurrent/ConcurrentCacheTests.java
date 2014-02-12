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

package org.springframework.cache.concurrent;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.junit.Before;
import org.junit.Test;

import org.springframework.cache.Cache;

import static org.junit.Assert.*;

/**
 * @author Costin Leau
 * @author Juergen Hoeller
 * @author Stephane Nicoll
 */
public class ConcurrentCacheTests  {

	protected final static String CACHE_NAME = "testCache";

	protected ConcurrentMap<Object, Object> nativeCache;

	protected Cache cache;


	@Before
	public void setUp() throws Exception {
		nativeCache = new ConcurrentHashMap<Object, Object>();
		cache = new ConcurrentMapCache(CACHE_NAME, nativeCache, true);
		cache.clear();
	}


	@Test
	public void testCacheName() throws Exception {
		assertEquals(CACHE_NAME, cache.getName());
	}

	@Test
	public void testNativeCache() throws Exception {
		assertSame(nativeCache, cache.getNativeCache());
	}

	@Test
	public void testCachePut() throws Exception {
		Object key = "enescu";
		Object value = "george";

		assertNull(cache.get(key));
		assertNull(cache.get(key, String.class));
		assertNull(cache.get(key, Object.class));

		cache.put(key, value);
		assertEquals(value, cache.get(key).get());
		assertEquals(value, cache.get(key, String.class));
		assertEquals(value, cache.get(key, Object.class));
		assertEquals(value, cache.get(key, null));

		cache.put(key, null);
		assertNotNull(cache.get(key));
		assertNull(cache.get(key).get());
		assertNull(cache.get(key, String.class));
		assertNull(cache.get(key, Object.class));
	}

	@Test
	public void testCachePutIfAbsent() throws Exception {
		Object key = new Object();
		Object value = "initialValue";

		assertNull(cache.get(key));
		assertNull(cache.putIfAbsent(key, value));
		assertEquals(value, cache.get(key).get());
		assertEquals("initialValue", cache.putIfAbsent(key, "anotherValue").get());
		assertEquals(value, cache.get(key).get()); // not changed
	}

	@Test
	public void testCacheRemove() throws Exception {
		Object key = "enescu";
		Object value = "george";

		assertNull(cache.get(key));
		cache.put(key, value);
	}

	@Test
	public void testCacheClear() throws Exception {
		assertNull(cache.get("enescu"));
		cache.put("enescu", "george");
		assertNull(cache.get("vlaicu"));
		cache.put("vlaicu", "aurel");
		cache.clear();
		assertNull(cache.get("vlaicu"));
		assertNull(cache.get("enescu"));
	}

}
