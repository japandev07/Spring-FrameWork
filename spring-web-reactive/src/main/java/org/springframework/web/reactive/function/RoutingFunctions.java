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

package org.springframework.web.reactive.function;

import java.util.Map;
import java.util.Optional;

import reactor.core.publisher.Mono;

import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.util.Assert;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.adapter.HttpWebHandlerAdapter;

/**
 * <strong>Central entry point Spring's functional web framework.</strong>
 * Exposes routing functionality, such as to
 * {@linkplain #route(RequestPredicate, HandlerFunction) create} a {@link RoutingFunction} given a
 * {@link RequestPredicate} and {@link HandlerFunction}, and to do further
 * {@linkplain #subroute(RequestPredicate, RoutingFunction) subrouting} on an existing routing
 * function.
 *
 * <p>Additionally, this class can {@linkplain #toHttpHandler(RoutingFunction) transform} a
 * {@link RoutingFunction} into an {@link HttpHandler}, which can be run in
 * {@linkplain org.springframework.http.server.reactive.ServletHttpHandlerAdapter Servlet 3.1+},
 * {@linkplain org.springframework.http.server.reactive.ReactorHttpHandlerAdapter Reactor},
 * {@linkplain org.springframework.http.server.reactive.RxNettyHttpHandlerAdapter RxNetty}, or
 * {@linkplain org.springframework.http.server.reactive.UndertowHttpHandlerAdapter Undertow}.
 * Or it {@linkplain #toHandlerMapping(RoutingFunction, Configuration) transform} a
  * {@link RoutingFunction} into an {@link HandlerMapping}, which can be run in a
 * {@link org.springframework.web.reactive.DispatcherHandler DispatcherHandler}.
 *
 * @author Arjen Poutsma
 * @since 5.0
 */
public abstract class RoutingFunctions {

	private static final HandlerFunction<Void> NOT_FOUND_HANDLER = request -> Response.notFound().build();

	/**
	 * Name of the {@link ServerWebExchange} attribute that contains the {@link Request}.
	 */
	public static final String REQUEST_ATTRIBUTE = RoutingFunctions.class.getName() + ".request";

	/**
	 * Name of the {@link ServerWebExchange} attribute that contains the
	 * {@linkplain Configuration configuration} used throughout the routing and handling of the
	 * request.
	 */
	public static final String CONFIGURATION_ATTRIBUTE = RoutingFunctions.class.getName() + ".configuration";

	/**
	 * Name of the {@link ServerWebExchange} attribute that contains the URI
	 * templates map, mapping variable names to values.
	 */
	public static final String URI_TEMPLATE_VARIABLES_ATTRIBUTE = RoutingFunctions.class.getName() + ".uriTemplateVariables";

	/**
	 * Route to the given handler function if the given request predicate applies.
	 *
	 * @param predicate       the predicate to test
	 * @param handlerFunction the handler function to route to
	 * @param <T>             the type of the handler function
	 * @return a routing function that routes to {@code handlerFunction} if {@code predicate} evaluates to {@code true}
	 * @see RequestPredicates
	 */
	public static <T> RoutingFunction<T> route(RequestPredicate predicate, HandlerFunction<T> handlerFunction) {
		Assert.notNull(predicate, "'predicate' must not be null");
		Assert.notNull(handlerFunction, "'handlerFunction' must not be null");

		return request -> predicate.test(request) ? Optional.of(handlerFunction) : Optional.empty();
	}

	/**
	 * Route to the given routing function if the given request predicate applies.
	 *
	 * @param predicate       the predicate to test
	 * @param routingFunction the routing function to route to
	 * @param <T>             the type of the handler function
	 * @return a routing function that routes to {@code routingFunction} if {@code predicate} evaluates to {@code true}
	 * @see RequestPredicates
	 */
	public static <T> RoutingFunction<T> subroute(RequestPredicate predicate, RoutingFunction<T> routingFunction) {
		Assert.notNull(predicate, "'predicate' must not be null");
		Assert.notNull(routingFunction, "'routingFunction' must not be null");

		return request -> {
			if (predicate.test(request)) {
				Request subRequest = predicate.subRequest(request);
				return routingFunction.route(subRequest);
			}
			else {
				return Optional.empty();
			}
		};
	}

