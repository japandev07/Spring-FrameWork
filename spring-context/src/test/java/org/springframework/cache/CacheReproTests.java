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

package org.springframework.cache;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mockito;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.cache.annotation.CachingConfigurerSupport;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.cache.interceptor.AbstractCacheResolver;
import org.springframework.cache.interceptor.CacheOperationInvocationContext;
import org.springframework.cache.interceptor.CacheResolver;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Tests to reproduce raised caching issues.
 *
 * @author Phillip Webb
 * @author Juergen Hoeller
 * @author Stephane Nicoll
 */
public class CacheReproTests {

	@Rule
	public final ExpectedException thrown = ExpectedException.none();

	@Test
	public void spr11124() throws Exception {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(Spr11124Config.class);
		Spr11124Service bean = context.getBean(Spr11124Service.class);
		bean.single(2);
		bean.single(2);
		bean.multiple(2);
		bean.multiple(2);
		context.close();
	}

	@Test
	public void spr11249() throws Exception {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(Spr11249Config.class);
		Spr11249Service bean = context.getBean(Spr11249Service.class);
		Object result = bean.doSomething("op", 2, 3);
		assertSame(result, bean.doSomething("op", 2, 3));
		context.close();
	}

	@Test
	public void spr11592GetSimple() {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(Spr11592Config.class);
		Spr11592Service bean = context.getBean(Spr11592Service.class);
		Cache cache = context.getBean("cache", Cache.class);

		String key = "1";
		Object result = bean.getSimple("1");
		verify(cache, times(1)).get(key); // first call: cache miss

		Object cachedResult = bean.getSimple("1");
		assertSame(result, cachedResult);
		verify(cache, times(2)).get(key); // second call: cache hit

		context.close();
	}

	@Test
	public void spr11592GetNeverCache() {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(Spr11592Config.class);
		Spr11592Service bean = context.getBean(Spr11592Service.class);
		Cache cache = context.getBean("cache", Cache.class);

		String key = "1";
		Object result = bean.getNeverCache("1");
		verify(cache, times(0)).get(key); // no cache hit at all, caching disabled

		Object cachedResult = bean.getNeverCache("1");
		assertNotSame(result, cachedResult);
		verify(cache, times(0)).get(key); // caching disabled

		context.close();
	}

	@Test
	public void spr13081ConfigNoCacheNameIsRequired() {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(Spr13081Config.class);
		MyCacheResolver cacheResolver = context.getBean(MyCacheResolver.class);
		Spr13081Service bean = context.getBean(Spr13081Service.class);
		assertNull(cacheResolver.getCache("foo").get("foo"));
		Object result = bean.getSimple("foo"); // cache name = id
		assertEquals(result, cacheResolver.getCache("foo").get("foo").get());
	}

	@Test
	public void spr13081ConfigFailIfCacheResolverReturnsNullCacheName() {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(Spr13081Config.class);
		Spr13081Service bean = context.getBean(Spr13081Service.class);


		thrown.expect(IllegalStateException.class);
		thrown.expectMessage(MyCacheResolver.class.getName());
		bean.getSimple(null);
	}


	@Configuration
	@EnableCaching
	public static class Spr11124Config {

		@Bean
		public CacheManager cacheManager() {
			return new ConcurrentMapCacheManager();
		}

		@Bean
		public Spr11124Service service() {
			return new Spr11124ServiceImpl();
		}
	}


	public interface Spr11124Service {

		List<String> single(int id);

		List<String> multiple(int id);
	}


	public static class Spr11124ServiceImpl implements Spr11124Service {

		private int multipleCount = 0;

		@Override
		@Cacheable("smallCache")
		public List<String> single(int id) {
			if (this.multipleCount > 0) {
				fail("Called too many times");
			}
			this.multipleCount++;
			return Collections.emptyList();
		}

		@Override
		@Caching(cacheable = {
				@Cacheable(cacheNames = "bigCache", unless = "#result.size() < 4"),
				@Cacheable(cacheNames = "smallCache", unless = "#result.size() > 3")})
		public List<String> multiple(int id) {
			if (this.multipleCount > 0) {
				fail("Called too many times");
			}
			this.multipleCount++;
			return Collections.emptyList();
		}
	}


	@Configuration
	@EnableCaching
	public static class Spr11249Config {

		@Bean
		public CacheManager cacheManager() {
			return new ConcurrentMapCacheManager();
		}

		@Bean
		public Spr11249Service service() {
			return new Spr11249Service();
		}
	}


	public static class Spr11249Service {

		@Cacheable("smallCache")
		public Object doSomething(String name, int... values) {
			return new Object();
		}
	}


	@Configuration
	@EnableCaching
	public static class Spr11592Config {

		@Bean
		public CacheManager cacheManager() {
			SimpleCacheManager cacheManager = new SimpleCacheManager();
			cacheManager.setCaches(Arrays.asList(cache()));
			return cacheManager;
		}

		@Bean
		public Cache cache() {
			Cache cache = new ConcurrentMapCache("cache");
			return Mockito.spy(cache);
		}

		@Bean
		public Spr11592Service service() {
			return new Spr11592Service();
		}
	}


	public static class Spr11592Service {

		@Cacheable("cache")
		public Object getSimple(String key) {
			return new Object();
		}

		@Cacheable(cacheNames = "cache", condition = "false")
		public Object getNeverCache(String key) {
			return new Object();
		}
	}

	@Configuration
	@EnableCaching
	public static class Spr13081Config extends CachingConfigurerSupport {

		@Bean
		@Override
		public CacheResolver cacheResolver() {
			return new MyCacheResolver();
		}

		@Bean
		public Spr13081Service service() {
			return new Spr13081Service();
		}

	}

	public static class MyCacheResolver extends AbstractCacheResolver {

		public MyCacheResolver() {
			super(new ConcurrentMapCacheManager());
		}

		@Override
		protected Collection<String> getCacheNames(CacheOperationInvocationContext<?> context) {
			String cacheName = (String) context.getArgs()[0];
			if (cacheName != null) {
				return Collections.singleton(cacheName);
			}
			return null;
		}

		public Cache getCache(String name) {
			return getCacheManager().getCache(name);
		}
	}

	public static class Spr13081Service {

		@Cacheable
		public Object getSimple(String cacheName) {
			return new Object();
		}
	}

}
