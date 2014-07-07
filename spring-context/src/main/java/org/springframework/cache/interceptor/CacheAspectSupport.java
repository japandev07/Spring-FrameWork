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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.BeanFactoryAnnotationUtils;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.support.SimpleValueWrapper;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.expression.EvaluationContext;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

/**
 * Base class for caching aspects, such as the {@link CacheInterceptor}
 * or an AspectJ aspect.
 *
 * <p>This enables the underlying Spring caching infrastructure to be
 * used easily to implement an aspect for any aspect system.
 *
 * <p>Subclasses are responsible for calling methods in this class in
 * the correct order.
 *
 * <p>Uses the <b>Strategy</b> design pattern. A {@link CacheResolver}
 * implementation will resolve the actual cache(s) to use, and a
 * {@link CacheOperationSource} is used for determining caching
 * operations.
 *
 * <p>A cache aspect is serializable if its {@code CacheResolver} and
 * {@code CacheOperationSource} are serializable.
 *
 * @author Costin Leau
 * @author Juergen Hoeller
 * @author Chris Beams
 * @author Phillip Webb
 * @author Sam Brannen
 * @author Stephane Nicoll
 * @since 3.1
 */
public abstract class CacheAspectSupport extends AbstractCacheInvoker
		implements InitializingBean, ApplicationContextAware {

	protected final Log logger = LogFactory.getLog(getClass());

	/**
	 * Cache of CacheOperationMetadata, keyed by {@link CacheOperationCacheKey}.
	 */
	private final Map<CacheOperationCacheKey, CacheOperationMetadata> metadataCache =
			new ConcurrentHashMap<CacheOperationCacheKey, CacheOperationMetadata>(1024);

	private final ExpressionEvaluator evaluator = new ExpressionEvaluator();

	private CacheOperationSource cacheOperationSource;

	private KeyGenerator keyGenerator = new SimpleKeyGenerator();

	private CacheResolver cacheResolver;

	private ApplicationContext applicationContext;

	private boolean initialized = false;


	/**
	 * Set one or more cache operation sources which are used to find the cache
	 * attributes. If more than one source is provided, they will be aggregated using a
	 * {@link CompositeCacheOperationSource}.
	 * @param cacheOperationSources must not be {@code null}
	 */
	public void setCacheOperationSources(CacheOperationSource... cacheOperationSources) {
		Assert.notEmpty(cacheOperationSources, "At least 1 CacheOperationSource needs to be specified");
		this.cacheOperationSource = (cacheOperationSources.length > 1 ?
				new CompositeCacheOperationSource(cacheOperationSources) : cacheOperationSources[0]);
	}

	/**
	 * Return the CacheOperationSource for this cache aspect.
	 */
	public CacheOperationSource getCacheOperationSource() {
		return this.cacheOperationSource;
	}

	/**
	 * Set the default {@link KeyGenerator} that this cache aspect should delegate to
	 * if no specific key generator has been set for the operation.
	 * <p>The default is a {@link SimpleKeyGenerator}
	 */
	public void setKeyGenerator(KeyGenerator keyGenerator) {
		this.keyGenerator = keyGenerator;
	}

	/**
	 * Return the default {@link KeyGenerator} that this cache aspect delegates to.
	 */
	public KeyGenerator getKeyGenerator() {
		return this.keyGenerator;
	}

	/**
	 * Set the {@link CacheManager} to use to create a default {@link CacheResolver}. Replace
	 * the current {@link CacheResolver}, if any.
	 *
	 * @see #setCacheResolver(CacheResolver)
	 * @see SimpleCacheResolver
	 */
	public void setCacheManager(CacheManager cacheManager) {
		this.cacheResolver = new SimpleCacheResolver(cacheManager);
	}

	/**
	 * Set the default {@link CacheResolver} that this cache aspect should delegate
	 * to if no specific cache resolver has been set for the operation.
	 * <p>The default resolver resolves the caches against their names and the
	 * default cache manager.
	 * @see #setCacheManager(org.springframework.cache.CacheManager)
	 * @see SimpleCacheResolver
	 */
	public void setCacheResolver(CacheResolver cacheResolver) {
		Assert.notNull(cacheResolver);
		this.cacheResolver = cacheResolver;
	}

	/**
	 * Return the default {@link CacheResolver} that this cache aspect delegates to.
	 */
	public CacheResolver getCacheResolver() {
		return cacheResolver;
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) {
		this.applicationContext = applicationContext;
	}

	public void afterPropertiesSet() {
		Assert.state(this.cacheResolver != null, "'cacheResolver' is required. Either set the cache resolver " +
				"to use or set the cache manager to create a default cache resolver based on it.");
		Assert.state(this.cacheOperationSource != null, "The 'cacheOperationSources' property is required: " +
				"If there are no cacheable methods, then don't use a cache aspect.");
		Assert.state(this.getErrorHandler() != null, "The 'errorHandler' is required.");
		Assert.state(this.applicationContext != null, "The application context was not injected as it should.");
		this.initialized = true;
	}


	/**
	 * Convenience method to return a String representation of this Method
	 * for use in logging. Can be overridden in subclasses to provide a
	 * different identifier for the given method.
	 * @param method the method we're interested in
	 * @param targetClass class the method is on
	 * @return log message identifying this method
	 * @see org.springframework.util.ClassUtils#getQualifiedMethodName
	 */
	protected String methodIdentification(Method method, Class<?> targetClass) {
		Method specificMethod = ClassUtils.getMostSpecificMethod(method, targetClass);
		return ClassUtils.getQualifiedMethodName(specificMethod);
	}

	protected Collection<? extends Cache> getCaches(CacheOperationInvocationContext<CacheOperation> context,
													CacheResolver cacheResolver) {
		Collection<? extends Cache> caches = cacheResolver.resolveCaches(context);
		if (caches.isEmpty()) {
			throw new IllegalStateException("No cache could be resolved for '" + context.getOperation()
					+ "' using  resolver '" + cacheResolver
					+ "'. At least one cache should be provided per cache operation.");
		}
		return caches;
	}

	protected CacheOperationContext getOperationContext(CacheOperation operation, Method method, Object[] args,
														Object target, Class<?> targetClass) {

		CacheOperationMetadata metadata = getCacheOperationMetadata(operation, method, targetClass);
		return new CacheOperationContext(metadata, args, target);
	}

	/**
	 * Return the {@link CacheOperationMetadata} for the specified operation.
	 * <p>Resolve the {@link CacheResolver} and the {@link KeyGenerator} to be
	 * used for the operation.
	 * @param operation the operation
	 * @param method the method on which the operation is invoked
	 * @param targetClass the target type
	 * @return the resolved metadata for the operation
	 */
	protected CacheOperationMetadata getCacheOperationMetadata(CacheOperation operation,
															   Method method, Class<?> targetClass) {
		final CacheOperationCacheKey cacheKey = new CacheOperationCacheKey(operation, method, targetClass);
		CacheOperationMetadata metadata = metadataCache.get(cacheKey);
		if (metadata == null) {
			KeyGenerator operationKeyGenerator;
			if (StringUtils.hasText(operation.getKeyGenerator())) {
				operationKeyGenerator = getBean(operation.getKeyGenerator(), KeyGenerator.class);
			}
			else {
				operationKeyGenerator = getKeyGenerator();
			}
			CacheResolver operationCacheResolver;
			if (StringUtils.hasText(operation.getCacheResolver())) {
				operationCacheResolver = getBean(operation.getCacheResolver(), CacheResolver.class);
			}
			else if (StringUtils.hasText(operation.getCacheManager())) {
				CacheManager cacheManager = getBean(operation.getCacheManager(), CacheManager.class);
				operationCacheResolver = new SimpleCacheResolver(cacheManager);
			}
			else {
				operationCacheResolver = getCacheResolver();
			}
			metadata = new CacheOperationMetadata(operation, method, targetClass,
					operationKeyGenerator, operationCacheResolver);
			metadataCache.put(cacheKey, metadata);
		}
		return metadata;
	}

	/**
	 * Return a bean with the specified name and type. Used to resolve services that
	 * are referenced by name in a {@link CacheOperation}.
	 *
	 * @param beanName the name of the bean, as defined by the operation
	 * @param expectedType type type for the bean
	 * @return the bean matching that name
	 * @throws org.springframework.beans.factory.NoSuchBeanDefinitionException if such bean does not exist
	 * @see CacheOperation#keyGenerator
	 * @see CacheOperation#cacheManager
	 * @see CacheOperation#cacheResolver
	 */
	protected <T> T getBean(String beanName, Class<T> expectedType) {
		return BeanFactoryAnnotationUtils.qualifiedBeanOfType(
				applicationContext, expectedType, beanName);
	}

	/**
	 * Clear the cached metadata.
	 */
	protected void clearMetadataCache() {
		metadataCache.clear();
	}

	protected Object execute(CacheOperationInvoker invoker, Object target, Method method, Object[] args) {
		// check whether aspect is enabled
		// to cope with cases where the AJ is pulled in automatically
		if (this.initialized) {
			Class<?> targetClass = getTargetClass(target);
			Collection<CacheOperation> operations = getCacheOperationSource().getCacheOperations(method, targetClass);
			if (!CollectionUtils.isEmpty(operations)) {
				return execute(invoker, new CacheOperationContexts(operations, method, args, target, targetClass));
			}
		}

		return invoker.invoke();
	}

	/**
	 * Execute the underlying operation (typically in case of cache miss) and return
	 * the result of the invocation. If an exception occurs it will be wrapped in
	 * a {@link CacheOperationInvoker.ThrowableWrapper}: the exception can be handled
	 * or modified but it <em>must</em> be wrapped in a
	 * {@link CacheOperationInvoker.ThrowableWrapper} as well.
	 * @param invoker the invoker handling the operation being cached
	 * @return the result of the invocation
	 * @see CacheOperationInvoker#invoke()
	 */
	protected Object invokeOperation(CacheOperationInvoker invoker) {
		return invoker.invoke();
	}

	private Class<?> getTargetClass(Object target) {
		Class<?> targetClass = AopProxyUtils.ultimateTargetClass(target);
		if (targetClass == null && target != null) {
			targetClass = target.getClass();
		}
		return targetClass;
	}

	private Object execute(CacheOperationInvoker invoker, CacheOperationContexts contexts) {
		// Process any early evictions
		processCacheEvicts(contexts.get(CacheEvictOperation.class), true, ExpressionEvaluator.NO_RESULT);

		// Check if we have a cached item matching the conditions
		Cache.ValueWrapper cacheHit = findCachedItem(contexts.get(CacheableOperation.class));

		// Collect puts from any @Cacheable miss, if no cached item is found
		List<CachePutRequest> cachePutRequests = new LinkedList<CachePutRequest>();
		if (cacheHit == null) {
			collectPutRequests(contexts.get(CacheableOperation.class), ExpressionEvaluator.NO_RESULT, cachePutRequests);
		}

		Cache.ValueWrapper result = null;

		// If there are no put requests, just use the cache hit
		if (cachePutRequests.isEmpty() && !hasCachePut(contexts)) {
			result = cacheHit;
		}

		// Invoke the method if don't have a cache hit
		if (result == null) {
			result = new SimpleValueWrapper(invokeOperation(invoker));
		}

		// Collect any explicit @CachePuts
		collectPutRequests(contexts.get(CachePutOperation.class), result.get(), cachePutRequests);

		// Process any collected put requests, either from @CachePut or a @Cacheable miss
		for (CachePutRequest cachePutRequest : cachePutRequests) {
			cachePutRequest.apply(result.get());
		}

		// Process any late evictions
		processCacheEvicts(contexts.get(CacheEvictOperation.class), false, result.get());

		return result.get();
	}

	private boolean hasCachePut(CacheOperationContexts contexts) {
		// Evaluate the conditions *without* the result object because we don't have it yet.
		Collection<CacheOperationContext> cachePutContexts = contexts.get(CachePutOperation.class);
		Collection<CacheOperationContext> excluded = new ArrayList<CacheOperationContext>();
		for (CacheOperationContext context : cachePutContexts) {
			try {
				if (!context.isConditionPassing(ExpressionEvaluator.RESULT_UNAVAILABLE)) {
	                excluded.add(context);
				}
			}
			catch (VariableNotAvailableException e) {
				// Ignoring failure due to missing result, consider the cache put has
				// to proceed
			}
		}
		// check if  all puts have been excluded by condition
		return cachePutContexts.size() != excluded.size();


	}

	private void processCacheEvicts(Collection<CacheOperationContext> contexts, boolean beforeInvocation, Object result) {
		for (CacheOperationContext context : contexts) {
			CacheEvictOperation operation = (CacheEvictOperation) context.metadata.operation;
			if (beforeInvocation == operation.isBeforeInvocation() && isConditionPassing(context, result)) {
				performCacheEvict(context, operation, result);
			}
		}
	}

	private void performCacheEvict(CacheOperationContext context, CacheEvictOperation operation, Object result) {
		Object key = null;
		for (Cache cache : context.getCaches()) {
			if (operation.isCacheWide()) {
				logInvalidating(context, operation, null);
				doClear(cache);
			}
			else {
				if (key == null) {
					key = context.generateKey(result);
				}
				logInvalidating(context, operation, key);
				doEvict(cache, key);
			}
		}
	}

	private void logInvalidating(CacheOperationContext context, CacheEvictOperation operation, Object key) {
		if (logger.isTraceEnabled()) {
			logger.trace("Invalidating " + (key != null ? "cache key [" + key + "]" : "entire cache") +
					" for operation " + operation + " on method " + context.metadata.method);
		}
	}

	/**
	 * Find a cached item only for {@link CacheableOperation} that passes the condition.
	 * @param contexts the cacheable operations
	 * @return a {@link Cache.ValueWrapper} holding the cached item,
	 * or {@code null} if none is found
	 */
	private Cache.ValueWrapper findCachedItem(Collection<CacheOperationContext> contexts) {
		Object result = ExpressionEvaluator.NO_RESULT;
		for (CacheOperationContext context : contexts) {
			if (isConditionPassing(context, result)) {
				Object key = generateKey(context, result);
				Cache.ValueWrapper cached = findInCaches(context, key);
				if (cached != null) {
					return cached;
				}
			}
		}
		return null;
	}

	/**
	 * Collect the {@link CachePutRequest} for all {@link CacheOperation} using
	 * the specified result item.
	 * @param contexts the contexts to handle
	 * @param result the result item (never {@code null})
	 * @param putRequests the collection to update
	 */
	private void collectPutRequests(Collection<CacheOperationContext> contexts,
			Object result, Collection<CachePutRequest> putRequests) {

		for (CacheOperationContext context : contexts) {
			if (isConditionPassing(context, result)) {
				Object key = generateKey(context, result);
				putRequests.add(new CachePutRequest(context, key));
			}
		}
	}

	private Cache.ValueWrapper findInCaches(CacheOperationContext context, Object key) {
		for (Cache cache : context.getCaches()) {
			Cache.ValueWrapper wrapper = doGet(cache, key);
			if (wrapper != null) {
				return wrapper;
			}
		}
		return null;
	}

	private boolean isConditionPassing(CacheOperationContext context, Object result) {
		boolean passing = context.isConditionPassing(result);
		if (!passing && logger.isTraceEnabled()) {
			logger.trace("Cache condition failed on method " + context.metadata.method +
					" for operation " + context.metadata.operation);
		}
		return passing;
	}

	private Object generateKey(CacheOperationContext context, Object result) {
		Object key = context.generateKey(result);
		Assert.notNull(key, "Null key returned for cache operation (maybe you are using named params " +
				"on classes without debug info?) " + context.metadata.operation);
		if (logger.isTraceEnabled()) {
			logger.trace("Computed cache key " + key + " for operation " + context.metadata.operation);
		}
		return key;
	}


	private class CacheOperationContexts {

		private final MultiValueMap<Class<? extends CacheOperation>, CacheOperationContext> contexts =
				new LinkedMultiValueMap<Class<? extends CacheOperation>, CacheOperationContext>();

		public CacheOperationContexts(Collection<? extends CacheOperation> operations,
									  Method method, Object[] args, Object target, Class<?> targetClass) {

			for (CacheOperation operation : operations) {
				this.contexts.add(operation.getClass(), getOperationContext(operation, method, args, target, targetClass));
			}
		}

		public Collection<CacheOperationContext> get(Class<? extends CacheOperation> operationClass) {
			Collection<CacheOperationContext> result = this.contexts.get(operationClass);
			return (result != null ? result : Collections.<CacheOperationContext>emptyList());
		}
	}


	/**
	 * Metadata of a cache operation that does not depend on a particular invocation
	 * which makes it a good candidate for caching.
	 */
	protected static class CacheOperationMetadata {

		private final CacheOperation operation;
		private final Method method;
		private final Class<?> targetClass;
		private final KeyGenerator keyGenerator;
		private final CacheResolver cacheResolver;

		public CacheOperationMetadata(CacheOperation operation, Method method,
									  Class<?> targetClass, KeyGenerator keyGenerator,
									  CacheResolver cacheResolver) {
			this.operation = operation;
			this.method = method;
			this.targetClass = targetClass;
			this.keyGenerator = keyGenerator;
			this.cacheResolver = cacheResolver;
		}
	}

	protected class CacheOperationContext implements CacheOperationInvocationContext<CacheOperation> {

		private final CacheOperationMetadata metadata;

		private final Object[] args;

		private final Object target;

		private final Collection<? extends Cache> caches;

		private final MethodCacheKey methodCacheKey;

		public CacheOperationContext(CacheOperationMetadata metadata,
									 Object[] args, Object target) {
			this.metadata = metadata;
			this.args = extractArgs(metadata.method, args);
			this.target = target;
			this.caches = CacheAspectSupport.this.getCaches(this, metadata.cacheResolver);
			this.methodCacheKey = new MethodCacheKey(metadata.method, metadata.targetClass);
		}

		@Override
		public CacheOperation getOperation() {
			return metadata.operation;
		}

		@Override
		public Object getTarget() {
			return target;
		}

		@Override
		public Method getMethod() {
			return metadata.method;
		}

		@Override
		public Object[] getArgs() {
			return args;
		}

		private Object[] extractArgs(Method method, Object[] args) {
			if (!method.isVarArgs()) {
				return args;
			}
			Object[] varArgs = ObjectUtils.toObjectArray(args[args.length - 1]);
			Object[] combinedArgs = new Object[args.length - 1 + varArgs.length];
			System.arraycopy(args, 0, combinedArgs, 0, args.length - 1);
			System.arraycopy(varArgs, 0, combinedArgs, args.length - 1, varArgs.length);
			return combinedArgs;
		}

		protected boolean isConditionPassing(Object result) {
			if (StringUtils.hasText(this.metadata.operation.getCondition())) {
				EvaluationContext evaluationContext = createEvaluationContext(result);
				return evaluator.condition(this.metadata.operation.getCondition(), this.methodCacheKey, evaluationContext);
			}
			return true;
		}

		protected boolean canPutToCache(Object value) {
			String unless = "";
			if (this.metadata.operation instanceof CacheableOperation) {
				unless = ((CacheableOperation) this.metadata.operation).getUnless();
			}
			else if (this.metadata.operation instanceof CachePutOperation) {
				unless = ((CachePutOperation) this.metadata.operation).getUnless();
			}
			if (StringUtils.hasText(unless)) {
				EvaluationContext evaluationContext = createEvaluationContext(value);
				return !evaluator.unless(unless, this.methodCacheKey, evaluationContext);
			}
			return true;
		}

		/**
		 * Computes the key for the given caching operation.
		 * @return generated key (null if none can be generated)
		 */
		protected Object generateKey(Object result) {
			if (StringUtils.hasText(this.metadata.operation.getKey())) {
				EvaluationContext evaluationContext = createEvaluationContext(result);
				return evaluator.key(this.metadata.operation.getKey(), this.methodCacheKey, evaluationContext);
			}
			return metadata.keyGenerator.generate(this.target, this.metadata.method, this.args);
		}

		private EvaluationContext createEvaluationContext(Object result) {
			return evaluator.createEvaluationContext(
					this.caches, this.metadata.method, this.args, this.target, this.metadata.targetClass, result);
		}

		protected Collection<? extends Cache> getCaches() {
			return this.caches;
		}
	}


	private class CachePutRequest {

		private final CacheOperationContext context;

		private final Object key;

		public CachePutRequest(CacheOperationContext context, Object key) {
			this.context = context;
			this.key = key;
		}

		public void apply(Object result) {
			if (this.context.canPutToCache(result)) {
				for (Cache cache : this.context.getCaches()) {
					doPut(cache, this.key, result);
				}
			}
		}
	}

	private static class CacheOperationCacheKey {

		private final CacheOperation cacheOperation;
		private final MethodCacheKey methodCacheKey;

		private CacheOperationCacheKey(CacheOperation cacheOperation, Method method, Class<?> targetClass) {
			this.cacheOperation = cacheOperation;
			this.methodCacheKey = new MethodCacheKey(method, targetClass);
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;

			CacheOperationCacheKey that = (CacheOperationCacheKey) o;
			return cacheOperation.equals(that.cacheOperation)
					&& methodCacheKey.equals(that.methodCacheKey);
		}

		@Override
		public int hashCode() {
			int result = cacheOperation.hashCode();
			result = 31 * result + methodCacheKey.hashCode();
			return result;
		}
	}
}