	/**
	 * Converts the given {@linkplain RoutingFunction routing function} into a {@link HttpHandler}.
	 * This conversion uses the {@linkplain Configuration#builder() default configuration}.
	 *
	 * <p>The returned {@code HttpHandler} can be adapted to run in
	 * {@linkplain org.springframework.http.server.reactive.ServletHttpHandlerAdapter Servlet 3.1+},
	 * {@linkplain org.springframework.http.server.reactive.ReactorHttpHandlerAdapter Reactor},
	 * {@linkplain org.springframework.http.server.reactive.RxNettyHttpHandlerAdapter RxNetty}, or
	 * {@linkplain org.springframework.http.server.reactive.UndertowHttpHandlerAdapter Undertow}.
	 *
	 * @param routingFunction the routing function to convert
	 * @return an http handler that handles HTTP request using the given routing function
	 */
	public static HttpHandler toHttpHandler(RoutingFunction<?> routingFunction) {
		return toHttpHandler(routingFunction, defaultConfiguration());
	}

	/**
	 * Converts the given {@linkplain RoutingFunction routing function} into a {@link HttpHandler},
	 * using the given configuration.
	 *
	 * <p>The returned {@code HttpHandler} can be adapted to run in
	 * {@linkplain org.springframework.http.server.reactive.ServletHttpHandlerAdapter Servlet 3.1+},
	 * {@linkplain org.springframework.http.server.reactive.ReactorHttpHandlerAdapter Reactor},
	 * {@linkplain org.springframework.http.server.reactive.RxNettyHttpHandlerAdapter RxNetty}, or
	 * {@linkplain org.springframework.http.server.reactive.UndertowHttpHandlerAdapter Undertow}.
	 *
	 * @param routingFunction the routing function to convert
	 * @param configuration   the configuration to use
	 * @return an http handler that handles HTTP request using the given routing function
	 */
	public static HttpHandler toHttpHandler(RoutingFunction<?> routingFunction, Configuration configuration) {
		Assert.notNull(routingFunction, "'routingFunction' must not be null");
		Assert.notNull(configuration, "'configuration' must not be null");

		return new HttpWebHandlerAdapter(exchange -> {
			Request request = new DefaultRequest(exchange, configuration);
			addAttributes(exchange, request);

			HandlerFunction<?> handlerFunction = routingFunction.route(request).orElse(notFound());
			Response<?> response = handlerFunction.handle(request);
			return response.writeTo(exchange, configuration);
		});
	}

	/**
	 * Converts the given {@linkplain RoutingFunction routing function} into a {@link HandlerMapping}.
	 * This conversion uses the {@linkplain Configuration#builder() default configuration}.
	 *
	 * <p>The returned {@code HttpHandler} can be run in a
	 * {@link org.springframework.web.reactive.DispatcherHandler}.
	 *
	 * @param routingFunction the routing function to convert
	 * @return an handler mapping that maps HTTP request to a handler using the given routing function
	 * @see org.springframework.web.reactive.function.support.HandlerFunctionAdapter
	 * @see org.springframework.web.reactive.function.support.ResponseResultHandler
	 */
	public static HandlerMapping toHandlerMapping(RoutingFunction<?> routingFunction) {
		return toHandlerMapping(routingFunction, defaultConfiguration());
	}

	/**
	 * Converts the given {@linkplain RoutingFunction routing function} into a {@link HandlerMapping}
	 * using the given configuration.
	 *
	 * <p>The returned {@code HttpHandler} can be run in a
	 * {@link org.springframework.web.reactive.DispatcherHandler}.
	 *
	 * @param routingFunction the routing function to convert
	 * @return an handler mapping that maps HTTP request to a handler using the given routing function
	 * @see org.springframework.web.reactive.function.support.HandlerFunctionAdapter
	 * @see org.springframework.web.reactive.function.support.ResponseResultHandler
	 */
	public static HandlerMapping toHandlerMapping(RoutingFunction<?> routingFunction, Configuration configuration) {
		Assert.notNull(routingFunction, "'routingFunction' must not be null");
		Assert.notNull(configuration, "'configuration' must not be null");

		return exchange -> {
			Request request = new DefaultRequest(exchange, configuration);
			addAttributes(exchange, request);

			Optional<? extends HandlerFunction<?>> route = routingFunction.route(request);
			return Mono.justOrEmpty(route);
		};
	}

	private static Configuration defaultConfiguration() {
		return Configuration.builder().build();
	}

	private static void addAttributes(ServerWebExchange exchange, Request request) {
		Map<String, Object> attributes = exchange.getAttributes();
		attributes.put(REQUEST_ATTRIBUTE, request);
	}

	@SuppressWarnings("unchecked")
	private static <T> HandlerFunction<T> notFound() {
		return (HandlerFunction<T>) NOT_FOUND_HANDLER;
	}

}
