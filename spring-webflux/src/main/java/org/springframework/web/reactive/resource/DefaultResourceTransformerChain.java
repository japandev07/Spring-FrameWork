/*
 * Copyright 2002-201/ the original author or authors.
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

package org.springframework.web.reactive.resource;

import java.util.ArrayList;
import java.util.List;

import reactor.core.publisher.Mono;

import org.springframework.core.io.Resource;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.web.server.ServerWebExchange;

/**
 * A default implementation of {@link ResourceTransformerChain} for invoking
 * a list of {@link ResourceTransformer}s.
 *
 * @author Rossen Stoyanchev
 * @since 5.0
 */
class DefaultResourceTransformerChain implements ResourceTransformerChain {

	private final ResourceResolverChain resolverChain;

	private final List<ResourceTransformer> transformers = new ArrayList<>();

	private int index = -1;


	public DefaultResourceTransformerChain(ResourceResolverChain resolverChain,
			@Nullable List<ResourceTransformer> transformers) {

		Assert.notNull(resolverChain, "ResourceResolverChain is required");
		this.resolverChain = resolverChain;
		if (transformers != null) {
			this.transformers.addAll(transformers);
		}
	}


	public ResourceResolverChain getResolverChain() {
		return this.resolverChain;
	}


	@Override
	public Mono<Resource> transform(ServerWebExchange exchange, Resource resource) {
		ResourceTransformer transformer = getNext();
		if (transformer == null) {
			return Mono.just(resource);
		}
		try {
			return transformer.transform(exchange, resource, this);
		}
		finally {
			this.index--;
		}
	}

	@Nullable
	private ResourceTransformer getNext() {
		Assert.state(this.index <= this.transformers.size(),
				"Current index exceeds the number of configured ResourceTransformer's");

		if (this.index == (this.transformers.size() - 1)) {
			return null;
		}

		this.index++;
		return this.transformers.get(this.index);
	}

}
