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

package org.springframework.cache.aspectj;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.cache.annotation.AbstractCachingConfiguration;
import org.springframework.cache.jcache.config.AbstractJCacheConfiguration;
import org.springframework.context.annotation.AnnotationConfigUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;

/**
 * {@code @Configuration} class that registers the Spring infrastructure beans necessary
 * to enable AspectJ-based annotation-driven cache management for standard JSR-107
 * annotations.
 *
 * @author Stephane Nicoll
 * @since 4.1
 * @see org.springframework.cache.annotation.EnableCaching
 * @see org.springframework.cache.annotation.CachingConfigurationSelector
 */
@Configuration
public class AspectJJCacheConfiguration extends AbstractJCacheConfiguration {

	@Bean(name=AnnotationConfigUtils.JCACHE_ASPECT_BEAN_NAME)
	@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
	public JCacheCacheAspect cacheAspect() {
		JCacheCacheAspect cacheAspect = JCacheCacheAspect.aspectOf();
		cacheAspect.setCacheOperationSource(cacheOperationSource());
		return cacheAspect;
	}

}
