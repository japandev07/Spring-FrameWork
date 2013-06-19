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

package org.springframework.web.messaging.service.method;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.core.MethodParameter;
import org.springframework.messaging.Message;
import org.springframework.util.Assert;

/**
 * Resolves method parameters by delegating to a list of registered
 * {@link ArgumentResolver}. Previously resolved method parameters are cached
 * for faster lookups.
 *
 * @author Rossen Stoyanchev
 * @since 4.0
 */
@SuppressWarnings("rawtypes")
public class ArgumentResolverComposite<M extends Message> implements ArgumentResolver<M> {

	protected final Log logger = LogFactory.getLog(getClass());

	private final List<ArgumentResolver<M>> argumentResolvers =	new LinkedList<ArgumentResolver<M>>();

	private final Map<MethodParameter, ArgumentResolver<M>> argumentResolverCache =
			new ConcurrentHashMap<MethodParameter, ArgumentResolver<M>>(256);


	/**
	 * Return a read-only list with the contained resolvers, or an empty list.
	 */
	public List<ArgumentResolver<M>> getResolvers() {
		return Collections.unmodifiableList(this.argumentResolvers);
	}

	/**
	 * Whether the given {@linkplain MethodParameter method parameter} is supported by any registered
	 * {@link ArgumentResolver}.
	 */
	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return getArgumentResolver(parameter) != null;
	}

	/**
	 * Iterate over registered {@link ArgumentResolver}s and invoke the one that supports it.
	 * @exception IllegalStateException if no suitable {@link ArgumentResolver} is found.
	 */
	@Override
	public Object resolveArgument(MethodParameter parameter, M message) throws Exception {

		ArgumentResolver<M> resolver = getArgumentResolver(parameter);
		Assert.notNull(resolver, "Unknown parameter type [" + parameter.getParameterType().getName() + "]");
		return resolver.resolveArgument(parameter, message);
	}

	/**
	 * Find a registered {@link ArgumentResolver} that supports the given method parameter.
	 */
	private ArgumentResolver<M> getArgumentResolver(MethodParameter parameter) {
		ArgumentResolver<M> result = this.argumentResolverCache.get(parameter);
		if (result == null) {
			for (ArgumentResolver<M> resolver : this.argumentResolvers) {
				if (resolver.supportsParameter(parameter)) {
					result = resolver;
					this.argumentResolverCache.put(parameter, result);
					break;
				}
			}
		}
		return result;
	}

	/**
	 * Add the given {@link ArgumentResolver}.
	 */
	public ArgumentResolverComposite<M> addResolver(ArgumentResolver<M> argumentResolver) {
		this.argumentResolvers.add(argumentResolver);
		return this;
	}

	/**
	 * Add the given {@link ArgumentResolver}s.
	 */
	public ArgumentResolverComposite<M> addResolvers(List<? extends ArgumentResolver<M>> argumentResolvers) {
		if (argumentResolvers != null) {
			for (ArgumentResolver<M> resolver : argumentResolvers) {
				this.argumentResolvers.add(resolver);
			}
		}
		return this;
	}

}
