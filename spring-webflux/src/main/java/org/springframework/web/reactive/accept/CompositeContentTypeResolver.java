/*
 * Copyright 2002-2017 the original author or authors.
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
package org.springframework.web.reactive.accept;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.util.Assert;
import org.springframework.web.server.ServerWebExchange;

/**
 * Contains and delegates to other {@link RequestedContentTypeResolver}.
 *
 * @author Rossen Stoyanchev
 * @since 5.0
 */
public class CompositeContentTypeResolver implements RequestedContentTypeResolver {

	private final List<RequestedContentTypeResolver> resolvers = new ArrayList<>();


	public CompositeContentTypeResolver(List<RequestedContentTypeResolver> resolvers) {
		Assert.notEmpty(resolvers, "At least one resolver is expected.");
		this.resolvers.addAll(resolvers);
	}


	@Override
	public List<MediaType> resolveMediaTypes(ServerWebExchange exchange) {
		for (RequestedContentTypeResolver resolver : this.resolvers) {
			List<MediaType> mediaTypes = resolver.resolveMediaTypes(exchange);
			if (mediaTypes.isEmpty() || (mediaTypes.size() == 1 && mediaTypes.contains(MediaType.ALL))) {
				continue;
			}
			return mediaTypes;
		}
		return Collections.emptyList();
	}

}
