/*
 * Copyright 2002-2016 the original author or authors.
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

package org.springframework.web.server.handler;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import reactor.core.publisher.Mono;

import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.server.WebHandler;

/**
 * WebHandler that delegates to a chain of {@link WebFilter} instances and then
 * to the target {@link WebHandler}.
 *
 * @author Rossen Stoyanchev
 * @since 5.0
 */
public class FilteringWebHandler extends WebHandlerDecorator {

	private final List<WebFilter> filters;


	public FilteringWebHandler(WebHandler targetHandler, WebFilter... filters) {
		super(targetHandler);
		this.filters = initList(filters);
	}

	private static List<WebFilter> initList(WebFilter[] list) {
		return (list != null ? Collections.unmodifiableList(Arrays.asList(list)) : Collections.emptyList());
	}


	/**
	 * Return a read-only list of the configured filters.
	 */
	public List<WebFilter> getFilters() {
		return this.filters;
	}

	@Override
	public Mono<Void> handle(ServerWebExchange exchange) {
		return new DefaultWebFilterChain().filter(exchange);
	}


	private class DefaultWebFilterChain implements WebFilterChain {

		private int index;

		@Override
		public Mono<Void> filter(ServerWebExchange exchange) {
			if (this.index < filters.size()) {
				WebFilter filter = filters.get(this.index++);
				return filter.filter(exchange, this);
			}
			else {
				return getDelegate().handle(exchange);
			}
		}
	}

}
