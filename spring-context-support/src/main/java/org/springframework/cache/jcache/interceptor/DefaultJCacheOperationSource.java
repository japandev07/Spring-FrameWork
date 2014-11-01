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

package org.springframework.cache.jcache.interceptor;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.cache.CacheManager;
import org.springframework.cache.interceptor.CacheResolver;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.cache.interceptor.SimpleCacheResolver;
import org.springframework.cache.interceptor.SimpleKeyGenerator;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.util.Assert;

/**
 * The default {@link JCacheOperationSource} implementation delegating
 * default operations to configurable services with sensible defaults
 * when not present.
 *
 * @author Stephane Nicoll
 * @since 4.1
 */
public class DefaultJCacheOperationSource extends AnnotationJCacheOperationSource
		implements ApplicationContextAware, InitializingBean, SmartInitializingSingleton {

	private CacheManager cacheManager;

	private KeyGenerator keyGenerator = new SimpleKeyGenerator();

	private KeyGenerator adaptedKeyGenerator;

	private CacheResolver cacheResolver;

	private CacheResolver exceptionCacheResolver;

	private ApplicationContext applicationContext;


	/**
	 * Set the default {@link CacheManager} to use to lookup cache by name. Only mandatory
	 * if the {@linkplain CacheResolver cache resolvers} have not been set.
	 */
	public void setCacheManager(CacheManager cacheManager) {
		this.cacheManager = cacheManager;
	}

	/**
	 * Set the default {@link KeyGenerator}. If none is set, a {@link SimpleKeyGenerator}
	 * honoringKe the JSR-107 {@link javax.cache.annotation.CacheKey} and
	 * {@link javax.cache.annotation.CacheValue} will be used.
	 */
	public void setKeyGenerator(KeyGenerator keyGenerator) {
		this.keyGenerator = keyGenerator;
	}

	public KeyGenerator getKeyGenerator() {
		return this.keyGenerator;
	}

	/**
	 * Set the {@link CacheResolver} to resolve regular caches. If none is set, a default
	 * implementation using the specified cache manager will be used.
	 */
	public void setCacheResolver(CacheResolver cacheResolver) {
		this.cacheResolver = cacheResolver;
	}

	public CacheResolver getCacheResolver() {
		return getDefaultCacheResolver();
	}

	/**
	 * Set the {@link CacheResolver} to resolve exception caches. If none is set, a default
	 * implementation using the specified cache manager will be used.
	 */
	public void setExceptionCacheResolver(CacheResolver exceptionCacheResolver) {
		this.exceptionCacheResolver = exceptionCacheResolver;
	}

	public CacheResolver getExceptionCacheResolver() {
		return getDefaultExceptionCacheResolver();
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) {
		this.applicationContext = applicationContext;
	}


	@Override
	public void afterPropertiesSet() {
		this.adaptedKeyGenerator = new KeyGeneratorAdapter(this, this.keyGenerator);
	}

	@Override
	public void afterSingletonsInstantiated() {
		// Make sure those are initialized on startup...
		Assert.notNull(getDefaultCacheResolver(), "Cache resolver should have been initialized");
		Assert.notNull(getDefaultExceptionCacheResolver(), "Exception cache resolver should have been initialized");
	}


	@Override
	protected <T> T getBean(Class<T> type) {
		try {
			return this.applicationContext.getBean(type);
		}
		catch (NoUniqueBeanDefinitionException ex) {
			throw new IllegalStateException("No unique [" + type.getName() + "] bean found in application context - " +
					"mark one as primary, or declare a more specific implementation type for your cache", ex);
		}
		catch (NoSuchBeanDefinitionException ex) {
			if (logger.isDebugEnabled()) {
				logger.debug("No bean of type [" + type.getName() + "] found in application context", ex);
			}
			return BeanUtils.instantiateClass(type);
		}
	}

	@Override
	protected CacheResolver getDefaultCacheResolver() {
		if (this.cacheResolver == null) {
			this.cacheResolver = new SimpleCacheResolver(getCacheManager());
		}
		return this.cacheResolver;
	}

	@Override
	protected CacheResolver getDefaultExceptionCacheResolver() {
		if (this.exceptionCacheResolver == null) {
			this.exceptionCacheResolver = new SimpleExceptionCacheResolver(getCacheManager());
		}
		return this.exceptionCacheResolver;
	}

	@Override
	protected KeyGenerator getDefaultKeyGenerator() {
		return this.adaptedKeyGenerator;
	}

	private CacheManager getCacheManager() {
		if (this.cacheManager == null) {
			try {
				this.cacheManager = this.applicationContext.getBean(CacheManager.class);
			}
			catch (NoUniqueBeanDefinitionException ex) {
				throw new IllegalStateException("No unique bean of type CacheManager found. "+
						"Mark one as primary or declare a specific CacheManager to use.");
			}
			catch (NoSuchBeanDefinitionException ex) {
				throw new IllegalStateException("No bean of type CacheManager found. Register a CacheManager "+
						"bean or remove the @EnableCaching annotation from your configuration.");
			}
		}
		return this.cacheManager;
	}

}
