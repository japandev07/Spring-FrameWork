/*
 * Copyright 2002-2018 the original author or authors.
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

package org.springframework.cache.annotation;

import java.lang.reflect.Method;
import java.util.Collection;

import org.springframework.cache.interceptor.CacheOperation;
import org.springframework.lang.Nullable;

/**
 * Strategy interface for parsing known caching annotation types.
 * {@link AnnotationCacheOperationSource} delegates to such
 * parsers for supporting specific annotation types such as Spring's own
 * {@link Cacheable}, {@link CachePut} or {@link CacheEvict}.
 *
 * @author Costin Leau
 * @author Stephane Nicoll
 * @since 3.1
 */
public interface CacheAnnotationParser {

	/**
	 * Parses the cache definition for the given class,
	 * based on a known annotation type.
	 * <p>This essentially parses a known cache annotation into Spring's
	 * metadata attribute class. Returns {@code null} if the class
	 * is not cacheable.
	 * @param type the annotated class
	 * @return the configured caching operation,
	 * or {@code null} if none was found
	 * @see AnnotationCacheOperationSource#findCacheOperations(Class)
	 */
	@Nullable
	Collection<CacheOperation> parseCacheAnnotations(Class<?> type);

	/**
	 * Parses the cache definition for the given method,
	 * based on a known annotation type.
	 * <p>This essentially parses a known cache annotation into Spring's
	 * metadata attribute class. Returns {@code null} if the method
	 * is not cacheable.
	 * @param method the annotated method
	 * @return the configured caching operation,
	 * or {@code null} if none was found
	 * @see AnnotationCacheOperationSource#findCacheOperations(Method)
	 */
	@Nullable
	Collection<CacheOperation> parseCacheAnnotations(Method method);
}
