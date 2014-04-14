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
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.cache.Cache;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.util.ObjectUtils;

/**
 * Utility class handling the SpEL expression parsing.
 * Meant to be used as a reusable, thread-safe component.
 *
 * <p>Performs internal caching for performance reasons
 * using {@link MethodCacheKey}.
 *
 * @author Costin Leau
 * @author Phillip Webb
 * @author Sam Brannen
 * @author Stephane Nicoll
 * @since 3.1
 */
class ExpressionEvaluator {

	public static final Object NO_RESULT = new Object();


	private final SpelExpressionParser parser = new SpelExpressionParser();

	// shared param discoverer since it caches data internally
	private final ParameterNameDiscoverer paramNameDiscoverer = new DefaultParameterNameDiscoverer();

	private final Map<ExpressionKey, Expression> keyCache
			= new ConcurrentHashMap<ExpressionKey, Expression>(64);

	private final Map<ExpressionKey, Expression> conditionCache
			= new ConcurrentHashMap<ExpressionKey, Expression>(64);

	private final Map<ExpressionKey, Expression> unlessCache
			= new ConcurrentHashMap<ExpressionKey, Expression>(64);

	private final Map<MethodCacheKey, Method> targetMethodCache = new ConcurrentHashMap<MethodCacheKey, Method>(64);


	/**
	 * Create an {@link EvaluationContext} without a return value.
	 * @see #createEvaluationContext(Collection, Method, Object[], Object, Class, Object)
	 */
	public EvaluationContext createEvaluationContext(Collection<? extends Cache> caches,
			Method method, Object[] args, Object target, Class<?> targetClass) {
		return createEvaluationContext(caches, method, args, target, targetClass,
				NO_RESULT);
	}

	/**
	 * Create an {@link EvaluationContext}.
	 *
	 * @param caches the current caches
	 * @param method the method
	 * @param args the method arguments
	 * @param target the target object
	 * @param targetClass the target class
	 * @param result the return value (can be {@code null}) or
	 *        {@link #NO_RESULT} if there is no return at this time
	 * @return the evaluation context
	 */
	public EvaluationContext createEvaluationContext(Collection<? extends Cache> caches,
			Method method, Object[] args, Object target, Class<?> targetClass,
			final Object result) {
		CacheExpressionRootObject rootObject = new CacheExpressionRootObject(caches,
				method, args, target, targetClass);
		LazyParamAwareEvaluationContext evaluationContext = new LazyParamAwareEvaluationContext(rootObject,
				this.paramNameDiscoverer, method, args, targetClass, this.targetMethodCache);
		if(result != NO_RESULT) {
			evaluationContext.setVariable("result", result);
		}
		return evaluationContext;
	}

	public Object key(String keyExpression, MethodCacheKey methodKey, EvaluationContext evalContext) {
		return getExpression(this.keyCache, keyExpression, methodKey).getValue(evalContext);
	}

	public boolean condition(String conditionExpression, MethodCacheKey methodKey, EvaluationContext evalContext) {
		return getExpression(this.conditionCache, conditionExpression, methodKey).getValue(
				evalContext, boolean.class);
	}

	public boolean unless(String unlessExpression, MethodCacheKey methodKey, EvaluationContext evalContext) {
		return getExpression(this.unlessCache, unlessExpression, methodKey).getValue(
				evalContext, boolean.class);
	}

	private Expression getExpression(Map<ExpressionKey, Expression> cache, String expression, MethodCacheKey methodKey) {
		ExpressionKey key = createKey(methodKey, expression);
		Expression rtn = cache.get(key);
		if (rtn == null) {
			rtn = this.parser.parseExpression(expression);
			cache.put(key, rtn);
		}
		return rtn;
	}

	private ExpressionKey createKey(MethodCacheKey methodCacheKey, String expression) {
		return new ExpressionKey(methodCacheKey, expression);
	}


	private static class ExpressionKey {
		private final MethodCacheKey methodCacheKey;
		private final String expression;

		private ExpressionKey(MethodCacheKey methodCacheKey, String expression) {
			this.methodCacheKey = methodCacheKey;
			this.expression = expression;
		}

		@Override
		public boolean equals(Object other) {
			if (this == other) {
				return true;
			}
			if (!(other instanceof ExpressionKey)) {
				return false;
			}
			ExpressionKey otherKey = (ExpressionKey) other;
			return (this.methodCacheKey.equals(otherKey.methodCacheKey)
					&& ObjectUtils.nullSafeEquals(this.expression, otherKey.expression));
		}

		@Override
		public int hashCode() {
			return this.methodCacheKey.hashCode() * 29 + (this.expression != null ? this.expression.hashCode() : 0);
		}
	}

}
